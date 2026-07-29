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
     * The counter's liveness, held as an AGE plus the moment we learnt it.
     *
     * `posSeenAgeMs` is how stale the counter was when the server told us;
     * `posSeenLearntAt` is this device's own elapsed-time reading at that
     * moment. Adding the two gives the counter's current staleness with no
     * further reads, no polling, and no dependence on the two machines'
     * clocks agreeing.
     *
     * It is deliberately NOT a boolean. A cached "online" cannot decay, and
     * that is exactly what went wrong: the counter was killed, no presence
     * leave ever arrived, and the phone showed "Counter online" for four
     * minutes after the server had stopped accepting orders.
     */
    private var posSeenAgeMs: Long? = null
    private var posSeenLearntAt = 0L
    private var posLiveWindowMs = 150_000L

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

    /** One tenant_info row: gate, permissions, versions, counter liveness. */
    private suspend fun readTenantInfo(): WireTenantInfo? {
        cloud.ensureEnrolled()
        val rows = supabase.postgrest.from("tenant_info").select().decodeList<WireTenantInfo>()
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

        posLiveWindowMs = info.posLiveWindowSeconds * 1000
        posSeenAgeMs = info.posSecondsSinceSeen?.times(1000)
        posSeenLearntAt = SystemClock.elapsedRealtime()
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
        _state.value = _state.value.copy(gate = gate, posOnline = effectivePosOnline())
        return info
    }

    private suspend fun readOpenOrders(): List<WireOrder> =
        supabase.postgrest.from("live_orders")
            .select {
                filter { isIn("status", listOf("queued", "placed")) }
                order("created_at", Order.DESCENDING)
                limit(500)
            }
            .decodeList<WireLiveOrderRow>()
            .map { it.toWire(json) }

    private suspend fun readCatalog(): Pair<WireCatalog, List<WireCustomer>> {
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
        return WireCatalog(categories, items, tables) to customers
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
                posSeenAgeMs = null
                posSeenLearntAt = 0L
                _roomId.value = null
            }
            loadedScope = c.scopeKey

            if (!force && _state.value.data != null) return

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
                posOnline = effectivePosOnline(),
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
                // it, so it is proof the counter was alive a moment ago.
                noteCounterAlive()
                _state.value = _state.value.copy(posOnline = effectivePosOnline())
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

    /**
     * Presence changed on the realtime channel (null when the socket is
     * down). Presence is now only ever a TRIGGER TO GO AND ASK — it is never
     * the answer. Treating it as the answer is what let the phone claim
     * "Counter online" for four minutes after the counter had been killed:
     * a process that dies does not send a presence leave, so the stale entry
     * sat there asserting a counter that was not there.
     */
    fun setPresencePosOnline(online: Boolean?) {
        scope.launch {
            runCatching { readTenantInfo() }
                .onFailure { logFailure("presence-recheck", it) }
            _state.value = _state.value.copy(posOnline = effectivePosOnline())
        }
    }

    /**
     * Is the counter up? Computed exactly the way the server computes it, so
     * the badge and the server can never disagree: the counter's staleness
     * when we last asked, plus however long ago that was, against the
     * server's own window.
     *
     * Getting this wrong in either direction is a real failure. Saying
     * "offline" while the server would still accept refuses a waiter for no
     * reason; saying "online" after the server has given up lets a waiter
     * build a whole order and only discover it when Send fails.
     */
    private fun effectivePosOnline(): Boolean {
        val age = posSeenAgeMs ?: return false
        val sinceWeAsked = SystemClock.elapsedRealtime() - posSeenLearntAt
        return age + sinceWeAsked < posLiveWindowMs
    }

    /**
     * A bell is proof the counter was alive when it wrote. Order truth and
     * catalog bumps are only ever authored by the counter, so receiving one
     * resets the staleness clock without a read.
     */
    private fun noteCounterAlive() {
        posSeenAgeMs = 0
        posSeenLearntAt = SystemClock.elapsedRealtime()
    }

    /**
     * Re-evaluate the counter badge from what we already know. Costs
     * nothing — no network, no database — and exists because the badge
     * decays with time: when a counter dies there is no read, no bell and
     * no presence event to trigger a recalculation, so without a tick the
     * screen would keep showing whatever it last decided. That is exactly
     * how a killed counter went on reading "Counter online" for four
     * minutes. Driven by OrdersRealtime while an Orders surface is visible.
     */
    fun tickPosOnline() {
        val now = effectivePosOnline()
        if (_state.value.posOnline != now) {
            _state.value = _state.value.copy(posOnline = now)
        }
    }

    /**
     * Single-order lookup — used by the detail screen to learn an order's
     * final status once it has left the open set.
     */
    suspend fun fetchOrderStatus(serverId: String): String? = try {
        cloud.ensureEnrolled()
        supabase.postgrest.from("live_orders")
            .select { filter { eq("id", serverId) } }
            .decodeList<WireLiveOrderRow>()
            .firstOrNull()?.status
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
            // essential: the waiter just tapped Send. If this device is not
            // enrolled yet, enrolling for it may exceed the hourly guard.
            cloud.ensureEnrolled(essential = true)
            supabase.postgrest.rpc(
                "mb_submit_event",
                buildJsonObject {
                    put("p_client_event_id", clientEventId)
                    put("p_kind", kind)
                    put("p_payload", payload)
                    orderClientUuid?.let { put("p_order_client_uuid", it) }
                },
            ).decodeAs<JsonObject>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: RevokedException) {
            throw e
        } catch (e: Exception) {
            markEvent(clientEventId, "failed", reason = null)
            return SubmitResult.Failed(MBErrors.network(e))
        }

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
            cloud.ensureEnrolled()
            supabase.postgrest.from("order_events")
                .select {
                    filter { isIn("id", open.mapNotNull { it.serverEventId }) }
                }
                .decodeList<WireEventStatusRow>()
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
            cloud.ensureEnrolled()
            supabase.postgrest.rpc("mb_touch_install", buildJsonObject { put("p_label", "") })
        }
    }

    /** 5.4 — the owner-facing usage readout. */
    fun usage(): OrdersCloud.Usage = cloud.usage()

    // ---------------- helpers ----------------

    private suspend fun emitFromLocal(c: Caller) {
        val data = readLocal(c) ?: return
        _state.value = _state.value.copy(
            data = data,
            posOnline = effectivePosOnline(),
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

    private fun onRefreshFailed(what: String, e: Exception) {
        Log.w(TAG, "[NET] $what refresh failed: ${e::class.simpleName}: ${e.message}")
        val s = _state.value
        _state.value = s.copy(
            refreshing = false,
            error = if (s.data == null) MBErrors.network(e) else null,
        )
    }

    private fun logFailure(what: String, e: Throwable) {
        if (e is CancellationException) return
        Log.w(TAG, "[ORDERS] $what failed: ${e::class.simpleName}: ${e.message}")
    }

    companion object {
        private const val TAG = "MB/Orders"
        private const val EVENT_RETENTION_MS = 7L * 24 * 60 * 60 * 1000

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
