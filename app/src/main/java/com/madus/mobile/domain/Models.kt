package com.madus.mobile.domain

enum class MusicSourceType(
    val id: String,
    val displayName: String,
) {
    BILIBILI("bilibili", "Bilibili"),
    LOCAL_DEMO("local_demo", "演示"),
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val coverUrl: String? = null,
    val durationMs: Long = 0L,
    val source: MusicSourceType = MusicSourceType.LOCAL_DEMO,
    val streamUrl: String? = null,
    /** Bilibili: bvid / aid / cid for playurl resolution */
    val bvid: String = "",
    val aid: String = "",
    val cid: String = "",
    /** UP 主 mid，点歌手名进主页用 */
    val ownerMid: String = "",
    /** UP 头像（短视频右侧球用；可空，播放时再补） */
    val ownerFace: String = "",
    /** 当前 streamUrl 是否为含画面的视频流（视频模式） */
    val isVideoStream: Boolean = false,
    /**
     * 多分 P / 合集集数提示（搜索结果可能 >1）。
     * 0/1 = 单集；>1 表示可展开整部合集。
     */
    val pageCount: Int = 1,
)

data class Playlist(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
    val trackCount: Int = 0,
    val source: MusicSourceType = MusicSourceType.LOCAL_DEMO,
)

data class AuthSession(
    val source: MusicSourceType,
    val displayName: String = "",
    val isLoggedIn: Boolean = false,
    val credentialBlob: String? = null,
    val updatedAtMs: Long = 0L,
    /** B 站头像等 */
    val avatarUrl: String? = null,
)

enum class RepeatMode { OFF, ONE, ALL }

data class PlaybackState(
    val current: Track? = null,
    val queue: List<Track> = emptyList(),
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface PlayerCommand {
    data object Play : PlayerCommand
    data object Pause : PlayerCommand
    data object Toggle : PlayerCommand
    /** 停播并清空当前媒体，避免空队列时 Next/Ended 死循环 */
    data object Stop : PlayerCommand
    data object Next : PlayerCommand
    data object Previous : PlayerCommand
    data class Seek(val positionMs: Long) : PlayerCommand
    data class PlayTrack(
        val track: Track,
        val queue: List<Track> = listOf(track),
        /** Resume from this position (ms). 0 = start from beginning. */
        val startPositionMs: Long = 0L,
    ) : PlayerCommand
}
