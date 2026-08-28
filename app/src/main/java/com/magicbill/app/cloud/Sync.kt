package com.magicbill.app.cloud

import com.magicbill.app.core.Answer
import com.magicbill.app.core.Clock
import com.magicbill.app.di.AppScope
import com.magicbill.app.prefs.Plain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

/**
 * When the mirror pulls. Screens ask on open (only if the last full pull is old), pull-to-
 * refresh asks now. One pull in flight at a time; a second ask while one runs is dropped, not
 * queued. Never on a timer, never in the background.
 */
@Singleton
class Sync @Inject constructor(
    private val mirror: Mirror,
    private val account: Account,
    private val plain: Plain,
    private val clock: Clock,
    @AppScope private val scope: CoroutineScope,
) {
    data class State(
        val pulling: Boolean = false,
        /** The last time everything this caller may see came down, ms. 0 = never. */
        val lastMs: Long = 0L,
        /** The one sentence to show when the last pull could not finish. */
        val sentence: String? = null,
    )

    private val stateFlow = MutableStateFlow(State())
    val state: StateFlow<State> get() = stateFlow
    private val lock = Mutex()

    fun load() {
        val r = account.current.value ?: return
        stateFlow.value = State(lastMs = plain.getLong(key(r.id)))
    }

    /** On a screen opening: a full pull if the last one is older than [minAgeMs]. */
    fun pullIfStale(minAgeMs: Long = 60_000) {
        val r = account.current.value ?: return
        val last = plain.getLong(key(r.id))
        if (clock.now() - last < minAgeMs) return
        scope.launch { pull(null) }
    }

    /** Pull-to-refresh, or a write that wants its row back. */
    suspend fun pullNow(only: Set<String>? = null): Mirror.Report? = pull(only)

    private suspend fun pull(only: Set<String>?): Mirror.Report? {
        val r = account.current.value ?: return null
        if (!lock.tryLock()) return null
        try {
            stateFlow.value = stateFlow.value.copy(pulling = true, sentence = null)
            val report = mirror.pull(r.id, r.permissions, only)
            val now = clock.now()
            if (report.ok && only == null) plain.putLong(key(r.id), now)
            val last = if (report.ok && only == null) now else plain.getLong(key(r.id))
            stateFlow.value = State(pulling = false, lastMs = last, sentence = report.trouble?.let { sentenceFor(it) })
            if (report.trouble is Answer.SignedOut) account.refresh() // will clear the account
            return report
        } finally {
            lock.unlock()
        }
    }

    private fun sentenceFor(a: Answer<Nothing>): String = a.sentenceOrNull ?: ""

    private fun key(rid: String) = "pulled.$rid"
}
