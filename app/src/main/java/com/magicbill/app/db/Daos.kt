package com.magicbill.app.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {
    @Upsert suspend fun upsert(rows: List<BillRow>)

    @Query("SELECT * FROM bills WHERE restaurantId = :r AND businessDay BETWEEN :from AND :to ORDER BY createdAtMs DESC")
    fun between(r: String, from: String, to: String): Flow<List<BillRow>>

    @Query("SELECT * FROM bills WHERE restaurantId = :r AND id = :id")
    suspend fun byId(r: String, id: String): BillRow?

    @Query("SELECT * FROM bills WHERE restaurantId = :r AND businessDay BETWEEN :from AND :to AND (billNumber LIKE :q OR customerName LIKE :q OR tableName LIKE :q OR staffName LIKE :q) ORDER BY createdAtMs DESC LIMIT 200")
    fun search(r: String, from: String, to: String, q: String): Flow<List<BillRow>>

    @Query("SELECT COUNT(*) FROM bills WHERE restaurantId = :r")
    suspend fun count(r: String): Int

    @Query("SELECT MIN(businessDay) FROM bills WHERE restaurantId = :r")
    suspend fun earliestDay(r: String): String?
}

@Dao
interface TotalsDao {
    @Upsert suspend fun upsertDays(rows: List<DayTotalRow>)
    @Upsert suspend fun upsertItems(rows: List<DayItemTotalRow>)
    @Upsert suspend fun upsertCategories(rows: List<DayCategoryTotalRow>)

    @Query("SELECT * FROM day_totals WHERE restaurantId = :r AND businessDay BETWEEN :from AND :to ORDER BY businessDay")
    fun days(r: String, from: String, to: String): Flow<List<DayTotalRow>>

    @Query("SELECT * FROM day_item_totals WHERE restaurantId = :r AND businessDay BETWEEN :from AND :to")
    fun items(r: String, from: String, to: String): Flow<List<DayItemTotalRow>>

    @Query("SELECT * FROM day_category_totals WHERE restaurantId = :r AND businessDay BETWEEN :from AND :to")
    fun categories(r: String, from: String, to: String): Flow<List<DayCategoryTotalRow>>

    @Query("SELECT MIN(businessDay) FROM day_totals WHERE restaurantId = :r")
    suspend fun earliestDay(r: String): String?
}

@Dao
interface ExpenseDao {
    @Upsert suspend fun upsert(rows: List<ExpenseRow>)
    @Upsert suspend fun upsertCategories(rows: List<ExpenseCategoryRow>)

    @Query("SELECT * FROM expenses WHERE restaurantId = :r AND deleted = 0 AND businessDay BETWEEN :from AND :to ORDER BY createdAtMs DESC")
    fun between(r: String, from: String, to: String): Flow<List<ExpenseRow>>

    @Query("SELECT * FROM expense_categories WHERE restaurantId = :r AND deleted = 0 ORDER BY sortOrder, name")
    fun categories(r: String): Flow<List<ExpenseCategoryRow>>
}

@Dao
interface CashDao {
    @Upsert suspend fun upsert(rows: List<CashMovementRow>)

    @Query("SELECT * FROM cash_movements WHERE restaurantId = :r AND deleted = 0 AND businessDay BETWEEN :from AND :to ORDER BY createdAtMs DESC")
    fun between(r: String, from: String, to: String): Flow<List<CashMovementRow>>
}

@Dao
interface KhataDao {
    @Upsert suspend fun upsertCustomers(rows: List<CustomerRow>)
    @Upsert suspend fun upsertLedger(rows: List<LedgerRow>)

    @Query("SELECT * FROM customers WHERE restaurantId = :r AND deleted = 0 ORDER BY name")
    fun customers(r: String): Flow<List<CustomerRow>>

    @Query("SELECT * FROM customers WHERE restaurantId = :r AND id = :id")
    suspend fun customer(r: String, id: String): CustomerRow?

    @Query("SELECT * FROM customer_ledger WHERE restaurantId = :r AND customerId = :c ORDER BY atMs DESC")
    fun ledger(r: String, c: String): Flow<List<LedgerRow>>
}

@Dao
interface PeopleDao {
    @Upsert suspend fun upsertStaff(rows: List<StaffRow>)
    @Upsert suspend fun upsertRoles(rows: List<RoleRow>)

    @Query("SELECT * FROM staff WHERE restaurantId = :r AND deleted = 0 ORDER BY status, name")
    fun staff(r: String): Flow<List<StaffRow>>

    @Query("SELECT * FROM staff WHERE restaurantId = :r AND id = :id")
    suspend fun member(r: String, id: String): StaffRow?

    @Query("SELECT * FROM roles WHERE restaurantId = :r AND deleted = 0 ORDER BY name")
    fun roles(r: String): Flow<List<RoleRow>>
}

@Dao
interface MenuDao {
    @Upsert suspend fun upsertItems(rows: List<MenuItemRow>)
    @Upsert suspend fun upsertCategories(rows: List<MenuCategoryRow>)

