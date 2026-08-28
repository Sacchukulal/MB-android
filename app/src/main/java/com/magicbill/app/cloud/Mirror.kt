package com.magicbill.app.cloud

import androidx.room.withTransaction
import com.magicbill.app.core.Answer
import com.magicbill.app.core.Clock
import com.magicbill.app.core.Ist
import com.magicbill.app.core.arr
import com.magicbill.app.core.asObjectOrNull
import com.magicbill.app.core.bool
import com.magicbill.app.core.int
import com.magicbill.app.core.intOrNull
import com.magicbill.app.core.long
import com.magicbill.app.core.longOrNull
import com.magicbill.app.core.obj
import com.magicbill.app.core.objects
import com.magicbill.app.core.raw
import com.magicbill.app.core.str
import com.magicbill.app.core.strOrNull
import com.magicbill.app.db.BillRow
import com.magicbill.app.db.CashMovementRow
import com.magicbill.app.db.CursorRow
import com.magicbill.app.db.CustomerRow
import com.magicbill.app.db.DayCategoryTotalRow
import com.magicbill.app.db.DayItemTotalRow
import com.magicbill.app.db.DayTotalRow
import com.magicbill.app.db.ExpenseCategoryRow
import com.magicbill.app.db.ExpenseRow
import com.magicbill.app.db.LedgerRow
import com.magicbill.app.db.MbDatabase
import com.magicbill.app.db.MenuCategoryRow
import com.magicbill.app.db.MenuItemRow
import com.magicbill.app.db.NoticeRow
import com.magicbill.app.db.RoleRow
import com.magicbill.app.db.StaffRow
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The phone's mirror of the shop (PHONE_API.md §2). ONE loop for every table: ask
 * `mb_changes` with the cursor we hold, upsert the page and the new cursor in one transaction,
 * repeat while `more`. The loop does not know which table it is on; the table list is data.
 *
 * Pull on app open, pull-to-refresh and when a screen opens. Never on a timer, never in the
 * background. Everything pulled stays on the phone.
 */
class Mirror(private val cloud: CloudLink, private val db: MbDatabase, private val clock: Clock = Clock.system) {

    class Table(val name: String, val need: String?, val write: suspend (restaurantId: String, rows: List<JsonObject>) -> Unit)

    val tables: List<Table> = listOf(
        Table("day_totals", "phone.reports") { r, rows -> db.totals().upsertDays(rows.map { dayTotal(r, it) }) },
        Table("day_item_totals", "phone.reports") { r, rows -> db.totals().upsertItems(rows.map { dayItem(r, it) }) },
        Table("day_category_totals", "phone.reports") { r, rows -> db.totals().upsertCategories(rows.map { dayCategory(r, it) }) },
        Table("bills", "phone.reports") { r, rows -> db.bills().upsert(rows.map { bill(r, it) }) },
        Table("expense_categories", "phone.reports") { r, rows -> db.expenses().upsertCategories(rows.map { expenseCategory(r, it) }) },
        Table("expenses", "phone.reports") { r, rows -> db.expenses().upsert(rows.map { expense(r, it) }) },
        Table("cash_movements", "phone.reports") { r, rows -> db.cash().upsert(rows.map { cash(r, it) }) },
        Table("customers", "phone.khata") { r, rows -> db.khata().upsertCustomers(rows.map { customer(r, it) }) },
        Table("customer_ledger", "phone.khata") { r, rows -> db.khata().upsertLedger(rows.map { ledger(r, it) }) },
        Table("roles", "phone.staff") { r, rows -> db.people().upsertRoles(rows.map { role(r, it) }) },
        Table("staff", "phone.staff") { r, rows -> db.people().upsertStaff(rows.map { staff(r, it) }) },
        Table("menu_categories", null) { r, rows -> db.menu().upsertCategories(rows.map { menuCategory(r, it) }) },
        Table("menu_items", null) { r, rows -> db.menu().upsertItems(rows.map { menuItem(r, it) }) },
        Table("notices", null) { _, rows -> db.notices().upsert(rows.map { notice(it) }) },
    )

