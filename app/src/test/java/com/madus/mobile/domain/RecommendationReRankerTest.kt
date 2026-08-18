package com.madus.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationReRankerTest {

    private val reranker = RecommendationReRanker()

    @Test
    fun singleLikeInsertsAtMostOneRealtimeItemInPositionsTwoToFour() {
        val realtimeA = candidate(
            track("rt-a", 3, "up-a"),
            98.0,
            realtime = true,
            source = "realtime-related",
        )
        val candidates = listOf(
            candidate(track("t0", 95, "up-0"), 100.0),
            candidate(track("t1", 160, "up-1"), 99.0),
            realtimeA,
            candidate(track("t2", 1, "up-2"), 97.0),
            candidate(track("t3", 4, "up-3"), 96.0),
            candidate(track("t4", 36, "up-4"), 95.0),
            candidate(track("t5", 181, "up-5"), 94.0),
            candidate(track("t6", 5, "up-6"), 93.0),
            candidate(track("t7", 20, "up-7"), 92.0),
        )

        val allowed = reranker.rerank(
            candidates,
            FeedContext(limit = 9, realtimeTopicQuota = mapOf("music" to 1)),
        )
        val index = allowed.indexOfFirst { it.id == "rt-a" }
        assertTrue("实时 A 应出现在第 2~4 位", index in 2..4)
        assertEquals(1, allowed.subList(2, 5).count { it.id == "rt-a" })
        assertFalse(allowed[0].id == "rt-a")
        assertFalse(allowed[1].id == "rt-a")

        val blocked = reranker.rerank(
            candidates,
            FeedContext(limit = 8, realtimeTopicQuota = mapOf("music" to 0)),
        )
        assertTrue(blocked.none { it.id == "rt-a" })
    }

    @Test
    fun likingAThenBPicksBothTopicsWithoutStackingA() {
        val a = (0..3).map { i -> candidate(track("a$i", 3, "up-a$i"), 100.0 - i) }
        val b = (0..3).map { i -> candidate(track("b$i", 4, "up-b$i"), 96.0 - i) }
        val c = (0..3).map { i -> candidate(track("c$i", 36, "up-c$i"), 92.0 - i) }

        val output = reranker.rerank(a + b + c, FeedContext(limit = 12))

        assertEquals(12, output.size)
        assertTrue(output.count { it.id.startsWith("a") } >= 2)
        assertTrue(output.count { it.id.startsWith("b") } >= 2)
        assertFalse(output.take(4).all { it.id.startsWith("a") })
        for (i in 3 until output.size) {
            val window = output.subList(i - 3, i + 1)
            assertTrue(
                "第 $i 位窗口内 A 类超过 2 条",
                window.count { it.id.startsWith("a") } <= 2,
            )
        }
    }

    @Test
    fun sameUpAtMostOnceInRecentFour() {
        val candidates = listOf(
            candidate(track("u1", 3, "UP_ONE"), 100.0),
            candidate(track("o1", 36, "other-1"), 99.0),
            candidate(track("o2", 160, "other-2"), 98.0),
            candidate(track("o3", 181, "other-3"), 97.0),
            candidate(track("o4", 5, "other-4"), 96.0),
            candidate(track("u2", 4, "UP_ONE"), 95.0),
            candidate(track("o5", 20, "other-5"), 94.0),
        )

        val output = reranker.rerank(candidates, FeedContext(limit = 7))

        assertEquals(7, output.size)
        for (i in 3 until output.size) {
            val window = output.subList(i - 3, i + 1)
            assertTrue(
                "第 $i 位窗口内同一 UP 超过 1 条",
                window.count { it.artist.equals("UP_ONE", ignoreCase = true) } <= 1,
            )
        }
    }

    @Test
    fun sameTopicAtMostTwiceInRecentFour() {
        val candidates = listOf(
            candidate(track("m1", 3, "musician-1"), 100.0),
            candidate(track("m2", 28, "musician-2"), 99.0),
            candidate(track("o1", 36, "other-1"), 98.0),
            candidate(track("o2", 160, "other-2"), 97.0),
            candidate(track("m3", 29, "musician-3"), 96.0),
            candidate(track("o3", 181, "other-3"), 95.0),
            candidate(track("o4", 5, "other-4"), 94.0),
            candidate(track("o5", 20, "other-5"), 93.0),
        )

        val output = reranker.rerank(candidates, FeedContext(limit = 8))

        assertEquals(8, output.size)
        assertTrue(output.any { it.id == "m3" })
        for (i in 3 until output.size) {
            val window = output.subList(i - 3, i + 1)
            assertTrue(
                "第 $i 位窗口内 music 主题超过 2 条",
                window.count { it.categoryId in setOf(3, 28, 29) } <= 2,
            )
        }
    }

    @Test
    fun homepageOnlyPoolStillYieldsTracks() {
        val homepage = (0 until 8).map { i ->
            candidate(track("h$i", if (i % 2 == 0) 3 else 4, "up-$i"), 80.0 - i, source = "homepage")
        }
        val output = reranker.rerank(homepage, FeedContext(limit = 8))
        assertTrue("首页热门池不该被重排掏空", output.isNotEmpty())
        assertTrue(output.size >= 4)
    }

    @Test
    fun singleUpPoolDegradesSafelyWithoutDroppingCandidates() {
        val candidates = listOf(
            candidate(track("same-0", 3, "ONLY_UP"), 100.0),
            candidate(track("same-1", 4, "ONLY_UP"), 90.0),
            candidate(track("same-2", 1, "ONLY_UP"), 80.0),
            candidate(track("same-3", 36, "ONLY_UP"), 70.0),
            candidate(track("same-4", 20, "ONLY_UP"), 60.0),
        )

        val output = reranker.rerank(candidates, FeedContext(limit = 5))

        assertEquals(5, output.size)
        assertEquals(
            listOf("same-0", "same-1", "same-2", "same-3", "same-4"),
            output.map { it.id },
        )

        val (tracks, pickedWithReasons) = reranker.rerankWithReasons(candidates, FeedContext(limit = 5))
        assertEquals(output, tracks)
        assertEquals(5, pickedWithReasons.size)
    }

    @Test
    fun exploreQuotaKeepsOneExploreInEverySix() {
        val normalCategories = listOf(1, 3, 4, 20, 36, 181, 5, 28, 2)
        val normal = normalCategories.mapIndexed { i, category ->
            candidate(track("n$i", category, "normal-$i"), 210.0 - i)
        }
        val explore = listOf(
            candidate(track("e0", 95, "explore-0"), 300.0, explore = true),
            candidate(track("e1", 17, "explore-1"), 99.0, explore = true),
        )
        val daily = listOf(candidate(track("d0", 160, "daily-0"), 290.0, dailyBaseline = true))

        val output = reranker.rerank(normal + explore + daily, FeedContext(limit = 12))

        assertEquals(12, output.size)
        assertEquals("e0", output[0].id)
        assertEquals("e1", output[11].id)
        for (start in 0 until output.size step 6) {
            val window = output.subList(start, start + 6)
            assertTrue("第 $start 起的 6 条没有 explore", window.any { it.id.startsWith("e") })
        }
    }

    @Test
    fun dailyBaselineQuotaKeepsOneBaselineInEveryFive() {
        val normalCategories = listOf(1, 3, 4, 20, 36, 181, 5, 28, 2)
        val normal = normalCategories.mapIndexed { i, category ->
            candidate(track("n$i", category, "normal-$i"), 210.0 - i)
        }
        val daily = listOf(
            candidate(track("d0", 95, "daily-0"), 300.0, dailyBaseline = true),
            candidate(track("d1", 17, "daily-1"), 99.0, dailyBaseline = true),
        )
        val explore = listOf(candidate(track("e0", 160, "explore-0"), 290.0, explore = true))

        val output = reranker.rerank(normal + daily + explore, FeedContext(limit = 10))

        assertEquals(10, output.size)
        assertEquals("d0", output[0].id)
        assertEquals("d1", output[9].id)
        for (start in 0 until output.size step 5) {
            val window = output.subList(start, start + 5)
            assertTrue("第 $start 起的 5 条没有 dailyBaseline", window.any { it.id.startsWith("d") })
        }
    }

    @Test
    fun sessionAndQueueIdsAreHardDeduped() {
        val categories = listOf(95, 160, 1, 3, 4, 20, 36, 181)
        val fresh = categories.mapIndexed { i, category ->
            candidate(track("fresh-$i", category, "fresh-$i"), 500.0 - i)
        }
        val candidates = listOf(
            candidate(track("seen-1", 3, "seen-up"), 1000.0),
            candidate(track("queued-1", 4, "queued-up"), 999.0),
        ) + fresh

        val output = reranker.rerank(
            candidates,
            FeedContext(
                limit = 8,
                sessionSeenIds = setOf("seen-1"),
                queueIds = setOf("queued-1"),
            ),
        )

        assertEquals(8, output.size)
        assertTrue(output.none { it.id == "seen-1" || it.id == "queued-1" })
        assertEquals(8, output.map { it.id }.toSet().size)
    }

    @Test
    fun blockedIdBvidAndAuthorNeverPicked() {
        val hidden = candidate(
            Track(id = "hid", title = "nope", artist = "up-x", bvid = "BV1hid", ownerMid = "99"),
            999.0,
        )
        val sameUp = candidate(
            Track(id = "same-up", title = "also", artist = "other", bvid = "BV2", ownerMid = "99"),
            998.0,
        )
        val mutedName = candidate(track("muted-name", 3, "up-x"), 997.0)
        val ok = candidate(track("ok", 36, "fresh-up"), 10.0)

        val output = reranker.rerank(
            listOf(hidden, sameUp, mutedName, ok),
            FeedContext(
                limit = 4,
                blockedIds = setOf("hid"),
                blockedBvids = setOf("BV1hid"),
                blockedAuthorIds = setOf("99"),
                mutedAuthors = setOf("up-x"),
            ),
        )

        assertEquals(listOf("ok"), output.map { it.id })
    }

    private fun track(id: String, categoryId: Int, artist: String): Track =
        Track(id = id, title = "title-$id", artist = artist, categoryId = categoryId)

    private fun candidate(
        track: Track,
        score: Double,
        source: String = "homepage",
        realtime: Boolean = false,
        explore: Boolean = false,
        dailyBaseline: Boolean = false,
    ): ScoredTrack = ScoredTrack(
        track = track,
        score = score,
        source = source,
        realtime = realtime,
        explore = explore,
        dailyBaseline = dailyBaseline,
    )
}
