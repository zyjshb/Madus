package com.madus.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendQueueOpsTest {

    @Test
    fun forYouKeepsOnlyUnblockedHistory() {
        val queue = listOf(t("a"), t("b"), t("cur"), t("rel1"), t("rel2"))
        val kept = RecommendQueueOps.historyAfterNotInterested(
            queue = queue,
            currentIndex = 2,
            dislikedId = "cur",
            isForYou = true,
            isBlocked = { it.id == "b" },
        )
        assertEquals(listOf("a"), kept.map { it.id })
    }

    @Test
    fun forYouFirstTrackLeavesEmptyHistory() {
        val queue = listOf(t("cur"), t("rel1"), t("rel2"))
        val kept = RecommendQueueOps.historyAfterNotInterested(
            queue = queue,
            currentIndex = 0,
            dislikedId = "cur",
            isForYou = true,
            isBlocked = { false },
        )
        assertTrue(kept.isEmpty())
        assertFalse(RecommendQueueOps.hasPlayableNext(kept.size, (kept.size - 1).coerceAtLeast(0)))
    }

    @Test
    fun playlistKeepsLaterUnblockedTracks() {
        val queue = listOf(t("a"), t("cur"), t("c"))
        val kept = RecommendQueueOps.historyAfterNotInterested(
            queue = queue,
            currentIndex = 1,
            dislikedId = "cur",
            isForYou = false,
            isBlocked = { false },
        )
        assertEquals(listOf("a", "c"), kept.map { it.id })
    }

    @Test
    fun hasPlayableNextOnlyWhenSomethingFollows() {
        assertFalse(RecommendQueueOps.hasPlayableNext(0, 0))
        assertFalse(RecommendQueueOps.hasPlayableNext(3, 2))
        assertTrue(RecommendQueueOps.hasPlayableNext(3, 1))
    }

    private fun t(id: String) = Track(id = id, title = id, artist = "up")
}
