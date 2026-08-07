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
import java.util.UUID

private val Context.localPlStore by preferencesDataStore(name = "madus_local_playlists")

/**
 * Local playlists (desktop-like create/add), stored as JSON in DataStore.
 * Key mirrors spirit of madus-local-playlists-v1.
 */
class LocalPlaylistStore(private val context: Context) {
    private val keyJson = stringPreferencesKey("local_playlists_v1")

    data class LocalPlaylist(
        val id: String,
        val title: String,
        val tracks: List<Track>,
        /** User-uploaded cover path (file:// or content cached path). */
        val customCoverPath: String? = null,
    ) {
        fun toPlaylist(): Playlist = Playlist(
            id = id,
            title = title,
            trackCount = tracks.size,
            source = MusicSourceType.LOCAL_DEMO,
            coverUrl = customCoverPath ?: tracks.firstOrNull()?.coverUrl,
        )
    }

    suspend fun list(): List<LocalPlaylist> {
        val raw = context.localPlStore.data.first()[keyJson].orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(o.toLocalPlaylist())
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun create(title: String): LocalPlaylist {
        val pl = LocalPlaylist(
            id = "local-${UUID.randomUUID()}",
            title = title.ifBlank { "新建歌单" },
            tracks = emptyList(),
            customCoverPath = null,
        )
        val all = list().toMutableList()
        all.add(0, pl)
        save(all)
        return pl
    }

    /** Only playlists that already have at least one track (home visibility). */
    suspend fun listNonEmpty(): List<LocalPlaylist> =
        list().filter { it.tracks.isNotEmpty() }

    /**
     * Remove empty junk playlists left by old home 「+ 新建」auto names
     * like「我的歌单 604」. Real empty user-named lists can stay if [aggressive]=false.
     */
    suspend fun purgeEmptyJunk(aggressive: Boolean = true): Int {
        val before = list()
        val kept = before.filter { pl ->
            if (pl.tracks.isNotEmpty()) return@filter true
            if (!aggressive) return@filter true
            // drop empty auto-generated names
            val t = pl.title.trim()
            val junk = t.matches(Regex("""我的歌单\s*\d+""")) ||
                t.matches(Regex("""新建歌单\s*\d*""")) ||
                t == "新建歌单" ||
                t.matches(Regex("""歌单\s*\d+"""))
            !junk
        }
        if (kept.size != before.size) save(kept)
        return before.size - kept.size
    }

    /** Delete all empty playlists (collect sheet cleanup option). */
    suspend fun purgeAllEmpty(): Int {
        val before = list()
        val kept = before.filter { it.tracks.isNotEmpty() }
        if (kept.size != before.size) save(kept)
        return before.size - kept.size
    }

    suspend fun setCover(playlistId: String, coverPath: String?): Boolean {
        val all = list().toMutableList()
        val idx = all.indexOfFirst { it.id == playlistId }
        if (idx < 0) return false
        all[idx] = all[idx].copy(customCoverPath = coverPath?.ifBlank { null })
        save(all)
        return true
    }

    suspend fun addTrack(playlistId: String, track: Track): Boolean {
        val all = list().toMutableList()
        val idx = all.indexOfFirst { it.id == playlistId }
        if (idx < 0) return false
        val pl = all[idx]
        if (pl.tracks.any { it.id == track.id }) return true
        all[idx] = pl.copy(tracks = pl.tracks + track)
        save(all)
        return true
    }

    suspend fun tracks(playlistId: String): List<Track> =
        list().firstOrNull { it.id == playlistId }?.tracks.orEmpty()

    suspend fun delete(playlistId: String) {
        save(list().filterNot { it.id == playlistId })
    }

    suspend fun rename(playlistId: String, newTitle: String): Boolean {
        val title = newTitle.trim()
        if (title.isEmpty()) return false
        val all = list().toMutableList()
        val idx = all.indexOfFirst { it.id == playlistId }
        if (idx < 0) return false
        all[idx] = all[idx].copy(title = title)
        save(all)
        return true
    }

    suspend fun removeTrack(playlistId: String, trackId: String): Boolean {
        val all = list().toMutableList()
        val idx = all.indexOfFirst { it.id == playlistId }
        if (idx < 0) return false
        val pl = all[idx]
        all[idx] = pl.copy(tracks = pl.tracks.filterNot { it.id == trackId })
        save(all)
        return true
    }

    /** 追加多首（导入歌单）；同 id 跳过 */
    suspend fun addTracks(playlistId: String, tracks: List<Track>): Int {
        if (tracks.isEmpty()) return 0
        val all = list().toMutableList()
        val idx = all.indexOfFirst { it.id == playlistId }
        if (idx < 0) return 0
        val pl = all[idx]
        val existing = pl.tracks.map { it.id }.toHashSet()
        val merged = pl.tracks.toMutableList()
        var added = 0
        for (t in tracks) {
            if (t.id in existing) continue
            existing.add(t.id)
            merged.add(t)
            added++
        }
        if (added > 0) {
            all[idx] = pl.copy(tracks = merged)
            save(all)
        }
        return added
    }

    /** 长按排序：把 from 移到 to */
    suspend fun moveTrack(playlistId: String, fromIndex: Int, toIndex: Int): Boolean {
        val all = list().toMutableList()
        val idx = all.indexOfFirst { it.id == playlistId }
        if (idx < 0) return false
        val tracks = all[idx].tracks.toMutableList()
        if (fromIndex !in tracks.indices || toIndex !in tracks.indices) return false
        if (fromIndex == toIndex) return true
        val item = tracks.removeAt(fromIndex)
        tracks.add(toIndex, item)
        all[idx] = all[idx].copy(tracks = tracks)
        save(all)
        return true
    }

    suspend fun replaceTracks(playlistId: String, tracks: List<Track>): Boolean {
        val all = list().toMutableList()
        val idx = all.indexOfFirst { it.id == playlistId }
        if (idx < 0) return false
        all[idx] = all[idx].copy(tracks = tracks)
        save(all)
        return true
    }

    private suspend fun save(list: List<LocalPlaylist>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        context.localPlStore.edit { it[keyJson] = arr.toString() }
    }

    private fun LocalPlaylist.toJson(): JSONObject {
        val tracksArr = JSONArray()
        tracks.forEach { t ->
            tracksArr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("title", t.title)
                    .put("artist", t.artist)
                    .put("album", t.album)
                    .put("coverUrl", t.coverUrl ?: "")
                    .put("durationMs", t.durationMs)
                    .put("source", t.source.id)
                    // 不持久化 http CDN（易过期导致本地歌单播失败）；仅保留 file 本地缓存路径
                    .put(
                        "streamUrl",
                        t.streamUrl?.takeIf {
                            it.startsWith("file:") || (it.startsWith("/") && !it.startsWith("http"))
                        }.orEmpty(),
                    )
                    .put("bvid", t.bvid)
                    .put("aid", t.aid)
                    .put("cid", t.cid)
                    .put("ownerMid", t.ownerMid)
                    .put("ownerFace", t.ownerFace),
            )
        }
        return JSONObject()
            .put("id", id)
            .put("title", title)
            .put("tracks", tracksArr)
            .put("customCoverPath", customCoverPath ?: "")
    }

