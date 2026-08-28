package com.magicbill.app.cloud

import com.magicbill.app.core.BillJson
import com.magicbill.app.core.Ist
import com.magicbill.app.db.DayCategoryTotalRow
import com.magicbill.app.db.DayItemTotalRow
import com.magicbill.app.db.DayTotalRow
import com.magicbill.app.db.ExpenseRow
import java.time.LocalDate

/**
 * Reports on the phone are sums of what the counter already computed (`day_totals`,
 * `day_item_totals`, `day_category_totals`). Nothing here prices, taxes or discounts anything.
 * Pure functions over rows, so a test can hand them a shop and check the rupees.
 */
object ReportMath {
    data class Totals(
        val bills: Int = 0,
        val voids: Int = 0,
        val grossPaise: Long = 0,
        val discountPaise: Long = 0,
        val taxPaise: Long = 0,
        val chargesPaise: Long = 0,
        val netPaise: Long = 0,
        val expensesPaise: Long = 0,
        val creditGivenPaise: Long = 0,
        val creditCollectedPaise: Long = 0,
        val byPayment: List<Pair<String, Long>> = emptyList(),
        val daysWithSales: Int = 0,
    ) {
        val averageBillPaise: Long get() = if (bills > 0) netPaise / bills else 0
        /** What the drawer keeps: net sales less expenses. Not profit — the phone does not know cost. */
        val afterExpensesPaise: Long get() = netPaise - expensesPaise
    }

    fun totals(days: List<DayTotalRow>): Totals {
        val split = LinkedHashMap<String, Long>()
        var t = Totals()
        for (d in days) {
            t = t.copy(
                bills = t.bills + d.bills, voids = t.voids + d.voids, grossPaise = t.grossPaise + d.grossPaise,
                discountPaise = t.discountPaise + d.discountPaise, taxPaise = t.taxPaise + d.taxPaise, chargesPaise = t.chargesPaise + d.chargesPaise,
                netPaise = t.netPaise + d.netPaise, expensesPaise = t.expensesPaise + d.expensesPaise,
                creditGivenPaise = t.creditGivenPaise + d.creditGivenPaise, creditCollectedPaise = t.creditCollectedPaise + d.creditCollectedPaise,
                daysWithSales = t.daysWithSales + if (d.bills > 0) 1 else 0,
            )
            for ((mode, paise) in BillJson.split(d.byPayment)) split[mode] = (split[mode] ?: 0L) + paise
        }
        return t.copy(byPayment = split.entries.map { it.key to it.value }.sortedByDescending { it.second })
    }

    data class ItemTotal(val id: String, val name: String, val qtyThousandths: Long, val salesPaise: Long)

    fun topItems(rows: List<DayItemTotalRow>, limit: Int = 50): List<ItemTotal> =
        rows.groupBy { it.itemId }.map { (id, list) ->
            ItemTotal(id, list.maxBy { it.updatedMs }.itemName, list.sumOf { it.qtyThousandths }, list.sumOf { it.salesPaise })
        }.sortedByDescending { it.salesPaise }.take(limit)

    data class CategoryTotal(val id: String, val name: String, val qtyThousandths: Long, val salesPaise: Long)

    fun categories(rows: List<DayCategoryTotalRow>): List<CategoryTotal> =
        rows.groupBy { it.categoryId }.map { (id, list) ->
            CategoryTotal(id, list.maxBy { it.updatedMs }.categoryName, list.sumOf { it.qtyThousandths }, list.sumOf { it.salesPaise })
        }.sortedByDescending { it.salesPaise }

    data class ExpenseGroup(val category: String, val paise: Long, val count: Int)

    fun expensesByCategory(rows: List<ExpenseRow>): List<ExpenseGroup> =
        rows.groupBy { it.categoryName.ifBlank { "Other" } }.map { (c, list) -> ExpenseGroup(c, list.sumOf { it.amountPaise }, list.size) }
            .sortedByDescending { it.paise }

    /** "+12%" / "−8%" against the range before; null when there was nothing before. */
    fun change(now: Long, before: Long): Int? = if (before <= 0) null else (((now - before) * 100) / before).toInt()

    /** Net per day for a trend, with zeros where the shop was shut, so a week has seven bars. */
    fun perDay(days: List<DayTotalRow>, range: Ist.Range): List<Pair<LocalDate, Long>> {
        val byDay = days.associate { it.businessDay to it.netPaise }
        val out = ArrayList<Pair<LocalDate, Long>>()
        var d = range.from
        while (!d.isAfter(range.to)) {
            out.add(d to (byDay[Ist.key(d)] ?: 0L))
            d = d.plusDays(1)
        }
        return out
    }

    /** The best day and the best hour are the two questions an owner asks first. */
    fun bestDay(days: List<DayTotalRow>): DayTotalRow? = days.maxByOrNull { it.netPaise }?.takeIf { it.netPaise > 0 }
}
