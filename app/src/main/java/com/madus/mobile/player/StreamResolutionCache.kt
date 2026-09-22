package com.madus.mobile.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/** Small, session-only cache. Playback can join a prefetch without starting another request. */
internal class StreamResolutionCache<T : Any>(
    private val scope: CoroutineScope,
    private val ttlMs: Long = 120_000L,
    private val capacity: Int = 24,
    private val now: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private val lock = Any()
    private val cached = LinkedHashMap<String, Pair<Long, T>>()
    private val pending = mutableMapOf<String, Deferred<T?>>()

    suspend fun resolve(key: String, refresh: Boolean = false, load: suspend () -> T?): T? {
        val request = synchronized(lock) {
            if (refresh) cached.remove(key)
            cached[key]?.let { (savedAt, value) ->
                if (now() - savedAt < ttlMs) return value
                cached.remove(key)
            }
            pending[key] ?: scope.async(start = CoroutineStart.LAZY) {
                try {
                    load()?.also { value ->
                        synchronized(lock) {
                            cached.remove(key)
                            cached[key] = now() to value
                            while (cached.size > capacity) cached.remove(cached.keys.first())
                        }
                    }
                } finally {
                    synchronized(lock) { pending.remove(key) }
                }
            }.also { pending[key] = it }
        }
        // The ViewModel owns the work: cancellation of one waiter must not cancel other waiters.
        request.start()
        return request.await()
    }
}
