package com.madus.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.madus.mobile.domain.MusicSourceType
import com.madus.mobile.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private val Context.trackCacheStore by preferencesDataStore(name = "madus_track_cache_index")

/**
 * 显式「缓存此曲」：把当前可播流落到 app 私有目录，仅用户点过的曲。
 * 不批量扒库，符合个人使用缓存语义。
 */
class TrackCacheStore(private val context: Context) {
    private val keyJson = stringPreferencesKey("cached_tracks_v1")

    data class CachedTrack(
        val track: Track,
        val filePath: String,
        val bytes: Long,
        val cachedAtMs: Long,
    )

    suspend fun list(): List<CachedTrack> {
        val raw = context.trackCacheStore.data.first()[keyJson].orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val path = o.optString("path", "")
                    if (path.isBlank() || !File(path).exists()) continue
                    add(
                        CachedTrack(
                            track = o.toTrack(path),
                            filePath = path,
                            bytes = o.optLong("bytes", File(path).length()),
                            cachedAtMs = o.optLong("at", 0L),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun isCached(trackId: String): Boolean = list().any { it.track.id == trackId }

    suspend fun getLocalPath(trackId: String): String? =
        list().firstOrNull { it.track.id == trackId }?.filePath

    /**
     * 下载 streamUrl 到本地。
     * @return 成功路径；失败抛异常
     */
    suspend fun cacheTrack(track: Track, streamUrl: String): String = withContext(Dispatchers.IO) {
        require(streamUrl.isNotBlank()) { "无流地址" }
        val dir = File(context.filesDir, "bili_offline").also { it.mkdirs() }
        val safe = track.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val out = File(dir, "$safe.m4s")
        val conn = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            )
            setRequestProperty("Referer", "https://www.bilibili.com/")
            setRequestProperty("Origin", "https://www.bilibili.com")
        }
        conn.inputStream.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        val path = out.absolutePath
        val entry = CachedTrack(
            track = track.copy(streamUrl = path),
            filePath = path,
            bytes = out.length(),
            cachedAtMs = System.currentTimeMillis(),
        )
        val all = list().filterNot { it.track.id == track.id } + entry
        save(all)
        path
    }

    suspend fun remove(trackId: String) {
        val all = list().toMutableList()
        val hit = all.firstOrNull { it.track.id == trackId }
        if (hit != null) {
            runCatching { File(hit.filePath).delete() }
            save(all.filterNot { it.track.id == trackId })
        }
    }

    suspend fun clearAll() {
        list().forEach { runCatching { File(it.filePath).delete() } }
        save(emptyList())
        runCatching { File(context.filesDir, "bili_offline").deleteRecursively() }
    }

    suspend fun totalBytes(): Long = list().sumOf { it.bytes }

    private suspend fun save(list: List<CachedTrack>) {
        val arr = JSONArray()
        list.forEach { c ->
            val t = c.track
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("title", t.title)
                    .put("artist", t.artist)
                    .put("album", t.album)
                    .put("cover", t.coverUrl.orEmpty())
                    .put("duration", t.durationMs)
                    .put("bvid", t.bvid)
                    .put("aid", t.aid)
                    .put("cid", t.cid)
                    .put("path", c.filePath)
                    .put("bytes", c.bytes)
                    .put("at", c.cachedAtMs),
            )
        }
        context.trackCacheStore.edit { it[keyJson] = arr.toString() }
    }

    private fun JSONObject.toTrack(path: String): Track = Track(
        id = optString("id", ""),
        title = optString("title", ""),
        artist = optString("artist", ""),
        album = optString("album", ""),
        coverUrl = optString("cover", "").ifBlank { null },
        durationMs = optLong("duration", 0L),
        source = MusicSourceType.BILIBILI,
        streamUrl = path,
        bvid = optString("bvid", ""),
        aid = optString("aid", ""),
        cid = optString("cid", ""),
    )
}
