package com.madus.mobile.domain

import org.junit.Assert.*
import org.junit.Test

class RecommendationEngineTest {
    private val now = 1_800_000_000_000L
    private val engine = RecommendationEngine()
    private val context = FeedContext(nowMs = now, musicOnly = true)
    private fun event(id: String, type: RecommendationEventType = RecommendationEventType.LIKE,
        topic: String = "folk", age: Long = 0, songKey: String = id) = RecommendationEvent(
        id, id, type, now - age, "recommend", setOf("music", topic), "same-uploader", songKey)
    private fun song(id: String, vararg tags: String) = Track(id, "单曲《$id》", "same-uploader",
        durationMs = 180_000, categoryId = 28, tags = tags.toList())

    @Test fun playbackStartNeverTeachesAnUnchosenGenre() {
        val likes = listOf(event("liked"))
        val impressions = (1..500).map { event("exposure$it", RecommendationEventType.PLAY_START, "rap") }
        assertEquals(engine.buildInterestState(likes, now).interestTopics,
            engine.buildInterestState(likes + impressions, now).interestTopics)
        assertTrue(engine.buildInterestState(impressions, now).interestTopics.isEmpty())
    }

    @Test fun sameSongRepeatedAndCollectedDoesNotMultiplyItsInfluence() {
        val first = event("song-a", RecommendationEventType.COLLECT_BILIBILI)
        val baseline = listOf(first, event("song-b", topic = "rock"))
        val repeats = (1..100).map { first.copy(trackId = "copy$it", bvid = "bv$it") }
        val learned = engine.buildInterestState(baseline + repeats, now)
        assertEquals(engine.buildInterestState(baseline, now).interestTopics, learned.interestTopics)
        assertEquals(2, learned.evidenceSongCount)
        assertEquals(1.0, learned.interestTopics.values.sum(), 1e-9)
    }

    @Test fun listeningMilestonesUseStrongestEvidenceOnce() {
        val milestones = listOf(RecommendationEventType.WATCH_30, RecommendationEventType.WATCH_50,
            RecommendationEventType.WATCH_90).map { event("song-a", it) }
        val other = event("song-b", topic = "rock")
        val all = engine.buildInterestState(milestones + other, now)
        val finalOnly = engine.buildInterestState(listOf(milestones.last(), other), now)
        assertEquals(finalOnly.interestTopics, all.interestTopics)
        assertTrue("听近整首应比单次点赞提供更强的行为证据", all.interestTopics.getValue("folk") > all.interestTopics.getValue("rock"))
    }

    @Test fun engagedListeningUpdatesNowButShortListeningDoesNotBecomeLongTermTaste() {
        val thirtySeconds = engine.buildInterestState(listOf(event("a", RecommendationEventType.WATCH_30)), now)
        val like = engine.buildInterestState(listOf(event("a")), now)
        assertTrue(thirtySeconds.realtimeTopics.getValue("folk") > 0)
        assertTrue(thirtySeconds.longTermTopics.isEmpty())
        val candidate = song("new", "民谣")
        assertTrue(engine.scoreCandidate(candidate, null, like, "search", context).score >
            engine.scoreCandidate(candidate, null, thirtySeconds, "search", context).score)
    }

    @Test fun selectedTasteRemainsAnAnchorEvenWithLargeHistoricalLibrary() {
        val history = (1..100).map { event("old$it", topic = "rap", age = 5 * RecommendationTuning.HOURLY_TTL_MS) }
        val state = engine.buildInterestState(history, now, setOf("folk", "not-a-topic"))
        assertEquals(setOf("folk"), state.preferredTopics)
        assertTrue(state.interestTopics.getValue("folk") > state.interestTopics.getValue("rap"))
        assertEquals(1.0, state.interestTopics.values.sum(), 1e-9)
    }

    @Test fun rejectingOneSongCoolsItsVersionsWithoutBanningGenreOrUploader() {
        val dislike = event("a", RecommendationEventType.NOT_INTERESTED, songKey = "歌甲")
        val state = engine.buildInterestState(listOf(dislike), now, setOf("folk"))
        assertTrue(state.mutedTopics.isEmpty())
        assertTrue("a" in state.cooledTrackIds)
        assertTrue("歌甲" in state.cooledSongKeys)
        val another = engine.scoreCandidate(song("歌乙", "民谣"), null, state, "search", context)
        val variant = engine.scoreCandidate(song("歌甲", "民谣"), null, state, "search", context)
        assertTrue(another.inInterestPool)
        assertFalse(another.cooledDown)
        assertTrue(variant.cooledDown)
        assertEquals(listOf("歌乙"), RecommendationReRanker().rerank(listOf(variant, another), context).map { it.id })
    }

    @Test fun repeatedSkipOnSameSongDoesNotBanGenreOrAccumulatePenalty() {
        val skip = event("a", RecommendationEventType.SKIP_FAST)
        val once = engine.buildInterestState(listOf(skip), now, setOf("folk"))
        val repeated = engine.buildInterestState(List(100) { skip }, now, setOf("folk"))
        assertEquals(once.negativeTopics, repeated.negativeTopics)
        assertTrue(repeated.mutedTopics.isEmpty())
    }

