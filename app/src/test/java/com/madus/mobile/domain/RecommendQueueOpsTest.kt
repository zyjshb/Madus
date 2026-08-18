package com.madus.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendQueueOpsTest {

    @Test
    fun forYouKeepsUpcomingButPeelsSameUp() {
        val queue = listOf(
            t("a", "hist"),
            t("cur", "up-x"),
            t("same1", "up-x"),
            t("same2", "up-x"),
            t("ok", "other"),
        )
        val result = RecommendQueueOps.afterNotInterested(
            queue = queue,
            currentIndex = 1,
            dislikedId = "cur",
            isForYou = true,
            isBlocked = { false },
        )
        assertEquals(listOf("a", "ok"), result.queue.map { it.id })
        assertEquals(1, result.playIndex)
    }

    @Test
    fun forYouKeepsDifferentUpRelated() {
        val queue = listOf(t("cur", "up-a"), t("rel1", "up-b"), t("rel2", "up-c"))
        val result = RecommendQueueOps.afterNotInterested(
            queue = queue,
            currentIndex = 0,
            dislikedId = "cur",
            isForYou = true,
            isBlocked = { false },
        )
        assertEquals(listOf("rel1", "rel2"), result.queue.map { it.id })
        assertEquals(0, result.playIndex)
    }

    @Test
    fun scatterPullsDifferentKindToSecondSlot() {
        val upcoming = listOf(
            Track(id = "c1", title = "翻唱A", artist = "a"),
            Track(id = "c2", title = "翻唱B", artist = "b"),
            Track(id = "g1", title = "原神攻略", artist = "c"),
        )
        val out = RecommendQueueOps.scatterDifferentKind(
            upcoming,
            ContentProfileParser.kindKeys(upcoming[0]),
        )
        assertEquals(listOf("c1", "g1", "c2"), out.map { it.id })
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

    private fun t(id: String, artist: String = "up") =
        Track(id = id, title = id, artist = artist)
}
