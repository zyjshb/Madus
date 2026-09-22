package com.madus.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.madus.mobile.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val Context.musicInterestPool by preferencesDataStore(name = "madus_music_interest_pool")

class MusicInterestPoolStore(private val context: Context) {
    private val key = stringPreferencesKey("snapshot_v1")
    private val saveMutex = Mutex()

    suspend fun load(): MusicPoolSnapshot = withContext(Dispatchers.IO) {
        try {
            val obj = JSONObject(context.musicInterestPool.data.first()[key] ?: "{}")
            val candidates = obj.optJSONArray("candidates") ?: JSONArray()
            val seeds = obj.optJSONArray("seeds") ?: JSONArray()
            MusicPoolSnapshot(
                (0 until candidates.length()).mapNotNull { i -> candidates.optJSONObject(i)?.let { o -> runCatching {
                    MusicPoolCandidate(o.getJSONObject("track").toTrack(), o.optString("source", "search"),
                        o.optJSONArray("seedTopics").strings().toSet(), o.optLong("at"),
                        o.optJSONArray("seedSongs").strings().toSet())
                }.getOrNull() } },
                (0 until seeds.length()).mapNotNull { i -> seeds.optJSONObject(i)?.let { o -> runCatching {
                    MusicPoolSeed(o.getJSONObject("track").toTrack(), o.optLong("at"))
                }.getOrNull() } },
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            MusicPoolSnapshot()
        }
    }

    suspend fun save(snapshot: MusicPoolSnapshot) = saveMutex.withLock { withContext(Dispatchers.IO) {
        val obj = JSONObject()
            .put("candidates", JSONArray().apply { snapshot.candidates.forEach { c ->
                put(JSONObject().put("track", c.track.toJson()).put("source", c.source)
                    .put("seedTopics", JSONArray(c.seedTopicKeys.toList()))
                    .put("seedSongs", JSONArray(c.seedSongKeys.toList())).put("at", c.addedAtMs))
            } })
            .put("seeds", JSONArray().apply { snapshot.seeds.forEach { s ->
                put(JSONObject().put("track", s.track.toJson()).put("at", s.supportedAtMs))
            } })
        context.musicInterestPool.edit { it[key] = obj.toString() }
    } }

    private fun Track.toJson() = JSONObject().put("id", id).put("title", title).put("artist", artist)
        .put("album", album).put("cover", coverUrl.orEmpty()).put("duration", durationMs)
        .put("bvid", bvid).put("aid", aid).put("cid", cid).put("ownerMid", ownerMid)
        .put("source", source.name)
        .put("categoryId", categoryId).put("categoryName", categoryName).put("tags", JSONArray(tags))

    private fun JSONObject.toTrack() = Track(
        id = getString("id"), title = getString("title"), artist = optString("artist"),
        album = optString("album"), coverUrl = optString("cover").ifBlank { null },
        durationMs = optLong("duration"), source = MusicSourceType.entries
            .firstOrNull { it.name == optString("source") } ?: MusicSourceType.BILIBILI,
        bvid = optString("bvid"), aid = optString("aid"), cid = optString("cid"),
        ownerMid = optString("ownerMid"), categoryId = optInt("categoryId"),
        categoryName = optString("categoryName"), tags = optJSONArray("tags").strings(),
    )

    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else
        (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
}
