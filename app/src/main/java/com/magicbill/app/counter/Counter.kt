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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This phone's link to a counter: the credential, who we are there, and pairing. Pairing is
 * LAN_PROTOCOL.md §3, in order: hello over a connection that trusts nothing → compare what was
 * presented with the QR → pin → present the token → the person names themselves and proves it
 * with their PIN (or a shared tablet waits for somebody to press Allow) → keep the secret
 * (shown once) in the keystore-backed box.
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
        meState.value = secure.get(ME)?.let { try { com.magicbill.app.core.parseJsonOrNull(it)?.let { j -> Me.parse(j as kotlinx.serialization.json.JsonObject) } } catch (e: Exception) { null } }
    }

    /** A code presented and accepted: the counter's name, the request to claim, and who it could be for. */
    data class Presented(val code: PairCode, val shopName: String, val asked: Asked)

    /**
     * Step one: present the code. Checks the counter is the one on the code, pins it, and
     * presents the token. Nothing is issued yet — the person still has to say who they are.
     */
    suspend fun present(code: PairCode, phoneName: String): Answer<Presented> {
        val seen = when (val a = link.helloUnpinned(code.host, code.port)) {
            is Answer.Ok -> a.value
            is Answer.Refused -> return a
            is Answer.Unreachable -> return a
            is Answer.SignedOut -> return a
        }
        if (!Fingerprints.same(seen.presentedFingerprint, code.fingerprint)) {
            return Answer.Refused("That is not the till on the code.")
        }
        return when (val a = link.pair(code.host, code.port, code.fingerprint, phoneName, code.token)) {
            is Answer.Ok -> Answer.Ok(Presented(code, seen.hello.shopName, a.value))
            is Answer.Refused -> a
            is Answer.Unreachable -> a
            is Answer.SignedOut -> a
        }
    }

    /** Step two, a person: their name and their PIN. A credential on the spot. */
    suspend fun claim(presented: Presented, staffId: String, pin: String): Answer<Credential> {
        val code = presented.code
        return when (val a = link.claim(code.host, code.port, code.fingerprint, presented.asked.requestId, staffId, pin)) {
            is Answer.Ok -> a.value?.let { Answer.Ok(keepPaired(code, presented.shopName, it)) }
                ?: Answer.Refused("The counter did not let this phone in. Try again.")
            is Answer.Refused -> a
            is Answer.Unreachable -> a
            is Answer.SignedOut -> a
        }
    }

    /**
     * Step two, a shared tablet: nobody's, so somebody at the counter has five minutes to press
     * Allow. [onWaiting] tells the screen the wait has begun.
     */
    suspend fun waitForAllow(presented: Presented, onWaiting: () -> Unit): Answer<Credential> {
        val code = presented.code
        when (val a = link.claim(code.host, code.port, code.fingerprint, presented.asked.requestId, null, null)) {
            is Answer.Ok -> a.value?.let { return Answer.Ok(keepPaired(code, presented.shopName, it)) }
            is Answer.Refused -> return a
            is Answer.Unreachable -> return a
            is Answer.SignedOut -> return a
        }
        onWaiting()
        val deadline = clock.now() + 5 * 60_000
        while (true) {
            if (clock.now() > deadline) return Answer.Refused("Nobody at the counter pressed Allow in five minutes. Scan the code again.")
            delay(1_500)
            when (val a = link.pairStatus(code.host, code.port, code.fingerprint, presented.asked.requestId)) {
                is Answer.Ok -> a.value?.let { return Answer.Ok(keepPaired(code, presented.shopName, it)) }
                is Answer.Refused -> return a
                is Answer.Unreachable -> {} // a blip while waiting is not a refusal
                is Answer.SignedOut -> return a
            }
        }
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

    /** The person chose to leave this counter. */
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
        put("may", kotlinx.serialization.json.JsonArray(me.may.map { kotlinx.serialization.json.JsonPrimitive(it) }))
    }.toString()

    companion object {
        private const val ME = "counter.me"
    }
}
