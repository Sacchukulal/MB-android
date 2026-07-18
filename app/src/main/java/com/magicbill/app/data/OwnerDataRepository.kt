package com.magicbill.app.data

import com.magicbill.app.core.BillItem
import com.magicbill.app.core.BillRow
import com.magicbill.app.core.DashboardData
import com.magicbill.app.core.IST
import com.magicbill.app.core.ItemAggDto
import com.magicbill.app.core.ReportData
import com.magicbill.app.core.TodayStats
import com.magicbill.app.core.TrendDay
import com.magicbill.app.core.istDayString
import com.magicbill.app.core.shiftDay
import com.magicbill.app.data.local.BillEntity
import com.magicbill.app.data.local.OwnerLocalDao
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owner data, computed LOCALLY from the SQLite mirror ([OwnerLocalDao]).
 * The network never blocks a screen: [OwnerSync] tops up the mirror and these
 * functions aggregate whatever is on the phone — so the last synced data is
 * always available, fully offline, for any date range.
 * Staff data access does NOT use this — it goes through Edge Functions so the
 * license key never reaches staff clients.
 */
@Singleton
class OwnerDataRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val dao: OwnerLocalDao,
    private val json: Json,
) {
    private companion object {
        const val BILL_COLUMNS =
            "id, bill_number, token_number, order_type, table_number, payment_mode, subtotal, gst, total, items, billed_at"
    }

    /** Epoch ms of the last successful sync, or null if never synced. */
    suspend fun lastSyncAt(licenseKey: String): Long? =
        dao.syncState(licenseKey)?.lastSyncAt

    // ---------------- local aggregation ----------------

    private fun BillEntity.toBillRow(): BillRow = BillRow(
        id = id,
        bill_number = billNumber,
        token_number = tokenNumber,
        order_type = orderType,
        table_number = tableNumber,
        payment_mode = paymentMode,
        subtotal = subtotal,
        gst = gst,
        total = total,
        items = itemsJson?.let {
            runCatching { json.decodeFromString(ListSerializer(BillItem.serializer()), it) }.getOrNull()
        },
        billed_at = billedAt,
        customer_name = customerName,
        customer_phone = customerPhone,
    )

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

    /**
     * Dashboard from local data. Returns null when this license has never
     * synced (first run needs one online fetch; after that, always available).
     */
    suspend fun dashboardLocal(licenseKey: String): DashboardData? = withContext(Dispatchers.Default) {
        if (dao.syncState(licenseKey) == null) return@withContext null
        val today = istDayString()
        val trendStart = shiftDay(today, -13)

        val todayBills = dao.billsInRange(licenseKey, today, today).map { it.toBillRow() }
        val summaries = dao.summariesInRange(licenseKey, trendStart, shiftDay(today, -1))

        val agg = aggregate(todayBills)
        val summaryByDay = summaries.associate { it.day to it.total }

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
                val hour = Instant.parse(
                    if (b.billed_at.endsWith("Z") || b.billed_at.contains('+')) b.billed_at else b.billed_at + "Z",
                ).atZone(IST).hour
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

    /** Report from local data. Null when this license has never synced. */
    suspend fun reportLocal(licenseKey: String, fromDay: String, toDay: String): ReportData? =
        withContext(Dispatchers.Default) {
            if (dao.syncState(licenseKey) == null) return@withContext null

            val bills = dao.billsInRange(licenseKey, fromDay, toDay).map { it.toBillRow() }
            val agg = aggregate(bills)
            val expenseTotal = dao.expenseTotalInRange(licenseKey, fromDay, toDay)

            // Compare chip: total of the previous period of equal length.
            val rangeDays = ChronoUnit.DAYS.between(
                java.time.LocalDate.parse(fromDay), java.time.LocalDate.parse(toDay),
            ) + 1
            val prevSummaries = dao.summariesInRange(
                licenseKey, shiftDay(fromDay, -rangeDays), shiftDay(fromDay, -1),
            )
            // Null (not 0) when we have no previous-period data — keeps the
            // compare chip neutral instead of claiming an infinite jump.
            val prevTotal = if (prevSummaries.isEmpty()) null else prevSummaries.sumOf { it.total }

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
                prevTotal = prevTotal,
            )
        }

    /** Full bill for the receipt view: local mirror first, network fallback. */
    suspend fun fetchBill(billId: String): BillRow? {
        dao.bill(billId)?.let { return it.toBillRow() }
        return supabase.from("bills").select(Columns.raw("$BILL_COLUMNS, customer_name, customer_phone")) {
            filter { eq("id", billId) }
        }.decodeList<BillRow>().firstOrNull()
    }
}
