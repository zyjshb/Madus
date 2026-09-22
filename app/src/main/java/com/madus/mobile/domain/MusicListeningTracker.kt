package com.madus.mobile.domain

/** 每次播放一个累积样本；进度更新覆盖该样本，不把长歌曲拆成多次“喜欢”。 */
class MusicListeningTracker(private val sessionPrefix: String = java.util.UUID.randomUUID().toString()) {
    private val progress = ListeningProgress()
    private var track: Track? = null
    private var generation = -1
    private var source = ""
    private var searched = false
    private var duration = 0L
    private var savedMs = 0L
    var heardMs = 0L
        private set

    fun sample(current: Track?, playGeneration: Int, positionMs: Long, durationMs: Long,
        playing: Boolean, foreground: Boolean, sourceId: String, fromSearch: Boolean,
        nowMs: Long): List<RecommendationEvent> {
        val snapshots = mutableListOf<RecommendationEvent>()
        if (track?.id != current?.id || generation != playGeneration) {
            flush(nowMs)?.let(snapshots::add)
            track = current
            generation = playGeneration
            source = sourceId
            searched = fromSearch
            savedMs = 0
            heardMs = 0
            duration = 0
        }
        duration = durationMs.takeIf { it > 0 } ?: current?.durationMs ?: 0L
        heardMs = progress.sample(current?.id, positionMs, playing, playGeneration, foreground)
        if (heardMs >= 500 && (savedMs == 0L || heardMs - savedMs >= 15_000 || !playing)) {
            flush(nowMs)?.let(snapshots::add)
        }
        return snapshots
    }

    fun flush(nowMs: Long): RecommendationEvent? {
        val song = track ?: return null
        if (heardMs < 500 || heardMs <= savedMs || !TrackFilters.isLikelyMusic(song)) return null
        savedMs = heardMs
        val profile = ContentProfileParser.profileFromTrack(song, nowMs)
        return RecommendationEvent(song.id, song.bvid, RecommendationEventType.LISTEN_SAMPLE, nowMs,
            source, profile.topicKeys, profile.authorKey, MusicDiscovery.songKey(song),
            "$sessionPrefix:$generation:${song.id}", heardMs, duration, progress.foregroundMs, searched)
    }

    fun discontinuity() = progress.discontinuity()
}
