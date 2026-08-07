package com.madus.mobile.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.madus.mobile.domain.MusicSourceType
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.madus.mobile.ui.components.BiliRecognizeFab
import com.madus.mobile.ui.components.BiliRecognizeResultSheet
import com.madus.mobile.ui.components.CollectSheet
import com.madus.mobile.ui.components.CommentsSheet
import com.madus.mobile.ui.components.ImportPlaylistSheet
import com.madus.mobile.ui.components.MiniPlayerBar
import com.madus.mobile.ui.components.PlaySourceSheet
import com.madus.mobile.ui.components.ShortVideoGuideOverlay

import com.madus.mobile.ui.navigation.RootTab
import com.madus.mobile.ui.navigation.Routes
import com.madus.mobile.ui.screens.BiliFavListScreen
import com.madus.mobile.ui.screens.FullscreenVideoScreen
import com.madus.mobile.ui.screens.HomeScreen
import com.madus.mobile.MadusApp
import com.madus.mobile.data.ThemeSettings
import com.madus.mobile.ui.screens.LibraryScreen
import com.madus.mobile.ui.screens.MeScreen
import com.madus.mobile.ui.screens.NowPlayingScreen
import com.madus.mobile.ui.screens.PlaylistDetailScreen
import com.madus.mobile.ui.screens.QueueScreen
import com.madus.mobile.ui.screens.RecommendScreen
import com.madus.mobile.ui.screens.SearchScreen
import com.madus.mobile.ui.components.QualityPickerSheet
import com.madus.mobile.ui.components.SleepTimerSheet
import com.madus.mobile.ui.screens.AiChatScreen
import com.madus.mobile.ui.screens.AiConfigScreen
import com.madus.mobile.ui.screens.CacheManagerScreen
import com.madus.mobile.ui.screens.AboutEasterEggScreen
import com.madus.mobile.ui.screens.ChangelogScreen
import com.madus.mobile.ui.screens.UpdateScreen
import com.madus.mobile.ui.screens.PlaybackPrefsScreen
import com.madus.mobile.ui.screens.SettingsScreen
import com.madus.mobile.ui.screens.UpSpaceScreen
import com.madus.mobile.ai.AiChatViewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState

private data class TabItem(
    val tab: RootTab,
    val icon: ImageVector,
)

/** 左右对称：首页+搜索 | 推荐(正中) | 曲库+我的 */
private val leftTabs = listOf(
    TabItem(RootTab.Home, Icons.Outlined.Home),
    TabItem(RootTab.Search, Icons.Outlined.Search),
)
private val rightTabs = listOf(
    TabItem(RootTab.Library, Icons.Outlined.LibraryMusic),
    TabItem(RootTab.Me, Icons.Outlined.Person),
)
private val centerTab = TabItem(RootTab.Recommend, Icons.Outlined.Album)

