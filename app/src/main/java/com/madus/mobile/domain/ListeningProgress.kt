package com.madus.mobile.domain

/** 只累计实际播放推进的时间；拖动进度条、恢复旧进度、暂停不等于听完。 */
class ListeningProgress {
    private var trackId: String? = null
    private var playbackGeneration = 0
    private var position = 0L
    private var playing = false
    private var listened = 0L
    var foregroundMs: Long = 0L
        private set
    private var foreground = false

    fun sample(id: String?, positionMs: Long, isPlaying: Boolean, generation: Int = 0, isForeground: Boolean = false): Long {
        if (id == null) {
            reset()
            return 0L
        }
        if (id != trackId || generation != playbackGeneration) {
            trackId = id
            playbackGeneration = generation
            listened = 0L
            foregroundMs = 0L
        } else if (playing && isPlaying) {
            val delta = positionMs - position
            if (delta in 1..4_000L) {
                listened += delta
                if (foreground && isForeground) foregroundMs += delta
            }
        }
        position = positionMs
        playing = isPlaying
        foreground = isForeground
        return listened
    }

    /** A new play attempt, including replaying the same track, starts from a new baseline. */
    fun reset() {
        trackId = null
        playbackGeneration = 0
        position = 0L
        playing = false
        listened = 0L
        foregroundMs = 0L
        foreground = false
    }

    fun discontinuity() { playing = false }
}
