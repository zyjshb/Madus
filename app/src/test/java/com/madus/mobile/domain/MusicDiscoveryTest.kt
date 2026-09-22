package com.madus.mobile.domain

import org.junit.Assert.*
import org.junit.Test

class MusicDiscoveryTest {
    private val now = 1_800_000_000_000L
    private fun song(id: String, title: String = "单曲 $id", tags: List<String> = emptyList()) =
        Track(id, title, "up-$id", durationMs = 200_000, categoryId = 28, tags = tags)

    @Test fun metadataAndWordBoundariesKeepVideosOut() {
        assertFalse(TrackFilters.isLikelyMusic(song("v", "游戏实况 BGM", listOf("音乐")).copy(categoryId = 4)))
        assertFalse(TrackFilters.isLikelyMusic(song("v", "官方完整版 手机测评").copy(categoryId = 95)))
        assertFalse(TrackFilters.isLikelyMusic(song("v", "Open video").copy(categoryId = 0)))
        assertFalse(TrackFilters.isLikelyMusic(song("v", "聊聊今天发生的事情").copy(categoryId = 130, album = "音乐搜索")))
        assertFalse(TrackFilters.hasMusicSignal("Developed shopping"))
        assertFalse(TrackFilters.isLikelyMusic(song("v", "歌曲教程")))
        assertFalse(TrackFilters.isLikelyMusic(song("v").copy(durationMs = 25_000)))
        assertFalse(TrackFilters.isLikelyMusic(song("v").copy(durationMs = 0)))
        assertTrue(TrackFilters.isLikelyMusic(song("m", "电影主题曲《归途》")))
        assertTrue(TrackFilters.isLikelyMusic(song("m", "怎么了")))
        assertTrue(TrackFilters.isLikelyMusic(song("m", "游戏原声 OST")))
    }

    @Test fun tagsTeachGenreAndLanguageWithoutTitleKeywords() {
        val topics = ContentProfileParser.profileFromTrack(song("a", "远方", listOf("民谣", "粤语"))).topicKeys
        assertTrue(topics.containsAll(setOf("folk", "cantonese", "music")))
        val english = ContentProfileParser.profileFromTrack(song("a", "Delivery"))
        assertFalse("live" in english.topicKeys)
    }

    @Test fun discoveryUsesTasteRotatesAndHonorsCooldown() {
        val state = InterestState(longTermTopics = mapOf("folk" to 4.0, "rock" to 2.0),
            mutedTopics = mapOf("rock" to now + 1000))
        val first = MusicDiscovery.searchQueries(state, emptyList(), 0, now)
        val next = MusicDiscovery.searchQueries(state, emptyList(), 1, now)
        assertEquals("民谣 单曲", first.first())
        assertTrue(first.none { it.contains("摇滚") })
        assertNotEquals(first, next)
        assertEquals(4, MusicDiscovery.searchQueries(InterestState(), emptyList(), 0, now).size)
    }

    @Test fun coreInterestStaysInEveryRoundAndExplorationRemainsAdjacent() {
        val state = RecommendationEngine().buildInterestState(emptyList(), now, setOf("folk"))
        for (round in 0..20) {
            val queries = MusicDiscovery.searchQueries(state, emptyList(), round, now)
            assertEquals("民谣 单曲", queries.first())
            assertTrue(queries.all { it.contains("民谣") || it.contains("流行") || it.contains("治愈") })
            assertTrue(queries.none { it.contains("说唱") || it.contains("电子") })
        }
    }

    @Test fun seedLibraryCannotOverrideExplicitPrimaryTaste() {
        val state = RecommendationEngine().buildInterestState(emptyList(), now, setOf("folk"))
        val rapSeeds = (1..100).map { song("rap$it", tags = listOf("说唱")) }
        assertEquals("民谣 单曲", MusicDiscovery.searchQueries(state, rapSeeds, 3, now).first())
    }

    @Test fun searchProvenanceIsKnownForEveryGeneratedQuery() {
        for (topic in MusicDiscovery.availableTopics.keys) {
            val state = RecommendationEngine().buildInterestState(emptyList(), now, setOf(topic))
            for (round in 0..2) for (query in MusicDiscovery.searchQueries(state, emptyList(), round, now)) {
                assertTrue("No topic provenance for $query", MusicDiscovery.queryTopicKeys(query).isNotEmpty())
            }
        }
    }

