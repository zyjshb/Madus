package com.madus.mobile.domain

/** 音乐发现策略：曲风召回、跨会话避重，不依赖 UP 主等同于歌手。 */
object MusicDiscovery {
    val availableTopics: Map<String, String> = linkedMapOf(
        "rock" to "摇滚", "folk" to "民谣", "rnb" to "R&B", "jazz" to "爵士",
        "pop" to "流行", "rap" to "说唱", "gufeng" to "国风", "dj" to "电子",
        "instrumental" to "纯音乐", "jp-song" to "日语", "en-song" to "欧美",
        "kpop" to "韩语", "cantonese" to "粤语", "mandarin" to "华语",
        "healing" to "治愈", "sleep" to "助眠", "anime-song" to "动漫歌曲",
        "vocaloid" to "虚拟歌手", "cover" to "翻唱", "live" to "现场",
    )
    val genreTopics: Set<String> = setOf("rock", "folk", "rnb", "jazz", "pop", "rap", "gufeng", "dj", "instrumental")
    val languageTopics: Set<String> = setOf("jp-song", "en-song", "kpop", "cantonese", "mandarin")
    private val neighbors = mapOf(
        "rock" to setOf("pop", "folk"), "folk" to setOf("pop", "healing"),
        "rnb" to setOf("jazz", "pop", "rap"), "jazz" to setOf("rnb", "instrumental"),
        "pop" to setOf("rnb", "folk"), "rap" to setOf("rnb", "dj"),
        "gufeng" to setOf("folk", "instrumental"), "dj" to setOf("pop", "rap"),
        "instrumental" to setOf("folk", "pop", "jazz", "healing"),
        "healing" to setOf("folk", "instrumental", "sleep"), "sleep" to setOf("healing", "instrumental"),
        "anime-song" to setOf("jp-song", "vocaloid"), "vocaloid" to setOf("anime-song", "jp-song"),
    )

    fun adjacentTopics(topics: Set<String>): Set<String> = topics.flatMap { neighbors[it].orEmpty() }.toSet() - topics

    private val queries = linkedMapOf(
        "rock" to "摇滚 单曲", "folk" to "民谣 单曲", "rnb" to "R&B 歌曲",
        "jazz" to "爵士 音乐", "pop" to "流行 单曲", "rap" to "说唱 单曲",
        "gufeng" to "国风 歌曲", "dj" to "电子音乐 单曲", "instrumental" to "纯音乐 演奏",
        "jp-song" to "日语 歌曲", "en-song" to "欧美 歌曲", "kpop" to "韩语 歌曲",
        "cantonese" to "粤语 歌曲", "mandarin" to "华语 单曲", "healing" to "治愈 音乐",
        "anime-song" to "动漫 主题曲", "vocaloid" to "VOCALOID 原创曲",
        "cover" to "翻唱 歌曲", "live" to "音乐 现场", "sleep" to "助眠 纯音乐",
    )

    /** Search intent is weak provenance, just like a positive seed, never a replacement for tags. */
    fun queryTopicKeys(query: String): Set<String> = queries.filterValues { it == query }.keys.ifEmpty {
        availableTopics.filterValues { label -> query.startsWith("$label ") }.keys
    }

    fun searchQueries(state: InterestState, seeds: List<Track>, round: Int, nowMs: Long): List<String> {
        if (state.confidence < 0.1 && state.preferredTopics.isEmpty()) {
            val searched = state.searchTopics.entries.sortedByDescending { it.value }.map { it.key }.take(1)
            return (searched + rotate(listOf("pop", "folk", "rnb", "instrumental", "rock", "gufeng"), round))
                .distinct().take(4).mapNotNull { queries[it] }
        }
        val weights = linkedMapOf<String, Double>()
        queries.keys.forEach { topic ->
            weights[topic] = state.interestTopics[topic] ?: ((state.realtimeTopics[topic] ?: 0.0) * 2.8 +
                (state.hourlyTopics[topic] ?: 0.0) * 2.0 + (state.longTermTopics[topic] ?: 0.0))
        }
        seeds.distinctBy { songKey(it).ifBlank { it.id } }.filter { TrackFilters.isLikelyMusic(it) }.forEach { track ->
            ContentProfileParser.profileFromTrack(track).topicKeys.forEach { topic ->
                // Seeds help bootstrap an empty profile; they must not overwhelm a learned taste.
                if (topic in queries && state.interestTopics.isEmpty()) weights[topic] = (weights[topic] ?: 0.0) + 0.05
            }
        }
        val available = queries.keys.filter { (state.mutedTopics[it] ?: 0L) <= nowMs }
        val preferred = available.filter { (weights[it] ?: 0.0) > 0.0 }
            .sortedByDescending { weights[it] }.take(8)
        if (preferred.isEmpty()) return rotate(listOf("pop", "folk", "rnb", "instrumental", "rock", "gufeng"), round)
            .take(4).mapNotNull { queries[it] }
        // 第二个请求就覆盖不同方向，省流模式只取两个请求也不会全是同一类型。
        val primary = preferred.first()
        val recentSearch = state.searchTopics.keys.filter { it in available && it != primary }
        val neighbors = if (primary == "instrumental") listOf("folk", "pop")
            else adjacentTopics(preferred.toSet()).filter { it in available }
        val discovery = rotate(recentSearch.ifEmpty { neighbors }, round)
            .firstOrNull { (state.negativeTopics[it] ?: 0.0) < 0.3 }
        val secondary = rotate(preferred.drop(1), round).firstOrNull()
        val result = listOfNotNull(primary, discovery, secondary).distinct().mapNotNull { queries[it] }.toMutableList()
        val label = availableTopics.getValue(primary)
        result += "$label ${listOf("原创 歌曲", "歌曲 高音质", "单曲 歌词")[Math.floorMod(round, 3)]}"
        return result.distinct()
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
                    RecommendationEventType.WATCH_90, RecommendationEventType.REPLAY, RecommendationEventType.SKIP_FAST,
                    RecommendationEventType.SKIP, RecommendationEventType.WATCH_30, RecommendationEventType.LISTEN_SAMPLE)
        }

    fun hasVariety(tracks: List<Track>): Boolean = tracks.flatMap {
        val topics = ContentProfileParser.profileFromTrack(it).topicKeys
        if ("instrumental" in topics) setOf("instrumental") else topics.intersect(genreTopics)
    }.toSet().size >= 2

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
