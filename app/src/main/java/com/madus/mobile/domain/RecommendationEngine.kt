package com.madus.mobile.domain

import kotlin.math.exp

class RecommendationEngine {

    fun buildInterestState(events: List<RecommendationEvent>, nowMs: Long): InterestState {
        val realtimeTopics = linkedMapOf<String, Double>()
        val hourlyTopics = linkedMapOf<String, Double>()
        val longTermTopics = linkedMapOf<String, Double>()
        val realtimeAuthors = linkedMapOf<String, Double>()
        val hourlyAuthors = linkedMapOf<String, Double>()
        val strongPositive = linkedMapOf<String, MutableList<Long>>()
        val skipCounts = linkedMapOf<String, MutableList<Long>>()

        for (event in events) {
            val age = nowMs - event.occurredAtMs
            if (age < 0L) continue
            val positive = event.type.weight > 0.0
            val strong = event.type == RecommendationEventType.LIKE ||
                event.type == RecommendationEventType.COLLECT_LOCAL ||
                event.type == RecommendationEventType.COLLECT_BILIBILI ||
                event.type == RecommendationEventType.REPLAY

            for (topic in event.topicKeys) {
                if (topic.isBlank() || topic == "unknown") continue
                if (positive) {
                    if (strong && age <= RecommendationTuning.REALTIME_TTL_MS) {
                        realtimeTopics[topic] = (realtimeTopics[topic] ?: 0.0) +
                            decayed(event.type.weight, age, RecommendationTuning.REALTIME_HALF_LIFE_MS)
                        strongPositive.getOrPut(topic) { mutableListOf() }.add(event.occurredAtMs)
                    }
                    if (age <= RecommendationTuning.HOURLY_TTL_MS) {
                        hourlyTopics[topic] = (hourlyTopics[topic] ?: 0.0) +
                            decayed(event.type.weight, age, RecommendationTuning.HOURLY_HALF_LIFE_MS)
                    }
                    if (age <= RecommendationTuning.LONG_TERM_TTL_MS &&
                        event.type in LONG_TERM_EVENTS
                    ) {
                        longTermTopics[topic] = (longTermTopics[topic] ?: 0.0) +
                            decayed(event.type.weight, age, RecommendationTuning.LONG_TERM_HALF_LIFE_MS)
                    }
                } else if (event.type == RecommendationEventType.SKIP_FAST &&
                    age <= RecommendationTuning.REALTIME_TTL_MS
                ) {
                    skipCounts.getOrPut(topic) { mutableListOf() }.add(event.occurredAtMs)
                    hourlyTopics[topic] = (hourlyTopics[topic] ?: 0.0) +
                        decayed(event.type.weight, age, RecommendationTuning.NEGATIVE_HALF_LIFE_MS)
                }
            }

            val author = event.authorKey
            if (author != null && positive) {
                if (strong && age <= RecommendationTuning.REALTIME_TTL_MS) {
                    realtimeAuthors[author] = (realtimeAuthors[author] ?: 0.0) +
                        decayed(event.type.weight, age, RecommendationTuning.REALTIME_HALF_LIFE_MS)
                }
                if (age <= RecommendationTuning.HOURLY_TTL_MS) {
                    hourlyAuthors[author] = (hourlyAuthors[author] ?: 0.0) +
                        decayed(event.type.weight, age, RecommendationTuning.HOURLY_HALF_LIFE_MS)
                }
            }
        }

        // 升级规则：10 分钟内 2 次同主题强正反馈提高实时分，30 分钟内 3 次提高小时分。
        for ((topic, times) in strongPositive) {
            val in10 = times.count { nowMs - it <= RecommendationTuning.REALTIME_STRONG_TTL_MS }
            val in30 = times.count { nowMs - it <= RecommendationTuning.REALTIME_TTL_MS }
            if (in10 >= 2) {
                realtimeTopics[topic] = (realtimeTopics[topic] ?: 0.0) * 1.5
            }
            if (in30 >= 3) {
                hourlyTopics[topic] = (hourlyTopics[topic] ?: 0.0) * 1.5
            }
        }

        val muted = linkedMapOf<String, Long>()
        for ((topic, times) in skipCounts) {
            val recent = times.count { nowMs - it <= RecommendationTuning.TOPIC_COOLDOWN_MS }
            if (recent >= 2) {
                muted[topic] = nowMs + RecommendationTuning.TOPIC_COOLDOWN_MS
            }
        }
        for (event in events) {
            if (event.type != RecommendationEventType.NOT_INTERESTED) continue
            val age = nowMs - event.occurredAtMs
            if (age < 0L || age > RecommendationTuning.NOT_INTERESTED_COOLDOWN_MS) continue
            val until = event.occurredAtMs + RecommendationTuning.NOT_INTERESTED_COOLDOWN_MS
            event.topicKeys.forEach { topic ->
                if (topic.isBlank() || topic == "unknown") return@forEach
                if (topic.startsWith("upid:")) {
                    muted[topic] = maxOf(muted[topic] ?: 0L, until)
                    return@forEach
                }
                if (topic in RecommendationTuning.BROAD_TOPICS) return@forEach
                val topicUntil = event.occurredAtMs + RecommendationTuning.TOPIC_MUTE_AFTER_DISLIKE_MS
                muted[topic] = maxOf(muted[topic] ?: 0L, topicUntil)
            }
            event.authorKey?.let {
                muted["author:$it"] = maxOf(muted["author:$it"] ?: 0L, until)
            }
        }

        return InterestState(
            realtimeTopics = realtimeTopics,
            hourlyTopics = hourlyTopics,
            longTermTopics = longTermTopics,
            mutedTopics = muted,
            realtimeAuthors = realtimeAuthors,
            hourlyAuthors = hourlyAuthors,
        )
    }

