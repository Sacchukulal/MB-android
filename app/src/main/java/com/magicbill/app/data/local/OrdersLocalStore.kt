package com.magicbill.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

/**
 * Local mirror for the live mobile-ordering feature: the menu/table/customer
 * catalog pushed by the POS, the open live orders, and the intents this phone
 * has submitted. Screens render these rows instantly (cache-first) while the
 * network refreshes behind.
 *
 * Everything is keyed by [scope] so a restaurant switch is clean:
 *   owner -> the license key; staff -> "staff:<restaurant code>"
 * (staff clients never hold the license key).
 */

@Entity(tableName = "menu_categories_local", primaryKeys = ["scope", "localId"])
data class MenuCategoryEntity(
    val scope: String,
    /** POS SQLite categories.id */
    val localId: Long,
    val name: String,
    val sortOrder: Long,
)

@Entity(tableName = "menu_items_local", primaryKeys = ["scope", "localId"])
data class MenuItemEntity(
    val scope: String,
    /** POS SQLite items.id */
    val localId: Long,
    val categoryLocalId: Long?,
    val name: String,
    val price: Double,
    /** false = "86"/out of stock — visible but not addable on the phone. */
    val isAvailable: Boolean,
)

@Entity(tableName = "restaurant_tables_local", primaryKeys = ["scope", "localId"])
data class RestaurantTableEntity(
    val scope: String,
    val localId: Long,
    val section: String,
    val label: String,
    val sortOrder: Long,
    val isActive: Boolean,
)

@Entity(tableName = "credit_customers_local", primaryKeys = ["scope", "localId"])
data class CreditCustomerEntity(
    val scope: String,
    val localId: Long,
    val name: String,
    val phone: String,
    val creditBalance: Double,
)

/**
 * Mirror of the cloud `live_orders` wire shape. [itemsJson]/[printedItemsJson]
 * hold the wire item list (OrderLine) encoded once by OrdersRepository — the
 * single wire<->app conversion point.
 */
@Entity(
    tableName = "live_orders_local",
    primaryKeys = ["scope", "clientUuid"],
    indices = [Index("scope", "status")],
)
data class LiveOrderEntity(
    val scope: String,
    val clientUuid: String,
    /** Cloud live_orders.id — null only for rows we created optimistically. */
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
    val itemsJson: String,
    val printedItemsJson: String,
    val subtotal: Double,
    val gst: Double,
    val total: Double,
    val printError: String,
    val createdByKind: String,
    val createdById: String?,
    val createdByName: String,
    val version: Long,
    val createdAt: String,
    val updatedAt: String,
    val billedAt: String?,
)

/**
 * Every intent this phone submitted (idempotency ledger + in-flight UI).
 * status: sending | pending | applied | rejected | failed
 * ("failed" = never accepted by the server — safe to retry with the SAME id).
 */
@Entity(
    tableName = "pending_events_local",
    indices = [Index("scope", "status")],
)
data class PendingEventEntity(
    @PrimaryKey val clientEventId: String,
    val scope: String,
    val kind: String,
    val orderClientUuid: String?,
    val payloadJson: String,
    val status: String,
    val rejectReason: String?,
    /** order_events.id once the server accepted it (drives event_status). */
    val serverEventId: String?,
    val createdAt: Long,
)

/** Per-scope bookkeeping: room id + versions for doorbell gap detection. */
@Entity(tableName = "orders_sync_state")
data class OrdersSyncStateEntity(
    @PrimaryKey val scope: String,
    /** Opaque realtime room id — never log it. */
    val roomId: String,
    val catalogVersion: Long,
    val ordersSeq: Long,
    val lastSyncAt: Long,
    val restaurantName: String,
)

@Dao
interface OrdersLocalDao {

    // ---- catalog (full replace per sync) ----

    @Query("SELECT * FROM menu_categories_local WHERE scope = :scope ORDER BY sortOrder, localId")
    suspend fun categories(scope: String): List<MenuCategoryEntity>

    @Query("SELECT * FROM menu_items_local WHERE scope = :scope ORDER BY name")
    suspend fun items(scope: String): List<MenuItemEntity>

    @Query("SELECT * FROM restaurant_tables_local WHERE scope = :scope ORDER BY sortOrder, localId")
    suspend fun tables(scope: String): List<RestaurantTableEntity>

    @Query("SELECT * FROM credit_customers_local WHERE scope = :scope ORDER BY name")
    suspend fun customers(scope: String): List<CreditCustomerEntity>

