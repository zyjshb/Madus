package com.madus.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningProgressTest {
    @Test fun sameTrackNewPlaybackGenerationStartsFromZeroEvenAtAdjacentPosition() {
        val progress = ListeningProgress()
        assertEquals(0L, progress.sample("a", 0, true, generation = 1))
        assertEquals(2_000L, progress.sample("a", 2_000, true, generation = 1))
        assertEquals(4_000L, progress.sample("a", 4_000, true, generation = 1))
        // A retry/replay can restore a nearby position; position delta alone cannot identify it.
        assertEquals(0L, progress.sample("a", 5_000, true, generation = 2))
        assertEquals(2_000L, progress.sample("a", 7_000, true, generation = 2))
    }

    @Test fun repeatedSamplesWithinGenerationAccumulateOnlyPlaybackProgress() {
        val progress = ListeningProgress()
        assertEquals(0L, progress.sample("a", 100_000, true, generation = 5))
        assertEquals(2_000L, progress.sample("a", 102_000, true, generation = 5))
        assertEquals(2_000L, progress.sample("a", 102_000, true, generation = 5))
        assertEquals(4_000L, progress.sample("a", 104_000, true, generation = 5))
    }

    @Test fun forwardAndBackwardSeeksDoNotBecomeListeningTime() {
        val progress = ListeningProgress()
        progress.sample("a", 0, true, generation = 1)
        assertEquals(2_000L, progress.sample("a", 2_000, true, generation = 1))
        assertEquals(2_000L, progress.sample("a", 180_000, true, generation = 1))
        assertEquals(4_000L, progress.sample("a", 182_000, true, generation = 1))
        assertEquals(4_000L, progress.sample("a", 10_000, true, generation = 1))
        assertEquals(6_000L, progress.sample("a", 12_000, true, generation = 1))
    }

    @Test fun pausingAndResumingDoNotCountUnobservedPositionChanges() {
        val progress = ListeningProgress()
        progress.sample("a", 0, true)
        assertEquals(2_000L, progress.sample("a", 2_000, true))
        assertEquals(2_000L, progress.sample("a", 3_000, false))
        assertEquals(2_000L, progress.sample("a", 5_000, false))
        assertEquals(2_000L, progress.sample("a", 7_000, true))
        assertEquals(4_000L, progress.sample("a", 9_000, true))
    }

    @Test fun changingTrackAndReturningToItNeverRestoresOldTotals() {
        val progress = ListeningProgress()
        progress.sample("a", 0, true)
        assertEquals(2_000L, progress.sample("a", 2_000, true))
        assertEquals(0L, progress.sample("b", 3_000, true))
        assertEquals(2_000L, progress.sample("b", 5_000, true))
        assertEquals(0L, progress.sample("a", 6_000, true))
        assertEquals(2_000L, progress.sample("a", 8_000, true))
    }

    @Test fun resetAndNoCurrentTrackClearTheListeningBaseline() {
        val progress = ListeningProgress()
        progress.sample("a", 0, true, generation = 9)
        progress.sample("a", 2_000, true, generation = 9)
        progress.reset()
        assertEquals(0L, progress.sample("a", 3_000, true, generation = 9))
        assertEquals(2_000L, progress.sample("a", 5_000, true, generation = 9))
        assertEquals(0L, progress.sample(null, 6_000, true, generation = 9))
        assertEquals(0L, progress.sample(null, 8_000, true, generation = 9))
        assertEquals(0L, progress.sample("a", 10_000, true, generation = 9))
    }
}
