package com.madus.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.madus.mobile.domain.ContentProfile
import com.madus.mobile.domain.RecommendationTuning
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.contentProfileStore by preferencesDataStore(name = "madus_content_profiles")

/**
 * BVID -> 分区/标签/主题缓存。同一 BVID 本会话只补一次详情，缓存 7 天。
 */
class ContentProfileStore(private val context: Context) {
    private val keyProfiles = stringPreferencesKey("profiles_v1")

    suspend fun get(key: String): ContentProfile? = all().firstOrNull { it.key == key }

    suspend fun all(): List<ContentProfile> {
        val raw = context.contentProfileStore.data.first()[keyProfiles].orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(o.toProfile())
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun put(profile: ContentProfile) {
        val all = all().associateBy { it.key }.toMutableMap()
        all[profile.key] = profile
        val list = all.values.toList().sortedByDescending { it.fetchedAtMs }
        save(list.take(RecommendationTuning.PROFILE_LIMIT))
    }

    suspend fun removeExpired(nowMs: Long = System.currentTimeMillis()) {
        val keep = all().filter {
            nowMs - it.fetchedAtMs <= RecommendationTuning.PROFILE_TTL_MS
        }
        if (keep.size != all().size) save(keep)
    }

    private suspend fun save(list: List<ContentProfile>) {
        val arr = JSONArray()
        list.forEach { p ->
            val tags = JSONArray()
            p.tags.forEach { tags.put(it) }
            val topics = JSONArray()
            p.topicKeys.forEach { topics.put(it) }
            arr.put(
                JSONObject()
                    .put("trackId", p.trackId)
                    .put("bvid", p.bvid)
                    .put("authorId", p.authorId ?: "")
                    .put("authorName", p.authorName ?: "")
                    .put("categoryId", p.categoryId ?: 0)
                    .put("categoryName", p.categoryName ?: "")
                    .put("tags", tags)
                    .put("topicKeys", topics)
                    .put("fetchedAtMs", p.fetchedAtMs),
            )
        }
        context.contentProfileStore.edit { it[keyProfiles] = arr.toString() }
    }

    private fun JSONObject.toProfile(): ContentProfile = ContentProfile(
        trackId = optString("trackId"),
        bvid = optString("bvid"),
        authorId = optString("authorId").ifBlank { null },
        authorName = optString("authorName").ifBlank { null },
        categoryId = optInt("categoryId", 0).takeIf { it > 0 },
        categoryName = optString("categoryName").ifBlank { null },
        tags = run {
            val tags = linkedSetOf<String>()
            optJSONArray("tags")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() }?.let { tags.add(it) }
                }
            }
            tags
        },
        topicKeys = run {
            val topics = linkedSetOf<String>()
            optJSONArray("topicKeys")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() }?.let { topics.add(it) }
                }
            }
            topics
        },
        fetchedAtMs = optLong("fetchedAtMs", System.currentTimeMillis()),
    )
}
