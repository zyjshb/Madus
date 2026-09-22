package com.madus.mobile.domain

import org.junit.Assert.*
import org.junit.Test

class RecommendationVarietyScenarioTest {
    private val now = 1_800_000_000_000L
    private val engine = RecommendationEngine()
    private val ranker = RecommendationReRanker()
    private val labels = mapOf("instrumental" to "纯音乐", "folk" to "民谣", "pop" to "流行")
    private fun song(topic: String, index: Int) = Track("$topic$index", "单曲《$topic$index》", "up$topic$index",
        durationMs = 200_000, categoryId = 28, tags = listOf(labels.getValue(topic)))
    private fun genre(track: Track) = ContentProfileParser.profileFromTrack(track).topicKeys.intersect(labels.keys).first()
    private fun like(index: Int) = RecommendationEvent("liked$index", "", RecommendationEventType.LIKE,
        now, "recommend", setOf("instrumental"), null, "liked$index")

    @Test fun threeInstrumentalLikesCannotProduceAnEndlessInstrumentalRadioWithOneTrackRefills() {
        val candidates = labels.keys.flatMap { topic -> (1..50).map { song(topic, it) } }
        val events = (1..3).map { like(it) }
        val history = mutableListOf<Track>()
        val state = engine.buildInterestState(events, now)
        repeat(24) {
            val base = FeedContext(musicOnly = true, nowMs = now, limit = 1,
                sessionSeenIds = history.map { it.id }.toSet(), recentQueue = history.takeLast(8))
            val past = history.takeLast(8).map { engine.scoreCandidate(it, null, state, "search", base) }
            val context = base.copy(recentExploration = past.map { it.explore }, recentTopicKeys = past.map { it.topicKeys })
            val ranked = ranker.rerank(candidates.map { engine.scoreCandidate(it, null, state, "search", context) }, context)
            assertEquals(1, ranked.size)
            history += ranked.single()
        }
        assertEquals(6, history.count { genre(it) != "instrumental" })
        assertTrue(history.windowed(4).all { window -> window.any { genre(it) != "instrumental" } })
    }

    @Test fun multipleExistingInterestsAreBalancedEvenWhenOneGenreHasMuchHigherScores() {
        val candidates = (1..20).map { ScoredTrack(song("instrumental", it), 12.0, "search", topicKeys = setOf("instrumental")) } +
            (1..20).map { ScoredTrack(song("folk", it), 7.0, "search", topicKeys = setOf("folk")) }
        val output = ranker.rerank(candidates, FeedContext(musicOnly = true, limit = 18))
        assertEquals(18, output.size)
        assertTrue(output.windowed(3).none { window -> window.all { genre(it) == "instrumental" } })
        assertTrue(output.windowed(6).all { window -> window.count { genre(it) == "instrumental" } <= 4 })
    }

    @Test fun ongoingListeningCanMoveTheRadioAwayFromInitialLikesWithoutLosingVariety() {
        val candidates = labels.keys.flatMap { topic -> (1..50).map { song(topic, it) } }
        val events = (1..3).map { like(it) }.toMutableList()
        val history = mutableListOf<Track>()
        repeat(24) { index ->
            val at = now + index * 210_000L
            val state = engine.buildInterestState(events, at)
            val base = FeedContext(musicOnly = true, nowMs = at, limit = 1,
                sessionSeenIds = history.map { it.id }.toSet(), recentQueue = history.takeLast(8))
            val past = history.takeLast(8).map { engine.scoreCandidate(it, null, state, "search", base) }
            val context = base.copy(recentExploration = past.map { it.explore }, recentTopicKeys = past.map { it.topicKeys })
            val next = ranker.rerank(candidates.map { engine.scoreCandidate(it, null, state, "search", context) }, context).single()
            history += next
            val topic = genre(next)
            val heard = if (topic == "instrumental") 10_000L else 190_000L
            events += RecommendationEvent(next.id, "", RecommendationEventType.LISTEN_SAMPLE, at, "recommend",
                setOf(topic), null, next.id, "play$index", heard, next.durationMs, heard)
        }
        assertTrue(history.takeLast(12).count { genre(it) != "instrumental" } >= 8)
        assertTrue(history.takeLast(12).map { genre(it) }.distinct().size >= 2)
    }

    @Test fun queuedFutureTracksCountWhenAppendingSoRefillBoundariesDoNotRestartVariety() {
        val history = (1..3).map { song("instrumental", it) }
        val state = engine.buildInterestState((1..3).map { like(it) }, now)
        val context = FeedContext(musicOnly = true, nowMs = now, limit = 1, recentQueue = history,
            recentExploration = listOf(false, false, false))
        val candidates = listOf(song("instrumental", 4), song("folk", 4)).map {
            engine.scoreCandidate(it, null, state, "search", context)
        }
        assertEquals("folk", genre(ranker.rerank(candidates, context).single()))
    }

    @Test fun discoveryRecallKeepsVocalAlternativesEvenInTwoRequestThriftMode() {
        val state = engine.buildInterestState(listOf(like(1)), now)
        for (round in 0..10) {
            val queries = MusicDiscovery.searchQueries(state, emptyList(), round, now).take(2)
            assertTrue(queries[0].contains("纯音乐"))
            assertTrue(queries[1].contains("民谣") || queries[1].contains("流行"))
        }
        assertFalse(MusicDiscovery.hasVariety((1..10).map { song("instrumental", it).copy(tags = listOf("纯音乐", "爵士")) }))
    }

    @Test fun missingAlternativesNeverInsertVideosOrDislikedRecordings() {
        val state = engine.buildInterestState(listOf(like(1)), now)
        val context = FeedContext(musicOnly = true, nowMs = now, limit = 8, blockedIds = setOf("folk1"))
        val catalog = (1..10).map { song("instrumental", it) } + song("folk", 1) +
            song("pop", 1).copy(title = "手机评测教程", categoryId = 95)
        val output = ranker.rerank(catalog.map { engine.scoreCandidate(it, null, state, "search", context) }, context)
        assertEquals(8, output.size)
        assertTrue(output.all { genre(it) == "instrumental" })
    }
}
