package com.magicbill.app.db

import androidx.room.Entity
import androidx.room.Index

/*
 * The phone's copy of the shop. Columns are the cloud's, in the cloud's units: paise, IST
 * business days as "YYYY-MM-DD", timestamps as ms. JSON columns stay JSON text until a detail
 * screen needs them. Every mirror row keys on (restaurantId, id) — the phone may hold more
 * than one shop.
 */

@Entity(tableName = "bills", primaryKeys = ["restaurantId", "id"], indices = [Index("restaurantId", "businessDay"), Index("restaurantId", "createdAtMs")])
data class BillRow(
    val restaurantId: String,
    val id: String,
    val terminalId: String,
    val billNumber: String,
    val tokenNumber: Int?,
    val businessDay: String,
    val createdAtMs: Long,
    val settledAtMs: Long?,
    val orderType: String,
    val placement: String,
    val tableName: String?,
    val customerId: String?,
    val customerName: String?,
    val staffId: String?,
    val staffName: String?,
    /** settled · voided · corrected. */
    val status: String,
    val subtotalPaise: Long,
    val discountPaise: Long,
    val taxPaise: Long,
    val chargesPaise: Long,
    val roundOffPaise: Long,
    val grandTotalPaise: Long,
    val payments: String,
    val lines: String,
    val taxRows: String,
    val voidReason: String?,
    val source: String,
    val updatedMs: Long,
)

@Entity(tableName = "day_totals", primaryKeys = ["restaurantId", "businessDay"])
data class DayTotalRow(
    val restaurantId: String,
    val businessDay: String,
    val bills: Int,
    val voids: Int,
    val grossPaise: Long,
    val discountPaise: Long,
    val taxPaise: Long,
    val chargesPaise: Long,
    val netPaise: Long,
    /** {"cash": paise, "upi": paise, …} */
    val byPayment: String,
    val expensesPaise: Long,
    val creditGivenPaise: Long,
    val creditCollectedPaise: Long,
    val isDayClosed: Boolean,
    val updatedMs: Long,
)

@Entity(tableName = "day_item_totals", primaryKeys = ["restaurantId", "businessDay", "itemId"])
data class DayItemTotalRow(
    val restaurantId: String,
    val businessDay: String,
    val itemId: String,
    val itemName: String,
    val categoryId: String?,
    val qtyThousandths: Long,
    val salesPaise: Long,
    val updatedMs: Long,
)

@Entity(tableName = "day_category_totals", primaryKeys = ["restaurantId", "businessDay", "categoryId"])
data class DayCategoryTotalRow(
    val restaurantId: String,
    val businessDay: String,
    val categoryId: String,
    val categoryName: String,
    val qtyThousandths: Long,
    val salesPaise: Long,
    val updatedMs: Long,
)

@Entity(tableName = "expenses", primaryKeys = ["restaurantId", "id"], indices = [Index("restaurantId", "businessDay")])
data class ExpenseRow(
    val restaurantId: String,
    val id: String,
    val categoryId: String?,
    val categoryName: String,
    val amountPaise: Long,
    val note: String,
    val businessDay: String,
    val paidByStaffId: String?,
    val createdAtMs: Long,
    val updatedMs: Long,
    val deleted: Boolean,
)

@Entity(tableName = "expense_categories", primaryKeys = ["restaurantId", "id"])
data class ExpenseCategoryRow(val restaurantId: String, val id: String, val name: String, val sortOrder: Int, val updatedMs: Long, val deleted: Boolean)

@Entity(tableName = "cash_movements", primaryKeys = ["restaurantId", "id"], indices = [Index("restaurantId", "businessDay")])
data class CashMovementRow(
    val restaurantId: String,
    val id: String,
    val kind: String,
    val amountPaise: Long,
    val businessDay: String,
    val note: String,
    val staffId: String?,
    val createdAtMs: Long,
    val updatedMs: Long,
    val deleted: Boolean,
)

@Entity(tableName = "customers", primaryKeys = ["restaurantId", "id"])
data class CustomerRow(
    val restaurantId: String,
    val id: String,
    val name: String,
    val phone: String?,
    val address: String?,
    val balancePaise: Long,
    val creditLimitPaise: Long?,
    val isActive: Boolean,
    val updatedMs: Long,
    val deleted: Boolean,
)

@Entity(tableName = "customer_ledger", primaryKeys = ["restaurantId", "id"], indices = [Index("restaurantId", "customerId")])
data class LedgerRow(
    val restaurantId: String,
    val id: String,
    val customerId: String,
    val kind: String,
    val billId: String?,
    /** Signed: + owed, − paid. */
    val amountPaise: Long,
    val businessDay: String,
    val atMs: Long,
    val note: String,
    val updatedMs: Long,
)

