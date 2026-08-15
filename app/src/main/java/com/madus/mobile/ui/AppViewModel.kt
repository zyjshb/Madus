package com.madus.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.madus.mobile.MadusApp
import com.madus.mobile.ai.SongCandidate
import com.madus.mobile.data.AudioQuality
import com.madus.mobile.data.BilibiliApi
import com.madus.mobile.data.ContentProfileStore
import com.madus.mobile.data.ExternalPlaylistImporter
import com.madus.mobile.data.LikedStore
import com.madus.mobile.data.LocalPlaylistStore
import com.madus.mobile.data.PlayerPrefs
import com.madus.mobile.data.PlayerSettings
import com.madus.mobile.data.PlaylistCoverStore
import com.madus.mobile.data.RecommendationEventStore
import com.madus.mobile.data.RecentStore
import com.madus.mobile.data.SoundFx
import com.madus.mobile.data.TrackCacheStore
import com.madus.mobile.domain.AuthSession
import com.madus.mobile.domain.ContentProfile
import com.madus.mobile.domain.ContentProfileParser
import com.madus.mobile.domain.FeedContext
import com.madus.mobile.domain.InterestState
import com.madus.mobile.domain.LyricsUiState
import com.madus.mobile.domain.MusicSourceType
import com.madus.mobile.domain.PlaybackState
import com.madus.mobile.domain.PlayerCommand
import com.madus.mobile.domain.Playlist
import com.madus.mobile.domain.RecommendationEngine
import com.madus.mobile.domain.RecommendationEvent
import com.madus.mobile.domain.RecommendationEventType
import com.madus.mobile.domain.RecommendationReRanker
import com.madus.mobile.domain.RecommendationTuning
import com.madus.mobile.domain.RepeatMode
import com.madus.mobile.domain.ScoredTrack
import com.madus.mobile.domain.Track
import com.madus.mobile.domain.TrackFilters
import com.madus.mobile.player.PlayerController
import com.madus.mobile.player.StreamCache
import com.madus.mobile.source.MusicSource
import com.madus.mobile.source.SourceRegistry
import com.madus.mobile.ui.components.PlaySourceItem
import com.madus.mobile.ui.components.PlaySourceKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

data class HomeUiState(
    val greeting: String = "今天想听什么",
    val playlists: List<Playlist> = emptyList(),
    /**
     * 我的歌单区：固定「我的喜欢」在前 + 有曲的本地歌单。
     * 我的喜欢始终展示（可 0 首）。
     */
    val localPlaylists: List<Playlist> = emptyList(),
    val recent: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
    /** B 站头像，同步首页顶栏 */
    val avatarUrl: String? = null,
    val biliLoggedIn: Boolean = false,
)

/** Play mode aligned with desktop cyclePlayMode */
enum class PlayModeLabel(val label: String) {
    LOOP("顺序循环"),
    SHUFFLE("随机"),
    SINGLE("单曲循环"),
}

data class SearchUiState(
    val query: String = "",
    val results: List<Track> = emptyList(),
    /** 输入联想（B 站 suggest） */
    val suggestions: List<String> = emptyList(),
    val isSearching: Boolean = false,
    val message: String? = null,
    /** 当前已加载到第几页（B 站搜索） */
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val total: Int = 0,
    /** 实际请求用的关键词（纠错后可能与输入框不同） */
    val keywordUsed: String = "",
)

/** 队列页内搜索（插播，不顶掉原歌单） */
data class QueueSearchUiState(
    val query: String = "",
    val results: List<Track> = emptyList(),
    val isSearching: Boolean = false,
    val message: String? = null,
)

data class LibraryUiState(
    val liked: Playlist? = null,
    /** 本地歌单（不含「我的喜欢」），含空歌单便于管理 */
    val localPlaylists: List<Playlist> = emptyList(),
    val biliPlaylists: List<Playlist> = emptyList(),
    val recent: List<Track> = emptyList(),
    val biliLoggedIn: Boolean = false,
    val offlineCount: Int = 0,
    val isLoading: Boolean = false,
)

enum class RecommendSegment { Feed, Recent }

data class RecommendUiState(
    val feed: List<Track> = emptyList(),
    val recent: List<Track> = emptyList(),
    val segment: RecommendSegment = RecommendSegment.Feed,
    val likedIds: Set<String> = emptySet(),
    /** 默认关：避免一进推荐就播演示/陌生曲 */
    val autoPlayOnEnter: Boolean = false,
    val isLoading: Boolean = false,
    /** 已生成推荐但播放器尚未挂上当前曲；短暂隐藏“播放”按钮，超时自动恢复 */
    val isStartingPlayback: Boolean = false,
    /** Label of current play context (推荐 / 歌单名). */
    val sourceLabel: String = "推荐电台",
    val sourceId: String = "recommend",
    val debugRows: List<String> = emptyList(),
    val debugVisible: Boolean = false,
)

data class PlaySourceUiState(
    val visible: Boolean = false,
    val sources: List<PlaySourceItem> = emptyList(),
)

data class MeUiState(
    val appVersion: String = com.madus.mobile.BuildConfig.VERSION_NAME,
    val sessions: List<AuthSession> = emptyList(),
    val designNote: String = "",
    val autoPlayOnEnterRecommend: Boolean = false,
    val likedCount: Int = 0,
    val playlistCount: Int = 0,
    val recentCount: Int = 0,
    val cacheSizeLabel: String = "0B",
    val offlineCount: Int = 0,
)

data class CommentsUiState(
    val visible: Boolean = false,
    val track: Track? = null,
    val comments: List<BilibiliApi.Comment> = emptyList(),
    val isLoading: Boolean = false,
    val loadingMore: Boolean = false,
    /** 正在展开楼中楼的根 rpid */
    val loadingRepliesRoot: String? = null,
    val page: Int = 1,
    val hasMore: Boolean = false,
    val nextCursor: Long = 0L,
    val usedMainApi: Boolean = false,
    val total: Int = 0,
    val draft: String = "",
    val error: String? = null,
    val posting: Boolean = false,
    /** 当前是否持有 B 站登录 Cookie（含 WebView） */
    val loggedIn: Boolean = false,
    /** 回复目标：null = 发一级评论 */
    val replyTo: BilibiliApi.Comment? = null,
    val replyRootRpid: String = "0",
    val replyParentRpid: String = "0",
    val replyHint: String = "",
)

data class CacheManagerUiState(
    val items: List<TrackCacheStore.CachedTrack> = emptyList(),
    val streamCacheLabel: String = "0B",
    val totalLabel: String = "0B",
    val isLoading: Boolean = false,
)

/** B 站收藏夹选项（同步目标） */
data class BiliFavOption(
    val id: String,
    val title: String,
    val count: Int = 0,
)

enum class CollectTab { Local, Bili }

/**
 * 推荐页「B站识曲」轻量状态（官方 BGM 标签，非 LLM）。
 */
data class BiliRecognizeUiState(
    val panelVisible: Boolean = false,
    val loading: Boolean = false,
    val sourceTrackId: String? = null,
    val sourceTitle: String? = null,
    val guessLabel: String? = null,
    val tracks: List<Track> = emptyList(),
    val error: String? = null,
)

/** 识别本片 BGM 面板状态（关闭只藏 UI，结果保留） */
data class BgmUiState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val status: String? = null,
    val error: String? = null,
    /** 对应识别的那首当前曲 id，用于复用结果 / 防重复截取 */
    val sourceTrackId: String? = null,
    val sourceTitle: String? = null,
    val reply: String = "",
    val guessLabel: String = "",
    val candidates: List<com.madus.mobile.ai.SongCandidate> = emptyList(),
    val tracks: List<Track> = emptyList(),
    /** 当前用于 BGM 的模型展示名 */
    val modelLabel: String = "",
    /** 外语 BGM：不强制华语歌名（默认关） */
    val preferForeignSong: Boolean = false,
) {
    val hasContent: Boolean
        get() = candidates.isNotEmpty() || tracks.isNotEmpty()
}

/** 收藏 sheet：本地歌单 / B 站收藏 两个分页 */
data class CollectUiState(
    val visible: Boolean = false,
    val track: Track? = null,
    val playlists: List<Playlist> = emptyList(),
    val toast: String? = null,
    val tab: CollectTab = CollectTab.Local,
    val biliLoggedIn: Boolean = false,
    val biliFolders: List<BiliFavOption> = emptyList(),
    val selectedBiliFolderId: String? = null,
    val biliSyncing: Boolean = false,
    /** 展开后的合集集数；>1 时显示「整部合集」 */
    val seriesCount: Int = 1,
)

data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val loadingMore: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = false,
    val total: Int = 0,
    val error: String? = null,
    /** 每次 openPlaylist 递增；用于进页后屏蔽误触发的播放 */
    val openGeneration: Int = 0,
)

data class UpSpaceUiState(
    val mid: String = "",
    val profile: BilibiliApi.UpProfile? = null,
    val videos: List<Track> = emptyList(),
    val seasons: List<BilibiliApi.UpSeason> = emptyList(),
    val seasonTracks: List<Track> = emptyList(),
    val selectedSeason: BilibiliApi.UpSeason? = null,
    val tab: UpSpaceTab = UpSpaceTab.Videos,
    val isLoading: Boolean = false,
    val loadingMore: Boolean = false,
    val followBusy: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = false,
    /** 投稿总数（小说式翻页展示用） */
    val total: Int = 0,
    val error: String? = null,
)

enum class UpSpaceTab { Videos, Seasons }

data class ImportPlaylistUiState(
    val visible: Boolean = false,
    val input: String = "",
    val isWorking: Boolean = false,
    val progress: String = "",
    val result: String? = null,
    val error: String? = null,
    /** 还有未导入曲目，可点「继续添加」 */
    val canContinue: Boolean = false,
    /** 还剩多少首未匹配 */
    val remaining: Int = 0,
    /** 原歌单总曲目 */
    val totalSongs: Int = 0,
    /** 已累计命中写入的数量 */
    val importedCount: Int = 0,
    val playlistTitle: String = "",
)

/**
 * 换歌模式：导入/队列里听错了，去搜索选一首替换。
 * [playlistId] 为 local-* 时会同步写回本地歌单。
 */
data class TrackReplaceUiState(
    val active: Boolean = false,
    val oldTrackId: String = "",
    val oldTitle: String = "",
    val playlistId: String? = null,
    val queryHint: String = "",
)

