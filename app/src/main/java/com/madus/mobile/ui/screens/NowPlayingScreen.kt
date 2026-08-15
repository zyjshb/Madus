package com.madus.mobile.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.madus.mobile.data.SoundFx
import com.madus.mobile.data.VIDEO_SPEED_OPTIONS
import com.madus.mobile.data.VideoGestureMode
import com.madus.mobile.domain.PlaybackState
import com.madus.mobile.ui.PlayModeLabel
import com.madus.mobile.ui.components.BiliPlayerSurface
import com.madus.mobile.ui.components.CoverArt
import com.madus.mobile.ui.components.MadusImageLoader
import com.madus.mobile.ui.components.SeekBar
import com.madus.mobile.ui.components.hasVisibleLines
import com.madus.mobile.ui.components.ThinVideoProgress
import com.madus.mobile.ui.components.normalizeCoverUrl
import com.madus.mobile.ui.theme.isLiquidTheme
import com.madus.mobile.ui.theme.liquidTokens
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 清屏短视频：手势随 [gestureMode]（抖音/B站/快手）。
 * 右侧 UP 头像 + 赞评；底左标题 + 进度；顶栏返回/搜索
 */
@Composable
fun NowPlayingScreen(
    playback: PlaybackState,
    liked: Boolean = false,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleLike: () -> Unit = {},
    onNotInterested: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onCollectLocal: () -> Unit = {},
    onCollectBili: () -> Unit = {},
    onComments: () -> Unit = {},
    onCache: () -> Unit = {},
    onShare: () -> Unit = {},
    onSearch: () -> Unit = {},
    onQualityClick: () -> Unit = {},
    onSleepClick: () -> Unit = {},
    qualityLabel: String = "标准",
    sleepLabel: String? = null,
    playMode: PlayModeLabel = PlayModeLabel.LOOP,
    onCyclePlayMode: () -> Unit = {},
    soundFx: SoundFx = SoundFx.Flat,
    onCycleSoundFx: () -> Unit = {},
    videoMode: Boolean = false,
    gestureMode: VideoGestureMode = VideoGestureMode.DOUYIN,
    onFullscreen: () -> Unit = {},
    onSetSpeed: (Float) -> Unit = {},
    onGetSpeed: () -> Float = { 1f },
    onPlaySeries: () -> Unit = {},
    onOpenUp: () -> Unit = {},
    ownerFaceUrl: String = "",
    lyrics: com.madus.mobile.domain.LyricsUiState = com.madus.mobile.domain.LyricsUiState(),
    modifier: Modifier = Modifier,
) {
    val track = playback.current
    val showVideo = videoMode && track != null && (track.isVideoStream || playback.isLoading)

    if (showVideo) {
        DouyinStyleVideoMode(
            playback = playback,
            liked = liked,
            gestureMode = gestureMode,
            onBack = onBack,
            onToggle = onToggle,
            onNext = onNext,
            onPrevious = onPrevious,
            onSeek = onSeek,
            onToggleLike = onToggleLike,
            onNotInterested = onNotInterested,
            onCollectLocal = onCollectLocal,
            onCollectBili = onCollectBili,
            onComments = onComments,
            onCache = onCache,
            onShare = onShare,
            onSearch = onSearch,
            onQualityClick = onQualityClick,
            onSleepClick = onSleepClick,
            qualityLabel = qualityLabel,
            sleepLabel = sleepLabel,
            onFullscreen = onFullscreen,
            onSetSpeed = onSetSpeed,
            onGetSpeed = onGetSpeed,
            onPlaySeries = onPlaySeries,
            onOpenUp = onOpenUp,
            ownerFaceUrl = ownerFaceUrl,
            modifier = modifier,
        )
    } else {
        MusicImmersiveMode(
            playback = playback,
            liked = liked,
            onBack = onBack,
            onToggle = onToggle,
            onNext = onNext,
            onPrevious = onPrevious,
            onSeek = onSeek,
            onToggleLike = onToggleLike,
            onOpenQueue = onOpenQueue,
            onQualityClick = onQualityClick,
            onSleepClick = onSleepClick,
            qualityLabel = qualityLabel,
            sleepLabel = sleepLabel,
            playMode = playMode,
            onCyclePlayMode = onCyclePlayMode,
            soundFx = soundFx,
            onCycleSoundFx = onCycleSoundFx,
            lyrics = lyrics,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DouyinStyleVideoMode(
    playback: PlaybackState,
    liked: Boolean,
    gestureMode: VideoGestureMode,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleLike: () -> Unit,
    onNotInterested: () -> Unit = {},
    onCollectLocal: () -> Unit,
    onCollectBili: () -> Unit,
    onComments: () -> Unit,
    onCache: () -> Unit,
    onShare: () -> Unit,
    onSearch: () -> Unit,
    onQualityClick: () -> Unit,
    onSleepClick: () -> Unit,
    qualityLabel: String,
    sleepLabel: String?,
    onFullscreen: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onGetSpeed: () -> Float = { 1f },
    onPlaySeries: () -> Unit = {},
    onOpenUp: () -> Unit = {},
    ownerFaceUrl: String = "",
    modifier: Modifier = Modifier,
) {
    val track = playback.current
    var dragAcc by remember { mutableFloatStateOf(0f) }
    var showMoreMenu by remember { mutableStateOf(false) }
    /** B 站模式：单击显隐底/侧信息条 */
    var showChrome by remember { mutableStateOf(true) }
    val moreSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var speedLocked by remember { mutableStateOf(false) }
    var holdSpeedActive by remember { mutableStateOf(false) }
    var holdDragY by remember { mutableFloatStateOf(0f) }
    var flashHint by remember { mutableStateOf<String?>(null) }

    var heartBursts by remember { mutableStateOf(listOf<HeartBurst>()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? Activity

    // 清屏视频：播放中保持亮屏，暂停后允许熄屏（长视频不操作不黑屏）
    DisposableEffect(playback.isPlaying) {
        val window = activity?.window
        if (window != null) {
            if (playback.isPlaying) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(flashHint) {
        if (flashHint != null) {
            kotlinx.coroutines.delay(650)
            flashHint = null
        }
    }

    // B 站：播放中信息条自动藏
    LaunchedEffect(showChrome, playback.isPlaying, gestureMode, showMoreMenu) {
        if (gestureMode != VideoGestureMode.BILIBILI) return@LaunchedEffect
        if (showChrome && playback.isPlaying && !showMoreMenu) {
            kotlinx.coroutines.delay(2800)
            if (playback.isPlaying && !showMoreMenu) showChrome = false
        }
    }

    fun flash(msg: String) {
        flashHint = msg
    }

    fun spawnHearts(center: Offset) {
        val burstId = System.nanoTime()
        val extras = List(4) { i ->
            HeartBurst(
                id = burstId + i,
                x = center.x + (i - 1.5f) * 28f + (if (i % 2 == 0) 12f else -12f),
                y = center.y + (i - 2) * 18f,
                scale = 0.55f + i * 0.12f,
                delayMs = i * 40L,
            )
        }
        heartBursts = heartBursts + HeartBurst(id = burstId + 99, x = center.x, y = center.y, scale = 1f, delayMs = 0) + extras
        scope.launch {
            kotlinx.coroutines.delay(900)
            heartBursts = heartBursts.filterNot { it.id in extras.map { e -> e.id } + burstId + 99 }
        }
    }

    val overlayVisible = gestureMode != VideoGestureMode.BILIBILI || showChrome

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(track?.id) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        when {
                            dragAcc < -90f -> onNext()
                            dragAcc > 90f -> onPrevious()
                        }
                        dragAcc = 0f
                    },
                    onDragCancel = { dragAcc = 0f },
                    onVerticalDrag = { _, amount -> dragAcc += amount },
                )
            }
            .pointerInput(track?.id, playback.isPlaying, gestureMode, speedLocked) {
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                val cornerW = w * 0.32f
                val cornerH = h * 0.28f
                detectTapGestures(
                    onPress = { offset ->
                        // B站：下半屏按住临时 2x
                        if (gestureMode == VideoGestureMode.BILIBILI && offset.y >= h * 0.5f) {
                            val releasedEarly = kotlinx.coroutines.withTimeoutOrNull(350) {
                                tryAwaitRelease()
                                true
                            }
                            if (releasedEarly != true) {
                                holdSpeedActive = true
                                onSetSpeed(2f)
                                flash("2.0x")
                                tryAwaitRelease()
                                if (!speedLocked) onSetSpeed(1f)
                                holdSpeedActive = false
                            }
                        }
                    },
                    onDoubleTap = { offset ->
                        when (gestureMode) {
                            VideoGestureMode.BILIBILI -> {
                                onToggle()
                                showChrome = true
                            }
                            else -> {
                                spawnHearts(offset)
                                onToggleLike()
                            }
                        }
                    },
                    onLongPress = { offset ->
                        when (gestureMode) {
                            VideoGestureMode.KUAISHOU -> showMoreMenu = true
                            VideoGestureMode.BILIBILI -> {
                                // 上半屏 = 菜单
                                if (offset.y < h * 0.5f) showMoreMenu = true
                            }
                            VideoGestureMode.DOUYIN -> {
                                val inCorner =
                                    offset.y <= cornerH && (offset.x <= cornerW || offset.x >= w - cornerW)
                                if (!inCorner) showMoreMenu = true
                            }
                        }
                    },
                    onTap = {
                        when (gestureMode) {
                            VideoGestureMode.BILIBILI -> showChrome = !showChrome
                            VideoGestureMode.DOUYIN, VideoGestureMode.KUAISHOU -> onToggle()
                        }
                    },
                )
            }
            .pointerInput(track?.id, speedLocked, gestureMode) {
                // 抖音角上长按 2x
                if (gestureMode != VideoGestureMode.DOUYIN) return@pointerInput
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                val cornerW = w * 0.32f
                val cornerH = h * 0.28f
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val inCorner =
                            offset.y <= cornerH && (offset.x <= cornerW || offset.x >= w - cornerW)
                        if (!inCorner) {
                            holdSpeedActive = false
                            return@detectDragGesturesAfterLongPress
                        }
                        holdDragY = 0f
                        holdSpeedActive = true
                        onSetSpeed(2f)
                        flash("2.0x")
                    },
                    onDrag = { change, dragAmount ->
                        if (!holdSpeedActive) return@detectDragGesturesAfterLongPress
                        change.consume()
                        holdDragY += dragAmount.y
                    },
                    onDragEnd = {
                        if (!holdSpeedActive) return@detectDragGesturesAfterLongPress
                        when {
                            holdDragY > 70f -> {
                                speedLocked = true
                                onSetSpeed(2f)
                                flash("已锁定 2.0x")
                            }
                            holdDragY < -70f -> {
                                speedLocked = false
                                onSetSpeed(1f)
                                flash("1.0x")
                            }
                            else -> {
                                if (!speedLocked) onSetSpeed(1f) else onSetSpeed(2f)
                            }
                        }
                        holdSpeedActive = false
                        holdDragY = 0f
                    },
                    onDragCancel = {
                        if (holdSpeedActive) {
                            if (!speedLocked) onSetSpeed(1f)
                            holdSpeedActive = false
                            holdDragY = 0f
                        }
                    },
                )
            },
    ) {
        BiliPlayerSurface(
            modifier = Modifier.fillMaxSize(),
            fit = true,
            edgeToEdge = true,
        )

        // 加载中轻指示
        if (playback.isLoading && !playback.isPlaying) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp),
                color = Color.White.copy(alpha = 0.7f),
                strokeWidth = 2.dp,
            )
        }

        // 暂停：画面正中透明玻璃播放钮（各模式通用；B 站隐藏 chrome 时也显示）
        if (!playback.isPlaying && !playback.isLoading) {
            GlassPlayPauseButton(
                isPlaying = false,
                onClick = {
                    onToggle()
                    if (gestureMode == VideoGestureMode.BILIBILI) showChrome = true
                },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        heartBursts.forEach { burst ->
            key(burst.id) {
                FloatingHeart(
                    x = burst.x,
                    y = burst.y,
                    baseScale = burst.scale,
                    delayMs = burst.delayMs,
                )
            }
        }

        AnimatedVisibility(
            visible = flashHint != null,
            enter = fadeIn(tween(80)),
            exit = fadeOut(tween(220)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Text(
                text = flashHint.orEmpty(),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        // 顶栏：返回始终可点；搜索随 chrome（B站可藏）
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "收起",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (overlayVisible) {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Default.Search, contentDescription = "搜索", tint = Color.White)
                }
            }
        }

        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 10.dp, bottom = 88.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // 抖音式：右侧最上 UP 头像，点进主页
                DouyinUpAvatar(
                    faceUrl = ownerFaceUrl.ifBlank { track?.ownerFace.orEmpty() },
                    name = track?.artist.orEmpty(),
                    onClick = onOpenUp,
                )
                DouyinSideAction(
                    icon = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    label = if (liked) "已赞" else "赞",
                    tint = if (liked) Color(0xFFFF4D6A) else Color.White,
                    onClick = onToggleLike,
                )
                DouyinSideAction(
                    icon = Icons.Default.ChatBubbleOutline,
                    label = "评论",
                    onClick = onComments,
                )
                DouyinSideAction(
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    label = "歌单",
                    onClick = onCollectLocal,
                )
                DouyinSideAction(
                    icon = Icons.Default.Share,
                    label = "转发",
                    onClick = onShare,
                )
            }
        }

        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 4.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .padding(start = 14.dp, end = 8.dp),
                ) {
                    if (!track?.title.isNullOrBlank()) {
                        Text(
                            text = track?.title.orEmpty(),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!track?.artist.isNullOrBlank()) {
                        Text(
                            text = "@${track?.artist}",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(top = 2.dp, bottom = 4.dp)
                                .clickable(onClick = onOpenUp),
                        )
                    } else {
                        Spacer(Modifier.height(4.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                ) {
                    ThinVideoProgress(
                        positionMs = playback.positionMs,
                        durationMs = playback.durationMs,
                        onSeek = onSeek,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.CenterStart)
                            .padding(end = 28.dp),
                    )
                    Icon(
                        Icons.Default.Fullscreen,
                        contentDescription = "全屏",
                        tint = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(22.dp)
                            .clickable(onClick = onFullscreen),
                    )
                }
            }
        }
    }

    if (showMoreMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMoreMenu = false },
            sheetState = moreSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
            ) {
                Text(
                    "更多",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "倍速",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                val currentSpeed = onGetSpeed()
                VIDEO_SPEED_OPTIONS.chunked(6).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        row.forEach { sp ->
                            val selected = kotlin.math.abs(currentSpeed - sp) < 0.05f
                            val label = if (sp == sp.toLong().toFloat()) {
                                "${sp.toLong()}x"
                            } else {
                                "${sp}x"
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                    .clickable {
                                        onSetSpeed(sp)
                                        speedLocked = sp != 1f
                                        flash(label)
                                        showMoreMenu = false
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                        repeat(6 - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(8.dp))
                MoreMenuRow("连播本合集（多P/系列 · 不改单集默认）") {
                    showMoreMenu = false
                    onPlaySeries()
                }
                MoreMenuRow("收藏（本地 / B站 · 可整部合集）") {
                    showMoreMenu = false
                    onCollectLocal()
                }
                MoreMenuRow("收藏到 B 站") {
                    showMoreMenu = false
                    onCollectBili()
                }
                MoreMenuRow("评论") {
                    showMoreMenu = false
                    onComments()
                }
                MoreMenuRow("分享") {
                    showMoreMenu = false
                    onShare()
                }
                MoreMenuRow("音质 · $qualityLabel") {
                    showMoreMenu = false
                    onQualityClick()
                }
                MoreMenuRow(
                    if (sleepLabel.isNullOrBlank()) "睡眠定时" else "定时 · $sleepLabel",
                ) {
                    showMoreMenu = false
                    onSleepClick()
                }
                MoreMenuRow("缓存当前") {
                    showMoreMenu = false
                    onCache()
                }
                MoreMenuRow(if (liked) "取消喜欢" else "喜欢") {
                    showMoreMenu = false
                    onToggleLike()
                }
                MoreMenuRow("不喜欢") {
                    showMoreMenu = false
                    onNotInterested()
                }
                MoreMenuRow("横屏全屏") {
                    showMoreMenu = false
                    onFullscreen()
                }
            }
        }
    }
}

@Composable
private fun MoreMenuRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}

