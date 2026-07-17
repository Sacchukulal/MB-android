package com.magicbill.app.data

import com.magicbill.app.core.BillRow
import com.magicbill.app.core.DashboardData
import com.magicbill.app.core.DaySummaryLite
import com.magicbill.app.core.ExpenseAmount
import com.magicbill.app.core.IST
import com.magicbill.app.core.ItemAggDto
import com.magicbill.app.core.ReportData
import com.magicbill.app.core.TodayStats
import com.magicbill.app.core.TrendDay
import com.magicbill.app.core.istDayEndUtc
import com.magicbill.app.core.istDayStartUtc
import com.magicbill.app.core.istDayString
import com.magicbill.app.core.shiftDay
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owner data (RLS-scoped reads via supabase-kt).
 * - "Today" is always computed live from `bills` (daily_summaries freshness
 *   depends on external refresh calls; bills sync continuously).
 * - History (yesterday and older) comes from `daily_summaries`.
 * Staff data access does NOT use this — it goes through Edge Functions so the
 * license key never reaches staff clients.
 */
@Singleton
class OwnerDataRepository @Inject constructor(
    private val supabase: SupabaseClient,
) {
    private companion object {
        const val BILL_COLUMNS =
            "id, bill_number, token_number, order_type, table_number, payment_mode, subtotal, gst, total, items, billed_at"
    }

    private data class Agg(
        val total: Double,
        val subtotal: Double,
        val gst: Double,
        val cash: Double,
        val card: Double,
        val upi: Double,
        val credit: Double,
        val items: List<ItemAggDto>,
    )

    private fun aggregate(bills: List<BillRow>): Agg {
        var total = 0.0; var subtotal = 0.0; var gst = 0.0
        var cash = 0.0; var card = 0.0; var upi = 0.0; var credit = 0.0
        val itemMap = LinkedHashMap<String, ItemAggDto>()
        for (b in bills) {
            val t = b.total ?: 0.0
            total += t
            subtotal += b.subtotal ?: 0.0
            gst += b.gst ?: 0.0
            when ((b.payment_mode ?: "").lowercase()) {
                "cash" -> cash += t
                "card" -> card += t
                "upi" -> upi += t
                "credit" -> credit += t
            }
            for (it in b.items.orEmpty()) {
                if (it.name.isBlank()) continue
                val key = it.name.trim().lowercase()
                val qty = it.quantity
                val amount = it.price * qty
                val prev = itemMap[key]
                itemMap[key] = if (prev == null) {
                    ItemAggDto(it.name.trim(), qty, amount)
                } else {
                    prev.copy(quantity = prev.quantity + qty, amount = prev.amount + amount)
                }
            }
        }
        return Agg(
            total, subtotal, gst, cash, card, upi, credit,
            itemMap.values.sortedByDescending { it.amount },
        )
    }

    private suspend fun fetchBillsInRange(licenseKey: String, fromDay: String, toDay: String): List<BillRow> =
        supabase.from("bills").select(Columns.raw(BILL_COLUMNS)) {
            filter {
                eq("license_key", licenseKey)
                gte("billed_at", istDayStartUtc(fromDay).toString())
                lt("billed_at", istDayEndUtc(toDay).toString())
            }
            order("billed_at", Order.DESCENDING)
            limit(2000)
        }.decodeList<BillRow>()

    suspend fun fetchDashboard(licenseKey: String): DashboardData = coroutineScope {
        val today = istDayString()
        val trendStart = shiftDay(today, -13)

        val todayBillsDeferred = async { fetchBillsInRange(licenseKey, today, today) }
        val summariesDeferred = async {
            supabase.from("daily_summaries").select(Columns.raw("day, total")) {
                filter {
                    eq("license_key", licenseKey)
                    gte("day", trendStart)
                    lt("day", today)
                }
                order("day", Order.ASCENDING)
            }.decodeList<DaySummaryLite>()
        }

        val todayBills = todayBillsDeferred.await()
        val summaries = summariesDeferred.await()

        val agg = aggregate(todayBills)
        val summaryByDay = summaries.associate { it.day to (it.total ?: 0.0) }

        val trend = buildList {
            for (i in 13 downTo 1) {
                val day = shiftDay(today, -i.toLong())
                add(TrendDay(day, summaryByDay[day] ?: 0.0))
            }
            add(TrendDay(today, agg.total))
        }

        // Insight: bills per IST hour today → "busiest hour".
        val hourCounts = IntArray(24)
        for (b in todayBills) {
            runCatching {
                val hour = Instant.parse(if (b.billed_at.endsWith("Z") || b.billed_at.contains('+')) b.billed_at else b.billed_at + "Z")
                    .atZone(IST).hour
                hourCounts[hour]++
            }
        }

        DashboardData(
            today = TodayStats(
                day = today,
                total = agg.total,
                gst = agg.gst,
                billCount = todayBills.size,
                avg = if (todayBills.isNotEmpty()) agg.total / todayBills.size else 0.0,
                cash = agg.cash, card = agg.card, upi = agg.upi, credit = agg.credit,
            ),
            yesterdayTotal = summaryByDay[shiftDay(today, -1)],
            trend = trend,
            topItems = agg.items.take(5),
            hourCounts = hourCounts.toList(),
        )
    }

    suspend fun fetchReport(licenseKey: String, fromDay: String, toDay: String): ReportData = coroutineScope {
        val billsDeferred = async { fetchBillsInRange(licenseKey, fromDay, toDay) }
        val expensesDeferred = async {
            supabase.from("expenses").select(Columns.raw("amount")) {
                filter {
                    eq("license_key", licenseKey)
                    gte("spent_at", istDayStartUtc(fromDay).toString())
                    lt("spent_at", istDayEndUtc(toDay).toString())
                }
            }.decodeList<ExpenseAmount>()
        }
        // Compare chip: total of the previous period of equal length.
        val rangeDays = ChronoUnit.DAYS.between(
            java.time.LocalDate.parse(fromDay), java.time.LocalDate.parse(toDay),
        ) + 1
        val prevDeferred = async {
            runCatching {
                supabase.from("daily_summaries").select(Columns.raw("day, total")) {
                    filter {
                        eq("license_key", licenseKey)
                        gte("day", shiftDay(fromDay, -rangeDays))
                        lt("day", fromDay)
                    }
                }.decodeList<DaySummaryLite>().sumOf { it.total ?: 0.0 }
            }.getOrNull()
        }

        val bills = billsDeferred.await()
        val agg = aggregate(bills)
        val expenseTotal = expensesDeferred.await().sumOf { it.amount ?: 0.0 }

        ReportData(
            fromDay = fromDay,
            toDay = toDay,
            total = agg.total,
            subtotal = agg.subtotal,
            gst = agg.gst,
            billCount = bills.size,
            avg = if (bills.isNotEmpty()) agg.total / bills.size else 0.0,
            cash = agg.cash, card = agg.card, upi = agg.upi, credit = agg.credit,
            items = agg.items,
            expenseTotal = expenseTotal,
            bills = bills,
            prevTotal = prevDeferred.await(),
        )
    }

    /** Full bill (incl. customer fields) for the receipt view. */
    suspend fun fetchBill(billId: String): BillRow? =
        supabase.from("bills").select(Columns.raw("$BILL_COLUMNS, customer_name, customer_phone")) {
            filter { eq("id", billId) }
        }.decodeList<BillRow>().firstOrNull()
}
