package com.madus.mobile.domain

import org.junit.Assert.*
import org.junit.Test

/** User-reported titles are fixtures with deliberately missing genre/artist metadata, not genre guesses. */
class SongReferenceRecommendationTest {
    private val now = 1_800_000_000_000L
    private val engine = RecommendationEngine()
    private val ranker = RecommendationReRanker()
    private val context = FeedContext(nowMs = now, musicOnly = true, limit = 12)
    private val titles = listOf("Duvet", "sacred play secret place", "stay with me", "cry for me", "红色高跟鞋")
    private fun song(title: String, id: String = title) = Track(id, title, "上传者", durationMs = 200_000,
        categoryId = 193, bvid = id, source = MusicSourceType.BILIBILI)
    private fun vote(track: Track, direction: Int = 1) = MusicStyleFeedback(MusicStyleFeedback.key(track),
        emptySet(), direction, now, track)
    private fun score(track: Track, state: InterestState, seed: Track? = null) =
        engine.scoreCandidate(track, null, state, "related-like", context,
            seedSongKeys = seed?.let { setOf(MusicStyleFeedback.key(it)) }.orEmpty())

    @Test fun allReportedSongsWorkAsReferencesWithoutAnyRecognizedGenre() {
        for (title in titles) {
            val seed = song(title)
            assertTrue(MusicStyleFeedback.topics(ContentProfileParser.profileFromTrack(seed).topicKeys).isEmpty())
            val state = engine.buildInterestState(emptyList(), now, styleFeedback = listOf(vote(seed)))
            assertTrue(score(seed, state).familiar)
            assertTrue(score(song("new recording"), state, seed).inInterestPool)
            assertFalse(score(song("unrelated recording"), state).inInterestPool)
            assertEquals(0, state.evidenceSongCount) // Manual feedback never fabricates listening.
            assertTrue(state.preferredTopics.isEmpty())
            assertTrue(state.interestTopics.isEmpty())
            assertTrue(MusicDiscovery.searchQueries(state, listOf(seed), 0, now).isEmpty())
        }
    }

    @Test fun sameTitleDifferentRecordingDoesNotInheritTheManualVote() {
        val chosen = song("stay with me", "chosen-version")
        val other = song("stay with me", "other-song-with-same-title")
        val state = engine.buildInterestState(emptyList(), now, styleFeedback = listOf(vote(chosen)))
        assertNotEquals(MusicStyleFeedback.key(chosen), MusicStyleFeedback.key(other))
        assertTrue(score(chosen, state).familiar)
        assertFalse(score(other, state).familiar)
    }

    @Test fun exactSearchListeningWorksEvenWithoutGenreLabels() {
        val selected = song("sacred play secret place")
        val event = RecommendationEvent(selected.id, selected.bvid, RecommendationEventType.LISTEN_SAMPLE,
            now, "search", setOf("music"), null, MusicDiscovery.songKey(selected), "session", 170_000, 200_000, 80_000, true)
        val state = engine.buildInterestState(listOf(event), now)
        assertTrue(score(selected, state).familiar)
        assertTrue(score(song("new song"), state, selected).inInterestPool)
        val merelyClicked = event.copy(type = RecommendationEventType.PLAY_START, listenedMs = 0)
        assertFalse(score(selected, engine.buildInterestState(listOf(merelyClicked), now)).familiar)
    }

    @Test fun relatedQueuesCannotBeCapturedByOneReferenceAcrossSingleTrackRefills() {
        val seeds = titles.take(3).map { song(it) }
        val state = engine.buildInterestState(emptyList(), now, styleFeedback = seeds.map { vote(it) })
        val candidates = seeds.flatMapIndexed { index, seed -> (1..20).map {
            score(song("recording $index-$it"), state, seed)
        } }
        val past = mutableListOf<ScoredTrack>()
        repeat(18) {
            val ctx = context.copy(limit = 1, sessionSeenIds = past.map { it.track.id }.toSet(),
                recentSeedSongKeys = past.takeLast(8).map { it.seedSongKeys })
            val picked = ranker.rerankWithReasons(candidates, ctx).second
            assertEquals(1, picked.size)
            past += picked.single()
        }
        assertTrue(past.zipWithNext().all { (a, b) -> a.seedSongKeys.intersect(b.seedSongKeys).isEmpty() })
        assertTrue(past.windowed(6).all { window -> seeds.all { seed ->
            window.count { MusicStyleFeedback.key(seed) in it.seedSongKeys } <= 2
        } })
    }

