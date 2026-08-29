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
    ],
    version = 3,
    exportSchema = false,
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

    /** Everything the cloud gave us about one shop, gone. Cursors too, so the next pull starts over. */
    suspend fun forgetShop(restaurantId: String) {
        clearAllTables()
    }

    companion object {
        const val NAME = "magicbill3.db"

        fun open(context: Context): MbDatabase =
            Room.databaseBuilder(context, MbDatabase::class.java, NAME)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()

        fun inMemory(context: Context): MbDatabase =
            Room.inMemoryDatabaseBuilder(context, MbDatabase::class.java).allowMainThreadQueries().build()
    }
}
