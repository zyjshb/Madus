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

    /**
     * 用户按「上一首」离开。汽水的离场原因里有 previous / hotkey_previous 两档，
     * 语义比「下一首」更接近否定：主动回到上一首说明这首不该被放到这里。
     */
    USER_BACK(-0.95),
    LISTEN_SAMPLE(0.0),
    SEARCH_INTENT(0.0),
}

/**
 * 听歌模式。汽水把「探索多少」做成用户可见的两档（熟悉模式 / 新鲜模式），
 * 而不是内部调 ε-greedy —— 可解释、可 A/B、也不会让算法偷偷把老歌全换掉。
 * 这里补一个中间档作为默认，保证不选模式的用户行为和改造前一致。
 */
enum class TasteMode(
    val label: String,
    val hint: String,
    /** 探索位占比上限，重排器据此算间隔。 */
    val exploreRatio: Double,
    /** 与画像不匹配时的扣分倍率。 */
    val mismatchScale: Double,
    /** 已喜欢/听过的曲目在熟悉模式下的加成。 */
    val sourceQualityBias: Double,
    /** 熟悉模式：明确不匹配的曲目额外降权，只在没有更合适候选时才用。 */
    val strictAnchor: Boolean,
) {
    FAMILIAR("熟悉", "以你验证过的口味为主，只留少量相邻曲风", 0.08, 1.35, 0.15, true),
    BALANCED("均衡", "既有熟悉感，也留出发现位", 0.25, 1.00, 0.0, false),
    FRESH("新鲜", "更多相邻曲风与陌生歌手", 0.40, 0.50, -0.12, false),
    ;

    companion object {
        fun of(name: String?): TasteMode = entries.firstOrNull { it.name == name } ?: BALANCED
    }
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
    /** 最近一天的重复收听（10 分钟 / 1 小时 / 1 天三档），区分单曲循环式真爱与当天疲劳。 */
    val playCounts: Map<String, RepeatProfile> = emptyMap(),
    /** 歌手级负反馈强度（0~0.5），作用域是作者而不是曲风。 */
    val artistNegatives: Map<String, Double> = emptyMap(),
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
    /** 听歌模式：熟悉 / 均衡 / 新鲜。默认均衡 = 改造前的行为。 */
    val tasteMode: TasteMode = TasteMode.BALANCED,
    /**
     * 已经排好、尚未播放的队列头部（顺序：最近将要播的在最后）。
     * 汽水把「待播」用 played_mills = -1 塞进同一个 30 条上下文窗口，
     * 让服务端在补队列时也能看到「接下来要放什么」，避免接缝处连着两首同曲风/同作者。
     */
    val upcomingQueue: List<Track> = emptyList(),
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
    /** 十分钟内反复播放（单曲循环式）加分，来自汽水三档重复窗口 */
    const val W_LOVE = 1.0
    /** 歌手级负反馈权重；强度已被 RecommendationFusion 封顶 0.5 */
    const val W_ARTIST_NEGATIVE = -1.8
    /** 熟悉模式下，明确不符合画像的曲目额外降权（不是硬封，避免队列枯竭） */
    const val W_ANCHOR_MISMATCH = -1.1
}