    data class Report(
        val pulled: Map<String, Int>,
        val skipped: List<String>,
        /** Null when every table the caller may see came down. */
        val trouble: Answer<Nothing>?,
    ) {
        val rows: Int get() = pulled.values.sum()
        val ok: Boolean get() = trouble == null
    }

    /**
     * Pulls the tables `permissions` opens (owners hold every phone permission). `only` narrows
     * it to the tables a screen is about, so opening Khata does not wait for a month of bills.
     */
    suspend fun pull(restaurantId: String, permissions: Set<String>, only: Set<String>? = null, pageSize: Int = 500): Report {
        val pulled = LinkedHashMap<String, Int>()
        val skipped = ArrayList<String>()
        var trouble: Answer<Nothing>? = null
        for (table in tables) {
            if (only != null && table.name !in only) continue
            if (table.need != null && table.need !in permissions) { skipped.add(table.name); continue }
            when (val r = pullOne(restaurantId, table, pageSize)) {
                is Answer.Ok -> pulled[table.name] = r.value
                is Answer.Refused -> if (r.code == "42501") skipped.add(table.name) else { trouble = r; break }
                is Answer.Unreachable -> { trouble = r; break }
                is Answer.SignedOut -> { trouble = r; break }
            }
        }
        return Report(pulled, skipped, trouble)
    }

    /** One table, all its pages. Answers how many rows came down. */
    private suspend fun pullOne(restaurantId: String, table: Table, pageSize: Int): Answer<Int> {
        var cursor: JsonObject? = db.cursors().get(restaurantId, table.name)?.cursor?.let { com.magicbill.app.core.parseJsonOrNull(it)?.asObjectOrNull() }
        var total = 0
        while (true) {
            val body = buildJsonObject {
                put("restaurant", restaurantId)
                put("tbl", table.name)
                put("lim", pageSize)
                put("cursor", cursor ?: JsonNull)
            }
            val page = when (val a = cloud.rpc("mb_changes", body)) {
                is Answer.Ok -> a.value.asObjectOrNull() ?: return Answer.Unreachable(com.magicbill.app.core.Sentences.CLOUD_UNREACHABLE)
                is Answer.Refused -> return a
                is Answer.Unreachable -> return a
                is Answer.SignedOut -> return a
            }
            val rows = page.arr("rows").objects()
            val next = page.obj("next_cursor")
            db.withTransaction {
                if (rows.isNotEmpty()) table.write(restaurantId, rows)
                if (next != null) db.cursors().put(CursorRow(restaurantId, table.name, next.toString(), clock.now()))
            }
            total += rows.size
            cursor = next ?: cursor
            if (!page.bool("more") || rows.isEmpty()) break
        }
        return Answer.Ok(total)
    }

    // ---- The cloud's columns → the phone's rows ---------------------------------------------

    private fun ts(o: JsonObject, key: String): Long? = Ist.parseTs(o.strOrNull(key))
    private fun deleted(o: JsonObject) = o.strOrNull("deleted_at") != null
    private fun updated(o: JsonObject) = o.longOrNull("updated_ms") ?: ts(o, "updated_at") ?: 0L

    private fun dayTotal(r: String, o: JsonObject) = DayTotalRow(
        r, o.str("business_day"), o.int("bills"), o.int("voids"), o.long("gross_paise"), o.long("discount_paise"), o.long("tax_paise"),
        o.long("charges_paise"), o.long("net_paise"), o.raw("by_payment"), o.long("expenses_paise"), o.long("credit_given_paise"),
        o.long("credit_collected_paise"), o.bool("is_day_closed"), updated(o),
    )

    private fun dayItem(r: String, o: JsonObject) = DayItemTotalRow(r, o.str("business_day"), o.str("item_id"), o.str("item_name"), o.strOrNull("category_id"), o.long("qty_thousandths"), o.long("sales_paise"), updated(o))

    private fun dayCategory(r: String, o: JsonObject) = DayCategoryTotalRow(r, o.str("business_day"), o.str("category_id"), o.str("category_name"), o.long("qty_thousandths"), o.long("sales_paise"), updated(o))

