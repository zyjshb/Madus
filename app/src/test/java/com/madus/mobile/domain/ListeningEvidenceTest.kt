package com.madus.mobile.domain

import org.junit.Assert.*
import org.junit.Test

class ListeningEvidenceTest {
    private val now = 1_800_000_000_000L
    private val engine = RecommendationEngine()
    private fun sample(id: String, topic: String, heard: Long, duration: Long = 200_000,
        dwell: Long = 0, searched: Boolean = false, session: String = id, age: Long = 0) =
        RecommendationEvent(id, id, RecommendationEventType.LISTEN_SAMPLE, now - age, "recommend",
            setOf(topic), null, id, session, heard, duration, dwell, searched)
    private fun like(id: String, topic: String) = sample(id, topic, 0).copy(type = RecommendationEventType.LIKE,
        listeningSessionId = "")

    @Test fun aFewLikesCannotReplaceLongerEngagedListeningAcrossOtherSongs() {
        val folk = (1..5).map { sample("folk$it", "folk", 190_000, dwell = 120_000,
            age = 2 * RecommendationTuning.HOURLY_TTL_MS) }
        val piano = (1..3).flatMap { listOf(like("piano$it", "instrumental"), sample("piano$it", "instrumental", 5_000)) }
        val state = engine.buildInterestState(folk + piano, now)
        assertTrue(state.interestTopics.getValue("folk") > state.interestTopics.getValue("instrumental"))
    }

    @Test fun averageResponseBeatsRecommendationExposureVolume() {
        val short = (1..80).map { sample("piano$it", "instrumental", 12_000) }
        val engaged = (1..4).map { sample("folk$it", "folk", 180_000) }
        val state = engine.buildInterestState(short + engaged, now)
        assertTrue(state.interestTopics.getValue("folk") > state.interestTopics.getValue("instrumental"))
    }

    @Test fun actualListeningAndExplicitActionsContributeTogether() {
        val heard = sample("a", "folk", 150_000)
        val liked = like("a", "folk")
        val collected = liked.copy(type = RecommendationEventType.COLLECT_LOCAL)
        assertTrue(engine.songQuality(listOf(heard, liked)) > engine.songQuality(listOf(liked)))
        assertTrue(engine.songQuality(listOf(heard, liked)) > engine.songQuality(listOf(heard)))
        assertTrue(engine.songQuality(listOf(heard, collected)) > engine.songQuality(listOf(heard)))
        assertEquals(engine.songQuality(listOf(heard, liked)),
            engine.songQuality(listOf(heard) + List(100) { liked }), 1e-9)
    }

    @Test fun durationsAndForegroundDwellAreNormalizedAndCannotRewardIdleScreenTime() {
        val shortSong = sample("a", "folk", 90_000, duration = 100_000)
        val longSong = sample("a", "folk", 90_000, duration = 400_000)
        assertTrue(engine.songQuality(listOf(shortSong)) > engine.songQuality(listOf(longSong)))
        assertTrue(engine.songQuality(listOf(shortSong.copy(foregroundMs = 60_000))) > engine.songQuality(listOf(shortSong)))
        assertEquals(engine.songQuality(listOf(shortSong.copy(foregroundMs = 90_000))),
            engine.songQuality(listOf(shortSong.copy(foregroundMs = 99_000_000))), 1e-9)
    }

    @Test fun sessionSnapshotsReplaceAndRepeatedPlaysAreAveraged() {
        val last = sample("a", "folk", 180_000)
        val snapshots = (1..90).map { last.copy(listenedMs = it * 2000L) }
        assertEquals(engine.songQuality(listOf(last)), engine.songQuality(snapshots), 1e-9)
        val quickReturn = sample("a", "folk", 2_000, session = "second")
        assertTrue(engine.songQuality(listOf(last, quickReturn)) < engine.songQuality(listOf(last)))
        val repeated = (1..50).map { last.copy(listeningSessionId = "session$it") }
        assertEquals(engine.buildInterestState(listOf(last), now).interestTopics,
            engine.buildInterestState(repeated, now).interestTopics)
    }

    @Test fun aShortClickDoesNotEstablishAnExclusiveTaste() {
        val state = engine.buildInterestState(listOf(sample("a", "instrumental", 2_000)), now)
        assertEquals(0.0, state.confidence, 1e-9)
        val queries = MusicDiscovery.searchQueries(state, emptyList(), 0, now)
        assertTrue(queries.any { it.contains("流行") })
        assertTrue(queries.any { it.contains("民谣") })
    }

    @Test fun submittedSearchIsWeakDeduplicatedAndExpiresWithoutCreatingHeardHistory() {
        val query = RecommendationEvent("query:folk", "", RecommendationEventType.SEARCH_INTENT,
            now, "search", setOf("folk"), null)
        val repeated = List(100) { query }
        assertEquals(engine.buildInterestState(listOf(query), now).searchTopics,
            engine.buildInterestState(repeated, now).searchTopics)
        assertTrue(engine.buildInterestState(repeated, now).interestTopics.isEmpty())
        assertTrue(MusicDiscovery.searchQueries(engine.buildInterestState(repeated, now), emptyList(), 0, now)
            .first().contains("民谣"))
        assertTrue(MusicDiscovery.recentlyHeard(repeated, now).isEmpty())
        assertTrue(engine.buildInterestState(repeated, now + RecommendationTuning.HOURLY_TTL_MS + 1).searchTopics.isEmpty())
        val selected = sample("a", "folk", 60_000, searched = true)
        assertTrue(engine.songQuality(listOf(selected)) > engine.songQuality(listOf(selected.copy(fromSearch = false))))
        assertEquals(engine.songQuality(listOf(selected.copy(listenedMs = 2_000))),
            engine.songQuality(listOf(selected.copy(listenedMs = 2_000, fromSearch = false))), 1e-9)
    }
}
