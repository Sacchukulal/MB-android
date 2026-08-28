package com.magicbill.app.counter

import com.magicbill.app.core.Answer
import com.magicbill.app.core.MbJson
import com.magicbill.app.core.Sentences
import com.magicbill.app.core.asObjectOrNull
import com.magicbill.app.core.parseJsonOrNull
import com.magicbill.app.core.strOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * The counter's side of the road, from the phone (LAN_PROTOCOL.md). TLS with the pinned
 * certificate on every call but the first `/v1/hello`; the bearer on every call but `hello`
 * and `pair`; a version header on all of them. Short deadlines: this is the shop's own WiFi.
 */
class CounterLink(
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clientFactory: (javax.net.ssl.X509TrustManager) -> OkHttpClient = ::defaultClient,
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val pinnedClients = ConcurrentHashMap<String, OkHttpClient>()

    private fun pinned(fingerprint: String): OkHttpClient =
        pinnedClients.getOrPut(fingerprint) { clientFactory(PinnedTrust(fingerprint)) }

    data class Seen(val hello: Hello, val presentedFingerprint: String)

    /** Over a connection that trusts nothing: what is answering there, and what certificate it showed. */
    suspend fun helloUnpinned(host: String, port: Int): Answer<Seen> {
        val recorder = RecordingTrust()
        val client = clientFactory(recorder)
        val wire = send(client, Request.Builder().url("https://$host:$port/v1/hello").header(VERSION_HEADER, PROTOCOL_VERSION).get().build())
        return when (wire) {
            is Wire.Failed -> Answer.Unreachable(Sentences.COUNTER_UNREACHABLE)
            is Wire.Http -> {
                if (wire.code != 200) return trouble(wire)
                val hello = try { MbJson.decodeFromString(Hello.serializer(), wire.body) } catch (e: Exception) { return Answer.Unreachable(Sentences.COUNTER_UNREACHABLE) }
                val seen = recorder.seenFingerprint ?: return Answer.Unreachable(Sentences.COUNTER_UNREACHABLE)
                Answer.Ok(Seen(hello, seen))
            }
        }
    }

    /** Pinned `hello`, for "is the counter there?" after pairing. */
    suspend fun hello(cred: Credential): Answer<Hello> = when (val wire = send(pinned(cred.fingerprint), plain(cred.url("/v1/hello")).get().build())) {
        is Wire.Failed -> Answer.Unreachable(Sentences.COUNTER_UNREACHABLE)
        is Wire.Http -> if (wire.code == 200) {
            try { Answer.Ok(MbJson.decodeFromString(Hello.serializer(), wire.body)) } catch (e: Exception) { Answer.Unreachable(Sentences.COUNTER_UNREACHABLE) }
        } else trouble(wire)
    }

    /** `POST /v1/pair` → the request id to poll. The certificate is already pinned by then. */
    suspend fun pair(host: String, port: Int, fingerprint: String, name: String, token: String): Answer<String> {
        val body = buildJsonObject { put("name", name); put("platform", "android"); put("token", token) }
        val wire = send(pinned(fingerprint), plain("https://$host:$port/v1/pair").post(body.toString().toRequestBody(jsonType)).build())
        return when (wire) {
            is Wire.Failed -> Answer.Unreachable(Sentences.COUNTER_UNREACHABLE)
            is Wire.Http -> if (wire.code == 202) {
                val id = parseJsonOrNull(wire.body)?.asObjectOrNull()?.strOrNull("request_id")
                if (id == null) Answer.Unreachable(Sentences.COUNTER_UNREACHABLE) else Answer.Ok(id)
            } else trouble(wire)
        }
    }

    /** `GET /v1/pair/{id}`: Ok(null) while somebody at the counter has not pressed Allow yet. */
    suspend fun pairStatus(host: String, port: Int, fingerprint: String, requestId: String): Answer<PairedDevice?> {
        val wire = send(pinned(fingerprint), plain("https://$host:$port/v1/pair/$requestId").get().build())
        return when (wire) {
            is Wire.Failed -> Answer.Unreachable(Sentences.COUNTER_UNREACHABLE)
            is Wire.Http -> when (wire.code) {
                200 -> try { Answer.Ok(MbJson.decodeFromString(PairedDevice.serializer(), wire.body)) } catch (e: Exception) { Answer.Unreachable(Sentences.COUNTER_UNREACHABLE) }
                202 -> Answer.Ok(null)
                else -> trouble(wire)
            }
        }
    }

    suspend fun me(cred: Credential): Answer<Me> = json(cred, signed(cred, "/v1/me").get().build()).let { a ->
        when (a) { is Answer.Ok -> a.value.asObjectOrNull()?.let { Answer.Ok(Me.parse(it)) } ?: Answer.Unreachable(Sentences.COUNTER_UNREACHABLE); is Answer.Refused -> a; is Answer.Unreachable -> a; is Answer.SignedOut -> a }
    }

    /** Ok(null) = 304, keep what you have. */
    suspend fun catalogue(cred: Credential, held: String?): Answer<Catalogue?> {
        val url = cred.url("/v1/catalogue") + (held?.let { "?version=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: "")
        return when (val wire = send(pinned(cred.fingerprint), signedUrl(cred, url).get().build())) {
            is Wire.Failed -> Answer.Unreachable(Sentences.COUNTER_UNREACHABLE)
            is Wire.Http -> when (wire.code) {
                200 -> parseJsonOrNull(wire.body)?.asObjectOrNull()?.let { Answer.Ok(Catalogue.parse(it)) } ?: Answer.Unreachable(Sentences.COUNTER_UNREACHABLE)
                304 -> Answer.Ok(null)
                else -> trouble(wire)
            }
        }
    }

    /** `GET /v1/floor`: the same body as a `floor` push, now. */
    suspend fun floor(cred: Credential): Answer<JsonObject> = json(cred, signed(cred, "/v1/floor").get().build()).let { a ->
        when (a) { is Answer.Ok -> a.value.asObjectOrNull()?.let { Answer.Ok(it) } ?: Answer.Unreachable(Sentences.COUNTER_UNREACHABLE); is Answer.Refused -> a; is Answer.Unreachable -> a; is Answer.SignedOut -> a }
    }

    /** One intent. 200/409/202 all carry an outcome; the outcome is the answer, the status is not. */
    suspend fun intent(cred: Credential, intent: Intent): Answer<Outcome> {
        val wire = send(pinned(cred.fingerprint), signed(cred, "/v1/intent").post(intent.toJson().toString().toRequestBody(jsonType)).build())
        return when (wire) {
            is Wire.Failed -> Answer.Unreachable(Sentences.COUNTER_UNREACHABLE)
            is Wire.Http -> {
                val outcome = parseJsonOrNull(wire.body)?.asObjectOrNull()?.let(Outcome::parse)
                if (outcome != null && wire.code in setOf(200, 202, 409)) Answer.Ok(outcome) else trouble(wire)
            }
        }
    }

    suspend fun batch(cred: Credential, intents: List<Intent>): Answer<BatchResult> {
        val body = buildJsonObject { put("intents", JsonArray(intents.map { it.toJson() })) }
        val wire = send(pinned(cred.fingerprint), signed(cred, "/v1/batch").post(body.toString().toRequestBody(jsonType)).build(), long = true)
        return when (wire) {
            is Wire.Failed -> Answer.Unreachable(Sentences.COUNTER_UNREACHABLE)
            is Wire.Http -> if (wire.code == 200) {
                parseJsonOrNull(wire.body)?.asObjectOrNull()?.let { Answer.Ok(BatchResult.parse(it)) } ?: Answer.Unreachable(Sentences.COUNTER_UNREACHABLE)
            } else trouble(wire)
        }
    }

    /** The WebSocket. The first frame is [Missed]; every frame after is a [Push]. */
    fun stream(cred: Credential, since: Long, listener: WebSocketListener): WebSocket {
        val request = Request.Builder()
            .url(cred.ws("/v1/stream?since=$since"))
            .header("Authorization", "Bearer ${cred.bearer}")
            .header(VERSION_HEADER, PROTOCOL_VERSION)
            .build()
        return pinned(cred.fingerprint).newBuilder().pingInterval(Duration.ofSeconds(20)).readTimeout(Duration.ZERO).build().newWebSocket(request, listener)
    }

    // ---- The wire -----------------------------------------------------------------------

    private fun plain(url: String): Request.Builder = Request.Builder().url(url).header(VERSION_HEADER, PROTOCOL_VERSION).header("Content-Type", "application/json")
    private fun signed(cred: Credential, path: String): Request.Builder = signedUrl(cred, cred.url(path))
    private fun signedUrl(cred: Credential, url: String): Request.Builder = plain(url).header("Authorization", "Bearer ${cred.bearer}")

    private sealed interface Wire {
        data class Http(val code: Int, val body: String, val retryAfter: Int?) : Wire
        data class Failed(val why: Exception) : Wire
    }

    private suspend fun send(client: OkHttpClient, request: Request, long: Boolean = false): Wire = withContext(io) {
        try {
            val c = if (long) client.newBuilder().callTimeout(Duration.ofSeconds(30)).readTimeout(Duration.ofSeconds(30)).build() else client
            c.newCall(request).execute().use { r ->
                Wire.Http(r.code, r.body.string(), r.header("Retry-After")?.trim()?.toIntOrNull())
            }
        } catch (e: IOException) {
            Wire.Failed(e)
        } catch (e: IllegalStateException) {
            Wire.Failed(e)
        }
    }

    private suspend fun json(cred: Credential, request: Request): Answer<kotlinx.serialization.json.JsonElement> =
        when (val wire = send(pinned(cred.fingerprint), request)) {
            is Wire.Failed -> Answer.Unreachable(Sentences.COUNTER_UNREACHABLE)
            is Wire.Http -> if (wire.code == 200) parseJsonOrNull(wire.body)?.let { Answer.Ok(it) } ?: Answer.Unreachable(Sentences.COUNTER_UNREACHABLE) else trouble(wire)
        }

    /** The counter's sentence, as-is (LAN_PROTOCOL.md §5: never reworded). */
    private fun trouble(wire: Wire.Http): Answer<Nothing> {
        val o = parseJsonOrNull(wire.body)?.asObjectOrNull()
        val says = o?.strOrNull("message")?.takeIf { it.isNotBlank() }
        return when (wire.code) {
            401 -> Answer.SignedOut(says ?: Sentences.NOT_PAIRED)
            429 -> Answer.Refused(says ?: "The counter is busy. Try again in a moment.", "too_many", wire.retryAfter)
            426 -> Answer.Refused(says ?: "This app and the counter need updating to talk to each other.", "upgrade")
            in 400..499 -> Answer.Refused(says ?: "The counter did not accept that.", wire.code.toString())
            else -> Answer.Unreachable(says ?: Sentences.COUNTER_UNREACHABLE)
        }
    }

    companion object {
        const val VERSION_HEADER = "x-magicbill-version"
        const val PROTOCOL_VERSION = "1"

        fun defaultClient(trust: javax.net.ssl.X509TrustManager): OkHttpClient = OkHttpClient.Builder()
            .trusting(trust)
            .connectTimeout(Duration.ofSeconds(3))
            .readTimeout(Duration.ofSeconds(6))
            .writeTimeout(Duration.ofSeconds(6))
            .callTimeout(Duration.ofSeconds(8))
            .retryOnConnectionFailure(true)
            .build()
    }
}

@Suppress("unused")
private fun keepImports(o: JsonObject) = o.strOrNull("x")
