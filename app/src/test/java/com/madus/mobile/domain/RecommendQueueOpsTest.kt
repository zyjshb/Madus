package com.madus.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendQueueOpsTest {

    @Test
    fun forYouSkipsToFirstUnblockedUpcoming() {
        val queue = listOf(t("a"), t("b"), t("cur"), t("same"), t("ok"))
        val result = RecommendQueueOps.afterNotInterested(
            queue = queue,
            currentIndex = 2,
            dislikedId = "cur",
            isForYou = true,
            isBlocked = { it.id == "b" || it.id == "same" },
        )
        assertEquals(listOf("a", "ok"), result.queue.map { it.id })
        assertEquals(1, result.playIndex)
    }

    @Test
    fun forYouNoUpcomingNeedsFetch() {
        val queue = listOf(t("a"), t("cur"), t("same"))
        val result = RecommendQueueOps.afterNotInterested(
            queue = queue,
            currentIndex = 1,
            dislikedId = "cur",
            isForYou = true,
            isBlocked = { it.id == "same" },
        )
        assertEquals(listOf("a"), result.queue.map { it.id })
        assertEquals(-1, result.playIndex)
    }

    @Test
    fun forYouFirstTrackLeavesOnlyUnblockedTail() {
        val queue = listOf(t("cur"), t("rel1"), t("ok"))
        val result = RecommendQueueOps.afterNotInterested(
            queue = queue,
            currentIndex = 0,
            dislikedId = "cur",
            isForYou = true,
            isBlocked = { it.id == "rel1" },
        )
        assertEquals(listOf("ok"), result.queue.map { it.id })
        assertEquals(0, result.playIndex)
    }

    @Test
    fun playlistKeepsLaterUnblockedTracks() {
        val queue = listOf(t("a"), t("cur"), t("c"))
        val result = RecommendQueueOps.afterNotInterested(
            queue = queue,
            currentIndex = 1,
            dislikedId = "cur",
            isForYou = false,
            isBlocked = { false },
        )
        assertEquals(listOf("a", "c"), result.queue.map { it.id })
        assertEquals(1, result.playIndex)
    }

    @Test
    fun hasPlayableNextOnlyWhenSomethingFollows() {
        assertFalse(RecommendQueueOps.hasPlayableNext(0, 0))
        assertFalse(RecommendQueueOps.hasPlayableNext(3, 2))
        assertTrue(RecommendQueueOps.hasPlayableNext(3, 1))
    }

    @Test
    fun forYouNeedsRefillWhenEmptyOrAtEnd() {
        assertTrue(RecommendQueueOps.needsForYouRefill(true, 0, 0))
        assertTrue(RecommendQueueOps.needsForYouRefill(true, 3, 2))
        assertFalse(RecommendQueueOps.needsForYouRefill(true, 3, 1))
        assertFalse(RecommendQueueOps.needsForYouRefill(false, 0, 0))
    }

    private fun t(id: String) = Track(id = id, title = id, artist = "up")
}
