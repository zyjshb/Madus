package com.madus.mobile.domain

class RecommendationReRanker {

    fun rerank(candidates: List<ScoredTrack>, context: FeedContext): List<Track> {
        val waiting = candidates.sortedByDescending { it.score }.toMutableList()
        val picked = mutableListOf<ScoredTrack>()
        var relaxation = 0
        while (waiting.isNotEmpty() && picked.size < context.limit) {
            val candidate = waiting.firstOrNull { canPick(it, picked, context, relaxation) }
                ?: if (relaxation >= 2) {
                    waiting.firstOrNull { canPick(it, picked, context, 1) }
                } else {
                    null
                }
            if (candidate == null) {
                if (relaxation < 2) {
                    relaxation++
                    continue
                }
                break
            }
            waiting.remove(candidate)
            picked.add(candidate)
        }
        return picked.map { it.track }
    }

    fun rerankWithReasons(candidates: List<ScoredTrack>, context: FeedContext): Pair<List<Track>, List<ScoredTrack>> {
        val waiting = candidates.sortedByDescending { it.score }.toMutableList()
        val picked = mutableListOf<ScoredTrack>()
        var relaxation = 0
        while (waiting.isNotEmpty() && picked.size < context.limit) {
            val candidate = waiting.firstOrNull { canPick(it, picked, context, relaxation) }
                ?: if (relaxation >= 2) {
                    waiting.firstOrNull { canPick(it, picked, context, 1) }
                } else {
                    null
                }
            if (candidate == null) {
                if (relaxation < 2) {
                    relaxation++
                    continue
                }
                break
            }
            waiting.remove(candidate)
            picked.add(candidate)
        }
        return picked.map { it.track } to picked
    }

    private fun violatesHard(candidate: ScoredTrack, context: FeedContext): Boolean {
        val t = candidate.track
        if (t.id in context.sessionSeenIds || t.id in context.queueIds) return true
        if (t.id in context.blockedIds) return true
        if (t.bvid.isNotBlank() && t.bvid in context.blockedBvids) return true
        if (t.ownerMid.isNotBlank() && t.ownerMid in context.blockedAuthorIds) return true
        val author = candidate.authorKey ?: authorOf(t)
        if (author != null && author in context.mutedAuthors) return true
        val topics = candidate.topicKeys.ifEmpty { topicsOf(t) }
        if (topics.any { it in context.mutedTopics }) return true
        if (context.blockedTitleKeys.any { ContentProfileParser.titlesOverlap(it, t.title) }) return true
        return false
    }

    private fun canPick(
        candidate: ScoredTrack,
        picked: List<ScoredTrack>,
        context: FeedContext,
        relaxation: Int,
    ): Boolean {
        if (violatesHard(candidate, context)) return false

        val window4 = picked.takeLast(4)
        val window6 = picked.takeLast(6)
        val author = candidate.authorKey ?: authorOf(candidate.track)
        val topics = candidate.topicKeys.ifEmpty { topicsOf(candidate.track) }

        val authorLimit = when {
            relaxation >= 2 -> Int.MAX_VALUE
            relaxation >= 1 -> RecommendationTuning.MAX_SAME_AUTHOR_IN_WINDOW_4 + 1
            else -> RecommendationTuning.MAX_SAME_AUTHOR_IN_WINDOW_4
        }
        val topicLimit = when {
            relaxation >= 2 -> Int.MAX_VALUE
            relaxation >= 1 -> RecommendationTuning.MAX_SAME_TOPIC_IN_WINDOW_4 + 1
            else -> RecommendationTuning.MAX_SAME_TOPIC_IN_WINDOW_4
        }

        if (author != null && window4.count { authorOf(it) == author } >= authorLimit) return false
        if (topics.isNotEmpty() &&
            window4.count { topicsOf(it).any { t -> t in topics } } >= topicLimit
        ) {
            return false
        }

        if (candidate.realtime) {
            if (picked.take(20).count { it.realtime } >= RecommendationTuning.MAX_REALTIME_IN_FIRST_20) {
                return false
            }
            val realtimeTopicIn6 = window6.count {
                it.realtime && topicsOf(it).any { t -> t in topics }
            }
            if (realtimeTopicIn6 >= 2) return false
            for (topic in topics) {
                val quota = context.realtimeTopicQuota[topic] ?: continue
                if (quota <= 0) return false
            }
        }

        if (relaxation < 2) {
            val position = picked.size
            if (position % 6 == 5 && picked.takeLast(6).none { it.explore }) {
                if (!candidate.explore) return false
            }
            if (position % 5 == 4 && picked.takeLast(5).none { it.dailyBaseline }) {
                if (!candidate.dailyBaseline) return false
            }
        }
        return true
    }

    private fun authorOf(scored: ScoredTrack): String? =
        scored.authorKey ?: authorOf(scored.track)

    private fun authorOf(track: Track): String? {
        val author = track.artist.trim()
        return author.takeIf { it.isNotBlank() && !it.equals("Bilibili", ignoreCase = true) }?.lowercase()
    }

    private fun topicsOf(scored: ScoredTrack): Set<String> =
        scored.topicKeys.ifEmpty { topicsOf(scored.track) }

    private fun topicsOf(track: Track): Set<String> =
        ContentProfileParser.profileFromTrack(track).topicKeys.filter { it != "unknown" }.toSet()
}
