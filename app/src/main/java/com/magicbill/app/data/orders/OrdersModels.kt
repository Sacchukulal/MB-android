package com.magicbill.app.data.orders

import kotlinx.serialization.Serializable

/**
 * Wire DTOs for live ordering (camelCase, exactly the shapes in
 * MB-backend/ORDERS_CONTRACT.md §6). These are what the realtime bell
 * carries and what PostgREST rows convert into; the wire shape is turned
 * into app models ONLY inside OrdersRepository, so it never leaks into Room
 * or the UI.
 */

// ---------------- item / order lines ----------------

/**
 * One cart line. Line identity for merging = (localId, note): the same item
 * with different notes is two lines and prints as two lines.
 */
@Serializable
data class OrderLine(
    val localId: Long = 0,
    val name: String = "",
    val price: Double = 0.0,
    val quantity: Int = 0,
    val categoryLocalId: Long? = null,
    val note: String? = null,
)

// ---------------- catalog ----------------

@Serializable
data class WireCategory(
    val localId: Long = 0,
    val name: String = "",
    val sortOrder: Long = 0,
)

@Serializable
data class WireMenuItem(
    val localId: Long = 0,
    val categoryLocalId: Long? = null,
    val name: String = "",
    val price: Double = 0.0,
    val isAvailable: Boolean = true,
)

@Serializable
data class WireTable(
    val localId: Long = 0,
    val section: String = "",
    val label: String = "",
    val sortOrder: Long = 0,
    val isActive: Boolean = true,
)

@Serializable
data class WireCustomer(
    val localId: Long = 0,
    val name: String = "",
    val phone: String = "",
    val creditBalance: Double = 0.0,
)

@Serializable
data class WireCatalog(
    val categories: List<WireCategory> = emptyList(),
    val items: List<WireMenuItem> = emptyList(),
    val tables: List<WireTable> = emptyList(),
)

// ---------------- live orders ----------------

@Serializable
data class WireOrder(
    val id: String? = null,
    val clientUuid: String = "",
    val localId: Long? = null,
    val status: String = "queued",
    val pendingKot: Boolean = false,
    val orderType: String = "Table",
    val tableNumber: String = "",
    val section: String = "",
    val tokenNumber: Long? = null,
    val billNumber: String? = null,
    val customerName: String = "",
    val customerPhone: String = "",
    val customerLocalId: Long? = null,
    val paymentMode: String = "",
    val items: List<OrderLine> = emptyList(),
    val printedItems: List<OrderLine> = emptyList(),
    val subtotal: Double = 0.0,
    val gst: Double = 0.0,
    val total: Double = 0.0,
    val printError: String = "",
    val createdByKind: String = "pos",
    val createdById: String? = null,
    val createdByName: String = "",
    val version: Long = 1,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val billedAt: String? = null,
)

// ---------------- app-facing models ----------------

data class MenuCategory(val localId: Long, val name: String, val sortOrder: Long)

data class MenuItem(
    val localId: Long,
    val categoryLocalId: Long?,
    val name: String,
    val price: Double,
    val isAvailable: Boolean,
)

data class TableInfo(
    val localId: Long,
    val section: String,
    val label: String,
    val sortOrder: Long,
    val isActive: Boolean,
)

data class CreditCustomer(
    val localId: Long,
    val name: String,
    val phone: String,
    val creditBalance: Double,
)

data class LiveOrder(
    val clientUuid: String,
    val serverId: String?,
    val status: String,
    val pendingKot: Boolean,
    val orderType: String,
    val tableNumber: String,
    val section: String,
    val tokenNumber: Long?,
    val billNumber: String?,
    val customerName: String,
    val customerPhone: String,
    val customerLocalId: Long?,
    val paymentMode: String,
    val items: List<OrderLine>,
    val printedItems: List<OrderLine>,
    val subtotal: Double,
    val gst: Double,
    val total: Double,
    val printError: String,
    val createdByKind: String,
    val createdById: String?,
    val createdByName: String,
    val createdAt: String,
    val updatedAt: String,
    val billedAt: String?,
) {
    val itemCount: Int get() = items.sumOf { it.quantity }
    val isOpen: Boolean get() = status == "queued" || status == "placed"
}

/** Everything the Orders tab needs, swapped in one emission. */
data class OrdersData(
    val restaurantName: String,
    val categories: List<MenuCategory>,
    val items: List<MenuItem>,
    val tables: List<TableInfo>,
    val customers: List<CreditCustomer>,
    val orders: List<LiveOrder>,
)

/**
 * Full-screen blocked states, each mapped from a server reason. `null` gate
 * = ordering is usable.
 */
enum class OrdersGate {
    /** Owner hasn't switched mobile ordering on at the POS. */
    OrderingDisabled,

    /** Subscription expired beyond grace. */
    Subscription,

    /** This phone was blocked from the POS. */
    Blocked,

    /** Plan's phone limit reached. */
    DeviceLimit,
}

/**
 * Whether the counter is alive — THREE states, not two.
 *
 * The missing third state is what made the reported bug so bad. When the
 * phone could not check, it reported that as [Offline]: the waiter was told
 * the owner's till was dead when the truth was that the phone could not ask.
 * "I don't know" is a different fact from "it has stopped", it points at a
 * different machine, and the waiter needs to be told which one it is.
 */
enum class PosStatus {
    /** We checked. The counter is alive. */
    Online,

    /** We checked. The counter has genuinely stopped. */
    Offline,

    /** We could not check — no connection, the request failed, or never yet. */
    Unknown,
}

data class OrdersUiState(
    val data: OrdersData? = null,
    val gate: OrdersGate? = null,
    /** The counter's liveness as far as this phone honestly knows (§4.3). */
    val posStatus: PosStatus = PosStatus.Unknown,
    val refreshing: Boolean = false,
    /** Set only when there is nothing to render at all. */
    val error: String? = null,
    val updatedAt: Long? = null,
)

/** Immediate outcome of submitting an intent. */
sealed interface SubmitResult {
    /** Server accepted it; resolution (applied/rejected) follows async. */
    data class Accepted(val clientEventId: String, val serverEventId: String) : SubmitResult

    /** Duplicate submit that the POS already resolved. */
    data class AlreadyResolved(val status: String, val rejectReason: String?) : SubmitResult

    /** Not accepted — safe to retry with the same clientEventId. */
    data class Failed(val message: String, val gate: OrdersGate? = null) : SubmitResult
}

/** Terminal state of an intent, resolved via doorbell or event_status polls. */
sealed interface EventResolution {
    data object Applied : EventResolution
    data class Rejected(val message: String) : EventResolution
    data object Timeout : EventResolution
}