    private fun bill(r: String, o: JsonObject) = BillRow(
        restaurantId = r, id = o.str("id"), terminalId = o.str("terminal_id"), billNumber = o.str("bill_number"), tokenNumber = o.intOrNull("token_number"),
        businessDay = o.str("business_day"), createdAtMs = ts(o, "created_at") ?: 0L, settledAtMs = ts(o, "settled_at"),
        orderType = o.str("order_type"), placement = o.str("placement"), tableName = o.strOrNull("table_name"), customerId = o.strOrNull("customer_id"),
        customerName = o.strOrNull("customer_name"), staffId = o.strOrNull("staff_id"), staffName = o.strOrNull("staff_name"), status = o.str("status"),
        subtotalPaise = o.long("subtotal_paise"), discountPaise = o.long("discount_paise"), taxPaise = o.long("tax_paise"), chargesPaise = o.long("charges_paise"),
        roundOffPaise = o.long("round_off_paise"), grandTotalPaise = o.long("grand_total_paise"), payments = o.raw("payments"), lines = o.raw("lines"),
        taxRows = o.raw("tax_rows"), voidReason = o.strOrNull("void_reason"), source = o.str("source"), updatedMs = updated(o),
    )

    private fun expenseCategory(r: String, o: JsonObject) = ExpenseCategoryRow(r, o.str("id"), o.str("name"), o.int("sort_order"), updated(o), deleted(o))

    private fun expense(r: String, o: JsonObject) = ExpenseRow(r, o.str("id"), o.strOrNull("category_id"), o.str("category_name"), o.long("amount_paise"), o.str("note"), o.str("business_day"), o.strOrNull("paid_by_staff_id"), ts(o, "created_at") ?: 0L, updated(o), deleted(o))

    private fun cash(r: String, o: JsonObject) = CashMovementRow(r, o.str("id"), o.str("kind"), o.long("amount_paise"), o.str("business_day"), o.str("note"), o.strOrNull("staff_id"), ts(o, "created_at") ?: 0L, updated(o), deleted(o))

    private fun customer(r: String, o: JsonObject) = CustomerRow(r, o.str("id"), o.str("name"), o.strOrNull("phone"), o.strOrNull("address"), o.long("balance_paise"), o.longOrNull("credit_limit_paise"), o.bool("is_active"), updated(o), deleted(o))

    private fun ledger(r: String, o: JsonObject) = LedgerRow(r, o.str("id"), o.str("customer_id"), o.str("kind"), o.strOrNull("bill_id"), o.long("amount_paise"), o.str("business_day"), ts(o, "at") ?: 0L, o.str("note"), updated(o))

    private fun role(r: String, o: JsonObject) = RoleRow(r, o.str("id"), o.str("name"), o.bool("is_builtin"), o.intOrNull("max_discount_bp"), o.longOrNull("max_discount_paise"), o.raw("permissions").let { if (it == "null") "[]" else it }, updated(o), deleted(o))

    private fun staff(r: String, o: JsonObject) = StaffRow(
        r, o.str("id"), o.strOrNull("role_id"), o.str("name"), o.strOrNull("code"), o.strOrNull("phone"), o.strOrNull("joined_on"), o.str("status"),
        o.strOrNull("designation"), o.strOrNull("department"), o.bool("is_rider"), o.str("employment_type"), o.strOrNull("left_on"),
        o.bool("can_login_on_phone"), updated(o), o.str("updated_by"), deleted(o),
    )

    private fun menuCategory(r: String, o: JsonObject) = MenuCategoryRow(r, o.str("id"), o.str("name"), o.int("sort_order"), o.bool("is_active"), updated(o), deleted(o))

    private fun menuItem(r: String, o: JsonObject) = MenuItemRow(r, o.str("id"), o.strOrNull("category_id"), o.str("name"), o.long("unit_price_paise"), o.int("tax_rate_bp"), o.strOrNull("short_code"), o.bool("is_available"), o.int("sort_order"), updated(o), deleted(o))

    private fun notice(o: JsonObject) = NoticeRow(o.str("id"), o.strOrNull("restaurant_id"), o.str("target"), o.str("title"), o.str("body"), ts(o, "starts_at") ?: 0L, ts(o, "ends_at"), updated(o), deleted(o))
}
