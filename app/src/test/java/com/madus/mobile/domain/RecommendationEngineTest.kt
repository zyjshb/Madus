package com.madus.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationEngineTest {

    private val engine = RecommendationEngine()

    @Test
    fun twoLikesWithinTenMinutesUpgradeRealtimeTopic() {
        val nowMs = 1_800_000_000_000L
        val events = listOf(
            event("like-a-1", RecommendationEventType.LIKE, nowMs, setOf("music"), "up-a"),
            event("like-a-2", RecommendationEventType.LIKE, nowMs, setOf("music"), "up-b"),
        )

        val state = engine.buildInterestState(events, nowMs)
        val single = engine.buildInterestState(events.take(1), nowMs)

        assertEquals(1.0, single.realtimeTopics.getValue("music"), 1e-9)
        assertEquals(3.0, state.realtimeTopics.getValue("music"), 1e-9)
        assertEquals(
            1.5,
            state.realtimeTopics.getValue("music") / (2.0 * single.realtimeTopics.getValue("music")),
            1e-9,
        )
    }

    @Test
    fun twoFastSkipsCreateMutedTopicCooldown() {
        val nowMs = 1_800_000_000_000L
        val events = listOf(
            event("skip-1", RecommendationEventType.SKIP_FAST, nowMs - 60_000L, setOf("music"), null),
            event("skip-2", RecommendationEventType.SKIP_FAST, nowMs, setOf("music"), null),
        )

        val state = engine.buildInterestState(events, nowMs)

        assertEquals(
            nowMs + RecommendationTuning.TOPIC_COOLDOWN_MS,
            state.mutedTopics.getValue("music"),
        )
        assertTrue(state.mutedTopics.getValue("music") > nowMs)

        val singleSkip = engine.buildInterestState(events.take(1), nowMs)
        assertTrue(singleSkip.mutedTopics.isEmpty())
    }

    @Test
    fun notInterestedMutesAuthorButNotBroadTopic() {
        val nowMs = 1_800_000_000_000L
        val events = listOf(
            event(
                "nope-1",
                RecommendationEventType.NOT_INTERESTED,
                nowMs,
                setOf("music", "upid:123"),
                "up-x",
            ),
        )
        val state = engine.buildInterestState(events, nowMs)
        val until = nowMs + RecommendationTuning.NOT_INTERESTED_COOLDOWN_MS
        assertTrue("整区 music 不该被一首不喜欢封掉", "music" !in state.mutedTopics)
        assertEquals(until, state.mutedTopics.getValue("author:up-x"))
        assertEquals(until, state.mutedTopics.getValue("upid:123"))
    }

    @Test
    fun notInterestedMutesFineTopicAndTitleToken() {
        val nowMs = 1_800_000_000_000L
        val events = listOf(
            event(
                "nope-2",
                RecommendationEventType.NOT_INTERESTED,
                nowMs,
                setOf("cover", "kw:告白气球"),
                "up-y",
            ),
        )
        val state = engine.buildInterestState(events, nowMs)
        val until = nowMs + RecommendationTuning.NOT_INTERESTED_COOLDOWN_MS
        assertEquals(until, state.mutedTopics.getValue("cover"))
        assertEquals(until, state.mutedTopics.getValue("kw:告白气球"))
    }

    @Test
    fun removingNotInterestedEventClearsMute() {
        val nowMs = 1_800_000_000_000L
        val events = listOf(
            event("nope-1", RecommendationEventType.NOT_INTERESTED, nowMs, setOf("music"), "up-x"),
        )
        val muted = engine.buildInterestState(events, nowMs)
        val cleared = engine.buildInterestState(emptyList(), nowMs)
        assertTrue(muted.mutedTopics.isNotEmpty())
        assertTrue(cleared.mutedTopics.isEmpty())
    }

    @Test
    fun realtimeAffinityHasLargestPositiveWeight() {
        val nowMs = 1_800_000_000_000L
        val track = musicTrack()
        val context = FeedContext(nowMs = nowMs)

        val realtimeScore = engine.scoreCandidate(
            track,
            null,
            InterestState(realtimeTopics = mapOf("music" to 1.0)),
            "related",
            context,
        ).score
        val hourlyScore = engine.scoreCandidate(
            track,
            null,
            InterestState(hourlyTopics = mapOf("music" to 1.0)),
            "related",
            context,
        ).score
        val longTermScore = engine.scoreCandidate(
            track,
            null,
            InterestState(longTermTopics = mapOf("music" to 1.0)),
            "related",
            context,
        ).score

        assertTrue("实时亲和权重应大于小时亲和", realtimeScore > hourlyScore)
        assertTrue("小时亲和权重应大于长期亲和", hourlyScore > longTermScore)
    }

    @Test
    fun mutedTopicAndNegativeFeedbackPenalizeScore() {
        val nowMs = 1_800_000_000_000L
        val track = musicTrack()
        val baseContext = FeedContext(nowMs = nowMs)

        val normal = engine.scoreCandidate(track, null, InterestState(), "homepage", baseContext)
        val cooling = engine.scoreCandidate(
            track,
            null,
            InterestState(mutedTopics = mapOf("music" to nowMs + 60_000L)),
            "homepage",
            baseContext,
        )
        val contextMuted = engine.scoreCandidate(
            track,
            null,
            InterestState(),
            "homepage",
            FeedContext(nowMs = nowMs, mutedTopics = setOf("music")),
        )

        assertTrue("冷却主题应明显扣分", cooling.score < normal.score)
        assertTrue("会话级负反馈主题应明显扣分", contextMuted.score < normal.score)
        assertEquals(RecommendationTuning.W_NEGATIVE, cooling.score - normal.score, 1e-9)
    }

    private fun musicTrack(): Track =
        Track(id = "music-track", title = "Music Title", artist = "singer-a", categoryId = 3)

    private fun event(
        trackId: String,
        type: RecommendationEventType,
        occurredAtMs: Long,
        topicKeys: Set<String>,
        authorKey: String?,
    ): RecommendationEvent = RecommendationEvent(
        trackId = trackId,
        bvid = trackId,
        type = type,
        occurredAtMs = occurredAtMs,
        sourceId = "recommend",
        topicKeys = topicKeys,
        authorKey = authorKey,
    )
}