    private fun JSONObject.toLocalPlaylist(): LocalPlaylist {
        val arr = optJSONArray("tracks") ?: JSONArray()
        val tracks = buildList {
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                val sourceId = t.optString("source", "bilibili")
                val source = MusicSourceType.entries.find { it.id == sourceId }
                    ?: MusicSourceType.BILIBILI
                add(
                    Track(
                        id = t.optString("id"),
                        title = t.optString("title"),
                        artist = t.optString("artist"),
                        album = t.optString("album"),
                        coverUrl = t.optString("coverUrl").ifBlank { null },
                        durationMs = t.optLong("durationMs"),
                        source = source,
                        // 丢弃历史持久化的 http 过期链
                        streamUrl = t.optString("streamUrl").ifBlank { null }
                            ?.takeIf {
                                it.startsWith("file:") ||
                                    (it.startsWith("/") && !it.startsWith("http"))
                            },
                        bvid = t.optString("bvid"),
                        aid = t.optString("aid"),
                        cid = t.optString("cid"),
                        ownerMid = t.optString("ownerMid"),
                        ownerFace = t.optString("ownerFace"),
                    ),
                )
            }
        }
        return LocalPlaylist(
            id = optString("id"),
            title = optString("title", "歌单"),
            tracks = tracks,
            customCoverPath = optString("customCoverPath").ifBlank { null },
        )
    }
}
