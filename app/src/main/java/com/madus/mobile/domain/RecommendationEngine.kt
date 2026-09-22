package com.madus.mobile.domain

import kotlin.math.exp
import kotlin.math.ln

/** Local, explainable musical taste learning; repeated feedback is bounded per song. */
class RecommendationEngine {
    fun buildInterestState(
        events: List<RecommendationEvent>, nowMs: Long, preferredTopics: Set<String> = emptySet(),
        styleFeedback: List<MusicStyleFeedback> = emptyList(),
    ): InterestState {
        val songs = events.filter { nowMs - it.occurredAtMs in 0..RecommendationTuning.LONG_TERM_TTL_MS }
            .groupBy { it.songKey.ifBlank { it.bvid.ifBlank { it.trackId } } }
        val realtime = linkedMapOf<String, Double>()
        val hourly = linkedMapOf<String, Double>()
        val longTerm = linkedMapOf<String, Double>()
        val realtimeAuthors = linkedMapOf<String, Double>()
        val hourlyAuthors = linkedMapOf<String, Double>()
        val negative = linkedMapOf<String, Double>()
        val cooledIds = linkedSetOf<String>()
        val cooledSongs = linkedSetOf<String>()
        var evidenceCount = 0
        for ((_, songEvents) in songs) {
            val rejection = songEvents.filter { it.type == RecommendationEventType.NOT_INTERESTED }
                .maxOfOrNull { it.occurredAtMs } ?: Long.MIN_VALUE
            val positive = songEvents.filter { it.type.weight > 0.0 && it.occurredAtMs > rejection &&
                (it.sourceId != "library-seed" || rejection == Long.MIN_VALUE) }
            if (positive.isNotEmpty()) evidenceCount++
            // One song contributes its strongest evidence once per horizon. Listening milestones,
            // repeated likes/replays and copies in collections cannot multiply a song's weight.
            val currentFeedback = positive.filter { it.sourceId != "library-seed" }
            addSongEvidence(currentFeedback, nowMs, RecommendationTuning.REALTIME_TTL_MS,
                RecommendationTuning.REALTIME_HALF_LIFE_MS, realtime, realtimeAuthors)
            addSongEvidence(currentFeedback, nowMs, RecommendationTuning.HOURLY_TTL_MS,
                RecommendationTuning.HOURLY_HALF_LIFE_MS, hourly, hourlyAuthors)
            addSongEvidence(positive.filter { it.type in LONG_TERM_EVENTS }, nowMs,
                RecommendationTuning.LONG_TERM_TTL_MS, RecommendationTuning.LONG_TERM_HALF_LIFE_MS, longTerm)
            val latestPositive = currentFeedback.filter { it.type in LONG_TERM_EVENTS }
                .maxOfOrNull { it.occurredAtMs } ?: Long.MIN_VALUE
            val dislikes = songEvents.filter { it.type.weight < 0.0 && it.occurredAtMs >= latestPositive }
            if (dislikes.any { nowMs - it.occurredAtMs <= cooldownFor(it.type) }) songEvents.forEach { event ->
                if (event.trackId.isNotBlank()) cooledIds += event.trackId
                if (event.bvid.isNotBlank()) cooledIds += event.bvid
                if (event.songKey.isNotBlank()) cooledSongs += event.songKey
            }
            // A skip means "not now". Rejections last longer, but neither bans a genre or uploader.
            // Several distinct rejected songs reduce a topic's score gradually.
            val dislike = dislikes.maxByOrNull { negativeStrength(it, nowMs) }
            if (dislike != null) {
                val strength = negativeStrength(dislike, nowMs)
                if (strength <= 0.0) continue
                musicTopics(dislike.topicKeys).forEach { topic ->
                    negative[topic] = ((negative[topic] ?: 0.0) + strength).coerceAtMost(0.8)
                }
            }
        }
        val rt = boundedDistribution(realtime)
        val hour = boundedDistribution(hourly)
        val long = boundedDistribution(longTerm)
        val selected = preferredTopics.intersect(MusicDiscovery.availableTopics.keys)
        val blended = linkedMapOf<String, Double>()
        fun mix(values: Map<String, Double>, weight: Double) = values.forEach { (topic, value) ->
            blended[topic] = (blended[topic] ?: 0.0) + value * weight
        }
        mix(long, 0.50)
        mix(hour, 0.15)
        mix(rt, 0.35)
        // Explicit choices anchor the pool across sessions and survive occasional skips.
        mix(selected.associateWith { 1.0 / selected.size.coerceAtLeast(1) }, 1.5)
        return InterestState(
            realtimeTopics = rt, hourlyTopics = hour, longTermTopics = long,
            realtimeAuthors = boundedDistribution(realtimeAuthors), hourlyAuthors = boundedDistribution(hourlyAuthors),
            interestTopics = normalized(blended), negativeTopics = negative,
            preferredTopics = selected, cooledTrackIds = cooledIds, cooledSongKeys = cooledSongs,
            evidenceSongCount = evidenceCount,
            styleAdjustments = MusicStyleFeedback.adjustments(styleFeedback, nowMs),
        )
    }

