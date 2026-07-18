package com.magicbill.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * Local mirror of the owner's business data (SQLite via Room). This is the
 * app's source of truth for dashboards and reports: screens ALWAYS compute
 * from these tables, and the network's only job is to top them up
 * ([com.magicbill.app.data.OwnerSync]). Last-synced data therefore stays
 * available forever, fully offline — and since the server archives bills
 * older than ~60 days, the phone can even hold more than the live backend.
 */

@Entity(
    tableName = "bills_local",
    indices = [Index("licenseKey", "dayIst")],
)
data class BillEntity(
    @PrimaryKey val id: String,
    val licenseKey: String,
    val billNumber: String?,
    val tokenNumber: Long?,
    val orderType: String?,
    val tableNumber: String?,
    val paymentMode: String?,
    val subtotal: Double?,
    val gst: Double?,
    val total: Double?,
    /** Raw items JSON exactly as the backend stores it. */
    val itemsJson: String?,
    /** Original ISO timestamp (kept for the receipt view). */
    val billedAt: String,
    val billedAtEpoch: Long,
    /** Precomputed IST day "YYYY-MM-DD" — range queries group on this. */
    val dayIst: String,
    val customerName: String?,
    val customerPhone: String?,
)

@Entity(tableName = "day_summaries_local", primaryKeys = ["licenseKey", "day"])
data class DaySummaryEntity(
    val licenseKey: String,
    val day: String,
    val billCount: Int,
    val subtotal: Double,
    val gst: Double,
    val total: Double,
    val cash: Double,
    val card: Double,
    val upi: Double,
    val credit: Double,
    val expenseTotal: Double,
)

@Entity(
    tableName = "expenses_local",
    indices = [Index("licenseKey", "dayIst")],
)
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val licenseKey: String,
    val amount: Double,
    val dayIst: String,
)

/** Per-license sync watermarks + the "Updated Xh ago" timestamp. */
@Entity(tableName = "owner_sync_state", primaryKeys = ["licenseKey"])
data class SyncStateEntity(
    val licenseKey: String,
    /** Keyset cursor: max bills.synced_at pulled so far (ISO). */
    val billsCursor: String?,
    /** Keyset cursor: max expenses.synced_at pulled so far (ISO). */
    val expensesCursor: String?,
    /** Epoch ms of the last fully successful sync. */
    val lastSyncAt: Long,
)

@Dao
interface OwnerLocalDao {

    // ---- bills ----

    @Upsert
    suspend fun upsertBills(bills: List<BillEntity>)

    @Query(
        "SELECT * FROM bills_local WHERE licenseKey = :licenseKey " +
            "AND dayIst BETWEEN :fromDay AND :toDay ORDER BY billedAtEpoch DESC LIMIT 2000",
    )
    suspend fun billsInRange(licenseKey: String, fromDay: String, toDay: String): List<BillEntity>

    @Query("SELECT * FROM bills_local WHERE id = :id")
    suspend fun bill(id: String): BillEntity?

    @Query("DELETE FROM bills_local WHERE dayIst < :beforeDay")
    suspend fun pruneBillsBefore(beforeDay: String)

    // ---- day summaries ----

    @Upsert
    suspend fun upsertSummaries(rows: List<DaySummaryEntity>)

    @Query(
        "SELECT * FROM day_summaries_local WHERE licenseKey = :licenseKey " +
            "AND day BETWEEN :fromDay AND :toDay ORDER BY day ASC",
    )
    suspend fun summariesInRange(licenseKey: String, fromDay: String, toDay: String): List<DaySummaryEntity>

    // ---- expenses ----

    @Upsert
    suspend fun upsertExpenses(rows: List<ExpenseEntity>)

    @Query(
        "SELECT COALESCE(SUM(amount), 0) FROM expenses_local WHERE licenseKey = :licenseKey " +
            "AND dayIst BETWEEN :fromDay AND :toDay",
    )
    suspend fun expenseTotalInRange(licenseKey: String, fromDay: String, toDay: String): Double

    // ---- sync state ----

    @Upsert
    suspend fun putSyncState(state: SyncStateEntity)

    @Query("SELECT * FROM owner_sync_state WHERE licenseKey = :licenseKey")
    suspend fun syncState(licenseKey: String): SyncStateEntity?

    // ---- wipe (logout) ----

    @Query("DELETE FROM bills_local")
    suspend fun clearBills()

    @Query("DELETE FROM day_summaries_local")
    suspend fun clearSummaries()

    @Query("DELETE FROM expenses_local")
    suspend fun clearExpenses()

    @Query("DELETE FROM owner_sync_state")
    suspend fun clearSyncState()
}
