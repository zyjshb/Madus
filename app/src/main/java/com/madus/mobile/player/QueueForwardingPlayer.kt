package com.madus.mobile.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/**
 * 引擎侧队列只有当前曲时，系统/锁屏的 next/prev 仍能切到 App 队列。
 */
class QueueForwardingPlayer(
    player: Player,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
) : ForwardingPlayer(player) {

    override fun hasNextMediaItem(): Boolean = true

    override fun hasPreviousMediaItem(): Boolean = true

    override fun seekToNext() {
        onNext()
    }

    override fun seekToNextMediaItem() {
        onNext()
    }

    override fun seekToPrevious() {
        if (currentPosition > 3_000) {
            seekTo(0)
        } else {
            onPrevious()
        }
    }

    override fun seekToPreviousMediaItem() {
        onPrevious()
    }

    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands()
            .buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()
    }
}