    @Test fun withdrawnAndNegativeReferencesDoNotKeepAStalePositiveRelation() {
        val seed = song("cry for me")
        val remaining = song("红色高跟鞋")
        val candidate = song("new recording")
        val before = engine.buildInterestState(emptyList(), now, styleFeedback = listOf(vote(seed), vote(remaining)))
        val after = engine.buildInterestState(emptyList(), now, styleFeedback = listOf(vote(seed, -1), vote(remaining)))
        assertTrue(score(candidate, before, seed).inInterestPool)
        assertFalse(score(candidate, after, seed).inInterestPool)
        assertTrue(score(candidate, after, remaining).inInterestPool)
        assertTrue(score(candidate, before, seed).score > score(candidate, after, seed).score)
    }

    @Test fun twoAvailableReferencesAlternateWithoutArtificiallyEndingPlayback() {
        val seeds = titles.take(2).map { song(it) }
        val state = engine.buildInterestState(emptyList(), now, styleFeedback = seeds.map { vote(it) })
        val candidates = seeds.flatMapIndexed { i, seed -> (1..20).map {
            score(song("recording $i-$it"), state, seed)
        } }
        val past = mutableListOf<ScoredTrack>()
        repeat(18) {
            val ctx = context.copy(limit = 1, sessionSeenIds = past.map { it.track.id }.toSet(),
                recentSeedSongKeys = past.takeLast(8).map { it.seedSongKeys })
            past += ranker.rerankWithReasons(candidates, ctx).second.single()
        }
        assertTrue(past.zipWithNext().all { (a, b) -> a.seedSongKeys.intersect(b.seedSongKeys).isEmpty() })
    }

    @Test fun metadataEnrichmentDuringSearchPlaybackKeepsEarlierListening() {
        val tracker = MusicListeningTracker("search")
        val unknown = song("Duvet").copy(categoryId = 0)
        tracker.sample(unknown, 1, 0, 200_000, true, true, "search", true, now)
        tracker.sample(unknown, 1, 2_000, 200_000, true, true, "search", true, now + 2_000)
        val events = tracker.sample(unknown.copy(categoryId = 193), 1, 4_000, 200_000,
            true, true, "search", true, now + 4_000)
        assertEquals(4_000L, events.single().listenedMs)
        assertTrue(events.single().fromSearch)
    }

    @Test fun familiarOpenerIsRestedAndNeverOverridesDislikeOrBlock() {
        val seed = song("Duvet")
        fun played(age: Long, session: String = "old") = RecommendationEvent(seed.id, seed.bvid,
            RecommendationEventType.LISTEN_SAMPLE, now - age, "recommend", emptySet(), null,
            MusicDiscovery.songKey(seed), session, 180_000, 200_000)
        assertFalse(MusicReferencePolicy.canOpen(seed, listOf(played(60_000)), now))
        assertTrue(MusicReferencePolicy.canOpen(seed, listOf(played(2 * 86_400_000L)), now))
        assertFalse(MusicReferencePolicy.canOpen(seed, (2..4).map { played(it * 86_400_000L, "$it") }, now))
        val rejected = played(1_000).copy(type = RecommendationEventType.NOT_INTERESTED)
        val state = engine.buildInterestState(listOf(rejected), now, styleFeedback = listOf(vote(seed)))
        assertTrue(ranker.rerank(listOf(score(seed, state)), context).isEmpty())
        val liked = engine.buildInterestState(emptyList(), now, styleFeedback = listOf(vote(seed)))
        assertTrue(ranker.rerank(listOf(score(seed, liked)), context.copy(blockedIds = setOf(seed.id))).isEmpty())
    }

