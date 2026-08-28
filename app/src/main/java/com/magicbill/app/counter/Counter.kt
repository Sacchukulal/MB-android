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
 * presented with the QR → pin → present the token → wait for somebody to press Allow → keep
 * the secret (shown once) in the keystore-backed box.
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

    sealed interface Step {
        data object Checking : Step
        data class Waiting(val shopName: String) : Step
        data object Done : Step
    }

    /**
     * Pairs with the counter on [code]. [onStep] tells the screen where we are; the person at
     * the counter has five minutes to press Allow.
     */
    suspend fun pair(code: PairCode, phoneName: String, onStep: (Step) -> Unit): Answer<Credential> {
        onStep(Step.Checking)
        val seen = when (val a = link.helloUnpinned(code.host, code.port)) {
            is Answer.Ok -> a.value
            is Answer.Refused -> return a
            is Answer.Unreachable -> return a
            is Answer.SignedOut -> return a
        }
        if (!Fingerprints.same(seen.presentedFingerprint, code.fingerprint)) {
            return Answer.Refused("That is not the till on the code.")
        }
        val requestId = when (val a = link.pair(code.host, code.port, code.fingerprint, phoneName, code.token)) {
            is Answer.Ok -> a.value
            is Answer.Refused -> return a
            is Answer.Unreachable -> return a
            is Answer.SignedOut -> return a
        }
        onStep(Step.Waiting(seen.hello.shopName))
        val deadline = clock.now() + 5 * 60_000
        var paired: PairedDevice? = null
        while (paired == null) {
            if (clock.now() > deadline) return Answer.Refused("Nobody at the counter pressed Allow in five minutes. Ask for a new code.")
            delay(1_500)
            when (val a = link.pairStatus(code.host, code.port, code.fingerprint, requestId)) {
                is Answer.Ok -> paired = a.value
                is Answer.Refused -> return a
                is Answer.Unreachable -> {} // a blip while waiting is not a refusal
                is Answer.SignedOut -> return a
            }
        }
        val c = Credential(code.host, code.port, code.fingerprint, paired.serverId, seen.hello.shopName, paired.deviceId, paired.secret)
        keep(c)
        revoked.value = null
        refreshMe()
        onStep(Step.Done)
        return Answer.Ok(c)
    }

    /** `GET /v1/me`; a 401 here means revoked, and the counter's sentence is kept for the screen. */
    suspend fun refreshMe(): Answer<Me> {
        val c = cred.value ?: return Answer.SignedOut(Sentences.NOT_PAIRED)
        return when (val a = link.me(c)) {
            is Answer.Ok -> { meState.value = a.value; secure.put(ME, meJson(a.value)); a }
            is Answer.SignedOut -> { revoked.value = a.sentence; a }
            is Answer.Refused -> a
            is Answer.Unreachable -> a
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
