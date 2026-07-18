package com.magicbill.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import com.magicbill.app.core.IST
import com.magicbill.app.core.MBErrors
import com.magicbill.app.core.istDayString
import com.magicbill.app.core.shiftDay
import com.magicbill.app.data.local.BillEntity
import com.magicbill.app.data.local.DaySummaryEntity
import com.magicbill.app.data.local.ExpenseEntity
import com.magicbill.app.data.local.OwnerLocalDao
import com.magicbill.app.data.local.SyncStateEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

// ---- wire rows (synced_at rides along as the keyset cursor) ----

@Serializable
private data class SyncBillRow(
    val id: String,
    val bill_number: String? = null,
    val token_number: Long? = null,
    val order_type: String? = null,
    val table_number: String? = null,
    val payment_mode: String? = null,
    val subtotal: Double? = null,
    val gst: Double? = null,
    val total: Double? = null,
    val items: JsonElement? = null,
    val billed_at: String,
    val customer_name: String? = null,
    val customer_phone: String? = null,
    val synced_at: String? = null,
)

@Serializable
private data class SyncExpenseRow(
    val id: String,
    val amount: Double? = null,
    val spent_at: String,
    val synced_at: String? = null,
)

@Serializable
private data class SyncSummaryRow(
    val day: String,
    val bill_count: Int? = null,
    val subtotal: Double? = null,
    val gst: Double? = null,
    val total: Double? = null,
    val cash_total: Double? = null,
    val card_total: Double? = null,
    val upi_total: Double? = null,
    val credit_total: Double? = null,
    val expense_total: Double? = null,
)

/**
 * Pulls the owner's business data into the local SQLite mirror.
 * Incremental: bills/expenses use `synced_at` keyset cursors (so re-synced or
 * late-arriving rows are picked up too); daily summaries (tiny, one row per
 * day) are re-pulled for the last 400 days. A successful sync bumps [tick] so
 * every screen recomputes from local data.
 */
