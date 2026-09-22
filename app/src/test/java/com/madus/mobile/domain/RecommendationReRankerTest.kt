package com.madus.mobile.domain

import org.junit.Assert.*
import org.junit.Test

class RecommendationReRankerTest {
    private val reranker = RecommendationReRanker()
    private fun track(id: String, author: String = "up-$id") = Track(
        id, "单曲《$id》", author, durationMs = 180_000, categoryId = 28, tags = listOf("民谣"))
    private fun candidate(id: String, score: Double = 10.0, inPool: Boolean = true,
        adjacent: Boolean = false, author: String = "up-$id") = ScoredTrack(
        track(id, author), score, "search", topicKeys = setOf("folk"),
        inInterestPool = inPool, adjacentInterest = adjacent, explore = adjacent)

    @Test fun interestPoolRemainsContinuousAndDoesNotInjectUnrelatedHighScores() {
        val core = (1..30).map { candidate("core$it", 100.0 - it) }
        val irrelevant = (1..50).map { candidate("off$it", 1000.0, inPool = false) }
        val output = reranker.rerank(core + irrelevant, FeedContext(musicOnly = true, limit = 30))
        assertEquals(30, output.size)
        assertTrue(output.all { it.id.startsWith("core") })
    }

    @Test fun adjacentDiscoveryActuallyGetsSlotsAlongsideAnAbundantCorePool() {
        val core = (1..30).map { candidate("core$it", author = "one-uploader") }
        val adjacent = (1..30).map { candidate("adjacent$it", 999.0, false, true) }
        val output = reranker.rerank(core + adjacent, FeedContext(musicOnly = true, limit = 30))
        assertEquals(30, output.size)
        assertEquals(7, output.count { it.id.startsWith("adjacent") })
    }

    @Test fun uploaderVarietyIsPreservedWhenThereAreChoicesInsideTheSameGenre() {
        val a = (1..4).map { candidate("a$it", 10.0 - it * 0.01, author = "same-up") }
        val b = (1..12).map { candidate("b$it", 9.9 - it * 0.01) }
        val output = reranker.rerank(a + b, FeedContext(musicOnly = true, limit = 16))
        assertEquals(16, output.size)
        for (window in output.windowed(4)) assertTrue(window.count { it.artist == "same-up" } <= 1)
    }

    @Test fun uploaderVarietyCannotPromoteMuchWeakerMusicalMatches() {
        val strong = (1..10).map { candidate("strong$it", 10.0, author = "same-up") }
        val weak = (1..10).map { candidate("weak$it", 2.0) }
        assertTrue(reranker.rerank(strong + weak, FeedContext(musicOnly = true, limit = 10))
            .all { it.id.startsWith("strong") })
    }

    @Test fun explorationHasARealBoundedSlotEvenWhenTheCorePoolIsFull() {
        val core = (1..26).map { candidate("core$it") }
        val adjacent = (1..50).map { candidate("adj$it", 999.0, false, true) }
        val output = reranker.rerank(core + adjacent, FeedContext(musicOnly = true, limit = 30))
        assertEquals(30, output.size)
        for (size in 1..output.size) assertTrue(output.take(size).count { it.id.startsWith("adj") } <= size * 0.25)
        assertEquals(7, output.count { it.id.startsWith("adj") })
        val plenty = core + (27..40).map { candidate("core$it") }
        assertEquals(7, reranker.rerank(plenty + adjacent, FeedContext(musicOnly = true, limit = 30))
            .count { it.id.startsWith("adj") })
    }

    @Test fun smallInterestPoolCannotBeReplacedWithMostlyExploration() {
        val core = (1..6).map { candidate("core$it") }
        val adjacent = (1..100).map { candidate("adj$it", 999.0, false, true) }
        val output = reranker.rerank(core + adjacent, FeedContext(musicOnly = true, limit = 30))
        assertEquals(8, output.size)
        assertEquals(2, output.count { it.id.startsWith("adj") })
        assertTrue(reranker.rerank(adjacent, FeedContext(musicOnly = true, limit = 30)).isEmpty())
        assertEquals(6, reranker.rerank(core + adjacent,
            FeedContext(musicOnly = true, limit = 30, maxExploreRatio = 0.0)).size)
    }

    @Test fun positiveRealtimeMatchesCanFillThePoolWithoutLegacyThreeItemCap() {
        val realtime = (1..12).map { candidate("realtime$it").copy(realtime = true) }
        assertEquals(12, reranker.rerank(realtime, FeedContext(musicOnly = true, limit = 12)).size)
        assertEquals(2, reranker.rerank(realtime, FeedContext(musicOnly = true,
            limit = 12, realtimeTopicQuota = mapOf("folk" to 2))).size)
    }

    @Test fun sessionQueueAndSongCooldownAreHardConstraints() {
        val candidates = listOf(candidate("seen"), candidate("queued"), candidate("skipped").copy(cooledDown = true),
            candidate("song"), candidate("fresh"), candidate("alias").copy(track = track("alias").copy(bvid = "BVSEEN")))
        val output = reranker.rerank(candidates, FeedContext(musicOnly = true,
            sessionSeenIds = setOf("seen", "BVSEEN"), queueIds = setOf("queued"), recentSongKeys = setOf("song")))
        assertEquals(listOf("fresh"), output.map { it.id })
    }

    @Test fun explicitIdBvidAndAuthorBlocksAreAlwaysRespected() {
        val candidates = listOf(candidate("id"), candidate("bv").copy(track = track("bv").copy(bvid = "BVNO")),
            candidate("owner").copy(track = track("owner").copy(ownerMid = "99")),
            candidate("muted", author = "muted-up"), candidate("ok"))
        val output = reranker.rerank(candidates, FeedContext(musicOnly = true, blockedIds = setOf("id"),
            blockedBvids = setOf("BVNO"), blockedAuthorIds = setOf("99"), mutedAuthors = setOf("muted-up")))
        assertEquals(listOf("ok"), output.map { it.id })
    }

    @Test fun reasonOutputUsesExactlyTheSameSelectionAndOrdering() {
        val candidates = (1..8).map { candidate("song$it", score = it.toDouble()).copy(reason = "符合民谣口味") }
        val context = FeedContext(musicOnly = true, limit = 5)
        val (tracks, reasons) = reranker.rerankWithReasons(candidates, context)
        assertEquals(reranker.rerank(candidates, context), tracks)
        assertEquals(tracks, reasons.map { it.track })
        assertTrue(reasons.all { it.reason == "符合民谣口味" })
    }
}
