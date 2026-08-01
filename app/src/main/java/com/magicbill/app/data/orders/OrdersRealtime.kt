package com.magicbill.app.data.orders

import android.os.Build
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
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
import kotlinx.coroutines.currentCoroutineContext
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
 * The phone's end of the private orders channels.
 *
 * TWO LINES, DIFFERENT LIFETIMES (2.4.5, PART C). This is the whole shape of
 * the file, and it replaces a single refcounted connection that was torn
 * down and rebuilt on every screen change.
 *
 *   PRESENCE  `orders-<room>`       Cheap. Once established it sends nothing
 *                                   until somebody joins or leaves. HELD FOR
 *                                   THE WHOLE TIME THE APP IS IN THE
 *                                   FOREGROUND, for any session with
 *                                   ordering access. Not tied to which tab
 *                                   is showing. This is what makes the
 *                                   counter's phone count steady.
 *
 *   LIVE      `orders-<room>-live`  Expensive: every order change is one
 *                                   billable message PER SUBSCRIBED PHONE.
 *                                   Held only while an Orders surface is on
 *                                   screen, PLUS a 60-second linger after
 *                                   the last one goes away. The linger is
 *                                   what makes navigation free.
 *
 * WHY. Compose Navigation pauses the outgoing screen BEFORE the incoming one
 * resumes, so a refcount held by the screens passes through zero on EVERY
 * table tap, tab switch and momentary minimise. Each of those cycles used to
 * perform a full leave-and-rejoin of both channels, which costs:
 *
 *   (a) a presence notice delivered to every other member of the room — a
 *       billable realtime message, and one nothing had ever modelled;
 *   (b) a full re-read of the open order set on every channel-up — egress;
 *   (c) an RPC for the counter's status on every arrival at the Orders tab.
 *
 * Measured on the real project (MB-backend quota-simulation Q9), ten minutes
 * of a waiter's actual behaviour:
 *
 *       channel rejoins                 61  ->   1
 *       presence notices at the counter 119 ->   2
 *       catch-up reads                  61  ->   1
 *
 * At 30 shops with two phones each that was 4.28 MILLION presence messages a
 * month — 214% of the free plan on its own — against 72,000 after. It is
 * also what made the counter's phone count flicker between 1 and 0, which is
 * how the owner noticed.
 *
 * WHAT IS DELIBERATELY UNCHANGED (scar tissue; see ORDERS_REBUILD_REPORT.md):
 *  - the backoff resets only after a subscription has HELD for 30 seconds,
 *    never on the subscribe event itself;
 *  - markDown() does not trigger a counter-status check;
 *  - presence is announced from the PRESENCE channel's own status, never the
 *    live channel's;
 *  - presence is a TRIGGER to go and ask, never the answer: a killed process
 *    sends no leave.
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

    // ---------------- what each line's lifetime depends on ----------------

    /** Orders surfaces currently on screen (list / builder / detail). */
    private var holders = 0

    /** Is the APP in the foreground? Not "is a screen visible". */
    private var foreground = false

    /** Does this session take orders at all? Owner always; staff need take_orders. */
    private var orderingAccess = false

    private var processObserverInstalled = false

    // ---------------- the two lines ----------------

    private var presenceJob: Job? = null
    private var liveJob: Job? = null

    /** Jobs that exist only while the LIVE line does. */
    private var fallbackJob: Job? = null
    private var statusJob: Job? = null
    private var networkJob: Job? = null

    /**
     * A pending stop, and the token that makes it safe.
     *
     * A stop scheduled for one incarnation of a line must never be able to
     * kill a line that has since been rebuilt — including for a DIFFERENT
     * ROOM after the owner switches restaurant. Cancelling the job is not
     * enough on its own: the coroutine can already be past its delay and
     * waiting on the lock when the new line starts. The token is checked
     * inside the lock, so a stale stop always loses.
     */
    private var presenceStopJob: Job? = null
    private var presenceStopToken = 0
    private var liveStopJob: Job? = null
    private var liveStopToken = 0

    private val _socketUp = MutableStateFlow(false)
    val socketUp: StateFlow<Boolean> = _socketUp.asStateFlow()

    /** Presence keys currently in the room (pos:<deviceId> / mob:<installId>). */
    private val present = mutableSetOf<String>()

    // ---------------- lifecycle in (called from shells and screens) ----------------

    /**
     * The session's ordering access. Owner shells pass true always; the
     * staff shell passes whether take_orders is granted. A session with no
     * ordering access holds NOTHING — no presence, no socket, no battery.
     */
    fun setOrderingAccess(enabled: Boolean) {
        installProcessObserver()
        synchronized(this) {
            if (orderingAccess == enabled) return
            orderingAccess = enabled
        }
        if (enabled) {
            // The presence line needs a room before it can join one, and
            // `roomId` is otherwise only published once the Orders tab has
            // loaded. This is a single read from Room, no network.
            scope.launch { runCatching { repo.primeRoomId() } }
            if (foreground) startPresence()
        } else {
            stopPresenceNow()
            stopLiveNow()
        }
    }

    /**
     * An Orders surface appeared. Cancels any pending live-line stop — which
     * is what makes navigation free: the channel that was going to close in
     * 60 seconds is simply reused, with no rejoin, no re-read and no
     * presence churn.
     */
    @Synchronized
    fun acquire() {
        installProcessObserver()
        // Being on an Orders surface IS ordering access. Deriving it here as
        // well as from the shell means a screen can never end up connected
        // to nothing because a shell forgot to declare it.
        orderingAccess = true
        holders++
        if (holders == 1) {
            if (foreground) startPresence()
            startLive()
        }
    }

    /** An Orders surface went away. Nothing stops now — the linger starts. */
    @Synchronized
    fun release() {
        holders = (holders - 1).coerceAtLeast(0)
        if (holders == 0) scheduleLiveStop(LINGER_MS)
    }

    /**
     * Whether the app is in the foreground, from ProcessLifecycleOwner.
     *
     * THE TRAP THIS AVOIDS: addObserver REPLAYS the current lifecycle state
     * on registration, so a "did we resume?" check that cannot tell a replay
     * from a real transition fires spuriously. `foreground` is compared
     * before acting, so a replayed ON_START while we already believe we are
     * foregrounded does nothing at all.
     */
    private fun installProcessObserver() {
        if (processObserverInstalled) return
        processObserverInstalled = true
        val owner = ProcessLifecycleOwner.get()
        // addObserver must happen on the main thread.
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            owner.lifecycle.addObserver(
                LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> onForeground()
                        Lifecycle.Event.ON_STOP -> onBackground()
                        else -> Unit
                    }
                },
            )
        }
    }

    private fun onForeground() {
        synchronized(this) {
            if (foreground) return          // a replay, not a transition
            foreground = true
        }
        Log.i(TAG, "[RT] app foregrounded")
        if (orderingAccess) startPresence()
        // The live line comes back through acquire() when an Orders surface
        // resumes — but if one is still held (the app was backgrounded with
        // Orders on screen and came back inside the linger), revive it.
        synchronized(this) { if (holders > 0) startLive() }
    }

    /**
     * Pocketed. Start the same 60-second timer for BOTH lines. Cross the
     * minute and everything closes: no socket, no jobs, no battery, no
     * connection held against the 200-connection ceiling. Come back inside
     * it and nothing happened at all.
     */
    private fun onBackground() {
        synchronized(this) {
            if (!foreground) return
            foreground = false
        }
        Log.i(TAG, "[RT] app backgrounded — both lines close in ${LINGER_MS / 1000}s")
        synchronized(this) {
            scheduleLiveStop(LINGER_MS)
            schedulePresenceStop(LINGER_MS)
        }
    }

    // ---------------- the presence line ----------------

    @Synchronized
    private fun startPresence() {
        presenceStopToken++            // invalidate any pending stop
        presenceStopJob?.cancel()
        presenceStopJob = null
        if (presenceJob?.isActive == true) return   // already held — reuse it
        Log.i(TAG, "[RT] presence line up")
        presenceJob = scope.launch {
            runLine("presence", isLive = false) { runPresenceChannel(it) }
        }
    }

    @Synchronized
    private fun schedulePresenceStop(delayMs: Long) {
        if (presenceJob == null) return
        presenceStopJob?.cancel()
        val token = ++presenceStopToken
        presenceStopJob = scope.launch {
            delay(delayMs)
            stopPresenceIfToken(token)
        }
    }

    @Synchronized
    private fun stopPresenceIfToken(token: Int) {
        if (token != presenceStopToken) return   // rebuilt since; this stop is stale
        stopPresenceNow()
    }

    @Synchronized
    private fun stopPresenceNow() {
        presenceStopToken++
        presenceStopJob?.cancel(); presenceStopJob = null
        if (presenceJob == null) return
        Log.i(TAG, "[RT] presence line down")
        presenceJob?.cancel(); presenceJob = null
        synchronized(present) { present.clear() }
    }

    // ---------------- the live line ----------------

    @Synchronized
    private fun startLive() {
        liveStopToken++
        liveStopJob?.cancel()
        liveStopJob = null
        if (liveJob?.isActive == true) {
            // THE LINGER SAVED IT. Nothing was missed, so there is
            // deliberately no catch-up read here — that is the whole saving.
            return
        }
        if (!foreground || !orderingAccess) return
        Log.i(TAG, "[RT] live line up")
        liveJob = scope.launch { runLine("live", isLive = true) { runLiveChannel(it) } }
        startSurfaceJobs()
    }

    @Synchronized
    private fun scheduleLiveStop(delayMs: Long) {
        if (liveJob == null) return
        liveStopJob?.cancel()
        val token = ++liveStopToken
        liveStopJob = scope.launch {
            delay(delayMs)
            stopLiveIfToken(token)
        }
    }

    @Synchronized
    private fun stopLiveIfToken(token: Int) {
        if (token != liveStopToken) return
        stopLiveNow()
    }

    @Synchronized
    private fun stopLiveNow() {
        liveStopToken++
        liveStopJob?.cancel(); liveStopJob = null
        if (liveJob == null) return
        Log.i(TAG, "[RT] live line down")
        liveJob?.cancel(); liveJob = null
        stopSurfaceJobs()
        _socketUp.value = false
    }

    // ---------------- one line's retry loop ----------------

    /**
     * The room/network watcher and the reconnect backoff, shared by both
     * lines. Each line runs its own copy, so a failure on one cannot take
     * the other down — the counter keeps seeing the phone even while the
     * order channel is retrying.
     */
    private suspend fun runLine(
        name: String,
        isLive: Boolean,
        body: suspend (String) -> Nothing,
    ) {
        try {
            combine(repo.roomId, network.online) { room, online ->
                if (online) room else null
            }.collectLatest { room ->
                if (room == null) {
                    if (isLive) markDown()
                    return@collectLatest
                }
                var backoff = BACKOFF_START_MS
                // Not a CoroutineScope receiver here (collectLatest's block is
                // a plain suspend lambda), so ask the context directly.
                while (currentCoroutineContext().isActive) {
                    val startedAt = System.currentTimeMillis()
                    try {
                        body(room) // returns only by throwing/cancel
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "[RT] $name failed: ${e::class.simpleName}: ${e.message}")
                    }
                    if (isLive) markDown()
                    // The backoff resets only after a subscription that
                    // actually HELD — never on the subscribe event itself.
                    // That is what stopped the counter's 2-second reconnect
                    // loop, and the same rule applies here.
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
            // Anything outside the per-attempt retry (the room/network flow
            // itself). The Orders tab must NOT go dead: the fallback job is
            // separate and keeps a refresh path alive.
            Log.e(TAG, "[RT] $name supervisor died — falling back to slow reads", e)
            if (isLive) markDown()
        }
    }

    // ---------------- jobs that live and die with the live line ----------------

    private fun startSurfaceJobs() {
        // 5.2 — the safety net. Deliberately independent of the socket job,
        // and deliberately idle while the socket is healthy.
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

        // §4.4 — THE COUNTER'S STATUS, EVENT-DRIVEN, NO SCHEDULES.
        //
        // There is no tick here. A successful check is trusted for five
        // minutes; this coroutine sleeps for exactly however long is left of
        // that, checks ONCE, and sleeps again. The wait is anchored to the
        // last real answer, not to a clock, so a bell — which renews our
        // knowledge for free — pushes the next check out without any work.
        statusJob?.cancel()
        statusJob = scope.launch {
            var failures = 0
            while (isActive) {
                val due = repo.msUntilStatusCheckDue()
                if (due > 0) {
                    delay(due)
                    continue
                }
                // A revoked staff member throws out of here. That is handled
                // elsewhere (the app signs them out); this loop must not be
                // the thing that dies of it, or the phone silently stops
                // asking about the counter for the rest of the session.
                val ok = try {
                    repo.checkCounterStatus(minGapMs = 0)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "[RT] counter status check failed: ${e.message}")
                    false
                }
                if (ok) {
                    failures = 0
                } else {
                    failures++
                    delay(retryDelayMs(failures))
                }
            }
        }

        // Connectivity coming back is a real event, so it is a trigger — and
        // it is what lets a phone taken off airplane mode recover on its own
        // with no user action and no app restart.
        networkJob?.cancel()
        networkJob = scope.launch {
            var wasOnline = network.online.value
            network.online.collect { up ->
                if (up && !wasOnline) repo.onConnectivityRestored()
                wasOnline = up
            }
        }
    }

    private fun stopSurfaceJobs() {
        fallbackJob?.cancel(); fallbackJob = null
        statusJob?.cancel(); statusJob = null
        networkJob?.cancel(); networkJob = null
    }

    /** 30s, 1m, 2m, 4m, then capped at the trust window itself. */
    private fun retryDelayMs(failures: Int): Long =
        (RETRY_BASE_MS shl (failures - 1).coerceIn(0, 4)).coerceAtMost(RETRY_MAX_MS)

    /**
     * The live socket is down. That is all this means.
     *
     * It used to fire a counter-status re-check as well, and a socket that
     * keeps failing calls it once per retry — which is how a phone log ended
     * up with twenty "presence-recheck failed" lines in a row, each one
     * spending allowance the device did not have. A dropped socket tells us
     * nothing about the counter; there is nothing to go and ask.
     */
    private fun markDown() {
        _socketUp.value = false
    }

    // ---------------- channel plumbing ----------------

    /**
     * The presence channel. Joins the room and stays suspended for the life
     * of the connection. Nothing is ever broadcast here — once subscribed it
     * costs nothing until somebody joins or leaves, which is exactly why it
     * can be held for the whole foreground session.
     */
    private suspend fun runPresenceChannel(roomId: String): Nothing = coroutineScope {
        // The credential must be current before the socket authenticates,
        // or the server closes the join on a private topic.
        cloud.ensureEnrolled()
        val installId = prefs.installId()

        val presenceChannel = supabase.channel("orders-$roomId") {
            isPrivate = true
            presence { key = "mob:$installId" }
        }
        try {
            launch {
                presenceChannel.presenceChangeFlow().collect { action -> onPresence(action) }
            }
            // Presence is announced from the PRESENCE channel's own status,
            // not the live channel's. They subscribe independently, and
            // tracking on a channel that has not finished subscribing is
            // silently dropped — which is exactly how the counter ended up
            // reading "0 phones" while a phone sat on the Orders tab.
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

            withTimeoutOrNull(SUBSCRIBE_TIMEOUT_MS) {
                presenceChannel.subscribe(blockUntilSubscribed = true)
            } ?: throw IllegalStateException("presence subscribe timeout")

            awaitCancellation()
        } finally {
            withContext(NonCancellable) {
                runCatching { supabase.realtime.removeChannel(presenceChannel) }
                synchronized(present) { present.clear() }
            }
        }
    }

    /**
     * The order-truth channel. Every order change is one billable message
     * per subscribed phone, so this is the expensive one and the one with
     * the short lifetime.
     */
    private suspend fun runLiveChannel(roomId: String): Nothing = coroutineScope {
        cloud.ensureEnrolled()

        val liveChannel = supabase.channel("orders-$roomId-live") {
            isPrivate = true
        }
        try {
            // Register the flow BEFORE subscribing so no early message is missed.
            launch {
                liveChannel.broadcastFlow<JsonObject>(event = "mb").collect { msg ->
                    repo.onBell(parseBell(msg))
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
                        // about missed data, not a reaction to the socket.
                        //
                        // With the 60-second linger this now fires roughly
                        // once per shift instead of once per navigation: if
                        // the linger saved the channel there is no
                        // channel-up event, because nothing was ever down.
                        runCatching { repo.refreshOrders(); repo.resolveOpenEvents() }
                    }
                    if (!up && wasUp) Log.i(TAG, "[RT] channel down")
                    wasUp = up
                }
            }

            withTimeoutOrNull(SUBSCRIBE_TIMEOUT_MS) {
                liveChannel.subscribe(blockUntilSubscribed = true)
            } ?: throw IllegalStateException("live subscribe timeout")

            awaitCancellation()
        } finally {
            withContext(NonCancellable) {
                runCatching { supabase.realtime.removeChannel(liveChannel) }
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

    /**
     * Presence changed in the room. Presence is only ever a TRIGGER TO GO AND
     * ASK — never the answer. A process that is killed does not send a leave,
     * so a stale `pos:` entry will sit there asserting a counter that is not
     * running; that is exactly how a phone claimed "Counter online" for four
     * minutes after the till had been killed.
     *
     * We only react when the COUNTER itself comes or goes. Phones joining and
     * leaving say nothing about the till, and reacting to them would turn a
     * busy restaurant into a source of pointless reads.
     */
    private fun onPresence(action: PresenceAction) {
        val counterChanged = (action.joins.keys + action.leaves.keys).any { it.startsWith("pos:") }
        synchronized(present) {
            action.leaves.keys.forEach { present.remove(it) }
            present.addAll(action.joins.keys)
        }
        if (counterChanged) repo.onCounterPresenceChanged()
    }

    companion object {
        private const val TAG = "MB/Orders"
        private const val BACKOFF_START_MS = 1_000L
        private const val BACKOFF_MAX_MS = 30_000L

        /**
         * PART C. How long a line is held after the last thing that wanted
         * it went away.
         *
         * Sixty seconds is chosen against real behaviour, not tuned: it
         * covers every navigation (instant), a table being built (seconds),
         * a glance at another tab (seconds) and a phone pocketed between
         * tables (tens of seconds) — while a phone genuinely put away for a
         * few minutes still closes everything and costs nothing. Shorter and
         * the pocketed-phone case starts paying for rejoins again; longer
         * and a phone in a drawer holds a connection against the 200-
         * connection ceiling for no reason.
         */
        private const val LINGER_MS = 60_000L

        /** How long a subscription must hold before we trust it (rule R2). */
        private const val STABLE_MS = 30_000L
        private const val SUBSCRIBE_TIMEOUT_MS = 10_000L

        /** 5.2 — the fallback arms only after this much continuous downtime. */
        private const val FALLBACK_ARM_AFTER_MS = 30_000L
        private const val FALLBACK_TICK_MS = 45_000L

        /**
         * Backoff for a counter-status check that could not reach Magic Bill.
         * This is failure recovery, not a schedule: one attempt succeeds and
         * the loop goes back to sleeping out the five-minute trust window.
         */
        private const val RETRY_BASE_MS = 30_000L
        private const val RETRY_MAX_MS = 5 * 60_000L
    }
}