    @Test fun heardCooldownSurvivesSessionButExpires() {
        fun event(id: String, age: Long, type: RecommendationEventType) = RecommendationEvent(
            id, id, type, now - age, "search", setOf("music"), null, "song$id")
        val heard = MusicDiscovery.recentlyHeard(listOf(
            event("recent", 60_000, RecommendationEventType.PLAY_START),
            event("old", RecommendationTuning.HEARD_COOLDOWN_MS + 1, RecommendationEventType.WATCH_90),
            event("like", 0, RecommendationEventType.LIKE),
            event("future", -1, RecommendationEventType.PLAY_START),
        ), now)
        assertEquals(listOf("recent"), heard.map { it.trackId })
    }

    @Test fun duplicateVersionsAndVideosNeverEnterMusicQueueEvenWhenPoolIsSmall() {
        val original = song("a", "歌手《远方》官方音频")
        val cover = song("b", "另一个歌手《远方》翻唱")
        val other = song("c", "《明天》")
        val video = song("v", "音乐教学")
        val candidates = listOf(original, cover, video, other).mapIndexed { i, t ->
            ScoredTrack(t, 10.0 - i, "related")
        }
        val ranker = RecommendationReRanker()
        val output = ranker.rerank(candidates, FeedContext(musicOnly = true, limit = 10))
        assertEquals(listOf("a", "c"), output.map { it.id })
        val afterRestart = ranker.rerank(candidates, FeedContext(musicOnly = true,
            recentSongKeys = setOf(MusicDiscovery.songKey(original))))
        assertEquals(listOf("c"), afterRestart.map { it.id })
    }

    @Test fun oneMusicTopicDoesNotPreventAFullMusicQueue() {
        val pool = (1..30).map { ScoredTrack(song("$it"), 100.0 - it, "search") }
        val result = RecommendationReRanker().rerank(pool, FeedContext(musicOnly = true, limit = 30))
        assertEquals(30, result.size)
    }

    @Test fun fineTasteOutranksGenericMusicAndOldLikesRemainUseful() {
        val event = RecommendationEvent("a", "a", RecommendationEventType.LIKE,
            now - 60L * 24 * 60 * 60 * 1000, "search", setOf("music", "folk"), null)
        val engine = RecommendationEngine()
        val state = engine.buildInterestState(listOf(event), now)
        assertTrue(state.longTermTopics.getValue("folk") > 0)
        val context = FeedContext(musicOnly = true, nowMs = now)
        val folk = engine.scoreCandidate(song("a", tags = listOf("民谣")), null, state, "search", context)
        val rock = engine.scoreCandidate(song("b", tags = listOf("摇滚")), null, state, "search", context)
        assertTrue(folk.score > rock.score)
    }

    @Test fun skipSongCooldownExpiresWithoutTurningIntoAGenreBan() {
        val events = (1..2).map { RecommendationEvent("$it", "$it", RecommendationEventType.SKIP_FAST,
            now - 60_000, "recommend", setOf("music", "rock"), null) }
        val engine = RecommendationEngine()
        val initial = engine.buildInterestState(events, now)
        assertTrue(initial.mutedTopics.isEmpty())
        assertEquals(setOf("1", "2"), initial.cooledTrackIds)
        assertEquals(initial.cooledTrackIds, engine.buildInterestState(events, now + 60_000).cooledTrackIds)
        assertTrue(engine.buildInterestState(events, now + RecommendationTuning.FAST_SKIP_COOLDOWN_MS).cooledTrackIds.isEmpty())
    }

    @Test fun seekingAndPausingDoNotCountAsListening() {
        val progress = ListeningProgress()
        assertEquals(0L, progress.sample("a", 120_000, true))
        assertEquals(2_000L, progress.sample("a", 122_000, true))
        assertEquals(2_000L, progress.sample("a", 190_000, true))
        assertEquals(2_000L, progress.sample("a", 190_000, false))
        assertEquals(2_000L, progress.sample("a", 190_000, true))
        assertEquals(4_000L, progress.sample("a", 192_000, true))
        assertEquals(0L, progress.sample("b", 2_000, true))
    }

    @Test fun sameBvidWithDifferentLocalIdIsStillHeard() {
        val candidate = ScoredTrack(song("local-bv").copy(bvid = "BV123"), 100.0, "search")
        assertTrue(RecommendationReRanker().rerank(listOf(candidate),
            FeedContext(musicOnly = true, sessionSeenIds = setOf("BV123"))).isEmpty())
    }
}
