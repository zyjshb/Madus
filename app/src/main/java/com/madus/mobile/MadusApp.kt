package com.madus.mobile

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.madus.mobile.data.AudioQuality
import com.madus.mobile.data.BilibiliApi
import com.madus.mobile.data.ContentProfileStore
import com.madus.mobile.data.LegalPrefs
import com.madus.mobile.data.LikedStore
import com.madus.mobile.data.LocalPlaylistStore
import com.madus.mobile.data.PlayerPrefs
import com.madus.mobile.data.PlaylistCoverStore
import com.madus.mobile.data.RecommendationEventStore
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
    lateinit var recommendationEventStore: RecommendationEventStore
        private set
    lateinit var contentProfileStore: ContentProfileStore
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

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    var currentQualityQn: Int = AudioQuality.High.qn

    /**
     * 整个 App 是否在后台（用户在打游戏/其它应用）。
     * 仅供后台降载判断，**不改变**播放与功能可用性。
     */
    @Volatile
    var appInBackground: Boolean = false
        private set

    /**
     * 游戏轻量档（设置项，与网络档「最省」同步）。
     */
    @Volatile
    var gameLiteMode: Boolean = false
        private set

    /**
     * 网络使用强度（预取/续刷/写盘）。默认均衡。
     */
    @Volatile
    var networkIntensity: com.madus.mobile.data.NetworkIntensity =
        com.madus.mobile.data.NetworkIntensity.BALANCED
        private set

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

    /** 通知栏点赞：由 ViewModel 注入，服务里直接调。 */
    var onNotificationLike: (() -> Unit)? = null

    /** 当前曲是否已喜欢，给通知栏爱心图标用。 */
    @Volatile
    var currentTrackLiked: Boolean = false

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
        recommendationEventStore = RecommendationEventStore(this)
        contentProfileStore = ContentProfileStore(this)
        llmConfigStore = LlmConfigStore(this)
        hummingConfigStore = HummingConfigStore(this)
        aiChatHistoryStore = AiChatHistoryStore(this)
        biliApi = BilibiliApi { sessionStore.getBiliCookie() }

        // 异步读音质/音效，避免主线程 runBlocking 卡启动
        applicationScope.launch {
            // 升级安装后清掉应用内下载的 APK，省空间
            runCatching {
                com.madus.mobile.data.AppUpdate.cleanupDownloadedApks(this@MadusApp)
            }
            runCatching {
                playerPrefs.migrateListenDefaults()
                val s = playerPrefs.flow.first()
                currentQualityQn = s.quality.qn
                videoModeEnabled = s.videoMode
                playerEngine.setSoundFx(s.soundFx)
                playerEngine.setAutoCache(s.autoCache)
                playerEngine.setGameMixAudio(s.gameMixAudio)
                playerEngine.setGameLiteMode(s.gameLiteMode)
                playerEngine.setNetworkIntensity(s.networkIntensity)
                gameLiteMode = s.gameLiteMode
                networkIntensity = s.networkIntensity
            }
            playerPrefs.flow.collect { s ->
                currentQualityQn = s.quality.qn
                videoModeEnabled = s.videoMode
                playerEngine.setSoundFx(s.soundFx)
                playerEngine.setAutoCache(s.autoCache)
                playerEngine.setGameMixAudio(s.gameMixAudio)
                playerEngine.setGameLiteMode(s.gameLiteMode)
                playerEngine.setNetworkIntensity(s.networkIntensity)
                gameLiteMode = s.gameLiteMode
                networkIntensity = s.networkIntensity
            }
        }

        // 进游戏/切到其它 App：后台降载（进度刷新、预取强度、通知节流、图片内存），不改播放与功能
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                appInBackground = false
                playerEngine.setAppInBackground(false)
            }

            override fun onStop(owner: LifecycleOwner) {
                appInBackground = true
                playerEngine.setAppInBackground(true)
                // 释放封面解码缓存，给游戏让出一点内存；磁盘缓存与功能不动
                runCatching {
                    com.madus.mobile.ui.components.MadusImageLoader
                        .get(this@MadusApp)
                        .memoryCache
                        ?.clear()
                }
            }
        })

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
