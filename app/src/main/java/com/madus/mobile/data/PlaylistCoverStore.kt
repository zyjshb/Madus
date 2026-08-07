package com.madus.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONObject

private val Context.coverStore by preferencesDataStore(name = "madus_playlist_covers")

/**
 * User-uploaded covers for any playlist id (local + B站收藏夹).
 * Keyed by playlist id → absolute file path.
 */
class PlaylistCoverStore(private val context: Context) {
    private val keyMap = stringPreferencesKey("cover_map_v1")

    suspend fun get(playlistId: String): String? {
        val map = load()
        return map[playlistId]?.ifBlank { null }
    }

    suspend fun set(playlistId: String, path: String?) {
        val map = load().toMutableMap()
        if (path.isNullOrBlank()) map.remove(playlistId)
        else map[playlistId] = path
        save(map)
    }

    suspend fun all(): Map<String, String> = load()

    private suspend fun load(): Map<String, String> {
        val raw = context.coverStore.data.first()[keyMap].orEmpty()
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            buildMap {
                o.keys().forEach { k ->
                    val v = o.optString(k, "")
                    if (v.isNotBlank()) put(k, v)
                }
            }
        }.getOrDefault(emptyMap())
    }

    private suspend fun save(map: Map<String, String>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        context.coverStore.edit { it[keyMap] = o.toString() }
    }
}
