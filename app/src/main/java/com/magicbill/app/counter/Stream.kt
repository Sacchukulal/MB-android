package com.magicbill.app.counter

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.magicbill.app.core.parseJsonOrNull
import com.magicbill.app.di.AppScope
import com.magicbill.app.prefs.KeyBox
import com.magicbill.app.prefs.Secure
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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
 * the moment the app comes back.
 *
 * Every frame goes through ONE inbox and is applied in the order it arrived: two floor pushes
 * a few milliseconds apart, applied on two threads, once landed in the wrong order and left
 * the floor stale until the next push. Every time the line comes up the phone replays what it
 * missed, then takes one floor snapshot — so a counter that restarted, or a push lost while
 * the line was down, can never leave the phone quietly behind (LAN_PROTOCOL.md §4).
 *
 * The network is watched: when the phone moves to another one the old socket is dead, so it
 * is dropped and a new one opened at once instead of waiting for a ping to notice.
 *
 * The counter counts a phone as live while this is open — that is the number on its top bar.
 */
@Singleton
class Stream @Inject constructor(
    private val link: CounterLink,
    private val counter: Counter,
    private val floor: Floor,
    private val secure: KeyBox,
    @ApplicationContext context: Context,
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
    private var network: Network? = null
    private val lock = Any()

    /** What the line delivered, in order. One consumer, so nothing is applied out of turn. */
    private sealed interface Frame {
        data object Opened : Frame
        data class Text(val text: String) : Frame
    }
    private val inbox = Channel<Frame>(Channel.UNLIMITED)

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { foreground = true; linger?.cancel(); linger = null; open() }
            override fun onStop(owner: LifecycleOwner) { foreground = false; scheduleClose() }
        })
        // A pairing made or broken while the app is open.
        scope.launch {
            counter.credential.collect { c -> if (c == null) close() else if (foreground) open() }
        }
        scope.launch(Dispatchers.IO) { for (frame in inbox) take(frame) }
        context.getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = networkChanged(network)
            override fun onLost(network: Network) = networkGone(network)
        })
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
            reconnect?.cancel(); reconnect = null
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

    /** The phone is on a network — a new one, or the first: whatever rode the old one is dead. */
    private fun networkChanged(now: Network) {
        synchronized(lock) {
            val changed = network != null && network != now
            network = now
            if (changed) { socket?.cancel(); socket = null }
            attempt = 0
            reconnect?.cancel(); reconnect = null
        }
        if (foreground) open()
    }

    /** The network went: the socket is dead, so say so now rather than when a ping finds out. */
    private fun networkGone(gone: Network) {
        synchronized(lock) {
            if (network != gone) return
            network = null
            socket?.cancel()
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            attempt = 0
            stateFlow.value = State.Live
            inbox.trySend(Frame.Opened)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            inbox.trySend(Frame.Text(text))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = lost(webSocket)
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = lost(webSocket)
    }

    private suspend fun take(frame: Frame) {
        when (frame) {
            // Who this phone is now (the counter may have changed it while the line was down),
            // then what was queued while we were away.
            is Frame.Opened -> { counter.refreshMe(); floor.flush() }
            is Frame.Text -> {
                val o = parseJsonOrNull(frame.text) as? JsonObject ?: return
                if (o.containsKey("what")) {
                    when (val missed = Missed.parse(o)) {
                        // What was missed, in order — then the floor as it is NOW, once.
                        is Missed.Since -> { missed.pushes.forEach { take(it) }; floor.catchUp() }
                        is Missed.TooFarBehind -> { floor.catchUp(); secure.put(Secure.STREAM_SEQ, missed.newest.toString()) }
                        null -> {}
                    }
                } else if (o.containsKey("seq")) {
                    take(Push.parse(o))
                }
            }
        }
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
        /** Short and capped: a floor that waits half a minute to try again is a floor that swipes to refresh. */
        val BACKOFF_MS = longArrayOf(1_000, 2_000, 5_000, 10_000)
    }
}
