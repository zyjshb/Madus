package com.madus.mobile.domain

import org.junit.Assert.*
import org.junit.Test

/** Behavioural scenarios use real metadata, events, scoring and pool snapshots, with no preset scores. */
class RealtimeRecommendationScenarioTest {
    private val now = 1_800_000_000_000L
    private val engine = RecommendationEngine()
    private val ranker = RecommendationReRanker()
    private val labels = mapOf("folk" to "民谣", "dj" to "电音", "rap" to "说唱", "pop" to "流行")

    @Test fun likingThenListeningToFolkKeepsNewFolkComingAfterRestart() {
        val pool = warmPool()
        val before = recommendations(pool, emptyList())
        val seed = song("folk", 99)
        val events = mutableListOf(event(seed, RecommendationEventType.LIKE, now))
        pool.support(seed, now)

        repeat(3) { step ->
            val at = now + (step + 1) * 240_000L
            val next = recommendations(pool, events, at = at).first()
            assertTrue("认真听民谣时，下首应继续符合兴趣", isGenre(next, "folk"))
            events += event(next, RecommendationEventType.WATCH_90, at)
            pool.support(next, at)
        }

        val restartAt = now + 15 * 60_000L
        val restarted = MusicInterestPool().apply { restore(pool.snapshot(restartAt), restartAt) }
        val after = recommendations(restarted, events, at = restartAt)
        assertEquals(10, after.size)
        assertTrue(after.count { isGenre(it, "folk") } > before.count { isGenre(it, "folk") })
        assertTrue("兴趣连续性不能被强制随机探索冲淡", after.count { isGenre(it, "folk") } >= 8)
        assertTrue(after.none { track -> events.any { it.trackId == track.id } })
        assertEquals(4, restarted.snapshot(restartAt).seeds.size)
    }

    @Test fun severalSkippedElectronicSongsLowerTheirShareWithoutDeletingFolkInterest() {
        val pool = warmPool()
        val preferences = setOf("folk", "dj")
        val before = recommendations(pool, emptyList(), preferences)
        val skipped = before.filter { isGenre(it, "dj") }.take(3)
        assertEquals("最初两类口味都应参与推荐", 3, skipped.size)
        val events = skipped.mapIndexed { i, track ->
            event(track, RecommendationEventType.SKIP_FAST, now + i * 10_000L)
        }
        val after = recommendations(pool, events, preferences, now + 30_000L)
        assertEquals(10, after.size)
        assertTrue(after.count { isGenre(it, "dj") } < before.count { isGenre(it, "dj") })
        assertTrue(after.any { isGenre(it, "folk") })
        assertTrue(after.none { track -> skipped.any { it.id == track.id } })
    }

    @Test fun rejectingOneRecordingRemovesItsVersionsButKeepsOtherMusicByTheUploader() {
        val pool = MusicInterestPool()
        val disliked = song("dj", 1).copy(artist = "音乐分享站", ownerMid = "same-uploader")
        val version = disliked.copy(id = "different-upload", bvid = "different-upload")
        val otherSong = song("folk", 2).copy(artist = disliked.artist, ownerMid = disliked.ownerMid)
        listOf(disliked, version, otherSong).forEach {
            pool.offer(MusicPoolCandidate(it, addedAtMs = now))
        }
        pool.support(disliked, now - 1000)
        val events = listOf(event(disliked, RecommendationEventType.NOT_INTERESTED, now))
        pool.reject(disliked)
        val after = recommendations(pool, events, setOf("folk", "dj"))
        assertEquals(listOf(otherSong.id), after.map { it.id })
        assertTrue(pool.snapshot(now).seeds.isEmpty())
        assertTrue(engine.buildInterestState(events, now).mutedTopics.isEmpty())
    }

    @Test fun theSameWarmCatalogProducesDifferentQueuesForDifferentListeners() {
        val snapshot = warmPool().snapshot(now)
        val folkUser = MusicInterestPool().apply { restore(snapshot, now) }
        val rapUser = MusicInterestPool().apply { restore(snapshot, now) }
        val folkQueue = recommendations(folkUser, emptyList(), setOf("folk"))
        val rapQueue = recommendations(rapUser, emptyList(), setOf("rap"))
        assertEquals(10, folkQueue.size)
        assertEquals(10, rapQueue.size)
        assertTrue(folkQueue.count { isGenre(it, "folk") } >= 7)
        assertTrue(rapQueue.count { isGenre(it, "rap") } >= 7)
        assertNotEquals(folkQueue.map { it.id }, rapQueue.map { it.id })
        assertTrue("预热候选不会伪造用户兴趣种子", folkUser.snapshot(now).seeds.isEmpty())
        assertTrue(rapUser.snapshot(now).seeds.isEmpty())
    }

    private fun recommendations(
        pool: MusicInterestPool,
        events: List<RecommendationEvent>,
        preferences: Set<String> = emptySet(),
        at: Long = now,
    ): List<Track> {
        val state = engine.buildInterestState(events, at, preferences)
        val heard = MusicDiscovery.recentlyHeard(events, at)
        val context = FeedContext(musicOnly = true, nowMs = at, limit = 10,
            sessionSeenIds = heard.map { it.trackId }.toSet(),
            recentSongKeys = heard.map { it.songKey }.toSet())
        val scored = pool.snapshot(at).candidates.map { candidate ->
            engine.scoreCandidate(candidate.track, null, state, candidate.source, context, candidate.seedTopicKeys)
        }
        return ranker.rerank(scored, context)
    }

    private fun warmPool() = MusicInterestPool().apply {
        repeat(16) { index ->
            labels.keys.forEach { genre -> offer(MusicPoolCandidate(song(genre, index), addedAtMs = now)) }
        }
    }

    private fun song(genre: String, index: Int) = Track(
        id = "$genre-$index", bvid = "$genre-$index", title = "《${labels.getValue(genre)}作品$index》官方音频",
        artist = "音乐人$genre$index", ownerMid = "$genre-$index", durationMs = 240_000,
        source = MusicSourceType.BILIBILI, categoryId = 28, categoryName = "原创音乐",
        tags = listOf(labels.getValue(genre)),
    )

    private fun event(track: Track, type: RecommendationEventType, at: Long): RecommendationEvent {
        val profile = ContentProfileParser.profileFromTrack(track, at)
        return RecommendationEvent(track.id, track.bvid, type, at, "recommend", profile.topicKeys,
            profile.authorKey, MusicDiscovery.songKey(track))
    }

    private fun isGenre(track: Track, genre: String) =
        genre in ContentProfileParser.profileFromTrack(track).topicKeys
}