@Composable
fun MadusRoot(
    modifier: Modifier = Modifier,
    vm: AppViewModel = viewModel(factory = AppViewModel.factory()),
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showChrome = route != null &&
        route in RootTab.routes &&
        route != Routes.FULLSCREEN_VIDEO
    val onRecommend = route == RootTab.Recommend.route

    val home by vm.home.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val recommend by vm.recommend.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val playlistDetail by vm.playlistDetail.collectAsStateWithLifecycle()
    val queueTracks by vm.queueTracks.collectAsStateWithLifecycle()
    val queueSearch by vm.queueSearch.collectAsStateWithLifecycle()
    val playMode by vm.playMode.collectAsStateWithLifecycle()
    val collect by vm.collect.collectAsStateWithLifecycle()
    val llmCfg by MadusApp.instance.llmConfigStore.state.collectAsStateWithLifecycle()
    val playSource by vm.playSource.collectAsStateWithLifecycle()
    val comments by vm.comments.collectAsStateWithLifecycle()
    val upSpace by vm.upSpace.collectAsStateWithLifecycle()
    val importPlaylist by vm.importPlaylist.collectAsStateWithLifecycle()
    val trackReplace by vm.trackReplace.collectAsStateWithLifecycle()
    val biliRecognize by vm.biliRecognize.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    val playerSettings by vm.playerSettings.collectAsStateWithLifecycle()
    val sleepRemainingMs by vm.sleepRemainingMs.collectAsStateWithLifecycle()
    val sleepSelectedMinutes by vm.sleepSelectedMinutes.collectAsStateWithLifecycle()
    val cacheManager by vm.cacheManager.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val themeSettings by MadusApp.instance.themePrefs.flow.collectAsState(
        initial = ThemeSettings(),
    )
    var showQualityPicker by remember { mutableStateOf(false) }
    var showSleepPicker by remember { mutableStateOf(false) }
    /** 清屏短视频内搜索浮层（不切 Tab，避免退出残留） */
    var immersiveSearchOpen by remember { mutableStateOf(false) }
    var showShortGuide by remember { mutableStateOf(false) }
    /** 识曲悬浮球：提升到 Root 记忆，切 tab 不丢位置/收起态 */
    var biliFabOffsetY by remember { mutableFloatStateOf(0f) }
    var biliFabExpanded by remember { mutableStateOf(true) }
    // initial=false：未读到 DataStore 前宁可先出指引，避免漏第一次
    val gestureGuideSeen by MadusApp.instance.playerPrefs
        .guideSeenForModeFlow(playerSettings.gestureMode)
        .collectAsState(initial = false)

    fun shareCurrent() {
        val text = vm.shareTextForCurrent() ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "分享"))
    }

    val sleepLabel = if (sleepRemainingMs > 0) {
        val m = (sleepRemainingMs / 60_000).toInt()
        val s = ((sleepRemainingMs / 1000) % 60).toInt()
        "${m}:${s.toString().padStart(2, '0')}"
    } else null

    /** 跳到推荐页电台台面（共用：点歌 / 搜索插播） */
    fun openRecommendPlayer() {
        // 关掉歌单详情并弹出栈：否则 popUpTo+saveState 会把「主页→歌单」存起来，
        // 再点主页 restore 后仍停在歌单里。
        vm.closePlaylist()
        runCatching { nav.popBackStack(Routes.PLAYLIST, inclusive = true) }
        nav.navigate(RootTab.Recommend.route) {
            popUpTo(nav.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    /** 点歌 → 进推荐页当电台台面播放，不另开独立播放页 */
    fun playAndOpen(
        track: com.madus.mobile.domain.Track,
        queue: List<com.madus.mobile.domain.Track>,
        sourceLabel: String? = null,
        sourceId: String? = null,
    ) {
        // 先点播（同步 suppress 推荐自动播），再跳转
        vm.playTrack(
            track = track,
            queue = queue,
            resumeIfSame = false,
            sourceLabel = sourceLabel,
            sourceId = sourceId,
        )
        openRecommendPlayer()
    }

    /**
     * 主搜索 Tab 点播：原逻辑插播 + 进推荐台。
     * 清屏短视频搜索走浮层，不经过这里。
     */
    fun playSearchAndOpen(track: com.madus.mobile.domain.Track) {
        vm.clearSearchFromImmersiveVideo()
        vm.playSearchTrack(track)
        openRecommendPlayer()
    }

    LaunchedEffect(onRecommend) {
        if (onRecommend) vm.onEnterRecommend()
    }

    LaunchedEffect(collect.toast) {
        val msg = collect.toast ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        vm.clearCollectToast()
    }

    LaunchedEffect(toast) {
        val msg = toast ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        vm.clearToast()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showChrome) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    // Soda-like: full-screen recommend hides mini bar (page is the player).
                    if (playback.current != null && !onRecommend) {
                        MiniPlayerBar(
                            playback = playback,
                            onToggle = vm::togglePlay,
                            onNext = vm::next,
                            // 点迷你条 → 推荐页电台台面（不是清屏页）
                            onOpenNowPlaying = {
                                nav.navigate(RootTab.Recommend.route) {
                                    popUpTo(nav.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onOpenQueue = {
                                nav.navigate(Routes.QUEUE) { launchSingleTop = true }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    LineSketchBottomBar(
                        route = route,
                        onSelect = { tab ->
                            // 回主页时：若还盖着歌单详情，先关掉，保证看到首页列表
                            if (tab == RootTab.Home) {
                                val cur = nav.currentBackStackEntry?.destination?.route
                                if (cur == Routes.PLAYLIST) {
                                    vm.closePlaylist()
                                    runCatching { nav.popBackStack(Routes.PLAYLIST, inclusive = true) }
                                }
                            }
                            // 底栏进搜索 = 普通搜索，不是清屏短视频内搜索
                            if (tab == RootTab.Search) {
                                vm.clearSearchFromImmersiveVideo()
                            }
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                // 主页不要 restore 到旧歌单详情
                                restoreState = tab != RootTab.Home
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            NavHost(
                navController = nav,
                startDestination = RootTab.Home.route,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(RootTab.Home.route) {
                    HomeScreen(
                        state = home,
                        onPlayTrack = ::playAndOpen,
                        onOpenPlaylist = { pl ->
                            vm.openPlaylist(pl)
                            nav.navigate(Routes.PLAYLIST)
                        },
                        onCollectTrack = { vm.openCollectSheet(it) },
                        onRemoveRecent = vm::removeFromRecent,
                        onClearRecent = vm::clearRecent,
                        onOpenMe = {
                            nav.navigate(RootTab.Me.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenRecentTab = {
                            nav.navigate(RootTab.Recommend.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                            vm.setRecommendSegment(com.madus.mobile.ui.RecommendSegment.Recent)
                        },
                        onOpenBiliList = {
                            nav.navigate(Routes.BILI_FAVS) { launchSingleTop = true }
                        },
                    )
                }
                composable(RootTab.Search.route) {
                    SearchScreen(
                        state = search,
                        onQueryChange = vm::onSearchQueryChange,
                        onSubmit = vm::submitSearch,
                        onSuggestionClick = vm::applySearchSuggestion,
                        onPlayTrack = { track ->
                            if (trackReplace.active) {
                                vm.applyReplaceTrack(track)
                                // 换歌后回队列或歌单更顺手
                                runCatching { nav.popBackStack(Routes.QUEUE, inclusive = false) }
                            } else {
                                playSearchAndOpen(track)
                            }
                        },
                        onCollectTrack = { vm.openCollectSheet(it) },
                        onOpenAiSearch = {
                            if (!trackReplace.active) {
                                nav.navigate(Routes.AI_CHAT) { launchSingleTop = true }
                            }
                        },
                        replaceHintTitle = trackReplace.oldTitle.takeIf { trackReplace.active },
                        onCancelReplace = if (trackReplace.active) {
                            { vm.cancelReplaceTrack() }
                        } else {
                            null
                        },
                    )
                }
                composable(RootTab.Recommend.route) {
                    RecommendScreen(
                        state = recommend,
                        playback = playback,
                        onSegment = vm::setRecommendSegment,
                        onToggle = vm::togglePlay,
                        onNext = vm::next,
                        onPrevious = vm::previous,
                        onToggleLike = vm::toggleLikeCurrent,
                        onPlayTrack = ::playAndOpen,
                        onOpenQueue = {
                            nav.navigate(Routes.QUEUE) { launchSingleTop = true }
                        },
                        onSeek = vm::seek,
                        onCollectCurrent = { vm.openCollectSheet(null, openBiliTab = false) },
                        onCollectTrack = { vm.openCollectSheet(it, openBiliTab = false) },
                        onBiliCollectCurrent = { vm.openBiliCollectSheet(null) },
                        onOpenPlaySource = { nav.navigate(Routes.QUEUE) { launchSingleTop = true } },
                        onRemoveRecent = vm::removeFromRecent,
                        onClearRecent = vm::clearRecent,
                        onImmersive = {
                            if (playback.current != null) {
                                nav.navigate(Routes.NOW_PLAYING) { launchSingleTop = true }
                            }
                        },
                        onShare = { shareCurrent() },
                        onComments = { vm.openComments() },
                        onCache = vm::cacheCurrentTrack,
                        onRelatedRadio = vm::playRelatedRadio,
                        onStartRadio = vm::startRecommendIfReady,
                        onLogin = { vm.requestLogin(MusicSourceType.BILIBILI) },
                        onQualityClick = { showQualityPicker = true },
                        onSleepClick = { showSleepPicker = true },
                        qualityLabel = playerSettings.quality.label,
                        sleepLabel = sleepLabel,
                        videoMode = playerSettings.videoMode,
                        onVideoModeChange = vm::setVideoMode,
                        // 视频也进清屏=心动模式（无边框全屏）
                        onFullscreen = {
                            if (playback.current != null) {
                                nav.navigate(Routes.NOW_PLAYING) { launchSingleTop = true }
                            }
                        },
                        onOpenUp = {
                            val t = playback.current ?: return@RecommendScreen
                            // 防双击双开：已在 UP 页则只刷新
                            if (nav.currentBackStackEntry?.destination?.route != Routes.UP_SPACE) {
                                nav.navigate(Routes.UP_SPACE) {
                                    launchSingleTop = true
                                }
                            }
                            vm.openUpSpace(t)
                        },
                    )
                }
                composable(RootTab.Library.route) {
                    LaunchedEffect(Unit) { vm.refreshLibrary() }
                    LibraryScreen(
                        state = library,
                        onOpenPlaylist = { pl ->
                            vm.openPlaylist(pl)
                            nav.navigate(Routes.PLAYLIST)
                        },
                        onOpenRecent = {
                            vm.openRecentPlaylist()
                            nav.navigate(Routes.PLAYLIST)
                        },
                        onOpenBiliList = {
                            nav.navigate(Routes.BILI_FAVS) { launchSingleTop = true }
                        },
                        onOpenCache = {
                            vm.refreshCacheManager()
                            nav.navigate(Routes.CACHE_MANAGER) { launchSingleTop = true }
                        },
                        onCreatePlaylist = vm::createLocalPlaylist,
                        onPlayTrack = { track, queue ->
                            playAndOpen(track, queue, sourceLabel = "最近播放", sourceId = "recent")
                        },
                        onCollectTrack = { vm.openCollectSheet(it) },
                        onRemoveRecent = vm::removeFromRecent,
                        onLoginBili = { vm.requestLogin(MusicSourceType.BILIBILI) },
                        onImportPlaylist = vm::openImportPlaylistSheet,
                    )
                }
                composable(RootTab.Me.route) {
                    LaunchedEffect(Unit) { vm.refreshMeStats() }
                    MeScreen(
                        state = me,
                        onOpenBiliLogin = { vm.requestLogin(MusicSourceType.BILIBILI) },
                        onOpenSettings = {
                            nav.navigate(Routes.SETTINGS) { launchSingleTop = true }
                        },
                        onOpenEasterEgg = {
                            nav.navigate(Routes.ABOUT_EASTER_EGG) { launchSingleTop = true }
                        },
                        onAboutSystemToast = { msg ->
                            scope.launch {
                                snackbar.currentSnackbarData?.dismiss()
                                snackbar.showSnackbar(msg)
                            }
                        },
                        onToolClick = { key ->
                            when (key) {
                                "playback" -> nav.navigate(Routes.PLAYBACK_PREFS) { launchSingleTop = true }
                                "cache" -> {
                                    vm.refreshCacheManager()
                                    nav.navigate(Routes.CACHE_MANAGER) { launchSingleTop = true }
                                }
                                "import" -> vm.openImportPlaylistSheet()
                                "update" -> nav.navigate(Routes.UPDATE) {
                                    launchSingleTop = true
                                    // 已在栈顶则不重复压入
                                    restoreState = true
                                }
                                "changelog" -> nav.navigate(Routes.CHANGELOG) { launchSingleTop = true }
                                else -> scope.launch {
                                    when (key) {
                                        "local" -> snackbar.showSnackbar("本地文件扫描 · 后续版本")
                                        else -> { /* about 已在 MeScreen 内部处理，不再每次弹窗 */ }
                                    }
                                }
                            }
                        },
                    )
                }
                composable(Routes.AI_CHAT) {
                    val aiVm: AiChatViewModel = viewModel(factory = AiChatViewModel.factory())
                    val aiState by aiVm.ui.collectAsStateWithLifecycle()
                    AiChatScreen(
                        state = aiState,
                        onBack = { nav.popBackStack() },
                        onOpenConfig = {
                            nav.navigate(Routes.AI_CONFIG) { launchSingleTop = true }
                        },
                        onInputChange = aiVm::onInputChange,
                        onSend = aiVm::send,
                        onSelectProfile = aiVm::setActiveProfile,
                        onDismissGuide = aiVm::dismissGuide,
                        onOpenHistory = aiVm::openHistory,
                        onCloseHistory = aiVm::closeHistory,
                        onNewChat = aiVm::newChat,
                        onOpenSession = aiVm::openSession,
                        onDeleteSession = aiVm::deleteSession,
                        onToggleRecord = aiVm::toggleRecording,
                        onCancelRecord = aiVm::cancelRecording,
          onPickImage = aiVm::sendImage,
          onPickVideo = aiVm::sendVideo,
          currentTrack = playback.current,
          currentPositionMs = playback.positionMs,
          onTogglePlaybackRecording = aiVm::togglePlaybackRecording,
          onExpandModelProcess = aiVm::expandModelProcess,
          onCollapseModelProcess = aiVm::collapseModelProcess,
          onPlayTrack = ::playSearchAndOpen,
                        onCollectTrack = { vm.openCollectSheet(it) },
                        onSearchCandidate = aiVm::searchHummingCandidate,
                    )
                }
                composable(Routes.AI_CONFIG) {
                    AiConfigScreen(
                        store = MadusApp.instance.llmConfigStore,
                        hummingStore = MadusApp.instance.hummingConfigStore,
                        onBack = { nav.popBackStack() },
                        onSaved = {
                            // 回到对话；刷新配置
                            nav.popBackStack()
                        },
                    )
                }
                composable(Routes.UPDATE) {
                    UpdateScreen(
                        onBack = { nav.popBackStack() },
                        onOpenChangelog = {
                            nav.navigate(Routes.CHANGELOG) { launchSingleTop = true }
                        },
                    )
                }
                composable(Routes.CHANGELOG) {
                    ChangelogScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.ABOUT_EASTER_EGG) {
                    AboutEasterEggScreen(
                        version = me.appVersion,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(Routes.PLAYBACK_PREFS) {
                    PlaybackPrefsScreen(
                        playerSettings = playerSettings,
                        cacheLabel = me.cacheSizeLabel,
                        onBack = { nav.popBackStack() },
                        onSoundFx = vm::setSoundFx,
                        onAutoCache = vm::setAutoCache,
                        onGameMixAudio = vm::setGameMixAudio,
                        onGameLiteMode = vm::setGameLiteMode,
                        onOpenCacheManager = {
                            vm.refreshCacheManager()
                            nav.navigate(Routes.CACHE_MANAGER) { launchSingleTop = true }
                        },
                    )
                }
                composable(Routes.CACHE_MANAGER) {
                    LaunchedEffect(Unit) { vm.refreshCacheManager() }
                    CacheManagerScreen(
                        state = cacheManager,
                        onBack = { nav.popBackStack() },
                        onRemove = vm::removeCachedTrack,
                        onClearOffline = vm::clearOfflineCacheOnly,
                        onClearStream = vm::clearStreamCacheOnly,
                        onClearAll = vm::clearStreamCache,
                        onPlay = { cached ->
                            playAndOpen(cached.track, listOf(cached.track), sourceLabel = "缓存")
                        },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        settings = themeSettings,
                        videoMode = playerSettings.videoMode,
                        gestureMode = playerSettings.gestureMode,
                        onBack = { nav.popBackStack() },
                        onAppearance = { mode ->
                            scope.launch {
                                MadusApp.instance.themePrefs.setAppearance(mode)
                            }
                        },
                        onColorTheme = { theme ->
                            scope.launch {
                                MadusApp.instance.themePrefs.setColorTheme(theme)
                            }
                        },
                        onVideoMode = vm::setVideoMode,
                        onGestureMode = vm::setGestureMode,
                    )
                }
                composable(Routes.PLAYLIST) {
                    val pl = playlistDetail.playlist
                    val id = pl?.id.orEmpty()
                    val isLiked = id == com.madus.mobile.data.LikedStore.LIKED_ID
                    val isRecent = id == "recent"
                    val isLocal = id.startsWith("local-")
                    // 本地 / 喜欢 / B站收藏夹：都能换封面；最近播放不支持换封面/删除
                    val canCover = id.isNotBlank() && !isRecent
                    val canRename = isLocal && !isLiked
                    val canRemove = isLocal || isLiked || isRecent
                    val canDeletePlaylist = isLocal && !isLiked
                    PlaylistDetailScreen(
                        state = playlistDetail,
                        isLocalPlaylist = isLocal || isRecent,
                        canChangeCover = canCover,
                        canRename = canRename,
                        canRemoveTrack = canRemove,
                        canDeletePlaylist = canDeletePlaylist,
                        onBack = {
                            vm.closePlaylist()
                            nav.popBackStack()
                        },
                        onPlayTrack = { track, queue ->
                            playAndOpen(
                                track,
                                queue,
                                sourceLabel = pl?.title,
                                sourceId = pl?.id,
                            )
                        },
                        onPlayAll = {
                            val tracks = playlistDetail.tracks
                            val first = tracks.firstOrNull() ?: return@PlaylistDetailScreen
                            playAndOpen(
                                first,
                                tracks,
                                sourceLabel = pl?.title,
                                sourceId = pl?.id,
                            )
                        },
                        onRename = { name ->
                            val pid = pl?.id ?: return@PlaylistDetailScreen
                            if (canRename) vm.renameLocalPlaylist(pid, name)
                        },
                        onRemoveTrack = { trackId ->
                            val pid = pl?.id ?: return@PlaylistDetailScreen
                            if (canRemove) vm.removeFromLocalPlaylist(pid, trackId)
                        },
                        onDeletePlaylist = {
                            val pid = pl?.id ?: return@PlaylistDetailScreen
                            if (canDeletePlaylist) {
                                vm.deleteLocalPlaylist(pid)
                                nav.popBackStack()
                            }
                        },
                        onCollectTrack = { vm.openCollectSheet(it) },
                        onSetCover = { uri ->
                            val pid = pl?.id ?: return@PlaylistDetailScreen
                            if (canCover) vm.setPlaylistCover(pid, uri)
                        },
                        onLoadMore = vm::loadMorePlaylistTracks,
                        onPrevPage = vm::loadPlaylistPrevPage,
                        onNextPage = vm::loadPlaylistNextPage,
                        onOpenUp = { track ->
                            if (nav.currentBackStackEntry?.destination?.route != Routes.UP_SPACE) {
                                nav.navigate(Routes.UP_SPACE) { launchSingleTop = true }
                            }
                            vm.openUpSpace(track)
                        },
                        onReplaceTrack = { track ->
                            val pid = pl?.id
                            vm.beginReplaceTrack(track, playlistId = pid)
                            nav.navigate(RootTab.Search.route) {
                                launchSingleTop = true
                                popUpTo(RootTab.Home.route) { saveState = true }
                                restoreState = true
                            }
                        },
                    )
                }
                composable(Routes.UP_SPACE) {
                    UpSpaceScreen(
                        state = upSpace,
                        onBack = {
                            vm.clearUpSpace()
                            nav.popBackStack()
                        },
                        onTab = vm::setUpSpaceTab,
                        onPlayTrack = { track, queue ->
                            playAndOpen(
                                track,
                                queue,
                                sourceLabel = upSpace.profile?.name ?: "UP主",
                                sourceId = "up-${upSpace.mid}",
                            )
                        },
                        onOpenSeason = vm::openUpSeason,
                        onCloseSeason = vm::closeUpSeason,
                        onPrevPage = vm::loadUpVideosPrevPage,
                        onNextPage = vm::loadUpVideosNextPage,
                        onToggleFollow = vm::toggleFollowUp,
                    )
                }
                composable(Routes.BILI_FAVS) {
                    BiliFavListScreen(
                        playlists = home.playlists,
                        onBack = { nav.popBackStack() },
                        onOpen = { pl ->
                            vm.openPlaylist(pl)
                            nav.navigate(Routes.PLAYLIST)
                        },
                    )
                }
                composable(Routes.QUEUE) {
                    QueueScreen(
                        tracks = queueTracks.ifEmpty { playback.queue },
                        currentId = playback.current?.id,
                        playMode = playMode,
                        sourceLabel = recommend.sourceLabel,
                        queueSearch = queueSearch,
                        onBack = {
                            vm.clearQueueSearch()
                            nav.popBackStack()
                        },
                        onPlayTrack = ::playAndOpen,
                        onClear = vm::clearQueue,
                        onRemove = vm::removeFromQueue,
                        onReplaceTrack = { track ->
                            vm.beginReplaceTrack(track)
                            nav.navigate(RootTab.Search.route) {
                                launchSingleTop = true
                                // 从队列去搜索：保留返回栈
                            }
                        },
                        onPlayNext = vm::queuePlayNext,
                        onShuffle = vm::shuffleQueue,
                        onCycleMode = vm::cyclePlayMode,
                        onMove = vm::moveInQueue,
                        onOpenPlaySource = vm::openPlaySourceSheet,
                        onCollectCurrent = { vm.openCollectSheet(null) },
                        onSearchQueryChange = vm::onQueueSearchQueryChange,
                        onSearchSubmit = vm::submitQueueSearch,
                        onSearchClear = vm::clearQueueSearch,
                        onPlaySearchResult = { track ->
                            // 队列搜索 = 普通插播，不强制清屏
                            vm.playFromQueueSearch(track)
                        },
                    )
                }
                // 清屏 / 迷你条 → 沉浸页：自下而上丝滑进入（Apple 心智）
                composable(
                    route = Routes.NOW_PLAYING,
                    enterTransition = {
                        slideInVertically(
                            animationSpec = tween(420, easing = FastOutSlowInEasing),
                            initialOffsetY = { it },
                        ) + fadeIn(animationSpec = tween(280))
                    },
                    exitTransition = {
                        fadeOut(animationSpec = tween(200))
                    },
                    popEnterTransition = {
                        fadeIn(animationSpec = tween(220))
                    },
                    popExitTransition = {
                        slideOutVertically(
                            animationSpec = tween(380, easing = FastOutSlowInEasing),
                            targetOffsetY = { it },
                        ) + fadeOut(animationSpec = tween(280))
                    },
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val inVideoUi = (playerSettings.videoMode || MadusApp.instance.videoModeEnabled) &&
                            (playback.current?.isVideoStream == true || playback.isLoading)
                        // 每种操作模式第一次进短视频都要看专属指引
                        LaunchedEffect(inVideoUi, gestureGuideSeen, playerSettings.gestureMode) {
                            if (inVideoUi && !gestureGuideSeen) showShortGuide = true
                        }
                        // 短视频右侧 UP 头像：切歌时懒加载 face
                        var shortUpFace by remember { mutableStateOf("") }
                        LaunchedEffect(playback.current?.id, playback.current?.ownerFace) {
                            val t = playback.current
                            shortUpFace = t?.ownerFace.orEmpty()
                            if (shortUpFace.isBlank() && t != null) {
                                shortUpFace = vm.loadOwnerFace(t)
                            }
                        }
                        NowPlayingScreen(
                            playback = playback,
                            liked = playback.current?.id?.let { recommend.likedIds.contains(it) } == true,
                            onBack = {
                                immersiveSearchOpen = false
                                showShortGuide = false
                                nav.popBackStack()
                            },
                            onToggle = vm::togglePlay,
                            onNext = vm::next,
                            onPrevious = vm::previous,
                            onSeek = vm::seek,
                            onToggleLike = vm::toggleLikeCurrent,
                            onOpenQueue = {
                                nav.navigate(Routes.QUEUE) { launchSingleTop = true }
                            },
                            onCollectLocal = { vm.openCollectSheet(null, openBiliTab = false) },
                            onCollectBili = { vm.openBiliCollectSheet(null) },
                            onComments = { vm.openComments() },
                            onCache = vm::cacheCurrentTrack,
                            onShare = { shareCurrent() },
                            onSearch = {
                                // 浮层搜索：不离开清屏页，避免 Surface/底栏残留
                                immersiveSearchOpen = true
                            },
                            onQualityClick = { showQualityPicker = true },
                            onSleepClick = { showSleepPicker = true },
                            qualityLabel = playerSettings.quality.label,
                            sleepLabel = sleepLabel,
                            videoMode = playerSettings.videoMode || MadusApp.instance.videoModeEnabled,
                            gestureMode = playerSettings.gestureMode,
                            onFullscreen = {
                                nav.navigate(Routes.FULLSCREEN_VIDEO) { launchSingleTop = true }
                            },
                            onSetSpeed = vm::setPlaybackSpeed,
                            onGetSpeed = vm::currentPlaybackSpeed,
                            onPlaySeries = { vm.playSeriesContinuous(null) },
                            ownerFaceUrl = shortUpFace,
                            onOpenUp = {
                                val t = playback.current ?: return@NowPlayingScreen
                                if (nav.currentBackStackEntry?.destination?.route != Routes.UP_SPACE) {
                                    nav.navigate(Routes.UP_SPACE) { launchSingleTop = true }
                                }
                                vm.openUpSpace(t)
                            },
                        )

                        if (showShortGuide && inVideoUi) {
                            ShortVideoGuideOverlay(
                                gestureMode = playerSettings.gestureMode,
                                onDismiss = {
                                    showShortGuide = false
                                    scope.launch {
                                        MadusApp.instance.playerPrefs
                                            .setGuideSeenForMode(playerSettings.gestureMode, true)
                                    }
                                },
                                modifier = Modifier.zIndex(3f),
                            )
                        }

                        if (immersiveSearchOpen) {
                            BackHandler { immersiveSearchOpen = false }
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(2f),
                                color = MaterialTheme.colorScheme.background,
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        IconButton(onClick = { immersiveSearchOpen = false }) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "关闭搜索",
                                            )
                                        }
                                        Text(
                                            text = "搜索视频",
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                    }
                                    SearchScreen(
                                        state = search,
                                        onQueryChange = vm::onSearchQueryChange,
                                        onSubmit = vm::submitSearch,
                                        onSuggestionClick = vm::applySearchSuggestion,
                                        onPlayTrack = { track ->
                                            vm.playSearchTrackFromImmersiveVideo(track)
                                            immersiveSearchOpen = false
                                        },
                                        onCollectTrack = { vm.openCollectSheet(it) },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
                composable(Routes.FULLSCREEN_VIDEO) {
                    FullscreenVideoScreen(
                        playback = playback,
                        liked = playback.current?.id?.let { recommend.likedIds.contains(it) } == true,
                        qualityLabel = playerSettings.quality.label,
                        gestureMode = playerSettings.gestureMode,
                        onBack = { nav.popBackStack() },
                        onToggle = vm::togglePlay,
                        onNext = vm::next,
                        onPrevious = vm::previous,
                        onSeek = vm::seek,
                        onToggleLike = vm::toggleLikeCurrent,
                        onComments = { vm.openComments() },
                        onShare = { shareCurrent() },
                        onCollect = { vm.openCollectSheet(null, openBiliTab = false) },
                        onQualityClick = { showQualityPicker = true },
                        onSleepClick = { showSleepPicker = true },
                        onCache = vm::cacheCurrentTrack,
                        onSetSpeed = vm::setPlaybackSpeed,
                        onGetSpeed = vm::currentPlaybackSpeed,
                        onPlaySeries = { vm.playSeriesContinuous(null) },
                    )
                }
            }

            // 推荐页：B站识曲轻量悬浮球（空闲自动收起；非 LLM 半屏面板）
            if (onRecommend &&
                recommend.segment == com.madus.mobile.ui.RecommendSegment.Feed &&
                playback.current != null
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(8f),
                ) {
                    BiliRecognizeFab(
                        enabled = true,
                        state = biliRecognize,
                        onRecognize = vm::recognizeBiliBgmOnCurrent,
                        expanded = biliFabExpanded,
                        onExpandedChange = { biliFabExpanded = it },
                        offsetY = biliFabOffsetY,
                        onOffsetYDelta = { delta ->
                            val range = 280f * context.resources.displayMetrics.density
                            biliFabOffsetY = (biliFabOffsetY + delta).coerceIn(-range, range)
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 0.dp),
                    )
                }
            }
        }

        if (biliRecognize.panelVisible) {
            BiliRecognizeResultSheet(
                state = biliRecognize,
                onDismiss = vm::dismissBiliRecognizePanel,
                onPlay = { track, queue ->
                    vm.playBiliRecognizeTrack(track, queue)
                },
            )
        }

        // Spotify-style add-to-playlist sheet
        val collectTrack = collect.track
        if (collect.visible && collectTrack != null) {
            CollectSheet(
                track = collectTrack,
                playlists = collect.playlists,
                tab = collect.tab,
                onTabChange = vm::setCollectTab,
                onDismiss = vm::dismissCollectSheet,
                onAddTo = vm::collectToPlaylist,
                onCreateAndAdd = vm::collectCreateAndAdd,
                onAddSeriesTo = vm::collectSeriesToLocal,
                onCreateSeriesAndAdd = vm::collectCreateSeriesAndAdd,
                biliLoggedIn = collect.biliLoggedIn,
                biliFolders = collect.biliFolders,
                selectedBiliFolderId = collect.selectedBiliFolderId,
                biliSyncing = collect.biliSyncing,
                onSelectBiliFolder = vm::setCollectBiliFolder,
                onConfirmBiliCollect = vm::collectToSelectedBiliFolder,
                onConfirmBiliSeriesCollect = vm::collectSeriesToSelectedBiliFolder,
                onLoginBili = { vm.requestLogin(MusicSourceType.BILIBILI) },
                onCreateBiliFolderAndCollect = vm::createBiliFolderAndCollect,
                seriesCount = collect.seriesCount,
            )
        }

        // 选择播放来源（换歌单听）
        if (playSource.visible) {
            PlaySourceSheet(
                currentSourceLabel = recommend.sourceLabel,
                sources = playSource.sources,
                onDismiss = vm::dismissPlaySourceSheet,
                onSelect = vm::selectPlaySource,
            )
        }

        if (comments.visible) {
            CommentsSheet(
                state = comments,
                onDismiss = vm::dismissComments,
                onDraftChange = vm::onCommentDraftChange,
                onPost = vm::postComment,
                onLoadMore = vm::loadMoreComments,
                onReply = vm::beginReplyComment,
                onCancelReply = vm::cancelReplyComment,
                onLoadAllReplies = vm::loadAllCommentReplies,
            )
        }

        if (importPlaylist.visible) {
            ImportPlaylistSheet(
                state = importPlaylist,
                onDismiss = vm::dismissImportPlaylistSheet,
                onInput = vm::onImportPlaylistInput,
                onStart = vm::startImportPlaylist,
            )
        }

        if (showQualityPicker) {
            QualityPickerSheet(
                current = playerSettings.quality,
                onSelect = vm::setQuality,
                onDismiss = { showQualityPicker = false },
            )
        }

        if (showSleepPicker) {
            SleepTimerSheet(
                activeMinutes = sleepSelectedMinutes.takeIf { it > 0 },
                remainingLabel = sleepLabel,
                onSelectMinutes = vm::setSleepTimer,
                onDismiss = { showSleepPicker = false },
            )
        }
    }
}

@Composable
private fun LineSketchBottomBar(
    route: String?,
    onSelect: (RootTab) -> Unit,
) {
    // 布局：首页 搜索 | 推荐(正中) | 曲库 我的
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .padding(horizontal = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leftTabs.forEach { item ->
                    BottomTabItem(
                        item = item,
                        selected = route == item.tab.route,
                        onClick = { onSelect(item.tab) },
                    )
                }
            }
            // 中央留给推荐
            Spacer(modifier = Modifier.width(72.dp))
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rightTabs.forEach { item ->
                    BottomTabItem(
                        item = item,
                        selected = route == item.tab.route,
                        onClick = { onSelect(item.tab) },
                    )
                }
            }
        }

        // 推荐：几何正中，略放大
        val centerSelected = route == centerTab.tab.route
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(72.dp)
                .clickable { onSelect(centerTab.tab) }
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = centerTab.icon,
                contentDescription = centerTab.tab.label,
                modifier = Modifier.size(if (centerSelected) 30.dp else 28.dp),
                tint = if (centerSelected) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = centerTab.tab.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (centerSelected) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .size(width = if (centerSelected) 18.dp else 0.dp, height = 1.5.dp)
                    .background(MaterialTheme.colorScheme.onBackground),
            )
        }
    }
}

@Composable
private fun BottomTabItem(
    item: TabItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.tab.label,
            modifier = Modifier.size(24.dp),
            tint = if (selected) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = item.tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .size(width = if (selected) 16.dp else 0.dp, height = 1.dp)
                .background(MaterialTheme.colorScheme.onBackground),
        )
    }
}
