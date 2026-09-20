package com.magicbill.app.cloud

import com.magicbill.app.core.Answer
import com.magicbill.app.core.Clock
import com.magicbill.app.core.MbJson
import com.magicbill.app.core.Sentences
import com.magicbill.app.core.asObjectOrNull
import com.magicbill.app.core.long
import com.magicbill.app.core.longOrNull
import com.magicbill.app.core.map
import com.magicbill.app.core.obj
import com.magicbill.app.core.objects
import com.magicbill.app.core.parseJsonOrNull
import com.magicbill.app.core.str
import com.magicbill.app.core.strOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * The one HTTP client for the cloud (PHONE_API.md). Two auth endpoints, the website's sign-up
 * route, PostgREST RPC and REST, one refresh on a 401, and nothing else. Every call has a deadline from the client;
 * every reply becomes an [Answer]. Nothing metered is called from here at all: a staff phone's
 * login is fetched by the counter and only kept here.
 */
class CloudLink(
    private val baseUrl: String,
    private val anonKey: String,
    private val client: OkHttpClient,
    private val sessions: SessionStore,
    private val clock: Clock = Clock.system,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** The website: its sign-up route, and the pages a browser is sent to. */
    private val siteUrl: String = SITE_URL,
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val refreshing = Mutex()

    // ---- Sign in --------------------------------------------------------------------------

    suspend fun passwordLogin(email: String, password: String): Answer<CloudSession> {
        val body = buildJsonObject { put("email", email.trim()); put("password", password) }
        val wire = send(anonRequest("$baseUrl/auth/v1/token?grant_type=password").post(body.toString().toRequestBody(jsonType)).build())
        return when (wire) {
            is Wire.Failed -> Answer.Unreachable(Sentences.CLOUD_UNREACHABLE)
            is Wire.Http -> {
                val o = parseJsonOrNull(wire.body)?.asObjectOrNull()
                if (wire.code in 200..299 && o != null) {
                    val s = sessionFrom(o, CloudSession.Kind.OWNER, email = o.obj("user")?.strOrNull("email") ?: email.trim())
                    sessions.save(s)
                    Answer.Ok(s)
                } else {
                    Answer.Refused(authSentence(o), o?.strOrNull("error_code"))
                }
            }
        }
    }

    /**
     * The login the COUNTER fetched for this phone's person (`POST /v1/cloud-login` on the LAN;
     * the cloud's `phone-session` reply, passed through). Kept as a staff session. Null when
     * the reply has no session in it.
     */
    fun adoptCounterLogin(o: JsonObject): CloudSession? {
        val sess = o.obj("session") ?: return null
        val r = o.obj("restaurant")
        val st = o.obj("staff")
        val s = sessionFrom(
            sess, CloudSession.Kind.STAFF,
            staff = StaffIdentity(r?.str("id") ?: "", r?.str("name") ?: "", r?.str("short_code") ?: "", st?.str("id") ?: "", st?.str("name") ?: ""),
            deviceId = o.strOrNull("device_id"),
        )
        sessions.save(s)
        return s
    }

    /**
     * A new owner's account, made by the website's own sign-up route so the site and the phone
     * share one sign-up: the login is pre-confirmed, and the name, mobile and shop details ride
     * in the user's metadata for the trial or the plan to use later. The phone then signs in
     * with the same email and password. No shop exists after this until a plan is chosen.
     */
    suspend fun signUp(form: SignUp): Answer<Unit> {
        val body = buildJsonObject {
            put("name", form.name.trim())
            put("restaurantName", form.restaurantName.trim())
            put("restaurantAddress", form.restaurantAddress.trim())
            put("phone", form.phone.filter { it.isDigit() })
            put("email", form.email.trim())
            put("password", form.password)
        }
        val request = Request.Builder().url("$siteUrl/api/auth/signup").header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonType)).build()
        return when (val wire = send(request)) {
            is Wire.Failed -> Answer.Unreachable(Sentences.SITE_UNREACHABLE)
            is Wire.Http -> {
                val said = parseJsonOrNull(wire.body)?.asObjectOrNull()?.strOrNull("error")?.takeIf { it.isNotBlank() }
                when {
                    wire.code in 200..299 -> Answer.Ok(Unit)
                    wire.code == 429 -> Answer.Refused(said ?: tooManySentence(wire.retryAfter), "too_many", wire.retryAfter)
                    wire.code in 400..499 -> Answer.Refused(said ?: "Magic Bill did not accept that.", if (wire.code == 409) "exists" else null)
                    else -> Answer.Unreachable(said ?: Sentences.SITE_UNREACHABLE)
                }
            }
        }
    }

    suspend fun signOut() {
        val s = sessions.current()
        sessions.clear()
        if (s != null) {
            // Best effort; the box is already empty, which is what matters.
            send(anonRequest("$baseUrl/auth/v1/logout").header("Authorization", "Bearer ${s.accessToken}").post(ByteArray(0).toRequestBody(null)).build())
        }
    }

    // ---- Talking to the cloud, signed in ---------------------------------------------------

    /** `POST /rest/v1/rpc/<fn>`. */
    suspend fun rpc(fn: String, body: JsonObject = JsonObject(emptyMap())): Answer<JsonElement> = authed { token ->
        signed("$baseUrl/rest/v1/rpc/$fn", token).post(body.toString().toRequestBody(jsonType)).build()
    }.map(::json)

    /** `GET /rest/v1/<path>` — a select with PostgREST filters in the path. */
    suspend fun select(path: String): Answer<JsonElement> = authed { token ->
        signed("$baseUrl/rest/v1/$path", token).get().build()
    }.map(::json)

    /** `POST /rest/v1/<table>` — an insert; the row is not returned. */
    suspend fun insert(table: String, row: JsonObject): Answer<JsonElement> = authed { token ->
        signed("$baseUrl/rest/v1/$table", token).header("Prefer", "return=minimal").post(row.toString().toRequestBody(jsonType)).build()
    }.map(::json)

    /** Anonymous reads: plans, releases, permissions. */
    suspend fun selectAnon(path: String): Answer<JsonElement> = answer(send(anonRequest("$baseUrl/rest/v1/$path").get().build())).map(::json)

    // ---- Storage: the day files, under the same login ------------------------------------------

    /**
     * `POST /storage/v1/object/list/<bucket>` — what sits directly under [prefix] (a folder
     * path ending in "/"): files with their `updated_at`, and sub-folders with none. Every
     * page, by name ascending; nothing metered, RLS is the wall.
     */
    suspend fun listObjects(bucket: String, prefix: String): Answer<List<StorageObject>> {
        val all = ArrayList<StorageObject>()
        while (true) {
            val body = buildJsonObject {
                put("prefix", prefix)
                put("limit", LIST_PAGE)
                put("offset", all.size)
                put("sortBy", buildJsonObject { put("column", "name"); put("order", "asc") })
            }
            val page = when (val a = authed { token -> signed("$baseUrl/storage/v1/object/list/$bucket", token).post(body.toString().toRequestBody(jsonType)).build() }.map(::json)) {
                is Answer.Ok -> a.value.objects().map { StorageObject(it.str("name"), it.strOrNull("updated_at"), it.obj("metadata")?.longOrNull("size")) }
                is Answer.Refused -> return a
                is Answer.Unreachable -> return a
                is Answer.SignedOut -> return a
            }
            all.addAll(page)
            if (page.size < LIST_PAGE) return Answer.Ok(all)
        }
    }

    /**
     * `GET /storage/v1/object/authenticated/<bucket>/<key>` — the object's bytes as they
     * arrive, never read into memory here. The caller closes the stream; the client's
     * deadlines still bound the whole read.
     */
    suspend fun getObjectStream(bucket: String, key: String): Answer<java.io.InputStream> =
        authed(streaming = true) { token -> signed("$baseUrl/storage/v1/object/authenticated/$bucket/$key", token).get().build() }
            .map { checkNotNull(it.stream) }

    /**
     * The one signed-in path: refresh before the token expires, one refresh and one retry on
     * a 401, then the reply as an [Answer] ([answer]). With [streaming] a 2xx body is left
     * open for the caller ([Wire.Http.stream]); every other reply is read to text.
     */
    private suspend fun authed(streaming: Boolean = false, build: (token: String) -> Request): Answer<Wire.Http> {
        val s = sessions.current() ?: return Answer.SignedOut(Sentences.NOT_SIGNED_IN)
        var token = s.accessToken
        if (s.expiresAtMs - clock.now() < 60_000) {
            when (val r = refresh(s.accessToken)) {
                is Answer.Ok -> token = r.value.accessToken
                is Answer.SignedOut -> return r
                else -> {} // try with what we have; the 401 path below refreshes again
            }
        }
        var wire = send(build(token), streaming)
        if (wire is Wire.Http && wire.code == 401) {
            wire = when (val r = refresh(token)) {
                is Answer.Ok -> send(build(r.value.accessToken), streaming)
                is Answer.SignedOut -> return r
                else -> return Answer.Unreachable(Sentences.CLOUD_UNREACHABLE)
            }
        }
        return answer(wire)
    }

    /**
     * One refresh at a time. A caller that arrives while another refresh is running waits and
     * then uses the newer token. The session is cleared only when the server says the refresh
     * token is dead.
     */
    suspend fun refresh(staleToken: String? = null): Answer<CloudSession> = refreshing.withLock {
        val s = sessions.current() ?: return Answer.SignedOut(Sentences.NOT_SIGNED_IN)
        if (staleToken != null && s.accessToken != staleToken) return Answer.Ok(s) // somebody already did
        val body = buildJsonObject { put("refresh_token", s.refreshToken) }
        when (val wire = send(anonRequest("$baseUrl/auth/v1/token?grant_type=refresh_token").post(body.toString().toRequestBody(jsonType)).build())) {
            is Wire.Failed -> Answer.Unreachable(Sentences.CLOUD_UNREACHABLE)
            is Wire.Http -> {
                val o = parseJsonOrNull(wire.body)?.asObjectOrNull()
                if (wire.code in 200..299 && o != null) {
                    val fresh = sessionFrom(o, s.kind, email = s.email, staff = s.staff, deviceId = s.deviceId)
                    sessions.save(fresh)
                    Answer.Ok(fresh)
                } else if (wire.code in 400..403 && refreshTokenIsDead(o)) {
                    // The server's own verdict, by name — the one thing that ends a sign-in
                    // without the person pressing "Sign out". Any other failure keeps the session.
                    android.util.Log.w(TAG, "refresh: the server says the token is dead (${o?.strOrNull("error_code") ?: o?.strOrNull("error")})")
                    sessions.clear()
                    Answer.SignedOut(Sentences.SIGN_IN_ENDED)
                } else {
                    Answer.Unreachable(Sentences.CLOUD_UNREACHABLE)
                }
            }
        }
    }

    // ---- The wire -----------------------------------------------------------------------

    private fun anonRequest(url: String): Request.Builder =
        Request.Builder().url(url).header("apikey", anonKey).header("Content-Type", "application/json")

    private fun signed(url: String, token: String): Request.Builder =
        anonRequest(url).header("Authorization", "Bearer $token")

    private sealed interface Wire {
        /** A reply: its text, or — a streamed 2xx — the body left open in [stream] and [body] empty. */
        data class Http(val code: Int, val body: String, val retryAfter: Int?, val stream: java.io.InputStream? = null) : Wire
        data class Failed(val why: Exception) : Wire
    }

    /**
     * The one exchange: the call under the client's deadlines, the log line, the reply. With
     * [streaming] a 2xx body is handed on unread (closing the stream closes the call); every
     * other body is read to text and closed here.
     */
    private suspend fun send(request: Request, streaming: Boolean = false): Wire = withContext(io) {
        try {
            val r = client.newCall(request).execute()
            // A failed call is logged by status and path — never its body, which may carry a token.
            if (r.code !in 200..299) android.util.Log.w(TAG, "${request.method} ${request.url.encodedPath} → ${r.code}")
            val retryAfter = r.header("Retry-After")?.trim()?.toIntOrNull()
            if (streaming && r.code in 200..299) Wire.Http(r.code, "", retryAfter, r.body.byteStream())
            else r.use { Wire.Http(r.code, it.body.string(), retryAfter) }
        } catch (e: IOException) {
            android.util.Log.w(TAG, "${request.method} ${request.url.encodedPath} failed: ${e.javaClass.simpleName}: ${e.message}")
            Wire.Failed(e)
        } catch (e: IllegalStateException) {
            android.util.Log.w(TAG, "${request.method} ${request.url.encodedPath} failed: ${e.javaClass.simpleName}: ${e.message}")
            Wire.Failed(e)
        }
    }

    /**
     * A reply as an [Answer]: 2xx is the reply itself; a 4xx from PostgREST or Storage carries
     * `{code, message}` (Storage: `{statusCode, error, message}`) written for a person.
     */
    private fun answer(wire: Wire): Answer<Wire.Http> = when (wire) {
        is Wire.Failed -> Answer.Unreachable(Sentences.CLOUD_UNREACHABLE)
        is Wire.Http -> when {
            wire.code in 200..299 -> Answer.Ok(wire)
            wire.code == 401 -> Answer.SignedOut(Sentences.SIGN_IN_ENDED)
            wire.code == 429 -> Answer.Refused(tooManySentence(wire.retryAfter), "too_many", wire.retryAfter)
            wire.code in 400..499 -> {
                val o = parseJsonOrNull(wire.body)?.asObjectOrNull()
                Answer.Refused(o?.strOrNull("message")?.takeIf { it.isNotBlank() } ?: "Magic Bill did not accept that.", o?.strOrNull("code"), null)
            }
            else -> Answer.Unreachable(Sentences.CLOUD_UNREACHABLE)
        }
    }

    /** A 2xx reply's JSON; [JsonNull] when the body is empty or not JSON. */
    private fun json(wire: Wire.Http): JsonElement = parseJsonOrNull(wire.body) ?: JsonNull

    private fun sessionFrom(o: JsonObject, kind: CloudSession.Kind, email: String? = null, staff: StaffIdentity? = null, deviceId: String? = null): CloudSession {
        val expiresAt = o.long("expires_at").takeIf { it > 0 }?.times(1000)
            ?: (clock.now() + o.long("expires_in").coerceAtLeast(60) * 1000)
        return CloudSession(kind, o.str("access_token"), o.str("refresh_token"), expiresAt, email, staff, deviceId)
    }

    /**
     * Supabase names a dead refresh token: `error_code` in its newer replies, `error` +
     * `error_description` in the older ones. A 4xx that says anything else — a rate limit, an
     * odd proxy page, an empty body — is treated as "not now", never as "signed out".
     */
    private fun refreshTokenIsDead(o: JsonObject?): Boolean {
        if (o == null) return false
        val code = o.strOrNull("error_code") ?: o.strOrNull("error") ?: ""
        if (code in DEAD_TOKEN_CODES) return true
        val words = (o.strOrNull("error_description") ?: o.strOrNull("msg") ?: o.strOrNull("message") ?: "").lowercase()
        return "refresh token" in words && ("not found" in words || "already used" in words || "invalid" in words || "expired" in words)
    }

    private fun authSentence(o: JsonObject?): String {
        val code = o?.strOrNull("error_code") ?: o?.strOrNull("error") ?: ""
        return when (code) {
            "invalid_credentials", "invalid_grant" -> "That email and password do not match."
            "email_not_confirmed" -> "Confirm your email first — the link is in your inbox."
            "over_request_rate_limit", "over_email_send_rate_limit" -> "Too many tries. Wait a minute and try again."
            else -> o?.strOrNull("error_description") ?: o?.strOrNull("msg") ?: o?.strOrNull("message") ?: "Could not sign in."
        }
    }

    private fun tooManySentence(retryAfter: Int?): String {
        val s = retryAfter ?: 60
        return if (s >= 120) "Too many tries. Wait ${(s + 59) / 60} minutes." else "Too many tries. Wait $s seconds."
    }

    companion object {
        const val TAG = "MagicBill.cloud"
        const val SITE_URL = "https://www.magicbill.in"
        /** Where a signed-in owner with no shop yet picks a plan. Opened in the phone's browser. */
        const val PLAN_PAGE = "$SITE_URL/dashboard"
        /** A Storage listing's page; a listing shorter than this is the last page. */
        const val LIST_PAGE = 1000

        private val DEAD_TOKEN_CODES = setOf(
            "invalid_grant", "refresh_token_not_found", "refresh_token_already_used",
            "session_not_found", "session_expired", "user_not_found", "user_banned",
        )

        /** Deadlines the caller owns. A phone on shop WiFi shared with customers is the reference. */
        fun client(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(6))
            .readTimeout(java.time.Duration.ofSeconds(20))
            .writeTimeout(java.time.Duration.ofSeconds(20))
            .callTimeout(java.time.Duration.ofSeconds(30))
            .retryOnConnectionFailure(true)
            .build()
    }
}
