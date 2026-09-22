package com.madus.mobile.domain

/** 音乐发现策略：曲风召回、跨会话避重，不依赖 UP 主等同于歌手。 */
object MusicDiscovery {
    private val queries = linkedMapOf(
        "rock" to "摇滚 单曲", "folk" to "民谣 单曲", "rnb" to "R&B 歌曲",
        "jazz" to "爵士 音乐", "pop" to "流行 单曲", "rap" to "说唱 单曲",
        "gufeng" to "国风 歌曲", "dj" to "电子音乐 单曲", "instrumental" to "纯音乐 演奏",
        "jp-song" to "日语 歌曲", "en-song" to "欧美 歌曲", "kpop" to "韩语 歌曲",
        "cantonese" to "粤语 歌曲", "mandarin" to "华语 单曲", "healing" to "治愈 音乐",
        "anime-song" to "动漫 主题曲", "vocaloid" to "VOCALOID 原创曲",
        "cover" to "翻唱 歌曲", "live" to "音乐 现场",
    )

    fun searchQueries(state: InterestState, seeds: List<Track>, round: Int, nowMs: Long): List<String> {
        val weights = linkedMapOf<String, Double>()
        queries.keys.forEach { topic ->
            weights[topic] = (state.realtimeTopics[topic] ?: 0.0) * 2.8 +
                (state.hourlyTopics[topic] ?: 0.0) * 2.0 + (state.longTermTopics[topic] ?: 0.0)
        }
        seeds.distinctBy { it.id }.filter { TrackFilters.isLikelyMusic(it) }.forEach { track ->
            ContentProfileParser.profileFromTrack(track).topicKeys.forEach { topic ->
                if (topic in queries) weights[topic] = (weights[topic] ?: 0.0) + 0.35
            }
        }
        val available = queries.keys.filter { (state.mutedTopics[it] ?: 0L) <= nowMs }
        val preferred = available.filter { (weights[it] ?: 0.0) > 0.0 }
            .sortedByDescending { weights[it] }.take(8)
        val rotated = rotate(preferred, round).take(3)
        val explore = rotate(available.filterNot { it in preferred }, round).take(if (preferred.isEmpty()) 4 else 1)
        return (rotated + explore).mapNotNull { queries[it] }
    }

    private fun <T> rotate(items: List<T>, round: Int): List<T> {
        if (items.isEmpty()) return items
        val offset = Math.floorMod(round, items.size)
        return items.drop(offset) + items.take(offset)
    }

    fun recentlyHeard(events: List<RecommendationEvent>, nowMs: Long): List<RecommendationEvent> =
        events.filter {
            nowMs - it.occurredAtMs in 0..RecommendationTuning.HEARD_COOLDOWN_MS &&
                it.type in setOf(RecommendationEventType.PLAY_START, RecommendationEventType.WATCH_50,
                    RecommendationEventType.WATCH_90, RecommendationEventType.REPLAY, RecommendationEventType.SKIP_FAST)
        }

    /** 有书名号时按歌名避重；否则只去掉明确的版本包装，保留歌名中的括号。 */
    fun songKey(track: Track): String {
        val title = track.title.lowercase()
        val named = Regex("《([^》]+)》").findAll(title).map { it.groupValues[1] }.toList()
        val core = if (named.size == 1) named.single() else title
            .replace(Regex("【[^】]*】|\\[[^]]*]"), " ")
            .replace(Regex("(?i)\\b(official|audio|video|lyrics?|mv|hd|4k|cover|live)\\b"), " ")
            .replace(Regex("完整版|高音质|无损|动态歌词|歌词版|官方音频"), " ")
        return core.replace(Regex("[^\\p{L}\\p{N}]"), "").takeIf { it.length >= 2 }.orEmpty()
    }
}