class AppViewModel(
    private val registry: SourceRegistry = MadusApp.instance.sourceRegistry,
    private val player: PlayerController = MadusApp.instance.playerController,
    private val localPl: LocalPlaylistStore = MadusApp.instance.localPlaylistStore,
    private val recentStore: RecentStore = MadusApp.instance.recentStore,
    private val likedStore: LikedStore = MadusApp.instance.likedStore,
    private val coverStore: PlaylistCoverStore = MadusApp.instance.playlistCoverStore,
    private val playerPrefs: PlayerPrefs = MadusApp.instance.playerPrefs,
    private val trackCache: TrackCacheStore = MadusApp.instance.trackCacheStore,
    private val biliApi: BilibiliApi = MadusApp.instance.biliApi,
    private val recommendationEventStore: RecommendationEventStore =
        MadusApp.instance.recommendationEventStore,
    private val contentProfileStore: ContentProfileStore =
        MadusApp.instance.contentProfileStore,
) : ViewModel() {

    private val recommendationEngine = RecommendationEngine()
    private val recommendationReRanker = RecommendationReRanker()

    val playback: StateFlow<PlaybackState> = player.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlaybackState())

    val sleepRemainingMs: StateFlow<Long> = player.sleepRemainingMs

    val playerSettings: StateFlow<PlayerSettings> = playerPrefs.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlayerSettings())

    private val _home = MutableStateFlow(HomeUiState(isLoading = true))
    val home: StateFlow<HomeUiState> = _home.asStateFlow()

    private val _search = MutableStateFlow(SearchUiState())
    val search: StateFlow<SearchUiState> = _search.asStateFlow()

    private val _queueSearch = MutableStateFlow(QueueSearchUiState())
    val queueSearch: StateFlow<QueueSearchUiState> = _queueSearch.asStateFlow()

    private val _library = MutableStateFlow(LibraryUiState())
    val library: StateFlow<LibraryUiState> = _library.asStateFlow()

    private val _recommend = MutableStateFlow(RecommendUiState(isLoading = true))
    val recommend: StateFlow<RecommendUiState> = _recommend.asStateFlow()

    private val _lyrics = MutableStateFlow(LyricsUiState())
    val lyrics: StateFlow<LyricsUiState> = _lyrics.asStateFlow()
    private var lyricsJob: Job? = null

    private val _me = MutableStateFlow(MeUiState())
    val me: StateFlow<MeUiState> = _me.asStateFlow()

    private val _playlistDetail = MutableStateFlow(PlaylistDetailUiState())
    val playlistDetail: StateFlow<PlaylistDetailUiState> = _playlistDetail.asStateFlow()
    /** 串行化打开/关闭，避免二次进入被旧协程写空 */
    private var playlistOpenJob: Job? = null
    private var playlistOpenSeq: Int = 0
    /** 打开详情后短时间内禁止「播放全部/点曲」真正开播 */
    private var playlistPlayUnlockAtMs: Long = 0L
    private var playlistPlayJob: Job? = null
    /**
     * 仅「用户点了播放全部/某首歌」时置位的一次性令牌。
     * 跳推荐台必须 consume 成功；打开封面进详情时绝不能带此令牌。
     */
    private var playlistExplicitPlayToken: Long = 0L
    /** 打开详情后一段时间内禁止任何「进推荐台」导航（除非持有显式播放令牌） */
    private var blockRecommendNavUntilMs: Long = 0L

    /** In-memory recent (mirrors RecentStore, newest last for push, UI reverses). */
    private val sessionRecent = mutableListOf<Track>()
    /** Unresolved queue for fast first-play; resolve on demand for next. */
    private var pendingQueue: List<Track> = emptyList()
    private var pendingIndex: Int = 0

    /** UI-facing play queue (search/playlist/recommend). */
    private val _queueTracks = MutableStateFlow<List<Track>>(emptyList())
    val queueTracks: StateFlow<List<Track>> = _queueTracks.asStateFlow()

    private val _playMode = MutableStateFlow(PlayModeLabel.LOOP)
    val playMode: StateFlow<PlayModeLabel> = _playMode.asStateFlow()

    private val _collect = MutableStateFlow(CollectUiState())
    val collect: StateFlow<CollectUiState> = _collect.asStateFlow()

    private val bgmPrefs get() = MadusApp.instance.aiChatHistoryStore
    private val _bgm = MutableStateFlow(
        BgmUiState(preferForeignSong = MadusApp.instance.aiChatHistoryStore.isBgmPreferForeign()),
    )
    val bgm: StateFlow<BgmUiState> = _bgm.asStateFlow()
    private var bgmJob: Job? = null

    private val _playSource = MutableStateFlow(PlaySourceUiState())
    val playSource: StateFlow<PlaySourceUiState> = _playSource.asStateFlow()

    private val _comments = MutableStateFlow(CommentsUiState())
    val comments: StateFlow<CommentsUiState> = _comments.asStateFlow()

    private val _upSpace = MutableStateFlow(UpSpaceUiState())
    val upSpace: StateFlow<UpSpaceUiState> = _upSpace.asStateFlow()

    private val _importPlaylist = MutableStateFlow(ImportPlaylistUiState())
    val importPlaylist: StateFlow<ImportPlaylistUiState> = _importPlaylist.asStateFlow()

    private val _trackReplace = MutableStateFlow(TrackReplaceUiState())
    val trackReplace: StateFlow<TrackReplaceUiState> = _trackReplace.asStateFlow()

    private val _biliRecognize = MutableStateFlow(BiliRecognizeUiState())
    val biliRecognize: StateFlow<BiliRecognizeUiState> = _biliRecognize.asStateFlow()
    private var biliRecognizeJob: Job? = null

    private val externalImporter by lazy { ExternalPlaylistImporter(biliApi) }

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    /**
     * 超长曲（循环 BGM 等）听太久时的轻提示，非阻塞。
     * null = 不显示。
     */
    private val _longPlayHint = MutableStateFlow<String?>(null)
    val longPlayHint: StateFlow<String?> = _longPlayHint.asStateFlow()
    private val longPlayDismissedIds = mutableSetOf<String>()
    private var longPlayHintTrackId: String = ""

    private var positionPersistJob: Job? = null

    /**
     * 用户刚点了某首歌（歌单/搜索）→ 进推荐页时禁止 onEnterRecommend 再自动开播演示流，
     * 否则会盖掉用户点的那首。
     */
    @Volatile
    private var suppressRecommendAutoPlay: Boolean = false

    /** 同一曲连续失败次数，避免死循环重解析 */
    private var playErrorRetries: Int = 0
    private var lastErrorTrackId: String? = null
    @Volatile private var handlingPlayError: Boolean = false

    /**
     * 会话内观看进度（上下滑切视频用）。比 DataStore 更快，离开页/切歌时写入，
     * 再滑回来从上次位置续播，而不是每次从 0。
     */
    private val sessionPositions = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** 本会话已曝光过的 id，用于无限流去重（抖音式不回看） */
    private val sessionSeenIds = linkedSetOf<String>()
    /** 本会话真正起播过的 id，用于快速跳过判定（重复播放不算负反馈） */
    private val sessionStartedIds = linkedSetOf<String>()
    /** 本会话重播过的 id：首次播放切走可记 SKIP_FAST，重播后不记 */
    private val sessionReplayedIds = linkedSetOf<String>()
    /** 主题 -> 最近 10 分钟实时插入时间戳，用于同主题插入配额 */
    private val realtimeInsertedTopics = mutableMapOf<String, MutableList<Long>>()
    /** 已触发过半/九成观看事件的 id，避免重复写 */
    private val watchFired50 = linkedSetOf<String>()
    private val watchFired90 = linkedSetOf<String>()
    @Volatile private var expandingFeed: Boolean = false
    private var expandJob: Job? = null
    /** 「为你推荐」起播单飞：避免连点重建 feed、按钮二次闪现 */
    private var recommendStartJob: Job? = null
    private var recommendClearLoadingJob: Job? = null

    init {
        // 曲终 → 下一首
        player.setOnPlaybackEnded {
            playErrorRetries = 0
            viewModelScope.launch { advanceToNext(userInitiated = false) }
        }
        // 锁屏 / 通知栏 next/prev
        player.setOnExternalNext {
            viewModelScope.launch { advanceToNext(userInitiated = true) }
        }
        player.setOnExternalPrevious {
            previous()
        }
        // 播放失败：重解析过期 CDN，再失败则跳过（防抖，避免连环崩溃）
        player.setOnPlayerError {
            viewModelScope.launch {
                if (handlingPlayError) return@launch
                handlingPlayError = true
                try {
                    handlePlaybackError()
                } finally {
                    delay(800)
                    handlingPlayError = false
                }
            }
        }
        refreshSessions()
        viewModelScope.launch {
            runCatching { localPl.purgeEmptyJunk(aggressive = true) }
            loadRecentFromStore()
            runCatching { recommendationEventStore.clearExpired() }
            runCatching { contentProfileStore.removeExpired() }
            runCatching {
                val ids = likedStore.ids()
                _recommend.update { it.copy(likedIds = ids) }
            }
            refreshHome()
            loadRecommendFeed(autoStart = false)
            refreshCacheStats()
        }
        startPositionPersistLoop()
        startRecommendationSignalLoop()
        MadusApp.instance.onNotificationLike = ::toggleLikeCurrent
    }

    fun clearToast() {
        _toast.value = null
    }

    fun dismissLongPlayHint() {
        val id = playback.value.current?.id
        if (!id.isNullOrBlank()) longPlayDismissedIds.add(id)
        _longPlayHint.value = null
    }

    /** 同一曲连续听够久则提示可换歌（长循环 BGM） */
    private fun maybeShowLongPlayHint() {
        val pb = playback.value
        val t = pb.current ?: run {
            _longPlayHint.value = null
            return
        }
        if (!pb.isPlaying) return
        if (t.id != longPlayHintTrackId) {
            longPlayHintTrackId = t.id
            // 换曲后清掉提示（保留 dismissed 集合，避免同一曲反复刷）
            if (_longPlayHint.value != null) _longPlayHint.value = null
        }
        if (t.id in longPlayDismissedIds) return
        if (_longPlayHint.value != null) return
        val pos = pb.positionMs
        // 同一曲连续听约 5 分钟提示可换歌（长循环 BGM 等）
        if (pos >= 5L * 60_000L) {
            _longPlayHint.value = "已经听了一会儿了，可以换首歌"
        }
    }

    fun setQuality(q: AudioQuality) {
        viewModelScope.launch {
            playerPrefs.setQuality(q)
            MadusApp.instance.currentQualityQn = q.qn
            _toast.value = "画质/音质：${q.label}（下一首生效）"
        }
    }

    /** 播放倍速：1=正常，2=两倍；UI 长按用 */
    fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
    }

    fun currentPlaybackSpeed(): Float = player.playbackSpeed()

    /**
     * 静默开启视频模式（搜索点播 / 清屏前），不弹 toast、不强制重解当前进度。
     * 必须在 resolveStream 之前同步写 [MadusApp.videoModeEnabled]。
     */
    fun ensureVideoModeEnabled() {
        if (MadusApp.instance.videoModeEnabled) return
        MadusApp.instance.videoModeEnabled = true
        viewModelScope.launch {
            runCatching { playerPrefs.setVideoMode(true) }
        }
    }

    fun setSoundFx(fx: SoundFx) {
        viewModelScope.launch {
            playerPrefs.setSoundFx(fx)
            player.setSoundFx(fx)
            _toast.value = "音效：${fx.label}"
        }
    }

    fun cycleSoundFx() {
        viewModelScope.launch {
            val cur = playerPrefs.flow.first().soundFx
            val all = SoundFx.entries
            val next = all[(all.indexOf(cur) + 1).mod(all.size)]
            playerPrefs.setSoundFx(next)
            player.setSoundFx(next)
            _toast.value = "音效：${next.label}"
        }
    }

    fun setAutoCache(enabled: Boolean) {
        viewModelScope.launch {
            playerPrefs.setAutoCache(enabled)
            player.setAutoCache(enabled)
            _toast.value = if (enabled) "已开启边听缓存" else "已关闭边听缓存 · 纯在线播放"
        }
    }

    fun setGameMixAudio(enabled: Boolean) {
        viewModelScope.launch {
            playerPrefs.setGameMixAudio(enabled)
            player.setGameMixAudio(enabled)
            _toast.value = if (enabled) {
                "打游戏时继续播放 · 已开"
            } else {
                "打游戏时继续播放 · 已关（会按系统音频焦点暂停）"
            }
        }
    }

    fun setGameLiteMode(enabled: Boolean) {
        viewModelScope.launch {
            playerPrefs.setGameLiteMode(enabled)
            player.setGameLiteMode(enabled)
            if (enabled) {
                player.setNetworkIntensity(com.madus.mobile.data.NetworkIntensity.MINIMAL)
            }
            _toast.value = if (enabled) {
                "网络已切到「最省」"
            } else {
                "游戏轻量 · 已关"
            }
        }
    }

    fun setNetworkIntensity(level: com.madus.mobile.data.NetworkIntensity) {
        viewModelScope.launch {
            playerPrefs.setNetworkIntensity(level)
            player.setNetworkIntensity(level)
            player.setGameLiteMode(level == com.madus.mobile.data.NetworkIntensity.MINIMAL)
            _toast.value = "网络使用 · ${level.label}"
        }
    }

    /**
     * 视频模式开关：开=看 B 站视频画面；关=纯音频听歌。
     * 切换后对当前曲重新取流。
     */
    fun setGestureMode(mode: com.madus.mobile.data.VideoGestureMode) {
        viewModelScope.launch {
            playerPrefs.setGestureMode(mode)
            val tip = when (mode) {
                com.madus.mobile.data.VideoGestureMode.BILIBILI ->
                    "B站模式 · 双击暂停 · 下半长按加速 · 上半长按菜单"
                com.madus.mobile.data.VideoGestureMode.KUAISHOU ->
                    "快手模式 · 单击暂停 · 长按出菜单"
                com.madus.mobile.data.VideoGestureMode.DOUYIN ->
                    "抖音模式 · 单击暂停 · 双击赞 · 角上长按 2x"
            }
            _toast.value = tip
        }
    }

    fun setVideoMode(enabled: Boolean) {
        viewModelScope.launch {
            playerPrefs.setVideoMode(enabled)
            MadusApp.instance.videoModeEnabled = enabled
            _toast.value = if (enabled) {
                "已开启视频模式 · 可看画面"
            } else {
                "已关闭视频模式 · 纯音频听歌"
            }
            // 正在播则按新模式重解流
            val cur = playback.value.current ?: return@launch
            val pos = playback.value.positionMs
            val q = if (pendingQueue.isNotEmpty()) pendingQueue else listOf(cur)
            // 清掉旧 stream，强制按新模式解析
            val cleared = q.map {
                if (it.id == cur.id || it.streamUrl != null) {
                    it.copy(streamUrl = null, isVideoStream = false)
                } else it
            }
            val freshCur = cleared.firstOrNull { it.id == cur.id } ?: cur.copy(streamUrl = null)
            playTrack(
                track = freshCur,
                queue = cleared,
                resumeIfSame = false,
                sourceLabel = null,
                sourceId = null,
            )
            // 尽量从原进度附近继续（下一帧 seek）
            if (pos > 2_000) {
                kotlinx.coroutines.delay(400)
                seek(pos)
            }
        }
    }

    private val _sleepSelectedMinutes = MutableStateFlow(0)
    /** 用户上次选择的定时分钟；0=关 */
    val sleepSelectedMinutes: StateFlow<Int> = _sleepSelectedMinutes.asStateFlow()

    fun setSleepTimer(minutes: Int) {
        _sleepSelectedMinutes.value = minutes.coerceAtLeast(0)
        player.setSleepTimerMinutes(minutes)
        _toast.value = if (minutes <= 0) "已取消定时关闭" else "将在 ${minutes} 分钟后暂停"
    }

    private val _cacheManager = MutableStateFlow(CacheManagerUiState())
    val cacheManager: StateFlow<CacheManagerUiState> = _cacheManager.asStateFlow()

    fun refreshCacheManager() {
        viewModelScope.launch {
            _cacheManager.update { it.copy(isLoading = true) }
            val list = runCatching { trackCache.list() }.getOrDefault(emptyList())
            val stream = StreamCache.usageBytes(MadusApp.instance)
            val offline = list.sumOf { it.bytes }
            _cacheManager.value = CacheManagerUiState(
                items = list.sortedByDescending { it.cachedAtMs },
                streamCacheLabel = StreamCache.formatSize(stream),
                totalLabel = StreamCache.formatSize(stream + offline),
                isLoading = false,
            )
            refreshCacheStats()
        }
    }

    fun removeCachedTrack(trackId: String) {
        viewModelScope.launch {
            trackCache.remove(trackId)
            refreshCacheManager()
            _toast.value = "已删除缓存"
        }
    }

    fun clearOfflineCacheOnly() {
        viewModelScope.launch {
            trackCache.clearAll()
            refreshCacheManager()
            _toast.value = "已清空离线曲"
        }
    }

    fun clearStreamCacheOnly() {
        viewModelScope.launch {
            StreamCache.clear(MadusApp.instance)
            refreshCacheManager()
            _toast.value = "已清空边听缓存"
        }
    }

    fun shareTextForCurrent(): String? {
        val t = playback.value.current ?: return null
        val bv = t.bvid.ifBlank {
            com.madus.mobile.data.BilibiliApi.parseBvid(t.id).orEmpty()
        }
        return if (bv.isNotBlank()) {
            "分享自 Madus · ${t.title} - ${t.artist}\nhttps://www.bilibili.com/video/$bv"
        } else {
            "分享自 Madus · ${t.title} - ${t.artist}"
        }
    }

    fun cacheCurrentTrack() {
        viewModelScope.launch {
            val t = playback.value.current ?: run {
                _toast.value = "没有在播的歌"
                return@launch
            }
            _toast.value = "正在缓存…"
            val resolved = resolveOne(t)
            val url = resolved?.streamUrl
            if (resolved == null || url.isNullOrBlank()) {
                _toast.value = "无法获取音频流"
                return@launch
            }
            // 已是本地文件
            if (!url.startsWith("http")) {
                _toast.value = "已在本地"
                return@launch
            }
            runCatching {
                trackCache.cacheTrack(resolved, url)
            }.onSuccess {
                refreshCacheStats()
                _toast.value = "已缓存：${t.title}"
            }.onFailure {
                _toast.value = "缓存失败：${it.message ?: "未知错误"}"
            }
        }
    }

    fun clearStreamCache() {
        viewModelScope.launch {
            StreamCache.clear(MadusApp.instance)
            trackCache.clearAll()
            refreshCacheStats()
            _toast.value = "缓存已清理"
        }
    }

    fun refreshCacheStats() {
        viewModelScope.launch {
            val stream = StreamCache.usageBytes(MadusApp.instance)
            val offline = trackCache.totalBytes()
            val count = trackCache.list().size
            _me.update {
                it.copy(
                    cacheSizeLabel = StreamCache.formatSize(stream + offline),
                    offlineCount = count,
                )
            }
        }
    }

    fun openComments(track: Track? = null) {
        val t = track ?: playback.value.current ?: return
        _comments.value = CommentsUiState(visible = true, track = t, isLoading = true, page = 1)
        viewModelScope.launch {
            val loggedIn = runCatching { biliApi.isLoggedInCookie() }.getOrDefault(false)
            var aid = t.aid.filter { it.isDigit() }
            if (aid.isBlank()) {
                aid = resolveOne(t)?.aid?.filter { it.isDigit() }.orEmpty()
            }
            if (aid.isBlank()) {
                _comments.update {
                    it.copy(
                        isLoading = false,
                        loggedIn = loggedIn,
                        error = "无法获取稿件 id，请先成功播放一次再开评论",
                    )
                }
                return@launch
            }
            val page = runCatching {
                biliApi.listComments(aid, page = 1, pageSize = 40)
            }.getOrElse {
                BilibiliApi.CommentPage(emptyList(), false, it.message)
            }
            _comments.update {
                it.copy(
                    isLoading = false,
                    comments = page.comments,
                    page = page.nextPn,
                    nextCursor = page.nextCursor,
                    usedMainApi = page.usedMainApi,
                    hasMore = page.hasMore,
                    total = page.total,
                    loggedIn = loggedIn,
                    track = t.copy(aid = aid),
                    error = when {
                        page.error != null && page.comments.isEmpty() -> page.error
                        page.comments.isEmpty() -> "这条还没有评论"
                        else -> null
                    },
                )
            }
        }
    }

    fun loadMoreComments() {
        viewModelScope.launch {
            val st = _comments.value
            val aid = st.track?.aid?.filter { it.isDigit() }.orEmpty()
            if (!st.hasMore || st.loadingMore || aid.isBlank()) return@launch
            _comments.update { it.copy(loadingMore = true) }
            val page = runCatching {
                if (st.usedMainApi) {
                    biliApi.listComments(
                        aid = aid,
                        page = st.page,
                        pageSize = 40,
                        cursorNext = st.nextCursor,
                        preferMain = true,
                    )
                } else {
                    biliApi.listComments(
                        aid = aid,
                        page = st.page,
                        pageSize = 40,
                    )
                }
            }.getOrElse {
                BilibiliApi.CommentPage(emptyList(), false, it.message)
            }
            val merged = (st.comments + page.comments)
                .distinctBy { it.rpid.ifBlank { "${it.uname}_${it.message.hashCode()}" } }
            // 若本页 0 条但声明 hasMore，停止，避免死循环
            val more = page.hasMore && page.comments.isNotEmpty()
            _comments.update {
                it.copy(
                    loadingMore = false,
                    comments = merged,
                    page = page.nextPn,
                    nextCursor = page.nextCursor,
                    usedMainApi = page.usedMainApi || st.usedMainApi,
                    hasMore = more,
                    total = page.total.coerceAtLeast(st.total),
                    error = if (page.error != null && page.comments.isEmpty()) page.error else null,
                )
            }
        }
    }

    /**
     * 展开/加载更多楼中楼：每次只加一页（20 条），避免一次拉几千条滑不完。
     */
    fun loadAllCommentReplies(root: BilibiliApi.Comment) {
        viewModelScope.launch {
            val st = _comments.value
            val aid = st.track?.aid?.filter { it.isDigit() }.orEmpty()
            val rootId = root.rpid.ifBlank { return@launch }
            if (aid.isBlank()) return@launch
            if (st.loadingRepliesRoot == rootId) return@launch
            _comments.update { it.copy(loadingRepliesRoot = rootId) }
            val pn = root.repliesNextPn.coerceAtLeast(1)
            val page = runCatching {
                biliApi.listCommentReplies(aid, rootId, page = pn, pageSize = 20)
            }.getOrElse {
                BilibiliApi.CommentPage(emptyList(), false, it.message)
            }
            val merged = linkedMapOf<String, BilibiliApi.Comment>()
            root.children.forEach { c ->
                merged[c.rpid.ifBlank { "${c.uname}_${c.message.hashCode()}" }] = c
            }
            page.comments.forEach { c ->
                merged[c.rpid.ifBlank { "${c.uname}_${c.message.hashCode()}" }] = c
            }
            val children = merged.values.toList()
            val hasMore = page.hasMore && page.comments.isNotEmpty()
            _comments.update { cur ->
                cur.copy(
                    loadingRepliesRoot = null,
                    comments = cur.comments.map { c ->
                        if (c.rpid == rootId) {
                            c.copy(
                                children = children,
                                rcount = c.rcount.coerceAtLeast(children.size),
                                repliesHasMore = hasMore || children.size < c.rcount,
                                repliesNextPn = page.nextPn,
                            )
                        } else c
                    },
                )
            }
        }
    }

    fun dismissComments() {
        _comments.value = CommentsUiState()
    }

    fun onCommentDraftChange(text: String) {
        _comments.update { it.copy(draft = text) }
    }

    fun beginReplyComment(comment: BilibiliApi.Comment) {
        val root = comment.rootRpid.ifBlank { comment.rpid }.ifBlank { "0" }
        val parent = comment.rpid.ifBlank { "0" }
        _comments.update {
            it.copy(
                replyTo = comment,
                replyRootRpid = root,
                replyParentRpid = parent,
                replyHint = "回复 @${comment.uname}",
            )
        }
    }

    fun cancelReplyComment() {
        _comments.update {
            it.copy(
                replyTo = null,
                replyRootRpid = "0",
                replyParentRpid = "0",
                replyHint = "",
            )
        }
    }

    fun postComment() {
        viewModelScope.launch {
            val st = _comments.value
            val t = st.track ?: return@launch
            val aid = t.aid
            if (aid.isBlank()) {
                _comments.update { it.copy(error = "缺少 aid") }
                return@launch
            }
            _comments.update { it.copy(posting = true, error = null) }
            val err = biliApi.postComment(
                aid = aid,
                message = st.draft,
                root = st.replyRootRpid,
                parent = st.replyParentRpid,
            )
            if (err != null) {
                _comments.update { it.copy(posting = false, error = err) }
                return@launch
            }
            _comments.update {
                it.copy(
                    posting = false,
                    draft = "",
                    replyTo = null,
                    replyRootRpid = "0",
                    replyParentRpid = "0",
                    replyHint = "",
                )
            }
            openComments(t)
            _toast.value = if (st.replyTo != null) "回复已同步到 B 站" else "评论已同步到 B 站"
        }
    }

    /** B 站相关推荐 → 当作轻量电台队列 */
    fun playRelatedRadio() {
        viewModelScope.launch {
            val cur = playback.value.current
            val bvid = cur?.bvid?.ifBlank {
                com.madus.mobile.data.BilibiliApi.parseBvid(cur.id).orEmpty()
            }.orEmpty()
            if (bvid.isBlank()) {
                _toast.value = "先播一首 B 站视频再开相关电台"
                return@launch
            }
            _toast.value = "加载相关推荐…"
            val related = runCatching { biliApi.relatedTracks(bvid, 24) }.getOrDefault(emptyList())
            if (related.isEmpty()) {
                _toast.value = "暂无相关推荐"
                return@launch
            }
            val queue = listOfNotNull(cur) + related.filter { it.id != cur?.id }
            playTrack(
                track = related.first(),
                queue = queue,
                loopAll = true,
                resumeIfSame = false,
                sourceLabel = "相关电台",
                sourceId = "related-$bvid",
            )
            _toast.value = "已切入相关电台 · ${related.size} 首"
        }
    }

    fun deleteLocalPlaylist(playlistId: String) {
        viewModelScope.launch {
            if (!playlistId.startsWith("local-") || playlistId == LikedStore.LIKED_ID) return@launch
            localPl.delete(playlistId)
            refreshHome()
            if (_playlistDetail.value.playlist?.id == playlistId) {
                _playlistDetail.value = PlaylistDetailUiState()
            }
            _toast.value = "已删除歌单"
        }
    }

    private fun applyPlayModeToPlayer() {
        when (_playMode.value) {
            PlayModeLabel.LOOP -> {
                player.setShuffle(false)
                player.setRepeat(RepeatMode.ALL)
            }
            PlayModeLabel.SHUFFLE -> {
                player.setShuffle(true)
                player.setRepeat(RepeatMode.ALL)
            }
            PlayModeLabel.SINGLE -> {
                player.setShuffle(false)
                player.setRepeat(RepeatMode.ONE)
            }
        }
    }

    private suspend fun applyCover(pl: Playlist): Playlist {
        val path = coverStore.get(pl.id) ?: return pl
        return pl.copy(coverUrl = path)
    }

    private suspend fun loadRecentFromStore() {
        val entries = runCatching { recentStore.list() }.getOrDefault(emptyList())
            // 丢掉历史演示曲
            .filter { it.track.source != MusicSourceType.LOCAL_DEMO }
        sessionRecent.clear()
        // store is newest-first; sessionRecent keeps oldest→newest for pushRecent
        entries.asReversed().forEach { sessionRecent.add(it.track) }
        val recent = entries.map { it.track }
        _home.update { it.copy(recent = recent.take(12)) }
        _recommend.update { it.copy(recent = recent) }
    }

    private fun startPositionPersistLoop() {
        positionPersistJob?.cancel()
        positionPersistJob = viewModelScope.launch {
            while (isActive) {
                // 后台拉长间隔，省 IO；前台仍 5s 一次
                val bg = MadusApp.instance.appInBackground
                val lite = MadusApp.instance.gameLiteMode
                delay(
                    when {
                        bg && lite -> 20_000L
                        bg -> 15_000L
                        lite -> 8_000L
                        else -> 5_000L
                    },
                )
                persistCurrentPosition()
                runCatching { maybeShowLongPlayHint() }
            }
        }
    }

    private suspend fun persistCurrentPosition() {
        val pb = playback.value
        val t = pb.current ?: return
        if (pb.positionMs < 1_500) return
        // Near end: reset so next open starts clean
        val pos = if (pb.durationMs > 0 && pb.positionMs > pb.durationMs - 3_000) {
            0L
        } else {
            pb.positionMs
        }
        rememberPosition(t.id, pos)
        runCatching { recentStore.savePosition(t.id, pos) }
    }

    private fun rememberPosition(trackId: String, positionMs: Long) {
        if (trackId.isBlank()) return
        sessionPositions[trackId] = positionMs.coerceAtLeast(0L)
    }

    /** 读取可续播进度；过短或已接近片尾则从 0 起 */
    private suspend fun resumePositionOf(trackId: String, durationMs: Long = 0L): Long {
        if (trackId.isBlank()) return 0L
        val raw = sessionPositions[trackId]
            ?: runCatching { recentStore.positionOf(trackId) }.getOrDefault(0L)
        if (raw < 2_000L) return 0L
        val dur = durationMs.coerceAtLeast(0L)
        if (dur > 0L && raw > dur - 3_000L) return 0L
        return raw
    }

    private fun isVideoPlayback(): Boolean =
        MadusApp.instance.videoModeEnabled ||
            playback.value.current?.isVideoStream == true

    /**
     * 切到相邻一条时的起点。
     * 视频上下滑：读会话/最近记忆续播。
     * 听歌切歌：一律从头，避免第一首落在 >3s 后「上一首」被当成重头播当前。
     */
    private fun startPosForNeighborSwitch(): Long = if (isVideoPlayback()) -1L else 0L

    /** 播放进度信号：WATCH_50 / WATCH_90，每曲每种只记一次。 */
    private fun startRecommendationSignalLoop() {
        viewModelScope.launch {
            while (isActive) {
                delay(2_000L)
                val pb = playback.value
                val t = pb.current ?: continue
                if (!isForYouQueue()) continue
                val pos = pb.positionMs
                val dur = pb.durationMs
                if (pos < 1_000L) continue
                val threshold50 = if (dur > 0L) {
                    maxOf(dur / 2L, RecommendationTuning.WATCH_50_MIN_MS).coerceAtMost(dur)
                } else {
                    RecommendationTuning.WATCH_50_MIN_MS
                }
                if (pos >= threshold50 && watchFired50.add(t.id)) {
                    recordEvent(t, RecommendationEventType.WATCH_50)
                }
                if (dur > 0L && pos >= dur * 9 / 10 && watchFired90.add(t.id)) {
                    recordEvent(t, RecommendationEventType.WATCH_90)
                }
            }
        }
    }

    /** 快速跳过判定：只对 recommend 无限流、主动切走、播放过短、本会话未重播的曲目记录。 */
    private fun maybeRecordSkipFast() {
        val pb = playback.value
        val t = pb.current ?: return
        if (!isForYouQueue()) return
        if (t.id in sessionReplayedIds) return
        val dur = pb.durationMs
        val threshold = if (dur > 0L) {
            minOf(
                RecommendationTuning.SKIP_FAST_MIN_MS,
                maxOf(dur / 100 * 15, 5_000L),
            )
        } else {
            RecommendationTuning.SKIP_FAST_MIN_MS
        }
        if (pb.positionMs >= threshold) return
        recordEvent(t, RecommendationEventType.SKIP_FAST)
    }

    private fun profileKey(track: Track): String = track.bvid.ifBlank { track.id }

    private suspend fun profileOf(track: Track, fetchRemote: Boolean = false): ContentProfile {
        val cached = runCatching { contentProfileStore.get(profileKey(track)) }.getOrNull()
        if (cached != null) return cached
        val fallback = ContentProfileParser.profileFromTrack(track)
        if (!fetchRemote || track.source != MusicSourceType.BILIBILI || track.bvid.isBlank()) {
            return fallback
        }
        return runCatching {
            val meta = biliApi.videoMeta(track.bvid) ?: return@runCatching fallback
            val profile = ContentProfile(
                trackId = track.id,
                bvid = track.bvid,
                authorId = meta.ownerMid.takeIf { it.isNotBlank() },
                authorName = meta.ownerName.takeIf { it.isNotBlank() },
                categoryId = meta.tid.takeIf { it > 0 },
                categoryName = meta.tname.takeIf { it.isNotBlank() },
                tags = meta.tags.toSet(),
                topicKeys = ContentProfileParser.parseTopicKeys(
                    categoryId = meta.tid.takeIf { it > 0 },
                    categoryName = meta.tname,
                    tags = meta.tags,
                    text = "${track.title} ${track.album} ${meta.tname}",
                ),
                fetchedAtMs = System.currentTimeMillis(),
            )
            runCatching { contentProfileStore.put(profile) }
            profile
        }.getOrDefault(fallback)
    }

    private fun recordEvent(
        track: Track,
        type: RecommendationEventType,
        sourceId: String = _recommend.value.sourceId,
    ) {
        viewModelScope.launch {
            val p = profileOf(track, fetchRemote = type in REALTIME_FEEDBACK_TYPES)
            val event = RecommendationEvent(
                trackId = track.id,
                bvid = track.bvid,
                type = type,
                occurredAtMs = System.currentTimeMillis(),
                sourceId = sourceId,
                topicKeys = p.topicKeys,
                authorKey = p.authorKey,
            )
            runCatching { recommendationEventStore.record(event) }
            if (type in REALTIME_FEEDBACK_TYPES) {
                runCatching { maybeInsertRealtime(track, event) }
                runCatching { reshuffleUpcomingAfterLike(track) }
            }
        }
    }

    private suspend fun maybeInsertRealtime(seed: Track, event: RecommendationEvent) {
        if (!isForYouQueue()) return
        val bv = seed.bvid.ifBlank { BilibiliApi.parseBvid(seed.id).orEmpty() }
        if (bv.isBlank()) return
        val now = System.currentTimeMillis()
        val related = runCatching { biliApi.relatedTracks(bv, 18) }.getOrDefault(emptyList())
        if (related.isEmpty()) return
        if (!isForYouQueue()) return

        val events = runCatching { recommendationEventStore.events() }.getOrDefault(emptyList())
        val state = recommendationEngine.buildInterestState(events, now)
        val queueIds = pendingQueue.mapTo(hashSetOf()) { it.id }
        val existing = hashSetOf<String>().apply {
            addAll(sessionSeenIds)
            addAll(queueIds)
        }
        val context = FeedContext(
            nowMs = now,
            limit = 2,
            sessionSeenIds = existing,
            queueIds = queueIds,
            recentQueue = pendingQueue.drop((pendingIndex - 2).coerceAtLeast(0)).take(6),
            mutedTopics = mutedTopicsOf(state, now),
            mutedAuthors = mutedAuthorsOf(state, now),
            sourceId = "recommend",
            realtimeTopicQuota = realtimeQuota(event, now),
        )
        val scored = related
            .filter { it.id !in existing && (it.bvid.isNotBlank() || it.id.startsWith("BV")) }
            .map { recommendationEngine.scoreCandidate(it, profileOf(it), state, "realtime-related", context) }
            .filter { it.score > 0.2 }
        if (scored.isEmpty()) return

        val (_, picked) = recommendationReRanker.rerankWithReasons(scored, context)
        if (picked.isEmpty()) return
        val extras = picked.map { it.track }
        val inserted = softInsertRealtime(extras, pendingIndex)
        if (inserted.isEmpty()) {
            spliceAfter(pendingIndex + 5, extras.take(3))
        }
        if (inserted.isNotEmpty()) {
            val rows = picked.filter { it.track.id in inserted }
                .map { "插:${it.track.title.take(14)} ${it.reason}" }
            _recommend.update {
                it.copy(debugRows = (listOf("实时:${seed.title.take(14)}") + rows).takeLast(12))
            }
        }
    }

    /**
     * 点赞后重排「当前+下一条」之后的队列。
     * 相关条不再被「列表里写过就算看过」挡掉。
     */
    private suspend fun reshuffleUpcomingAfterLike(seed: Track) {
        if (!isForYouQueue()) return
        val keep = (pendingIndex + 2).coerceAtMost(pendingQueue.size)
        if (keep <= 0 || pendingQueue.isEmpty()) return
        val head = pendingQueue.take(keep)
        val rest = pendingQueue.drop(keep)
        val bv = seed.bvid.ifBlank { BilibiliApi.parseBvid(seed.id).orEmpty() }
        val related = if (bv.isBlank()) {
            emptyList()
        } else {
            runCatching { biliApi.relatedTracks(bv, 18) }.getOrDefault(emptyList())
        }
        if (related.isEmpty() && rest.isEmpty()) return
        val now = System.currentTimeMillis()
        val events = runCatching { recommendationEventStore.events() }.getOrDefault(emptyList())
        val state = recommendationEngine.buildInterestState(events, now)
        val headIds = head.mapTo(hashSetOf()) { it.id }
        val played = sessionSeenIds.toSet()
        val context = FeedContext(
            nowMs = now,
            limit = (rest.size + 12).coerceIn(12, 28),
            sessionSeenIds = played,
            queueIds = headIds,
            recentQueue = head.takeLast(4),
            mutedTopics = mutedTopicsOf(state, now),
            mutedAuthors = mutedAuthorsOf(state, now),
            sourceId = "recommend",
        )
        val pool = linkedMapOf<String, ScoredTrack>()
        fun put(t: Track, source: String) {
            if (t.id in headIds || t.id in played) return
            val p = ContentProfileParser.profileFromTrack(t)
            val sc = recommendationEngine.scoreCandidate(t, p, state, source, context)
            val prev = pool[t.id]
            if (prev == null || sc.score > prev.score) pool[t.id] = sc
        }
        related.forEach { put(it, "related-like") }
        rest.forEach { put(it, "related") }
        if (pool.isEmpty()) return
        val (next, _) = recommendationReRanker.rerankWithReasons(pool.values.toList(), context)
        if (next.isEmpty()) return
        pendingQueue = head + next
        _queueTracks.value = pendingQueue
        _recommend.update { it.copy(feed = pendingQueue) }
    }

    private fun realtimeQuota(
        event: RecommendationEvent,
        nowMs: Long,
    ): Map<String, Int> {
        val quota = linkedMapOf<String, Int>()
        val cutoff = nowMs - RecommendationTuning.REALTIME_STRONG_TTL_MS
        for (topic in event.topicKeys.filter { it != "unknown" }) {
            val times = realtimeInsertedTopics.getOrPut(topic) { mutableListOf() }
            times.removeAll { it < cutoff }
            quota[topic] = (2 - times.size).coerceAtLeast(0)
        }
        return quota
    }

    /** 软插入到当前播放之后的第 2～4 位，不替换当前曲与下一条。 */
    private fun softInsertRealtime(newTracks: List<Track>, currentIndex: Int): List<String> {
        val queue = pendingQueue.toMutableList()
        val insertedIds = mutableListOf<String>()
        for (track in newTracks.take(2)) {
            if (queue.any { it.id == track.id }) continue
            var placed = false
            for (offset in 2..8) {
                val idx = currentIndex + offset
                if (idx > queue.size) break
                if (idx >= queue.size) {
                    queue.add(track)
                    placed = true
                    break
                }
                val prev = queue.getOrNull(idx - 1)
                val next = queue.getOrNull(idx)
                val topicOk = !topicsOverlap(prev, track) && !topicsOverlap(next, track)
                val authorOk = !sameAuthor(prev, track) && !sameAuthor(next, track)
                if (topicOk && authorOk) {
                    queue.add(idx, track)
                    placed = true
                    break
                }
            }
            if (placed) {
                insertedIds.add(track.id)
                val ts = System.currentTimeMillis()
                ContentProfileParser.profileFromTrack(track)
                    .topicKeys.filter { it != "unknown" }
                    .forEach { realtimeInsertedTopics.getOrPut(it) { mutableListOf() }.add(ts) }
            }
        }
        if (insertedIds.isEmpty()) return emptyList()
        pendingQueue = queue
        _queueTracks.value = queue
        _recommend.update { it.copy(feed = queue) }
        return insertedIds
    }

    /** 把相关条塞到更后面，不碰当前和下一条。 */
    private fun spliceAfter(index: Int, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val queue = pendingQueue.toMutableList()
        var at = index.coerceIn(0, queue.size)
        var added = 0
        for (track in tracks) {
            if (queue.any { it.id == track.id }) continue
            queue.add(at, track)
            at++
            added++
        }
        if (added == 0) return
        pendingQueue = queue
        _queueTracks.value = queue
        _recommend.update { it.copy(feed = queue) }
    }

    private fun mutedTopicsOf(state: InterestState, nowMs: Long): Set<String> =
        state.mutedTopics
            .filter { (key, until) -> until > nowMs && !key.startsWith("author:") }
            .keys

    private fun mutedAuthorsOf(state: InterestState, nowMs: Long): Set<String> =
        state.mutedTopics
            .filter { (key, until) -> until > nowMs && key.startsWith("author:") }
            .keys
            .map { it.removePrefix("author:") }
            .toSet()

    private fun topicsOverlap(a: Track?, b: Track): Boolean {
        if (a == null) return false
        val ta = ContentProfileParser.profileFromTrack(a).topicKeys.filter { it != "unknown" }
        val tb = ContentProfileParser.profileFromTrack(b).topicKeys.filter { it != "unknown" }
        return ta.any { it in tb }
    }

    private fun sameAuthor(a: Track?, b: Track): Boolean {
        if (a == null) return false
        val an = a.artist.trim()
        val bn = b.artist.trim()
        return an.isNotBlank() && an.equals(bn, ignoreCase = true)
    }

    fun refreshHome() {
        viewModelScope.launch {
            _home.update { it.copy(isLoading = true, message = null) }
            val bili = registry.get(MusicSourceType.BILIBILI)
            val session = bili?.getAuthSession()
            val biliPlaylists = if (session?.isLoggedIn == true) {
                runCatching { bili.featuredPlaylists() }.getOrDefault(emptyList())
                    .map { applyCover(it) }
            } else emptyList()
            // 我的喜欢永远在前 + 有曲的本地歌单
            val liked = runCatching { likedStore.toPlaylist() }.getOrElse {
                Playlist(id = LikedStore.LIKED_ID, title = LikedStore.LIKED_TITLE, trackCount = 0)
            }.let { applyCover(it) }
            val locals = runCatching {
                localPl.listNonEmpty().map { applyCover(it.toPlaylist()) }
            }.getOrDefault(emptyList())
            val localPlaylists = listOf(liked) + locals
            val recent = sessionRecent.asReversed().distinctBy { it.id }.take(12)
            _home.value = HomeUiState(
                greeting = if (session?.isLoggedIn == true) {
                    "你好，${session.displayName.ifBlank { "B站用户" }}"
                } else {
                    "今天想听什么"
                },
                playlists = biliPlaylists,
                localPlaylists = localPlaylists,
                recent = recent,
                isLoading = false,
                message = null,
                avatarUrl = session?.avatarUrl,
                biliLoggedIn = session?.isLoggedIn == true,
            )
            _recommend.update {
                it.copy(likedIds = runCatching { likedStore.ids() }.getOrDefault(emptySet()))
            }
            // 曲库与首页同源刷新
            refreshLibraryInternal(
                liked = liked,
                biliPlaylists = biliPlaylists,
                biliLoggedIn = session?.isLoggedIn == true,
            )
        }
    }

    /** 曲库 Tab 专用刷新（进入时 / 手动） */
    fun refreshLibrary() {
        viewModelScope.launch {
            _library.update { it.copy(isLoading = true) }
            val bili = registry.get(MusicSourceType.BILIBILI)
            val session = runCatching { bili?.getAuthSession() }.getOrNull()
            val liked = runCatching { likedStore.toPlaylist() }.getOrElse {
                Playlist(id = LikedStore.LIKED_ID, title = LikedStore.LIKED_TITLE, trackCount = 0)
            }.let { applyCover(it) }
            val biliPlaylists = if (session?.isLoggedIn == true) {
                runCatching { bili?.featuredPlaylists().orEmpty() }.getOrDefault(emptyList())
                    .map { applyCover(it) }
            } else emptyList()
            refreshLibraryInternal(
                liked = liked,
                biliPlaylists = biliPlaylists,
                biliLoggedIn = session?.isLoggedIn == true,
            )
        }
    }

    private suspend fun refreshLibraryInternal(
        liked: Playlist,
        biliPlaylists: List<Playlist>,
        biliLoggedIn: Boolean,
    ) {
        val locals = runCatching {
            localPl.list()
                .filter { it.id != LikedStore.LIKED_ID }
                .map { applyCover(it.toPlaylist()) }
        }.getOrDefault(emptyList())
        val recent = sessionRecent.asReversed().distinctBy { it.id }
        val offline = runCatching { trackCache.list().size }.getOrDefault(0)
        _library.value = LibraryUiState(
            liked = liked,
            localPlaylists = locals,
            biliPlaylists = biliPlaylists,
            recent = recent,
            biliLoggedIn = biliLoggedIn,
            offlineCount = offline,
            isLoading = false,
        )
    }

    private var suggestJob: Job? = null

    fun onSearchQueryChange(query: String) {
        _search.update { it.copy(query = query, message = null) }
        // 输入联想：防抖 280ms
        suggestJob?.cancel()
        val q = query.trim()
        if (q.length < 1) {
            _search.update { it.copy(suggestions = emptyList()) }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(280)
            val tips = runCatching { biliApi.searchSuggest(q) }.getOrDefault(emptyList())
            // 查询可能已变
            if (_search.value.query.trim() == q) {
                _search.update { it.copy(suggestions = tips) }
            }
        }
    }

    fun applySearchSuggestion(tip: String) {
        _search.update { it.copy(query = tip, suggestions = emptyList()) }
        submitSearch()
    }

    fun submitSearch() {
        val q = _search.value.query.trim()
        if (q.isEmpty()) return
        suggestJob?.cancel()
        viewModelScope.launch {
            _search.update {
                it.copy(
                    isSearching = true,
                    message = null,
                    suggestions = emptyList(),
                    loadingMore = false,
                    hasMore = false,
                    page = 0,
                    total = 0,
                    keywordUsed = q,
                )
            }
            runCatching { biliApi.ensureGuestCookies() }
            // 对齐 B 站网页：综合排序 + 每页 42 条
            val page = runCatching {
                biliApi.searchPage(
                    keyword = q,
                    page = 1,
                    pageSize = 42,
                    order = "totalrank",
                    allowCorrect = true,
                )
            }.getOrElse {
                _search.value = SearchUiState(
                    query = q,
                    isSearching = false,
                    message = it.message ?: "搜索失败",
                )
                return@launch
            }
            val results = page.tracks.distinctBy { it.id }
            val tips = if (results.isEmpty()) {
                runCatching { biliApi.searchSuggest(q) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            _search.value = SearchUiState(
                query = q,
                results = results,
                suggestions = tips,
                isSearching = false,
                page = page.page,
                hasMore = page.hasMore,
                loadingMore = false,
                total = page.total,
                keywordUsed = page.keywordUsed.ifBlank { q },
                message = if (results.isEmpty()) {
                    if (tips.isNotEmpty()) {
                        "没有精确结果。试试联想：${tips.take(3).joinToString(" / ")}"
                    } else {
                        "没有找到结果。试试换关键词，或直接搜 BV 号"
                    }
                } else {
                    null
                },
            )
        }
    }

    /** 搜索结果滚到底：继续拉 B 站下一页 */
    fun loadMoreSearch() {
        val st = _search.value
        if (st.isSearching || st.loadingMore || !st.hasMore) return
        val kw = st.keywordUsed.ifBlank { st.query }.trim()
        if (kw.isBlank()) return
        val next = st.page + 1
        viewModelScope.launch {
            _search.update { it.copy(loadingMore = true) }
            val page = runCatching {
                biliApi.searchPage(
                    keyword = kw,
                    page = next,
                    pageSize = 42,
                    order = "totalrank",
                    allowCorrect = false,
                )
            }.getOrElse { e ->
                _search.update {
                    it.copy(loadingMore = false, hasMore = false, message = e.message ?: "加载更多失败")
                }
                return@launch
            }
            // 用户已换词/重新搜，丢弃旧页
            if (_search.value.keywordUsed != kw && _search.value.query.trim() != st.query.trim()) {
                return@launch
            }
            if (page.tracks.isEmpty()) {
                _search.update { it.copy(loadingMore = false, hasMore = false) }
                return@launch
            }
            val merged = (st.results + page.tracks).distinctBy { it.id }
            _search.update {
                it.copy(
                    results = merged,
                    page = page.page,
                    hasMore = page.hasMore && page.tracks.isNotEmpty(),
                    loadingMore = false,
                    total = page.total.coerceAtLeast(it.total),
                    message = null,
                )
            }
        }
    }

    /**
     * 普通搜索：直接返回 B 站全站视频，不做歌名相关度过滤。
     * 识曲相关过滤只留在 AI 搜/哼唱链路里。
     */
    private suspend fun plainBiliSearch(query: String, limit: Int): List<Track> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return runCatching {
            biliApi.searchPage(
                keyword = q,
                page = 1,
                pageSize = limit.coerceIn(4, 50),
                allowCorrect = true,
            ).tracks.distinctBy { it.id }.take(limit)
        }.getOrDefault(emptyList())
    }

    /**
     * Spotify 式：整表 context 写入 pendingQueue，从点击项起播。
     * 引擎侧仍懒解析当前曲；曲终由 onPlaybackEnded → advanceToNext。
     */
    fun playTrack(
        track: Track,
        queue: List<Track> = listOf(track),
        loopAll: Boolean = false,
        resumeIfSame: Boolean = true,
        sourceLabel: String? = null,
        sourceId: String? = null,
    ) {
        suppressRecommendAutoPlay = true
        // 切入有限队列时停掉推荐流后台续刷，避免歌单里继续塞 related
        if (sourceId != null && sourceId != "recommend") {
            expandJob?.cancel()
            expandJob = null
            expandingFeed = false
        } else if (sourceLabel != null && sourceId == null) {
            // 有标签但未给 id：按有限上下文处理，不要继承旧的 recommend
            expandJob?.cancel()
            expandJob = null
            expandingFeed = false
        }
        val base = if (queue.isEmpty()) listOf(track) else queue
        pendingQueue = base
        pendingIndex = base.indexOfFirst { it.id == track.id }.let { if (it < 0) 0 else it }
        _queueTracks.value = base
        if (sourceLabel != null) {
            // sourceId 未传时绝不能沿用「recommend」，否则歌单会变成无限流
            val resolvedSourceId = sourceId?.takeIf { it.isNotBlank() }
                ?: "ctx-${sourceLabel.hashCode().toUInt().toString(16)}"
            _recommend.update {
                it.copy(
                    sourceLabel = sourceLabel,
                    sourceId = resolvedSourceId,
                    segment = RecommendSegment.Feed,
                    feed = base,
                )
            }
        }
        // 若调用方要求 loopAll（播放全部），切到 LOOP
        if (loopAll) {
            _playMode.value = PlayModeLabel.LOOP
        }

        viewModelScope.launch {
            val pb = playback.value
            if (resumeIfSame &&
                pb.current?.id == track.id &&
                pb.current?.title == track.title &&
                pendingQueue.size == 1
            ) {
                ensureService()
                if (!pb.isPlaying) player.dispatch(PlayerCommand.Play)
                return@launch
            }

            ensureService()
            applyPlayModeToPlayer()
            // 自动续播记忆（上下滑/重进同一条不从零开始）
            playIndex(pendingIndex)

            // 并行预解析：前台 3 / 后台 1 / 游戏轻量再压一档（至少保下一首）
            launch {
                val ahead = prefetchAheadCount()
                val from = pendingIndex + 1
                val to = minOf(pendingIndex + ahead, pendingQueue.size)
                if (from >= to) return@launch
                val jobs = (from until to).map { i ->
                    async(Dispatchers.IO) {
                        val t = pendingQueue.getOrNull(i) ?: return@async null
                        resolveOne(t)
                    }
                }
                val map = jobs.mapNotNull { it.await() }.associateBy { it.id }
                if (map.isNotEmpty()) {
                    pendingQueue = pendingQueue.map { map[it.id] ?: it }
                    _queueTracks.value = pendingQueue
                }
            }
        }
    }

    /**
     * 搜索点播：不要用「整页搜索结果」顶掉当前歌单队列。
     *
     * - 已有队列（收藏夹 / 本地歌单等）：把该曲插到当前曲之后并立即播放，其余曲目保留
     * - 无队列：只播这一首（不把搜索结果当电台）
     * - 来源标签保持原歌单名，避免推荐页看起来像换了台
     */
    /**
     * 从清屏短视频点右上角搜索时置 true；主搜索 Tab 点进时清掉。
     */
    @Volatile
    var searchFromImmersiveVideo: Boolean = false

    fun markSearchFromImmersiveVideo() {
        searchFromImmersiveVideo = true
    }

    fun clearSearchFromImmersiveVideo() {
        searchFromImmersiveVideo = false
    }

    /**
     * 主搜索 / 队列搜索点播（原逻辑，不要改坏）：
     * - 无队列：只播这一首
     * - 有队列：插到当前后播放，**不替换**原歌单；sourceLabel 保持
     * - 会 toast 插播结果（主搜索保留）
     */
    /**
     * 搜索点播：只播点中的这一首，**不**自动展开合集。
     * 整部合集仅在收藏「整部合集」时展开。
     */
    /**
     * 从队列/歌单发起「换歌」：搜索框保持空白，由用户自己输入后搜索，点结果替换原曲。
     * [playlistId] 为空时尝试用当前播放来源 local-*。
     */
    fun beginReplaceTrack(track: Track, playlistId: String? = null) {
        val plId = playlistId
            ?: _playlistDetail.value.playlist?.id?.takeIf { it.startsWith("local-") }
            ?: _recommend.value.sourceId.takeIf { it.startsWith("local-") }
        _trackReplace.value = TrackReplaceUiState(
            active = true,
            oldTrackId = track.id,
            oldTitle = track.title,
            playlistId = plId,
            queryHint = "",
        )
        // 搜索框不预填旧歌名，避免干扰；清空结果
        _search.update {
            it.copy(query = "", suggestions = emptyList(), results = emptyList(), message = null, isSearching = false)
        }
        _toast.value = "搜索后点结果即可替换"
    }

    fun cancelReplaceTrack() {
        _trackReplace.value = TrackReplaceUiState()
        _toast.value = "已取消换歌"
    }

    /** 用搜索结果替换队列（及可选本地歌单）中的旧曲 */
    fun applyReplaceTrack(newTrack: Track) {
        val st = _trackReplace.value
        if (!st.active || st.oldTrackId.isBlank()) {
            playSearchTrack(newTrack)
            return
        }
        val play = newTrack.copy(
            title = newTrack.title.replace(Regex("""\s*·\s*\d+P$"""), ""),
        )
        val oldId = st.oldTrackId
        val wasCurrent = pendingQueue.getOrNull(pendingIndex)?.id == oldId ||
            playback.value.current?.id == oldId

        // 1) 队列替换（保持位置）
        if (pendingQueue.isNotEmpty()) {
            val q = pendingQueue.toMutableList()
            val idx = q.indexOfFirst { it.id == oldId }
            if (idx >= 0) {
                q[idx] = play
                // 去掉其它重复的新 id
                val cleaned = q.filterIndexed { i, t -> i == idx || t.id != play.id }
                val newIndex = cleaned.indexOfFirst { it.id == play.id }.coerceAtLeast(0)
                pendingQueue = cleaned
                pendingIndex = if (wasCurrent) newIndex else {
                    val curId = playback.value.current?.id
                    cleaned.indexOfFirst { it.id == curId }.let { if (it < 0) pendingIndex.coerceIn(0, cleaned.lastIndex) else it }
                }
                _queueTracks.value = cleaned
                _recommend.update { it.copy(feed = cleaned) }
            }
        }

        // 2) 本地歌单写回
        val plId = st.playlistId
        if (!plId.isNullOrBlank() && plId.startsWith("local-")) {
            viewModelScope.launch {
                val ok = localPl.replaceTrack(plId, oldId, play)
                if (ok) {
                    refreshLibrary()
                    // 若正在看该歌单详情，刷新列表
                    val detail = _playlistDetail.value
                    if (detail.playlist?.id == plId) {
                        openPlaylist(detail.playlist!!)
                    }
                }
            }
        }

        _trackReplace.value = TrackReplaceUiState()
        _toast.value = "已替换为「${play.title.take(20)}」"

        if (wasCurrent || pendingQueue.isEmpty()) {
            val q = if (pendingQueue.isNotEmpty()) pendingQueue else listOf(play)
            playTrack(
                track = play,
                queue = q,
                resumeIfSame = false,
                sourceLabel = null,
                sourceId = null,
            )
        }
    }

    fun playSearchTrack(track: Track) {
        if (_trackReplace.value.active) {
            applyReplaceTrack(track)
            return
        }
        // 去掉标题上的「 · NP」展示后缀，取真实 bvid 稿件
        val play = track.copy(
            title = track.title.replace(Regex("""\s*·\s*\d+P$"""), ""),
        )
        if (pendingQueue.isEmpty()) {
            playTrack(
                track = play,
                queue = listOf(play),
                resumeIfSame = false,
                sourceLabel = "搜索",
                sourceId = "search",
            )
            _toast.value = "已开始播放"
            return
        }

        val kept = pendingQueue.size
        val currentId = pendingQueue.getOrNull(pendingIndex)?.id
        val without = pendingQueue.filter { it.id != play.id }
        val curIdx = when {
            currentId == null || currentId == play.id -> {
                without.indexOfFirst { it.id == currentId }.let { if (it < 0) -1 else it }
            }
            else -> without.indexOfFirst { it.id == currentId }.let { if (it < 0) 0 else it }
        }
        val insertAt = if (curIdx < 0) {
            pendingIndex.coerceIn(0, without.size)
        } else {
            (curIdx + 1).coerceIn(0, without.size)
        }
        val q = without.toMutableList().also { it.add(insertAt, play) }
        _recommend.update { it.copy(feed = q) }
        playTrack(
            track = play,
            queue = q,
            resumeIfSame = false,
            sourceLabel = null,
            sourceId = null,
        )
        val src = _recommend.value.sourceLabel
        _toast.value = if (src.isNotBlank() && src != "搜索") {
            "已插播 · 原队列「$src」保留（$kept→${q.size}）"
        } else {
            "已插播到队列（共 ${q.size} 首）"
        }
    }

    /** 展开多分 P / ugc 合集（仅收藏整部时用） */
    private suspend fun expandIfSeries(track: Track): List<Track> {
        val expanded = runCatching { biliApi.expandSeries(track) }.getOrDefault(emptyList())
        return if (expanded.size > 1) expanded else listOf(track)
    }

    /** 收藏整部合集到本地歌单 */
    fun collectSeriesToLocal(playlistId: String, track: Track? = null) {
        viewModelScope.launch {
            val seed = track ?: _collect.value.track ?: playback.value.current ?: return@launch
            val series = expandIfSeries(seed)
            val added = series.count { localPl.addTrack(playlistId, it) }
            if (added > 0) recordEvent(seed, RecommendationEventType.COLLECT_LOCAL)
            val name = localPl.list().firstOrNull { it.id == playlistId }?.title ?: "歌单"
            _collect.update { it.copy(visible = false) }
            _toast.value = "已将合集 ${series.size} 集加入本地「$name」"
            refreshHome()
        }
    }

    fun collectCreateSeriesAndAdd(title: String) {
        viewModelScope.launch {
            val seed = _collect.value.track ?: playback.value.current ?: return@launch
            val name = title.trim().ifBlank { "合集" }
            val pl = localPl.create(name)
            collectSeriesToLocal(pl.id, seed)
        }
    }

    fun collectSeriesToSelectedBiliFolder() {
        val id = _collect.value.selectedBiliFolderId ?: return
        collectSeriesToBili(id)
    }

    /** 收藏整部合集到 B 站夹（多稿件逐个 deal） */
    fun collectSeriesToBili(folderId: String, track: Track? = null) {
        viewModelScope.launch {
            val seed = track ?: _collect.value.track ?: playback.value.current ?: return@launch
            val series = expandIfSeries(seed)
            _collect.update { it.copy(biliSyncing = true) }
            var ok = 0
            var lastErr: String? = null
            // 同 aid 只收藏一次（多 P 同一稿件）
            val byAid = series.groupBy { it.aid.ifBlank { it.bvid } }
            for ((_, parts) in byAid) {
                val one = parts.first()
                val err = biliApi.addToFavFolder(one, folderId)
                if (err == null) {
                    ok++
                    recordEvent(one, RecommendationEventType.COLLECT_BILIBILI)
                } else {
                    lastErr = err
                }
            }
            _collect.update { it.copy(biliSyncing = false, visible = false) }
            _toast.value = if (ok > 0) {
                "已同步 B 站收藏 $ok 个稿件" + if (lastErr != null) "（部分失败）" else ""
            } else {
                "同步失败：${lastErr ?: "未知错误"}"
            }
            refreshHome()
        }
    }

    /**
     * 清屏短视频内搜索点播：
     * - 同样 **插播不替换** 原队列/歌单
     * - **不 toast**（不要「为你推荐 43→44」）
     * - 开视频流并占位，由 UI 跳回清屏
     */
    fun playSearchTrackFromImmersiveVideo(track: Track) {
        // 清屏内搜索：同样只播点中的一首，不展开合集
        ensureVideoModeEnabled()
        val play = track.copy(
            title = track.title.replace(Regex("""\s*·\s*\d+P$"""), ""),
        )
        player.prepareTrack(play, asVideo = true)
        if (pendingQueue.isEmpty()) {
            playTrack(
                track = play,
                queue = listOf(play),
                resumeIfSame = false,
                sourceLabel = "搜索",
                sourceId = "search",
            )
            return
        }
        val currentId = pendingQueue.getOrNull(pendingIndex)?.id
        val without = pendingQueue.filter { it.id != play.id }
        val curIdx = without.indexOfFirst { it.id == currentId }.let { if (it < 0) -1 else it }
        val insertAt = if (curIdx < 0) {
            pendingIndex.coerceIn(0, without.size)
        } else {
            (curIdx + 1).coerceIn(0, without.size)
        }
        val q = without.toMutableList().also { it.add(insertAt, play) }
        _recommend.update { it.copy(feed = q) }
        playTrack(
            track = play,
            queue = q,
            resumeIfSame = false,
            sourceLabel = null,
            sourceId = null,
        )
    }

    /**
     * @param startPos 显式起点；&lt;0 表示自动从会话/最近播放记忆续播。
     */
    /**
     * @param skipDelta 取流失败时跳过方向：+1 下一首，-1 上一首（修「上一首却跳到新歌」）
     */
    private suspend fun playIndex(
        index: Int,
        startPos: Long = -1L,
        skipBudget: Int = 6,
        skipDelta: Int = 1,
    ) {
        if (pendingQueue.isEmpty()) return
        val i = index.coerceIn(0, pendingQueue.lastIndex)
        pendingIndex = i
        val track = pendingQueue[i]
        val resolvedStart = if (startPos >= 0L) {
            startPos
        } else {
            resumePositionOf(track.id, track.durationMs)
        }
        // 听歌：先把封面/标题切过去并显示加载，避免还停在上一首像卡住
        if (!isVideoPlayback()) {
            player.prepareTrack(track, asVideo = false)
        }
        // 先占前台服务（服务内立刻 startForeground），取流期间进程不被杀
        ensureService()
        // 熄屏/弱网：多试几次 + 允许用预取 CDN，避免「暂无可播地址」连环跳
        var resolved = resolveOne(track)
        if (resolved == null || resolved.streamUrl.isNullOrBlank()) {
            delay(450)
            ensureService()
            resolved = resolveOne(track, forceRefresh = true)
        }
        if (resolved == null || resolved.streamUrl.isNullOrBlank()) {
            delay(900)
            resolved = resolveOne(track, forceRefresh = true)
        }
        if (resolved == null || resolved.streamUrl.isNullOrBlank()) {
            delay(1400)
            resolved = resolveOne(track, forceRefresh = true)
        }
        if (resolved == null || resolved.streamUrl.isNullOrBlank()) {
            // 有限次跳过坏链：方向与调用方一致（上一首失败继续往前找，不是往后跳）
            if (skipBudget > 0 && pendingQueue.size > 1) {
                val delta = if (skipDelta == 0) 1 else skipDelta
                val next = (i + delta).let { n ->
                    when {
                        n < 0 -> if (_playMode.value == PlayModeLabel.LOOP) pendingQueue.lastIndex else -1
                        n > pendingQueue.lastIndex ->
                            if (_playMode.value == PlayModeLabel.LOOP) 0 else -1
                        else -> n
                    }
                }
                if (next >= 0 && next != i) {
                    android.util.Log.w("AppViewModel", "skip unplayable ${track.title} → $next")
                    // 与本次切歌一致：显式从头则继续从头，避免跳坏链后又落到历史进度
                    val contStart = if (startPos >= 0L) 0L else -1L
                    playIndex(next, contStart, skipBudget - 1, delta)
                    return
                }
            }
            // 展示错误，停在当前（保持 index，勿乱跳）
            player.dispatch(PlayerCommand.PlayTrack(track, listOf(track), resolvedStart))
            return
        }
        // 以队列下标为准，保留原 id，避免解析后 id 漂导致上一首对不上
        pendingIndex = i
        pendingQueue = pendingQueue.toMutableList().also { list ->
            if (i in list.indices) {
                val old = list[i]
                list[i] = resolved.copy(
                    id = old.id,
                    title = old.title.ifBlank { resolved.title },
                    artist = old.artist.ifBlank { resolved.artist },
                    album = old.album.ifBlank { resolved.album },
                    ownerMid = old.ownerMid.ifBlank { resolved.ownerMid },
                    ownerFace = resolved.ownerFace.ifBlank { old.ownerFace },
                    durationMs = when {
                        resolved.durationMs > 0L -> resolved.durationMs
                        else -> old.durationMs
                    },
                )
            }
        }
        val playTrack = pendingQueue[i]
        _queueTracks.value = pendingQueue
        applyPlayModeToPlayer()
        // 先起播，再二次 ensure（MediaSession 能拿到当前曲元数据）
        player.dispatch(PlayerCommand.PlayTrack(playTrack, listOf(playTrack), resolvedStart))
        ensureService()
        rememberPosition(playTrack.id, resolvedStart)
        pushRecent(playTrack, resolvedStart)
        sessionSeenIds.add(playTrack.id)
        if (isForYouQueue()) {
            if (!sessionStartedIds.add(playTrack.id)) {
                sessionReplayedIds.add(playTrack.id)
                recordEvent(playTrack, RecommendationEventType.REPLAY)
            } else {
                recordEvent(playTrack, RecommendationEventType.PLAY_START)
            }
        }
        // 推荐流播放中后台预取
        if (isForYouQueue()) scheduleInfinitePrefetch()
        // 始终预解析下一首，熄屏切歌少踩 CDN 空窗
        schedulePrefetchNextStreams()
        requestLyrics(playTrack)
    }

    private fun requestLyrics(track: Track) {
        if (track.source != MusicSourceType.BILIBILI || track.bvid.isBlank()) {
            _lyrics.value = LyricsUiState()
            return
        }
        val key = "${track.bvid}:${track.cid}"
        val cur = _lyrics.value
        if (cur.key == key && !cur.loading && (cur.lines.isNotEmpty() || cur.unavailable)) return
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            _lyrics.value = LyricsUiState(key = key, loading = true)
            val sheet = runCatching { biliApi.fetchLyrics(track.bvid, track.cid) }.getOrNull()
            if (!isActive) return@launch
            val lines = sheet?.lines.orEmpty()
            _lyrics.value = LyricsUiState(
                key = key,
                lines = lines,
                loading = false,
                unavailable = lines.isEmpty(),
                language = sheet?.language.orEmpty(),
            )
        }
    }

    /** 预解析数量：由用户网络档位决定（不关功能） */
    private fun prefetchAheadCount(): Int {
        val net = MadusApp.instance.networkIntensity
        return if (MadusApp.instance.appInBackground) {
            net.prefetchBackground
        } else {
            net.prefetchForeground
        }
    }

    /** 预解析后续 playurl；最省档可跳过 */
    private fun schedulePrefetchNextStreams() {
        viewModelScope.launch {
            val bg = MadusApp.instance.appInBackground
            val net = MadusApp.instance.networkIntensity
            val ahead = prefetchAheadCount()
            if (ahead <= 0) return@launch
            // 后台延迟，先把带宽让给当前曲和游戏
            if (bg) delay(net.backgroundPrefetchDelayMs)
            val from = pendingIndex + 1
            val to = minOf(pendingIndex + ahead, pendingQueue.size)
            if (from >= to) return@launch
            val thrift = net.thriftFeed(bg) || net == com.madus.mobile.data.NetworkIntensity.MINIMAL
            for (i in from until to) {
                val t = pendingQueue.getOrNull(i) ?: continue
                val url = t.streamUrl
                val durable = !url.isNullOrBlank() &&
                    (url.startsWith("file:") || (url.startsWith("/") && !url.startsWith("http")))
                if (durable) continue
                // 已有 http 预取也不强刷，留给 play 时再取；空地址才预取
                if (!url.isNullOrBlank() && url.startsWith("http")) continue
                val r = resolveOne(
                    t.copy(streamUrl = null),
                    forceRefresh = !thrift,
                ) ?: continue
                pendingQueue = pendingQueue.toMutableList().also { list ->
                    if (i in list.indices && list[i].id == r.id) list[i] = r
                }
                _queueTracks.value = pendingQueue
            }
        }
    }

    /**
     * 连播当前稿件的合集（多 P / ugc 系列）。
     * 不改搜索「点单集只播单集」默认；仅用户主动点「连播合集」时调用。
     */
    fun playSeriesContinuous(track: Track? = null) {
        viewModelScope.launch {
            val seed = track ?: playback.value.current ?: return@launch
            ensureService()
            _toast.value = "正在展开合集…"
            val series = expandIfSeries(seed)
            if (series.size <= 1) {
                _toast.value = "这是单集视频，没有可连播的合集"
                return@launch
            }
            // 从当前集在合集中的位置起播（找不到则从第 1 集）
            val start = series.indexOfFirst {
                it.id == seed.id || (it.bvid == seed.bvid && it.cid == seed.cid) || it.bvid == seed.bvid
            }.let { if (it < 0) 0 else it }
            val startTrack = series[start]
            playTrack(
                track = startTrack,
                queue = series,
                loopAll = false,
                resumeIfSame = false,
                sourceLabel = "合集连播 · ${series.size} 集",
                sourceId = "series-${seed.bvid.ifBlank { seed.id }}",
            )
            _toast.value = "合集连播 · 共 ${series.size} 集（从第 ${start + 1} 集）"
        }
    }

    fun togglePlay() {
        ensureService()
        val pb = playback.value
        if (pb.isPlaying) {
            viewModelScope.launch {
                pb.current?.let {
                    runCatching { recentStore.savePosition(it.id, pb.positionMs) }
                }
            }
        }
        player.dispatch(PlayerCommand.Toggle)
    }

    fun next() {
        viewModelScope.launch { advanceToNext(userInitiated = true) }
    }

    /**
     * @param userInitiated 用户点下一首 / 通知栏；false = 曲终自动。
     * @param recordSkipFast 播放器报错跳歌时必须显式关掉，避免误记快速跳过。
     */
    private suspend fun advanceToNext(
        userInitiated: Boolean,
        recordSkipFast: Boolean = userInitiated,
    ) {
        ensureService()
        // 曲终/用户切歌都先记进度，滑回来才能续播
        persistCurrentPosition()
        if (userInitiated && recordSkipFast) maybeRecordSkipFast()

        if (pendingQueue.isEmpty()) {
            // 切勿再 dispatch Next：空队列 + Ended 回调会与这里形成死循环导致卡死
            player.dispatch(PlayerCommand.Pause)
            return
        }

        val isForYou = isForYouQueue()
        // 推荐流：提前自动续刷，像抖音一样刷不完
        if (isForYou) {
            runCatching { ensureInfiniteFeed() }
        }

        val mode = _playMode.value
        val atEnd = pendingIndex + 1 >= pendingQueue.size
        // 听歌切歌从头播；视频上下滑读记忆，滑回来接着看。
        val switchStart = startPosForNeighborSwitch()
        when {
            !atEnd -> {
                playIndex(pendingIndex + 1, startPos = switchStart)
                if (isForYou) scheduleInfinitePrefetch()
            }
            isForYou -> {
                // 见底：再强扩一次；绝不回到第一首（除非完全没新内容）
                runCatching { ensureInfiniteFeed(force = true) }
                if (pendingIndex + 1 < pendingQueue.size) {
                    playIndex(pendingIndex + 1, startPos = switchStart)
                    scheduleInfinitePrefetch()
                } else {
                    _toast.value = "正在加载更多推荐…"
                    runCatching { ensureInfiniteFeed(force = true, minAdd = 12) }
                    if (pendingIndex + 1 < pendingQueue.size) {
                        playIndex(pendingIndex + 1, startPos = switchStart)
                    } else {
                        // 真没了才停，不循环
                        player.dispatch(PlayerCommand.Pause)
                        _toast.value = "暂时没有更多，稍后再刷"
                    }
                }
            }
            mode == PlayModeLabel.LOOP || mode == PlayModeLabel.SHUFFLE -> {
                playIndex(0, startPos = switchStart)
            }
            mode == PlayModeLabel.SINGLE && userInitiated -> {
                playIndex(
                    if (pendingQueue.size > 1) (pendingIndex + 1) % pendingQueue.size else 0,
                    startPos = switchStart,
                )
            }
            else -> {
                player.dispatch(PlayerCommand.Pause)
            }
        }
    }

    /**
     * 是否「为你推荐」无限流。
     * **只用 sourceId 严格判断**，禁止用歌单名包含「推荐/为你」来猜——
     * 网易云/QQ 导入歌单常带「每日推荐」「官方推荐」等字样，误判后会往播放列表
     * 自动塞 related/热门视频，歌单播完也无法循环。
     */
    private fun isForYouQueue(): Boolean {
        return _recommend.value.sourceId == "recommend"
    }

    private fun scheduleInfinitePrefetch() {
        if (!isForYouQueue()) return
        expandJob?.cancel()
        expandJob = viewModelScope.launch {
            val bg = MadusApp.instance.appInBackground
            val net = MadusApp.instance.networkIntensity
            delay(
                when {
                    bg && net == com.madus.mobile.data.NetworkIntensity.MINIMAL -> 5_000L
                    bg -> 3_000L
                    net == com.madus.mobile.data.NetworkIntensity.MINIMAL -> 1_500L
                    else -> 400L
                },
            )
            val remain = pendingQueue.size - pendingIndex - 1
            if (bg) {
                val th = net.backgroundFeedRemainThreshold
                // 最省：后台不主动续推荐
                if (th < 0) return@launch
                if (remain > th) return@launch
                runCatching {
                    ensureInfiniteFeed(
                        force = false,
                        minAdd = net.thriftFeedMinAdd,
                        thriftNet = true,
                    )
                }
            } else if (net.thriftFeed(appInBackground = false) ||
                net == com.madus.mobile.data.NetworkIntensity.MINIMAL
            ) {
                if (remain > 3) return@launch
                runCatching {
                    ensureInfiniteFeed(
                        force = false,
                        minAdd = net.thriftFeedMinAdd,
                        thriftNet = true,
                    )
                }
            } else {
                runCatching { ensureInfiniteFeed(minAdd = net.thriftFeedMinAdd) }
            }
        }
    }

    fun previous() {
        viewModelScope.launch {
            ensureService()
            persistCurrentPosition()
            if (pendingQueue.isEmpty()) {
                // 空队列不要再 Previous，避免与引擎回调打架
                return@launch
            }
            // 用当前曲 id 校准 index；歌单有重复 id 时优先保留已对准的下标，
            // 避免 indexOfFirst 总跳回第一首导致「上一首仍是这首」。
            val curId = playback.value.current?.id
            if (curId != null) {
                val at = pendingQueue.getOrNull(pendingIndex)
                if (at?.id != curId) {
                    val real = pendingQueue.indexOfFirst { it.id == curId }
                    if (real >= 0) pendingIndex = real
                }
            }
            val pb = playback.value
            val canWrap = pendingQueue.size > 1 &&
                (_playMode.value == PlayModeLabel.LOOP || _playMode.value == PlayModeLabel.SHUFFLE)

            // 队首 + 循环：上一首直接到队尾。
            // 不要先走「播过 3s 重头」——否则第一首要点两次才能到最后一首。
            val video = isVideoPlayback()
            val switchStart = startPosForNeighborSwitch()
            if (pendingIndex <= 0) {
                if (canWrap) {
                    playIndex(pendingQueue.lastIndex, startPos = switchStart, skipDelta = -1)
                } else {
                    player.dispatch(PlayerCommand.Seek(0))
                }
                return@launch
            }

            // 非队首：纯音乐播过约 3s 再点上一首 = 重头播当前（常见播放器习惯）
            // 视频模式 / 竖屏上下滑：始终切上一条，并用记忆续播
            if (!video && pb.positionMs > 3_000) {
                player.dispatch(PlayerCommand.Seek(0))
                return@launch
            }
            // skipDelta=-1：取流失败也往「更早」找，绝不跳到新歌
            playIndex(pendingIndex - 1, startPos = switchStart, skipDelta = -1)
        }
    }

    fun seek(ms: Long) {
        ensureService()
        player.dispatch(PlayerCommand.Seek(ms))
        val id = playback.value.current?.id
        if (id != null) {
            rememberPosition(id, ms)
            viewModelScope.launch {
                runCatching { recentStore.savePosition(id, ms) }
            }
        }
    }

    fun removeFromQueue(trackId: String) {
        val q = pendingQueue.toMutableList()
        val idx = q.indexOfFirst { it.id == trackId }
        if (idx < 0) return
        q.removeAt(idx)
        if (idx < pendingIndex) pendingIndex--
        else if (idx == pendingIndex) {
            pendingIndex = pendingIndex.coerceAtMost((q.size - 1).coerceAtLeast(0))
        }
        pendingQueue = q
        _queueTracks.value = q
        if (playback.value.current?.id == trackId) {
            if (q.isEmpty()) clearQueue()
            else {
                val next = q[pendingIndex.coerceIn(0, q.lastIndex)]
                playTrack(next, q, resumeIfSame = false)
            }
        }
    }

    fun queuePlayNext(track: Track) {
        val q = pendingQueue.toMutableList()
        q.removeAll { it.id == track.id }
        val insertAt = (pendingIndex + 1).coerceIn(0, q.size)
        q.add(insertAt, track)
        pendingQueue = q
        _queueTracks.value = q
        _toast.value = "已设为下一首：${track.title}"
    }

    /** 播放队列长按排序：from/to 为整表下标 */
    fun moveInQueue(fromIndex: Int, toIndex: Int) {
        val q = pendingQueue.toMutableList()
        if (fromIndex !in q.indices || toIndex !in q.indices) return
        if (fromIndex == toIndex) return
        val currentId = playback.value.current?.id
        val item = q.removeAt(fromIndex)
        q.add(toIndex, item)
        pendingQueue = q
        _queueTracks.value = q
        if (currentId != null) {
            val ni = q.indexOfFirst { it.id == currentId }
            if (ni >= 0) pendingIndex = ni
        }
    }

    /** 关闭面板：只隐藏，不清结果、不取消进行中的识别 */
    fun dismissBgmSheet() {
        _bgm.update { it.copy(visible = false) }
    }

    /**
     * 推荐页悬浮球：读当前稿件官方 BGM 标签 → B 站搜可播。
     * 与 AI 搜「B站识曲」同源，不恢复旧 LLM 半屏面板。
     */
    fun recognizeBiliBgmOnCurrent() {
        val current = playback.value.current
        if (current == null) {
            _toast.value = "没有正在播放的内容"
            return
        }
        val bv = current.bvid.ifBlank { BilibiliApi.parseBvid(current.id).orEmpty() }
        if (bv.isBlank()) {
            _toast.value = "当前不是 B 站稿件，无法识曲"
            return
        }
        if (_biliRecognize.value.loading) {
            _biliRecognize.update { it.copy(panelVisible = true) }
            return
        }
        // 同曲已有结果：直接打开面板
        if (_biliRecognize.value.sourceTrackId == current.id &&
            (_biliRecognize.value.tracks.isNotEmpty() || _biliRecognize.value.guessLabel != null)
        ) {
            _biliRecognize.update { it.copy(panelVisible = true) }
            return
        }

        biliRecognizeJob?.cancel()
        _biliRecognize.value = BiliRecognizeUiState(
            panelVisible = true,
            loading = true,
            sourceTrackId = current.id,
            sourceTitle = current.title,
        )
        val positionMs = playback.value.positionMs
        biliRecognizeJob = viewModelScope.launch {
            try {
                val tags = withContext(Dispatchers.IO) {
                    biliApi.recognizeBgm(bv, current.cid, positionMs)
                }
                val candidates = tags.mapNotNull { tag ->
                    val title = tag.title.ifBlank { tag.tagName }.trim()
                    val artist = tag.artist?.takeIf { it.isNotBlank() }
                    if (title.isBlank()) return@mapNotNull null
                    com.madus.mobile.ai.SongCandidate(
                        title = title,
                        artist = artist,
                        confidence = 0.92f,
                        bilibiliQuery = listOfNotNull(title, artist).joinToString(" "),
                        note = "B站官方BGM",
                    )
                }.distinctBy { it.title.lowercase() }

                if (candidates.isEmpty()) {
                    _biliRecognize.value = BiliRecognizeUiState(
                        panelVisible = true,
                        loading = false,
                        sourceTrackId = current.id,
                        sourceTitle = current.title,
                        error = "B站未识别到曲目（稿件可能未标注 BGM 标签）",
                    )
                    return@launch
                }

                val tracks = withContext(Dispatchers.IO) {
                    searchBiliForSongCandidates(candidates)
                }
                val top = candidates.first()
                _biliRecognize.value = BiliRecognizeUiState(
                    panelVisible = true,
                    loading = false,
                    sourceTrackId = current.id,
                    sourceTitle = current.title,
                    guessLabel = listOfNotNull(top.title, top.artist).joinToString(" · "),
                    tracks = tracks,
                    error = if (tracks.isEmpty()) "识别到「${top.title}」，但 B 站暂无匹配" else null,
                )
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _biliRecognize.value = BiliRecognizeUiState(
                    panelVisible = true,
                    loading = false,
                    sourceTrackId = current.id,
                    sourceTitle = current.title,
                    error = t.message ?: "识曲失败",
                )
            }
        }
    }

    fun dismissBiliRecognizePanel() {
        _biliRecognize.update { it.copy(panelVisible = false) }
    }

    fun playBiliRecognizeTrack(track: Track, queue: List<Track>) {
        playTrack(
            track = track,
            queue = queue.ifEmpty { listOf(track) },
            sourceLabel = "B站识曲",
            sourceId = "bili_recognize",
        )
        dismissBiliRecognizePanel()
    }

    /** 识曲/哼唱落地：音乐区优先 + 歌名相关度 */
    private suspend fun searchBiliForSongCandidates(
        candidates: List<com.madus.mobile.ai.SongCandidate>,
    ): List<Track> {
        val top = candidates.take(4)
        if (top.isEmpty()) return emptyList()
        val seen = LinkedHashSet<String>()
        val out = ArrayList<Track>()
        for (c in top) {
            val queries = com.madus.mobile.ai.SongRanker.buildSearchQueries(c, max = 4)
            for (q in queries) {
                val music = runCatching { biliApi.searchMusic(q, limit = 12) }.getOrDefault(emptyList())
                val list = if (music.size >= 3) {
                    music
                } else {
                    val gen = runCatching { biliApi.search(q, limit = 12) }.getOrDefault(emptyList())
                    (music + gen).distinctBy { it.id }
                }
                for (t in list) {
                    if (com.madus.mobile.ai.SongRanker.isHardGarbageTitle(t.title)) continue
                    if (com.madus.mobile.ai.SongRanker.bestTrackScore(t, top) < 12) continue
                    if (seen.add(t.id)) {
                        out.add(t)
                        if (out.size >= 30) {
                            return com.madus.mobile.ai.SongRanker.rankTracks(out, top, minScore = 16)
                        }
                    }
                }
            }
        }
        val strict = com.madus.mobile.ai.SongRanker.rankTracks(out, top, minScore = 16)
        return strict.ifEmpty {
            com.madus.mobile.ai.SongRanker.rankTracks(out, top, minScore = 10)
        }
    }

    /** BGM 面板「外语 BGM」开关（本机记住） */
    fun setBgmPreferForeign(on: Boolean) {
        bgmPrefs.setBgmPreferForeign(on)
        _bgm.update { it.copy(preferForeignSong = on) }
    }

    fun toggleBgmPreferForeign() {
        setBgmPreferForeign(!_bgm.value.preferForeignSong)
    }

    /**
     * 悬浮球点击：只打开面板，**不自动开跑**。
     * 方便先选模型 / 开「外语 BGM」，再点「开始识别」。
     */
    fun onBgmFabClick() {
        val current = playback.value.current
        if (current == null) {
            _toast.value = "没有正在播放的内容"
            return
        }
        openBgmPanel(current)
    }

    /** 打开面板；同曲保留结果/进度/空闲态，换曲则进空闲待识别 */
    private fun openBgmPanel(current: Track) {
        val st = _bgm.value
        val foreign = st.preferForeignSong
        val profile = MadusApp.instance.llmConfigStore.state.value.bgmProfile
        val modelLabel = profile?.let { "${it.name} · ${it.modelId}" }.orEmpty()
            .ifBlank { st.modelLabel }

        // 同曲：只保证可见（不自动开跑）
        if (st.sourceTrackId == current.id) {
            _bgm.update {
                it.copy(
                    visible = true,
                    sourceTitle = current.title,
                    modelLabel = modelLabel.ifBlank { it.modelLabel },
                )
            }
            return
        }

        // 换曲：停掉上一曲任务，空闲等待「开始识别」
        if (st.loading) {
            bgmJob?.cancel()
            bgmJob = null
        }
        _bgm.value = BgmUiState(
            visible = true,
            loading = false,
            status = null,
            error = null,
            sourceTrackId = current.id,
            sourceTitle = current.title,
            modelLabel = modelLabel,
            preferForeignSong = foreign,
            reply = "",
            guessLabel = "",
            candidates = emptyList(),
            tracks = emptyList(),
        )
    }

    /** 面板内「开始识别 / 重新识别」 */
    fun reidentifyCurrentBgm() {
        startBgmIdentify()
    }

    /** 取消正在进行的截取/识别（类似 Ctrl+C） */
    fun cancelBgmIdentify() {
        val job = bgmJob
        bgmJob = null
        job?.cancel()
        val current = playback.value.current
        val foreign = _bgm.value.preferForeignSong
        val modelLabel = _bgm.value.modelLabel
        _bgm.update { st ->
            st.copy(
                visible = true,
                loading = false,
                status = null,
                error = null,
                reply = "已取消识别。可改模型/外语开关后点「开始识别」。",
                guessLabel = "",
                candidates = emptyList(),
                tracks = emptyList(),
                sourceTrackId = current?.id ?: st.sourceTrackId,
                sourceTitle = current?.title ?: st.sourceTitle,
                modelLabel = modelLabel,
                preferForeignSong = foreign,
            )
        }
        _toast.value = "已取消 BGM 识别"
    }

    /** @deprecated 菜单入口已弃用，转 [onBgmFabClick] */
    fun identifyCurrentBgm() = onBgmFabClick()

    private fun startBgmIdentify() {
        val current = playback.value.current
        if (current == null) {
            _toast.value = "没有正在播放的内容"
            return
        }
        if (_bgm.value.loading) {
            _bgm.update { it.copy(visible = true) }
            return
        }

        val store = MadusApp.instance.llmConfigStore
        val cfg = store.state.value
        val profile = cfg.bgmProfile
        if (profile == null) {
            _toast.value = "请先在「AI 搜歌」里配置模型（推荐 MiMo 普通档）"
            return
        }
        if (!profile.effectiveCapabilities().audioInput) {
            _toast.value = "当前模型不支持听音频，请在面板里换 MiMo / 千问 Omni"
            return
        }
        val apiKey = store.getApiKey(profile.id)
        if (apiKey.isNullOrBlank()) {
            _toast.value = "当前模型未保存 API Key"
            return
        }

        bgmJob?.cancel()
        val pos = playback.value.positionMs
        val startMs = (pos - 1_500L).coerceAtLeast(0L)
        val modelLabel = "${profile.name} · ${profile.modelId}"
        val foreign = _bgm.value.preferForeignSong
        _bgm.value = BgmUiState(
            visible = true,
            loading = true,
            status = if (foreign) "提取音轨（外语 BGM）…" else "提取音轨…",
            sourceTrackId = current.id,
            sourceTitle = current.title,
            modelLabel = modelLabel,
            preferForeignSong = foreign,
        )
        bgmJob = viewModelScope.launch {
            try {
                val audioTrack = withContext(Dispatchers.IO) {
                    runCatching {
                        biliApi.ensureGuestCookies()
                        biliApi.resolvePlayUrl(
                            current,
                            preferredQn = MadusApp.instance.currentQualityQn,
                            videoMode = false,
                        )
                    }.getOrElse { e ->
                        error("取音频失败：${e.message ?: "网络错误"}")
                    }
                }
                if (!isActive) return@launch
                val streamUrl = audioTrack.streamUrl?.takeIf { it.isNotBlank() }
                    ?: error("没有可用的音频流")
                _bgm.update {
                    it.copy(
                        status = if (_bgm.value.preferForeignSong) {
                            "截取片段（外语）…"
                        } else {
                            "截取片段…"
                        },
                    )
                }
                // clipToWav 内部已有总超时；这里再兜一层，避免 UI 永久转圈
                val wav = kotlinx.coroutines.withTimeoutOrNull(
                    com.madus.mobile.ai.BgmAudioClipper.TOTAL_TIMEOUT_MS + 5_000L,
                ) {
                    com.madus.mobile.ai.BgmAudioClipper.clipToWav(
                        context = MadusApp.instance,
                        streamUrl = streamUrl,
                        startMs = startMs,
                        durationMs = com.madus.mobile.ai.BgmAudioClipper.CLIP_MS,
                    )
                }?.getOrElse { e ->
                    error(e.message ?: "截取音频失败")
                } ?: error("截取超时，请换播放进度或稍后重试")
                if (!isActive) {
                    runCatching { wav.delete() }
                    return@launch
                }
                val b64 = runCatching {
                    com.madus.mobile.ai.MediaEncode.fileToBase64(wav)
                }.getOrNull()
                runCatching { wav.delete() }
                if (b64.isNullOrBlank()) error("音频编码失败")
                if (b64.length > 5_500_000) error("音频片段过大，请换短一点的进度再试")

                // 识别时再读一次开关（用户可能中途改过）
                val preferForeign = _bgm.value.preferForeignSong
                _bgm.update {
                    it.copy(
                        status = if (preferForeign) "识别外语 BGM…" else "识别 BGM…",
                    )
                }
                val source = registry.get(MusicSourceType.BILIBILI)
                val result = com.madus.mobile.ai.BgmSongIdentifier.identifyFromWavBase64(
                    profile = profile,
                    apiKey = apiKey,
                    audioBase64 = b64,
                    audioFormat = "wav",
                    preferForeign = preferForeign,
                    searchTracks = { q ->
                        if (source == null) {
                            emptyList()
                        } else {
                            val music = runCatching {
                                biliApi.searchMusic(q, limit = 12)
                            }.getOrDefault(emptyList())
                            if (music.size >= 4) {
                                music
                            } else {
                                val generic = runCatching {
                                    source.search(q, limit = 12)
                                }.getOrDefault(emptyList())
                                (music + generic).distinctBy { it.id }.take(16)
                            }
                        }
                    },
                )
                if (!isActive) return@launch
                val top = result.candidates.firstOrNull()
                val guessLabel = listOfNotNull(top?.title, top?.artist).joinToString(" · ")
                // 完成时：若用户关掉了面板，不强制弹回；结果保留，球可再开
                val keepVisible = _bgm.value.visible
                val foreignNow = _bgm.value.preferForeignSong
                _bgm.value = BgmUiState(
                    visible = keepVisible,
                    loading = false,
                    status = null,
                    error = if (result.tracks.isEmpty() && result.candidates.isEmpty()) {
                        result.reply
                    } else {
                        null
                    },
                    sourceTrackId = current.id,
                    sourceTitle = current.title,
                    reply = result.reply,
                    guessLabel = guessLabel,
                    candidates = result.candidates,
                    tracks = result.tracks,
                    modelLabel = modelLabel,
                    preferForeignSong = foreignNow,
                )
                if (!keepVisible && (result.candidates.isNotEmpty() || result.tracks.isNotEmpty())) {
                    _toast.value = "BGM 识别完成，点左侧悬浮球查看"
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) {
                    // cancelBgmIdentify 已写 UI；若仍卡在 loading 则兜底
                    _bgm.update { st ->
                        if (!st.loading) st
                        else st.copy(
                            loading = false,
                            status = null,
                            reply = "已取消识别。可改模型/外语开关后点「开始识别」。",
                            guessLabel = "",
                            candidates = emptyList(),
                            tracks = emptyList(),
                            error = null,
                        )
                    }
                    return@launch
                }
                val keepVisible = _bgm.value.visible
                val foreignNow = _bgm.value.preferForeignSong
                _bgm.value = BgmUiState(
                    visible = keepVisible,
                    loading = false,
                    error = t.message ?: t.javaClass.simpleName,
                    sourceTrackId = current.id,
                    sourceTitle = current.title,
                    modelLabel = modelLabel,
                    preferForeignSong = foreignNow,
                )
                if (!keepVisible) {
                    _toast.value = "BGM 识别失败：${t.message ?: "未知错误"}"
                }
            }
        }
    }

    /** BGM 面板切换模型（仅影响识 BGM，可与 AI 聊天默认模型不同） */
    fun setBgmModel(profileId: String) {
        viewModelScope.launch {
            MadusApp.instance.llmConfigStore.setBgmProfile(profileId)
            val p = MadusApp.instance.llmConfigStore.state.value.bgmProfile
            val label = p?.let { "${it.name} · ${it.modelId}" }.orEmpty()
            _bgm.update { it.copy(modelLabel = label) }
            _toast.value = if (p != null) "BGM 模型：${p.name}" else "已切换模型"
        }
    }

    /** BGM 结果：立即播放（插播语义） */
    fun playBgmTrackNow(track: Track) {
        playSearchTrack(track)
        _bgm.update { it.copy(visible = false) }
    }

    /** BGM 结果：仅排到下一首，不打断当前视频 */
    fun playBgmTrackNext(track: Track) {
        queuePlayNext(track)
    }

    fun shuffleQueue() {
        if (pendingQueue.size < 2) return
        val current = pendingQueue.getOrNull(pendingIndex)
        val rest = pendingQueue.filterIndexed { i, _ -> i != pendingIndex }.shuffled()
        val q = if (current != null) listOf(current) + rest else rest
        pendingQueue = q
        pendingIndex = 0
        _queueTracks.value = q
    }

    fun cyclePlayMode() {
        val next = when (_playMode.value) {
            PlayModeLabel.LOOP -> PlayModeLabel.SHUFFLE
            PlayModeLabel.SHUFFLE -> PlayModeLabel.SINGLE
            PlayModeLabel.SINGLE -> PlayModeLabel.LOOP
        }
        _playMode.value = next
        when (next) {
            PlayModeLabel.LOOP -> applyPlayModeToPlayer()
            PlayModeLabel.SHUFFLE -> {
                shuffleQueue()
                applyPlayModeToPlayer()
            }
            PlayModeLabel.SINGLE -> applyPlayModeToPlayer()
        }
    }

    /** Create only via collect sheet (not home). Never leave empty auto-named shells. */
    fun createLocalPlaylist(title: String) {
        viewModelScope.launch {
            localPl.create(title.ifBlank { defaultPlaylistName() })
            refreshHome()
            refreshLibrary()
        }
    }

    private fun defaultPlaylistName(): String {
        val c = Calendar.getInstance()
        return "收藏 · ${c.get(Calendar.MONTH) + 1}月${c.get(Calendar.DAY_OF_MONTH)}日"
    }

    /** 打开收藏：默认本地页；[openBiliTab]=true 直接进 B 站页 */
    fun openCollectSheet(track: Track? = null, openBiliTab: Boolean = false) {
        viewModelScope.launch {
            val t = track ?: playback.value.current ?: return@launch
            runCatching { localPl.purgeEmptyJunk(aggressive = true) }
            val lists = localPl.list().map { it.toPlaylist() }
            val biliOk = runCatching { biliApi.isLoggedInCookie() }.getOrDefault(false)
            val folders = if (biliOk) {
                runCatching {
                    biliApi.favFolders().map {
                        BiliFavOption(id = it.id, title = it.title, count = it.count)
                    }
                }.getOrDefault(emptyList())
            } else emptyList()
            val prev = _collect.value.selectedBiliFolderId
            val selected = when {
                prev != null && folders.any { it.id == prev } -> prev
                folders.isNotEmpty() -> folders.minByOrNull { it.count }?.id
                else -> null
            }
            // 探测是否合集（多 P / ugc season），未登录本地也可整部收藏
            val series = runCatching { expandIfSeries(t) }.getOrDefault(listOf(t))
            val seriesCount = series.size.coerceAtLeast(t.pageCount.coerceAtLeast(1))
            _collect.value = CollectUiState(
                visible = true,
                track = t,
                playlists = lists,
                tab = if (openBiliTab) CollectTab.Bili else CollectTab.Local,
                biliLoggedIn = biliOk,
                biliFolders = folders,
                selectedBiliFolderId = selected,
                seriesCount = seriesCount,
            )
        }
    }

    /** 独立「B 站收藏」入口 */
    fun openBiliCollectSheet(track: Track? = null) {
        openCollectSheet(track, openBiliTab = true)
    }

    fun dismissCollectSheet() {
        _collect.update { it.copy(visible = false, track = null, biliSyncing = false) }
    }

    fun setCollectTab(tab: CollectTab) {
        _collect.update { it.copy(tab = tab) }
    }

    fun setCollectBiliFolder(folderId: String) {
        _collect.update { it.copy(selectedBiliFolderId = folderId) }
    }

    /**
     * 新建 B 站收藏夹并把当前曲加进去（同步 B 站）。
     */
    fun createBiliFolderAndCollect(title: String) {
        viewModelScope.launch {
            val st = _collect.value
            val t = st.track ?: return@launch
            val name = title.trim().ifBlank { defaultPlaylistName() }
            _collect.update { it.copy(biliSyncing = true) }
            val (id, err) = runCatching { biliApi.createFavFolder(name) }
                .getOrElse { null to (it.message ?: "创建失败") }
            if (id.isNullOrBlank()) {
                _collect.update { it.copy(biliSyncing = false) }
                _toast.value = "新建收藏夹失败：${err ?: "未知错误"}"
                return@launch
            }
            val addErr = biliApi.addToFavFolder(t, id)
            if (addErr != null) {
                // 夹已建成功，刷新列表并选中
                val folders = runCatching {
                    biliApi.favFolders().map { BiliFavOption(id = it.id, title = it.title, count = it.count) }
                }.getOrDefault(emptyList())
                _collect.update {
                    it.copy(
                        biliSyncing = false,
                        biliFolders = folders,
                        selectedBiliFolderId = id,
                    )
                }
                _toast.value = "夹已创建，但加曲失败：$addErr"
                return@launch
            }
            recordEvent(t, RecommendationEventType.COLLECT_BILIBILI)
            val folders = runCatching {
                biliApi.favFolders().map { BiliFavOption(id = it.id, title = it.title, count = it.count) }
            }.getOrDefault(emptyList())
            _collect.value = CollectUiState(
                visible = false,
                toast = "已新建并收藏到 B 站「$name」",
                selectedBiliFolderId = id,
                biliFolders = folders,
            )
            refreshHome()
        }
    }

    fun collectToPlaylist(playlistId: String) {
        viewModelScope.launch {
            val state = _collect.value
            val t = state.track ?: return@launch
            val added = localPl.addTrack(playlistId, t)
            if (added) recordEvent(t, RecommendationEventType.COLLECT_LOCAL)
            val name = localPl.list().firstOrNull { it.id == playlistId }?.title ?: "歌单"
            _collect.value = CollectUiState(
                visible = false,
                toast = "已加入本地「$name」",
                selectedBiliFolderId = state.selectedBiliFolderId,
            )
            refreshHome()
            val open = _playlistDetail.value.playlist
            if (open?.id == playlistId) openPlaylist(open)
        }
    }

    fun collectCreateAndAdd(title: String) {
        viewModelScope.launch {
            val state = _collect.value
            val t = state.track ?: return@launch
            val name = title.trim().ifBlank { defaultPlaylistName() }
            val pl = localPl.create(name)
            val added = localPl.addTrack(pl.id, t)
            if (added) recordEvent(t, RecommendationEventType.COLLECT_LOCAL)
            _collect.value = CollectUiState(
                visible = false,
                toast = "已加入本地「${pl.title}」",
                selectedBiliFolderId = state.selectedBiliFolderId,
            )
            refreshHome()
        }
    }

    /** B 站页：选中夹后点「收藏到此夹」 */
    fun collectToSelectedBiliFolder() {
        viewModelScope.launch {
            val state = _collect.value
            val t = state.track ?: return@launch
            val folderId = state.selectedBiliFolderId
            if (folderId.isNullOrBlank()) {
                _toast.value = "请先选择一个 B 站收藏夹"
                return@launch
            }
            if (t.source != MusicSourceType.BILIBILI) {
                _toast.value = "非 B 站曲目，无法同步"
                return@launch
            }
            _collect.update { it.copy(biliSyncing = true) }
            val folderName = state.biliFolders.firstOrNull { it.id == folderId }?.title ?: "收藏夹"
            val err = runCatching { biliApi.addToFavFolder(t, folderId) }.getOrElse { e ->
                e.message ?: "同步失败"
            }
            if (err == null) {
                recordEvent(t, RecommendationEventType.COLLECT_BILIBILI)
                _collect.value = CollectUiState(
                    visible = false,
                    toast = "已收藏到 B 站「$folderName」",
                    selectedBiliFolderId = folderId,
                )
            } else {
                _collect.update { it.copy(biliSyncing = false) }
                _toast.value = "B 站收藏失败：$err"
            }
        }
    }

    // ── 队列内搜索（清屏后也能找新歌，插播不打乱原队列） ──────────

    fun onQueueSearchQueryChange(q: String) {
        _queueSearch.update { it.copy(query = q, message = null) }
    }

    fun submitQueueSearch() {
        viewModelScope.launch {
            val q = _queueSearch.value.query.trim()
            if (q.isEmpty()) {
                _queueSearch.update { it.copy(results = emptyList(), message = null) }
                return@launch
            }
            _queueSearch.update { it.copy(isSearching = true, message = null) }
            val results = runCatching {
                plainBiliSearch(q, limit = 24)
            }.getOrElse { e ->
                _queueSearch.update {
                    it.copy(isSearching = false, results = emptyList(), message = e.message ?: "搜索失败")
                }
                return@launch
            }
            _queueSearch.update {
                it.copy(
                    isSearching = false,
                    results = results,
                    message = if (results.isEmpty()) "没有匹配歌名的视频，试试加歌手名" else null,
                )
            }
        }
    }

    fun clearQueueSearch() {
        _queueSearch.value = QueueSearchUiState()
    }

    /** 队列页搜索点播：与主搜索相同（插播不替换） */
    fun playFromQueueSearch(track: Track) {
        if (_trackReplace.value.active) {
            applyReplaceTrack(track)
            return
        }
        playSearchTrack(track)
    }

    // ── 选择播放来源（换歌单听，不是加歌） ──────────────────────────

    fun openPlaySourceSheet() {
        viewModelScope.launch {
            val currentId = _recommend.value.sourceId
            val items = mutableListOf<PlaySourceItem>()
            items += PlaySourceItem(
                id = "recommend",
                title = "推荐电台",
                subtitle = "B 站收藏流",
                kind = PlaySourceKind.Recommend,
                selected = currentId == "recommend",
            )
            val recent = sessionRecent.asReversed().distinctBy { it.id }
            items += PlaySourceItem(
                id = "recent",
                title = "最近播放",
                subtitle = if (recent.isEmpty()) "还没有记录" else "${recent.size} 首",
                kind = PlaySourceKind.Recent,
                enabled = recent.isNotEmpty(),
                selected = currentId == "recent",
            )
            val likedPl = runCatching { likedStore.toPlaylist() }.getOrNull()
            val likedTracks = runCatching { likedStore.tracks() }.getOrDefault(emptyList())
            items += PlaySourceItem(
                id = LikedStore.LIKED_ID,
                title = LikedStore.LIKED_TITLE,
                subtitle = if (likedTracks.isEmpty()) "还没有喜欢的歌" else "${likedTracks.size} 首",
                kind = PlaySourceKind.LocalPlaylist,
                playlist = likedPl,
                enabled = likedTracks.isNotEmpty(),
                selected = currentId == LikedStore.LIKED_ID,
            )
            // Local playlists: non-empty playable; empty disabled at bottom
            val locals = runCatching { localPl.list() }.getOrDefault(emptyList())
            locals.filter { it.tracks.isNotEmpty() }.forEach { pl ->
                items += PlaySourceItem(
                    id = pl.id,
                    title = pl.title,
                    subtitle = "本地 · ${pl.tracks.size} 首",
                    kind = PlaySourceKind.LocalPlaylist,
                    playlist = pl.toPlaylist(),
                    selected = currentId == pl.id,
                )
            }
            locals.filter { it.tracks.isEmpty() }.forEach { pl ->
                items += PlaySourceItem(
                    id = pl.id,
                    title = pl.title,
                    subtitle = "空歌单，先加几首歌",
                    kind = PlaySourceKind.LocalPlaylist,
                    playlist = pl.toPlaylist(),
                    enabled = false,
                    selected = false,
                )
            }
            // B站收藏夹
            val bili = registry.get(MusicSourceType.BILIBILI)
            val session = bili?.getAuthSession()
            if (session?.isLoggedIn == true) {
                runCatching { bili.featuredPlaylists() }.getOrDefault(emptyList()).forEach { pl ->
                    items += PlaySourceItem(
                        id = pl.id,
                        title = pl.title,
                        subtitle = if (pl.trackCount > 0) "B站 · ${pl.trackCount} 首" else "B站 · 空",
                        kind = PlaySourceKind.BiliFav,
                        playlist = pl,
                        enabled = pl.trackCount > 0,
                        selected = currentId == pl.id,
                    )
                }
            }
            _playSource.value = PlaySourceUiState(visible = true, sources = items)
        }
    }

    fun dismissPlaySourceSheet() {
        _playSource.update { it.copy(visible = false) }
    }

    fun selectPlaySource(item: PlaySourceItem) {
        if (!item.enabled) return
        viewModelScope.launch {
            _playSource.update { it.copy(visible = false) }
            when (item.kind) {
                PlaySourceKind.Recommend -> {
                    _recommend.update {
                        it.copy(
                            sourceId = "recommend",
                            sourceLabel = "推荐电台",
                            segment = RecommendSegment.Feed,
                        )
                    }
                    // 只加载列表，不自动开播；用户点播放键再播
                    loadRecommendFeed(autoStart = false)
                }
                PlaySourceKind.Recent -> {
                    val recent = sessionRecent.asReversed().distinctBy { it.id }
                    if (recent.isEmpty()) return@launch
                    playTrack(
                        track = recent.first(),
                        queue = recent,
                        loopAll = true,
                        resumeIfSame = true,
                        sourceLabel = "最近播放",
                        sourceId = "recent",
                    )
                }
                PlaySourceKind.LocalPlaylist -> {
                    val pl = item.playlist ?: return@launch
                    val tracks = when {
                        pl.id == LikedStore.LIKED_ID ->
                            runCatching { likedStore.tracks() }.getOrDefault(emptyList())
                        else ->
                            runCatching { localPl.tracks(pl.id) }.getOrDefault(emptyList())
                    }
                    if (tracks.isEmpty()) return@launch
                    _recommend.update {
                        it.copy(
                            sourceId = pl.id,
                            sourceLabel = pl.title,
                            segment = RecommendSegment.Feed,
                            feed = tracks,
                        )
                    }
                    playTrack(
                        tracks.first(),
                        tracks,
                        loopAll = true,
                        resumeIfSame = false,
                        sourceLabel = pl.title,
                        sourceId = pl.id,
                    )
                }
                PlaySourceKind.BiliFav -> {
                    val pl = item.playlist ?: return@launch
                    _toast.value = "正在加载完整歌单…"
                    val tracks = runCatching {
                        registry.get(MusicSourceType.BILIBILI)
                            ?.playlistTracks(pl.id, limit = 0).orEmpty()
                    }.getOrDefault(emptyList())
                    if (tracks.isEmpty()) {
                        _collect.update { it.copy(toast = "该收藏夹暂无内容") }
                        return@launch
                    }
                    playTrack(
                        track = tracks.first(),
                        queue = tracks,
                        loopAll = true,
                        resumeIfSame = false,
                        sourceLabel = pl.title,
                        sourceId = pl.id,
                    )
                    _toast.value = "已加载 ${tracks.size} 首 · 顺序循环"
                }
            }
        }
    }

    fun clearCollectToast() {
        _collect.update { it.copy(toast = null) }
    }

    fun renameLocalPlaylist(playlistId: String, newTitle: String) {
        viewModelScope.launch {
            if (!localPl.rename(playlistId, newTitle)) return@launch
            refreshHome()
            val open = _playlistDetail.value.playlist
            if (open?.id == playlistId) {
                _playlistDetail.update {
                    it.copy(playlist = open.copy(title = newTitle.trim()))
                }
            }
        }
    }

    /**
     * 任意歌单封面（本地 / 我的喜欢 / B站收藏夹）均可上传。
     * 存本地文件 + CoverStore，不回写 B 站。
     */
    fun setPlaylistCover(playlistId: String, coverPath: String?) {
        viewModelScope.launch {
            val persisted = coverPath?.let { uri ->
                runCatching {
                    val ctx = MadusApp.instance
                    val input = ctx.contentResolver.openInputStream(android.net.Uri.parse(uri))
                        ?: return@runCatching uri
                    val dir = java.io.File(ctx.filesDir, "playlist_covers").also { it.mkdirs() }
                    val safe = playlistId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                    val outFile = java.io.File(dir, "$safe.jpg")
                    input.use { inp -> outFile.outputStream().use { out -> inp.copyTo(out) } }
                    outFile.absolutePath
                }.getOrDefault(uri)
            }
            coverStore.set(playlistId, persisted)
            // 兼容旧本地歌单字段
            if (playlistId.startsWith("local-") && playlistId != LikedStore.LIKED_ID) {
                runCatching { localPl.setCover(playlistId, persisted) }
            }
            if (playlistId == LikedStore.LIKED_ID) {
                runCatching { likedStore.setCover(persisted) }
            }
            refreshHome()
            val open = _playlistDetail.value.playlist
            if (open?.id == playlistId) {
                _playlistDetail.update {
                    it.copy(playlist = open.copy(coverUrl = persisted ?: open.coverUrl))
                }
            }
        }
    }

    @Deprecated("use setPlaylistCover", ReplaceWith("setPlaylistCover(playlistId, coverPath)"))
    fun setLocalPlaylistCover(playlistId: String, coverPath: String?) =
        setPlaylistCover(playlistId, coverPath)

    fun removeFromLocalPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            when {
                playlistId == "recent" -> {
                    sessionRecent.removeAll { it.id == trackId }
                    runCatching { recentStore.remove(trackId) }
                }
                playlistId == LikedStore.LIKED_ID -> likedStore.remove(trackId)
                playlistId.startsWith("local-") -> localPl.removeTrack(playlistId, trackId)
                else -> return@launch
            }
            refreshHome()
            val open = _playlistDetail.value.playlist
            if (open?.id == playlistId) openPlaylist(open)
        }
    }

    fun addCurrentToLocalPlaylist(playlistId: String) {
        viewModelScope.launch {
            val t = playback.value.current ?: return@launch
            val added = localPl.addTrack(playlistId, t)
            if (added) recordEvent(t, RecommendationEventType.COLLECT_LOCAL)
            refreshHome()
        }
    }

    fun addTrackToLocalPlaylist(playlistId: String, track: Track) {
        viewModelScope.launch {
            val added = localPl.addTrack(playlistId, track)
            if (added) recordEvent(track, RecommendationEventType.COLLECT_LOCAL)
            refreshHome()
        }
    }

    private fun ensureService() {
        runCatching { MadusApp.instance.ensurePlaybackService() }
    }

    private suspend fun resolveOne(track: Track, forceRefresh: Boolean = false): Track? {
        val wantVideo = MadusApp.instance.videoModeEnabled
        // 本地离线缓存仅音频；视频模式下仍走在线
        if (!wantVideo) {
            val local = runCatching { trackCache.getLocalPath(track.id) }.getOrNull()
            if (!local.isNullOrBlank()) {
                return track.copy(streamUrl = local, isVideoStream = false)
            }
        }
        val url = track.streamUrl
        // 本地 file 路径可直接复用
        val isDurableLocal = !url.isNullOrBlank() &&
            (url.startsWith("file:") ||
                (url.startsWith("/") && !url.startsWith("//") && !url.startsWith("http")))
        if (!forceRefresh && isDurableLocal && !wantVideo) {
            return track.copy(isVideoStream = false)
        }
        // 非强制刷新且已有预取 http：先直接播（熄屏时 playurl 接口常被系统限网）
        // 若 CDN 已过期，会走 onPlayerError → forceRefresh 重取
        if (!forceRefresh && !url.isNullOrBlank() && url.startsWith("http")) {
            return track
        }
        val seed = track.copy(streamUrl = null, isVideoStream = false)
        val result = runCatching {
            registry.get(seed.source)?.resolveStream(seed)
        }
        result.exceptionOrNull()?.let { e ->
            android.util.Log.w("AppViewModel", "resolve fail ${track.title}: ${e.message}")
        }
        val resolved = result.getOrNull()?.takeIf { !it.streamUrl.isNullOrBlank() }
        if (resolved != null) return resolved
        // 取流失败：回退已有地址，总比「暂无播放地址」硬跳好
        if (!url.isNullOrBlank()) {
            android.util.Log.w("AppViewModel", "resolve fail, fallback cached stream ${track.title}")
            return track
        }
        return null
    }

    /**
     * Exo 报错：常见原因是歌单里存了过期 CDN。
     * 1) 强制重解析当前曲  2) 仍失败则跳下一首
     */
    private suspend fun handlePlaybackError() {
        ensureService()
        val cur = playback.value.current ?: run {
            if (pendingQueue.isNotEmpty()) {
                playIndex(pendingIndex.coerceIn(0, pendingQueue.lastIndex), skipBudget = 5)
            }
            return
        }
        if (lastErrorTrackId == cur.id) {
            playErrorRetries++
        } else {
            lastErrorTrackId = cur.id
            playErrorRetries = 1
        }
        // 清队列中该曲的过期 stream
        pendingQueue = pendingQueue.map {
            if (it.id == cur.id) it.copy(streamUrl = null, isVideoStream = false) else it
        }
        _queueTracks.value = pendingQueue

        if (playErrorRetries <= 1) {
            _toast.value = "播放失败，正在重新取流…"
            val pos = playback.value.positionMs.coerceAtLeast(0L)
            val idx = pendingQueue.indexOfFirst { it.id == cur.id }.let { if (it < 0) pendingIndex else it }
            val seed = pendingQueue.getOrNull(idx) ?: cur.copy(streamUrl = null)
            val resolved = resolveOne(seed, forceRefresh = true)
            if (resolved != null && !resolved.streamUrl.isNullOrBlank()) {
                pendingQueue = pendingQueue.toMutableList().also { list ->
                    val i = list.indexOfFirst { it.id == resolved.id }
                    if (i >= 0) list[i] = resolved
                }
                _queueTracks.value = pendingQueue
                player.dispatch(PlayerCommand.PlayTrack(resolved, listOf(resolved), pos))
                return
            }
        }
        // 重试失败 → 跳过
        playErrorRetries = 0
        lastErrorTrackId = null
        _toast.value = "「${cur.title}」无法播放，已跳过"
        if (pendingQueue.size > 1) {
            advanceToNext(userInitiated = true, recordSkipFast = false)
        }
    }

    fun setRecommendSegment(segment: RecommendSegment) {
        val recent = sessionRecent.asReversed().distinctBy { it.id }
        _recommend.update {
            it.copy(segment = segment, recent = recent)
        }
        // Do NOT auto-start playing when switching to Recent — user picks a track.
        // Avoid restarting current song from zero.
    }

    fun onEnterRecommend() {
        viewModelScope.launch {
            val recent = sessionRecent.asReversed().distinctBy { t -> t.id }
            _recommend.update { it.copy(recent = recent) }
            // 用户刚点歌 / 已有播放：不抢播
            if (suppressRecommendAutoPlay || pendingQueue.isNotEmpty() || playback.value.current != null) {
                suppressRecommendAutoPlay = false
                if (_recommend.value.feed.isEmpty()) loadRecommendFeed(autoStart = false)
                return@launch
            }
            if (_recommend.value.feed.isEmpty()) {
                loadRecommendFeed(autoStart = false)
            }
        }
    }

    fun setAutoPlayOnEnterRecommend(enabled: Boolean) {
        _recommend.update { it.copy(autoPlayOnEnter = enabled) }
        _me.update { it.copy(autoPlayOnEnterRecommend = enabled) }
    }

    fun toggleLikeCurrent() {
        val track = playback.value.current ?: return
        viewModelScope.launch {
            val nowLiked = likedStore.toggle(track)
            if (nowLiked) recordEvent(track, RecommendationEventType.LIKE)
            MadusApp.instance.currentTrackLiked = nowLiked
            _recommend.update { s ->
                val next = s.likedIds.toMutableSet()
                if (nowLiked) next.add(track.id) else next.remove(track.id)
                s.copy(likedIds = next)
            }
            notifyPlaybackNotification()
            refreshHome()
            val open = _playlistDetail.value.playlist
            if (open?.id == LikedStore.LIKED_ID) openPlaylist(open)
        }
    }

    fun toggleLikeTrack(track: Track) {
        viewModelScope.launch {
            val nowLiked = likedStore.toggle(track)
            if (nowLiked) recordEvent(track, RecommendationEventType.LIKE)
            _recommend.update { s ->
                val next = s.likedIds.toMutableSet()
                if (nowLiked) next.add(track.id) else next.remove(track.id)
                s.copy(likedIds = next)
            }
            if (playback.value.current?.id == track.id) {
                MadusApp.instance.currentTrackLiked = nowLiked
            }
            notifyPlaybackNotification()
            refreshHome()
        }
    }

    /** 不喜欢：这首不再进推荐，同类/同 UP 冷却 7 天，当前正在播就切走。 */
    fun markNotInterested(track: Track? = playback.value.current) {
        val t = track ?: return
        viewModelScope.launch {
            val p = profileOf(t, fetchRemote = true)
            val event = RecommendationEvent(
                trackId = t.id,
                bvid = t.bvid,
                type = RecommendationEventType.NOT_INTERESTED,
                occurredAtMs = System.currentTimeMillis(),
                sourceId = _recommend.value.sourceId,
                topicKeys = p.topicKeys,
                authorKey = p.authorKey,
            )
            runCatching { recommendationEventStore.record(event) }
            sessionSeenIds.add(t.id)
            pruneUpcomingAfterNotInterested(t)
            val playingThis = playback.value.current?.id == t.id
            if (playingThis) {
                if (pendingQueue.isNotEmpty()) {
                    advanceToNext(userInitiated = true, recordSkipFast = false)
                } else {
                    player.dispatch(PlayerCommand.Pause)
                }
            }
            if (isForYouQueue()) {
                runCatching { ensureInfiniteFeed() }
            }
            _toast.value = "好，少推这类"
        }
    }

    private suspend fun pruneUpcomingAfterNotInterested(t: Track) {
        if (pendingQueue.isEmpty()) return
        val now = System.currentTimeMillis()
        val events = runCatching { recommendationEventStore.events() }.getOrDefault(emptyList())
        val state = recommendationEngine.buildInterestState(events, now)
        val mutedTopics = mutedTopicsOf(state, now)
        val mutedAuthors = mutedAuthorsOf(state, now)
        val keep = (pendingIndex + 1).coerceAtMost(pendingQueue.size)
        val head = pendingQueue.take(keep)
        val rest = pendingQueue.drop(keep).filter { item ->
            if (item.id == t.id) return@filter false
            val ip = ContentProfileParser.profileFromTrack(item)
            val author = ip.authorKey
            if (author != null && author in mutedAuthors) return@filter false
            if (ip.topicKeys.any { it != "unknown" && it in mutedTopics }) return@filter false
            true
        }
        pendingQueue = head + rest
        _queueTracks.value = pendingQueue
        _recommend.update { it.copy(feed = pendingQueue) }
    }

    private fun notifyPlaybackNotification() {
        runCatching {
            val ctx = MadusApp.instance
            ctx.startService(
                android.content.Intent(ctx, com.madus.mobile.player.PlaybackService::class.java)
                    .setAction(com.madus.mobile.player.PlaybackService.ACTION_REFRESH),
            )
        }
    }

    fun refreshSessions() {
        viewModelScope.launch {
            val sessions = registry.all().map { it.getAuthSession() }
            val liked = runCatching { likedStore.tracks().size }.getOrDefault(0)
            val pl = runCatching { localPl.listNonEmpty().size }.getOrDefault(0)
            val recent = sessionRecent.size
            _me.update {
                it.copy(
                    sessions = sessions,
                    likedCount = liked,
                    // +1 我的喜欢
                    playlistCount = pl + if (liked >= 0) 1 else 0,
                    recentCount = recent,
                )
            }
        }
    }

    fun refreshMeStats() {
        refreshSessions()
        viewModelScope.launch {
            val liked = runCatching { likedStore.tracks().size }.getOrDefault(0)
            val pl = runCatching { localPl.listNonEmpty().size }.getOrDefault(0)
            _me.update {
                it.copy(
                    likedCount = liked,
                    playlistCount = pl + 1,
                    recentCount = sessionRecent.distinctBy { t -> t.id }.size,
                    appVersion = com.madus.mobile.BuildConfig.VERSION_NAME,
                )
            }
            refreshCacheStats()
        }
    }

    fun requestLogin(type: MusicSourceType) {
        viewModelScope.launch {
            val session = registry.get(type)?.login()
            val sessions = registry.all().map { it.getAuthSession() }
            _me.update { it.copy(sessions = sessions) }
            refreshHome()
            if (session?.isLoggedIn == true && type == MusicSourceType.BILIBILI) {
                loadRecommendFeed(autoStart = false)
            }
        }
    }

    fun requestLogout(type: MusicSourceType) {
        viewModelScope.launch {
            registry.get(type)?.logout()
            refreshSessions()
            refreshHome()
        }
    }

    fun openPlaylist(playlist: Playlist) {
        // 取消上一次加载 / 未完成的「从详情播放」
        playlistOpenJob?.cancel()
        playlistPlayJob?.cancel()
        playlistPlayJob = null
        playlistExplicitPlayToken = 0L
        val seq = ++playlistOpenSeq
        val now = android.os.SystemClock.elapsedRealtime()
        // 仅挡「退出后连点落到播放全部」：约 0.45s，与详情页手指落定解锁配合
        playlistPlayUnlockAtMs = now + 450L
        blockRecommendNavUntilMs = 0L
        val prev = _playlistDetail.value
        // 同一歌单再进：立刻展示缓存列表，后台静默刷新
        val reuse = prev.playlist?.id == playlist.id &&
            prev.tracks.isNotEmpty() &&
            !prev.isLoading
        _playlistDetail.value = if (reuse) {
            prev.copy(
                playlist = playlist.copy(
                    title = playlist.title.ifBlank { prev.playlist?.title.orEmpty() },
                    coverUrl = playlist.coverUrl ?: prev.playlist?.coverUrl,
                    trackCount = playlist.trackCount.takeIf { it > 0 }
                        ?: prev.playlist?.trackCount
                        ?: prev.tracks.size,
                ),
                isLoading = false,
                openGeneration = seq,
                error = null,
            )
        } else {
            PlaylistDetailUiState(
                playlist = playlist,
                isLoading = true,
                openGeneration = seq,
            )
        }
        playlistOpenJob = viewModelScope.launch {
            val isBiliFav = !playlist.id.startsWith("local-") &&
                playlist.id != "recent" &&
                playlist.id != LikedStore.LIKED_ID &&
                playlist.source == MusicSourceType.BILIBILI

            if (isBiliFav) {
                // 仅拉列表并过滤失效展示；不在打开时 purge（purge 会改 B 站数据且易与二次进入竞态导致空白）
                val page = runCatching {
                    biliApi.favTracksPage(
                        playlist.id,
                        page = 1,
                        pageSize = 40,
                        excludeInvalid = true,
                    )
                }.getOrElse {
                    if (seq != playlistOpenSeq) return@launch
                    _playlistDetail.value = PlaylistDetailUiState(
                        playlist = playlist,
                        isLoading = false,
                        error = it.message ?: "加载失败",
                        openGeneration = seq,
                    )
                    return@launch
                }
                if (seq != playlistOpenSeq || !isActive) return@launch
                // 夹封面：优先用户自定义 → 接口封面 → 列表第一首封面
                val remoteCover = playlist.coverUrl?.takeIf { it.isNotBlank() }
                    ?: page.tracks.firstOrNull()?.coverUrl
                val pl = applyCover(
                    playlist.copy(
                        trackCount = page.total.takeIf { it > 0 } ?: page.tracks.size,
                        coverUrl = remoteCover ?: playlist.coverUrl,
                    ),
                )
                if (seq != playlistOpenSeq) return@launch
                _playlistDetail.value = PlaylistDetailUiState(
                    playlist = pl,
                    tracks = page.tracks,
                    isLoading = false,
                    page = 1,
                    hasMore = page.hasMore,
                    total = page.total,
                    error = if (page.tracks.isEmpty()) "歌单为空" else null,
                    openGeneration = seq,
                )
                // 回写首页/曲库列表封面（空白时补上）
                if (!remoteCover.isNullOrBlank()) {
                    _home.update { h ->
                        h.copy(
                            playlists = h.playlists.map { p ->
                                if (p.id == playlist.id && p.coverUrl.isNullOrBlank()) {
                                    p.copy(coverUrl = remoteCover)
                                } else p
                            },
                        )
                    }
                    _library.update { lib ->
                        lib.copy(
                            biliPlaylists = lib.biliPlaylists.map { p ->
                                if (p.id == playlist.id && p.coverUrl.isNullOrBlank()) {
                                    p.copy(coverUrl = remoteCover)
                                } else p
                            },
                        )
                    }
                }
                return@launch
            }

            val tracks = when {
                playlist.id == "recent" ->
                    sessionRecent.asReversed().distinctBy { it.id }
                playlist.id == LikedStore.LIKED_ID ->
                    runCatching { likedStore.tracks() }.getOrDefault(emptyList())
                playlist.id.startsWith("local-") ->
                    runCatching { localPl.tracks(playlist.id) }.getOrDefault(emptyList())
                else ->
                    runCatching {
                        registry.get(playlist.source)?.playlistTracks(playlist.id, limit = 0).orEmpty()
                    }.getOrElse { e ->
                        if (seq != playlistOpenSeq) return@launch
                        _playlistDetail.value = PlaylistDetailUiState(
                            playlist = playlist,
                            isLoading = false,
                            error = e.message ?: "加载失败",
                            openGeneration = seq,
                        )
                        return@launch
                    }
            }
            if (seq != playlistOpenSeq || !isActive) return@launch
            val base = when {
                playlist.id == "recent" ->
                    Playlist(
                        id = "recent",
                        title = "最近播放",
                        trackCount = tracks.size,
                        coverUrl = tracks.firstOrNull()?.coverUrl,
                    )
                playlist.id == LikedStore.LIKED_ID ->
                    runCatching { likedStore.toPlaylist() }.getOrDefault(playlist)
                playlist.id.startsWith("local-") ->
                    localPl.list().firstOrNull { it.id == playlist.id }?.toPlaylist() ?: playlist
                else -> playlist
            }
            val pl = applyCover(base)
            if (seq != playlistOpenSeq) return@launch
            _playlistDetail.value = PlaylistDetailUiState(
                playlist = pl,
                tracks = tracks,
                isLoading = false,
                page = 1,
                hasMore = false,
                total = tracks.size,
                openGeneration = seq,
                error = if (tracks.isEmpty()) {
                    when (playlist.id) {
                        LikedStore.LIKED_ID -> "还没有喜欢的歌，点 ♡ 加入"
                        "recent" -> "听过的歌会出现在这里"
                        else -> "歌单为空"
                    }
                } else null,
            )
        }
    }

    /** 进页后极短保护期：挡连点误触播放，不挡浏览 */
    fun canPlayFromPlaylistDetail(): Boolean {
        return android.os.SystemClock.elapsedRealtime() >= playlistPlayUnlockAtMs
    }

    /** 用户在详情里点了「播放全部 / 某首歌」 */
    fun markExplicitPlaylistPlay(): Boolean {
        if (!canPlayFromPlaylistDetail()) return false
        playlistExplicitPlayToken = android.os.SystemClock.elapsedRealtime()
        return true
    }

    /** 收藏夹「小说式」翻页：替换当前页，不追加 */
    fun loadMorePlaylistTracks() {
        loadPlaylistPage(_playlistDetail.value.page + 1)
    }

    fun loadPlaylistPrevPage() {
        val p = _playlistDetail.value.page
        if (p <= 1) return
        loadPlaylistPage(p - 1)
    }

    fun loadPlaylistNextPage() {
        val st = _playlistDetail.value
        if (!st.hasMore && st.page * 40 >= st.total && st.total > 0) return
        loadPlaylistPage(st.page + 1)
    }

    private fun loadPlaylistPage(page: Int) {
        viewModelScope.launch {
            val st = _playlistDetail.value
            val pl = st.playlist ?: return@launch
            val openId = pl.id
            val isBiliFav = !pl.id.startsWith("local-") &&
                pl.id != "recent" &&
                pl.id != LikedStore.LIKED_ID &&
                pl.source == MusicSourceType.BILIBILI
            if (!isBiliFav || st.loadingMore) return@launch
            val target = page.coerceAtLeast(1)
            if (target == st.page && st.tracks.isNotEmpty()) return@launch
            _playlistDetail.update { it.copy(loadingMore = true, error = null) }
            val result = runCatching {
                biliApi.favTracksPage(
                    pl.id,
                    page = target,
                    pageSize = 40,
                    excludeInvalid = true,
                )
            }.getOrElse {
                if (_playlistDetail.value.playlist?.id != openId) return@launch
                _playlistDetail.update {
                    it.copy(loadingMore = false, error = it.error ?: "翻页失败")
                }
                return@launch
            }
            // 已退出或换了歌单则丢弃结果
            if (_playlistDetail.value.playlist?.id != openId) return@launch
            if (result.tracks.isEmpty() && target > 1) {
                _playlistDetail.update {
                    it.copy(loadingMore = false, hasMore = false, error = "已经是最后一页")
                }
                return@launch
            }
            _playlistDetail.update {
                it.copy(
                    loadingMore = false,
                    tracks = result.tracks,
                    page = target,
                    hasMore = result.hasMore && result.tracks.isNotEmpty(),
                    total = result.total.coerceAtLeast(it.total),
                    error = if (result.tracks.isEmpty()) "本页无内容" else null,
                )
            }
        }
    }

    /** 短视频右侧头像：优先 track 自带，否则 card 接口补 */
    suspend fun loadOwnerFace(track: Track?): String {
        if (track == null) return ""
        if (track.ownerFace.isNotBlank()) return track.ownerFace
        val mid = track.ownerMid.filter { it.isDigit() }
        if (mid.isBlank()) {
            // 尽量从稿件解析 mid+face（resolvePlayUrl 也会补）
            val resolved = runCatching {
                biliApi.resolveOwnerMid(track)
            }.getOrNull()
            val id = resolved?.first.orEmpty()
            if (id.isBlank()) return ""
            return runCatching { biliApi.resolveOwnerFace(id) }.getOrDefault("")
        }
        return runCatching { biliApi.resolveOwnerFace(mid) }.getOrDefault("")
    }

    fun openUpSpace(track: Track? = null, mid: String = "", name: String = "") {
        viewModelScope.launch {
            // 防连点：同 mid 已在加载/展示则跳过
            val hintMid = mid.filter { it.isDigit() }
                .ifBlank { track?.ownerMid?.filter { it.isDigit() }.orEmpty() }
            if (hintMid.isNotBlank() &&
                _upSpace.value.mid == hintMid &&
                (_upSpace.value.isLoading || _upSpace.value.profile != null)
            ) {
                return@launch
            }

            var id = hintMid
            var displayName = name.ifBlank { track?.artist.orEmpty() }
            if (id.isBlank() && track != null) {
                val resolved = runCatching { biliApi.resolveOwnerMid(track) }.getOrNull()
                if (resolved != null) {
                    id = resolved.first
                    if (resolved.second.isNotBlank()) displayName = resolved.second
                }
            }
            if (id.isBlank() && displayName.isNotBlank()) {
                id = runCatching {
                    biliApi.search(displayName, limit = 8)
                        .firstOrNull { it.artist.equals(displayName, ignoreCase = true) }
                        ?.ownerMid
                        .orEmpty()
                        .ifBlank {
                            biliApi.search(displayName.take(20), limit = 5)
                                .firstOrNull()?.ownerMid.orEmpty()
                        }
                }.getOrDefault("")
            }
            if (id.isBlank()) {
                _toast.value = "无法打开主页：缺少 UP mid"
                return@launch
            }
            if (_upSpace.value.mid == id && _upSpace.value.isLoading) return@launch
            _upSpace.value = UpSpaceUiState(mid = id, isLoading = true)
            val profile = runCatching { biliApi.upProfile(id) }.getOrNull()
            val finalName = profile?.name?.ifBlank { null }
                ?: displayName.ifBlank { "UP主" }
            // 与收藏夹一致：每页 40，小说式翻页（替换不追加）
            val videos = runCatching { biliApi.upVideos(id, page = 1, pageSize = UP_PAGE_SIZE) }
                .getOrDefault(BilibiliApi.UpVideosPage(emptyList(), false, 1))
            val seasons = runCatching { biliApi.upSeasons(id) }.getOrDefault(emptyList())
            val namedVideos = videos.tracks.map {
                it.copy(
                    artist = it.artist.ifBlank { finalName },
                    ownerMid = id,
                )
            }
            val totalCount = videos.total.coerceAtLeast(namedVideos.size)
            _upSpace.value = UpSpaceUiState(
                mid = id,
                profile = (profile ?: BilibiliApi.UpProfile(mid = id, name = finalName))
                    .let { p ->
                        // 投稿数优先用 card.archive_count，否则用接口 total
                        if (p.archiveCount <= 0 && totalCount > 0) {
                            p.copy(archiveCount = totalCount)
                        } else p
                    },
                videos = namedVideos,
                seasons = seasons,
                isLoading = false,
                page = 1,
                hasMore = videos.hasMore && namedVideos.isNotEmpty(),
                total = totalCount,
                error = when {
                    namedVideos.isNotEmpty() -> null
                    seasons.isNotEmpty() -> null
                    else -> "暂无投稿（可稍后再试，或从搜索点进该 UP 的视频）"
                },
            )
        }
    }

    fun toggleFollowUp() {
        viewModelScope.launch {
            val st = _upSpace.value
            val mid = st.mid.filter { it.isDigit() }
            val profile = st.profile ?: return@launch
            if (mid.isBlank() || st.followBusy) return@launch
            _upSpace.update { it.copy(followBusy = true) }
            val wantFollow = !profile.isFollowing
            val err = runCatching { biliApi.setFollowUp(mid, wantFollow) }
                .getOrElse { it.message }
            if (err != null) {
                _toast.value = err
                _upSpace.update { it.copy(followBusy = false) }
                return@launch
            }
            val delta = if (wantFollow) 1L else -1L
            _upSpace.update {
                it.copy(
                    followBusy = false,
                    profile = profile.copy(
                        isFollowing = wantFollow,
                        fans = (profile.fans + delta).coerceAtLeast(0L),
                    ),
                )
            }
            _toast.value = if (wantFollow) "已关注 ${profile.name}" else "已取消关注"
        }
    }

    fun setUpSpaceTab(tab: UpSpaceTab) {
        _upSpace.update { it.copy(tab = tab, selectedSeason = null, seasonTracks = emptyList()) }
    }

    /** 投稿「小说式」翻页：与收藏夹相同，替换当前页，不无限追加 */
    fun loadUpVideosPrevPage() {
        val p = _upSpace.value.page
        if (p <= 1) return
        loadUpVideosPage(p - 1)
    }

    fun loadUpVideosNextPage() {
        val st = _upSpace.value
        if (!st.hasMore && st.page * UP_PAGE_SIZE >= st.total && st.total > 0) return
        loadUpVideosPage(st.page + 1)
    }

    /** @deprecated 保留旧名给可能外部调用；行为改为下一页替换 */
    fun loadMoreUpVideos() = loadUpVideosNextPage()

    private fun loadUpVideosPage(page: Int) {
        viewModelScope.launch {
            val st = _upSpace.value
            if (st.mid.isBlank() || st.loadingMore) return@launch
            if (st.selectedSeason != null) return@launch
            val target = page.coerceAtLeast(1)
            if (target == st.page && st.videos.isNotEmpty()) return@launch
            _upSpace.update { it.copy(loadingMore = true, error = null) }
            val result = runCatching {
                biliApi.upVideos(st.mid, page = target, pageSize = UP_PAGE_SIZE)
            }.getOrElse {
                _upSpace.update {
                    it.copy(loadingMore = false, error = it.error ?: "翻页失败")
                }
                return@launch
            }
            val finalName = st.profile?.name.orEmpty()
            val named = result.tracks.map { t ->
                t.copy(
                    artist = t.artist.ifBlank { finalName }.ifBlank { t.artist },
                    ownerMid = st.mid,
                )
            }
            if (named.isEmpty() && target > 1) {
                _upSpace.update {
                    it.copy(loadingMore = false, hasMore = false, error = "已经是最后一页")
                }
                return@launch
            }
            _upSpace.update {
                it.copy(
                    loadingMore = false,
                    videos = named,
                    page = target,
                    hasMore = result.hasMore && named.isNotEmpty(),
                    total = result.total.coerceAtLeast(it.total).coerceAtLeast(named.size),
                    error = if (named.isEmpty()) "本页无内容" else null,
                )
            }
        }
    }

    fun openUpSeason(season: BilibiliApi.UpSeason) {
        viewModelScope.launch {
            val st = _upSpace.value
            if (st.mid.isBlank()) return@launch
            _upSpace.update {
                it.copy(
                    selectedSeason = season,
                    seasonTracks = emptyList(),
                    isLoading = true,
                    tab = UpSpaceTab.Seasons,
                )
            }
            val page = runCatching {
                biliApi.seasonArchives(st.mid, season.seasonId, page = 1, pageSize = 40)
            }.getOrDefault(BilibiliApi.UpVideosPage(emptyList(), false, 1))
            val named = page.tracks.map {
                it.copy(artist = st.profile?.name.orEmpty().ifBlank { it.artist })
            }
            _upSpace.update {
                it.copy(
                    isLoading = false,
                    seasonTracks = named,
                    hasMore = page.hasMore,
                    page = 1,
                )
            }
        }
    }

    fun closeUpSeason() {
        _upSpace.update {
            it.copy(selectedSeason = null, seasonTracks = emptyList(), hasMore = it.videos.isNotEmpty())
        }
    }

    fun clearUpSpace() {
        _upSpace.value = UpSpaceUiState()
    }

    /** 外站导入分批会话：解析一次，每批 500 首 */
    private data class ImportBatchSession(
        val platform: String,
        val title: String,
        val allSongs: List<ExternalPlaylistImporter.SourceSong>,
        val playlistId: String,
        val playlistTitle: String,
        var nextOffset: Int,
        var totalMatched: Int,
        var totalFailed: Int,
    )

    private var importBatchSession: ImportBatchSession? = null

    fun openImportPlaylistSheet() {
        importBatchSession = null
        _importPlaylist.value = ImportPlaylistUiState(visible = true)
    }

    fun dismissImportPlaylistSheet() {
        if (_importPlaylist.value.isWorking) return
        importBatchSession = null
        _importPlaylist.value = ImportPlaylistUiState()
    }

    fun onImportPlaylistInput(text: String) {
        // 改输入则丢弃未完成的分批会话
        if (importBatchSession != null) {
            importBatchSession = null
        }
        _importPlaylist.update {
            it.copy(
                input = text,
                error = null,
                result = null,
                canContinue = false,
                remaining = 0,
            )
        }
    }

    /** 开始导入：解析全表，先匹配写入第一批 500 首 */
    fun startImportPlaylist() {
        viewModelScope.launch {
            val input = _importPlaylist.value.input
            if (input.isBlank()) {
                _importPlaylist.update { it.copy(error = "请粘贴链接或歌单文本") }
                return@launch
            }
            importBatchSession = null
            _importPlaylist.update {
                it.copy(
                    isWorking = true,
                    progress = "解析中…",
                    error = null,
                    result = null,
                    canContinue = false,
                    remaining = 0,
                    importedCount = 0,
                )
            }
            val (parsed, parseErr) = try {
                externalImporter.parseForImport(input)
            } catch (t: Throwable) {
                _importPlaylist.update {
                    it.copy(isWorking = false, error = t.message ?: "解析失败")
                }
                return@launch
            }
            if (parsed == null) {
                _importPlaylist.update {
                    it.copy(isWorking = false, error = parseErr.ifBlank { "无法解析" })
                }
                return@launch
            }
            val plTitle = "${parsed.title.ifBlank { "导入歌单" }} · ${parsed.platform}"
            val pl = localPl.create(plTitle)
            val session = ImportBatchSession(
                platform = parsed.platform,
                title = parsed.title,
                allSongs = parsed.songs,
                playlistId = pl.id,
                playlistTitle = pl.title,
                nextOffset = 0,
                totalMatched = 0,
                totalFailed = 0,
            )
            importBatchSession = session
            runImportBatch(isFirst = true)
        }
    }

    /** 继续导入下一批 500 首（追加到同一本地歌单） */
    fun continueImportPlaylist() {
        viewModelScope.launch {
            val session = importBatchSession
            if (session == null || session.nextOffset >= session.allSongs.size) {
                _importPlaylist.update {
                    it.copy(error = "没有更多可导入的曲目了", canContinue = false)
                }
                return@launch
            }
            if (_importPlaylist.value.isWorking) return@launch
            _importPlaylist.update {
                it.copy(isWorking = true, progress = "准备下一批…", error = null)
            }
            runImportBatch(isFirst = false)
        }
    }

    private suspend fun runImportBatch(isFirst: Boolean) {
        val session = importBatchSession ?: return
        val batchSize = ExternalPlaylistImporter.BATCH_SIZE
        val start = session.nextOffset
        val end = (start + batchSize).coerceAtMost(session.allSongs.size)
        val batch = session.allSongs.subList(start, end)
        val batchNo = (start / batchSize) + 1
        val totalBatches = ((session.allSongs.size + batchSize - 1) / batchSize).coerceAtLeast(1)

        val match = try {
            externalImporter.matchSongsBatch(
                songs = batch,
                platform = session.platform,
            ) { done, total, label ->
                _importPlaylist.update {
                    it.copy(
                        progress = "第 $batchNo/$totalBatches 批 · B站匹配 $done/$total · $label",
                    )
                }
            }
        } catch (t: Throwable) {
            _importPlaylist.update {
                it.copy(isWorking = false, error = t.message ?: "本批导入失败")
            }
            return
        }

        if (match.matched.isNotEmpty()) {
            localPl.addTracks(session.playlistId, match.matched)
        }
        session.nextOffset = end
        session.totalMatched += match.matched.size
        session.totalFailed += match.failed.size
        refreshLibrary()
        refreshHome()

        val remaining = session.allSongs.size - session.nextOffset
        val canContinue = remaining > 0
        val resultText = buildString {
            append("${session.platform}「${session.title}」")
            append(" · 本批命中 ${match.matched.size}/${batch.size}")
            if (match.failed.isNotEmpty()) append(" · 本批未匹配 ${match.failed.size}")
            append(" · 累计 ${session.totalMatched} 首")
            append(" · 已写入「${session.playlistTitle}」")
            if (canContinue) {
                append("\n还剩 $remaining 首，可继续添加（每次 $batchSize 首）")
            } else if (session.allSongs.size > batchSize) {
                append("\n全部批次已完成")
            }
        }

        if (isFirst && session.totalMatched == 0 && !canContinue) {
            // 整表就一批且全失败
            _importPlaylist.update {
                it.copy(
                    isWorking = false,
                    progress = "",
                    error = "没有匹配到 B 站可播内容",
                    result = null,
                    canContinue = false,
                    remaining = 0,
                    totalSongs = session.allSongs.size,
                    importedCount = 0,
                )
            }
            return
        }

        _importPlaylist.update {
            it.copy(
                isWorking = false,
                progress = "",
                error = if (isFirst && match.matched.isEmpty() && canContinue) {
                    "本批未匹配到，可继续下一批"
                } else {
                    null
                },
                result = resultText,
                input = if (canContinue) it.input else "",
                canContinue = canContinue,
                remaining = remaining,
                totalSongs = session.allSongs.size,
                importedCount = session.totalMatched,
                playlistTitle = session.playlistTitle,
            )
        }
        _toast.value = if (canContinue) {
            "已导入 ${session.totalMatched} 首 · 还剩 $remaining"
        } else {
            "导入完成 · ${session.totalMatched} 首"
        }
        if (!canContinue) {
            importBatchSession = null
        }
    }

    /** 打开「最近播放」虚拟歌单 */
    fun openRecentPlaylist() {
        openPlaylist(
            Playlist(
                id = "recent",
                title = "最近播放",
                trackCount = sessionRecent.distinctBy { it.id }.size,
            ),
        )
    }

    /**
     * 取消进行中的加载，但**保留**当前详情内容。
     * 退出动画期间若立刻清空，会闪一下「歌单 · 0 首」。
     */
    fun cancelPlaylistLoad() {
        playlistOpenJob?.cancel()
        playlistOpenJob = null
        playlistPlayJob?.cancel()
        playlistPlayJob = null
        playlistExplicitPlayToken = 0L
        playlistOpenSeq++
        playlistPlayUnlockAtMs = 0L
        blockRecommendNavUntilMs = 0L
    }

    /** 真正丢掉详情（换页后或显式需要清空时） */
    fun closePlaylist() {
        cancelPlaylistLoad()
        _playlistDetail.value = PlaylistDetailUiState()
    }

    /**
     * 清除当前打开的 B 站收藏夹里已删除/失效稿件（同步到 B 站）。
     */
    fun cleanInvalidInCurrentBiliFav() {
        viewModelScope.launch {
            val pl = _playlistDetail.value.playlist ?: return@launch
            val isBiliFav = !pl.id.startsWith("local-") &&
                pl.id != "recent" &&
                pl.id != LikedStore.LIKED_ID &&
                pl.source == MusicSourceType.BILIBILI
            if (!isBiliFav) {
                _toast.value = "仅支持 B 站收藏夹"
                return@launch
            }
            _toast.value = "正在清除失效视频…"
            val result = runCatching { biliApi.purgeInvalidFromFav(pl.id) }
                .getOrElse { BilibiliApi.PurgeInvalidResult(0, it.message ?: "清理失败", false) }
            _toast.value = result.message
            // 手动清理后再重载列表
            openPlaylist(pl)
            refreshHome()
            refreshLibrary()
        }
    }

    /**
     * 一键清理账号下全部 B 站收藏夹中的失效视频。
     */
    fun cleanInvalidInAllBiliFavs() {
        viewModelScope.launch {
            _toast.value = "正在清理全部收藏夹失效视频…"
            val result = runCatching { biliApi.cleanInvalidInAllFavFolders() }
                .getOrElse {
                    BilibiliApi.CleanInvalidResult(0, 0, it.message ?: "清理失败")
                }
            _toast.value = result.message
            val open = _playlistDetail.value.playlist
            if (open != null &&
                open.source == MusicSourceType.BILIBILI &&
                !open.id.startsWith("local-")
            ) {
                openPlaylist(open)
            }
            refreshHome()
            refreshLibrary()
        }
    }

    /**
     * 从 UP 主页点播：投稿/合集详情页同样是分页，播放时尽量拉全量再入队并循环。
     */
    fun playFromUpSpace(startTrack: Track, pageTracks: List<Track>) {
        viewModelScope.launch {
            val st = _upSpace.value
            val name = st.profile?.name?.ifBlank { null } ?: "UP主"
            val mid = st.mid
            val season = st.selectedSeason

            val tracks: List<Track> = if (mid.isBlank()) {
                pageTracks
            } else if (season != null) {
                if (pageTracks.size >= 40 || st.hasMore) {
                    _toast.value = "正在加载完整合集…"
                }
                val all = mutableListOf<Track>()
                var page = 1
                var hasMore = true
                while (hasMore && page <= 100) {
                    val result = runCatching {
                        biliApi.seasonArchives(mid, season.seasonId, page = page, pageSize = 40)
                    }.getOrNull() ?: break
                    val named = result.tracks.map {
                        it.copy(artist = it.artist.ifBlank { name }, ownerMid = mid)
                    }
                    if (named.isEmpty()) break
                    all.addAll(named)
                    hasMore = result.hasMore && named.isNotEmpty()
                    page++
                }
                all.ifEmpty { pageTracks }
            } else {
                val totalHint = st.total
                if (totalHint > UP_PAGE_SIZE || pageTracks.size >= UP_PAGE_SIZE || st.hasMore) {
                    _toast.value = "正在加载 UP 全部投稿…"
                }
                val all = mutableListOf<Track>()
                var page = 1
                var hasMore = true
                while (hasMore && page <= 100) {
                    val result = runCatching {
                        biliApi.upVideos(mid, page = page, pageSize = UP_PAGE_SIZE)
                    }.getOrNull() ?: break
                    val named = result.tracks.map {
                        it.copy(artist = it.artist.ifBlank { name }, ownerMid = mid)
                    }
                    if (named.isEmpty()) break
                    all.addAll(named)
                    hasMore = result.hasMore && named.isNotEmpty()
                    if (totalHint > 0 && all.size >= totalHint) break
                    page++
                }
                all.ifEmpty { pageTracks }
            }

            if (tracks.isEmpty()) {
                _toast.value = "没有可播放内容"
                return@launch
            }
            val start = tracks.firstOrNull {
                it.id == startTrack.id ||
                    (it.bvid.isNotBlank() && it.bvid == startTrack.bvid)
            } ?: startTrack
            val sourceId = if (season != null) {
                "up-$mid-season-${season.seasonId}"
            } else {
                "up-$mid"
            }
            val label = if (season != null) {
                "${season.title.ifBlank { "合集" }} · $name"
            } else {
                name
            }
            playTrack(
                track = start,
                queue = tracks,
                loopAll = true,
                resumeIfSame = false,
                sourceLabel = label,
                sourceId = sourceId,
            )
            if (tracks.size > pageTracks.size) {
                _toast.value = "已加载 ${tracks.size} 首 · 顺序循环"
            }
        }
    }

    /**
     * 从歌单详情点播 / 播放全部。
     * - B 站收藏夹：详情页是「小说式」每页 40 首，播放时必须拉全量入队，否则队列只有当前页。
     * - 本地 / 喜欢 / 最近 / 导入歌单：已是全量列表。
     * - 一律顺序循环，播完回到第一首；**不会**自动追加推荐视频。
     *
     * @param startTrack 起播曲；null 表示播放全部（从列表第一首）
     * @param pageTracks 当前页/详情里看到的列表（本地歌单即全量）
     * @param onStarted 真正开始入队播放后回调（用于再跳推荐台）；被门闩拦截时不调用
     */
    fun playFromPlaylistDetail(
        startTrack: Track?,
        pageTracks: List<Track>,
    ) {
        if (!canPlayFromPlaylistDetail()) return
        // 取消上一次未完成的详情播放
        playlistPlayJob?.cancel()
        val openGen = playlistOpenSeq
        playlistPlayJob = viewModelScope.launch {
            val pl = _playlistDetail.value.playlist
            val label = pl?.title?.ifBlank { "歌单" } ?: "歌单"
            val sourceId = pl?.id?.ifBlank { "playlist" } ?: "playlist"
            val isBiliFav = pl != null &&
                !pl.id.startsWith("local-") &&
                pl.id != "recent" &&
                pl.id != LikedStore.LIKED_ID &&
                pl.source == MusicSourceType.BILIBILI

            val tracks: List<Track> = if (isBiliFav) {
                // 已有全量且覆盖当前页时复用，避免重复拉
                val existing = pendingQueue
                val pageIds = pageTracks.map { it.id }.toSet()
                val alreadyFull = existing.size > pageTracks.size &&
                    _recommend.value.sourceId == pl.id &&
                    pageIds.all { id -> existing.any { it.id == id } }
                if (alreadyFull) {
                    existing
                } else {
                    val totalHint = _playlistDetail.value.total
                    if (totalHint > 40 || pageTracks.size >= 40 || _playlistDetail.value.hasMore) {
                        _toast.value = "正在加载完整歌单…"
                    }
                    val all = runCatching {
                        registry.get(MusicSourceType.BILIBILI)
                            ?.playlistTracks(pl.id, limit = 0).orEmpty()
                    }.getOrDefault(emptyList())
                    when {
                        all.isNotEmpty() -> all
                        pageTracks.isNotEmpty() -> pageTracks
                        else -> emptyList()
                    }
                }
            } else {
                pageTracks.ifEmpty {
                    when {
                        pl == null -> emptyList()
                        pl.id == "recent" ->
                            sessionRecent.asReversed().distinctBy { it.id }
                        pl.id == LikedStore.LIKED_ID ->
                            runCatching { likedStore.tracks() }.getOrDefault(emptyList())
                        pl.id.startsWith("local-") ->
                            runCatching { localPl.tracks(pl.id) }.getOrDefault(emptyList())
                        else -> pageTracks
                    }
                }
            }

            if (openGen != playlistOpenSeq) return@launch

            // 播放跳过失效稿，避免点播卡住
            val finalTracks = tracks.filter {
                it.bvid.isNotBlank() &&
                    it.album != "失效" &&
                    !biliApi.isInvalidFavTitle(it.title)
            }
            if (finalTracks.isEmpty()) {
                _toast.value = if (tracks.any {
                        biliApi.isInvalidFavTitle(it.title) || it.album == "失效"
                    }
                ) {
                    "本页只有失效视频，请用 ⋯ 清除失效"
                } else {
                    "歌单为空"
                }
                return@launch
            }

            val start = startTrack?.let { seed ->
                finalTracks.firstOrNull {
                    it.id == seed.id ||
                        (it.bvid.isNotBlank() && it.bvid == seed.bvid &&
                            (seed.cid.isBlank() || it.cid == seed.cid))
                }
            } ?: finalTracks.first()

            if (openGen != playlistOpenSeq) return@launch

            playTrack(
                track = start,
                queue = finalTracks,
                loopAll = true,
                resumeIfSame = false,
                sourceLabel = label,
                sourceId = sourceId,
            )
            if (isBiliFav && finalTracks.size > pageTracks.size) {
                _toast.value = "已加载全部 ${finalTracks.size} 首 · 顺序循环"
            }
            // 跳推荐台改由 UI 在 onClick 同步调用，避免异步回调误跳
        }
    }

    /**
     * 清空当前列表后，**无缝**接上「为你推荐」并自动开播。
     * 清屏里清空也不用退出再手动点推荐。
     */
    fun clearQueue() {
        expandJob?.cancel()
        expandJob = null
        expandingFeed = false
        // 先停掉旧队列，避免空队列 Next 死循环
        pendingQueue = emptyList()
        pendingIndex = 0
        _queueTracks.value = emptyList()
        player.dispatch(PlayerCommand.Stop)
        // 立刻自动切入为你推荐（静默，无多余 toast）
        startForYouRecommend(silent = true)
    }

    fun sources(): List<MusicSource> = registry.all()

    /**
     * 个性化推荐冷启动：喜欢/最近 → related 扩池 + 探索补量。
     * 之后靠 [ensureInfiniteFeed] 自动续刷，可刷很久。
     */
    private fun loadRecommendFeed(autoStart: Boolean) {
        viewModelScope.launch {
            _recommend.update { it.copy(isLoading = true) }
            val pack = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val likedInteractions = runCatching { likedStore.interactions() }.getOrDefault(emptyList())
                    val liked = likedInteractions.map { it.track }
                    val recent = sessionRecent.asReversed().distinctBy { it.id }
                    val localSample = runCatching {
                        localPl.listNonEmpty().flatMap { it.tracks }.shuffled().take(12)
                    }.getOrDefault(emptyList())
                    val bili = registry.get(MusicSourceType.BILIBILI)
                    val session = runCatching { bili?.getAuthSession() }.getOrNull()
                    val loggedIn = session?.isLoggedIn == true
                    val feed = buildSmartFeed(
                        liked = liked,
                        recent = recent,
                        localSample = localSample,
                        limit = 40,
                        excludeExtra = sessionSeenIds,
                        recentLikeIds = likedInteractions
                            .filter { it.likedAtMs >= System.currentTimeMillis() - HOUR_MS }
                            .mapTo(linkedSetOf<String>()) { it.track.id },
                    )
                    Triple(feed, recent, loggedIn)
                }
            }.getOrElse {
                android.util.Log.w("AppViewModel", "recommend fail: ${it.message}")
                Triple(emptyList(), sessionRecent.asReversed().distinctBy { t -> t.id }, false)
            }
            val (feed, recent, loggedIn) = pack
            val shouldAutoStart = autoStart &&
                !suppressRecommendAutoPlay &&
                feed.isNotEmpty() &&
                playback.value.current == null &&
                pendingQueue.isEmpty()
            val label = when {
                !loggedIn && feed.isEmpty() -> "请先登录"
                feed.isNotEmpty() -> "为你推荐"
                else -> "推荐电台"
            }
            _recommend.update {
                it.copy(
                    feed = feed,
                    isLoading = false,
                    isStartingPlayback = shouldAutoStart,
                    recent = recent,
                    sourceLabel = label,
                    sourceId = when (label) {
                        "请先登录" -> "need_login"
                        else -> "recommend"
                    },
                )
            }
            if (shouldAutoStart) {
                player.prepareTrack(
                    feed.first(),
                    asVideo = MadusApp.instance.videoModeEnabled,
                )
                startRecommendQueue(feed)
                clearRecommendLoadingAfterStart()
            }
        }
    }

    /**
     * 丐版「抖音 / B 站首页」推荐（本地信号 + B 站官方接口补丁）：
     * 1. 登录后优先首页 rcmd（账号兴趣）+ 观看历史种子
     * 2. 喜欢 / 最近 / 收藏 → related 链式扩展（跟用户真实点赞走，不硬塞音乐区）
     * 3. 作者 + 标题关键词搜索强化兴趣
     * 4. 约 15–20% 全站热门探索，防茧房；**不再默认塞音乐分区排行**
     * 5. 同作者打散 + 会话去重；续刷见 [ensureInfiniteFeed]
     */
    private suspend fun buildSmartFeed(
        liked: List<Track>,
        recent: List<Track>,
        localSample: List<Track>,
        limit: Int,
        excludeExtra: Set<String> = emptySet(),
        recentLikeIds: Set<String> = emptySet(),
    ): List<Track> {
        val exclude = linkedSetOf<String>().apply {
            addAll(excludeExtra)
            // 最近刚看过的略降权，但不永久拉黑（与 sessionSeen 不同）
            recent.take(8).forEach { add(it.id) }
        }

        val interestArtists = buildList {
            addAll(liked.map { it.artist })
            addAll(recent.take(30).map { it.artist })
            addAll(localSample.map { it.artist })
        }.map { it.trim() }
            .filter { it.isNotBlank() && it != "Bilibili" && it.length in 1..24 }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }

        // 标题关键词：从点赞/最近里抠 2–8 字片段当搜索种子
        val interestKeywords = buildList {
            (liked.take(20) + recent.take(20)).forEach { tr ->
                val t = tr.title
                    .replace(Regex("【.*?】|\\[.*?]|\\(.*?\\)|（.*?）"), " ")
                    .replace(Regex("[\\s|｜/\\\\·•~～!！?？.。,，、:：;；\"'“”‘’]+"), " ")
                    .trim()
                if (t.length in 2..16) add(t.take(12))
                Regex("[\\u4e00-\\u9fff]{2,8}").findAll(t).take(2).forEach { add(it.value) }
            }
        }.filter { it.length in 2..12 }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(8)

        val history = runCatching { biliApi.watchHistory(20) }.getOrDefault(emptyList())
        // 收藏夹抽样：学习用户「真收藏」的歌
        val favSeeds = runCatching {
            biliApi.favFolders().take(4).flatMap { f ->
                biliApi.favTracksPage(f.id, page = 1, pageSize = 12).tracks
            }
        }.getOrDefault(emptyList())
        // Daily candidates remain a stable baseline; hourly candidates respond to
        // new likes immediately. This keeps the feed fresh without locking the
        // user into the latest single interest.
        val calendar = java.util.Calendar.getInstance()
        val daySalt = calendar.get(java.util.Calendar.YEAR) * 400 +
            calendar.get(java.util.Calendar.DAY_OF_YEAR)
        val hourSalt = daySalt * 24 + calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val freshLikeSeeds = liked.filter { it.id in recentLikeIds }.take(8)
        val freshFavSeeds = favSeeds.take(8)
        val freshSeedIds = (freshLikeSeeds + freshFavSeeds).mapTo(hashSetOf<String>()) { it.id }
        val seeds = buildList {
            addAll(freshLikeSeeds.sortedBy { (it.id.hashCode() xor hourSalt).toUInt() })
            addAll(freshFavSeeds.sortedBy { (it.id.hashCode() xor hourSalt).toUInt() })
            addAll(liked.filterNot { it.id in freshSeedIds }.take(24))
            addAll(localSample)
            addAll(favSeeds.filterNot { it.id in freshSeedIds })
            addAll(recent.take(24))
            addAll(history.take(16))
        }.distinctBy { it.id }
            .filter { it.bvid.isNotBlank() || it.id.startsWith("BV") }
            .sortedBy { (it.id.hashCode() xor daySalt).toUInt() }
            .take(22)

        val now = System.currentTimeMillis()
        val events = runCatching { recommendationEventStore.events() }.getOrDefault(emptyList())
        val state = recommendationEngine.buildInterestState(events, now)
        val profiles = runCatching { contentProfileStore.all() }.getOrDefault(emptyList())
            .associateBy { it.key }
        val context = FeedContext(
            nowMs = now,
            limit = limit.coerceAtLeast(24),
            sessionSeenIds = exclude,
            queueIds = emptySet(),
            recentQueue = recent.take(8),
            mutedTopics = mutedTopicsOf(state, now),
            mutedAuthors = mutedAuthorsOf(state, now),
            sourceId = "recommend",
        )
        val scoredPool = linkedMapOf<String, ScoredTrack>()

        fun offer(t: Track, source: String, bonus: Double = 0.0) {
            if (t.id in exclude) return
            val p = profiles[profileKey(t)] ?: ContentProfileParser.profileFromTrack(t, now)
            val sc = recommendationEngine.scoreCandidate(t, p, state, source, context)
            val hourlyJitter = ((t.id.hashCode() xor hourSalt).toUInt() % 80u).toDouble() / 100.0
            val withJitter = sc.copy(score = sc.score + hourlyJitter + bonus)
            val prev = scoredPool[t.id]
            if (prev == null || withJitter.score > prev.score) scoredPool[t.id] = withJitter
        }

        // 0) B 站首页 rcmd
        val rcmd = buildList {
            addAll(runCatching { biliApi.homepageRcmd(limit = 24, freshIdx = 1) }.getOrDefault(emptyList()))
            addAll(runCatching { biliApi.homepageRcmd(limit = 24, freshIdx = 2 + (daySalt % 3)) }.getOrDefault(emptyList()))
        }.distinctBy { it.id }
        for (t in rcmd) offer(t, "homepage")

        // 1) related：跟着喜欢/收藏/最近走（抖音式「看过同类再推」）
        for ((i, seed) in seeds.withIndex()) {
            if (scoredPool.size >= limit * 5) break
            val bv = seed.bvid.ifBlank { BilibiliApi.parseBvid(seed.id).orEmpty() }
            if (bv.isBlank()) continue
            val related = runCatching { biliApi.relatedTracks(bv, 18) }.getOrDefault(emptyList())
            val src = if (seed.id in freshSeedIds) "related-like" else "related"
            val seedBonus = if (seed.id in freshSeedIds) 0.9 else 0.0
            for (t in related) offer(t, src, seedBonus)
        }

        // 2) 作者向搜索（常听的 UP）
        for (artist in interestArtists.take(8)) {
            if (scoredPool.size >= limit * 5) break
            val hits = runCatching {
                registry.get(MusicSourceType.BILIBILI)?.search(artist, limit = 12).orEmpty()
            }.getOrDefault(emptyList())
            for (t in hits) offer(t, "search")
        }

        // 3) 标题关键词
        for (kw in interestKeywords.take(6)) {
            if (scoredPool.size >= limit * 5) break
            val hits = runCatching {
                registry.get(MusicSourceType.BILIBILI)?.search(kw, limit = 10).orEmpty()
            }.getOrDefault(emptyList())
            for (t in hits) offer(t, "search")
        }

        // 4) 轻量探索防茧房
        val popular = runCatching {
            biliApi.popularTracks(
                ((limit * RecommendationTuning.MIN_EXPLORE_RATIO * 2).toInt()).coerceAtLeast(16),
            )
        }.getOrDefault(emptyList())
        for (t in popular) offer(t, "popular")

        // 5) 喜欢/本地歌单回访（每天轮换）
        for (t in liked.shuffled().take(6)) offer(t, "liked")
        for (t in localSample.shuffled().take(5)) offer(t, "local")
        for (t in favSeeds.shuffled().take(5)) offer(t, "liked")

        // 每日基线切片：作为 dailyBaseline 候选交给重排器穿插，保留日更稳定输入。
        val daily = scoredPool.values
            .sortedBy { (it.track.id.hashCode() xor daySalt).toUInt() }
            .take(((limit * RecommendationTuning.MIN_DAILY_BASELINE_RATIO).toInt()).coerceAtLeast(6))
            .map { it.copy(dailyBaseline = true) }
        val candidates = (scoredPool.values + daily)
            .associateBy { it.track.id }
            .values
            .toList()
        val (feed, picked) = recommendationReRanker.rerankWithReasons(
            candidates,
            context.copy(limit = limit.coerceAtLeast(24)),
        )
        _recommend.update {
            it.copy(
                debugRows = picked.take(limit.coerceAtLeast(24))
                    .map { row ->
                        val topics = ContentProfileParser.profileFromTrack(row.track)
                            .topicKeys.joinToString(",")
                        val score = java.lang.String.format(java.util.Locale.US, "%.2f", row.score)
                        "${row.track.title.take(14)} | $score | $topics | ${row.reason}"
                    }
                    .takeLast(12),
            )
        }
        return feed
    }

    /**
     * 无限流续刷：剩余不足时自动拉取更多，不回绕旧列表。
     * @param thriftNet 后台/打游戏：只轻量 related，不扫热门/多分区/搜索，省网。
     */
    private suspend fun ensureInfiniteFeed(
        force: Boolean = false,
        minAdd: Int = 15,
        thriftNet: Boolean = false,
    ) {
        if (!isForYouQueue()) return
        if (expandingFeed) return
        val remain = pendingQueue.size - pendingIndex - 1
        val bgNow = MadusApp.instance.appInBackground || MadusApp.instance.gameLiteMode
        val thrift = thriftNet || bgNow
        if (!force && remain > if (thrift) 2 else 10) return
        expandingFeed = true
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val existing = pendingQueue.map { it.id }.toHashSet()
                existing.addAll(sessionSeenIds)
                val likedInteractions = runCatching { likedStore.interactions() }.getOrDefault(emptyList())
                val liked = likedInteractions.map { it.track }
                val recent = sessionRecent.asReversed().distinctBy { it.id }
                val localSample = runCatching {
                    localPl.listNonEmpty().flatMap { it.tracks }.shuffled().take(8)
                }.getOrDefault(emptyList())

                // A newly liked item should affect the currently running infinite feed
                // within the hour, not only after the user re-enters the recommend tab.
                val freshLikeSeeds = likedInteractions
                    .filter { it.likedAtMs >= System.currentTimeMillis() - HOUR_MS }
                    .map { it.track }
                    .take(4)

                // 用当前尾部多首当种子，链式 related（越刷越远）
                val tailSeeds = pendingQueue
                    .drop((pendingIndex - 2).coerceAtLeast(0))
                    .take(if (thrift) 2 else 6)
                    .reversed()

                val now = System.currentTimeMillis()
                val events = runCatching { recommendationEventStore.events() }.getOrDefault(emptyList())
                val state = recommendationEngine.buildInterestState(events, now)
                val profiles = runCatching { contentProfileStore.all() }.getOrDefault(emptyList())
                    .associateBy { it.key }
                val context = FeedContext(
                    nowMs = now,
                    limit = 28,
                    sessionSeenIds = existing,
                    queueIds = existing,
                    recentQueue = tailSeeds,
                    mutedTopics = mutedTopicsOf(state, now),
                    mutedAuthors = mutedAuthorsOf(state, now),
                    sourceId = "recommend",
                )
                val batch = linkedMapOf<String, ScoredTrack>()
                fun putNew(t: Track, source: String, bonus: Double = 0.0) {
                    if (t.id in existing) return
                    val p = profiles[profileKey(t)] ?: ContentProfileParser.profileFromTrack(t, now)
                    val sc = recommendationEngine.scoreCandidate(t, p, state, source, context)
                    val bumped = if (bonus == 0.0) sc else sc.copy(score = sc.score + bonus)
                    val prev = batch[t.id]
                    if (prev == null || bumped.score > prev.score) batch[t.id] = bumped
                }

                for (seed in freshLikeSeeds) {
                    val bv = seed.bvid.ifBlank { BilibiliApi.parseBvid(seed.id).orEmpty() }
                    if (bv.isBlank()) continue
                    runCatching {
                        biliApi.relatedTracks(bv, if (thrift) 8 else 18)
                    }.getOrDefault(emptyList()).forEach { putNew(it, "related-like", 0.9) }
                }

                for (seed in tailSeeds) {
                    val bv = seed.bvid.ifBlank { BilibiliApi.parseBvid(seed.id).orEmpty() }
                    if (bv.isBlank()) continue
                    runCatching {
                        biliApi.relatedTracks(bv, if (thrift) 8 else 18)
                    }.getOrDefault(emptyList())
                        .forEach { putNew(it, "related") }
                }

                // 后台省网：related 够了就停，不再扫首页/搜索/热门分区
                if (!thrift) {
                    // B 站首页 rcmd 续页
                    runCatching {
                        biliApi.homepageRcmd(
                            limit = 20,
                            freshIdx = (pendingIndex / 8 + 2).coerceAtLeast(2),
                        )
                    }.getOrDefault(emptyList()).forEach { putNew(it, "homepage") }

                    // 兴趣作者再搜一波
                    val artists = (liked + recent + localSample)
                        .map { it.artist.trim() }
                        .filter { it.isNotBlank() && it != "Bilibili" }
                        .distinct()
                        .shuffled()
                        .take(4)
                    for (a in artists) {
                        if (batch.size >= minAdd + 20) break
                        runCatching {
                            registry.get(MusicSourceType.BILIBILI)?.search(a, limit = 8).orEmpty()
                        }.getOrDefault(emptyList()).forEach { putNew(it, "search") }
                    }

                    // 探索补量：全站热门 + 多分区
                    if (batch.size < minAdd) {
                        runCatching { biliApi.popularTracks(24) }.getOrDefault(emptyList())
                            .forEach { putNew(it, "popular") }
                        for (rid in intArrayOf(1, 4, 36, 160, 5, 129, 3)) {
                            runCatching { biliApi.rankingTracks(rid = rid, limit = 12) }
                                .getOrDefault(emptyList())
                                .forEach { putNew(it, "explore") }
                        }
                    }

                    // 再链一层 related
                    if (batch.size < minAdd + 8) {
                        for (seed in batch.values.shuffled().take(5)) {
                            val seedTrack = seed.track
                            val bv = seedTrack.bvid.ifBlank { BilibiliApi.parseBvid(seedTrack.id).orEmpty() }
                            if (bv.isBlank()) continue
                            runCatching { biliApi.relatedTracks(bv, 12) }.getOrDefault(emptyList())
                                .forEach { putNew(it, "related") }
                        }
                    }
                }

                val (add, _) = recommendationReRanker.rerankWithReasons(batch.values.toList(), context)
                if (add.isEmpty()) return@withContext
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    while (sessionSeenIds.size > 800) {
                        val first = sessionSeenIds.firstOrNull() ?: break
                        sessionSeenIds.remove(first)
                    }
                    pendingQueue = pendingQueue + add
                    _queueTracks.value = pendingQueue
                    _recommend.update { it.copy(feed = pendingQueue) }
                }
            }
        } finally {
            expandingFeed = false
        }
    }

    /**
     * 推荐台「播放 为你推荐」/ 冷启动开播。
     * 已有推荐 feed 时直接起播，避免每次连点都重建列表导致二次闪按钮。
     */
    fun startRecommendIfReady() {
        startForYouRecommend(silent = false)
    }

    /**
     * 强制切入「为你推荐」无限流并开播（可从歌单/清空列表无缝接上）。
     */
    fun startForYouRecommend(silent: Boolean = false) {
        // 已在起播中：忽略连点（不要再闪「播放」或重拉 feed）
        if (recommendStartJob?.isActive == true) return
        val alreadyPlayingForYou = isForYouQueue() &&
            playback.value.current != null &&
            (playback.value.isPlaying || playback.value.isLoading)
        if (alreadyPlayingForYou) return

        recommendStartJob?.cancel()
        recommendClearLoadingJob?.cancel()
        recommendStartJob = viewModelScope.launch {
            try {
                _recommend.update {
                    it.copy(
                        isLoading = true,
                        isStartingPlayback = true,
                        segment = RecommendSegment.Feed,
                    )
                }

                // 有近 15 分钟新赞就重算，别抱着启动时那批旧列表
                val likedInteractions = runCatching { likedStore.interactions() }.getOrDefault(emptyList())
                val recentLikeIds = likedInteractions
                    .filter { it.likedAtMs >= System.currentTimeMillis() - HOUR_MS }
                    .mapTo(linkedSetOf<String>()) { it.track.id }
                val veryFreshLike = likedInteractions.any {
                    it.likedAtMs >= System.currentTimeMillis() - 15 * 60_000L
                }
                val reuse = _recommend.value.feed.takeIf {
                    it.isNotEmpty() &&
                        _recommend.value.sourceId == "recommend" &&
                        !veryFreshLike
                }
                val built = if (reuse != null) {
                    reuse
                } else {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            val liked = likedInteractions.map { it.track }
                            val recent = sessionRecent.asReversed().distinctBy { it.id }
                            val localSample = runCatching {
                                localPl.listNonEmpty().flatMap { it.tracks }.shuffled().take(12)
                            }.getOrDefault(emptyList())
                            buildSmartFeed(
                                liked = liked,
                                recent = recent,
                                localSample = localSample,
                                limit = 40,
                                excludeExtra = sessionSeenIds,
                                recentLikeIds = recentLikeIds,
                            )
                        }
                    }.getOrDefault(emptyList())
                }

                val bili = registry.get(MusicSourceType.BILIBILI)
                val loggedIn = runCatching { bili?.getAuthSession()?.isLoggedIn }.getOrNull() == true

                if (built.isEmpty()) {
                    _recommend.update {
                        it.copy(
                            feed = emptyList(),
                            isLoading = false,
                            isStartingPlayback = false,
                            sourceLabel = if (!loggedIn) "请先登录" else "推荐电台",
                            sourceId = if (!loggedIn) "need_login" else "recommend",
                        )
                    }
                    if (!silent) {
                        _toast.value = if (!loggedIn) {
                            "请先登录 B 站，再听推荐电台"
                        } else {
                            "暂无推荐内容，去搜索点赞一些你想看的视频吧"
                        }
                    }
                    return@launch
                }

                _recommend.update {
                    it.copy(
                        feed = built,
                        isLoading = false,
                        isStartingPlayback = true,
                        sourceLabel = "为你推荐",
                        sourceId = "recommend",
                        segment = RecommendSegment.Feed,
                    )
                }
                // 立刻占位当前曲，避免 UI 仍显示「未在播放」+ 播放按钮回弹
                // （取流完成前 isStartingPlayback 仍保持 true）
                player.prepareTrack(
                    built.first(),
                    asVideo = MadusApp.instance.videoModeEnabled,
                )
                startRecommendQueue(built)
                // 在本 Job 内等到真正可播，期间 isActive=true 可挡住连点
                awaitRecommendPlaybackSettled()
            } catch (e: CancellationException) {
                _recommend.update {
                    it.copy(isLoading = false, isStartingPlayback = false)
                }
                throw e
            } catch (e: Exception) {
                android.util.Log.w("AppViewModel", "startForYouRecommend: ${e.message}")
                _recommend.update {
                    it.copy(isLoading = false, isStartingPlayback = false)
                }
                if (!silent) _toast.value = "起播失败，请再试一次"
            }
        }
    }

    private fun startRecommendQueue(feed: List<Track>) {
        if (feed.isEmpty()) return
        // 推荐流：不 loop 回第一首；靠无限续刷
        playTrack(
            feed.first(),
            feed,
            loopAll = false,
            sourceLabel = "为你推荐",
            sourceId = "recommend",
        )
        // UI 显示「顺序」；实际见底自动续
        _playMode.value = PlayModeLabel.LOOP
        applyPlayModeToPlayer()
        scheduleInfinitePrefetch()
    }

    /**
     * 真正可播（或明确失败）后再收起 isStartingPlayback。
     * 不能只看 current!=null：prepareTrack 占位会立刻满足，导致按钮二次闪现、画面未出。
     */
    private fun clearRecommendLoadingAfterStart() {
        recommendClearLoadingJob?.cancel()
        recommendClearLoadingJob = viewModelScope.launch {
            awaitRecommendPlaybackSettled()
        }
    }

    private suspend fun awaitRecommendPlaybackSettled() {
        val deadline = System.currentTimeMillis() + 20_000L
        while (System.currentTimeMillis() < deadline) {
            if (isRecommendPlaybackSettled()) break
            delay(120L) // 取消时会抛 CancellationException
        }
        _recommend.update {
            it.copy(isLoading = false, isStartingPlayback = false)
        }
    }

    /** 起播完成：在播 / 缓冲中带真实流 / 有明确错误 */
    private fun isRecommendPlaybackSettled(): Boolean {
        val pb = playback.value
        val cur = pb.current ?: return false
        if (pb.isPlaying) return true
        if (!pb.errorMessage.isNullOrBlank()) return true
        // prepareTrack 占位：isLoading=true 且无 streamUrl → 未完成
        if (!cur.streamUrl.isNullOrBlank()) return true
        return false
    }

    private fun pushRecent(track: Track, positionMs: Long = 0L) {
        sessionRecent.removeAll { it.id == track.id }
        sessionRecent.add(track)
        if (sessionRecent.size > 50) sessionRecent.removeAt(0)
        val recent = sessionRecent.asReversed().distinctBy { it.id }
        _recommend.update { it.copy(recent = recent) }
        _home.update { it.copy(recent = recent.take(12)) }
        viewModelScope.launch {
            runCatching { recentStore.push(track, positionMs) }
        }
    }

    /** 从最近播放移除（首页 / 推荐·最近 / 曲库 共用） */
    fun removeFromRecent(trackId: String) {
        sessionRecent.removeAll { it.id == trackId }
        val recent = sessionRecent.asReversed().distinctBy { it.id }
        _recommend.update { it.copy(recent = recent) }
        _home.update { it.copy(recent = recent.take(12)) }
        _library.update { it.copy(recent = recent) }
        viewModelScope.launch {
            runCatching { recentStore.remove(trackId) }
            val open = _playlistDetail.value.playlist
            if (open?.id == "recent") openPlaylist(open)
        }
    }

    fun clearRecent() {
        sessionRecent.clear()
        _recommend.update { it.copy(recent = emptyList()) }
        _home.update { it.copy(recent = emptyList()) }
        _library.update { it.copy(recent = emptyList()) }
        viewModelScope.launch {
            runCatching { recentStore.clear() }
            val open = _playlistDetail.value.playlist
            if (open?.id == "recent") openPlaylist(open)
        }
    }

    override fun onCleared() {
        MadusApp.instance.onNotificationLike = null
        super.onCleared()
    }

    companion object {
        private const val HOUR_MS = 60L * 60L * 1000L
        private val REALTIME_FEEDBACK_TYPES = setOf(
            RecommendationEventType.LIKE,
            RecommendationEventType.COLLECT_LOCAL,
            RecommendationEventType.COLLECT_BILIBILI,
            RecommendationEventType.REPLAY,
        )
        /** UP 投稿分页，与收藏夹一致 */
        private const val UP_PAGE_SIZE = 40

        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppViewModel() as T
            }
        }
    }
}
