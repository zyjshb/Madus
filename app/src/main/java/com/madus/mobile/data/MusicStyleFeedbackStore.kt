package com.madus.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.madus.mobile.domain.MusicDiscovery
import com.madus.mobile.domain.MusicStyleFeedback
import com.madus.mobile.domain.Track
import com.madus.mobile.domain.MusicSourceType
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.musicStyleFeedbackStore by preferencesDataStore(name = "madus_music_style_feedback")

class MusicStyleFeedbackStore(private val context: Context) {
    private val key = stringPreferencesKey("votes_v1")

    suspend fun load(): List<MusicStyleFeedback> = decode(context.musicStyleFeedbackStore.data.first()[key])

    suspend fun set(vote: MusicStyleFeedback): List<MusicStyleFeedback> {
        var saved = emptyList<MusicStyleFeedback>()
        context.musicStyleFeedbackStore.edit { prefs ->
            saved = (decode(prefs[key]).filterNot { it.songKey == vote.songKey ||
                (it.referenceTrack == null && vote.referenceTrack?.let { t -> MusicDiscovery.songKey(t) } == it.songKey) } +
                listOfNotNull(vote.takeIf { it.direction != 0 }))
                .filter { System.currentTimeMillis() - it.occurredAtMs in 0..MusicStyleFeedback.TTL_MS }
                .sortedByDescending { it.occurredAtMs }.take(200)
            val array = JSONArray()
            saved.forEach { value -> array.put(JSONObject().put("song", value.songKey)
                .put("topics", JSONArray(value.topics.toList())).put("direction", value.direction)
                .put("at", value.occurredAtMs).put("track", value.referenceTrack?.let { t ->
                    JSONObject().put("id", t.id).put("title", t.title).put("artist", t.artist)
                        .put("album", t.album).put("duration", t.durationMs).put("source", t.source.name)
                        .put("bvid", t.bvid).put("aid", t.aid).put("cid", t.cid).put("cover", t.coverUrl)
                        .put("category", t.categoryId).put("categoryName", t.categoryName)
                        .put("tags", JSONArray(t.tags)).put("ownerMid", t.ownerMid)
                })) }
            prefs[key] = array.toString()
        }
        return saved
    }

    private fun decode(raw: String?): List<MusicStyleFeedback> = runCatching {
        val array = JSONArray(raw ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val tags = item.optJSONArray("topics") ?: JSONArray()
            val vote = MusicStyleFeedback(item.optString("song"),
                (0 until tags.length()).map { tags.optString(it) }.toSet(),
                item.optInt("direction"), item.optLong("at"), item.optJSONObject("track")?.let { t ->
                    Track(id = t.optString("id"), title = t.optString("title"), artist = t.optString("artist"),
                        album = t.optString("album"), durationMs = t.optLong("duration"),
                        source = MusicSourceType.entries.firstOrNull { it.name == t.optString("source") } ?: MusicSourceType.BILIBILI,
                        bvid = t.optString("bvid"), aid = t.optString("aid"), cid = t.optString("cid"),
                        coverUrl = t.optString("cover").takeIf { it.isNotBlank() && it != "null" },
                        categoryId = t.optInt("category"), categoryName = t.optString("categoryName"),
                        tags = t.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(),
                        ownerMid = t.optString("ownerMid"))
                })
            vote.takeIf { it.songKey.isNotBlank() && it.direction in setOf(-1, 1) &&
                System.currentTimeMillis() - it.occurredAtMs in 0..MusicStyleFeedback.TTL_MS }
        }
    }.getOrDefault(emptyList())
}
