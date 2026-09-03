package com.magicbill.app.cloud

import com.magicbill.app.core.Answer
import com.magicbill.app.core.Clock
import com.magicbill.app.core.MbJson
import com.magicbill.app.core.Sentences
import com.magicbill.app.core.asObjectOrNull
import com.magicbill.app.core.long
import com.magicbill.app.core.obj
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
 * The one HTTP client for the cloud (PHONE_API.md). Two auth endpoints, PostgREST RPC and
 * REST, one refresh on a 401, and nothing else. Every call has a deadline from the client;
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
    }

    /** `GET /rest/v1/<path>` — a select with PostgREST filters in the path. */
    suspend fun select(path: String): Answer<JsonElement> = authed { token ->
        signed("$baseUrl/rest/v1/$path", token).get().build()
    }

    /** `POST /rest/v1/<table>` — an insert; the row is not returned. */
    suspend fun insert(table: String, row: JsonObject): Answer<JsonElement> = authed { token ->
        signed("$baseUrl/rest/v1/$table", token).header("Prefer", "return=minimal").post(row.toString().toRequestBody(jsonType)).build()
    }

    /** Anonymous reads: plans, releases, permissions. */
    suspend fun selectAnon(path: String): Answer<JsonElement> = when (val wire = send(anonRequest("$baseUrl/rest/v1/$path").get().build())) {
        is Wire.Failed -> Answer.Unreachable(Sentences.CLOUD_UNREACHABLE)
        is Wire.Http -> fromRest(wire)
    }

    private suspend fun authed(build: (token: String) -> Request): Answer<JsonElement> {
        val s = sessions.current() ?: return Answer.SignedOut(Sentences.NOT_SIGNED_IN)
        var token = s.accessToken
        if (s.expiresAtMs - clock.now() < 60_000) {
            when (val r = refresh(s.accessToken)) {
                is Answer.Ok -> token = r.value.accessToken
                is Answer.SignedOut -> return r
                else -> {} // try with what we have; the 401 path below refreshes again
            }
        }
        val first = send(build(token))
        if (first is Wire.Http && first.code == 401) {
            return when (val r = refresh(token)) {
                is Answer.Ok -> when (val second = send(build(r.value.accessToken))) {
                    is Wire.Failed -> Answer.Unreachable(Sentences.CLOUD_UNREACHABLE)
                    is Wire.Http -> if (second.code == 401) Answer.SignedOut(Sentences.SIGN_IN_ENDED) else fromRest(second)
                }
                is Answer.SignedOut -> r
                else -> Answer.Unreachable(Sentences.CLOUD_UNREACHABLE)
            }
        }
        return when (first) {
            is Wire.Failed -> Answer.Unreachable(Sentences.CLOUD_UNREACHABLE)
            is Wire.Http -> fromRest(first)
        }
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
        data class Http(val code: Int, val body: String, val retryAfter: Int?) : Wire
        data class Failed(val why: Exception) : Wire
    }

    private suspend fun send(request: Request): Wire = withContext(io) {
        try {
            client.newCall(request).execute().use { r ->
                val body = r.body.string()
                // A failed call is logged by status and path — never its body, which may carry a token.
                if (r.code !in 200..299) android.util.Log.w(TAG, "${request.method} ${request.url.encodedPath} → ${r.code}")
                Wire.Http(r.code, body, r.header("Retry-After")?.trim()?.toIntOrNull())
            }
        } catch (e: IOException) {
            android.util.Log.w(TAG, "${request.method} ${request.url.encodedPath} failed: ${e.javaClass.simpleName}: ${e.message}")
            Wire.Failed(e)
        } catch (e: IllegalStateException) {
            android.util.Log.w(TAG, "${request.method} ${request.url.encodedPath} failed: ${e.javaClass.simpleName}: ${e.message}")
            Wire.Failed(e)
        }
    }

    /** PostgREST: 2xx is data; 4xx carries `{code, message}` written for a person. */
    private fun fromRest(wire: Wire.Http): Answer<JsonElement> {
        val parsed = parseJsonOrNull(wire.body)
        return when {
            wire.code in 200..299 -> Answer.Ok(parsed ?: JsonNull)
            wire.code == 401 -> Answer.SignedOut(Sentences.SIGN_IN_ENDED)
            wire.code == 429 -> Answer.Refused(tooManySentence(wire.retryAfter), "too_many", wire.retryAfter)
            wire.code in 400..499 -> {
                val o = parsed?.asObjectOrNull()
                Answer.Refused(o?.strOrNull("message")?.takeIf { it.isNotBlank() } ?: "Magic Bill did not accept that.", o?.strOrNull("code"), null)
            }
            else -> Answer.Unreachable(Sentences.CLOUD_UNREACHABLE)
        }
    }

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
