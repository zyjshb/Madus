package com.madus.mobile.domain

/** Keep discovery inside the listener's interest pool; variety must not override musical fit. */
class RecommendationReRanker {
    fun rerank(candidates: List<ScoredTrack>, context: FeedContext): List<Track> =
        rerankWithReasons(candidates, context).first

    fun rerankWithReasons(candidates: List<ScoredTrack>, context: FeedContext): Pair<List<Track>, List<ScoredTrack>> {
        val waiting = candidates.filterNot { violatesHard(it, context) }.sortedByDescending { it.score }.toMutableList()
        val picked = mutableListOf<ScoredTrack>()
        while (waiting.isNotEmpty() && picked.size < context.limit) {
            val eligible = waiting.filter { canPick(it, picked, context) }.ifEmpty {
                // A small catalog must not stop playback just to meet an impossible origin quota.
                // Only relax origin spacing; content, rejection, repeat and exploration rules stay intact.
                val fallback = waiting.filter { canPick(it, picked, context, enforceSeedVariety = false) }
                val lastOrigin = (context.recentSeedSongKeys + picked.map { it.seedSongKeys }).lastOrNull().orEmpty()
                fallback.filter { it.seedSongKeys.intersect(lastOrigin).isEmpty() }.ifEmpty { fallback }
            }
            if (eligible.isEmpty()) break
            val pool = eligible.filter { it.inInterestPool && !it.explore }
            val discoveries = eligible.filter { it.explore || !it.inInterestPool }
            // 满足间隔时真正留出发现位，不能被永远存在的同类候选挤掉。
            val relevant = discoveries.ifEmpty { pool.ifEmpty { eligible } }
            val seedHistory = context.recentSeedSongKeys + picked.map { it.seedSongKeys }
            fun seedCount(candidate: ScoredTrack): Int = seedHistory.takeLast(5)
                .count { it.intersect(candidate.seedSongKeys).isNotEmpty() }
            // Balance reference origins before exhausting their quotas (A,B,A,B,C would dead-end).
            val preferred = if (context.musicOnly && relevant.all { it.seedSongKeys.isNotEmpty() }) {
                val least = relevant.minOf(::seedCount)
                relevant.filter { seedCount(it) == least }
            } else relevant
            val history = context.recentTopicKeys.ifEmpty {
                context.recentQueue.map { ContentProfileParser.profileFromTrack(it).topicKeys }
            } + picked.map { topicsOf(it) }
            fun sharesGenre(a: Set<String>, b: Set<String>): Boolean = genres(a).intersect(genres(b)).isNotEmpty()
            val withoutStreak = if (context.musicOnly && history.size >= 2) preferred.filter { candidate ->
                !history.takeLast(2).all { sharesGenre(it, topicsOf(candidate)) }
            }.ifEmpty { preferred } else preferred
            // 六首内尽量不让同一曲风超过四首；没有合格替代时仍允许播放。
            val balanced = if (context.musicOnly) withoutStreak.filter { candidate ->
                history.takeLast(5).count { sharesGenre(it, topicsOf(candidate)) } < 4
            }.ifEmpty { withoutStreak } else withoutStreak
            // Uploader variety breaks near-ties; it cannot promote a much weaker musical match.
            val choiceSet = balanced.filter { it.score >= balanced.first().score - 1.25 }
            val candidate = choiceSet.firstOrNull { variedAuthor(it, picked) &&
                (context.musicOnly || variedTopic(it, picked)) }
                ?: choiceSet.firstOrNull { variedAuthor(it, picked) }
                ?: choiceSet.first()
            waiting.remove(candidate)
            picked += candidate
        }
        return picked.map { it.track } to picked
    }