    @Test fun skipIsShortTermWhileExplicitRejectionLastsLonger() {
        val skipped = event("a", RecommendationEventType.SKIP_FAST, age = 2 * RecommendationTuning.HOURLY_TTL_MS + 1)
        val rejected = skipped.copy(type = RecommendationEventType.NOT_INTERESTED)
        val skipState = engine.buildInterestState(listOf(skipped), now)
        val rejectedState = engine.buildInterestState(listOf(rejected), now)
        assertTrue(skipState.negativeTopics.isEmpty())
        assertTrue(skipState.cooledTrackIds.isEmpty())
        assertTrue(rejectedState.negativeTopics.getValue("folk") > 0)
        assertTrue("a" in rejectedState.cooledTrackIds)
    }

    @Test fun laterLikeRestoresRejectedSongAndUndoRemovesItsPenalty() {
        val rejection = event("a", RecommendationEventType.NOT_INTERESTED, age = 1000)
        val likedLater = event("a")
        val state = engine.buildInterestState(listOf(rejection, likedLater), now)
        assertTrue(state.cooledTrackIds.isEmpty())
        assertTrue(state.negativeTopics.isEmpty())
        assertTrue(state.interestTopics.getValue("folk") > 0)
        assertTrue(engine.buildInterestState(emptyList(), now).cooledTrackIds.isEmpty())
    }

    @Test fun resamplingAnOldCollectionDoesNotCancelARejectionOrCreateRealtimeFeedback() {
        val oldRejection = event("a", RecommendationEventType.NOT_INTERESTED,
            age = 2 * RecommendationTuning.HOURLY_TTL_MS)
        val collection = event("a").copy(sourceId = "library-seed")
        val rejected = engine.buildInterestState(listOf(oldRejection, collection), now)
        assertTrue("a" in rejected.cooledTrackIds)
        assertTrue(rejected.interestTopics.isEmpty())
        val libraryOnly = engine.buildInterestState(listOf(collection), now)
        assertTrue(libraryOnly.realtimeTopics.isEmpty())
        assertTrue(libraryOnly.hourlyTopics.isEmpty())
        assertTrue(libraryOnly.longTermTopics.getValue("folk") > 0)
    }

    @Test fun laterWeakSkipDoesNotShortenAnExplicitRejection() {
        val rejected = event("a", RecommendationEventType.NOT_INTERESTED,
            age = 2 * RecommendationTuning.HOURLY_TTL_MS)
        val laterSkip = event("a", RecommendationEventType.SKIP, age = RecommendationTuning.HOURLY_TTL_MS)
        val state = engine.buildInterestState(listOf(rejected, laterSkip), now)
        assertTrue("a" in state.cooledTrackIds)
        assertTrue(state.negativeTopics.getValue("folk") > 0)
    }

    @Test fun repeatedSameGenreDoesNotCreateGenreFatigue() {
        val state = engine.buildInterestState(emptyList(), now, setOf("folk"))
        val candidate = song("new", "民谣").copy(artist = "new-uploader")
        val base = engine.scoreCandidate(candidate, null, state, "search", context)
        val recent = (1..10).map { song("old$it", "民谣") }
        val continued = engine.scoreCandidate(candidate, null, state, "search", context.copy(recentQueue = recent))
        assertEquals(base.score, continued.score, 1e-9)
    }

    @Test fun missingGenreCanInheritPositiveSeedButConflictingMetadataCannot() {
        val state = engine.buildInterestState(emptyList(), now, setOf("folk", "mandarin"))
        val unknown = song("unknown")
        val noSeed = engine.scoreCandidate(unknown, null, state, "related", context)
        val withSeed = engine.scoreCandidate(unknown, null, state, "related-like", context, setOf("folk"))
        val conflict = engine.scoreCandidate(song("rap", "说唱", "华语"), null, state,
            "related-like", context, setOf("folk"))
        val languageConflict = engine.scoreCandidate(song("english", "民谣", "欧美"), null, state,
            "related-like", context, setOf("folk", "mandarin"))
        assertFalse(noSeed.inInterestPool)
        assertTrue(withSeed.inInterestPool)
        assertFalse(conflict.inInterestPool)
        assertFalse(conflict.adjacentInterest)
        assertFalse(languageConflict.inInterestPool)
    }

    @Test fun discoveryClassificationDependsOnTasteNotSourceName() {
        val state = engine.buildInterestState(emptyList(), now, setOf("folk"))
        val familiarGenre = engine.scoreCandidate(song("folk", "民谣"), null, state, "popular", context)
        val adjacent = engine.scoreCandidate(song("pop", "流行"), null, state, "search", context)
        val unrelated = engine.scoreCandidate(song("rap", "说唱"), null, state, "related-like", context)
        assertTrue(familiarGenre.inInterestPool)
        assertFalse(familiarGenre.explore)
        assertTrue(adjacent.adjacentInterest)
        assertTrue(adjacent.explore)
        assertFalse(unrelated.inInterestPool)
        assertFalse(unrelated.adjacentInterest)
    }
}
