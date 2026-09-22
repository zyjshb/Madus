package com.madus.mobile.domain

/** Musical evidence beyond a fixed genre menu. Never equate an uploader with a singer. */
object MusicReferencePolicy {
    private val genericTags = setOf("音乐", "歌曲", "单曲", "欧美", "英文", "英语", "日语", "华语",
        "流行", "摇滚", "翻唱", "现场", "高音质", "无损", "推荐", "宝藏歌曲", "热门", "治愈",
        "music", "song", "pop", "rock", "cover", "live", "mv", "audio", "official", "lyrics")
    private fun tags(track: Track): Set<String> = track.tags.map { it.trim().lowercase() }
        .filter { it.length in 2..40 && it !in genericTags }.toSet()

    fun similarity(candidate: Track, reference: Track): Double {
        val shared = tags(candidate).intersect(tags(reference))
        // One arbitrary Bilibili tag is weak evidence; two independent tags are stronger.
        return when { shared.size >= 2 -> 0.75; shared.size == 1 -> 0.25; else -> 0.0 }
    }

    fun versionPenalty(track: Track, preferredTopics: Set<String>): Double {
        val text = (track.title + " " + track.tags.joinToString(" ")).lowercase()
        if (listOf("ai翻唱", "ai 翻唱", "ai cover", "加速版", "降调版", "升调版", "变速版",
                "slowed", "sped up", "speed up", "nightcore").any { it in text }) return 2.0
        val topics = ContentProfileParser.profileFromTrack(track).topicKeys
        return when {
            "cover" in topics && "cover" !in preferredTopics -> 1.1
            "live" in topics && "live" !in preferredTopics -> 0.6
            else -> 0.0
        }
    }

    /** A familiar opener is useful; repeating yesterday's opener every launch is not. */
    fun canOpen(track: Track, events: List<RecommendationEvent>, nowMs: Long): Boolean {
        val key = MusicDiscovery.songKey(track)
        val matching = MusicDiscovery.recentlyHeard(events, nowMs).filter {
            it.trackId == track.id || (key.isNotBlank() && it.songKey == key)
        }
        if (matching.any { nowMs - it.occurredAtMs < 24 * 60 * 60 * 1000L }) return false
        val samples = matching.filter { it.type == RecommendationEventType.LISTEN_SAMPLE }
        fun day(event: RecommendationEvent) = event.occurredAtMs / (24 * 60 * 60 * 1000L)
        val sampledDays = samples.map(::day).toSet()
        val sessions = (samples.map { it.listeningSessionId.ifBlank { "day:${day(it)}" } } +
            matching.filter { it.type == RecommendationEventType.PLAY_START && day(it) !in sampledDays }
                .map { "day:${day(it)}" }).distinct().size
        return sessions < 3
    }
}
