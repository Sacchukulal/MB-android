package com.magicbill.app.data

import com.magicbill.app.data.local.CacheStore
import com.magicbill.app.data.local.CachedUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The cache-first engine (the anti-glitch rule, mechanized):
 *  1. emit cached data IMMEDIATELY when present — screens render instantly
 *  2. fetch in the background and overwrite both cache and UI when it lands
 *  3. on network failure, keep showing cache and quietly flag `fromCacheOnly`
 * A loading state exists only when there has never been a successful fetch.
 */
@Singleton
class CachedQuery @Inject constructor(
    private val cache: CacheStore,
) {
    fun <T> run(
        scope: CoroutineScope,
        key: String,
        serializer: KSerializer<T>,
        state: MutableStateFlow<CachedUi<T>>,
        fetch: suspend () -> T,
    ): Job = scope.launch {
        val cached = cache.read(key, serializer)
        if (cached != null) {
            state.value = CachedUi(
                data = cached.data,
                updatedAt = cached.updatedAt,
                refreshing = true,
            )
        } else {
            state.value = CachedUi(refreshing = true)
        }

        try {
            val fresh = fetch()
            cache.write(key, serializer, fresh)
            state.value = CachedUi(
                data = fresh,
                updatedAt = System.currentTimeMillis(),
                refreshing = false,
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("MB/Cache", "[CACHE] refresh failed for $key: ${e::class.simpleName}")
            val current = state.value
            state.value = current.copy(
                refreshing = false,
                fromCacheOnly = current.data != null,
                error = if (current.data == null) friendlyError(e) else null,
            )
        }
    }

    companion object {
        fun friendlyError(e: Exception): String = com.magicbill.app.core.MBErrors.network(e)
    }
}

typealias CachedStateFlow<T> = StateFlow<CachedUi<T>>