    fun scoreCandidate(
        track: Track,
        profile: ContentProfile?,
        state: InterestState,
        source: String,
        context: FeedContext,
    ): ScoredTrack {
        val p = profile ?: ContentProfileParser.profileFromTrack(track, context.nowMs)
        val topics = p.topicKeys.filter { it != "unknown" }
        val author = p.authorKey

        val realtimeAffinity = topics.sumOf { state.realtimeTopics[it] ?: 0.0 } +
            (author?.let { (state.realtimeAuthors[it] ?: 0.0) * 0.7 } ?: 0.0)
        val hourlyAffinity = topics.sumOf { state.hourlyTopics[it] ?: 0.0 } +
            (author?.let { (state.hourlyAuthors[it] ?: 0.0) * 0.6 } ?: 0.0)
        val longTermAffinity = topics.sumOf { state.longTermTopics[it] ?: 0.0 }

        val sourceQuality = when (source) {
            "realtime-related", "related-like" -> 1.0
            "homepage" -> 0.75
            "daily" -> 0.7
            "history" -> 0.7
            "search" -> 0.65
            "related" -> 0.5
            "liked", "local" -> 0.6
            "popular", "explore" -> 0.55
            else -> 0.4
        }

        val fresh = track.id !in context.sessionSeenIds && track.id !in context.queueIds
        val freshness = if (fresh) 1.0 else 0.0
        val interestTopics = state.realtimeTopics.keys +
            state.hourlyTopics.keys +
            state.longTermTopics.keys
        val matchesInterest = interestTopics.isEmpty() || topics.any { it in interestTopics }
        val novelty = when {
            !matchesInterest && interestTopics.isNotEmpty() -> 0.0
            state.longTermTopics.isNotEmpty() &&
                topics.none { state.longTermTopics.containsKey(it) } -> 0.8
            state.longTermTopics.isEmpty() && (source == "popular" || source == "explore") -> 0.5
            else -> 0.0
        }
        val mismatch = when {
            interestTopics.isEmpty() || matchesInterest -> 0.0
            source == "popular" || source == "explore" -> 0.0
            source == "related-like" || source == "realtime-related" || source == "related" -> 0.0
            source == "liked" || source == "local" || source == "history" -> 0.0
            else -> 1.0
        }

        val negativePenalty = topics.count { topic ->
            topic in context.mutedTopics || isMutedTopic(state, topic, context.nowMs)
        }.toDouble() +
            if (author != null && (author in context.mutedAuthors ||
                    isMutedAuthor(state, author, context.nowMs))
            ) {
                1.5
            } else {
                0.0
            }

        val fatiguePenalty = context.recentQueue.count { q ->
            val qt = topicsOf(q)
            topics.any { it in qt }
        } * 0.5 +
            context.recentQueue.count { q -> q.artist.trim().lowercase() == author } * 0.8

        val repeatPenalty = when {
            track.id in context.queueIds || track.id in context.sessionSeenIds -> 2.0
            context.recentQueue.any { titleSimilar(it.title, track.title) } -> 1.0
            context.recentQueue.any { it.artist.trim().lowercase() == author } -> 0.4
            else -> 0.0
        }

        val score = RecommendationTuning.W_REALTIME * realtimeAffinity +
            RecommendationTuning.W_HOURLY * hourlyAffinity +
            RecommendationTuning.W_LONG_TERM * longTermAffinity +
            RecommendationTuning.W_SOURCE_QUALITY * sourceQuality +
            RecommendationTuning.W_FRESHNESS * freshness +
            RecommendationTuning.W_NOVELTY * novelty +
            RecommendationTuning.W_NEGATIVE * negativePenalty +
            RecommendationTuning.W_FATIGUE * fatiguePenalty +
            RecommendationTuning.W_REPEAT * repeatPenalty +
            RecommendationTuning.W_MISMATCH * mismatch

        val reason = buildString {
            append("src=").append(source)
            append(",rt=").append(format(realtimeAffinity))
            append(",h=").append(format(hourlyAffinity))
            append(",lt=").append(format(longTermAffinity))
            if (negativePenalty > 0.0) append(",neg=").append(format(negativePenalty))
            if (fatiguePenalty > 0.0) append(",fatigue=").append(format(fatiguePenalty))
            if (repeatPenalty > 0.0) append(",repeat=").append(format(repeatPenalty))
            if (mismatch > 0.0) append(",mis=").append(format(mismatch))
        }

        return ScoredTrack(
            track = track,
            score = score,
            source = source,
            reason = reason,
            explore = source == "popular" || source == "explore",
            realtime = source == "realtime-related",
            dailyBaseline = source == "daily",
            topicKeys = topics.toSet(),
            authorKey = author,
        )
    }

