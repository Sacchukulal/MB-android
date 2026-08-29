package com.magicbill.app.counter

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.magicbill.app.core.parseJsonOrNull
import com.magicbill.app.di.AppScope
import com.magicbill.app.prefs.KeyBox
import com.magicbill.app.prefs.Secure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one long-lived connection: `/v1/stream`. Open for as long as this phone is paired and
 * the app is in front — not per screen. A screen that toggled it on its way in and off on its
 * way out once left the phone deaf sixty seconds after every tab hop; now nothing a screen does
 * can close it. Sixty seconds after the app goes to the background it closes, and it reopens
 * the moment the app comes back. Reconnects with `since=<seq>` and backs off. On
 * `too_far_behind` the phone refetches ONCE, as a decision, not a storm (LAN_PROTOCOL.md §4).
 *
 * The counter counts a phone as live while this is open — that is the number on its top bar.
 */
@Singleton
class Stream @Inject constructor(
    private val link: CounterLink,
    private val counter: Counter,
    private val floor: Floor,
    private val secure: KeyBox,
    @AppScope private val scope: CoroutineScope,
) {
    enum class State { Off, Connecting, Live, Lost }

    private val stateFlow = MutableStateFlow(State.Off)
    val state: StateFlow<State> get() = stateFlow

    private var socket: WebSocket? = null
    private var foreground = true
    private var linger: Job? = null
    private var reconnect: Job? = null
    private var attempt = 0
    private val lock = Any()

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { foreground = true; linger?.cancel(); linger = null; open() }
            override fun onStop(owner: LifecycleOwner) { foreground = false; scheduleClose() }
        })
        // A pairing made or broken while the app is open.
        scope.launch {
            counter.credential.collect { c -> if (c == null) close() else if (foreground) open() }
        }
    }

    /** The app came to the floor: make sure the line is up. Safe to call any time. */
    fun ensure() {
        if (foreground) open()
    }

    private fun scheduleClose() {
        linger?.cancel()
        linger = scope.launch {
            delay(LINGER_MS)
            close()
        }
    }

    private fun open() {
        synchronized(lock) {
            if (socket != null) return
            val cred = counter.credential.value ?: return
            stateFlow.value = State.Connecting
            val since = secure.get(Secure.STREAM_SEQ)?.toLongOrNull() ?: 0L
            socket = link.stream(cred, since, listener)
        }
    }

    private fun close() {
        synchronized(lock) {
            reconnect?.cancel(); reconnect = null
            socket?.close(1000, "off the floor")
            socket = null
            stateFlow.value = State.Off
            attempt = 0
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            attempt = 0
            stateFlow.value = State.Live
            scope.launch {
                if (counter.me.value == null) counter.refreshMe() // a phone that never learnt who it is
                floor.flush() // what was queued while we were away
                floor.refreshCatalogue() // one cheap 304 when nothing changed
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val o = parseJsonOrNull(text) as? JsonObject ?: return
            scope.launch(Dispatchers.IO) {
                if (o.containsKey("what")) {
                    when (val missed = Missed.parse(o)) {
                        is Missed.Since -> missed.pushes.forEach { take(it) }
                        is Missed.TooFarBehind -> { floor.catchUp(); secure.put(Secure.STREAM_SEQ, missed.newest.toString()) }
                        null -> {}
                    }
                } else if (o.containsKey("seq")) {
                    take(Push.parse(o))
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = lost(webSocket)
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = lost(webSocket)
    }

    private suspend fun take(push: Push) {
        floor.apply(push)
        secure.put(Secure.STREAM_SEQ, push.seq.toString())
    }

    private fun lost(which: WebSocket) {
        synchronized(lock) {
            if (socket !== which) return
            socket = null
            if (!foreground || counter.credential.value == null) { stateFlow.value = State.Off; return }
            stateFlow.value = State.Lost
            val wait = BACKOFF_MS[attempt.coerceAtMost(BACKOFF_MS.size - 1)]
            attempt++
            reconnect = scope.launch {
                delay(wait)
                if (attempt >= 3) withContext(Dispatchers.IO) { counter.rediscover() } // the counter may have moved
                if (foreground) open()
            }
        }
    }

    companion object {
        const val LINGER_MS = 60_000L
        val BACKOFF_MS = longArrayOf(1_000, 2_000, 5_000, 10_000, 30_000)
    }
}
