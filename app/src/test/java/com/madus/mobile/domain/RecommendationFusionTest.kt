package com.madus.mobile.domain

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 汽水音乐融合项的行为契约。
 *
 * 覆盖三件事：
 * 1. 三档重复收听窗口（10 分钟 / 1 小时 / 1 天）区分「单曲循环式真爱」与「当天刷太多」；
 * 2. 歌手级负反馈（`/luna/dislike` 带 author_ids）只压作者，不封曲风；
 * 3. 熟悉 / 均衡 / 新鲜三档只改探索配额与不匹配惩罚，默认档不改动旧行为；
 * 4. 待播队列参与重排（等价于汽水请求里 `played_mills = -1` 的那一段）。
 */
class RecommendationFusionTest {
    private val now = 1_800_000_000_000L
    private val engine = RecommendationEngine()
    private val ranker = RecommendationReRanker()
    private val context = FeedContext(nowMs = now, musicOnly = true)

    private fun song(id: String, tag: String = "民谣", artist: String = "up-$id") =
        Track(id, "单曲《$id》", artist, durationMs = 200_000, categoryId = 28, tags = listOf(tag))

    private fun sample(id: String, age: Long, heard: Long = 180_000, duration: Long = 180_000,
        session: String = "$id-$age", tag: String = "folk") =
        RecommendationEvent(id, id, RecommendationEventType.LISTEN_SAMPLE, now - age, "recommend",
            setOf(tag), null, id, session, heard, duration, heard)

    private fun feedback(id: String, type: RecommendationEventType, artist: String,
        age: Long = 0, tag: String = "folk") =
        RecommendationEvent(id, id, type, now - age, "recommend", setOf(tag), artist, id)

    // ---- 三档重复窗口 ----

    @Test fun repeatedPlayWithinTenMinutesIsLoveNotFatigue() {
        val events = listOf(
            sample("a", age = 4 * 60_000L),
            sample("a", age = 2 * 60_000L),
            sample("a", age = 0L),
        )
        val profile = RecommendationFusion.repeatProfiles(events, now).getValue("a")
        assertEquals(3, profile.inTenMinutes)
        assertEquals(3, profile.inDay)
        assertTrue(RecommendationFusion.loveBoost(profile) > 0.5)
        assertEquals(0.0, RecommendationFusion.fatiguePenalty(profile), 1e-9)
    }

    @Test fun spreadingTheSamePlaysOverADayTurnsIntoFatigue() {
        val spread = (0..5).map { hour -> sample("a", age = hour * 60 * 60_000L, session = "s$hour") }
        val profile = RecommendationFusion.repeatProfiles(spread, now).getValue("a")
        assertEquals(6, profile.inDay)
        assertTrue(profile.inTenMinutes <= 1)
        assertTrue(
            "隔一阵子反复刷的歌要被打疲劳分，而不是当成真爱",
            RecommendationFusion.fatiguePenalty(profile) + RecommendationFusion.loveBoost(profile) < 0.0,
        )
    }

    @Test fun listeningPastTheDurationCountsAsLoops() {
        // 单曲循环时 listenedMs 会累加超过时长：3.2 倍时长 ≈ 播了 3 遍。
        val looped = sample("a", age = 60_000L, heard = 576_000, duration = 180_000, session = "loop")
        assertEquals(3, RecommendationFusion.repeatsOf(looped))
        val profile = RecommendationFusion.repeatProfiles(listOf(looped), now).getValue("a")
        assertEquals(3, profile.inTenMinutes)
    }

    @Test fun twoSongsPlayedOnceEachAreNotRepeats() {
        val events = listOf(sample("a", age = 0L), sample("b", age = 0L))
        val profiles = RecommendationFusion.repeatProfiles(events, now)
        assertEquals(1, profiles.getValue("a").inTenMinutes)
        assertEquals(1, profiles.getValue("b").inTenMinutes)
        assertEquals(0.0, RecommendationFusion.loveBoost(profiles.getValue("a")), 1e-9)
    }

    // ---- 歌手级负反馈 ----

