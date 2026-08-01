package com.magicbill.app.data.orders

import android.os.SystemClock
import android.util.Log
import com.magicbill.app.core.MBErrors
import com.magicbill.app.core.PermissionMap
import com.magicbill.app.data.AuthRepository
import com.magicbill.app.data.MBSession
import com.magicbill.app.data.RevokedException
import com.magicbill.app.data.local.CreditCustomerEntity
import com.magicbill.app.data.local.LiveOrderEntity
import com.magicbill.app.data.local.MenuCategoryEntity
import com.magicbill.app.data.local.MenuItemEntity
import com.magicbill.app.data.local.OrdersLocalDao
import com.magicbill.app.data.local.OrdersSyncStateEntity
import com.magicbill.app.data.local.PendingEventEntity
import com.magicbill.app.data.local.RestaurantTableEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The phone side of live mobile ordering.
 *
 * WHAT CHANGED IN THE REBUILD:
 *  - Nothing asks "anything new?" on a timer. The phone fetches only when
 *    the Orders tab opens, the user pulls to refresh, or a bell says
 *    something actually changed.
 *  - THE BELL CARRIES THE PAYLOAD. A changed order arrives complete and is
 *    written straight into Room. Six phones cost ONE realtime message, not
 *    six Edge Function invocations.
 *  - Reads go straight to PostgREST under Row Level Security, which is not
 *    metered. The only Edge Function in the path is enrolment, once.
 *
 * Cache-first is unchanged: Room renders instantly and is never blocked
 * behind a spinner.
 */