@Singleton
class OwnerSync @Inject constructor(
    private val supabase: SupabaseClient,
    private val dao: OwnerLocalDao,
    @ApplicationContext context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val lastSuccess = HashMap<String, Long>()

    /** Bumps (epoch ms) after every successful sync — screens observe this. */
    private val _tick = MutableStateFlow(0L)
    val tick: StateFlow<Long> = _tick

    /** The license the UI is currently on; connectivity-return auto-syncs it. */
    @Volatile
    var activeLicense: String? = null

    init {
        // "Once internet is connected it should fetch the latest data" —
        // resync the active license the moment a network shows up.
        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val license = activeLicense ?: return
                    Log.i(TAG, "[NET] network available — auto-sync $license")
                    scope.launch { sync(license, force = true) }
                }
            })
        }
    }

    /**
     * Runs a sync for [licenseKey]. @return null on success (or fresh-enough
     * skip), else a user-facing error message. Never throws.
     */
    suspend fun sync(licenseKey: String, force: Boolean = false): String? = mutex.withLock {
        val now = System.currentTimeMillis()
        if (!force && now - (lastSuccess[licenseKey] ?: 0L) < FRESH_MS) return null
        try {
            withTimeoutOrNull(5_000) { supabase.auth.awaitInitialization() }
            if (supabase.auth.currentSessionOrNull() == null) return MBErrors.SESSION_EXPIRED

            val state = dao.syncState(licenseKey)
            val billsCursor = pullBills(licenseKey, state?.billsCursor)
            val expensesCursor = pullExpenses(licenseKey, state?.expensesCursor)
            pullSummaries(licenseKey)

            dao.putSyncState(
                SyncStateEntity(
                    licenseKey = licenseKey,
                    billsCursor = billsCursor,
                    expensesCursor = expensesCursor,
                    lastSyncAt = System.currentTimeMillis(),
                ),
            )
            // Local bills beyond the server's own retention + a buffer can go.
            dao.pruneBillsBefore(shiftDay(istDayString(), -92))

            lastSuccess[licenseKey] = System.currentTimeMillis()
            _tick.value = System.currentTimeMillis()
            Log.i(TAG, "[CACHE] sync complete for $licenseKey")
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "[NET] sync failed for $licenseKey: ${e::class.simpleName}: ${e.message}")
            MBErrors.network(e)
        }
    }

    private suspend fun pullBills(licenseKey: String, startCursor: String?): String? {
        var cursor = startCursor
        repeat(MAX_PAGES) {
            val rows = supabase.from("bills").select(
                Columns.raw(
                    "id, bill_number, token_number, order_type, table_number, payment_mode, " +
                        "subtotal, gst, total, items, billed_at, customer_name, customer_phone, synced_at",
                ),
            ) {
                filter {
                    eq("license_key", licenseKey)
                    cursor?.let { gt("synced_at", it) }
                }
                order("synced_at", Order.ASCENDING)
                limit(PAGE_SIZE)
            }.decodeList<SyncBillRow>()

            if (rows.isEmpty()) return cursor
            dao.upsertBills(
                rows.map { r ->
                    val instant = parseIso(r.billed_at)
                    BillEntity(
                        id = r.id,
                        licenseKey = licenseKey,
                        billNumber = r.bill_number,
                        tokenNumber = r.token_number,
                        orderType = r.order_type,
                        tableNumber = r.table_number,
                        paymentMode = r.payment_mode,
                        subtotal = r.subtotal,
                        gst = r.gst,
                        total = r.total,
                        itemsJson = r.items?.toString(),
                        billedAt = r.billed_at,
                        billedAtEpoch = instant.toEpochMilli(),
                        dayIst = instant.atZone(IST).toLocalDate().toString(),
                        customerName = r.customer_name,
                        customerPhone = r.customer_phone,
                    )
                },
            )
            cursor = rows.mapNotNull { it.synced_at }.maxOrNull() ?: cursor
            if (rows.size < PAGE_SIZE) return cursor
        }
        return cursor
    }

    private suspend fun pullExpenses(licenseKey: String, startCursor: String?): String? {
        var cursor = startCursor
        repeat(MAX_PAGES) {
            val rows = supabase.from("expenses").select(
                Columns.raw("id, amount, spent_at, synced_at"),
            ) {
                filter {
                    eq("license_key", licenseKey)
                    cursor?.let { gt("synced_at", it) }
                }
                order("synced_at", Order.ASCENDING)
                limit(PAGE_SIZE)
            }.decodeList<SyncExpenseRow>()

            if (rows.isEmpty()) return cursor
            dao.upsertExpenses(
                rows.map { r ->
                    ExpenseEntity(
                        id = r.id,
                        licenseKey = licenseKey,
                        amount = r.amount ?: 0.0,
                        dayIst = parseIso(r.spent_at).atZone(IST).toLocalDate().toString(),
                    )
                },
            )
            cursor = rows.mapNotNull { it.synced_at }.maxOrNull() ?: cursor
            if (rows.size < PAGE_SIZE) return cursor
        }
        return cursor
    }

    private suspend fun pullSummaries(licenseKey: String) {
        val rows = supabase.from("daily_summaries").select(
            Columns.raw(
                "day, bill_count, subtotal, gst, total, cash_total, card_total, " +
                    "upi_total, credit_total, expense_total",
            ),
        ) {
            filter {
                eq("license_key", licenseKey)
                gte("day", shiftDay(istDayString(), -400))
            }
            order("day", Order.ASCENDING)
        }.decodeList<SyncSummaryRow>()

        if (rows.isEmpty()) return
        dao.upsertSummaries(
            rows.map { r ->
                DaySummaryEntity(
                    licenseKey = licenseKey,
                    day = r.day,
                    billCount = r.bill_count ?: 0,
                    subtotal = r.subtotal ?: 0.0,
                    gst = r.gst ?: 0.0,
                    total = r.total ?: 0.0,
                    cash = r.cash_total ?: 0.0,
                    card = r.card_total ?: 0.0,
                    upi = r.upi_total ?: 0.0,
                    credit = r.credit_total ?: 0.0,
                    expenseTotal = r.expense_total ?: 0.0,
                )
            },
        )
    }

    private fun parseIso(iso: String): Instant =
        Instant.parse(if (iso.endsWith("Z") || iso.contains('+')) iso else iso + "Z")

    companion object {
        private const val TAG = "MB/Sync"
        private const val FRESH_MS = 60_000L
        private const val PAGE_SIZE = 1000L
        private const val MAX_PAGES = 10
    }
}
