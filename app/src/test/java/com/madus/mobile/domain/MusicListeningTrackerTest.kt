package com.madus.mobile.domain

import org.junit.Assert.*
import org.junit.Test

class MusicListeningTrackerTest {
    private val song = Track("a", "单曲《远方》", "作者", durationMs = 200_000, categoryId = 28)

    @Test fun savesOneCumulativeSessionAndFlushesItBeforeChangingTracks() {
        val tracker = MusicListeningTracker("test")
        val events = mutableListOf<RecommendationEvent>()
        for (i in 0..10) events += tracker.sample(song, 1, i * 2000L, 200_000, true, true, "search", true, i * 2000L)
        events += tracker.sample(song.copy(id = "b"), 2, 0, 200_000, true, true, "recommend", false, 22_000)
        assertEquals(1, events.map { it.listeningSessionId }.distinct().size)
        assertEquals(20_000, events.last().listenedMs)
        assertEquals(20_000, events.last().foregroundMs)
        assertTrue(events.last().fromSearch)
        assertEquals(0, tracker.heardMs)
    }

    @Test fun pausesLoadingAndSeekingCannotInflatePlaybackOrDwell() {
        val tracker = MusicListeningTracker("test")
        tracker.sample(song, 1, 0, 200_000, true, true, "recommend", false, 0)
        tracker.sample(song, 1, 2000, 200_000, true, true, "recommend", false, 2000)
        tracker.sample(song, 1, 2000, 200_000, false, true, "recommend", false, 200_000)
        tracker.discontinuity()
        tracker.sample(song, 1, 180_000, 200_000, true, true, "recommend", false, 202_000)
        tracker.sample(song, 1, 182_000, 200_000, true, true, "recommend", false, 204_000)
        val event = tracker.flush(204_000)!!
        assertEquals(4000, event.listenedMs)
        assertEquals(4000, event.foregroundMs)
    }

    @Test fun backgroundListeningStillCountsButAddsNoForegroundDwellAndReplayStartsANewSample() {
        val tracker = MusicListeningTracker("test")
        tracker.sample(song, 1, 0, 200_000, true, false, "recommend", false, 0)
        val first = tracker.sample(song, 1, 2000, 200_000, true, false, "recommend", false, 2000).single()
        tracker.sample(song, 2, 0, 200_000, true, false, "recommend", false, 4000)
        val replay = tracker.sample(song, 2, 2000, 200_000, true, false, "recommend", false, 6000).single()
        assertNotEquals(first.listeningSessionId, replay.listeningSessionId)
        assertEquals(2000, replay.listenedMs)
        assertEquals(0, replay.foregroundMs)
    }
}
