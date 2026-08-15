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
import kotlinx.coroutines.launch

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
    /** 通知节流：同一文案不狂刷；切歌/播停仍立即更新 */
    private var lastNotifAtMs: Long = 0L
    private var lastNotifKey: String = ""
    private val throttledRefreshRunnable = Runnable {
        refreshNotification(force = true)
    }

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
                    refreshNotification(force = true)
                }

                override fun onMediaItemTransition(
                    mediaItem: androidx.media3.common.MediaItem?,
                    reason: Int,
                ) {
                    syncLikedFlag()
                    refreshNotification(force = true)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    // 缓冲等状态变化可节流，避免后台打游戏时通知狂刷
                    refreshNotification(force = false)
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
                    if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                        refreshNotification(force = true)
                    } else if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) {
                        refreshNotification(force = false)
                    }
                }
            }
            playerListener = listener
            exo.addListener(listener)
            refreshNotification(force = true)
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
            ACTION_LIKE -> runCatching {
                val cb = MadusApp.instance.onNotificationLike
                if (cb != null) {
                    cb()
                } else {
                    toggleLikeInService()
                }
            }
            ACTION_REFRESH -> { }
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
        refreshNotification(force = true)
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

    /**
     * @param force 切歌/播停必须立即刷；缓冲状态等可节流（后台打游戏更省）
     */
    private fun refreshNotification(force: Boolean = false) {
        val engine = runCatching { MadusApp.instance.playerEngine }.getOrNull()
        val st = engine?.state?.value
        val exo = engine?.player
        val title = st?.current?.title.orEmpty()
        val playing = st?.isPlaying == true || exo?.isPlaying == true
        val liked = MadusApp.instance.currentTrackLiked
        val key = "$title|$playing|${st?.isLoading == true}|$liked"
        val now = System.currentTimeMillis()
        val minGap = when {
            MadusApp.instance.appInBackground && MadusApp.instance.gameLiteMode -> 3_000L
            MadusApp.instance.appInBackground -> 2_000L
            MadusApp.instance.gameLiteMode -> 1_200L
            else -> 800L
        }
        if (force) {
            mainHandler.removeCallbacks(throttledRefreshRunnable)
        } else if (key == lastNotifKey && now - lastNotifAtMs < minGap) {
            return
        } else if (now - lastNotifAtMs < minGap) {
            // 合并短时间多次状态变化
            mainHandler.removeCallbacks(throttledRefreshRunnable)
            mainHandler.postDelayed(throttledRefreshRunnable, minGap - (now - lastNotifAtMs))
            return
        }
        lastNotifAtMs = now
        lastNotifKey = key
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
        val liked = MadusApp.instance.currentTrackLiked

        return buildMediaNotification(title = title, text = artist, isPlaying = isPlaying, liked = liked)
    }

    private fun buildMediaNotification(
        title: String,
        text: String,
        isPlaying: Boolean,
        liked: Boolean,
    ): Notification {
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
        val like = NotificationCompat.Action(
            if (liked) R.drawable.ic_notif_liked else R.drawable.ic_notif_like,
            if (liked) "取消喜欢" else "喜欢",
            serviceActionPi(ACTION_LIKE, 16),
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
            .addAction(like)

        // 折叠/锁屏三键：播放、下一首、喜欢。上一首在展开通知里。
        val style = MediaNotificationCompat.MediaStyle()
            .setShowActionsInCompactView(1, 2, 3)
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

    private fun syncLikedFlag() {
        val id = MadusApp.instance.playerEngine.state.value.current?.id ?: return
        MadusApp.instance.applicationScope.launch {
            MadusApp.instance.currentTrackLiked =
                runCatching { MadusApp.instance.likedStore.contains(id) }.getOrDefault(false)
            refreshNotification(force = true)
        }
    }

    private fun toggleLikeInService() {
        val track = MadusApp.instance.playerEngine.state.value.current ?: return
        MadusApp.instance.applicationScope.launch {
            val nowLiked = MadusApp.instance.likedStore.toggle(track)
            MadusApp.instance.currentTrackLiked = nowLiked
            if (nowLiked) {
                val p = com.madus.mobile.domain.ContentProfileParser.profileFromTrack(track)
                runCatching {
                    MadusApp.instance.recommendationEventStore.record(
                        com.madus.mobile.domain.RecommendationEvent(
                            trackId = track.id,
                            bvid = track.bvid,
                            type = com.madus.mobile.domain.RecommendationEventType.LIKE,
                            occurredAtMs = System.currentTimeMillis(),
                            sourceId = "recommend",
                            topicKeys = p.topicKeys,
                            authorKey = p.authorKey,
                        ),
                    )
                }
            }
            refreshNotification(force = true)
        }
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
        const val ACTION_LIKE = "com.madus.mobile.action.LIKE"
        const val ACTION_REFRESH = "com.madus.mobile.action.REFRESH"
    }
}
