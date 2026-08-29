package com.magicbill.app.counter

import com.magicbill.app.core.Answer
import com.magicbill.app.core.Clock
import com.magicbill.app.core.MbJson
import com.magicbill.app.core.Sentences
import com.magicbill.app.prefs.KeyBox
import com.magicbill.app.prefs.Secure
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This phone's link to a counter: the credential, who we are there, and pairing. Pairing is
 * LAN_PROTOCOL.md §3, in order: hello over a connection that trusts nothing → compare what was
 * presented with the QR → pin → present the token → wait while somebody at the counter says
 * whose phone this is and presses Allow → keep the secret (shown once) in the keystore-backed
 * box. The phone types nothing and picks nothing: the counter decides.
 */
@Singleton
class Counter @Inject constructor(
    private val link: CounterLink,
    private val secure: KeyBox,
    private val discovery: Discovery,
    private val clock: Clock,
) {
    private val cred = MutableStateFlow<Credential?>(null)
    private val meState = MutableStateFlow<Me?>(null)
    private val revoked = MutableStateFlow<String?>(null)

    val credential: StateFlow<Credential?> get() = cred
    val me: StateFlow<Me?> get() = meState
    /** The counter's sentence when it said this phone is no longer welcome. Cleared by a new pairing. */
    val revokedSays: StateFlow<String?> get() = revoked

    val isPaired: Boolean get() = cred.value != null

    /** Off the main thread, once, at start. */
    fun load() {
        cred.value = secure.get(Secure.COUNTER_CREDENTIAL)?.let { try { MbJson.decodeFromString(Credential.serializer(), it) } catch (e: Exception) { null } }
        meState.value = secure.get(ME)?.let { try { com.magicbill.app.core.parseJsonOrNull(it)?.let { j -> Me.parse(j as JsonObject) } } catch (e: Exception) { null } }
    }

    /** A code presented and accepted: the counter's name and the request the counter is deciding on. */
    data class Presented(val code: PairCode, val shopName: String, val requestId: String)

    /**
     * Step one: present the code. Checks the counter is the one on the code, pins it, and
     * presents the token. Nothing is issued yet — somebody at the counter has to press Allow.
     */
    suspend fun present(code: PairCode, phoneName: String, install: String): Answer<Presented> {
        val seen = when (val a = link.helloUnpinned(code.host, code.port)) {
            is Answer.Ok -> a.value
            is Answer.Refused -> return a
            // The address is known here, so say it — and name the usual reason, which is the
            // counter's own firewall, not this phone's WiFi.
            is Answer.Unreachable -> return Answer.Unreachable(
                "The counter at ${code.host}:${code.port} did not answer. On the counter, open " +
                    "Settings › Phones — it says if Windows Firewall is blocking it.",
            )
            is Answer.SignedOut -> return a
        }
        if (!Fingerprints.same(seen.presentedFingerprint, code.fingerprint)) {
            return Answer.Refused("That is not the till on the code.")
        }
        return when (val a = link.pair(code.host, code.port, code.fingerprint, phoneName, code.token, install)) {
            is Answer.Ok -> Answer.Ok(Presented(code, seen.hello.shopName, a.value))
            is Answer.Refused -> a
            is Answer.Unreachable -> a
            is Answer.SignedOut -> a
        }
    }

    /**
     * Step two: wait. Somebody at the counter has five minutes to say whose phone this is and
     * press Allow. [onWaiting] tells the screen the wait has begun.
     */
    suspend fun waitForAllow(presented: Presented, onWaiting: () -> Unit): Answer<Credential> {
        val code = presented.code
        onWaiting()
        val deadline = clock.now() + 5 * 60_000
        while (true) {
            when (val a = link.pairStatus(code.host, code.port, code.fingerprint, presented.requestId)) {
                is Answer.Ok -> a.value?.let { return Answer.Ok(keepPaired(code, presented.shopName, it)) }
                is Answer.Refused -> return a
                is Answer.Unreachable -> {} // a blip while waiting is not a refusal
                is Answer.SignedOut -> return a
            }
            if (clock.now() > deadline) return Answer.Refused("Nobody at the counter pressed Allow in five minutes. Scan the code again.")
            delay(1_500)
        }
    }

    /**
     * The phone's login to the cloud, from the counter — for the person the counter bound this
     * phone to. The counter's (or the cloud's) sentence comes back as-is when it cannot.
     */
    suspend fun cloudLogin(): Answer<JsonObject> {
        val c = cred.value ?: return Answer.SignedOut(Sentences.NOT_PAIRED)
        return link.cloudLogin(c)
    }

    private suspend fun keepPaired(code: PairCode, shopName: String, paired: PairedDevice): Credential {
        val c = Credential(code.host, code.port, code.fingerprint, paired.serverId, shopName, paired.deviceId, paired.secret)
        keep(c)
        revoked.value = null
        // Who this phone is at the counter — the floor marks "mine" by it. A phone that has just
        // paired must not walk onto the floor as nobody, so a first miss is tried once more.
        if (refreshMe() !is Answer.Ok) { delay(600); refreshMe() }
        return c
    }

    /** `GET /v1/me`; a 401 here means revoked, and the counter's sentence is kept for the screen. */
    suspend fun refreshMe(): Answer<Me> {
        val c = cred.value ?: return Answer.SignedOut(Sentences.NOT_PAIRED)
        return when (val a = link.me(c)) {
            is Answer.Ok -> { meState.value = a.value; secure.put(ME, meJson(a.value)); a }
            is Answer.SignedOut -> { revoked.value = a.sentence; a }
            is Answer.Refused -> { android.util.Log.w("MagicBill", "/v1/me refused: ${a.sentence}"); a }
            is Answer.Unreachable -> { android.util.Log.w("MagicBill", "/v1/me unreachable: ${a.sentence}"); a }
        }
    }

    /** The credential is bound to the server id, never to an address: find the counter again. */
    suspend fun rediscover(): Boolean {
        val c = cred.value ?: return false
        val found = discovery.find(c.serverId) ?: return false
        if (found.fingerprint != null && !Fingerprints.same(found.fingerprint, c.fingerprint)) return false
        if (found.host != c.host || found.port != c.port) keep(c.copy(host = found.host, port = found.port))
        return true
    }

    /** The person chose to leave this counter: tell it (so the seat is free), then forget it. */
    suspend fun leave() {
        cred.value?.let { c ->
            when (val a = link.leave(c)) {
                is Answer.Ok, is Answer.SignedOut -> {}
                else -> android.util.Log.w("MagicBill", "leaving the counter: ${a.sentenceOrNull}")
            }
        }
        forget()
    }

    /** The counter forgot this phone (or never knew it): drop the credential. */
    fun forget() {
        secure.remove(Secure.COUNTER_CREDENTIAL)
        secure.remove(ME)
        secure.remove(Secure.STREAM_SEQ)
        secure.remove(Secure.CATALOGUE_VERSION)
        cred.value = null
        meState.value = null
        revoked.value = null
    }

    fun may(code: String): Boolean = meState.value?.may?.contains(code) == true

    private fun keep(c: Credential) {
        secure.put(Secure.COUNTER_CREDENTIAL, MbJson.encodeToString(Credential.serializer(), c))
        cred.value = c
    }

    private fun meJson(me: Me): String = kotlinx.serialization.json.buildJsonObject {
        put("device_id", kotlinx.serialization.json.JsonPrimitive(me.deviceId))
        put("name", kotlinx.serialization.json.JsonPrimitive(me.name))
        put("staff_id", me.staffId?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
        put("staff_name", me.staffName?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
        put("may", kotlinx.serialization.json.JsonArray(me.may.map { kotlinx.serialization.json.JsonPrimitive(it) }))
    }.toString()

    companion object {
        private const val ME = "counter.me"
    }
}