    fun scoreCandidate(
        track: Track, profile: ContentProfile?, state: InterestState, source: String,
        context: FeedContext, seedTopicKeys: Set<String> = emptySet(),
    ): ScoredTrack {
        val p = profile ?: ContentProfileParser.profileFromTrack(track, context.nowMs)
        val topics = p.topicKeys.filter { it != "unknown" &&
            (!context.musicOnly || it !in RecommendationTuning.BROAD_TOPICS) }.toSet()
        val musicalTopics = musicTopics(topics)
        val weights = state.interestTopics.ifEmpty {
            normalized((state.realtimeTopics.keys + state.hourlyTopics.keys + state.longTermTopics.keys)
                .associateWith { (state.realtimeTopics[it] ?: 0.0) * 0.35 +
                    (state.hourlyTopics[it] ?: 0.0) * 0.15 + (state.longTermTopics[it] ?: 0.0) * 0.50 })
        }
        val interest = weights.filterValues { it >= 0.04 }.keys + state.preferredTopics
        val knownTaste = musicTopics(interest).isNotEmpty()
        val genreConflict = conflicts(musicalTopics, interest, MusicDiscovery.genreTopics)
        val languageConflict = conflicts(musicalTopics, interest, MusicDiscovery.languageTopics)
        val directMatch = musicalTopics.any { it in interest } && !genreConflict && !languageConflict
        val seedMatch = !directMatch && !genreConflict && !languageConflict &&
            musicalTopics.intersect(MusicDiscovery.genreTopics).isEmpty() &&
            musicTopics(seedTopicKeys).any { it in interest }
        val inPool = !knownTaste || directMatch || seedMatch
        val adjacent = knownTaste && !inPool && !languageConflict &&
            musicalTopics.any { it in MusicDiscovery.adjacentTopics(interest) } &&
            musicalTopics.none { (state.negativeTopics[it] ?: 0.0) >= 0.3 }
        val evidenceTopics = if (seedMatch) musicalTopics + musicTopics(seedTopicKeys) else topics
        val confidence = if (seedMatch) 0.45 else 1.0
        val author = p.authorKey
        val realtimeAffinity = evidenceTopics.sumOf { state.realtimeTopics[it] ?: 0.0 } * confidence +
            (author?.let { (state.realtimeAuthors[it] ?: 0.0) * 0.10 } ?: 0.0)
        val hourlyAffinity = evidenceTopics.sumOf { state.hourlyTopics[it] ?: 0.0 } * confidence +
            (author?.let { (state.hourlyAuthors[it] ?: 0.0) * 0.08 } ?: 0.0)
        val longTermAffinity = evidenceTopics.sumOf { state.longTermTopics[it] ?: 0.0 } * confidence
        val poolAffinity = evidenceTopics.sumOf { weights[it] ?: 0.0 } * confidence
        val sourceQuality = when (source) {
            "realtime-related", "related-like" -> 1.0
            "search", "interest-search" -> 0.85
            "liked", "local", "history" -> 0.8
            "daily", "homepage" -> 0.6
            else -> 0.5
        }
        val songKey = MusicDiscovery.songKey(track)
        val cooled = track.id in state.cooledTrackIds ||
            (track.bvid.isNotBlank() && track.bvid in state.cooledTrackIds) ||
            (songKey.isNotBlank() && songKey in state.cooledSongKeys)
        val freshness = if (track.id !in context.sessionSeenIds && track.id !in context.queueIds) 1.0 else 0.0
        val negativePenalty = topics.sumOf { state.negativeTopics[it] ?: 0.0 } +
            topics.count { it in context.mutedTopics || (state.mutedTopics[it] ?: 0L) > context.nowMs } +
            if (author != null && (author in context.mutedAuthors ||
                    (state.mutedTopics["author:$author"] ?: 0L) > context.nowMs)) 1.5 else 0.0
        // Hearing several folk songs is not evidence of being tired of folk. Genre continuity is
        // the point of an interest pool; fatigue applies to recordings and repeated uploaders.
        val fatiguePenalty = context.recentQueue.takeLast(5).count {
            author != null && it.artist.trim().lowercase() == author
        } * 0.15
        val repeatPenalty = when {
            track.id in context.queueIds || track.id in context.sessionSeenIds -> 2.0
            songKey.isNotBlank() && songKey in context.recentSongKeys -> 2.0
            context.recentQueue.any { songKey.isNotBlank() && MusicDiscovery.songKey(it) == songKey } -> 1.5
            else -> 0.0
        }
        val mismatch = if (knownTaste && !inPool) if (adjacent) 0.4 else 1.0 else 0.0
        val styleAdjustment = musicalTopics.sumOf { state.styleAdjustments[it] ?: 0.0 }
            .coerceIn(-MusicStyleFeedback.MAX_SCORE_ADJUSTMENT, MusicStyleFeedback.MAX_SCORE_ADJUSTMENT)
        val score = 4.0 * poolAffinity + RecommendationTuning.W_REALTIME * realtimeAffinity +
            RecommendationTuning.W_HOURLY * hourlyAffinity + RecommendationTuning.W_LONG_TERM * longTermAffinity +
            sourceQuality + RecommendationTuning.W_FRESHNESS * freshness +
            RecommendationTuning.W_NEGATIVE * negativePenalty + RecommendationTuning.W_FATIGUE * fatiguePenalty +
            RecommendationTuning.W_REPEAT * repeatPenalty + RecommendationTuning.W_MISMATCH * mismatch + styleAdjustment
        val labels = evidenceTopics.filter { it in interest }.mapNotNull { MusicDiscovery.availableTopics[it] }.take(2)
        val reason = when {
            cooled -> "最近已跳过这首歌"
            seedMatch -> "从你喜欢的歌曲继续发现"
            directMatch -> "符合你的${labels.joinToString("、")}口味"
            adjacent -> "试一点相近曲风"
            !knownTaste -> "听一听，找到你的口味"
            else -> "与你的口味关联较弱"
        }
        return ScoredTrack(
            track = track, score = score, source = source, reason = reason,
            explore = adjacent, realtime = source == "realtime-related", dailyBaseline = source == "daily",
            topicKeys = evidenceTopics, authorKey = author, inInterestPool = inPool,
            adjacentInterest = adjacent, cooledDown = cooled,
        )
    }