    @Test fun dislikingOneSongPenalizesTheArtistWithoutBanningTheGenre() {
        val state = engine.buildInterestState(
            listOf(feedback("bad", RecommendationEventType.NOT_INTERESTED, artist = "artist-x")),
            now,
            setOf("folk"),
        )
        assertTrue(state.artistNegatives.getValue("artist-x") > 0.0)
        val sameArtist = engine.scoreCandidate(song("new1", artist = "artist-x"), null, state, "search", context)
        val otherArtist = engine.scoreCandidate(song("new2", artist = "artist-y"), null, state, "search", context)
        assertTrue(otherArtist.score > sameArtist.score)
        // 曲风没有被封：同歌手的其它歌仍然在兴趣池里，只是排后面。
        assertTrue(sameArtist.inInterestPool)
        assertTrue(sameArtist.topicKeys.isNotEmpty())
    }

    @Test fun artistPenaltyIsBoundedAndExpires() {
        val many = (1..12).map { feedback("bad$it", RecommendationEventType.NOT_INTERESTED, artist = "artist-x") }
        val state = engine.buildInterestState(many, now, setOf("folk"))
        assertTrue(state.artistNegatives.getValue("artist-x") <= 0.5 + 1e-9)
        val stale = listOf(feedback("old", RecommendationEventType.NOT_INTERESTED, artist = "artist-x",
            age = 200 * 24 * 60 * 60_000L))
        assertTrue(engine.buildInterestState(stale, now, setOf("folk")).artistNegatives.isEmpty())
    }

    @Test fun goingBackIsStrongerEvidenceThanASoftSkip() {
        val back = engine.buildInterestState(
            listOf(feedback("a", RecommendationEventType.USER_BACK, artist = "up-a")), now, setOf("folk"),
        )
        val skip = engine.buildInterestState(
            listOf(feedback("a", RecommendationEventType.SKIP, artist = "up-a")), now, setOf("folk"),
        )
        assertTrue(back.negativeTopics.getValue("folk") > skip.negativeTopics.getValue("folk"))
        // 回退只冷却这首歌，不封曲风。
        assertTrue("a" in back.cooledTrackIds)
    }

    // ---- 听歌模式 ----

    @Test fun balancedModeIsTheUnchangedDefault() {
        assertEquals(0.25, TasteMode.BALANCED.exploreRatio, 1e-9)
        assertEquals(1.0, TasteMode.BALANCED.mismatchScale, 1e-9)
        assertEquals(0.0, TasteMode.BALANCED.sourceQualityBias, 1e-9)
        assertFalse(TasteMode.BALANCED.strictAnchor)
        assertEquals(TasteMode.BALANCED, TasteMode.of(null))
        assertEquals(TasteMode.BALANCED, TasteMode.of("nonsense"))
        assertEquals(TasteMode.FRESH, TasteMode.of("FRESH"))
    }

    @Test fun familiarModeSuppressesAdjacentGenresWhileFreshModeKeepsThem() {
        val state = engine.buildInterestState(emptyList(), now, setOf("folk"))
        val pop = song("pop1", tag = "流行")
        val balanced = engine.scoreCandidate(pop, null, state, "search", context)
        val familiar = engine.scoreCandidate(pop, null, state, "search",
            context.copy(tasteMode = TasteMode.FAMILIAR, maxExploreRatio = TasteMode.FAMILIAR.exploreRatio))
        val fresh = engine.scoreCandidate(pop, null, state, "search",
            context.copy(tasteMode = TasteMode.FRESH, maxExploreRatio = TasteMode.FRESH.exploreRatio))
        assertTrue("熟悉模式要压住相邻曲风", familiar.score < balanced.score)
        assertTrue("新鲜模式要给相邻曲风更多机会", fresh.score > balanced.score)
        assertTrue(familiar.adjacentInterest)
    }

    @Test fun freshModeActuallyAllowsMoreExplorationSlots() {
        val core = (1..30).map { ScoredTrack(song("core$it"), 12.0, "search", topicKeys = setOf("folk")) }
        val discovery = (1..30).map {
            ScoredTrack(song("new$it", tag = "流行"), 12.0, "search", topicKeys = setOf("pop"), explore = true,
                inInterestPool = false, adjacentInterest = true)
        }
        val candidates = core + discovery
        val balanced = ranker.rerank(candidates,
            FeedContext(musicOnly = true, limit = 20, maxExploreRatio = TasteMode.BALANCED.exploreRatio))
        val fresh = ranker.rerank(candidates,
            FeedContext(musicOnly = true, limit = 20, maxExploreRatio = TasteMode.FRESH.exploreRatio,
                tasteMode = TasteMode.FRESH))
        val balancedExplore = balanced.count { it.id.startsWith("new") }
        val freshExplore = fresh.count { it.id.startsWith("new") }
        assertTrue("新鲜模式的探索位必须多于均衡档：$freshExplore vs $balancedExplore", freshExplore > balancedExplore)
    }

