package com.madus.mobile.player

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * B 站音频流本地缓存（边听边缓存 + 手动预缓存）。
 * 只存用户自己请求过的流，不批量扒站。
 */
object StreamCache {
    @Volatile
    private var cache: SimpleCache? = null

    private const val MAX_BYTES = 512L * 1024L * 1024L // 512 MB

    fun get(context: Context): SimpleCache {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val dir = File(context.applicationContext.cacheDir, "bili_audio_cache").also { it.mkdirs() }
            val db = StandaloneDatabaseProvider(context.applicationContext)
            val c = SimpleCache(dir, LeastRecentlyUsedCacheEvictor(MAX_BYTES), db)
            cache = c
            return c
        }
    }

    fun usageBytes(context: Context): Long {
        val dir = File(context.applicationContext.cacheDir, "bili_audio_cache")
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun clear(context: Context) {
        synchronized(this) {
            runCatching { cache?.release() }
            cache = null
            val dir = File(context.applicationContext.cacheDir, "bili_audio_cache")
            if (dir.exists()) dir.deleteRecursively()
        }
    }

    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "${bytes}B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.2f GB".format(mb / 1024.0)
    }
}
