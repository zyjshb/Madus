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
        val rtCounts = linkedMapOf<String, Double>()
        val hourCounts = linkedMapOf<String, Double>()
        val longCounts = linkedMapOf<String, Double>()
        val realtimeAuthors = linkedMapOf<String, Double>()
        val hourlyAuthors = linkedMapOf<String, Double>()
        val negative = linkedMapOf<String, Double>()
        val cooledIds = linkedSetOf<String>()
        val cooledSongs = linkedSetOf<String>()
        val songAffinities = linkedMapOf<String, Double>()
        var evidenceCount = 0
        for ((songKey, songEvents) in songs) {
            val rejection = songEvents.filter { it.type == RecommendationEventType.NOT_INTERESTED }
                .maxOfOrNull { it.occurredAtMs } ?: Long.MIN_VALUE
            val positive = songEvents.filter { (it.type.weight > 0.0 || it.type == RecommendationEventType.LISTEN_SAMPLE) && it.occurredAtMs > rejection &&
                (it.sourceId != "library-seed" || rejection == Long.MIN_VALUE) }
            if (songQuality(positive) >= 0.15) {
                evidenceCount++
                positive.forEach { event ->
                    songAffinities["track:${event.bvid.ifBlank { event.trackId }}"] = songQuality(positive)
                }
            }
            // 同曲合并实际平均收听、完成率与主动操作；曝光次数不能冒充偏好强度。
            val currentFeedback = positive.filter { it.sourceId != "library-seed" }
            addSongEvidence(currentFeedback, nowMs, RecommendationTuning.REALTIME_TTL_MS,
                RecommendationTuning.REALTIME_HALF_LIFE_MS, realtime, rtCounts, realtimeAuthors)
            addSongEvidence(currentFeedback, nowMs, RecommendationTuning.HOURLY_TTL_MS,
                RecommendationTuning.HOURLY_HALF_LIFE_MS, hourly, hourCounts, hourlyAuthors)
            addSongEvidence(positive.filter { it.type in LONG_TERM_EVENTS || it.type == RecommendationEventType.LISTEN_SAMPLE }, nowMs,
                RecommendationTuning.LONG_TERM_TTL_MS, RecommendationTuning.LONG_TERM_HALF_LIFE_MS, longTerm, longCounts)
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
        fun average(values: Map<String, Double>, counts: Map<String, Double>) =
            values.mapValues { (topic, value) -> value / (3.0 + (counts[topic] ?: 0.0)) }
        val rt = average(realtime, rtCounts)
        val hour = average(hourly, hourCounts)
        val long = average(longTerm, longCounts)
        val searches = linkedMapOf<String, Double>()
        events.filter { it.type == RecommendationEventType.SEARCH_INTENT &&
            nowMs - it.occurredAtMs in 0..RecommendationTuning.HOURLY_TTL_MS }
            .distinctBy { it.trackId }.forEach { event ->
                musicTopics(event.topicKeys).forEach { topic ->
                    searches[topic] = maxOf(searches[topic] ?: 0.0,
                        decayed(0.15, nowMs - event.occurredAtMs, RecommendationTuning.HOURLY_HALF_LIFE_MS))
                }
            }
        val selected = preferredTopics.intersect(MusicDiscovery.availableTopics.keys)
        val blended = linkedMapOf<String, Double>()
        fun mix(values: Map<String, Double>, weight: Double) = values.forEach { (topic, value) ->
            blended[topic] = (blended[topic] ?: 0.0) + value * weight
        }
        mix(long, 0.65)
        mix(hour, 0.20)
        mix(rt, 0.15)
        // Explicit choices anchor the pool across sessions and survive occasional skips.
        mix(selected.associateWith { 1.0 / selected.size.coerceAtLeast(1) }, 1.5)
        val references = styleFeedback.filter { it.referenceTrack != null &&
            nowMs - it.occurredAtMs in 0..MusicStyleFeedback.TTL_MS && it.direction != 0 }
            .groupBy { it.songKey }.values.map { it.maxBy { vote -> vote.occurredAtMs } }
        return InterestState(
            songAffinities = songAffinities.filterKeys { it.removePrefix("track:") !in cooledIds },
            referencePreferences = references.associate { it.songKey to it.direction },
            referenceTracks = references.filter { it.direction > 0 }.mapNotNull { it.referenceTrack },
            negativeReferenceTracks = references.filter { it.direction < 0 }.mapNotNull { it.referenceTrack },
            realtimeTopics = rt, hourlyTopics = hour, longTermTopics = long,
            realtimeAuthors = boundedDistribution(realtimeAuthors), hourlyAuthors = boundedDistribution(hourlyAuthors),
            interestTopics = normalized(blended), negativeTopics = negative,
            preferredTopics = selected, cooledTrackIds = cooledIds, cooledSongKeys = cooledSongs,
            evidenceSongCount = evidenceCount,
            styleAdjustments = MusicStyleFeedback.adjustments(styleFeedback, nowMs),
            searchTopics = searches,
            confidence = if (selected.isNotEmpty()) 1.0 else evidenceCount.toDouble() / (evidenceCount + 6.0),
        )
    }

    fun scoreCandidate(
        track: Track, profile: ContentProfile?, state: InterestState, source: String,
        context: FeedContext, seedTopicKeys: Set<String> = emptySet(), seedSongKeys: Set<String> = emptySet(),
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
        val songKey = MusicDiscovery.songKey(track)
        val referenceKey = MusicStyleFeedback.key(track)
        val familiar = (state.songAffinities[referenceKey] ?: 0.0) >= 0.15 ||
            (state.referencePreferences[referenceKey] ?: 0) > 0
        val supportedSeeds = seedSongKeys.filterTo(linkedSetOf()) {
            ((state.songAffinities[it] ?: 0.0) >= 0.15 || (state.referencePreferences[it] ?: 0) > 0) &&
                (state.referencePreferences[it] ?: 0) >= 0 && it.removePrefix("track:") !in state.cooledTrackIds
        }
        val referenceSimilarity = state.referenceTracks.maxOfOrNull { MusicReferencePolicy.similarity(track, it) } ?: 0.0
        val referencePenalty = maxOf(
            state.negativeReferenceTracks.maxOfOrNull { MusicReferencePolicy.similarity(track, it) } ?: 0.0,
            if (seedSongKeys.any { (state.referencePreferences[it] ?: 0) < 0 }) 0.75 else 0.0)
        val knownTaste = (musicTopics(interest).isNotEmpty() && state.confidence >= 0.1) ||
            state.songAffinities.isNotEmpty() || state.referenceTracks.isNotEmpty()
        val genreConflict = conflicts(musicalTopics, interest, MusicDiscovery.genreTopics)
        val languageConflict = conflicts(musicalTopics, interest, MusicDiscovery.languageTopics)
        // A learned language is a boundary, not proof that every song in that language fits.
        val matchingInterests = (interest - MusicDiscovery.languageTopics) + state.preferredTopics
        val directMatch = musicalTopics.any { it in matchingInterests } && !genreConflict && !languageConflict
        val seedMatch = !directMatch && !genreConflict && !languageConflict &&
            musicalTopics.intersect(MusicDiscovery.genreTopics).isEmpty() &&
            musicTopics(seedTopicKeys).any { it in matchingInterests }
        val detailedMatch = referenceSimilarity >= 0.5 && !genreConflict && !languageConflict
        val songMatch = familiar || detailedMatch || supportedSeeds.any {
            // An exact manual reference may add a small discovery branch outside an older broad taste.
            (state.referencePreferences[it] ?: 0) > 0 || (!genreConflict && !languageConflict)
        }
        val inPool = !knownTaste || directMatch || seedMatch || songMatch
        val discoveryEvidence = musicalTopics + if (musicalTopics.intersect(MusicDiscovery.genreTopics).isEmpty())
            musicTopics(seedTopicKeys) else emptySet()
        val adjacent = knownTaste && !inPool && !languageConflict &&
            discoveryEvidence.any { it in MusicDiscovery.adjacentTopics(interest) || it in state.searchTopics } &&
            musicalTopics.none { (state.negativeTopics[it] ?: 0.0) >= 0.3 }
        val evidenceTopics = if (seedMatch || adjacent) discoveryEvidence else topics
        val confidence = if (seedMatch) 0.45 else 1.0
        val author = p.authorKey
        val realtimeAffinity = evidenceTopics.sumOf { state.realtimeTopics[it] ?: 0.0 } * confidence +
            (author?.let { (state.realtimeAuthors[it] ?: 0.0) * 0.10 } ?: 0.0)
        val hourlyAffinity = evidenceTopics.sumOf { state.hourlyTopics[it] ?: 0.0 } * confidence +
            (author?.let { (state.hourlyAuthors[it] ?: 0.0) * 0.08 } ?: 0.0)
        val longTermAffinity = evidenceTopics.sumOf { state.longTermTopics[it] ?: 0.0 } * confidence
        val poolAffinity = evidenceTopics.sumOf { weights[it] ?: 0.0 } * confidence * state.confidence
        val sourceQuality = when (source) {
            "realtime-related", "related-like" -> 1.0
            "search", "interest-search" -> 0.85
            "liked", "local", "history" -> 0.8
            "daily", "homepage" -> 0.6
            else -> 0.5
        }
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
            RecommendationTuning.W_REPEAT * repeatPenalty + RecommendationTuning.W_MISMATCH * mismatch + styleAdjustment +
            evidenceTopics.sumOf { state.searchTopics[it] ?: 0.0 }.coerceAtMost(0.3) +
            (if (familiar) 2.0 else if (supportedSeeds.isNotEmpty()) 0.35 else 0.0) + referenceSimilarity - referencePenalty -
            MusicReferencePolicy.versionPenalty(track, state.preferredTopics +
                weights.filterValues { it >= 0.15 }.keys) +
            (state.referencePreferences[referenceKey] ?: 0) * 0.45
        val labels = evidenceTopics.filter { it in interest }.mapNotNull { MusicDiscovery.availableTopics[it] }.take(2)
        val reason = when {
            cooled -> "最近已跳过这首歌"
            familiar -> "你明确喜欢或认真听过的歌曲"
            songMatch -> "从你喜欢的具体歌曲继续发现"
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
            seedSongKeys = supportedSeeds, familiar = familiar,
        )
    }

    private fun addSongEvidence(
        events: List<RecommendationEvent>, nowMs: Long, ttl: Long, halfLife: Long,
        topics: MutableMap<String, Double>, counts: MutableMap<String, Double>, authors: MutableMap<String, Double>? = null,
    ) {
        val within = events.filter { nowMs - it.occurredAtMs <= ttl }
        val newest = within.maxByOrNull { it.occurredAtMs } ?: return
        val weight = decayed(songQuality(within), nowMs - newest.occurredAtMs, halfLife)
        val keys = musicTopics(within.flatMap { it.topicKeys }.toSet())
        keys.forEach { topic ->
            topics[topic] = (topics[topic] ?: 0.0) + weight / keys.size.coerceAtLeast(1)
            counts[topic] = (counts[topic] ?: 0.0) + 1.0 / keys.size.coerceAtLeast(1)
        }
        newest.authorKey?.takeIf { it.isNotBlank() }?.let { author ->
            if (authors != null) authors[author] = (authors[author] ?: 0.0) + weight
        }
    }

    internal fun songQuality(events: List<RecommendationEvent>): Double {
        val samples = events.filter { it.type == RecommendationEventType.LISTEN_SAMPLE && it.listenedMs >= 500 }
            .groupBy { it.listeningSessionId.ifBlank { "${it.trackId}:${it.occurredAtMs}" } }
            .values.map { versions -> versions.maxBy { it.listenedMs } }
        val listening = if (samples.isNotEmpty()) {
            // 每次播放先算比例再求平均，避免长歌天然占优；同曲重复听不增加样本权重。
            val completion = samples.map { if (it.durationMs > 0) (it.listenedMs.toDouble() / it.durationMs).coerceIn(0.0, 1.0) else 0.0 }.average()
            val seconds = samples.map { (it.listenedMs / 90_000.0).coerceIn(0.0, 1.0) }.average()
            val dwell = samples.map { (minOf(it.foregroundMs, it.listenedMs) / 60_000.0).coerceIn(0.0, 1.0) }.average()
            val search = samples.map { if (it.fromSearch && it.listenedMs >= 15_000) 0.1 else 0.0 }.average()
            completion * 0.6 + seconds * 0.25 + dwell * 0.05 + search
        } else events.maxOfOrNull {
            when (it.type) {
                RecommendationEventType.WATCH_30 -> 0.08
                RecommendationEventType.WATCH_50 -> 0.25
                RecommendationEventType.WATCH_90 -> 0.60
                RecommendationEventType.REPLAY -> 0.60
                else -> 0.0
            }
        } ?: 0.0
        val like = if (events.any { it.type == RecommendationEventType.LIKE }) {
            if (events.filter { it.type == RecommendationEventType.LIKE }.all { it.sourceId == "library-seed" }) 0.18 else 0.25
        } else 0.0
        val collection = if (events.any { it.type == RecommendationEventType.COLLECT_LOCAL || it.type == RecommendationEventType.COLLECT_BILIBILI }) 0.35 else 0.0
        return (listening + (like + collection).coerceAtMost(0.45)).coerceAtMost(1.25)
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
