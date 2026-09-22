package com.madus.mobile.domain

import kotlin.math.exp
import kotlin.math.ln

/** 独立的辅助信号；保留歌曲参照，即使未知曲风也不丢弃反馈。 */
data class MusicStyleFeedback(
    val songKey: String,
    val topics: Set<String>,
    val direction: Int,
    val occurredAtMs: Long,
    val referenceTrack: Track? = null,
) {
    companion object {
        const val TTL_MS = 180 * 24 * 60 * 60 * 1000L
        const val MAX_SCORE_ADJUSTMENT = 0.75
        fun key(track: Track): String = "track:${track.bvid.ifBlank { track.id }}"

        // “这类”不扩散为整个语种、上传者或音乐大区；优先采用明确曲风。
        fun topics(keys: Set<String>): Set<String> = keys.intersect(MusicDiscovery.genreTopics).ifEmpty {
            keys.intersect(setOf("healing", "sleep", "anime-song", "vocaloid", "cover", "live"))
        }

        fun adjustments(feedback: List<MusicStyleFeedback>, nowMs: Long): Map<String, Double> {
            val result = mutableMapOf<String, Double>()
            feedback.filter { nowMs - it.occurredAtMs in 0..TTL_MS }
                .groupBy { it.songKey }.values.forEach { votes ->
                    val vote = votes.maxBy { it.occurredAtMs }
                    val keys = topics(vote.topics)
                    val value = vote.direction.coerceIn(-1, 1) * 0.45 *
                        exp(-ln(2.0) * (nowMs - vote.occurredAtMs) / (30 * 24 * 60 * 60 * 1000.0))
                    keys.forEach { topic -> result[topic] = (result[topic] ?: 0.0) + value / keys.size }
                }
            return result.mapValues { it.value.coerceIn(-MAX_SCORE_ADJUSTMENT, MAX_SCORE_ADJUSTMENT) }
        }
    }
}
