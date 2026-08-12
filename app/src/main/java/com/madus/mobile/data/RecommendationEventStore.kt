package com.madus.mobile.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.madus.mobile.domain.RecommendationEvent
import com.madus.mobile.domain.RecommendationEventType
import com.madus.mobile.domain.RecommendationTuning
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.recommendationEventStore by preferencesDataStore(name = "madus_recommend_events")

/**
 * 本地统一行为事件表。只用于本机推荐，最多保留最近 [RecommendationTuning.EVENT_LIMIT] 条。
 */
class RecommendationEventStore(private val context: Context) {
    private val keyEvents = stringPreferencesKey("events_v1")

    suspend fun record(event: RecommendationEvent) {
        val all = readInternal().toMutableList()
        all.add(event)
        while (all.size > RecommendationTuning.EVENT_LIMIT) all.removeAt(0)
        save(all)
        Log.d(
            "Recommendation",
            "event=${event.type} bvid=${event.bvid} topics=${event.topicKeys} author=${event.authorKey}",
        )
    }

    /** 最新在前。 */
    suspend fun events(): List<RecommendationEvent> = readInternal().asReversed()

    suspend fun realtimeEvents(nowMs: Long = System.currentTimeMillis()): List<RecommendationEvent> =
        readInternal().filter { nowMs - it.occurredAtMs <= RecommendationTuning.REALTIME_TTL_MS }

    suspend fun hourlyEvents(nowMs: Long = System.currentTimeMillis()): List<RecommendationEvent> =
        readInternal().filter { nowMs - it.occurredAtMs <= RecommendationTuning.HOURLY_TTL_MS }

    suspend fun longTermEvents(nowMs: Long = System.currentTimeMillis()): List<RecommendationEvent> =
        readInternal().filter { nowMs - it.occurredAtMs <= RecommendationTuning.LONG_TERM_TTL_MS }

    suspend fun clearExpired(nowMs: Long = System.currentTimeMillis()) {
        val keep = readInternal().filter {
            nowMs - it.occurredAtMs <= RecommendationTuning.LONG_TERM_TTL_MS
        }
        if (keep.size != readInternal().size) save(keep)
    }

    private suspend fun readInternal(): List<RecommendationEvent> {
        val raw = context.recommendationEventStore.data.first()[keyEvents].orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(o.toEvent())
                }
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun save(list: List<RecommendationEvent>) {
        val arr = JSONArray()
        list.forEach { event ->
            val topics = JSONArray()
            event.topicKeys.forEach { topics.put(it) }
            arr.put(
                JSONObject()
                    .put("trackId", event.trackId)
                    .put("bvid", event.bvid)
                    .put("type", event.type.name)
                    .put("occurredAtMs", event.occurredAtMs)
                    .put("sourceId", event.sourceId)
                    .put("topicKeys", topics)
                    .put("authorKey", event.authorKey ?: ""),
            )
        }
        context.recommendationEventStore.edit { it[keyEvents] = arr.toString() }
    }

    private fun JSONObject.toEvent(): RecommendationEvent {
        val type = runCatching {
            RecommendationEventType.valueOf(optString("type", RecommendationEventType.LIKE.name))
        }.getOrDefault(RecommendationEventType.LIKE)
        val topics = linkedSetOf<String>()
        optJSONArray("topicKeys")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optString(i).takeIf { it.isNotBlank() }?.let { topics.add(it) }
            }
        }
        return RecommendationEvent(
            trackId = optString("trackId"),
            bvid = optString("bvid"),
            type = type,
            occurredAtMs = optLong("occurredAtMs", System.currentTimeMillis()),
            sourceId = optString("sourceId", "recommend"),
            topicKeys = topics,
            authorKey = optString("authorKey").ifBlank { null },
        )
    }
}