    private fun isMutedTopic(state: InterestState, topic: String, nowMs: Long): Boolean =
        (state.mutedTopics[topic] ?: 0L) > nowMs

    private fun isMutedAuthor(state: InterestState, author: String, nowMs: Long): Boolean =
        (state.mutedTopics["author:$author"] ?: 0L) > nowMs

    private fun decayed(base: Double, ageMs: Long, halfLifeMs: Long): Double {
        if (halfLifeMs <= 0L) return 0.0
        return base * exp(-ageMs.toDouble() / halfLifeMs)
    }

    private fun topicsOf(track: Track): Set<String> =
        ContentProfileParser.profileFromTrack(track).topicKeys.filter { it != "unknown" }.toSet()

    private fun titleSimilar(a: String, b: String): Boolean {
        val na = normalizeTitle(a)
        val nb = normalizeTitle(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        if (na.length >= 4 && nb.contains(na)) return true
        if (nb.length >= 4 && na.contains(nb)) return true
        return false
    }

    private fun normalizeTitle(title: String): String =
        title.lowercase()
            .replace(Regex("【.*?】|\\[.*?]|\\(.*?\\)|（.*?）"), " ")
            .replace(Regex("[\\s|｜/\\\\·•~～!！?？.。,，、:：;；\"'“”‘’]+"), " ")
            .trim()

    private fun format(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else String.format("%.2f", value)

    companion object {
        private val LONG_TERM_EVENTS = setOf(
            RecommendationEventType.LIKE,
            RecommendationEventType.COLLECT_LOCAL,
            RecommendationEventType.COLLECT_BILIBILI,
            RecommendationEventType.WATCH_90,
            RecommendationEventType.REPLAY,
        )
    }
}
