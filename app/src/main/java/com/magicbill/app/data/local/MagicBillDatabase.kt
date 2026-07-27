package com.magicbill.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Offline cache. One generic JSON KV table backs the cache-first pattern:
 * every dataset a screen renders is stored under a stable key
 * ("dashboard.<license>", "report.<license>.<from>.<to>", "account.<license>",
 * "staff.dashboard", …) with its fetch timestamp. Screens render the cached
 * row instantly, then a background refresh overwrites it.
 */
@Entity(tableName = "kv_cache")
data class KvEntry(
    @PrimaryKey val key: String,
    val json: String,
    val updatedAt: Long,
)

@Dao
interface KvCacheDao {
    @Query("SELECT * FROM kv_cache WHERE `key` = :key")
    suspend fun get(key: String): KvEntry?

    @Upsert
    suspend fun put(entry: KvEntry)

    @Query("DELETE FROM kv_cache WHERE `key` = :key")
    suspend fun remove(key: String)

    @Query("DELETE FROM kv_cache")
    suspend fun clearAll()
}

@Database(
    entities = [
        KvEntry::class,
        BillEntity::class,
        DaySummaryEntity::class,
        ExpenseEntity::class,
        SyncStateEntity::class,
        MenuCategoryEntity::class,
        MenuItemEntity::class,
        RestaurantTableEntity::class,
        CreditCustomerEntity::class,
        LiveOrderEntity::class,
        PendingEventEntity::class,
        OrdersSyncStateEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class MagicBillDatabase : RoomDatabase() {
    abstract fun kvCacheDao(): KvCacheDao
    abstract fun ownerLocalDao(): OwnerLocalDao
    abstract fun ordersLocalDao(): OrdersLocalDao

    companion object {
        /**
         * 3 -> 4: CREATE the mobile-orders tables only. MUST stay a real,
         * additive migration — the destructive fallback would wipe
         * bills_local/day_summaries_local and destroy the user's offline
         * mirror on upgrade.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `menu_categories_local` (" +
                        "`scope` TEXT NOT NULL, `localId` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`scope`, `localId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `menu_items_local` (" +
                        "`scope` TEXT NOT NULL, `localId` INTEGER NOT NULL, " +
                        "`categoryLocalId` INTEGER, `name` TEXT NOT NULL, " +
                        "`price` REAL NOT NULL, `isAvailable` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`scope`, `localId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `restaurant_tables_local` (" +
                        "`scope` TEXT NOT NULL, `localId` INTEGER NOT NULL, " +
                        "`section` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`scope`, `localId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `credit_customers_local` (" +
                        "`scope` TEXT NOT NULL, `localId` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, `phone` TEXT NOT NULL, " +
                        "`creditBalance` REAL NOT NULL, " +
                        "PRIMARY KEY(`scope`, `localId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `live_orders_local` (" +
                        "`scope` TEXT NOT NULL, `clientUuid` TEXT NOT NULL, " +
                        "`serverId` TEXT, `status` TEXT NOT NULL, " +
                        "`pendingKot` INTEGER NOT NULL, `orderType` TEXT NOT NULL, " +
                        "`tableNumber` TEXT NOT NULL, `section` TEXT NOT NULL, " +
                        "`tokenNumber` INTEGER, `billNumber` TEXT, " +
                        "`customerName` TEXT NOT NULL, `customerPhone` TEXT NOT NULL, " +
                        "`customerLocalId` INTEGER, `paymentMode` TEXT NOT NULL, " +
                        "`itemsJson` TEXT NOT NULL, `printedItemsJson` TEXT NOT NULL, " +
                        "`subtotal` REAL NOT NULL, `gst` REAL NOT NULL, " +
                        "`total` REAL NOT NULL, `printError` TEXT NOT NULL, " +
                        "`createdByKind` TEXT NOT NULL, `createdById` TEXT, " +
                        "`createdByName` TEXT NOT NULL, `version` INTEGER NOT NULL, " +
                        "`createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, " +
                        "`billedAt` TEXT, " +
                        "PRIMARY KEY(`scope`, `clientUuid`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_live_orders_local_scope_status` " +
                        "ON `live_orders_local` (`scope`, `status`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_events_local` (" +
                        "`clientEventId` TEXT NOT NULL, `scope` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, `orderClientUuid` TEXT, " +
                        "`payloadJson` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                        "`rejectReason` TEXT, `serverEventId` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`clientEventId`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pending_events_local_scope_status` " +
                        "ON `pending_events_local` (`scope`, `status`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `orders_sync_state` (" +
                        "`scope` TEXT NOT NULL, `roomId` TEXT NOT NULL, " +
                        "`catalogVersion` INTEGER NOT NULL, `ordersSeq` INTEGER NOT NULL, " +
                        "`lastSyncAt` INTEGER NOT NULL, `restaurantName` TEXT NOT NULL, " +
                        "PRIMARY KEY(`scope`))",
                )
            }
        }
    }
}
