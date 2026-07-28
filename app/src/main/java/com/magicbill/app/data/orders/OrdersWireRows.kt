package com.magicbill.app.data.orders

import com.magicbill.app.core.PermissionMap
import com.magicbill.app.data.local.CreditCustomerEntity
import com.magicbill.app.data.local.LiveOrderEntity
import com.magicbill.app.data.local.MenuCategoryEntity
import com.magicbill.app.data.local.MenuItemEntity
import com.magicbill.app.data.local.RestaurantTableEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The database's own row shapes, read straight from PostgREST under Row
 * Level Security. These are snake_case because they ARE the table columns —
 * this file is the single place where the database shape becomes the app's
 * camelCase wire model, and the wire model never reaches Room or the UI.
 */

// ---------------- tenant_info: gate, permissions, versions, liveness ----------------

@Serializable
data class WireTenantInfo(
    @SerialName("room_id") val roomId: String = "",
    @SerialName("restaurant_name") val restaurantName: String = "",
    @SerialName("restaurant_code") val restaurantCode: String = "",
    @SerialName("ordering_enabled") val orderingEnabled: Boolean = false,
    @SerialName("catalog_version") val catalogVersion: Long = 0,
    @SerialName("orders_seq") val ordersSeq: Long = 0,
    @SerialName("max_mobile_devices") val maxMobileDevices: Long = 1,
    @SerialName("pos_online") val posOnline: Boolean = false,
    @SerialName("client_kind") val clientKind: String = "",
    /** "" when nothing is wrong; otherwise a machine reason from the contract. */
    val gate: String = "",
    val permissions: PermissionMap = emptyMap(),
    @SerialName("actor_name") val actorName: String = "",
)

// ---------------- catalog mirrors ----------------

@Serializable
data class WireCategoryRow(
    @SerialName("local_id") val localId: Long = 0,
    val name: String = "",
    @SerialName("sort_order") val sortOrder: Long = 0,
) {
    fun toWire() = WireCategory(localId, name, sortOrder)
}

@Serializable
data class WireMenuItemRow(
    @SerialName("local_id") val localId: Long = 0,
    @SerialName("category_local_id") val categoryLocalId: Long? = null,
    val name: String = "",
    val price: Double = 0.0,
    @SerialName("is_available") val isAvailable: Boolean = true,
) {
    fun toWire() = WireMenuItem(localId, categoryLocalId, name, price, isAvailable)
}

@Serializable
data class WireTableRow(
    @SerialName("local_id") val localId: Long = 0,
    val section: String = "",
    val label: String = "",
    @SerialName("sort_order") val sortOrder: Long = 0,
    @SerialName("is_active") val isActive: Boolean = true,
) {
    fun toWire() = WireTable(localId, section, label, sortOrder, isActive)
}

@Serializable
data class WireCustomerRow(
    @SerialName("local_id") val localId: Long = 0,
    val name: String = "",
    val phone: String = "",
    @SerialName("credit_balance") val creditBalance: Double = 0.0,
) {
    fun toWire() = WireCustomer(localId, name, phone, creditBalance)
}

// ---------------- live_orders ----------------

@Serializable
data class WireLiveOrderRow(
    val id: String? = null,
    @SerialName("client_uuid") val clientUuid: String = "",
    @SerialName("local_id") val localId: Long? = null,
    val status: String = "queued",
    @SerialName("pending_kot") val pendingKot: Boolean = false,
    @SerialName("order_type") val orderType: String = "Table",
    @SerialName("table_number") val tableNumber: String = "",
    val section: String = "",
    @SerialName("token_number") val tokenNumber: Long? = null,
    @SerialName("bill_number") val billNumber: String? = null,
    @SerialName("customer_name") val customerName: String = "",
    @SerialName("customer_phone") val customerPhone: String = "",
    @SerialName("customer_local_id") val customerLocalId: Long? = null,
    @SerialName("payment_mode") val paymentMode: String = "",
    val items: JsonElement = JsonNull,
    @SerialName("printed_items") val printedItems: JsonElement = JsonNull,
    val subtotal: Double = 0.0,
    val gst: Double = 0.0,
    val total: Double = 0.0,
    @SerialName("print_error") val printError: String = "",
    @SerialName("created_by_kind") val createdByKind: String = "pos",
    @SerialName("created_by_id") val createdById: String? = null,
    @SerialName("created_by_name") val createdByName: String = "",
    val version: Long = 1,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("billed_at") val billedAt: String? = null,
) {
    fun toWire(json: Json): WireOrder = WireOrder(
        id = id,
        clientUuid = clientUuid,
        localId = localId,
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
        items = decodeLines(json, items),
        printedItems = decodeLines(json, printedItems),
        subtotal = subtotal,
        gst = gst,
        total = total,
        printError = printError,
        createdByKind = createdByKind,
        createdById = createdById,
        createdByName = createdByName,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
        billedAt = billedAt,
    )
}

@Serializable
data class WireEventStatusRow(
    val id: String = "",
    val status: String = "pending",
    @SerialName("reject_reason") val rejectReason: String? = null,
)

private fun decodeLines(json: Json, element: JsonElement): List<OrderLine> =
    runCatching {
        json.decodeFromJsonElement(ListSerializer(OrderLine.serializer()), element)
    }.getOrDefault(emptyList())

// ---------------- the bell ----------------

/**
 * One realtime message. `order` and `event` carry the CHANGED ROW itself —
 * the whole point of the rebuild: the phone applies it without a fetch, so
 * N phones cost one message instead of N Edge Function invocations.
 */
data class OrdersBell(
    val kind: String,
    val seq: Long = 0,
    val order: WireOrder? = null,
    val event: JsonObject? = null,
)

/** `content` on a JSON null yields the string "null"; this yields null. */
fun JsonPrimitive.contentOrNullSafe(): String? =
    if (this is JsonNull) null else content.takeIf { it != "null" }

// ---------------- wire -> Room -> app model ----------------

fun WireCategory.toEntity(scope: String) = MenuCategoryEntity(scope, localId, name, sortOrder)

fun WireMenuItem.toEntity(scope: String) =
    MenuItemEntity(scope, localId, categoryLocalId, name, price, isAvailable)

fun WireTable.toEntity(scope: String) =
    RestaurantTableEntity(scope, localId, section, label, sortOrder, isActive)

fun WireCustomer.toEntity(scope: String) =
    CreditCustomerEntity(scope, localId, name, phone, creditBalance)

fun WireOrder.toEntity(scope: String, json: Json) = LiveOrderEntity(
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

/** Open orders are the ones the tab shows; billed/cancelled leave the set. */
val WireOrder.isOpen: Boolean get() = status == "queued" || status == "placed"

fun MenuCategoryEntity.toModel() = MenuCategory(localId, name, sortOrder)

fun MenuItemEntity.toModel() = MenuItem(localId, categoryLocalId, name, price, isAvailable)

fun RestaurantTableEntity.toModel() = TableInfo(localId, section, label, sortOrder, isActive)

fun CreditCustomerEntity.toModel() = CreditCustomer(localId, name, phone, creditBalance)

fun LiveOrderEntity.toModel(json: Json): LiveOrder {
    fun decode(raw: String): List<OrderLine> = runCatching {
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
        items = decode(itemsJson),
        printedItems = decode(printedItemsJson),
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
