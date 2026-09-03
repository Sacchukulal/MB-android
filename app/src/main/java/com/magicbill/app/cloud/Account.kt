package com.magicbill.app.cloud

import com.magicbill.app.core.Answer
import com.magicbill.app.core.Clock
import com.magicbill.app.counter.Counter
import com.magicbill.app.core.parseJsonOrNull
import com.magicbill.app.db.MbDatabase
import com.magicbill.app.di.AppScope
import com.magicbill.app.prefs.Plain
import com.magicbill.app.prefs.Secure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Who is signed in at the cloud and which shop they are looking at. The list from
 * `mb_my_restaurants` is kept as JSON so a cold start has it before the network does.
 */
@Singleton
class Account @Inject constructor(
    private val cloud: CloudLink,
    val sessions: SessionStore,
    private val plain: Plain,
    private val secure: Secure,
    private val db: MbDatabase,
    private val clock: Clock,
    @AppScope private val scope: CoroutineScope,
) {
    private val list = MutableStateFlow<List<Restaurant>>(emptyList())
    private val chosen = MutableStateFlow<String?>(null)
    private val refreshedAt = MutableStateFlow(0L)
    private val counterSaid = MutableStateFlow<String?>(null)

    val restaurants: StateFlow<List<Restaurant>> get() = list
    val lastRefreshedMs: StateFlow<Long> get() = refreshedAt
    /** The counter's sentence the last time it could not sign this phone in; null once it has. */
    val counterLoginSays: StateFlow<String?> get() = counterSaid

    /** The shop on screen: the chosen one, else the first. Null until signed in. */
    val current: StateFlow<Restaurant?> = combine(list, chosen) { l, id -> l.firstOrNull { it.id == id } ?: l.firstOrNull() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val session: StateFlow<CloudSession?> get() = sessions.session

    /** Off the main thread, once, at start. */
    fun load() {
        sessions.load()
        chosen.value = plain.get(Plain.RESTAURANT)
        plain.get(CACHE)?.let { parseJsonOrNull(it) }?.let { list.value = Restaurant.parse(it) }
        refreshedAt.value = plain.getLong(REFRESHED)
    }

    fun installId(): String = secure.installId()

    /** What the counter shows for this phone: the make and model. Nobody types a name. */
    fun phoneName(): String = (android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + android.os.Build.MODEL).trim()

    /**
     * A paired phone's cloud login, from the counter (LAN_PROTOCOL.md §3): the counter asks the
     * cloud for the person it bound this phone to and passes the login through. Nothing to do
     * when the phone is already signed in — an owner's email login is never replaced.
     */
    suspend fun signInThroughCounter(counter: Counter): Answer<CloudSession> {
        sessions.current()?.let { android.util.Log.i(CloudLink.TAG, "counter login: already signed in as ${it.kind}"); counterSaid.value = null; return Answer.Ok(it) }
        android.util.Log.i(CloudLink.TAG, "counter login: asking the counter")
        val answer: Answer<CloudSession> = withContext(Dispatchers.IO) {
            when (val a = counter.cloudLogin()) {
                is Answer.Ok -> {
                    val s = cloud.adoptCounterLogin(a.value)
                    android.util.Log.i(CloudLink.TAG, "counter login: got ${if (s == null) "no session" else "a session for " + s.staff?.staffName}")
                    if (s == null) Answer.Unreachable("The counter's answer had no login in it.")
                    else { val r = refresh(); android.util.Log.i(CloudLink.TAG, "counter login: shop list ${r::class.simpleName}"); Answer.Ok(s) }
                }
                is Answer.Refused -> { android.util.Log.w(CloudLink.TAG, "counter login refused: ${a.sentence}"); a }
                is Answer.Unreachable -> { android.util.Log.w(CloudLink.TAG, "counter login unreachable: ${a.sentence}"); a }
                is Answer.SignedOut -> { android.util.Log.w(CloudLink.TAG, "counter login: not paired / refused at the door: ${a.sentence}"); a }
            }
        }
        counterSaid.value = answer.sentenceOrNull
        return answer
    }

    /** The shop again when the last read is older than [minAgeMs]: on a return to the front, never on a timer. */
    suspend fun refreshIfStale(minAgeMs: Long = 60_000): Answer<List<Restaurant>>? {
        if (sessions.current() == null || clock.now() - refreshedAt.value < minAgeMs) return null
        return refresh()
    }

    suspend fun refresh(): Answer<List<Restaurant>> = when (val a = cloud.rpc("mb_my_restaurants")) {
        is Answer.Ok -> {
            val parsed = Restaurant.parse(a.value)
            list.value = parsed
            plain.put(CACHE, a.value.toString())
            refreshedAt.value = clock.now()
            plain.putLong(REFRESHED, clock.now())
            Answer.Ok(parsed)
        }
        is Answer.Refused -> { android.util.Log.w(CloudLink.TAG, "my restaurants refused: ${a.code} ${a.sentence}"); a }
        is Answer.Unreachable -> { android.util.Log.w(CloudLink.TAG, "my restaurants unreachable"); a }
        is Answer.SignedOut -> { android.util.Log.w(CloudLink.TAG, "my restaurants: signed out — ${a.sentence}"); forget(); a }
    }

    fun choose(id: String) {
        chosen.value = id
        plain.put(Plain.RESTAURANT, id)
    }

    suspend fun signOut() {
        cloud.signOut()
        forget()
    }

    /** Everything the cloud gave this phone, gone: the list, the choice, the mirror. */
    private suspend fun forget() {
        list.value = emptyList()
        chosen.value = null
        refreshedAt.value = 0L
        plain.remove(CACHE)
        plain.remove(Plain.RESTAURANT)
        plain.remove(REFRESHED)
        withContext(Dispatchers.IO) { db.clearAllTables() }
    }

    companion object {
        private const val CACHE = "restaurants.json"
        private const val REFRESHED = "restaurants.refreshed"
    }
}
