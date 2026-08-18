package com.madus.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendQueueOpsTest {

    @Test
    fun forYouDropsWholeRelatedChain() {
        val queue = listOf(t("a"), t("b"), t("cur"), t("rel1"), t("ok"))
        val result = RecommendQueueOps.afterNotInterested(
            queue = queue,
            currentIndex = 2,
            dislikedId = "cur",
            isForYou = true,
            isBlocked = { it.id == "b" },
        )
        assertEquals(listOf("a"), result.queue.map { it.id })
        assertEquals(-1, result.playIndex)
    }

    @Test
    fun forYouFirstTrackLeavesEmptyQueue() {
        val queue = listOf(t("cur"), t("rel1"), t("rel2"))
        val result = RecommendQueueOps.afterNotInterested(
            queue = queue,
            currentIndex = 0,
            dislikedId = "cur",
            isForYou = true,
            isBlocked = { false },
        )
        assertTrue(result.queue.isEmpty())
        assertEquals(-1, result.playIndex)
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