    private fun addSongEvidence(
        events: List<RecommendationEvent>, nowMs: Long, ttl: Long, halfLife: Long,
        topics: MutableMap<String, Double>, authors: MutableMap<String, Double>? = null,
    ) {
        val within = events.filter { nowMs - it.occurredAtMs <= ttl }
        val strongest = within.maxByOrNull { decayed(it.type.weight, nowMs - it.occurredAtMs, halfLife) } ?: return
        val weight = decayed(strongest.type.weight, nowMs - strongest.occurredAtMs, halfLife)
        val keys = musicTopics(within.flatMap { it.topicKeys }.toSet())
        keys.forEach { topic -> topics[topic] = (topics[topic] ?: 0.0) + weight / keys.size.coerceAtLeast(1) }
        strongest.authorKey?.takeIf { it.isNotBlank() }?.let { author ->
            if (authors != null) authors[author] = (authors[author] ?: 0.0) + weight
        }
    }

    private fun conflicts(candidate: Set<String>, interest: Set<String>, dimension: Set<String>): Boolean {
        val candidateValues = candidate.intersect(dimension)
        val preferredValues = interest.intersect(dimension)
        return candidateValues.isNotEmpty() && preferredValues.isNotEmpty() && candidateValues.intersect(preferredValues).isEmpty()
    }
    private fun musicTopics(topics: Set<String>): Set<String> = topics.intersect(MusicDiscovery.availableTopics.keys)
    private fun cooldownFor(type: RecommendationEventType): Long = when (type) {
        RecommendationEventType.NOT_INTERESTED -> RecommendationTuning.NOT_INTERESTED_COOLDOWN_MS
        RecommendationEventType.SKIP_FAST -> RecommendationTuning.FAST_SKIP_COOLDOWN_MS
        else -> RecommendationTuning.SKIP_COOLDOWN_MS
    }
    private fun negativeStrength(event: RecommendationEvent, nowMs: Long): Double {
        val explicit = event.type == RecommendationEventType.NOT_INTERESTED
        val ttl = if (explicit) RecommendationTuning.NOT_INTERESTED_COOLDOWN_MS else RecommendationTuning.HOURLY_TTL_MS
        val age = nowMs - event.occurredAtMs
        if (age !in 0..ttl) return 0.0
        val halfLife = if (explicit) 3 * RecommendationTuning.HOURLY_TTL_MS else RecommendationTuning.NEGATIVE_HALF_LIFE_MS
        return decayed(-event.type.weight * 0.16, age, halfLife)
    }
    private fun boundedDistribution(values: Map<String, Double>): Map<String, Double> {
        val positive = values.filterValues { it.isFinite() && it > 0.0 }
        val total = positive.values.sum().coerceAtLeast(1.0)
        return positive.mapValues { it.value / total }
    }
    private fun normalized(values: Map<String, Double>): Map<String, Double> {
        val positive = values.filterValues { it.isFinite() && it > 0.0 }
        val sum = positive.values.sum()
        return if (sum > 0.0) positive.mapValues { it.value / sum } else emptyMap()
    }
    private fun decayed(base: Double, ageMs: Long, halfLifeMs: Long): Double =
        if (halfLifeMs <= 0L) 0.0 else base * exp(-ln(2.0) * ageMs.toDouble() / halfLifeMs)
    companion object {
        private val LONG_TERM_EVENTS = setOf(
            RecommendationEventType.LIKE, RecommendationEventType.COLLECT_LOCAL,
            RecommendationEventType.COLLECT_BILIBILI, RecommendationEventType.WATCH_90, RecommendationEventType.REPLAY,
        )
    }
}
