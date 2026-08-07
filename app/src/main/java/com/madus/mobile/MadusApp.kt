package com.madus.mobile

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.madus.mobile.data.AudioQuality
import com.madus.mobile.data.BilibiliApi
import com.madus.mobile.data.LegalPrefs
import com.madus.mobile.data.LikedStore
import com.madus.mobile.data.LocalPlaylistStore
import com.madus.mobile.data.PlayerPrefs
import com.madus.mobile.data.PlaylistCoverStore
import com.madus.mobile.data.RecentStore
import com.madus.mobile.data.SessionStore
import com.madus.mobile.data.ThemePrefs
import com.madus.mobile.data.TrackCacheStore
import com.madus.mobile.ai.AiChatHistoryStore
import com.madus.mobile.ai.HummingConfigStore
import com.madus.mobile.ai.LlmConfigStore
import com.madus.mobile.player.PlaybackService
import com.madus.mobile.player.PlayerController
import com.madus.mobile.player.PlayerEngine
import com.madus.mobile.source.BilibiliSource
import com.madus.mobile.source.SourceRegistry
import com.madus.mobile.ui.LoginCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MadusApp : Application() {
    lateinit var playerEngine: PlayerEngine
        private set
    lateinit var playerController: PlayerController
        private set
    lateinit var sessionStore: SessionStore
        private set
    lateinit var localPlaylistStore: LocalPlaylistStore
        private set
    lateinit var recentStore: RecentStore
        private set
    lateinit var likedStore: LikedStore
        private set
    lateinit var playlistCoverStore: PlaylistCoverStore
        private set
    lateinit var themePrefs: ThemePrefs
        private set
    lateinit var playerPrefs: PlayerPrefs
        private set
    lateinit var legalPrefs: LegalPrefs
        private set
    lateinit var trackCacheStore: TrackCacheStore
        private set
    lateinit var biliApi: BilibiliApi
        private set
    lateinit var sourceRegistry: SourceRegistry
        private set
    lateinit var llmConfigStore: LlmConfigStore
        private set
    lateinit var hummingConfigStore: HummingConfigStore
        private set
    lateinit var aiChatHistoryStore: AiChatHistoryStore
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    var currentQualityQn: Int = AudioQuality.Standard.qn

    /** 设置里「视频模式」：开=看视频，关=听音乐 */
    @Volatile
    /** 默认音乐模式；DataStore 异步读入后会覆盖 */
    var videoModeEnabled: Boolean = false

    var startBiliLogin: (() -> Unit)? = null

    /**
     * 由 MainActivity 注入：Android 13+ 首次播放时申请通知权限，
     * 这样通知栏能显示曲名/控制（不像旧版空壳卡「准备播放」）。
     */
    var requestPostNotifications: (() -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        coil.Coil.setImageLoader(com.madus.mobile.ui.components.MadusImageLoader.get(this))
        playerEngine = PlayerEngine(this)
        playerController = PlayerController(playerEngine)
        sessionStore = SessionStore(this)
        localPlaylistStore = LocalPlaylistStore(this)
        recentStore = RecentStore(this)
        likedStore = LikedStore(this)
        playlistCoverStore = PlaylistCoverStore(this)
        themePrefs = ThemePrefs(this)
        playerPrefs = PlayerPrefs(this)
        legalPrefs = LegalPrefs(this)
        trackCacheStore = TrackCacheStore(this)
        llmConfigStore = LlmConfigStore(this)
        hummingConfigStore = HummingConfigStore(this)
        aiChatHistoryStore = AiChatHistoryStore(this)
        biliApi = BilibiliApi { sessionStore.getBiliCookie() }

        // 异步读音质/音效，避免主线程 runBlocking 卡启动
        appScope.launch {
            runCatching {
                val s = playerPrefs.flow.first()
                currentQualityQn = s.quality.qn
                videoModeEnabled = s.videoMode
                playerEngine.setSoundFx(s.soundFx)
                playerEngine.setAutoCache(s.autoCache)
            }
            playerPrefs.flow.collect { s ->
                currentQualityQn = s.quality.qn
                videoModeEnabled = s.videoMode
                playerEngine.setSoundFx(s.soundFx)
                playerEngine.setAutoCache(s.autoCache)
            }
        }

        sourceRegistry = SourceRegistry(
            listOf(
                BilibiliSource(
                    store = sessionStore,
                    api = biliApi,
                    loginUi = {
                        suspendCancellableCoroutine { cont ->
                            val deferred = LoginCoordinator.beginLogin()
                            deferred.invokeOnCompletion {
                                if (cont.isActive) cont.resume(runCatching { deferred.getCompleted() }.getOrNull())
                            }
                            cont.invokeOnCancellation { LoginCoordinator.complete(null) }
                            val starter = startBiliLogin
                            if (starter == null) LoginCoordinator.complete(null)
                            else starter.invoke()
                        }
                    },
                    qualityProvider = { currentQualityQn },
                    videoModeProvider = { videoModeEnabled },
                ),
            ),
        )
    }

    /**
     * 拉起媒体前台服务：熄屏/后台保持播歌与切歌取流 + 媒体通知。
     *
     * [PlaybackService] 在 onCreate 立刻 startForeground，避免
     * ForegroundServiceDidNotStartInTime 杀进程。
     * 同时在缺权限时触发一次通知权限申请（由 Activity 弹系统框）。
     */
    fun ensurePlaybackService() {
        maybeRequestNotificationPermission()
        runCatching {
            val intent = Intent(this, PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                @Suppress("DEPRECATION")
                startService(intent)
            }
        }.onFailure { e ->
            android.util.Log.w("MadusApp", "ensurePlaybackService failed: ${e.message}")
            runCatching { startService(Intent(this, PlaybackService::class.java)) }
        }
    }

    fun hasPostNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun maybeRequestNotificationPermission() {
        if (hasPostNotificationPermission()) return
        runCatching { requestPostNotifications?.invoke() }
    }

    companion object {
        lateinit var instance: MadusApp
            private set
    }
}
