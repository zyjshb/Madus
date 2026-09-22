package com.madus.mobile.domain

/** Keep discovery inside the listener's interest pool; variety must not override musical fit. */
class RecommendationReRanker {
    fun rerank(candidates: List<ScoredTrack>, context: FeedContext): List<Track> =
        rerankWithReasons(candidates, context).first

    fun rerankWithReasons(candidates: List<ScoredTrack>, context: FeedContext): Pair<List<Track>, List<ScoredTrack>> {
        val waiting = candidates.filterNot { violatesHard(it, context) }.sortedByDescending { it.score }.toMutableList()
        val picked = mutableListOf<ScoredTrack>()
        while (waiting.isNotEmpty() && picked.size < context.limit) {
            val eligible = waiting.filter { canPick(it, picked, context) }
            if (eligible.isEmpty()) break
            // Relax uploader repetition inside the pool before ever leaving the user's taste.
            val pool = eligible.filter { it.inInterestPool && !it.explore }
            val preferred = pool.ifEmpty { eligible }
            // Uploader variety breaks near-ties; it cannot promote a much weaker musical match.
            val choiceSet = preferred.filter { it.score >= preferred.first().score - 1.25 }
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

    private fun canPick(candidate: ScoredTrack, picked: List<ScoredTrack>, context: FeedContext): Boolean {
        if (picked.any { it.track.id == candidate.track.id ||
                (candidate.track.bvid.isNotBlank() && it.track.bvid == candidate.track.bvid) }) return false
        if (context.musicOnly) {
            val key = MusicDiscovery.songKey(candidate.track)
            if (key.isNotBlank() && picked.any { MusicDiscovery.songKey(it.track) == key }) return false
        }
        if (candidate.explore || !candidate.inInterestPool) {
            // Enforce a maximum on every prefix, including partially filled queues. A thin pool
            // cannot silently become mostly exploration, and the first few tracks stay familiar.
            val ratio = context.maxExploreRatio.coerceIn(0.0, 0.15)
            val allowed = ((picked.size + 1) * ratio).toInt()
            if (picked.count { it.explore || !it.inInterestPool } >= allowed) return false
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

    private fun topicsOf(scored: ScoredTrack): Set<String> = scored.topicKeys.ifEmpty {
        ContentProfileParser.profileFromTrack(scored.track).topicKeys.filter { it != "unknown" }.toSet()
    }
}