    // ---- 待播队列参与重排（played_mills = -1 的等价物） ----

    @Test fun upcomingQueueKeepsGenreSpacingAcrossARefillBoundary() {
        val instrumental = (1..6).map {
            ScoredTrack(song("ins$it", tag = "纯音乐"), 12.0, "search", topicKeys = setOf("instrumental"))
        }
        val folk = ScoredTrack(song("folk1", tag = "民谣"), 8.0, "search", topicKeys = setOf("folk"))
        val candidates = instrumental + folk
        val head = (1..5).map { song("queued$it", tag = "纯音乐") }

        val withoutBoundary = ranker.rerank(candidates, FeedContext(musicOnly = true, limit = 1))
        val withBoundary = ranker.rerank(candidates,
            FeedContext(musicOnly = true, limit = 1, upcomingQueue = head))

        assertEquals("ins1", withoutBoundary.single().id)
        assertEquals("排队尾已经是纯音乐，补队列要先换味", "folk1", withBoundary.single().id)
    }

    @Test fun upcomingQueueAuthorIsAvoidedWhenTheScoreIsClose() {
        val upcoming = listOf(song("queued1", artist = "same-up"))
        val mine = ScoredTrack(song("a1", artist = "same-up"), 10.0, "search", topicKeys = setOf("folk"))
        val other = ScoredTrack(song("b1", artist = "other-up"), 9.8, "search", topicKeys = setOf("folk"))
        val picked = ranker.rerank(listOf(mine, other),
            FeedContext(musicOnly = true, limit = 1, upcomingQueue = upcoming))
        assertEquals("b1", picked.single().id)
    }

    // ---- 每日 5:00 刷新锚点 ----

    @Test fun dailyWindowStartsAtFiveAndCrossesMidnight() {
        val zone = TimeZone.getTimeZone("Asia/Shanghai")
        fun at(hour: Int, minute: Int): Long = Calendar.getInstance(zone).apply {
            set(2026, Calendar.OCTOBER, 20, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val afterFive = DailyRefresh.windowStartMs(at(12, 0), zone)
        assertEquals(5, Calendar.getInstance(zone).apply { timeInMillis = afterFive }.get(Calendar.HOUR_OF_DAY))
        assertEquals(20, Calendar.getInstance(zone).apply { timeInMillis = afterFive }.get(Calendar.DAY_OF_MONTH))

        // 凌晨 4:30 还属于前一天 5:00 开启的窗口（跨夜听歌算前一天）。
        val beforeFive = DailyRefresh.windowStartMs(at(4, 30), zone)
        assertEquals(19, Calendar.getInstance(zone).apply { timeInMillis = beforeFive }.get(Calendar.DAY_OF_MONTH))
        assertEquals(5, Calendar.getInstance(zone).apply { timeInMillis = beforeFive }.get(Calendar.HOUR_OF_DAY))
    }

    @Test fun dailyRefreshHappensOncePerFiveAmWindow() {
        val zone = TimeZone.getTimeZone("Asia/Shanghai")
        fun at(hour: Int, minute: Int): Long = Calendar.getInstance(zone).apply {
            set(2026, Calendar.OCTOBER, 20, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val nowMs = at(12, 0)
        val windowStart = DailyRefresh.windowStartMs(nowMs, zone)

        assertTrue(DailyRefresh.shouldRefresh(0L, nowMs))
        assertTrue("上一窗口刷新过，今天还没刷新", DailyRefresh.shouldRefresh(windowStart - 60_000L, nowMs))
        assertFalse("今天 5 点后刷过了", DailyRefresh.shouldRefresh(windowStart + 60_000L, nowMs))

        val jitter = DailyRefresh.jitterMs(nowMs)
        assertTrue(jitter in 0 until DailyRefresh.MAX_JITTER_MS)
        assertEquals(jitter, DailyRefresh.jitterMs(nowMs))
        assertEquals(windowStart + jitter, DailyRefresh.refreshAtMs(nowMs, nowMs))
    }
}