    @Upsert suspend fun upsertCategories(rows: List<MenuCategoryEntity>)
    @Upsert suspend fun upsertItems(rows: List<MenuItemEntity>)
    @Upsert suspend fun upsertTables(rows: List<RestaurantTableEntity>)
    @Upsert suspend fun upsertCustomers(rows: List<CreditCustomerEntity>)

    @Query("DELETE FROM menu_categories_local WHERE scope = :scope") suspend fun clearCategories(scope: String)
    @Query("DELETE FROM menu_items_local WHERE scope = :scope") suspend fun clearItems(scope: String)
    @Query("DELETE FROM restaurant_tables_local WHERE scope = :scope") suspend fun clearTables(scope: String)
    @Query("DELETE FROM credit_customers_local WHERE scope = :scope") suspend fun clearCustomers(scope: String)

    @Transaction
    suspend fun replaceCatalog(
        scope: String,
        categories: List<MenuCategoryEntity>,
        items: List<MenuItemEntity>,
        tables: List<RestaurantTableEntity>,
        customers: List<CreditCustomerEntity>,
    ) {
        clearCategories(scope); upsertCategories(categories)
        clearItems(scope); upsertItems(items)
        clearTables(scope); upsertTables(tables)
        clearCustomers(scope); upsertCustomers(customers)
    }

    // ---- live orders (server returns the full open set each time) ----

    @Query("SELECT * FROM live_orders_local WHERE scope = :scope ORDER BY createdAt DESC")
    suspend fun orders(scope: String): List<LiveOrderEntity>

    @Query("SELECT * FROM live_orders_local WHERE scope = :scope AND clientUuid = :clientUuid")
    suspend fun order(scope: String, clientUuid: String): LiveOrderEntity?

    @Upsert suspend fun upsertOrders(rows: List<LiveOrderEntity>)

    @Query("DELETE FROM live_orders_local WHERE scope = :scope") suspend fun clearOrders(scope: String)

    @Transaction
    suspend fun replaceOrders(scope: String, rows: List<LiveOrderEntity>) {
        clearOrders(scope)
        upsertOrders(rows)
    }

    // ---- submitted events ----

    @Upsert suspend fun putEvent(row: PendingEventEntity)

    @Query("SELECT * FROM pending_events_local WHERE clientEventId = :clientEventId")
    suspend fun event(clientEventId: String): PendingEventEntity?

    @Query(
        "SELECT * FROM pending_events_local WHERE scope = :scope " +
            "AND status IN ('sending', 'pending') ORDER BY createdAt ASC",
    )
    suspend fun openEvents(scope: String): List<PendingEventEntity>

    @Query("DELETE FROM pending_events_local WHERE createdAt < :beforeEpochMs")
    suspend fun pruneEventsBefore(beforeEpochMs: Long)

    // ---- sync state ----

    @Upsert suspend fun putSyncState(state: OrdersSyncStateEntity)

    @Query("SELECT * FROM orders_sync_state WHERE scope = :scope")
    suspend fun syncState(scope: String): OrdersSyncStateEntity?

    // ---- wipes ----

    /** Restaurant switch: drop the old scope's rows before re-bootstrapping. */
    @Transaction
    suspend fun clearScope(scope: String) {
        clearCategories(scope); clearItems(scope); clearTables(scope); clearCustomers(scope)
        clearOrders(scope)
        clearEventsForScope(scope)
        clearSyncStateForScope(scope)
    }

    @Query("DELETE FROM pending_events_local WHERE scope = :scope")
    suspend fun clearEventsForScope(scope: String)

    @Query("DELETE FROM orders_sync_state WHERE scope = :scope")
    suspend fun clearSyncStateForScope(scope: String)

    /** Logout wipe (called from CacheStore.clearAll). */
    @Query("DELETE FROM menu_categories_local") suspend fun wipeCategories()
    @Query("DELETE FROM menu_items_local") suspend fun wipeItems()
    @Query("DELETE FROM restaurant_tables_local") suspend fun wipeTables()
    @Query("DELETE FROM credit_customers_local") suspend fun wipeCustomers()
    @Query("DELETE FROM live_orders_local") suspend fun wipeOrders()
    @Query("DELETE FROM pending_events_local") suspend fun wipeEvents()
    @Query("DELETE FROM orders_sync_state") suspend fun wipeSyncState()

    @Transaction
    suspend fun wipeAll() {
        wipeCategories(); wipeItems(); wipeTables(); wipeCustomers()
        wipeOrders(); wipeEvents(); wipeSyncState()
    }
}
