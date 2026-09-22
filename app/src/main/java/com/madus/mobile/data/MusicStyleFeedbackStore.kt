package com.madus.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.madus.mobile.domain.MusicStyleFeedback
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
            saved = (decode(prefs[key]).filterNot { it.songKey == vote.songKey } +
                listOfNotNull(vote.takeIf { it.direction != 0 }))
                .filter { System.currentTimeMillis() - it.occurredAtMs in 0..MusicStyleFeedback.TTL_MS }
                .sortedByDescending { it.occurredAtMs }.take(200)
            val array = JSONArray()
            saved.forEach { value -> array.put(JSONObject().put("song", value.songKey)
                .put("topics", JSONArray(value.topics.toList())).put("direction", value.direction)
                .put("at", value.occurredAtMs)) }
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
                item.optInt("direction"), item.optLong("at"))
            vote.takeIf { it.songKey.isNotBlank() && it.direction in setOf(-1, 1) &&
                System.currentTimeMillis() - it.occurredAtMs in 0..MusicStyleFeedback.TTL_MS }
        }
    }.getOrDefault(emptyList())
}
