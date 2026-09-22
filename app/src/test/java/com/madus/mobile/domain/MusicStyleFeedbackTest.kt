package com.madus.mobile.domain

import org.junit.Assert.*
import org.junit.Test

class MusicStyleFeedbackTest {
    private val now = 1_800_000_000_000L
    private val engine = RecommendationEngine()
    private val context = FeedContext(nowMs = now, musicOnly = true)
    private val listened = listOf(RecommendationEvent("heard", "heard", RecommendationEventType.WATCH_90,
        now, "recommend", setOf("music", "folk"), "artist", "heard"))
    private fun vote(direction: Int, key: String = "sample", age: Long = 0, topic: String = "folk") =
        MusicStyleFeedback(key, setOf(topic), direction, now - age)
    private fun state(votes: List<MusicStyleFeedback>) =
        engine.buildInterestState(listened, now, setOf("folk"), votes)
    private fun candidate(topic: String = "民谣") = Track("fresh", "歌曲《fresh》", "artist",
        durationMs = 180_000, categoryId = 28, tags = listOf(topic))
    private fun score(votes: List<MusicStyleFeedback>, track: Track = candidate()) =
        engine.scoreCandidate(track, null, state(votes), "search", context)

    @Test fun manualFeedbackLeavesAllAutomaticLearningAndExplicitTasteUntouched() {
        val baseline = state(emptyList())
        for (direction in listOf(-1, 1)) {
            val modified = state(listOf(vote(direction)))
            assertEquals(baseline, modified.copy(styleAdjustments = emptyMap()))
        }
    }

    @Test fun moreAndLessChangeScoresWithoutBlockingSongOrGenre() {
        val baseline = score(emptyList())
        val more = score(listOf(vote(1)))
        val less = score(listOf(vote(-1)))
        assertTrue(more.score > baseline.score)
        assertTrue(less.score < baseline.score)
        assertTrue(less.inInterestPool)
        assertFalse(less.cooledDown)
        assertTrue(RecommendationReRanker().rerank(listOf(less), context).isNotEmpty())
    }

    @Test fun duplicateVotesCannotAccumulateAndLastChoiceWins() {
        val repeated = List(100) { vote(1) }
        assertEquals(score(listOf(vote(1))).score, score(repeated).score, 1e-9)
        assertEquals(score(listOf(vote(-1))).score,
            score(repeated.map { it.copy(occurredAtMs = now - 1) } + vote(-1)).score, 1e-9)
    }

    @Test fun manySongsStillHaveBoundedAuxiliaryInfluence() {
        val baseline = score(emptyList()).score
        val lots = (1..100).map { vote(1, "song$it") }
        assertEquals(MusicStyleFeedback.MAX_SCORE_ADJUSTMENT, score(lots).score - baseline, 1e-9)
        assertEquals(-MusicStyleFeedback.MAX_SCORE_ADJUSTMENT,
            score(lots.map { it.copy(direction = -1) }).score - baseline, 1e-9)
    }

    @Test fun manualFeedbackCannotAdmitAnUnrelatedGenreOrCreateHeardHistory() {
        val track = candidate("说唱")
        val ranked = score((1..100).map { vote(1, "rap$it", topic = "rap") }, track)
        assertFalse(ranked.inInterestPool)
        assertTrue(RecommendationReRanker().rerank(listOf(ranked), context).isEmpty())
        assertEquals(1, state(listOf(vote(1))).evidenceSongCount)
    }

    @Test fun withdrawAndExpirationRestoreOriginalRanking() {
        val baseline = score(emptyList()).score
        assertEquals(baseline, score(listOf(vote(0))).score, 1e-9)
        assertEquals(baseline, score(listOf(vote(1, age = MusicStyleFeedback.TTL_MS + 1))).score, 1e-9)
        assertTrue(score(listOf(vote(1, age = 30 * 24 * 60 * 60 * 1000L))).score < score(listOf(vote(1))).score)
    }

    @Test fun classFeedbackUsesSpecificGenresInsteadOfLanguageAndBroadCategory() {
        assertEquals(setOf("folk"), MusicStyleFeedback.topics(setOf("music", "folk", "mandarin", "live")))
        assertTrue(MusicStyleFeedback.topics(setOf("music", "mandarin", "unknown")).isEmpty())
        assertEquals(setOf("healing"), MusicStyleFeedback.topics(setOf("music", "healing")))
        assertEquals(score(emptyList()).score, score(listOf(vote(1, topic = "rap"))).score, 1e-9)
    }
}
