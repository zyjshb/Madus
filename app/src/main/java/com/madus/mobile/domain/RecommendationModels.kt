package com.madus.mobile.domain

enum class RecommendationEventType(val weight: Double) {
    LIKE(1.00),
    COLLECT_LOCAL(1.15),
    COLLECT_BILIBILI(1.25),
    PLAY_START(0.0),
    WATCH_30(0.15),
    WATCH_50(0.30),
    WATCH_90(0.60),
    REPLAY(0.80),
    SKIP_FAST(-0.70),
    SKIP(-0.35),
    NOT_INTERESTED(-1.50),
    LISTEN_SAMPLE(0.0),
    SEARCH_INTENT(0.0),
}

data class RecommendationEvent(
    val trackId: String,
    val bvid: String,
    val type: RecommendationEventType,
    val occurredAtMs: Long,
    val sourceId: String,
    val topicKeys: Set<String>,
    val authorKey: String?,
    val songKey: String = "",
    val listeningSessionId: String = "",
    val listenedMs: Long = 0,
    val durationMs: Long = 0,
    val foregroundMs: Long = 0,
    val fromSearch: Boolean = false,
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
    /** Normalized, bounded musical interests; repeated feedback on one song is one piece of evidence. */
    val interestTopics: Map<String, Double> = emptyMap(),
    val negativeTopics: Map<String, Double> = emptyMap(),
    val preferredTopics: Set<String> = emptySet(),
    val cooledTrackIds: Set<String> = emptySet(),
    val cooledSongKeys: Set<String> = emptySet(),
    val evidenceSongCount: Int = 0,
    val styleAdjustments: Map<String, Double> = emptyMap(),
    val searchTopics: Map<String, Double> = emptyMap(),
    val confidence: Double = 1.0,
    val songAffinities: Map<String, Double> = emptyMap(),
    val referencePreferences: Map<String, Int> = emptyMap(),
    val referenceTracks: List<Track> = emptyList(),
    val negativeReferenceTracks: List<Track> = emptyList(),
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
    val inInterestPool: Boolean = true,
    val adjacentInterest: Boolean = false,
    val cooledDown: Boolean = false,
    val seedSongKeys: Set<String> = emptySet(),
    val familiar: Boolean = false,
)

data class FeedContext(
    val musicOnly: Boolean = false,
    val recentSongKeys: Set<String> = emptySet(),
    val nowMs: Long = System.currentTimeMillis(),
    val limit: Int = 30,
    val sessionSeenIds: Set<String> = emptySet(),
    val queueIds: Set<String> = emptySet(),
    val recentQueue: List<Track> = emptyList(),
    val mutedTopics: Set<String> = emptySet(),
    val mutedAuthors: Set<String> = emptySet(),
    val blockedIds: Set<String> = emptySet(),
    val blockedBvids: Set<String> = emptySet(),
    val blockedAuthorIds: Set<String> = emptySet(),
    val blockedTitleKeys: Set<String> = emptySet(),
    val sourceId: String = "recommend",
    val realtimeTopicQuota: Map<String, Int> = emptyMap(),
    /** Exploration is an upper bound, never a requirement to inject unrelated music. */
    val maxExploreRatio: Double = 0.25,
    /** 已播+已排队的顺序上下文，保证单首补队列也能轮到探索。 */
    val recentExploration: List<Boolean> = emptyList(),
    val recentTopicKeys: List<Set<String>> = emptyList(),
    val recentSeedSongKeys: List<Set<String>> = emptyList(),
    val isOpening: Boolean = false,
)

object RecommendationTuning {
    const val REALTIME_TTL_MS = 30 * 60 * 1000L
    const val REALTIME_STRONG_TTL_MS = 10 * 60 * 1000L
    const val HOURLY_TTL_MS = 24 * 60 * 60 * 1000L
    const val LONG_TERM_TTL_MS = 180 * 24 * 60 * 60 * 1000L
    const val HEARD_COOLDOWN_MS = 14 * 24 * 60 * 60 * 1000L
    const val TOPIC_COOLDOWN_MS = 30 * 60 * 1000L
    const val NOT_INTERESTED_COOLDOWN_MS = 7 * 24 * 60 * 60 * 1000L
    const val FAST_SKIP_COOLDOWN_MS = 2 * 24 * 60 * 60 * 1000L
    const val SKIP_COOLDOWN_MS = 6 * 60 * 60 * 1000L
    /** snackbar 大约 4 秒，撤销窗口略长一点 */
    const val UNDO_NOT_INTERESTED_MS = 6_000L
    /** 一首不喜欢不该封掉整个音乐/动画区 */
    val BROAD_TOPICS = setOf("music", "anime", "life", "gaming", "unknown")
    const val EVENT_LIMIT = 6000
    const val PROFILE_LIMIT = 400
    const val PROFILE_TTL_MS = 7 * 24 * 60 * 60 * 1000L
    const val MAX_REALTIME_IN_FIRST_20 = 3
    const val MAX_SAME_TOPIC_IN_WINDOW_4 = 2
    const val MAX_SAME_AUTHOR_IN_WINDOW_4 = 1
    const val MIN_EXPLORE_RATIO = 0.0
    const val MIN_DAILY_BASELINE_RATIO = 0.20
    const val REALTIME_HALF_LIFE_MS = 10 * 60 * 1000L
    const val HOURLY_HALF_LIFE_MS = 6 * 60 * 60 * 1000L
    const val LONG_TERM_HALF_LIFE_MS = 45 * 24 * 60 * 60 * 1000L
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
    /** 用户已有兴趣时，和画像完全不沾边的热门/首页要压下去 */
    const val W_MISMATCH = -1.6
}
