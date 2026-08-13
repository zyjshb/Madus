package com.madus.mobile.domain

enum class RecommendationEventType(val weight: Double) {
    LIKE(1.00),
    COLLECT_LOCAL(1.15),
    COLLECT_BILIBILI(1.25),
    PLAY_START(0.05),
    WATCH_50(0.30),
    WATCH_90(0.60),
    REPLAY(0.80),
    SKIP_FAST(-0.70),
    NOT_INTERESTED(-1.50),
}

data class RecommendationEvent(
    val trackId: String,
    val bvid: String,
    val type: RecommendationEventType,
    val occurredAtMs: Long,
    val sourceId: String,
    val topicKeys: Set<String>,
    val authorKey: String?,
)

data class ContentProfile(
    val trackId: String,
    val bvid: String,
    val authorId: String?,
    val authorName: String?,
    val categoryId: Int?,
    val categoryName: String?,
    val tags: Set<String>,
    val topicKeys: Set<String>,
    val fetchedAtMs: Long,
) {
    val key: String
        get() = bvid.ifBlank { trackId }

    val authorKey: String?
        get() = authorName
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("Bilibili", ignoreCase = true) }
            ?.lowercase()
}

data class InterestState(
    val realtimeTopics: Map<String, Double> = emptyMap(),
    val hourlyTopics: Map<String, Double> = emptyMap(),
    val longTermTopics: Map<String, Double> = emptyMap(),
    val mutedTopics: Map<String, Long> = emptyMap(),
    val realtimeAuthors: Map<String, Double> = emptyMap(),
    val hourlyAuthors: Map<String, Double> = emptyMap(),
)

data class ScoredTrack(
    val track: Track,
    val score: Double,
    val source: String,
    val reason: String = "",
    val explore: Boolean = false,
    val realtime: Boolean = false,
    val dailyBaseline: Boolean = false,
    val topicKeys: Set<String> = emptySet(),
    val authorKey: String? = null,
)

data class FeedContext(
    val nowMs: Long = System.currentTimeMillis(),
    val limit: Int = 30,
    val sessionSeenIds: Set<String> = emptySet(),
    val queueIds: Set<String> = emptySet(),
    val recentQueue: List<Track> = emptyList(),
    val mutedTopics: Set<String> = emptySet(),
    val mutedAuthors: Set<String> = emptySet(),
    val sourceId: String = "recommend",
    val realtimeTopicQuota: Map<String, Int> = emptyMap(),
)

object RecommendationTuning {
    const val REALTIME_TTL_MS = 30 * 60 * 1000L
    const val REALTIME_STRONG_TTL_MS = 10 * 60 * 1000L
    const val HOURLY_TTL_MS = 24 * 60 * 60 * 1000L
    const val LONG_TERM_TTL_MS = 30 * 24 * 60 * 60 * 1000L
    const val TOPIC_COOLDOWN_MS = 30 * 60 * 1000L
    const val NOT_INTERESTED_COOLDOWN_MS = 7 * 24 * 60 * 60 * 1000L
    const val EVENT_LIMIT = 1000
    const val PROFILE_LIMIT = 400
    const val PROFILE_TTL_MS = 7 * 24 * 60 * 60 * 1000L
    const val MAX_REALTIME_IN_FIRST_20 = 3
    const val MAX_SAME_TOPIC_IN_WINDOW_4 = 2
    const val MAX_SAME_AUTHOR_IN_WINDOW_4 = 1
    const val MIN_EXPLORE_RATIO = 0.15
    const val MIN_DAILY_BASELINE_RATIO = 0.20
    const val REALTIME_HALF_LIFE_MS = 10 * 60 * 1000L
    const val HOURLY_HALF_LIFE_MS = 6 * 60 * 60 * 1000L
    const val LONG_TERM_HALF_LIFE_MS = 7 * 24 * 60 * 60 * 1000L
    const val NEGATIVE_HALF_LIFE_MS = 30 * 60 * 1000L
    const val WATCH_50_MIN_MS = 30_000L
    const val SKIP_FAST_MIN_MS = 15_000L
    const val W_REALTIME = 2.8
    const val W_HOURLY = 2.0
    const val W_LONG_TERM = 1.2
    const val W_SOURCE_QUALITY = 1.0
    const val W_FRESHNESS = 0.8
    const val W_NOVELTY = 0.6
    const val W_NEGATIVE = -2.5
    const val W_FATIGUE = -1.5
    const val W_REPEAT = -1.2
}
