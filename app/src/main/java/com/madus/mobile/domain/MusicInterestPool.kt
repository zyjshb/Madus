package com.madus.mobile.domain

data class MusicPoolCandidate(
    val track: Track,
    val source: String = "search",
    val seedTopicKeys: Set<String> = emptySet(),
    val addedAtMs: Long,
    val seedSongKeys: Set<String> = emptySet(),
)

data class MusicPoolSeed(val track: Track, val supportedAtMs: Long)
data class MusicPoolSnapshot(
    val candidates: List<MusicPoolCandidate> = emptyList(),
    val seeds: List<MusicPoolSeed> = emptyList(),
)

/** 只缓存曲目元数据。候选永远不能自行升级成兴趣种子。 */
class MusicInterestPool(private val capacity: Int = 320) {
    init { require(capacity > 0) { "Music interest pool capacity must be positive" } }

    private val candidates = linkedMapOf<String, MusicPoolCandidate>()
    private val seeds = linkedMapOf<String, MusicPoolSeed>()
    private var restored = false

    @Synchronized fun restore(snapshot: MusicPoolSnapshot, nowMs: Long) {
        if (restored) return
        restored = true
        // 冷启动读盘可能晚于首批网络结果，按时间合并，不能挤掉或回退新数据。
        val mergedCandidates = (candidates.values.toList() + snapshot.candidates)
            .filter { nowMs - it.addedAtMs in 0..CANDIDATE_TTL_MS }
            .groupBy { it.track.id }.values.map { versions -> versions.maxBy { it.addedAtMs } }
            .sortedBy { it.addedAtMs }
            .takeLast(capacity)
        val mergedSeeds = (seeds.values.toList() + snapshot.seeds)
            .filter { nowMs - it.supportedAtMs in 0..SEED_TTL_MS }
            .groupBy { it.track.id }.values.map { versions -> versions.maxBy { it.supportedAtMs } }
            .sortedBy { it.supportedAtMs }
            .takeLast(80)
        candidates.clear()
        seeds.clear()
        mergedCandidates.forEach { offer(it) }
        mergedSeeds.forEach { support(it.track, it.supportedAtMs) }
    }

    @Synchronized fun offer(candidate: MusicPoolCandidate) {
        if (!TrackFilters.isLikelyMusic(candidate.track)) return
        val old = candidates[candidate.track.id]
        if (old != null && old.addedAtMs > candidate.addedAtMs) return
        // 刷新过的候选排到队尾，不会因首次入池较早而立即被淘汰。
        candidates.remove(candidate.track.id)
        val metadata = candidate.track.copy(streamUrl = null, isVideoStream = false)
        // 新搜索结果不能覆盖已经得到正反馈种子支持的来源关系。
        candidates[candidate.track.id] = if (old != null && (old.seedTopicKeys.isNotEmpty() || old.seedSongKeys.isNotEmpty()) && candidate.seedTopicKeys.isEmpty() && candidate.seedSongKeys.isEmpty()) {
            old.copy(track = metadata, addedAtMs = candidate.addedAtMs)
        } else candidate.copy(track = metadata,
            seedSongKeys = (old?.seedSongKeys.orEmpty() + candidate.seedSongKeys).take(6).toSet())
        while (candidates.size > capacity) candidates.remove(candidates.keys.first())
    }

    @Synchronized fun support(track: Track, nowMs: Long) {
        if (!TrackFilters.isLikelyMusic(track)) return
        if ((seeds[track.id]?.supportedAtMs ?: Long.MIN_VALUE) > nowMs) return
        seeds.remove(track.id)
        seeds[track.id] = MusicPoolSeed(track.copy(streamUrl = null, isVideoStream = false), nowMs)
        while (seeds.size > 80) seeds.remove(seeds.keys.first())
    }

    @Synchronized fun reject(track: Track) {
        val key = MusicDiscovery.songKey(track)
        seeds.entries.removeAll { it.key == track.id || (key.isNotBlank() && MusicDiscovery.songKey(it.value.track) == key) }
        candidates.entries.removeAll { it.key == track.id || (key.isNotBlank() && MusicDiscovery.songKey(it.value.track) == key) }
    }

    @Synchronized fun snapshot(nowMs: Long): MusicPoolSnapshot {
        candidates.entries.removeAll { nowMs - it.value.addedAtMs !in 0..CANDIDATE_TTL_MS }
        seeds.entries.removeAll { nowMs - it.value.supportedAtMs !in 0..SEED_TTL_MS }
        return MusicPoolSnapshot(candidates.values.toList(), seeds.values.toList())
    }

    companion object {
        const val CANDIDATE_TTL_MS = 7 * 24 * 60 * 60 * 1000L
        const val SEED_TTL_MS = 180 * 24 * 60 * 60 * 1000L
    }
}