@Singleton
class OrdersRepository @Inject constructor(
    private val auth: AuthRepository,
    private val cloud: OrdersCloud,
    private val supabase: SupabaseClient,
    private val dao: OrdersLocalDao,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loadMutex = Mutex()

    private val _state = MutableStateFlow(OrdersUiState())
    val state: StateFlow<OrdersUiState> = _state.asStateFlow()

    /** Caller's current permission map — every tenant_info read refreshes it. */
    private val _permissions = MutableStateFlow<PermissionMap>(emptyMap())
    val permissions: StateFlow<PermissionMap> = _permissions.asStateFlow()

    /** Realtime room id. Opaque capability — never log it. */
    private val _roomId = MutableStateFlow<String?>(null)
    val roomId: StateFlow<String?> = _roomId.asStateFlow()

    private var loadedScope: String? = null
    private var lastOrdersSeq = 0L
    private var lastCatalogVersion = -1L

    /**
     * WHAT WE KNOW ABOUT THE COUNTER, and exactly when we learnt it.
     *
     * `serverAgeMs` is how stale the counter was when the SERVER told us;
     * `learntAt` is this device's own elapsed-time reading at that moment.
     * Holding both means the knowledge can decay honestly, with no polling
     * and no dependence on the two machines' clocks agreeing.
     *
     * It is deliberately not a boolean. A cached "online" cannot decay, and
     * that is exactly what went wrong once already: the counter was killed,
     * no presence leave ever arrived, and the phone showed "Counter online"
     * for four minutes after the server had stopped accepting orders.
     */
    private data class PosReading(
        val serverAgeMs: Long,
        val windowMs: Long,
        val learntAt: Long,
    )

    @Volatile
    private var posReading: PosReading? = null

    /** The server's own window, so no number here has to be kept in step. */
    @Volatile
    private var posLiveWindowMs = DEFAULT_POS_WINDOW_MS

    /** True once a renewal has actually been attempted and failed. */
    @Volatile
    private var lastCheckFailed = false

    /** Guards against stacking status checks on a slow connection. */
    @Volatile
    private var checkInFlight = false

    // ---------------- caller identity ----------------

    private data class Caller(val scopeKey: String, val restaurantName: String)

    private suspend fun caller(): Caller? = when (val s = auth.session.value) {
        is MBSession.Staff -> auth.loadStaffSession()?.let {
            Caller("staff:${it.restaurant.code}", it.restaurant.name)
        }
        is MBSession.Owner -> Caller("owner:${s.active.licenseKey}", s.active.name)
        else -> null
    }

    // ---------------- transport ----------------

    /**
     * Runs one PostgREST call under this device's credential.
     *
     * On an enrolled phone `ensureEnrolled()` is two reads from encrypted
     * preferences — no network, no Edge call. The only thing that ever buys
     * a new credential is the server ITSELF saying no on a real call, which
     * is what the retry below reacts to. We never re-mint pre-emptively;
     * doing that on every app start is what caused this whole bug.
     */
    private suspend fun <T> underCredential(block: suspend () -> T): T {
        cloud.ensureEnrolled()
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!cloud.isCredentialRejection(e)) throw e
            Log.i(TAG, "[ENROL] the server rejected our credential — recovering once")
            cloud.recoverFromRejection()
            block()
        }
    }

    /** One tenant_info row: gate, permissions, versions, counter liveness. */
    private suspend fun readTenantInfo(): WireTenantInfo? {
        val rows = underCredential {
            supabase.postgrest.from("tenant_info").select().decodeList<WireTenantInfo>()
        }
        val info = rows.firstOrNull() ?: return null

        _permissions.value = info.permissions
        // Keep the stored staff permissions in step so the rest of the app
        // (menus, buttons) reflects an owner's edit immediately.
        if (auth.session.value is MBSession.Staff) {
            auth.loadStaffSession()?.let { stored ->
                if (stored.staff.permissions != info.permissions) {
                    auth.saveStaffSession(
                        stored.copy(staff = stored.staff.copy(permissions = info.permissions)),
                    )
                }
            }
        }

        // A completed read IS the counter-status check (§4.4). There is no
        // separate liveness request anywhere in the app.
        posLiveWindowMs = (info.posLiveWindowSeconds * 1000).takeIf { it > 0 }
            ?: DEFAULT_POS_WINDOW_MS
        posReading = PosReading(
            // A null age means the counter has NEVER checked in. That is a
            // real answer from the server, not a failure to get one — this
            // till has never come online — so it reads OFFLINE, not UNKNOWN.
            serverAgeMs = info.posSecondsSinceSeen?.times(1000) ?: NEVER_SEEN_MS,
            windowMs = posLiveWindowMs,
            learntAt = SystemClock.elapsedRealtime(),
        )
        lastCheckFailed = false
        _roomId.value = info.roomId

        val gate = when (info.gate) {
            "ordering-disabled" -> OrdersGate.OrderingDisabled
            "subscription" -> OrdersGate.Subscription
            "blocked" -> OrdersGate.Blocked
            "device-limit" -> OrdersGate.DeviceLimit
            else -> null
        }
        if (info.gate == "revoked") {
            auth.markStaffRevoked()
            throw RevokedException()
        }
        _state.value = _state.value.copy(gate = gate, posStatus = posStatus())
        return info
    }

    private suspend fun readOpenOrders(): List<WireOrder> = underCredential {
        supabase.postgrest.from("live_orders")
            .select {
                filter { isIn("status", listOf("queued", "placed")) }
                order("created_at", Order.DESCENDING)
                limit(500)
            }
            .decodeList<WireLiveOrderRow>()
            .map { it.toWire(json) }
    }

    private suspend fun readCatalog(): Pair<WireCatalog, List<WireCustomer>> = underCredential {
        val categories = supabase.postgrest.from("menu_categories")
            .select { order("sort_order", Order.ASCENDING) }
            .decodeList<WireCategoryRow>().map { it.toWire() }
        val items = supabase.postgrest.from("menu_items")
            .select { order("name", Order.ASCENDING) }
            .decodeList<WireMenuItemRow>().map { it.toWire() }
        val tables = supabase.postgrest.from("restaurant_tables")
            .select {
                order("section", Order.ASCENDING)
                order("sort_order", Order.ASCENDING)
            }
            .decodeList<WireTableRow>().map { it.toWire() }
        val customers = supabase.postgrest.from("credit_customers")
            .select { order("name", Order.ASCENDING) }
            .decodeList<WireCustomerRow>().map { it.toWire() }
        WireCatalog(categories, items, tables) to customers
    }

    // ---------------- load / refresh ----------------

    /**
     * Entry point for the Orders tab. Cache-first: emit the Room mirror for
     * this scope instantly, then a network bootstrap swaps fresh truth in.
     * A restaurant switch wipes the old scope and starts clean.
     */
    fun ensureLoaded(force: Boolean = false) {
        scope.launch { runCatching { load(force) }.onFailure { logFailure("load", it) } }
    }

    private suspend fun load(force: Boolean) {
        val c = caller() ?: return
        loadMutex.withLock {
            val previous = loadedScope
            if (previous != null && previous != c.scopeKey) {
                // Different restaurant — old data must never flash on screen,
                // and the credential belongs to the old one.
                runCatching { dao.clearScope(previous) }
                cloud.forget(signOut = false)
                _state.value = OrdersUiState(refreshing = true)
                lastOrdersSeq = 0L
                lastCatalogVersion = -1L
                posReading = null
                lastCheckFailed = false
                _roomId.value = null
            }
            loadedScope = c.scopeKey

            if (!force && _state.value.data != null) {
                // The orders are already in memory, so there is nothing to
                // re-download — but arriving on this screen is trigger (a)/(b)
                // for the counter's status, and this is where the old code
                // showed a waiter whatever it had decided minutes ago.
                //
                // PART C6: this used to be minGapMs = 0, so it was an RPC on
                // EVERY arrival — and a waiter arrives here dozens of times
                // an hour (every table tap comes straight back to this
                // screen). Measured at 50 of them in ten minutes. The
                // five-minute trust window already keeps the badge honest;
                // re-asking within the same minute cannot tell us anything
                // the last answer did not.
                checkCounterStatus(minGapMs = ARRIVAL_MIN_GAP_MS)
                return
            }

            // 1) Room mirror, instantly.
            val cached = readLocal(c)
            val sync = dao.syncState(c.scopeKey)
            if (cached != null) {
                lastOrdersSeq = sync?.ordersSeq ?: 0L
                lastCatalogVersion = sync?.catalogVersion ?: -1L
                _roomId.value = sync?.roomId?.takeIf { it.isNotEmpty() }
                _state.value = _state.value.copy(
                    data = cached,
                    updatedAt = sync?.lastSyncAt,
                    refreshing = true,
                )
            } else {
                _state.value = _state.value.copy(refreshing = true)
            }

            // 2) Network bootstrap.
            bootstrap(c)
        }
    }

    private suspend fun bootstrap(c: Caller) {
        val info = try {
            readTenantInfo()
        } catch (e: CancellationException) {
            throw e
        } catch (e: RevokedException) {
            throw e
        } catch (e: Exception) {
            onRefreshFailed("bootstrap", e)
            return
        }
        if (info == null) {
            _state.value = _state.value.copy(refreshing = false)
            return
        }
        if (info.gate.isNotEmpty()) {
            // Gated — the gate is already on screen; there is nothing to read.
            _state.value = _state.value.copy(refreshing = false)
            return
        }

        try {
            val orders = readOpenOrders()
            val now = System.currentTimeMillis()
            lastOrdersSeq = info.ordersSeq

            // The catalog is version-gated: an unchanged menu is never
            // re-downloaded. Catalog payloads are the main egress cost.
            if (info.catalogVersion != lastCatalogVersion) {
                val (catalog, customers) = readCatalog()
                dao.replaceCatalog(
                    c.scopeKey,
                    catalog.categories.map { it.toEntity(c.scopeKey) },
                    catalog.items.map { it.toEntity(c.scopeKey) },
                    catalog.tables.map { it.toEntity(c.scopeKey) },
                    customers.map { it.toEntity(c.scopeKey) },
                )
                lastCatalogVersion = info.catalogVersion
            }

            dao.replaceOrders(c.scopeKey, orders.map { it.toEntity(c.scopeKey, json) })
            val name = info.restaurantName.ifEmpty { c.restaurantName }
            dao.putSyncState(
                OrdersSyncStateEntity(
                    scope = c.scopeKey,
                    roomId = info.roomId,
                    catalogVersion = lastCatalogVersion,
                    ordersSeq = lastOrdersSeq,
                    lastSyncAt = now,
                    restaurantName = name,
                ),
            )
            dao.pruneEventsBefore(now - EVENT_RETENTION_MS)

            _state.value = OrdersUiState(
                data = readLocal(c, restaurantName = name),
                gate = null,
                posStatus = posStatus(),
                refreshing = false,
                updatedAt = now,
            )
            Log.i(TAG, "[SYNC] bootstrap ok: ${orders.size} open orders, catalog v$lastCatalogVersion")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onRefreshFailed("bootstrap", e)
        }
    }

    /**
     * Make the cached room id available WITHOUT a network call.
     *
     * PART C1 holds the presence line for the whole foreground session, but
     * the line cannot join a room it does not know, and `roomId` used to be
     * published only once the Orders tab had loaded. So a phone sitting on
     * Home was invisible to the counter until a waiter opened Orders — which
     * is the very flicker the change was meant to remove. Caught on hardware.
     *
     * Reads one row from Room. A phone that has never opened Orders has no
     * cached room and legitimately cannot announce itself yet.
     */
    suspend fun primeRoomId() {
        if (_roomId.value != null) return
        val c = caller() ?: return
        val room = dao.syncState(c.scopeKey)?.roomId?.takeIf { it.isNotEmpty() } ?: return
        _roomId.value = room
    }

    /**
     * A full re-read of the open set. Used on tab open, pull-to-refresh, and
     * as the ONE catch-up read after a detected sequence gap — never on a
     * timer.
     */
    suspend fun refreshOrders() {
        val c = caller() ?: return
        try {
            val info = readTenantInfo() ?: return
            if (info.gate.isNotEmpty()) return
            val orders = readOpenOrders()
            lastOrdersSeq = info.ordersSeq
            dao.replaceOrders(c.scopeKey, orders.map { it.toEntity(c.scopeKey, json) })
            touchSyncState(c) {
                it.copy(ordersSeq = lastOrdersSeq, lastSyncAt = System.currentTimeMillis())
            }
            emitFromLocal(c)
            Log.i(TAG, "[SYNC] re-read the open set: ${orders.size} orders")
        } catch (e: CancellationException) {
            throw e
        } catch (e: RevokedException) {
            throw e
        } catch (e: Exception) {
            onRefreshFailed("orders", e)
        }
    }

    /** Catalog refresh — skipped entirely when our version is already current. */
    suspend fun refreshCatalog(catalogVersion: Long? = null) {
        val c = caller() ?: return
        try {
            val version = catalogVersion ?: readTenantInfo()?.catalogVersion ?: return
            if (version == lastCatalogVersion) return
            val (catalog, customers) = readCatalog()
            dao.replaceCatalog(
                c.scopeKey,
                catalog.categories.map { it.toEntity(c.scopeKey) },
                catalog.items.map { it.toEntity(c.scopeKey) },
                catalog.tables.map { it.toEntity(c.scopeKey) },
                customers.map { it.toEntity(c.scopeKey) },
            )
            lastCatalogVersion = version
            touchSyncState(c) { it.copy(catalogVersion = version) }
            emitFromLocal(c)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onRefreshFailed("catalog", e)
        }
    }

    // ---------------- the bell ----------------

    /**
     * A bell carrying the changed row. THIS IS THE WHOLE POINT OF THE
     * REBUILD: the payload is applied straight into Room with no fetch, so
     * one message updates every phone in the restaurant.
     *
     * A detected sequence gap triggers exactly ONE catch-up read, never a
     * storm.
     */
    fun onBell(bell: OrdersBell) {
        scope.launch {
            runCatching {
                val c = caller() ?: return@runCatching
                // Whatever this bell says, only the counter could have sent
                // it, so it is proof the counter was alive a moment ago —
                // trigger (d), and the only one that costs nothing at all.
                noteCounterAlive()
                _state.value = _state.value.copy(posStatus = posStatus())
                when (bell.kind) {
                    "order" -> {
                        // The counter's ack rides on the same message as the
                        // order it produced, so a waiter's "Sending…"
                        // resolves at the moment the order changes —
                        // including when the counter REJECTED it, which is
                        // what used to strand people.
                        bell.event?.let { applyEventStatus(c, it) }

                        if (bell.seq > 0 && lastOrdersSeq > 0 && bell.seq > lastOrdersSeq + 1) {
                            // We missed something — one catch-up read, then stop.
                            Log.i(TAG, "[BELL] sequence gap, one catch-up read")
                            refreshOrders()
                            return@runCatching
                        }
                        if (bell.seq > lastOrdersSeq) lastOrdersSeq = bell.seq
                        bell.order?.let { applyOrder(c, it) }
                    }
                    "catalog" -> refreshCatalog(bell.seq)
                    // 'event' bells go to the counter's own topic, not ours.
                }
            }.onFailure { logFailure("bell", it) }
        }
    }

    /** Resolve one in-flight intent from the bell. No network. */
    private suspend fun applyEventStatus(c: Caller, e: JsonObject) {
        val serverId = e["eventId"]?.jsonPrimitive?.content ?: return
        val status = e["status"]?.jsonPrimitive?.content ?: return
        val reason = e["rejectReason"]?.jsonPrimitive?.contentOrNullSafe()
        dao.openEvents(c.scopeKey)
            .firstOrNull { it.serverEventId == serverId }
            ?.let { dao.putEvent(it.copy(status = status, rejectReason = reason)) }
    }

    /** Write one broadcast order into Room and re-emit. No network. */
    private suspend fun applyOrder(c: Caller, order: WireOrder) {
        if (order.isOpen) {
            dao.putOrder(order.toEntity(c.scopeKey, json))
        } else {
            // billed or cancelled — it leaves the open set
            dao.deleteOrder(c.scopeKey, order.clientUuid)
        }
        touchSyncState(c) {
            it.copy(ordersSeq = lastOrdersSeq, lastSyncAt = System.currentTimeMillis())
        }
        emitFromLocal(c)
    }

    // ---------------- is the counter alive? (§4.3, §4.4) ----------------

    /**
     * The three states, and how each one is reached.
     *
     *   ONLINE   a check came back saying the counter is inside the server's
     *            window, or a bell arrived (only the counter authors those).
     *   OFFLINE  a check came back saying it is NOT. Server-confirmed. We
     *            never arrive here by assumption.
     *   UNKNOWN  we could not check: never checked, or our reading has run
     *            out and renewing it failed.
     *
     * The distinction is the whole point. "I could not check" used to be
     * rendered as "Counter offline", which told a waiter the owner's till was
     * dead when the truth was that the PHONE could not ask. That is the bug
     * that put "Counter offline" on two phones for twenty minutes while the
     * counter sat there working perfectly.
     */
    private fun posStatus(): PosStatus {
        val r = posReading ?: return PosStatus.Unknown
        val heldFor = SystemClock.elapsedRealtime() - r.learntAt
        val verdict =
            if (r.serverAgeMs < r.windowMs) PosStatus.Online else PosStatus.Offline

        // Inside the trust window the reading stands, whatever happens. That
        // is the deal: one check, then five minutes of no requests at all.
        if (heldFor < TRUST_MS) return verdict

        // Past it, the reading is living on borrowed time. It stands until a
        // renewal has actually been tried and failed, or until it is simply
        // too old to stand behind — and then we say we do not know, rather
        // than inventing an answer in either direction.
        if (lastCheckFailed || heldFor > TRUST_MS + TRUST_GRACE_MS) return PosStatus.Unknown
        return verdict
    }

    /**
     * A bell is proof the counter was alive when it wrote. Order truth and
     * catalog bumps are only ever authored by the counter, so receiving one
     * renews our knowledge for free — no request, no round trip. In a busy
     * restaurant this means the phone never asks about the counter at all.
     */
    private fun noteCounterAlive() {
        posReading = PosReading(0, posLiveWindowMs, SystemClock.elapsedRealtime())
        lastCheckFailed = false
    }

    /**
     * Submitting an intent runs the server's own counter-is-alive gate, so
     * the reply is a free and perfectly current reading — better than
     * anything we could have gone and asked for. An accepted intent means
     * the counter was alive; a `pos-offline` refusal means it was not.
     */
    private fun noteCounterFromSubmit(reply: JsonObject) {
        val ok = reply["ok"]?.jsonPrimitive?.booleanOrNull == true
        when {
            ok -> noteCounterAlive()
            reply["reason"]?.jsonPrimitive?.content == "pos-offline" -> {
                posReading = PosReading(
                    serverAgeMs = posLiveWindowMs,
                    windowMs = posLiveWindowMs,
                    learntAt = SystemClock.elapsedRealtime(),
                )
                lastCheckFailed = false
            }
        }
        _state.value = _state.value.copy(posStatus = posStatus())
    }

    /**
     * The ONE way the counter's status is checked. One `tenant_info` row over
     * PostgREST — unmetered, a few hundred bytes — and it also refreshes the
     * gate, the permissions and the catalog version, so it is never a request
     * made for the badge alone.
     *
     * There is no timer behind this. It runs on events only (§4.4): the app
     * coming to the foreground, an Orders page opening, a pull to refresh,
     * the counter joining the room, connectivity returning, and the single
     * catch-up when a trusted reading expires while a surface is visible.
     *
     * @param minGapMs skip if a reading this fresh is already in hand. Used
     * by the triggers we do not control the rate of, so a flapping socket can
     * never turn an event into a poll.
     * @return true if a reading was obtained.
     */
    suspend fun checkCounterStatus(minGapMs: Long): Boolean {
        // Say what we honestly know BEFORE going to ask. A reading can have
        // run out while the screen was away, and a slow connection would
        // otherwise leave a stale "Counter online" on a waiter's screen for
        // as long as the request takes. Free — no network, no database.
        _state.value = _state.value.copy(posStatus = posStatus())

        val r = posReading
        if (minGapMs > 0 && r != null &&
            SystemClock.elapsedRealtime() - r.learntAt < minGapMs
        ) {
            return true
        }
        if (checkInFlight) return false
        checkInFlight = true
        return try {
            readTenantInfo()
            val status = posStatus()
            _state.value = _state.value.copy(posStatus = status)
            // The one line that makes this whole model auditable from a phone
            // log: what we asked, what came back, and how long it stands for.
            Log.i(
                TAG,
                "[STATUS] counter=$status " +
                    "(server last saw it ${(posReading?.serverAgeMs ?: 0) / 1000}s ago) " +
                    "— trusting for ${TRUST_MS / 60_000} min",
            )
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: RevokedException) {
            throw e
        } catch (e: Exception) {
            // We asked and got nothing back. Say so — do NOT turn a phone's
            // failure into an accusation against the counter.
            lastCheckFailed = true
            _state.value = _state.value.copy(posStatus = posStatus())
            logFailure("counter status", e)
            false
        } finally {
            checkInFlight = false
        }
    }

    /**
     * How long the current reading may still be trusted, in milliseconds.
     * OrdersRealtime waits exactly this long and then checks once — which is
     * why there is no fixed tick anywhere: the wait is anchored to the last
     * real answer, not to a clock.
     */
    fun msUntilStatusCheckDue(): Long {
        val r = posReading ?: return 0
        return (TRUST_MS - (SystemClock.elapsedRealtime() - r.learntAt)).coerceAtLeast(0)
    }

    /**
     * The counter appeared in — or vanished from — the presence room.
     * Presence is only ever a TRIGGER TO GO AND ASK, never the answer: a
     * process that is killed does not send a leave, so a stale entry will sit
     * there asserting a counter that is not running.
     *
     * This is what brings a phone back to "Counter online" seconds after the
     * till is restarted, with no user action and no app restart.
     */
    fun onCounterPresenceChanged() {
        scope.launch {
            runCatching { checkCounterStatus(minGapMs = PRESENCE_MIN_GAP_MS) }
                .onFailure { logFailure("presence check", it) }
        }
    }

    /** The phone got its connection back — ask again straight away (V4). */
    fun onConnectivityRestored() {
        scope.launch {
            runCatching { checkCounterStatus(minGapMs = 0) }
                .onFailure { logFailure("reconnect check", it) }
        }
    }

    /**
     * Single-order lookup — used by the detail screen to learn an order's
     * final status once it has left the open set.
     */
    suspend fun fetchOrderStatus(serverId: String): String? = try {
        underCredential {
            supabase.postgrest.from("live_orders")
                .select { filter { eq("id", serverId) } }
                .decodeList<WireLiveOrderRow>()
                .firstOrNull()?.status
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    // ---------------- intents ----------------

    suspend fun submitCreate(
        orderType: String,
        tableNumber: String,
        section: String,
        items: List<OrderLine>,
        existingClientEventId: String? = null,
        existingOrderClientUuid: String? = null,
    ): Pair<SubmitResult, String> {
        val orderClientUuid = existingOrderClientUuid ?: UUID.randomUUID().toString()
        val payload = buildJsonObject {
            put("orderType", orderType)
            put("tableNumber", tableNumber)
            put("section", section)
            put("items", json.encodeToJsonElement(ListSerializer(OrderLine.serializer()), items))
        }
        val result = submitEvent(
            "create", payload,
            orderClientUuid = orderClientUuid,
            existingClientEventId = existingClientEventId,
        )
        return result to orderClientUuid
    }

    suspend fun submitAddItems(
        orderClientUuid: String,
        items: List<OrderLine>,
        existingClientEventId: String? = null,
    ): SubmitResult = submitEvent(
        "add_items",
        buildJsonObject {
            put("items", json.encodeToJsonElement(ListSerializer(OrderLine.serializer()), items))
        },
        orderClientUuid = orderClientUuid,
        existingClientEventId = existingClientEventId,
    )

    suspend fun submitVoidItems(
        orderClientUuid: String,
        lines: List<Pair<Long, Int>>,
        reason: String,
    ): SubmitResult = submitEvent(
        "void_items",
        buildJsonObject {
            put(
                "items",
                json.encodeToJsonElement(
                    ListSerializer(VoidLine.serializer()),
                    lines.map { VoidLine(it.first, it.second) },
                ),
            )
            put("reason", reason)
        },
        orderClientUuid = orderClientUuid,
    )

    suspend fun submitFinalize(
        orderClientUuid: String,
        paymentMode: String,
        customerLocalId: Long? = null,
    ): SubmitResult = submitEvent(
        "finalize",
        buildJsonObject {
            put("paymentMode", paymentMode)
            customerLocalId?.let { put("customerLocalId", it) }
            put("printBill", true)
        },
        orderClientUuid = orderClientUuid,
    )

    suspend fun submitCancelOrder(orderClientUuid: String, reason: String): SubmitResult =
        submitEvent("cancel_order", buildJsonObject { put("reason", reason) }, orderClientUuid = orderClientUuid)

    suspend fun submitReprintKot(orderClientUuid: String): SubmitResult =
        submitEvent("reprint_kot", buildJsonObject {}, orderClientUuid = orderClientUuid)

    @kotlinx.serialization.Serializable
    private data class VoidLine(val localId: Long, val quantity: Int)

    /**
     * Submits one intent as a Postgres RPC — a PostgREST call, not an Edge
     * Function, so a waiter's order can never be blocked by the invocation
     * ceiling. The clientEventId makes retries safe: a duplicate returns the
     * existing event's state and never creates a second one.
     */
    private suspend fun submitEvent(
        kind: String,
        payload: JsonObject,
        orderClientUuid: String? = null,
        existingClientEventId: String? = null,
    ): SubmitResult {
        val c = caller() ?: return SubmitResult.Failed(MBErrors.SESSION_EXPIRED)
        val clientEventId = existingClientEventId ?: UUID.randomUUID().toString()
        dao.putEvent(
            PendingEventEntity(
                clientEventId = clientEventId,
                scope = c.scopeKey,
                kind = kind,
                orderClientUuid = orderClientUuid,
                payloadJson = payload.toString(),
                status = "sending",
                rejectReason = null,
                serverEventId = null,
                createdAt = System.currentTimeMillis(),
            ),
        )

        val reply = try {
            underCredential {
                supabase.postgrest.rpc(
                    "mb_submit_event",
                    buildJsonObject {
                        put("p_client_event_id", clientEventId)
                        put("p_kind", kind)
                        put("p_payload", payload)
                        orderClientUuid?.let { put("p_order_client_uuid", it) }
                    },
                ).decodeAs<JsonObject>()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RevokedException) {
            throw e
        } catch (e: Exception) {
            markEvent(clientEventId, "failed", reason = null)
            return SubmitResult.Failed(failureCopy(e))
        }

        // The server answered, which means the counter's liveness gate ran.
        // An accepted intent is therefore proof the counter was alive a
        // moment ago, and a `pos-offline` refusal is proof it was not —
        // both free, both more current than anything we could have read.
        noteCounterFromSubmit(reply)

        val ok = reply["ok"]?.jsonPrimitive?.booleanOrNull == true
        if (!ok) {
            val reason = reply["reason"]?.jsonPrimitive?.content
            val gate = when (reason) {
                "ordering-disabled" -> OrdersGate.OrderingDisabled
                "subscription" -> OrdersGate.Subscription
                "blocked" -> OrdersGate.Blocked
                "device-limit" -> OrdersGate.DeviceLimit
                else -> null
            }
            if (gate != null) _state.value = _state.value.copy(gate = gate)
            if (reason == "revoked") {
                auth.markStaffRevoked()
                throw RevokedException()
            }
            markEvent(clientEventId, "failed", reason = reason)
            return SubmitResult.Failed(reasonCopy(reason), gate)
        }

        reply["permissions"]?.let { element ->
            runCatching {
                _permissions.value = json.decodeFromJsonElement(
                    MapSerializer(String.serializer(), Boolean.serializer()),
                    element,
                )
            }
        }

        val status = reply["status"]?.jsonPrimitive?.content ?: "pending"
        val serverEventId = reply["eventId"]?.jsonPrimitive?.content
        val rejectReason = reply["rejectReason"]?.jsonPrimitive?.contentOrNullSafe()
        dao.event(clientEventId)?.let {
            dao.putEvent(
                it.copy(status = status, serverEventId = serverEventId, rejectReason = rejectReason),
            )
        }
        return if (status == "applied" || status == "rejected") {
            SubmitResult.AlreadyResolved(status, rejectReason)
        } else {
            SubmitResult.Accepted(clientEventId, serverEventId ?: "")
        }
    }

    /**
     * Waits for the counter to apply or reject an accepted intent.
     *
     * The bell resolves this — the counter's ack updates order_events, whose
     * trigger broadcasts the resolution, INCLUDING a rejection. The two
     * fallback checks below exist only so a lost bell can never strand a
     * waiter on "Sending…"; they replace the old 1.5-second loop that cost
     * up to ~16 calls per order.
     */
    suspend fun awaitResolution(clientEventId: String, timeoutMs: Long = 25_000): EventResolution {
        val started = System.currentTimeMillis()
        val fallbacks = longArrayOf(6_000, 15_000)
        var nextFallback = 0
        while (System.currentTimeMillis() - started < timeoutMs) {
            val ev = dao.event(clientEventId) ?: return EventResolution.Timeout
            when (ev.status) {
                "applied" -> return EventResolution.Applied
                "rejected" -> return EventResolution.Rejected(reasonCopy(ev.rejectReason))
            }
            val elapsed = System.currentTimeMillis() - started
            if (nextFallback < fallbacks.size && elapsed >= fallbacks[nextFallback]) {
                nextFallback++
                runCatching { resolveOpenEvents() }
            }
            delay(250)
        }
        return EventResolution.Timeout
    }

    /**
     * One unmetered read of everything still in flight. Called on tab open,
     * after a reconnect, and as awaitResolution's two fallbacks — never on a
     * timer.
     */
    suspend fun resolveOpenEvents() {
        val c = caller() ?: return
        val open = dao.openEvents(c.scopeKey).filter { it.serverEventId != null }
        if (open.isEmpty()) return
        val rows = try {
            underCredential {
                supabase.postgrest.from("order_events")
                    .select {
                        filter { isIn("id", open.mapNotNull { it.serverEventId }) }
                    }
                    .decodeList<WireEventStatusRow>()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return // transient; the bell or the next tab open tries again
        }
        val byServerId = open.associateBy { it.serverEventId }
        var resolvedAny = false
        rows.forEach { row ->
            val ev = byServerId[row.id] ?: return@forEach
            if (ev.status != row.status) {
                dao.putEvent(ev.copy(status = row.status, rejectReason = row.rejectReason))
                if (row.status == "applied" || row.status == "rejected") resolvedAny = true
            }
        }
        if (resolvedAny) runCatching { refreshOrders() }
    }

    /** The phone's own "I am here", so the counter's device list stays true. */
    suspend fun touchInstall() {
        runCatching {
            underCredential {
                supabase.postgrest.rpc("mb_touch_install", buildJsonObject { put("p_label", "") })
            }
        }
    }

    // ---------------- helpers ----------------

    private suspend fun emitFromLocal(c: Caller) {
        val data = readLocal(c) ?: return
        _state.value = _state.value.copy(
            data = data,
            posStatus = posStatus(),
            refreshing = false,
            error = null,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private suspend fun readLocal(c: Caller, restaurantName: String? = null): OrdersData? {
        val sync = dao.syncState(c.scopeKey)
        val categories = dao.categories(c.scopeKey)
        val orders = dao.orders(c.scopeKey)
        if (sync == null && categories.isEmpty() && orders.isEmpty()) return null
        return OrdersData(
            restaurantName = restaurantName
                ?: sync?.restaurantName?.takeIf { it.isNotEmpty() }
                ?: c.restaurantName,
            categories = categories.map { it.toModel() },
            items = dao.items(c.scopeKey).map { it.toModel() },
            tables = dao.tables(c.scopeKey).map { it.toModel() },
            customers = dao.customers(c.scopeKey).map { it.toModel() },
            orders = orders.map { it.toModel(json) },
        )
    }

    private suspend fun touchSyncState(
        c: Caller,
        mutate: (OrdersSyncStateEntity) -> OrdersSyncStateEntity,
    ) {
        val current = dao.syncState(c.scopeKey) ?: OrdersSyncStateEntity(
            scope = c.scopeKey, roomId = _roomId.value ?: "", catalogVersion = 0,
            ordersSeq = 0, lastSyncAt = 0, restaurantName = c.restaurantName,
        )
        dao.putSyncState(mutate(current))
    }

    private suspend fun markEvent(clientEventId: String, status: String, reason: String?) {
        dao.event(clientEventId)?.let { dao.putEvent(it.copy(status = status, rejectReason = reason)) }
    }

    /**
     * A refresh failed. The reading we already have on screen STAYS there —
     * cached orders are never replaced by an error, which is the single worst
     * part of what the staff phone did. `error` is set only when there is
     * genuinely nothing else to show.
     */
    private fun onRefreshFailed(what: String, e: Exception) {
        Log.w(TAG, "[NET] $what refresh failed: ${e::class.simpleName}: ${e.message}")
        lastCheckFailed = true
        val s = _state.value
        _state.value = s.copy(
            refreshing = false,
            posStatus = posStatus(),
            error = if (s.data == null) failureCopy(e) else null,
        )
    }

    /**
     * User-facing copy for a failure in the ordering path. It must never
     * blame the counter for something that happened on this phone.
     */
    private fun failureCopy(e: Throwable): String = when (e) {
        is OrdersCloud.BudgetExceeded ->
            "This phone has tried to register with Magic Bill far too many " +
                "times, so it has stopped for now. Your saved orders are still " +
                "here. If this keeps happening, sign out and sign in again."
        is OrdersCloud.NotEnrolled -> reasonCopy(e.reason)
        is Exception -> MBErrors.network(e)
        else -> MBErrors.UNKNOWN
    }

    private fun logFailure(what: String, e: Throwable) {
        if (e is CancellationException) return
        Log.w(TAG, "[ORDERS] $what failed: ${e::class.simpleName}: ${e.message}")
    }

    companion object {
        private const val TAG = "MB/Orders"
        private const val EVENT_RETENTION_MS = 7L * 24 * 60 * 60 * 1000

        /**
         * How long a successful check is trusted. Five minutes, matching
         * `mb_pos_live_window()` on the server (migration 0019) — they are
         * equal on purpose, so the badge and the server cannot disagree.
         *
         * In this window the phone makes NO requests about the counter at
         * all, whatever happens. That is the whole behaviour change: an idle
         * phone with the Orders tab open costs one small unmetered read every
         * five minutes, and a phone with the tab closed costs nothing.
         */
        private const val TRUST_MS = 5 * 60_000L

        /**
         * How long a reading may stand past its trust window while we are
         * still trying to renew it. Beyond this we simply do not know, and we
         * say so. Bounds how long a phone that was put down for hours can
         * show a verdict nobody has confirmed.
         */
        private const val TRUST_GRACE_MS = 5 * 60_000L

        /**
         * A presence event is a trigger, not an answer — but it is a trigger
         * whose rate we do not control, so it may cost at most one check a
         * minute. A flapping socket can never turn it into a poll.
         */
        private const val PRESENCE_MIN_GAP_MS = 60_000L

        /**
         * Arriving on an Orders surface is a trigger whose rate the waiter
         * controls, not us — every table tap comes back through here. One
         * check a minute is plenty against a five-minute trust window, and
         * it is the difference between ~50 RPCs in ten minutes and ~8.
         */
        private const val ARRIVAL_MIN_GAP_MS = 60_000L

        /**
         * Used only until the first reading arrives; the real number always
         * comes from `tenant_info.pos_live_window_seconds`, so this one can
         * never drift out of step with the server.
         */
        private const val DEFAULT_POS_WINDOW_MS = 300_000L

        /** "The counter has never checked in" — a real answer, and a bad one. */
        private const val NEVER_SEEN_MS = Long.MAX_VALUE / 4

        /** Friendly copy for every machine reason in the contract. */
        fun reasonCopy(reason: String?): String = when (reason) {
            "pos-offline" -> "Counter is offline — ask at the billing desk."
            "table-full" -> "That table already has the maximum number of sub-orders. Ask at the counter."
            "already-removed" -> "Those items were already removed at the counter."
            "order-gone" -> "This order was already closed at the counter."
            "empty-cart" -> "There's nothing to send."
            "no-printer" -> "Saved, but there's a printer problem at the counter."
            "forbidden" -> "You don't have permission to do that. Ask the owner."
            "subscription" -> "The restaurant's subscription has expired."
            "ordering-disabled" -> "Mobile ordering is switched off. The owner can enable it at the billing counter."
            "blocked" -> "This phone was blocked from taking orders. Ask the owner."
            "device-limit" -> "The plan's phone limit has been reached. Ask the owner to free up a device."
            "bad-request" -> MBErrors.UNKNOWN
            else -> MBErrors.SERVER_DOWN
        }
    }
}