    @Query("SELECT * FROM menu_items WHERE restaurantId = :r AND deleted = 0 ORDER BY sortOrder, name")
    fun items(r: String): Flow<List<MenuItemRow>>

    @Query("SELECT * FROM menu_categories WHERE restaurantId = :r AND deleted = 0 ORDER BY sortOrder, name")
    fun categories(r: String): Flow<List<MenuCategoryRow>>
}

@Dao
interface NoticeDao {
    @Upsert suspend fun upsert(rows: List<NoticeRow>)
    @Upsert suspend fun markRead(rows: List<NoticeReadRow>)

    @Query("SELECT * FROM notices WHERE deleted = 0 AND (forRestaurant IS NULL OR forRestaurant = :r) ORDER BY startsAtMs DESC")
    fun forShop(r: String): Flow<List<NoticeRow>>

    @Query("SELECT * FROM notice_reads")
    fun reads(): Flow<List<NoticeReadRow>>
}

@Dao
interface CursorDao {
    @Query("SELECT * FROM cursors WHERE restaurantId = :r AND tbl = :t")
    suspend fun get(r: String, t: String): CursorRow?

    @Query("SELECT * FROM cursors WHERE restaurantId = :r")
    fun all(r: String): Flow<List<CursorRow>>

    @Upsert suspend fun put(row: CursorRow)

    @Query("DELETE FROM cursors WHERE restaurantId = :r")
    suspend fun forget(r: String)
}

@Dao
interface IntentDao {
    @Upsert suspend fun put(row: IntentRow)

    @Query("SELECT * FROM intents WHERE id = :id")
    suspend fun byId(id: String): IntentRow?

    @Query("SELECT * FROM intents WHERE state = 'queued' ORDER BY createdMs")
    suspend fun queued(): List<IntentRow>

    @Query("SELECT COUNT(*) FROM intents WHERE state = 'queued'")
    fun queuedCount(): Flow<Int>

    @Query("SELECT * FROM intents ORDER BY createdMs DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<IntentRow>>

    @Query("SELECT * FROM intents WHERE state = 'held' ORDER BY createdMs")
    fun held(): Flow<List<IntentRow>>

    @Query("DELETE FROM intents WHERE state <> 'queued' AND createdMs < :olderThanMs")
    suspend fun prune(olderThanMs: Long)
}

@Dao
interface FloorDao {
    @Query("DELETE FROM floor_items") suspend fun clearItems()
    @Query("DELETE FROM floor_tables") suspend fun clearTables()
    @Upsert suspend fun putItems(rows: List<FloorItemRow>)
    @Upsert suspend fun putTables(rows: List<FloorTableRow>)

    @Transaction
    suspend fun replaceCatalogue(items: List<FloorItemRow>, tables: List<FloorTableRow>) {
        clearItems(); clearTables(); putItems(items); putTables(tables)
    }

    @Query("SELECT * FROM floor_items ORDER BY ord") fun items(): Flow<List<FloorItemRow>>
    @Query("SELECT * FROM floor_tables ORDER BY ord") fun tables(): Flow<List<FloorTableRow>>
    @Query("UPDATE floor_tables SET state = :state WHERE id = :id") suspend fun setTableState(id: String, state: String)
    @Query("UPDATE floor_items SET isAvailable = :available WHERE id = :id") suspend fun setAvailable(id: String, available: Boolean)

    @Upsert suspend fun putOrder(row: FloorOrderRow)
    @Upsert suspend fun putIntents(rows: List<IntentRow>)

    /** A whole order, staged in ONE write: its intents and the tile that shows it on the floor. */
    @Transaction
    suspend fun stage(intents: List<IntentRow>, pending: FloorOrderRow?) {
        putIntents(intents)
        if (pending != null) putOrder(pending)
    }

    /** Staged orders the counter has answered — their real rows have replaced them. */
    @Query("DELETE FROM floor_orders WHERE orderId LIKE 'pending_%' AND orderId NOT IN (SELECT 'pending_' || id FROM intents WHERE state = 'queued')")
    suspend fun dropPendingAnswered()
    @Query("UPDATE floor_orders SET sending = 0 WHERE sending = 1 AND orderId NOT LIKE 'pending_%'")
    suspend fun clearSending()
    @Query("SELECT * FROM floor_orders WHERE orderId = :id") suspend fun order(id: String): FloorOrderRow?
    @Query("SELECT * FROM floor_orders WHERE orderId = :id") fun orderFlow(id: String): Flow<FloorOrderRow?>
    @Query("SELECT * FROM floor_orders WHERE closedSays IS NULL ORDER BY updatedMs DESC") fun openOrders(): Flow<List<FloorOrderRow>>
    @Query("SELECT * FROM floor_orders WHERE tableId = :tableId AND closedSays IS NULL LIMIT 1") suspend fun openOnTable(tableId: String): FloorOrderRow?
    @Query("DELETE FROM floor_orders WHERE closedSays IS NOT NULL AND updatedMs < :olderThanMs") suspend fun pruneClosed(olderThanMs: Long)
    @Query("DELETE FROM floor_orders") suspend fun clearOrders()
}
