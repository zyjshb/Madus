package com.madus.mobile.domain

import org.junit.Assert.*
import org.junit.Test

class MusicInterestPoolTest {
    private val now = 1_800_000_000_000L
    private fun track(id: String) = Track(id, "《单曲$id》", "上传者$id", durationMs = 200_000, categoryId = 28)

    @Test fun machineRecommendationsNeverBecomeTasteSeeds() {
        val pool = MusicInterestPool()
        pool.offer(MusicPoolCandidate(track("a"), addedAtMs = now))
        assertTrue(pool.snapshot(now).seeds.isEmpty())
        pool.support(track("b"), now)
        assertEquals(listOf("b"), pool.snapshot(now).seeds.map { it.track.id })
    }

    @Test fun restoresUnplayedPoolButExpiresOldCandidatesAndRejectsSongVersions() {
        val pool = MusicInterestPool()
        val a = track("a")
        pool.offer(MusicPoolCandidate(a, addedAtMs = now))
        pool.offer(MusicPoolCandidate(track("b").copy(title = a.title), addedAtMs = now))
        pool.support(a, now)
        val restored = MusicInterestPool()
        restored.restore(pool.snapshot(now), now + 1000)
        assertEquals(2, restored.snapshot(now + 1000).candidates.size)
        restored.reject(a)
        assertTrue(restored.snapshot(now + 1000).candidates.isEmpty())
        assertTrue(restored.snapshot(now + 1000).seeds.isEmpty())
        assertTrue(pool.snapshot(now + MusicInterestPool.CANDIDATE_TTL_MS + 1).candidates.isEmpty())
    }

    @Test fun boundedPoolPreservesRelatedEvidenceAndRejectsNonMusic() {
        val pool = MusicInterestPool(capacity = 2)
        pool.offer(MusicPoolCandidate(track("a"), "related-like", setOf("folk"), now))
        pool.offer(MusicPoolCandidate(track("a"), addedAtMs = now + 1))
        assertEquals(setOf("folk"), pool.snapshot(now + 1).candidates.single().seedTopicKeys)
        pool.offer(MusicPoolCandidate(track("video").copy(categoryId = 4), addedAtMs = now))
        pool.offer(MusicPoolCandidate(track("b"), addedAtMs = now))
        pool.offer(MusicPoolCandidate(track("c"), addedAtMs = now))
        assertEquals(listOf("b", "c"), pool.snapshot(now + 1).candidates.map { it.track.id })
    }

    @Test fun refreshingCandidateKeepsItWhileUnrefreshedCandidateIsEvicted() {
        val pool = MusicInterestPool(capacity = 2)
        pool.offer(MusicPoolCandidate(track("a"), addedAtMs = now))
        pool.offer(MusicPoolCandidate(track("b"), addedAtMs = now + 1))
        pool.offer(MusicPoolCandidate(track("a"), addedAtMs = now + 2))
        pool.offer(MusicPoolCandidate(track("c"), addedAtMs = now + 3))
        assertEquals(listOf("a", "c"), pool.snapshot(now + 3).candidates.map { it.track.id })
    }

    @Test fun delayedDiskRestoreCannotReplaceOrEvictNewNetworkResults() {
        val pool = MusicInterestPool(capacity = 2)
        val fresh = track("a").copy(title = "《新的音乐元数据》")
        pool.offer(MusicPoolCandidate(fresh, "related-like", setOf("folk"), now))
        pool.offer(MusicPoolCandidate(track("b"), addedAtMs = now))
        pool.support(fresh, now)
        val oldDisk = MusicPoolSnapshot(
            candidates = listOf(
                MusicPoolCandidate(track("a"), addedAtMs = now - 2000),
                MusicPoolCandidate(track("old"), addedAtMs = now - 1000),
            ),
            seeds = listOf(MusicPoolSeed(track("a"), now - 2000)),
        )
        pool.restore(oldDisk, now)
        val restored = pool.snapshot(now)
        assertEquals(setOf("a", "b"), restored.candidates.map { it.track.id }.toSet())
        assertEquals(fresh.title, restored.candidates.first { it.track.id == "a" }.track.title)
        assertEquals(setOf("folk"), restored.candidates.first { it.track.id == "a" }.seedTopicKeys)
        assertEquals(now, restored.seeds.single().supportedAtMs)

        pool.reject(fresh)
        pool.restore(oldDisk, now)
        assertEquals(listOf("b"), pool.snapshot(now).candidates.map { it.track.id })
        assertTrue(pool.snapshot(now).seeds.isEmpty())
    }

    @Test fun poolDoesNotKeepExpiringStreamUrlsOrVideoFlags() {
        val pool = MusicInterestPool()
        val playing = track("a").copy(streamUrl = "https://example.test/temporary", isVideoStream = true)
        pool.offer(MusicPoolCandidate(playing, addedAtMs = now))
        pool.support(playing, now)
        val cached = pool.snapshot(now)
        assertNull(cached.candidates.single().track.streamUrl)
        assertFalse(cached.candidates.single().track.isVideoStream)
        assertNull(cached.seeds.single().track.streamUrl)
        assertFalse(cached.seeds.single().track.isVideoStream)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidCapacityIsRejectedAtConstruction() {
        MusicInterestPool(capacity = -1)
    }
}
