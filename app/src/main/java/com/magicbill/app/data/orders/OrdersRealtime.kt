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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The phone's end of the PRIVATE orders channel.
 *
 * WHAT CHANGED IN THE REBUILD:
 *  - The channel is private: RLS on realtime.messages resolves this device's
 *    credential to its restaurant, so a phone can only ever subscribe to its
 *    own topic. Proved by MB-backend/test/tenant-isolation.mjs.
 *  - The bell CARRIES the changed order. There is no "go and ask" fetch and
 *    therefore no fan-out: six phones cost one message, not six calls.
 *  - The 5-second backup poll is GONE. A fallback read arms only after the
 *    socket has been continuously down for 30 seconds, runs at 45 seconds,
 *    and stops the instant the socket recovers. Even that fallback is a
 *    PostgREST read, so it costs nothing against the invocation quota.
 *
 * The good property from the previous round is kept: the fallback job is a
 * SEPARATE coroutine from the socket supervisor, so a socket failure can
 * never leave the tab with no path at all.
 */
@Singleton
class OrdersRealtime @Inject constructor(
    private val supabase: SupabaseClient,
    private val repo: OrdersRepository,
    private val cloud: OrdersCloud,
    private val prefs: SecurePrefs,
    private val network: NetworkMonitor,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var holders = 0
    private var runJob: Job? = null
    private var fallbackJob: Job? = null

    private val _socketUp = MutableStateFlow(false)
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
            try {
                combine(repo.roomId, network.online) { room, online ->
                    if (online) room else null
                }.collectLatest { room ->
                    if (room == null) {
                        markDown()
                        return@collectLatest
                    }
                    var backoff = BACKOFF_START_MS
                    while (isActive) {
                        val startedAt = System.currentTimeMillis()
                        try {
                            runChannel(room) // returns only by throwing/cancel
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "[RT] channel failed: ${e::class.simpleName}: ${e.message}")
                        }
                        markDown()
                        // The backoff resets only after a subscription that
                        // actually HELD — never on the subscribe event itself.
                        // That is what stopped the counter's 2-second
                        // reconnect loop, and the same rule applies here.
                        backoff = if (System.currentTimeMillis() - startedAt >= STABLE_MS) {
                            BACKOFF_START_MS
                        } else {
                            (backoff * 2).coerceAtMost(BACKOFF_MAX_MS)
                        }
                        delay(backoff)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Anything outside the per-attempt retry (the room/network
                // flow itself). The Orders tab must NOT go dead: the fallback
                // job below is separate and keeps a refresh path alive.
                Log.e(TAG, "[RT] realtime supervisor died — falling back to slow reads", e)
                markDown()
            }
        }

        // 5.2 — the safety net. Deliberately independent of runJob, and
        // deliberately idle while the socket is healthy.
        fallbackJob?.cancel()
        fallbackJob = scope.launch {
            var downSince = 0L
            while (isActive) {
                delay(FALLBACK_TICK_MS)
                if (_socketUp.value || !network.online.value) {
                    downSince = 0
                    continue
                }
                val now = System.currentTimeMillis()
                if (downSince == 0L) {
                    downSince = now
                    continue
                }
                if (now - downSince < FALLBACK_ARM_AFTER_MS) continue
                runCatching {
                    repo.refreshOrders()
                    repo.resolveOpenEvents()
                }.onFailure { Log.w(TAG, "[RT] fallback read failed: ${it.message}") }
            }
        }
    }

    private fun stop() {
        Log.i(TAG, "[RT] stop")
        runJob?.cancel(); runJob = null
        fallbackJob?.cancel(); fallbackJob = null
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
     * Only a failed initial subscribe (or cancellation) exits this function,
     * and the channel is always removed on the way out — a channel is never
     * left behind for a new join to collide with.
     */
    private suspend fun runChannel(roomId: String): Nothing = coroutineScope {
        // The credential must be current before the socket authenticates,
        // or the server closes the join on a private topic.
        cloud.ensureEnrolled()

        val installId = prefs.installId()

        // TWO topics, one socket:
        //   orders-<room>        presence only — how this phone knows the
        //                        counter is up and how the counter counts
        //                        phones. Nothing is ever broadcast here.
        //   orders-<room>-live   order truth. The counter does NOT subscribe
        //                        to this one: it authors every message on it,
        //                        and receiving them back cost a third of the
        //                        project's realtime budget.
        val presenceChannel = supabase.channel("orders-$roomId") {
            isPrivate = true
            presence { key = "mob:$installId" }
        }
        val liveChannel = supabase.channel("orders-$roomId-live") {
            isPrivate = true
        }
        try {
            // Register flows BEFORE subscribing so no early message is missed.
            launch {
                liveChannel.broadcastFlow<JsonObject>(event = "mb").collect { msg ->
                    repo.onBell(parseBell(msg))
                }
            }
            launch {
                presenceChannel.presenceChangeFlow().collect { action -> onPresence(action) }
            }
            // Presence is announced from the PRESENCE channel's own status, not
            // the live channel's. They subscribe independently, and tracking on
            // a channel that has not finished subscribing is silently dropped —
            // which is exactly how the counter ended up reading "0 phones"
            // while a phone sat on the Orders tab.
            launch {
                var wasTracking = false
                presenceChannel.status.collect { st ->
                    val up = st == RealtimeChannel.Status.SUBSCRIBED
                    if (up && !wasTracking) {
                        runCatching { presenceChannel.track(presencePayload()) }
                            .onFailure { Log.w(TAG, "[RT] presence track failed: ${it.message}") }
                    }
                    wasTracking = up
                }
            }
            launch {
                var wasUp = false
                liveChannel.status.collect { st ->
                    val up = st == RealtimeChannel.Status.SUBSCRIBED
                    _socketUp.value = up
                    if (up && !wasUp) {
                        Log.i(TAG, "[RT] channel up")
                        // A RECONNECT DOES NOT FETCH BY ITSELF. We ask for
                        // the open set once here because we may genuinely
                        // have missed bells while away — that is a decision
                        // about missed data, not a reaction to the socket,
                        // and it happens once per real reconnect, not once
                        // per two seconds.
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

            withTimeoutOrNull(SUBSCRIBE_TIMEOUT_MS) {
                presenceChannel.subscribe(blockUntilSubscribed = true)
                liveChannel.subscribe(blockUntilSubscribed = true)
            } ?: throw IllegalStateException("subscribe timeout")

            awaitCancellation()
        } finally {
            withContext(NonCancellable) {
                runCatching { supabase.realtime.removeChannel(liveChannel) }
                runCatching { supabase.realtime.removeChannel(presenceChannel) }
            }
        }
    }

    /** One `mb` message -> a typed bell carrying the changed row. */
    private fun parseBell(msg: JsonObject): OrdersBell {
        val kind = msg["kind"]?.jsonPrimitive?.content ?: ""
        val seq = msg["seq"]?.jsonPrimitive?.longOrNull ?: 0L
        val order = msg["order"]?.let {
            runCatching { json.decodeFromJsonElement(WireOrder.serializer(), it) }.getOrNull()
        }
        val event = msg["event"]?.let { runCatching { it.jsonObject }.getOrNull() }
        return OrdersBell(kind = kind, seq = seq, order = order, event = event)
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
        private const val BACKOFF_START_MS = 1_000L
        private const val BACKOFF_MAX_MS = 30_000L

        /** How long a subscription must hold before we trust it (rule R2). */
        private const val STABLE_MS = 30_000L
        private const val SUBSCRIBE_TIMEOUT_MS = 10_000L

        /** 5.2 — the fallback arms only after this much continuous downtime. */
        private const val FALLBACK_ARM_AFTER_MS = 30_000L
        private const val FALLBACK_TICK_MS = 45_000L
    }
}
