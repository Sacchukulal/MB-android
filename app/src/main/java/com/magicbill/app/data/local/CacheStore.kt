package com.magicbill.app.data.local

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class Cached<T>(val data: T, val updatedAt: Long)

/** Typed facade over the kv_cache table (+ full local-data wipe on logout). */
@Singleton
class CacheStore @Inject constructor(
    private val dao: KvCacheDao,
    private val ownerLocal: OwnerLocalDao,
    private val json: Json,
) {
    suspend fun <T> read(key: String, serializer: KSerializer<T>): Cached<T>? {
        val row = dao.get(key) ?: return null
        return runCatching {
            Cached(json.decodeFromString(serializer, row.json), row.updatedAt)
        }.getOrNull() // corrupted/outdated shape → treat as no cache
    }

    suspend fun <T> write(key: String, serializer: KSerializer<T>, value: T) {
        dao.put(KvEntry(key, json.encodeToString(serializer, value), System.currentTimeMillis()))
    }

    /** Logout wipe: kv cache AND the owner's local SQLite mirror. */
    suspend fun clearAll() {
        dao.clearAll()
        ownerLocal.clearBills()
        ownerLocal.clearSummaries()
        ownerLocal.clearExpenses()
        ownerLocal.clearSyncState()
    }
}

/**
 * The cache-first result every screen consumes:
 *  - [data] renders immediately (cache or fresh network)
 *  - [updatedAt] drives the "Updated Xh ago" chip
 *  - [fromCacheOnly] true when the latest refresh FAILED and cache is showing
 *  - [refreshing] true while a silent background refresh is in flight
 */
data class CachedUi<T>(
    val data: T? = null,
    val updatedAt: Long? = null,
    val fromCacheOnly: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
)
