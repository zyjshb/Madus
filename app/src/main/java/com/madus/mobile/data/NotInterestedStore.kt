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

private val Context.notInterestedStore by preferencesDataStore(name = "madus_not_interested")

/**
 * 点过「不喜欢」的歌。用来撤销，也给「我的」列表用。
 */
class NotInterestedStore(private val context: Context) {
    private val keyJson = stringPreferencesKey("hidden_tracks_v1")

    suspend fun tracks(): List<Track> {
        val raw = context.notInterestedStore.data.first()[keyJson].orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(o.toTrack())
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun ids(): Set<String> = tracks().map { it.id }.toSet()

    suspend fun contains(trackId: String): Boolean = tracks().any { it.id == trackId }

    suspend fun add(track: Track) {
        val all = tracks().toMutableList()
        all.removeAll { it.id == track.id || (track.bvid.isNotBlank() && it.bvid == track.bvid) }
        all.add(0, track)
        while (all.size > LIMIT) all.removeAt(all.lastIndex)
        save(all)
    }

    suspend fun remove(trackId: String, bvid: String = "") {
        save(
            tracks().filterNot {
                it.id == trackId || (bvid.isNotBlank() && it.bvid == bvid)
            },
        )
    }

    private suspend fun save(list: List<Track>) {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("title", t.title)
                    .put("artist", t.artist)
                    .put("album", t.album)
                    .put("coverUrl", t.coverUrl ?: "")
                    .put("durationMs", t.durationMs)
                    .put("source", t.source.id)
                    .put("bvid", t.bvid)
                    .put("aid", t.aid)
                    .put("cid", t.cid)
                    .put("ownerMid", t.ownerMid)
                    .put("categoryId", t.categoryId)
                    .put("categoryName", t.categoryName),
            )
        }
        context.notInterestedStore.edit { it[keyJson] = arr.toString() }
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
            bvid = optString("bvid"),
            aid = optString("aid"),
            cid = optString("cid"),
            ownerMid = optString("ownerMid"),
            categoryId = optInt("categoryId"),
            categoryName = optString("categoryName"),
        )
    }

    companion object {
        private const val LIMIT = 80
    }
}
