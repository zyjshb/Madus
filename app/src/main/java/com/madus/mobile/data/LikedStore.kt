package com.madus.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.madus.mobile.domain.MusicSourceType
import com.madus.mobile.domain.Playlist
import com.madus.mobile.domain.Track
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.likedStore by preferencesDataStore(name = "madus_liked")

/**
 * System playlist「我的喜欢」— 用户点赞的歌。
 * Fixed id [LIKED_ID], always available on home.
 */
class LikedStore(private val context: Context) {
    private val keyJson = stringPreferencesKey("liked_tracks_v1")
    private val keyCover = stringPreferencesKey("liked_cover_path")

    /** A local like is also a strong, time-sensitive recommendation signal. */
    data class Interaction(
        val track: Track,
        val likedAtMs: Long,
    )

    companion object {
        const val LIKED_ID = "local-liked"
        const val LIKED_TITLE = "我的喜欢"
    }

    suspend fun interactions(): List<Interaction> {
        val raw = context.likedStore.data.first()[keyJson].orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val t = arr.optJSONObject(i) ?: continue
                    // Old installs have no timestamp. Keep their existing order, but do not
                    // mistake every historic like for a brand-new hourly interaction.
                    val likedAtMs = t.optLong("likedAtMs", Long.MIN_VALUE + i)
                    add(Interaction(t.toTrack(), likedAtMs))
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun tracks(): List<Track> = interactions().map { it.track }

    suspend fun ids(): Set<String> = tracks().map { it.id }.toSet()

    suspend fun contains(trackId: String): Boolean = tracks().any { it.id == trackId }

    /** Toggle like; returns true if now liked. */
    suspend fun toggle(track: Track): Boolean {
        val all = interactions().toMutableList()
        val idx = all.indexOfFirst { it.track.id == track.id }
        return if (idx >= 0) {
            all.removeAt(idx)
            save(all)
            false
        } else {
            all.add(0, Interaction(track, System.currentTimeMillis()))
            save(all)
            true
        }
    }

    suspend fun add(track: Track) {
        val all = interactions().toMutableList()
        if (all.any { it.track.id == track.id }) return
        all.add(0, Interaction(track, System.currentTimeMillis()))
        save(all)
    }

    suspend fun remove(trackId: String) {
        save(interactions().filterNot { it.track.id == trackId })
    }

    suspend fun coverPath(): String? =
        context.likedStore.data.first()[keyCover]?.ifBlank { null }

    suspend fun setCover(path: String?) {
        context.likedStore.edit {
            if (path.isNullOrBlank()) it.remove(keyCover)
            else it[keyCover] = path
        }
    }

    suspend fun toPlaylist(): Playlist {
        val list = tracks()
        val custom = coverPath()
        return Playlist(
            id = LIKED_ID,
            title = LIKED_TITLE,
            trackCount = list.size,
            source = MusicSourceType.LOCAL_DEMO,
            coverUrl = custom ?: list.firstOrNull()?.coverUrl,
        )
    }

    private suspend fun save(list: List<Interaction>) {
        val arr = JSONArray()
        list.forEach { interaction ->
            val t = interaction.track
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("title", t.title)
                    .put("artist", t.artist)
                    .put("album", t.album)
                    .put("coverUrl", t.coverUrl ?: "")
                    .put("durationMs", t.durationMs)
                    .put("source", t.source.id)
                    .put(
                        "streamUrl",
                        t.streamUrl?.takeIf {
                            it.startsWith("file:") || (it.startsWith("/") && !it.startsWith("http"))
                        }.orEmpty(),
                    )
                    .put("bvid", t.bvid)
                    .put("aid", t.aid)
                    .put("cid", t.cid)
                    .put("likedAtMs", interaction.likedAtMs),
            )
        }
        context.likedStore.edit { it[keyJson] = arr.toString() }
    }

    private fun JSONObject.toTrack(): Track {
        val sourceId = optString("source", "bilibili")
        val source = MusicSourceType.entries.find { it.id == sourceId }
            ?: MusicSourceType.BILIBILI
        return Track(
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
    }
}