private data class HeartBurst(
    val id: Long,
    val x: Float,
    val y: Float,
    val scale: Float,
    val delayMs: Long,
)

@Composable
private fun FloatingHeart(
    x: Float,
    y: Float,
    baseScale: Float,
    delayMs: Long,
) {
    val scale = remember { Animatable(0.2f) }
    val alpha = remember { Animatable(0f) }
    val rise = remember { Animatable(0f) }
    val rot = remember { Animatable(((-18..18).random()).toFloat()) }
    val density = LocalDensity.current

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMs)
        alpha.snapTo(1f)
        launch {
            scale.animateTo(
                baseScale * 1.2f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            )
            scale.animateTo(baseScale * 0.9f, tween(180))
        }
        launch {
            rise.animateTo(-80f, tween(700, easing = FastOutSlowInEasing))
        }
        kotlinx.coroutines.delay(420)
        alpha.animateTo(0f, tween(280))
    }

    val sizePx = with(density) { (96 * baseScale).dp.toPx() }
    Icon(
        imageVector = Icons.Filled.Favorite,
        contentDescription = null,
        tint = Color(0xFFFF2D55),
        modifier = Modifier
            .offset {
                IntOffset(
                    (x - sizePx / 2f).roundToInt(),
                    (y - sizePx / 2f + rise.value).roundToInt(),
                )
            }
            .size((96 * baseScale).dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
                rotationZ = rot.value
            },
    )
}

