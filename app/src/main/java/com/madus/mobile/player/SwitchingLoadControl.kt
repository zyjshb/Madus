package com.madus.mobile.player

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 按「游戏轻量」切换缓冲策略；不重建 ExoPlayer。
 * 轻量：更小 min/max 缓冲，少占内存与预读带宽，功能/曲目不变。
 */
@UnstableApi
class SwitchingLoadControl(
    private val gameLiteMode: AtomicBoolean,
) : LoadControl {

    private val normal: LoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
            DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
        )
        .build()

    /** 约一半默认缓冲：够听歌，少抢游戏带宽/内存 */
    private val lite: LoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs */ 12_000,
            /* maxBufferMs */ 24_000,
            /* bufferForPlaybackMs */ 1_200,
            /* bufferForPlaybackAfterRebufferMs */ 2_000,
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    private fun active(): LoadControl = if (gameLiteMode.get()) lite else normal

    override fun onPrepared() = active().onPrepared()

    @Deprecated("Deprecated in Media3")
    override fun onTracksSelected(
        renderers: Array<out Renderer>,
        trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection>,
    ) {
        @Suppress("DEPRECATION")
        active().onTracksSelected(renderers, trackGroups, trackSelections)
    }

    override fun onStopped() = active().onStopped()

    override fun onReleased() = active().onReleased()

    override fun getAllocator(): Allocator = active().allocator

    override fun getBackBufferDurationUs(): Long = active().backBufferDurationUs

    override fun retainBackBufferFromKeyframe(): Boolean = active().retainBackBufferFromKeyframe()

    override fun shouldContinueLoading(
        playbackPositionUs: Long,
        bufferedDurationUs: Long,
        playbackSpeed: Float,
    ): Boolean = active().shouldContinueLoading(playbackPositionUs, bufferedDurationUs, playbackSpeed)

    override fun shouldStartPlayback(
        bufferedDurationUs: Long,
        playbackSpeed: Float,
        rebuffering: Boolean,
        targetLiveOffsetUs: Long,
    ): Boolean = active().shouldStartPlayback(
        bufferedDurationUs,
        playbackSpeed,
        rebuffering,
        targetLiveOffsetUs,
    )
}