    private fun violatesHard(candidate: ScoredTrack, context: FeedContext): Boolean {
        val t = candidate.track
        if (!candidate.score.isFinite() || candidate.cooledDown) return true
        if (context.musicOnly && (!TrackFilters.isLikelyMusic(t) ||
                (!candidate.inInterestPool && !candidate.adjacentInterest))) return true
        val songKey = MusicDiscovery.songKey(t)
        if (songKey.isNotBlank() && songKey in context.recentSongKeys) return true
        if (t.id in context.sessionSeenIds || t.id in context.queueIds) return true
        if (t.bvid.isNotBlank() && (t.bvid in context.sessionSeenIds || t.bvid in context.queueIds)) return true
        if (t.id in context.blockedIds) return true
        if (t.bvid.isNotBlank() && t.bvid in context.blockedBvids) return true
        if (t.ownerMid.isNotBlank() && t.ownerMid in context.blockedAuthorIds) return true
        val author = authorOf(candidate)
        if (author != null && author in context.mutedAuthors) return true
        if (topicsOf(candidate).any { it in context.mutedTopics }) return true
        return context.blockedTitleKeys.any { ContentProfileParser.titlesOverlap(it, t.title) }
    }

    private fun canPick(candidate: ScoredTrack, picked: List<ScoredTrack>, context: FeedContext,
        enforceSeedVariety: Boolean = true): Boolean {
        if (picked.any { it.track.id == candidate.track.id ||
                (candidate.track.bvid.isNotBlank() && it.track.bvid == candidate.track.bvid) }) return false
        if (context.musicOnly) {
            val key = MusicDiscovery.songKey(candidate.track)
            if (key.isNotBlank() && picked.any { MusicDiscovery.songKey(it.track) == key }) return false
        }
        if (candidate.explore || !candidate.inInterestPool) {
            if (context.isOpening && picked.isEmpty()) return false
            val ratio = context.maxExploreRatio.coerceIn(0.0, 0.25)
            if (ratio <= 0.0) return false
            val interval = kotlin.math.ceil(1.0 / ratio).toInt()
            val history = context.recentExploration.ifEmpty { context.recentQueue.map { false } } +
                picked.map { it.explore || !it.inInterestPool }
            if (history.size < interval - 1 || history.takeLast(interval - 1).any { it }) return false
        }
        // One liked song must not turn into an uninterrupted chain of its related videos.
        // Apply across refill boundaries, not just inside a single response.
        if (enforceSeedVariety && context.musicOnly && candidate.seedSongKeys.isNotEmpty() && !candidate.familiar) {
            val history = context.recentSeedSongKeys + picked.map { it.seedSongKeys }
            val origins = candidate.seedSongKeys
            if (history.lastOrNull()?.let { it.intersect(origins).isNotEmpty() } == true) return false
            if (history.takeLast(5).count { it.intersect(origins).isNotEmpty() } >= 2) return false
        }
        if (candidate.realtime) for (topic in topicsOf(candidate)) {
            val quota = context.realtimeTopicQuota[topic] ?: continue
            if (picked.count { it.realtime && topic in topicsOf(it) } >= quota) return false
        }
        return true
    }

    private fun variedAuthor(candidate: ScoredTrack, picked: List<ScoredTrack>): Boolean {
        val author = authorOf(candidate) ?: return true
        return picked.takeLast(3).none { authorOf(it) == author }
    }

    private fun variedTopic(candidate: ScoredTrack, picked: List<ScoredTrack>): Boolean {
        val topics = topicsOf(candidate)
        return picked.takeLast(3).count { topicsOf(it).any { topic -> topic in topics } } < 2
    }

    private fun authorOf(scored: ScoredTrack): String? = scored.authorKey ?: scored.track.artist.trim()
        .takeIf { it.isNotBlank() && !it.equals("Bilibili", ignoreCase = true) }?.lowercase()

    private fun genres(topics: Set<String>): Set<String> =
        if ("instrumental" in topics) setOf("instrumental") else topics.intersect(MusicDiscovery.genreTopics)

    private fun topicsOf(scored: ScoredTrack): Set<String> = scored.topicKeys.ifEmpty {
        ContentProfileParser.profileFromTrack(scored.track).topicKeys.filter { it != "unknown" }.toSet()
    }
}
