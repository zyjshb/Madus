package com.madus.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.madus.mobile.domain.MusicSourceType
import com.madus.mobile.domain.Track
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.recentStore by preferencesDataStore(name = "madus_recent")

/**
 * Persists recently played tracks + last known position per track.
 * Never auto-clears — only user actions or size cap trim.
 */
class RecentStore(private val context: Context) {
    private val keyTracks = stringPreferencesKey("recent_tracks_v1")
    private val keyPositions = stringPreferencesKey("recent_positions_v1")

    data class Entry(
        val track: Track,
        val positionMs: Long = 0L,
        val playedAtMs: Long = System.currentTimeMillis(),
    )

    suspend fun list(): List<Entry> {
        val raw = context.recentStore.data.first()[keyTracks].orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(o.toEntry())
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun tracks(): List<Track> = list().map { it.track }

    suspend fun push(track: Track, positionMs: Long = 0L) {
        val all = list().toMutableList()
        all.removeAll { it.track.id == track.id }
        all.add(
            0,
            Entry(
                track = track,
                positionMs = positionMs.coerceAtLeast(0L),
                playedAtMs = System.currentTimeMillis(),
            ),
        )
        while (all.size > 50) all.removeAt(all.lastIndex)
        save(all)
    }

    /** Update progress for a track already in recent (does not reorder). */
    suspend fun savePosition(trackId: String, positionMs: Long) {
        if (positionMs < 0 || trackId.isBlank()) return
        val all = list().toMutableList()
        val idx = all.indexOfFirst { it.track.id == trackId }
        if (idx < 0) {
            // 上下滑时可能尚未 push 进列表：只记进度，不造假曲目
            return
        }
        all[idx] = all[idx].copy(positionMs = positionMs.coerceAtLeast(0L))
        save(all)
    }

    suspend fun positionOf(trackId: String): Long =
        list().firstOrNull { it.track.id == trackId }?.positionMs ?: 0L

    suspend fun remove(trackId: String) {
        val next = list().filterNot { it.track.id == trackId }
        save(next)
    }

    suspend fun clear() {
        save(emptyList())
    }

    private suspend fun save(list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.track.id)
                    .put("title", e.track.title)
                    .put("artist", e.track.artist)
                    .put("album", e.track.album)
                    .put("coverUrl", e.track.coverUrl ?: "")
                    .put("durationMs", e.track.durationMs)
                    .put("source", e.track.source.id)
                    .put(
                        "streamUrl",
                        e.track.streamUrl?.takeIf {
                            it.startsWith("file:") || (it.startsWith("/") && !it.startsWith("http"))
                        }.orEmpty(),
                    )
                    .put("bvid", e.track.bvid)
                    .put("aid", e.track.aid)
                    .put("cid", e.track.cid)
                    .put("positionMs", e.positionMs)
                    .put("playedAtMs", e.playedAtMs),
            )
        }
        context.recentStore.edit { it[keyTracks] = arr.toString() }
    }

    private fun JSONObject.toEntry(): Entry {
        val sourceId = optString("source", "bilibili")
        val source = MusicSourceType.entries.find { it.id == sourceId }
            ?: MusicSourceType.BILIBILI
        val track = Track(
            id = optString("id"),
            title = optString("title"),
            artist = optString("artist"),
            album = optString("album"),
            coverUrl = optString("coverUrl").ifBlank { null },
            durationMs = optLong("durationMs"),
            source = source,
            streamUrl = optString("streamUrl").ifBlank { null }
                ?.takeIf {
                    it.startsWith("file:") || (it.startsWith("/") && !it.startsWith("http"))
                },
            bvid = optString("bvid"),
            aid = optString("aid"),
            cid = optString("cid"),
        )
        return Entry(
            track = track,
            positionMs = optLong("positionMs", 0L),
            playedAtMs = optLong("playedAtMs", 0L),
        )
    }
}
