package com.madus.mobile.domain

/** 只累计实际播放推进的时间；拖动进度条、恢复旧进度、暂停不等于听完。 */
class ListeningProgress {
    private var trackId: String? = null
    private var position = 0L
    private var playing = false
    private var listened = 0L

    fun sample(id: String?, positionMs: Long, isPlaying: Boolean): Long {
        if (id != trackId) {
            trackId = id
            listened = 0L
        } else if (playing && isPlaying) {
            val delta = positionMs - position
            if (delta in 1..4_000L) listened += delta
        }
        position = positionMs
        playing = isPlaying
        return listened
    }
}
