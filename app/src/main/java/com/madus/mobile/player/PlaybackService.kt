package com.madus.mobile.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.madus.mobile.MadusApp
import com.madus.mobile.MainActivity
import com.madus.mobile.R

/**
 * 媒体前台服务：熄屏保活 + **可见**媒体通知（曲名/歌手 + 上/下首/播放暂停）。
 *
 * 与旧版「准备播放」卡死的区别：
 * - onCreate 立刻 startForeground（防系统杀进程）
 * - 标题永远优先用当前曲真实 metadata，禁止写死「准备播放」
 * - 起播/切歌后马上刷新通知
 * - 共享 Application ExoPlayer，onDestroy 不 release 播放器
 */
@UnstableApi
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var playerListener: Player.Listener? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        // 立刻进前台，文案用当前曲（若有），绝不用「准备播放」
        enterForeground(buildPlaybackNotification())

        try {
            val engine = MadusApp.instance.playerEngine
            val exo = engine.player
            val player = QueueForwardingPlayer(
                player = exo,
                onNext = { engine.requestExternalNext() },
                onPrevious = { engine.requestExternalPrevious() },
            )

            val sessionActivity = activityPendingIntent(requestCode = 0)
            mediaSession = MediaSession.Builder(this, player)
                .setId("madus_main")
                .setSessionActivity(sessionActivity)
                .build()

            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    refreshNotification()
                }

                override fun onMediaItemTransition(
                    mediaItem: androidx.media3.common.MediaItem?,
                    reason: Int,
                ) {
                    refreshNotification()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    refreshNotification()
                    if (playbackState == Player.STATE_IDLE && exo.mediaItemCount == 0) {
                        mainHandler.postDelayed({
                            if (exo.playbackState == Player.STATE_IDLE && exo.mediaItemCount == 0) {
                                stopForegroundCompat()
                                stopSelf()
                            }
                        }, 3_000L)
                    }
                }

                override fun onEvents(player: Player, events: Player.Events) {
                    if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED) ||
                        events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)
                    ) {
                        refreshNotification()
                    }
                }
            }
            playerListener = listener
            exo.addListener(listener)
            refreshNotification()
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "PlaybackService onCreate failed", t)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> runCatching {
                MadusApp.instance.playerEngine.player.play()
            }
            ACTION_PAUSE -> runCatching {
                MadusApp.instance.playerEngine.player.pause()
            }
            ACTION_TOGGLE -> runCatching {
                val p = MadusApp.instance.playerEngine.player
                if (p.isPlaying) p.pause() else p.play()
            }
            ACTION_NEXT -> runCatching {
                MadusApp.instance.playerEngine.requestExternalNext()
            }
            ACTION_PREV -> runCatching {
                MadusApp.instance.playerEngine.requestExternalPrevious()
            }
        }
        enterForeground(buildPlaybackNotification())
        return try {
            super.onStartCommand(intent, flags, startId)
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "onStartCommand", t)
            START_STICKY
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        // 自管通知（曲名真实 + 按钮），避免 Media3 默认空标题卡「准备中」类文案
        enterForeground(buildPlaybackNotification())
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = runCatching { MadusApp.instance.playerEngine.player }.getOrNull()
        val keep = p != null && (p.isPlaying || p.playWhenReady)
        if (!keep) {
            stopForegroundCompat()
            stopSelf()
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        playerListener?.let { listener ->
            runCatching { MadusApp.instance.playerEngine.player.removeListener(listener) }
        }
        playerListener = null
        mediaSession?.run { runCatching { release() } }
        mediaSession = null
        super.onDestroy()
    }

    private fun refreshNotification() {
        enterForeground(buildPlaybackNotification())
    }

    /**
     * 标题优先级：引擎状态曲目 → MediaItem metadata → 「Madus」
     * **禁止**使用「准备播放」等占位导致永久卡住的旧文案。
     */
    private fun buildPlaybackNotification(): Notification {
        val engine = runCatching { MadusApp.instance.playerEngine }.getOrNull()
        val state = engine?.state?.value
        val exo = engine?.player
        val mediaMeta = exo?.mediaMetadata
        val mediaTitle = mediaMeta?.title?.toString()?.takeIf { it.isNotBlank() }
        val mediaArtist = mediaMeta?.artist?.toString()?.takeIf { it.isNotBlank() }

        val title = state?.current?.title?.takeIf { it.isNotBlank() }
            ?: mediaTitle
            ?: "Madus"
        val artist = state?.current?.artist?.takeIf { it.isNotBlank() }
            ?: mediaArtist
            ?: when {
                state?.isLoading == true || exo?.playbackState == Player.STATE_BUFFERING -> "缓冲中…"
                state?.isPlaying == true || exo?.isPlaying == true -> "正在播放"
                else -> "已暂停"
            }
        val isPlaying = state?.isPlaying == true || exo?.isPlaying == true

        return buildMediaNotification(title = title, text = artist, isPlaying = isPlaying)
    }

    private fun buildMediaNotification(title: String, text: String, isPlaying: Boolean): Notification {
        val contentPi = activityPendingIntent(requestCode = 1)

        val prev = NotificationCompat.Action(
            R.drawable.ic_notif_prev,
            "上一首",
            serviceActionPi(ACTION_PREV, 10),
        )
        val playPause = if (isPlaying) {
            NotificationCompat.Action(
                R.drawable.ic_notif_pause,
                "暂停",
                serviceActionPi(ACTION_PAUSE, 11),
            )
        } else {
            NotificationCompat.Action(
                R.drawable.ic_notif_play,
                "播放",
                serviceActionPi(ACTION_PLAY, 12),
            )
        }
        val next = NotificationCompat.Action(
            R.drawable.ic_notif_next,
            "下一首",
            serviceActionPi(ACTION_NEXT, 13),
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText("Madus")
            .setContentIntent(contentPi)
            .setDeleteIntent(serviceActionPi(ACTION_PAUSE, 14))
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(prev)
            .addAction(playPause)
            .addAction(next)

        // MediaStyle：系统媒体样式 + 紧凑三键；挂上 Session 后锁屏/磁贴也能控
        val style = MediaNotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
            .setShowCancelButton(true)
            .setCancelButtonIntent(serviceActionPi(ACTION_PAUSE, 15))
        mediaSession?.let { session ->
            runCatching {
                style.setMediaSession(session.sessionCompatToken)
            }
        }
        builder.setStyle(style)

        return builder.build()
    }

    private fun activityPendingIntent(requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            this,
            requestCode,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun serviceActionPi(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        // 若旧版 LOW 通道已存在，重建为 DEFAULT，保证能显示在通知栏
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null && existing.importance < NotificationManager.IMPORTANCE_DEFAULT) {
            nm.deleteNotificationChannel(CHANNEL_ID)
        }
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.playback_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "显示正在播放的歌曲，可暂停/切歌"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(false)
        }
        nm.createNotificationChannel(ch)
    }

    private fun enterForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                @Suppress("DEPRECATION")
                startForeground(NOTIF_ID, notification)
            }
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "startForeground typed failed, fallback", t)
            runCatching {
                @Suppress("DEPRECATION")
                startForeground(NOTIF_ID, notification)
            }
        }
    }

    private fun stopForegroundCompat() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
    }

    companion object {
        private const val TAG = "PlaybackService"
        private const val CHANNEL_ID = "madus_playback"
        private const val NOTIF_ID = 0x4D445301

        const val ACTION_PLAY = "com.madus.mobile.action.PLAY"
        const val ACTION_PAUSE = "com.madus.mobile.action.PAUSE"
        const val ACTION_TOGGLE = "com.madus.mobile.action.TOGGLE"
        const val ACTION_NEXT = "com.madus.mobile.action.NEXT"
        const val ACTION_PREV = "com.madus.mobile.action.PREV"
    }
}
