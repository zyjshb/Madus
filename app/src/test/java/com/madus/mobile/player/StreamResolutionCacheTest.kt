package com.madus.mobile.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StreamResolutionCacheTest {
    @Test fun playbackJoinsPrefetchEvenWhenPrefetchWaiterIsCancelled() = runBlocking {
        val owner = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val cache = StreamResolutionCache<String>(owner)
            val started = CompletableDeferred<Unit>()
            val ready = CompletableDeferred<Unit>()
            var calls = 0
            val prefetch = async {
                cache.resolve("song:audio:80") {
                    calls++; started.complete(Unit); ready.await(); "url"
                }
            }
            started.await()
            prefetch.cancel()
            val playback = async { cache.resolve("song:audio:80") { calls++; "duplicate" } }
            ready.complete(Unit)
            assertEquals("url", playback.await())
            assertEquals(1, calls)
        } finally { owner.cancel() }
    }

    @Test fun expiredAndForcedRequestsResolveAgain() = runBlocking {
        var clock = 0L
        val cache = StreamResolutionCache<String>(this, ttlMs = 100, now = { clock })
        var calls = 0
        suspend fun resolve(refresh: Boolean = false) = cache.resolve("song", refresh) { "url-${++calls}" }
        assertEquals("url-1", resolve())
        assertEquals("url-1", resolve())
        assertEquals("url-2", resolve(true))
        clock = 101
        assertEquals("url-3", resolve())
    }

    @Test fun failureDoesNotPoisonRetryAndDifferentModesStaySeparate() = runBlocking {
        val cache = StreamResolutionCache<String>(this)
        assertNull(cache.resolve("song:audio:80") { null })
        assertEquals("audio", cache.resolve("song:audio:80") { "audio" })
        assertEquals("video", cache.resolve("song:video:80") { "video" })
        assertEquals("low", cache.resolve("song:audio:32") { "low" })
        assertEquals("audio", cache.resolve("song:audio:80") { "wrong" })
    }

    @Test fun oldEntriesAreEvicted() = runBlocking {
        val cache = StreamResolutionCache<String>(this, capacity = 2)
        cache.resolve("a") { "old" }
        cache.resolve("b") { "b" }
        cache.resolve("c") { "c" }
        assertEquals("new", cache.resolve("a") { "new" })
    }

    @Test fun failedLoaderCanBeRetried() = runBlocking {
        val owner = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val cache = StreamResolutionCache<String>(owner)
            val failure = runCatching { cache.resolve("song") { error("offline") } }
            assertTrue(failure.isFailure)
            assertEquals("recovered", cache.resolve("song") { "recovered" })
        } finally { owner.cancel() }
    }
}