@Entity(tableName = "staff", primaryKeys = ["restaurantId", "id"])
data class StaffRow(
    val restaurantId: String,
    val id: String,
    val roleId: String?,
    val name: String,
    val phone: String?,
    val joinedOn: String?,
    val status: String,
    val designation: String?,
    val department: String?,
    val isRider: Boolean,
    val employmentType: String,
    val leftOn: String?,
    val updatedMs: Long,
    val updatedBy: String,
    val deleted: Boolean,
)

@Entity(tableName = "roles", primaryKeys = ["restaurantId", "id"])
data class RoleRow(
    val restaurantId: String,
    val id: String,
    val name: String,
    val isBuiltin: Boolean,
    val maxDiscountBp: Int?,
    val maxDiscountPaise: Long?,
    /** JSON array of permission codes. */
    val permissions: String,
    val updatedMs: Long,
    val deleted: Boolean,
)

@Entity(tableName = "menu_items", primaryKeys = ["restaurantId", "id"])
data class MenuItemRow(
    val restaurantId: String,
    val id: String,
    val categoryId: String?,
    val name: String,
    val unitPricePaise: Long,
    val taxRateBp: Int,
    val shortCode: String?,
    val isAvailable: Boolean,
    val sortOrder: Int,
    val updatedMs: Long,
    val deleted: Boolean,
)

@Entity(tableName = "menu_categories", primaryKeys = ["restaurantId", "id"])
data class MenuCategoryRow(val restaurantId: String, val id: String, val name: String, val sortOrder: Int, val isActive: Boolean, val updatedMs: Long, val deleted: Boolean)

/** A notice from Magic Bill. `forRestaurant` null = every shop. Read state lives in [NoticeReadRow]. */
@Entity(tableName = "notices")
data class NoticeRow(
    @androidx.room.PrimaryKey val id: String,
    val forRestaurant: String?,
    val target: String,
    val title: String,
    val body: String,
    val startsAtMs: Long,
    val endsAtMs: Long?,
    val updatedMs: Long,
    val deleted: Boolean,
)

@Entity(tableName = "notice_reads")
data class NoticeReadRow(@androidx.room.PrimaryKey val id: String, val readAtMs: Long)

/** Where the mirror is, per restaurant and table. Opaque; never built by hand. */
@Entity(tableName = "cursors", primaryKeys = ["restaurantId", "tbl"])
data class CursorRow(val restaurantId: String, val tbl: String, val cursor: String, val pulledAtMs: Long)

/**
 * An intent for the counter, durable from before its first send. The id is generated once and
 * kept across restarts — an id regenerated on retry is a duplicate order (LAN_PROTOCOL.md §6).
 */
@Entity(tableName = "intents", indices = [Index("state"), Index("createdMs")])
data class IntentRow(
    @androidx.room.PrimaryKey val id: String,
    val orderId: String?,
    /** The phone's clock when the person pressed the button, ms. */
    val atMs: Long,
    /** The `what` block, JSON. */
    val what: String,
    /** "2 × Masala Dosa" — what the queue shows. */
    val label: String,
    val tableLabel: String?,
    /** queued · ok · refused · held. */
    val state: String,
    /** The outcome, JSON, once there is one. Byte for byte what the counter said. */
    val outcome: String?,
    val createdMs: Long,
    val answeredMs: Long?,
    val attempts: Int,
)

/** The counter's catalogue, cached so the floor opens with no network. */
@Entity(tableName = "floor_items")
data class FloorItemRow(@androidx.room.PrimaryKey val id: String, val name: String, val category: String, val price: String, val isAvailable: Boolean, val ord: Int)

@Entity(tableName = "floor_tables")
data class FloorTableRow(@androidx.room.PrimaryKey val id: String, val label: String, val section: String, val seats: Int, val state: String, val ord: Int)

/** An open order on the floor — anybody's — as the counter last described it. */
@Entity(tableName = "floor_orders")
data class FloorOrderRow(
    @androidx.room.PrimaryKey val orderId: String,
    val tableId: String?,
    val tableLabel: String?,
    val orderType: String,
    val total: String,
    val token: String?,
    /** Lines, JSON, as the counter's last outcome or push listed them. */
    val lines: String,
    val note: String?,
    /** Who opened it, as the counter names them. */
    val by: String?,
    val byId: String?,
    /** Opened by this phone's own person. */
    val mine: Boolean,
    /** The bill was asked for from a phone and printed at the counter. */
    val billAsked: Boolean,
    /** Staged here and on its way to the counter; the answer clears it. */
    val sending: Boolean,
    /** Open, or the counter's sentence about why it is not. */
    val closedSays: String?,
    val updatedMs: Long,
)
