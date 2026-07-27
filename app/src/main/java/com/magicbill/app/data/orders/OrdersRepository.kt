package com.magicbill.app.data.orders

import android.os.Build
import android.util.Log
import com.magicbill.app.core.FriendlyException
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
import com.magicbill.app.data.prefs.SecurePrefs
import com.magicbill.app.data.remote.EdgeFunctions
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The phone side of live mobile ordering. Cache-first: Room renders
 * instantly, the `mobile-orders` Edge Function tops it up, and the realtime
 * doorbell (OrdersRealtime) triggers refreshes. The phone only ever submits
 * INTENTS — the POS is the authority for all order state.
 */
@Singleton
class OrdersRepository @Inject constructor(
    private val auth: AuthRepository,
    private val edge: EdgeFunctions,
    private val dao: OrdersLocalDao,
    private val prefs: SecurePrefs,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loadMutex = Mutex()

    private val _state = MutableStateFlow(OrdersUiState())
    val state: StateFlow<OrdersUiState> = _state.asStateFlow()

    /** Caller's current permission map — every server reply refreshes it. */
    private val _permissions = MutableStateFlow<PermissionMap>(emptyMap())
    val permissions: StateFlow<PermissionMap> = _permissions.asStateFlow()

    /** Realtime room id for OrdersRealtime. Opaque capability — never log. */
    private val _roomId = MutableStateFlow<String?>(null)
    val roomId: StateFlow<String?> = _roomId.asStateFlow()

    private var loadedScope: String? = null
    private var lastOrdersSeq = 0L
    private var lastCatalogVersion = -1L

    /** Presence verdict from the realtime channel; null = no verdict yet. */
    private var presencePosOnline: Boolean? = null

    /** Server's pos_last_seen_at freshness flag from the last reply. */
    private var serverPosOnline = false

    /** When that flag was last refreshed — it is only trusted while recent. */
    private var serverPosOnlineAt = 0L

    // ---------------- caller identity ----------------

    private data class Caller(
        val scopeKey: String,
        val staffToken: String?,
        val ownerJwt: String?,
        val licenseKey: String?,
        val restaurantName: String,
    )

    private suspend fun caller(): Caller? = when (val s = auth.session.value) {
        is MBSession.Staff -> auth.loadStaffSession()?.let {
            Caller(
                scopeKey = "staff:${it.restaurant.code}",
                staffToken = it.token,
                ownerJwt = null,
                licenseKey = null,
                restaurantName = it.restaurant.name,
            )
        }
        is MBSession.Owner -> auth.ownerAccessToken()?.let { jwt ->
            Caller(
                scopeKey = s.active.licenseKey,
                staffToken = null,
                ownerJwt = jwt,
                licenseKey = s.active.licenseKey,
                restaurantName = s.active.name,
            )
        }
        else -> null
    }

    // ---------------- transport ----------------

    /**
     * One round trip to `mobile-orders`. Side effects handled centrally:
     * permission refresh, gate mapping, revoked logout. Returns the reply
     * (ok or not) — callers branch on [MobileOrdersReply.ok].
     */
    private suspend fun call(
        c: Caller,
        build: JsonObjectBuilder.() -> Unit,
    ): MobileOrdersReply {
        val body = buildJsonObject {
            c.staffToken?.let { put("token", it) }
            c.licenseKey?.let { put("licenseKey", it) }
            put("installId", prefs.installId())
            put("deviceLabel", "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(120))
            build()
        }
        val raw = edge.call("mobile-orders", body, token = c.ownerJwt)
        val reply = json.decodeFromJsonElement(MobileOrdersReply.serializer(), raw)

        if (reply.reason == "revoked") {
            auth.markStaffRevoked()
            throw RevokedException()
        }

        // Keep permissions fresh everywhere (owner edits apply immediately).
        reply.permissions?.let { fresh ->
            _permissions.value = fresh
            if (c.staffToken != null) {
                auth.loadStaffSession()?.let { stored ->
                    if (stored.staff.permissions != fresh) {
                        auth.saveStaffSession(stored.copy(staff = stored.staff.copy(permissions = fresh)))
                    }
                }
            }
        }

        val gate = when (reply.reason) {
            "ordering-disabled" -> OrdersGate.OrderingDisabled
            "subscription" -> OrdersGate.Subscription
            "blocked" -> OrdersGate.Blocked
            "device-limit" -> OrdersGate.DeviceLimit
            else -> null
        }
        if (gate != null) {
            _state.value = _state.value.copy(gate = gate, refreshing = false)
        } else if (reply.ok && _state.value.gate != null) {
            _state.value = _state.value.copy(gate = null)
        }

        reply.posOnline?.let {
            serverPosOnline = it
            serverPosOnlineAt = System.currentTimeMillis()
            _state.value = _state.value.copy(posOnline = effectivePosOnline())
        }
        return reply
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
                // Different restaurant — old data must never flash on screen.
                runCatching { dao.clearScope(previous) }
                _state.value = OrdersUiState(refreshing = true)
                lastOrdersSeq = 0L
                lastCatalogVersion = -1L
                presencePosOnline = null
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
        val reply = try {
            call(c) { put("view", "bootstrap") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onRefreshFailed("bootstrap", e)
            return
        }
        if (!reply.ok) {
            // Gate already applied by call(); stop refreshing quietly.
            _state.value = _state.value.copy(refreshing = false)
            return
        }

        val catalog = reply.catalog ?: WireCatalog()
        val customers = reply.customers.orEmpty()
        val orders = reply.orders.orEmpty()
        val name = reply.restaurant?.name?.takeIf { it.isNotEmpty() } ?: c.restaurantName
        val now = System.currentTimeMillis()

        lastOrdersSeq = reply.ordersSeq ?: 0L
        lastCatalogVersion = reply.catalogVersion ?: 0L
        _roomId.value = reply.roomId

        dao.replaceCatalog(
            c.scopeKey,
            catalog.categories.map { it.toEntity(c.scopeKey) },
            catalog.items.map { it.toEntity(c.scopeKey) },
            catalog.tables.map { it.toEntity(c.scopeKey) },
            customers.map { it.toEntity(c.scopeKey) },
        )
        dao.replaceOrders(c.scopeKey, orders.map { it.toEntity(c.scopeKey, json) })
        dao.putSyncState(
            OrdersSyncStateEntity(
                scope = c.scopeKey,
                roomId = reply.roomId ?: "",
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
    }

    /** Doorbell/polling refresh of just the open-orders list. */
    suspend fun refreshOrders() {
        val c = caller() ?: return
        val reply = try {
            call(c) { put("view", "orders") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onRefreshFailed("orders", e)
            return
        }
        if (!reply.ok) return
        val orders = reply.orders.orEmpty()
        lastOrdersSeq = reply.ordersSeq ?: lastOrdersSeq
        dao.replaceOrders(c.scopeKey, orders.map { it.toEntity(c.scopeKey, json) })
        touchSyncState(c) { it.copy(ordersSeq = lastOrdersSeq, lastSyncAt = System.currentTimeMillis()) }
        emitFromLocal(c)
    }

    /** Catalog refresh — the server answers `unchanged` when we're current. */
    suspend fun refreshCatalog() {
        val c = caller() ?: return
        val reply = try {
            call(c) {
                put("view", "catalog")
                put("haveVersion", lastCatalogVersion)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onRefreshFailed("catalog", e)
            return
        }
        if (!reply.ok || reply.unchanged == true) return
        val catalog = reply.catalog ?: return
        lastCatalogVersion = reply.catalogVersion ?: lastCatalogVersion
        dao.replaceCatalog(
            c.scopeKey,
            catalog.categories.map { it.toEntity(c.scopeKey) },
            catalog.items.map { it.toEntity(c.scopeKey) },
            catalog.tables.map { it.toEntity(c.scopeKey) },
            reply.customers.orEmpty().map { it.toEntity(c.scopeKey) },
        )
        touchSyncState(c) { it.copy(catalogVersion = lastCatalogVersion) }
        emitFromLocal(c)
    }

    /**
     * Doorbell handler (wired by OrdersRealtime). A seq gap forces a full
     * re-bootstrap so a missed ping can never leave stale truth on screen.
     */
    fun onDoorbell(kind: String, seq: Long) {
        scope.launch {
            runCatching {
                when (kind) {
                    "catalog" -> refreshCatalog()
                    "orders", "events" -> {
                        if (seq > lastOrdersSeq + 1) {
                            caller()?.let { bootstrap(it) }
                        } else {
                            refreshOrders()
                        }
                        resolveOpenEvents()
                    }
                }
            }.onFailure { logFailure("doorbell", it) }
        }
    }

    /** Presence verdict from OrdersRealtime (null when the socket is down). */
    fun setPresencePosOnline(online: Boolean?) {
        val was = presencePosOnline
        presencePosOnline = online
        _state.value = _state.value.copy(posOnline = effectivePosOnline())
        // Presence just said the counter left the room. That may only mean the
        // counter's socket dropped while its heartbeat is still running, so ask
        // the server rather than sitting on a possibly stale flag — this is
        // what makes the tab flip within ~5s when the counter really goes down.
        if (online != true && was != online) {
            scope.launch { runCatching { refreshOrders() }.onFailure { logFailure("presence-recheck", it) } }
        }
    }

    /**
     * Is the counter up? The server's flag is the FLOOR, never the ceiling:
     * `mobile-orders` accepts or rejects an event purely on its own
     * `pos_last_seen_at` window, so refusing to let a waiter order while the
     * server would have taken it happily is always wrong. Presence is only
     * ever allowed to say "yes" faster, never to veto a fresh server "yes".
     *
     * The server flag is trusted only while recent, so a counter that dies
     * without a presence event still drops out within one poll cycle.
     */
    private fun effectivePosOnline(): Boolean {
        if (presencePosOnline == true) return true
        val fresh = System.currentTimeMillis() - serverPosOnlineAt < SERVER_FLAG_TTL_MS
        return serverPosOnline && fresh
    }

    /**
     * Single-order lookup by cloud id — used by the detail screen to learn
     * an order's final status (billed/cancelled) once it leaves the open set.
     * Returns null when the order is gone or the network fails.
     */
    suspend fun fetchOrderStatus(serverId: String): String? {
        val c = caller() ?: return null
        val reply = try {
            call(c) {
                put("view", "order")
                put("orderId", serverId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        return if (reply.ok) reply.order?.status else null
    }

    // ---------------- intents ----------------

    /**
     * [existingClientEventId]/[existingOrderClientUuid] make a RETRY of a
     * failed send safe: if the first attempt actually reached the server, the
     * duplicate id returns the original event instead of double-creating.
     */
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
    ): SubmitResult =
        submitEvent(
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
     * Submits one intent. The clientEventId makes retries safe: a duplicate
     * returns the existing event's state and never creates a second one.
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
            call(c) {
                put("action", "submit_event")
                put("clientEventId", clientEventId)
                put("kind", kind)
                orderClientUuid?.let { put("orderClientUuid", it) }
                put("payload", payload)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RevokedException) {
            throw e
        } catch (e: Exception) {
            markEvent(clientEventId, "failed", reason = null)
            return SubmitResult.Failed(MBErrors.network(e))
        }

        if (!reply.ok) {
            val gate = _state.value.gate
            markEvent(clientEventId, "failed", reason = reply.reason)
            return SubmitResult.Failed(reasonCopy(reply.reason), gate)
        }

        val status = reply.status ?: "pending"
        dao.event(clientEventId)?.let {
            dao.putEvent(
                it.copy(
                    status = status,
                    serverEventId = reply.eventId,
                    rejectReason = reply.rejectReason,
                ),
            )
        }
        return if (status == "applied" || status == "rejected") {
            SubmitResult.AlreadyResolved(status, reply.rejectReason)
        } else {
            SubmitResult.Accepted(clientEventId, reply.eventId ?: "")
        }
    }

    /**
     * Waits for the POS to apply/reject an accepted intent. The realtime
     * doorbell resolves this in ~1s; event_status polling is the fallback.
     */
    suspend fun awaitResolution(clientEventId: String, timeoutMs: Long = 25_000): EventResolution {
        val deadline = System.currentTimeMillis() + timeoutMs
        var pollAt = 0L
        while (System.currentTimeMillis() < deadline) {
            val ev = dao.event(clientEventId) ?: return EventResolution.Timeout
            when (ev.status) {
                "applied" -> return EventResolution.Applied
                "rejected" -> return EventResolution.Rejected(reasonCopy(ev.rejectReason))
            }
            if (System.currentTimeMillis() >= pollAt) {
                runCatching { resolveOpenEvents() }
                pollAt = System.currentTimeMillis() + 1_500
            }
            delay(250)
        }
        return EventResolution.Timeout
    }

    /** event_status sweep for everything still in flight (also the doorbell). */
    suspend fun resolveOpenEvents() {
        val c = caller() ?: return
        val open = dao.openEvents(c.scopeKey).filter { it.serverEventId != null }
        if (open.isEmpty()) return
        val reply = try {
            call(c) {
                put("action", "event_status")
                putJsonArray("eventIds") {
                    open.mapNotNull { it.serverEventId }.forEach { add(it) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return // transient; the next doorbell/poll tries again
        }
        if (!reply.ok) return
        val byServerId = open.associateBy { it.serverEventId }
        var resolvedAny = false
        reply.events.orEmpty().forEach { ws ->
            val ev = byServerId[ws.eventId] ?: return@forEach
            if (ev.status != ws.status) {
                dao.putEvent(ev.copy(status = ws.status, rejectReason = ws.rejectReason))
                if (ws.status == "applied" || ws.status == "rejected") resolvedAny = true
            }
        }
        if (resolvedAny) runCatching { refreshOrders() }
    }

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
            restaurantName = restaurantName ?: sync?.restaurantName?.takeIf { it.isNotEmpty() }
                ?: c.restaurantName,
            categories = categories.map { it.toModel() },
            items = dao.items(c.scopeKey).map { it.toModel() },
            tables = dao.tables(c.scopeKey).map { it.toModel() },
            customers = dao.customers(c.scopeKey).map { it.toModel() },
            orders = orders.map { it.toModel(json) },
        )
    }

    private suspend fun touchSyncState(c: Caller, mutate: (OrdersSyncStateEntity) -> OrdersSyncStateEntity) {
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

        /**
         * How long the server's posOnline flag stays trustworthy. The degraded
         * poll runs every 5s and the server's own window is 75s, so 20s keeps
         * the tab honest without flickering between refreshes.
         */
        private const val SERVER_FLAG_TTL_MS = 20_000L

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

// ---------------- wire/entity/model conversion (the single place) ----------------

private fun WireCategory.toEntity(scope: String) =
    MenuCategoryEntity(scope, localId, name, sortOrder)

private fun WireMenuItem.toEntity(scope: String) =
    MenuItemEntity(scope, localId, categoryLocalId, name, price, isAvailable)

private fun WireTable.toEntity(scope: String) =
    RestaurantTableEntity(scope, localId, section, label, sortOrder, isActive)

private fun WireCustomer.toEntity(scope: String) =
    CreditCustomerEntity(scope, localId, name, phone, creditBalance)

private fun WireOrder.toEntity(scope: String, json: Json) = LiveOrderEntity(
    scope = scope,
    clientUuid = clientUuid,
    serverId = id,
    status = status,
    pendingKot = pendingKot,
    orderType = orderType,
    tableNumber = tableNumber,
    section = section,
    tokenNumber = tokenNumber,
    billNumber = billNumber,
    customerName = customerName,
    customerPhone = customerPhone,
    customerLocalId = customerLocalId,
    paymentMode = paymentMode,
    itemsJson = json.encodeToString(ListSerializer(OrderLine.serializer()), items),
    printedItemsJson = json.encodeToString(ListSerializer(OrderLine.serializer()), printedItems),
    subtotal = subtotal,
    gst = gst,
    total = total,
    printError = printError,
    createdByKind = createdByKind,
    createdById = createdById,
    createdByName = createdByName,
    version = version,
    createdAt = createdAt ?: "",
    updatedAt = updatedAt ?: "",
    billedAt = billedAt,
)

private fun MenuCategoryEntity.toModel() = MenuCategory(localId, name, sortOrder)

private fun MenuItemEntity.toModel() = MenuItem(localId, categoryLocalId, name, price, isAvailable)

private fun RestaurantTableEntity.toModel() = TableInfo(localId, section, label, sortOrder, isActive)

private fun CreditCustomerEntity.toModel() = CreditCustomer(localId, name, phone, creditBalance)

private fun LiveOrderEntity.toModel(json: Json): LiveOrder {
    fun decodeLines(raw: String): List<OrderLine> = runCatching {
        json.decodeFromString(ListSerializer(OrderLine.serializer()), raw)
    }.getOrDefault(emptyList())
    return LiveOrder(
        clientUuid = clientUuid,
        serverId = serverId,
        status = status,
        pendingKot = pendingKot,
        orderType = orderType,
        tableNumber = tableNumber,
        section = section,
        tokenNumber = tokenNumber,
        billNumber = billNumber,
        customerName = customerName,
        customerPhone = customerPhone,
        customerLocalId = customerLocalId,
        paymentMode = paymentMode,
        items = decodeLines(itemsJson),
        printedItems = decodeLines(printedItemsJson),
        subtotal = subtotal,
        gst = gst,
        total = total,
        printError = printError,
        createdByKind = createdByKind,
        createdById = createdById,
        createdByName = createdByName,
        createdAt = createdAt,
        updatedAt = updatedAt,
        billedAt = billedAt,
    )
}
