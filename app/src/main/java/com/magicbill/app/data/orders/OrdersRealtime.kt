package com.magicbill.app.data.orders

import android.os.Build
import android.util.Log
import com.magicbill.app.data.NetworkMonitor
import com.magicbill.app.data.prefs.SecurePrefs
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PresenceAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The realtime doorbell + presence for live ordering. One channel per
 * restaurant (`orders-<roomId>`); presence answers "is the counter up",
 * broadcast `mb` {kind, seq} tells us to refetch through the Edge Function —
 * no order data ever rides the socket.
 *
 * Lifecycle-aware: Orders screens acquire()/release() this; with zero
 * holders the socket closes (no background connection). While the socket is
 * down but a screen is visible, a 5s poll keeps correctness (degraded mode)
 * and stops the moment the socket is back.
 */
@Singleton
class OrdersRealtime @Inject constructor(
    private val supabase: SupabaseClient,
    private val repo: OrdersRepository,
    private val prefs: SecurePrefs,
    private val network: NetworkMonitor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var holders = 0
    private var runJob: Job? = null
    private var pollJob: Job? = null

    private val _socketUp = MutableStateFlow(false)

    /** True while the channel is live — degraded polling switches on its inverse. */
    val socketUp: StateFlow<Boolean> = _socketUp.asStateFlow()

    /** Presence keys currently in the room (pos:<deviceId> / mob:<installId>). */
    private val present = mutableSetOf<String>()

    // ---------------- lifecycle (called from Orders screens) ----------------

    @Synchronized
    fun acquire() {
        holders++
        if (holders == 1) start()
    }

    @Synchronized
    fun release() {
        holders = (holders - 1).coerceAtLeast(0)
        if (holders == 0) stop()
    }

    private fun start() {
        Log.i(TAG, "[RT] start")
        runJob?.cancel()
        runJob = scope.launch {
            // Re-join whenever the room or connectivity changes.
            combine(repo.roomId, network.online) { room, online ->
                if (online) room else null
            }.collectLatest { room ->
                if (room == null) {
                    markDown()
                    return@collectLatest
                }
                var backoff = 1_000L
                while (isActive) {
                    try {
                        runChannel(room) // returns only by throwing/cancel
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "[RT] channel failed: ${e::class.simpleName}: ${e.message}")
                    }
                    markDown()
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(30_000L)
                }
            }
        }
        // Degraded safety net, foreground only (this job dies with stop()).
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                delay(5_000)
                if (!_socketUp.value && network.online.value) {
                    runCatching {
                        repo.refreshOrders()
                        repo.resolveOpenEvents()
                    }
                }
            }
        }
    }

    private fun stop() {
        Log.i(TAG, "[RT] stop")
        runJob?.cancel(); runJob = null
        pollJob?.cancel(); pollJob = null
        markDown()
    }

    private fun markDown() {
        _socketUp.value = false
        synchronized(present) { present.clear() }
        repo.setPresencePosOnline(null)
    }

    // ---------------- channel plumbing ----------------

    /**
     * Joins the room and stays suspended for the life of the connection.
     * supabase-kt heals transient socket drops itself; the status collector
     * mirrors that into [socketUp] and re-tracks presence after each rejoin.
     * Only a failed initial subscribe (or cancellation) exits this function.
     */
    private suspend fun runChannel(roomId: String): Nothing = coroutineScope {
        val installId = prefs.installId()
        val channel = supabase.channel("orders-$roomId") {
            presence { key = "mob:$installId" }
        }
        try {
            // Register flows BEFORE subscribing so no early message is missed.
            launch {
                channel.broadcastFlow<JsonObject>(event = "mb").collect { msg ->
                    val kind = msg["kind"]?.jsonPrimitive?.content ?: return@collect
                    val seq = msg["seq"]?.jsonPrimitive?.longOrNull ?: 0L
                    repo.onDoorbell(kind, seq)
                }
            }
            launch {
                channel.presenceChangeFlow().collect { action -> onPresence(action) }
            }
            launch {
                var wasUp = false
                channel.status.collect { st ->
                    val up = st == RealtimeChannel.Status.SUBSCRIBED
                    _socketUp.value = up
                    if (up && !wasUp) {
                        Log.i(TAG, "[RT] channel up")
                        // (Re)joined: presence must be re-announced, and we
                        // may have missed doorbells while away.
                        runCatching { channel.track(presencePayload()) }
                        runCatching { repo.refreshOrders(); repo.resolveOpenEvents() }
                    }
                    if (!up && wasUp) {
                        Log.i(TAG, "[RT] channel down")
                        synchronized(present) { present.clear() }
                        repo.setPresencePosOnline(null)
                    }
                    wasUp = up
                }
            }

            withTimeoutOrNull(10_000) { channel.subscribe(blockUntilSubscribed = true) }
                ?: throw IllegalStateException("subscribe timeout")

            awaitCancellation()
        } finally {
            withContext(NonCancellable) {
                runCatching { supabase.realtime.removeChannel(channel) }
            }
        }
    }

    private fun presencePayload(): JsonObject = buildJsonObject {
        put("kind", "mobile")
        put("name", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
        put("version", com.magicbill.app.BuildConfig.VERSION_NAME)
        put("at", Instant.now().toString())
    }

    private fun onPresence(action: PresenceAction) {
        val posUp: Boolean
        synchronized(present) {
            action.leaves.keys.forEach { present.remove(it) }
            present.addAll(action.joins.keys)
            posUp = present.any { it.startsWith("pos:") }
        }
        repo.setPresencePosOnline(posUp)
    }

    companion object {
        private const val TAG = "MB/Orders"
    }
}