    @Test fun openingCannotUseAnExplorationSlotLeftOverFromLastSession() {
        val familiar = ScoredTrack(song("good"), 5.0, "liked", familiar = true)
        val explore = ScoredTrack(song("experiment"), 10.0, "search", explore = true,
            inInterestPool = false, adjacentInterest = true)
        val ctx = context.copy(isOpening = true, limit = 1, recentExploration = List(8) { false })
        assertEquals("good", ranker.rerank(listOf(explore, familiar), ctx).single().id)
    }

    @Test fun titleDoesNotInventGenreAndSpecificMetadataCanBeUsed() {
        assertTrue(MusicStyleFeedback.topics(ContentProfileParser.profileFromTrack(song("Duvet")).topicKeys).isEmpty())
        val enriched = song("recording").copy(tags = listOf("dream pop", "shoegaze"))
        assertTrue(ContentProfileParser.profileFromTrack(enriched).topicKeys.containsAll(setOf("dream-pop", "shoegaze")))
        assertEquals(0.0, MusicReferencePolicy.similarity(song("a").copy(tags = listOf("欧美", "高音质")),
            song("b").copy(tags = listOf("欧美", "高音质"))), 0.0)
    }

    @Test fun likingAnEnglishSongDoesNotAdmitEveryEnglishSong() {
        val selected = song("Duvet").copy(tags = listOf("欧美"))
        val event = RecommendationEvent(selected.id, selected.bvid, RecommendationEventType.LIKE, now,
            "search", setOf("music", "en-song"), null, MusicDiscovery.songKey(selected))
        val state = engine.buildInterestState(listOf(event), now)
        val unrelated = song("unrelated English song").copy(tags = listOf("欧美"))
        assertFalse(score(unrelated, state).inInterestPool)
        assertTrue(score(unrelated, state, selected).inInterestPool)
    }

    @Test fun poolPreservesMultipleReferenceOriginsAcrossSearchRefreshAndRestart() {
        val candidate = song("new song")
        val pool = MusicInterestPool()
        pool.offer(MusicPoolCandidate(candidate, "related-like", addedAtMs = now, seedSongKeys = setOf("a")))
        pool.offer(MusicPoolCandidate(candidate, "related-like", addedAtMs = now + 1, seedSongKeys = setOf("b")))
        pool.offer(MusicPoolCandidate(candidate, "search", addedAtMs = now + 2))
        val restarted = MusicInterestPool().apply { restore(pool.snapshot(now + 3), now + 3) }
        assertEquals(setOf("a", "b"), restarted.snapshot(now + 3).candidates.single().seedSongKeys)
    }

    @Test fun detailedSharedTagsCanMatchWhileBroadLanguageCannot() {
        val seed = song("Duvet").copy(tags = listOf("dream pop", "shoegaze"))
        val state = engine.buildInterestState(emptyList(), now, styleFeedback = listOf(vote(seed)))
        val candidate = song("new recording").copy(tags = seed.tags)
        assertTrue(score(candidate, state).inInterestPool)
        val generic = song("another recording").copy(tags = listOf("欧美", "高音质"))
        assertFalse(score(generic, state).inInterestPool)
    }

    @Test fun explicitSongReferenceDoesNotGetDiscardedBecauseOldTasteWasDifferent() {
        val seed = song("search choice").copy(tags = listOf("欧美"))
        val state = engine.buildInterestState(emptyList(), now, setOf("mandarin", "folk"), listOf(vote(seed)))
        val related = song("a new song").copy(tags = listOf("欧美"))
        assertTrue(score(related, state, seed).inInterestPool)
        assertFalse(score(related, state).inInterestPool)
        assertEquals(setOf("mandarin", "folk"), state.preferredTopics)
    }

    @Test fun unrequestedModifiedVersionsRankBelowTheSameQualityOriginal() {
        val seed = song("Duvet")
        val state = engine.buildInterestState(emptyList(), now, styleFeedback = listOf(vote(seed)))
        val original = score(song("new recording 官方音频"), state, seed)
        val altered = score(song("new recording 加速版"), state, seed)
        assertTrue(original.score > altered.score)
    }
}