/** 暂停时正中：透明玻璃感播放钮 */
@Composable
private fun GlassPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 内层再加一点「玻璃」层次
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.08f),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                tint = Color.White.copy(alpha = 0.95f),
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
internal fun DouyinSideAction(
    icon: ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier
                .size(36.dp)
                .clickable(onClick = onClick),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 短视频右侧 UP 头像（抖音位：只有圆头像，无「主页」字） */
@Composable
private fun DouyinUpAvatar(
    faceUrl: String,
    name: String,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val loader = remember { MadusImageLoader.get(context) }
    val url = normalizeCoverUrl(faceUrl)
    val initial = name.trim().firstOrNull()?.toString().orEmpty().ifBlank { "UP" }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .border(1.5.dp, Color.White.copy(alpha = 0.92f), CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(160)
                    .build(),
                contentDescription = name.ifBlank { "UP主" },
                imageLoader = loader,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
            )
        } else {
            Text(
                text = initial.take(1),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun SpeedHud(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun MusicImmersiveMode(
    playback: PlaybackState,
    liked: Boolean,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleLike: () -> Unit,
    onOpenQueue: () -> Unit,
    onQualityClick: () -> Unit,
    onSleepClick: () -> Unit,
    qualityLabel: String,
    sleepLabel: String?,
    playMode: PlayModeLabel,
    onCyclePlayMode: () -> Unit,
    soundFx: SoundFx,
    onCycleSoundFx: () -> Unit,
    lyrics: com.madus.mobile.domain.LyricsUiState = com.madus.mobile.domain.LyricsUiState(),
    modifier: Modifier = Modifier,
) {
    val track = playback.current
    var dragY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val dismissPx = with(density) { 120.dp.toPx() }
    val sleepOn = !sleepLabel.isNullOrBlank()
    val qualityActive = qualityLabel != "标准"

    Column(
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(0, dragY.roundToInt().coerceAtLeast(0)) }
            .alpha((1f - (dragY / (dismissPx * 3f)).coerceIn(0f, 0.35f)))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragY > dismissPx) onBack()
                        dragY = 0f
                    },
                    onDragCancel = { dragY = 0f },
                    onVerticalDrag = { _, amount ->
                        dragY = (dragY + amount).coerceAtLeast(0f)
                    },
                )
            }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "收起", modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenQueue) {
                Icon(Icons.AutoMirrored.Outlined.QueueMusic, contentDescription = "播放列表")
            }
        }
        Spacer(Modifier.height(16.dp))
        CoverArt(
            coverUrl = track?.coverUrl,
            size = 0.dp,
            modifier = Modifier.fillMaxWidth(0.86f).aspectRatio(1f),
            shape = if (isLiquidTheme()) {
                RoundedCornerShape(liquidTokens().cornerNowPlaying)
            } else {
                null
            },
        )
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.size(48.dp))
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = track?.title ?: "未在播放",
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = track?.artist ?: "—",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onToggleLike,
                enabled = track != null,
            ) {
                Icon(
                    imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (liked) "取消喜欢" else "喜欢",
                    tint = if (liked) {
                        Color(0xFFFF4D6A)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        if (lyrics.hasVisibleLines()) {
            Spacer(Modifier.height(12.dp))
            com.madus.mobile.ui.components.LyricTwoLines(
                state = lyrics,
                positionMs = playback.positionMs,
            )
        }
        val err = playback.errorMessage?.trim().orEmpty()
        if (err.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = err,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        SeekBar(
            positionMs = playback.positionMs,
            durationMs = maxOf(playback.durationMs, track?.durationMs ?: 0L),
            onSeek = onSeek,
            enabled = track != null,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(28.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            IconButton(onClick = onPrevious, enabled = track != null) {
                Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(36.dp))
            }
            val switching = playback.isLoading && !playback.isPlaying
            IconButton(onClick = onToggle, enabled = track != null && !switching) {
                if (switching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (playback.isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(52.dp),
                    )
                }
            }
            IconButton(onClick = onNext, enabled = track != null) {
                Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(36.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ImmersiveSecondary(
                icon = if (sleepOn) Icons.Filled.Bedtime else Icons.Outlined.Bedtime,
                label = if (sleepOn) sleepLabel!! else "定时",
                active = sleepOn,
                onClick = onSleepClick,
            )
            ImmersiveSecondary(
                icon = if (qualityActive) Icons.Filled.HighQuality else Icons.Outlined.HighQuality,
                label = qualityLabel,
                active = qualityActive,
                onClick = onQualityClick,
            )
            ImmersiveSecondary(
                icon = Icons.Filled.GraphicEq,
                label = soundFx.label,
                active = soundFx != SoundFx.Flat,
                onClick = onCycleSoundFx,
            )
            ImmersiveSecondary(
                icon = when (playMode) {
                    PlayModeLabel.SHUFFLE -> Icons.Default.Shuffle
                    PlayModeLabel.SINGLE -> Icons.Default.RepeatOne
                    PlayModeLabel.LOOP -> Icons.Default.Repeat
                },
                label = playMode.label,
                active = playMode != PlayModeLabel.LOOP,
                onClick = onCyclePlayMode,
            )
        }
    }
}

@Composable
private fun ImmersiveSecondary(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(icon, label, modifier = Modifier.size(24.dp), tint = tint)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}
