package com.magicbill.app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The phone's own copy. Version 2 of the rebuilt app (the floor became the whole floor): the old app's database is a different
 * file and is left where it is; this one is filled by the mirror in seconds.
 */
@Database(
    entities = [
        BillRow::class, DayTotalRow::class, DayItemTotalRow::class, DayCategoryTotalRow::class,
        ExpenseRow::class, ExpenseCategoryRow::class, CashMovementRow::class,
        CustomerRow::class, LedgerRow::class, StaffRow::class, RoleRow::class,
        MenuItemRow::class, MenuCategoryRow::class, NoticeRow::class, NoticeReadRow::class,
        CursorRow::class, IntentRow::class, FloorItemRow::class, FloorTableRow::class, FloorOrderRow::class,
        ArchiveDayRow::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class MbDatabase : RoomDatabase() {
    abstract fun bills(): BillDao
    abstract fun totals(): TotalsDao
    abstract fun expenses(): ExpenseDao
    abstract fun cash(): CashDao
    abstract fun khata(): KhataDao
    abstract fun people(): PeopleDao
    abstract fun menu(): MenuDao
    abstract fun notices(): NoticeDao
    abstract fun cursors(): CursorDao
    abstract fun intents(): IntentDao
    abstract fun floor(): FloorDao
    abstract fun archive(): ArchiveDao

    /** Everything the cloud gave us about one shop, gone. Cursors and the archive ledger too, so the next pull starts over. */
    suspend fun forgetShop(restaurantId: String) {
        clearAllTables()
    }

    companion object {
        const val NAME = "magicbill3.db"

        /**
         * Every step from 4 on is a real migration: with the whole bill history on the phone a
         * wipe is a full re-download. Versions 1–2 were development builds without migrations;
         * version 3 has its own migration and must not also be listed for destructive reset.
         */
        fun open(context: Context): MbDatabase =
            Room.databaseBuilder(context, MbDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2)
                .build()

        /** 3 → 4: a phone may ask the counter to settle a bill; the floor says which ones it did. */
        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE floor_orders ADD COLUMN settleAsked INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE floor_orders ADD COLUMN minutes INTEGER")
            }
        }

        /** Room's own SQL for [ArchiveDayRow] (schemas/5.json); the hygiene test holds the two together. */
        internal const val ARCHIVE_DAYS_SQL = "CREATE TABLE IF NOT EXISTS `archive_days` (`restaurantId` TEXT NOT NULL, `businessDay` TEXT NOT NULL, `objectUpdatedAt` TEXT NOT NULL, `bills` INTEGER NOT NULL, `importedAtMs` INTEGER NOT NULL, PRIMARY KEY(`restaurantId`, `businessDay`))"

        /** 4 → 5: the day files this phone has imported ([ArchiveDayRow]). */
        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(ARCHIVE_DAYS_SQL)
            }
        }

        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE intents ADD COLUMN openIntentId TEXT")
                db.execSQL("ALTER TABLE floor_orders ADD COLUMN seat TEXT")
                // Old staged orders wrote all their operations at the same instant and in row order.
                // Include answered opens so a partially recorded reply keeps its dependency too.
                var opening: String? = null
                var openedAt: Long? = null
                db.query("SELECT id, orderId, what, createdMs FROM intents ORDER BY createdMs, rowid").use { rows ->
                    while (rows.moveToNext()) {
                        val what = com.magicbill.app.core.parseJsonOrNull(rows.getString(2)) as? kotlinx.serialization.json.JsonObject
                        val action = (what?.get("do") as? kotlinx.serialization.json.JsonPrimitive)?.content
                        val at = rows.getLong(3)
                        if (action == "open_order") { opening = rows.getString(0); openedAt = at }
                        if (rows.isNull(1) && opening != null && openedAt == at) {
                            db.execSQL("UPDATE intents SET openIntentId = ? WHERE id = ?", arrayOf(opening, rows.getString(0)))
                        }
                    }
                }
            }
        }

        val MIGRATIONS: Array<androidx.room.migration.Migration> = arrayOf(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)

        fun inMemory(context: Context): MbDatabase =
            Room.inMemoryDatabaseBuilder(context, MbDatabase::class.java).allowMainThreadQueries().build()
    }
}
