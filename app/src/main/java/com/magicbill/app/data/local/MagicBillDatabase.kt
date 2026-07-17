package com.magicbill.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

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
    entities = [KvEntry::class],
    version = 2,
    exportSchema = false,
)
abstract class MagicBillDatabase : RoomDatabase() {
    abstract fun kvCacheDao(): KvCacheDao
}
