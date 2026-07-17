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
        } catch (e: Exception) {
            val current = state.value
            state.value = current.copy(
                refreshing = false,
                fromCacheOnly = current.data != null,
                error = if (current.data == null) friendlyError(e) else null,
            )
        }
    }

    companion object {
        fun friendlyError(e: Exception): String = when {
            e is java.io.IOException || e.message?.contains("timeout", true) == true ->
                "Couldn't reach the server. Check your internet and try again."
            else -> "Something went wrong — pull to retry."
        }
    }
}

typealias CachedStateFlow<T> = StateFlow<CachedUi<T>>
