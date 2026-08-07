package com.madus.mobile.player

import androidx.media3.common.PlaybackException
import com.madus.mobile.data.SoundFx
import com.madus.mobile.domain.PlaybackState
import com.madus.mobile.domain.PlayerCommand
import com.madus.mobile.domain.RepeatMode
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin facade kept for ViewModel call sites.
 * Backed by [PlayerEngine] (ExoPlayer) from Application.
 */
class PlayerController(
    private val engine: PlayerEngine,
) {
    val state: StateFlow<PlaybackState> = engine.state
    val sleepRemainingMs: StateFlow<Long> = engine.sleepRemainingMs

    fun dispatch(command: PlayerCommand) = engine.dispatch(command)

    fun setRepeat(mode: RepeatMode) = engine.setRepeat(mode)

    fun setShuffle(enabled: Boolean) = engine.setShuffle(enabled)

    fun setSoundFx(fx: SoundFx) = engine.setSoundFx(fx)

    fun setAutoCache(enabled: Boolean) = engine.setAutoCache(enabled)

    fun setGameMixAudio(enabled: Boolean) = engine.setGameMixAudio(enabled)

    fun setAppInBackground(inBackground: Boolean) = engine.setAppInBackground(inBackground)

    fun setSleepTimerMinutes(minutes: Int) = engine.setSleepTimerMinutes(minutes)

    fun setPlaybackSpeed(speed: Float) = engine.setPlaybackSpeed(speed)

    fun playbackSpeed(): Float = engine.playbackSpeed()

    fun prepareTrack(track: com.madus.mobile.domain.Track, asVideo: Boolean = false) =
        engine.prepareTrack(track, asVideo)

    fun setOnPlaybackEnded(cb: (() -> Unit)?) {
        engine.onPlaybackEnded = cb
    }

    fun setOnExternalNext(cb: (() -> Unit)?) {
        engine.onExternalNext = cb
    }

    fun setOnExternalPrevious(cb: (() -> Unit)?) {
        engine.onExternalPrevious = cb
    }

    fun setOnPlayerError(cb: ((PlaybackException) -> Unit)?) {
        engine.onPlayerError = cb
    }
}
