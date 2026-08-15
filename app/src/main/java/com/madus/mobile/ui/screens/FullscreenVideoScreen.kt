package com.madus.mobile.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.madus.mobile.data.VIDEO_SPEED_OPTIONS
import com.madus.mobile.data.VideoGestureMode
import com.madus.mobile.domain.PlaybackState
import com.madus.mobile.ui.components.BiliPlayerSurface
import com.madus.mobile.ui.components.ThinVideoProgress
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 横屏全屏：手势随 [gestureMode]（抖音/B站/快手）。
 * 播放中控制条必须自动隐藏；B 站模式单击只显隐控件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenVideoScreen(
    playback: PlaybackState,
    liked: Boolean = false,
    qualityLabel: String = "标准",
    gestureMode: VideoGestureMode = VideoGestureMode.DOUYIN,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleLike: () -> Unit = {},
    onNotInterested: () -> Unit = {},
    onComments: () -> Unit = {},
    onShare: () -> Unit = {},
    onCollect: () -> Unit = {},
    onQualityClick: () -> Unit = {},
    onSleepClick: () -> Unit = {},
    onCache: () -> Unit = {},
    onSetSpeed: (Float) -> Unit = {},
    onGetSpeed: () -> Float = { 1f },
    onPlaySeries: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    // 进入全屏先亮一下控件，播放中靠 auto-hide 收起
    var showChrome by remember { mutableStateOf(true) }
    var chromeEpoch by remember { mutableIntStateOf(0) }
    var screenLocked by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val track = playback.current
    var dragAcc by remember { mutableFloatStateOf(0f) }

    var speedLocked by remember { mutableStateOf(false) }
    var holdMode by remember { mutableStateOf<HoldMode?>(null) }
    var holdDragY by remember { mutableFloatStateOf(0f) }
    var flashHint by remember { mutableStateOf<String?>(null) }
    var lockedSpeed by remember { mutableFloatStateOf(2f) }

    val heartScale = remember { Animatable(0f) }
    val heartAlpha = remember { Animatable(0f) }
    val heartRotate = remember { Animatable(0f) }
    var heartPos by remember { mutableStateOf(Offset.Zero) }
    var heartVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isPlaying = playback.isPlaying

    fun bumpChrome(show: Boolean = true) {
        showChrome = show
        if (show) chromeEpoch++
    }

    BackHandler(onBack = {
        if (screenLocked) {
            screenLocked = false
            bumpChrome(true)
            flashHint = "已解锁"
        } else {
            onSetSpeed(1f)
            onBack()
        }
    })

    DisposableEffect(Unit) {
        val prevOrientation = activity?.requestedOrientation
            ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val window = activity?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, view)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            onSetSpeed(1f)
            activity?.requestedOrientation = prevOrientation
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val w = activity?.window
            if (w != null) {
                WindowCompat.setDecorFitsSystemWindows(w, true)
                WindowInsetsControllerCompat(w, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // 播放中常亮；暂停后允许系统熄屏（长视频不操作也不会黑屏）
    DisposableEffect(isPlaying) {
        val window = activity?.window
        if (window != null) {
            if (isPlaying) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        onDispose {
            // 状态切换时由下一轮 effect 接管；离开页时上面 Unit effect 会清旗
        }
    }

    LaunchedEffect(holdMode) {
        if (holdMode != HoldMode.REWIND) return@LaunchedEffect
        while (isActive && holdMode == HoldMode.REWIND) {
            val pos = playback.positionMs
            onSeek((pos - 1500L).coerceAtLeast(0L))
            kotlinx.coroutines.delay(200)
        }
    }

    LaunchedEffect(flashHint) {
        if (flashHint != null) {
            kotlinx.coroutines.delay(700)
            flashHint = null
        }
    }

    // 播放中 + 控件可见 → 约 2s 自动藏（epoch 变化重置计时）
    LaunchedEffect(chromeEpoch, showChrome, isPlaying, showSettings, holdMode, screenLocked) {
        if (!showChrome) return@LaunchedEffect
        if (!isPlaying) return@LaunchedEffect
        if (showSettings || holdMode != null || screenLocked) return@LaunchedEffect
        kotlinx.coroutines.delay(2000)
        showChrome = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(track?.id, screenLocked) {
                if (screenLocked) return@pointerInput
                detectVerticalDragGestures(
                    onDragEnd = {
                        when {
                            dragAcc < -80f -> onNext()
                            dragAcc > 80f -> onPrevious()
                        }
                        dragAcc = 0f
                    },
                    onDragCancel = { dragAcc = 0f },
                    onVerticalDrag = { _, amount -> dragAcc += amount },
                )
            }
            .pointerInput(track?.id, screenLocked, gestureMode, showChrome, speedLocked, lockedSpeed) {
                if (screenLocked) {
                    detectTapGestures(onTap = { bumpChrome(!showChrome) })
                    return@pointerInput
                }
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                detectTapGestures(
                    onPress = { offset ->
                        // B站：下半屏按住 ≥350ms = 临时 2x，松手恢复（与上半菜单分工）
                        if (gestureMode == VideoGestureMode.BILIBILI && offset.y >= h * 0.5f) {
                            val releasedEarly = kotlinx.coroutines.withTimeoutOrNull(350) {
                                tryAwaitRelease()
                                true
                            }
                            if (releasedEarly != true) {
                                // 仍按住：加速
                                holdMode = HoldMode.SPEED
                                val sp = if (speedLocked) lockedSpeed else 2f
                                onSetSpeed(sp)
                                flashHint = String.format("%.1fx", sp)
                                tryAwaitRelease()
                                if (!speedLocked) onSetSpeed(1f)
                                holdMode = null
                            }
                        }
                    },
                    onDoubleTap = { offset ->
                        when (gestureMode) {
                            VideoGestureMode.BILIBILI -> {
                                // 双击 = 暂停/续播
                                onToggle()
                                bumpChrome(true)
                            }
                            else -> {
                                heartPos = offset
                                heartVisible = true
                                onToggleLike()
                                scope.launch {
                                    runHeartAnim(heartScale, heartAlpha, heartRotate) {
                                        heartVisible = false
                                    }
                                }
                            }
                        }
                    },
                    onLongPress = { offset ->
                        when (gestureMode) {
                            VideoGestureMode.KUAISHOU -> {
                                showSettings = true
                                bumpChrome(true)
                            }
                            // B站：上半屏长按 = 菜单
                            VideoGestureMode.BILIBILI -> {
                                if (offset.y < h * 0.5f) {
                                    showSettings = true
                                    bumpChrome(true)
                                }
                            }
                            // 抖音：非角区长按出菜单
                            VideoGestureMode.DOUYIN -> {
                                val cornerW = w * 0.32f
                                val cornerH = h * 0.28f
                                val inCorner =
                                    offset.y <= cornerH && (offset.x <= cornerW || offset.x >= w - cornerW)
                                if (!inCorner) {
                                    showSettings = true
                                    bumpChrome(true)
                                }
                            }
                        }
                    },
                    onTap = {
                        // ★ 关键：控件已显示时，点空白只关控件，绝不暂停
                        // 解决「点一下暂停 → 再点播放 → 控制器关不掉」
                        if (showChrome) {
                            showChrome = false
                            return@detectTapGestures
                        }
                        when (gestureMode) {
                            VideoGestureMode.BILIBILI -> bumpChrome(true)
                            VideoGestureMode.DOUYIN, VideoGestureMode.KUAISHOU -> {
                                onToggle()
                                // 不强制亮控件，避免挡画面；要控件用底部按钮或再点（B站）
                            }
                        }
                    },
                )
            }
            .pointerInput(track?.id, speedLocked, screenLocked, gestureMode, lockedSpeed) {
                if (screenLocked) return@pointerInput
                // 仅抖音：角上长按 2x / 左半回退
                if (gestureMode != VideoGestureMode.DOUYIN) return@pointerInput
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                val cornerW = w * 0.32f
                val cornerH = h * 0.28f
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        holdDragY = 0f
                        val inCorner =
                            offset.y <= cornerH && (offset.x <= cornerW || offset.x >= w - cornerW)
                        if (!inCorner) {
                            holdMode = null
                            return@detectDragGesturesAfterLongPress
                        }
                        if (offset.x < w / 2f) {
                            holdMode = HoldMode.REWIND
                            flashHint = "回退"
                            if (!speedLocked) onSetSpeed(1f)
                        } else {
                            holdMode = HoldMode.SPEED
                            val sp = if (speedLocked) lockedSpeed else 2f
                            onSetSpeed(sp)
                            flashHint = String.format("%.1fx", sp)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        if (holdMode == null) return@detectDragGesturesAfterLongPress
                        change.consume()
                        holdDragY += dragAmount.y
                    },
                    onDragEnd = {
                        when (holdMode) {
                            HoldMode.SPEED -> when {
                                holdDragY > 70f -> {
                                    speedLocked = true
                                    lockedSpeed = 2f
                                    onSetSpeed(2f)
                                    flashHint = "已锁定 2.0x"
                                }
                                holdDragY < -70f -> {
                                    speedLocked = false
                                    onSetSpeed(1f)
                                    flashHint = "1.0x"
                                }
                                else -> if (!speedLocked) onSetSpeed(1f) else onSetSpeed(lockedSpeed)
                            }
                            HoldMode.REWIND -> Unit
                            null -> Unit
                        }
                        holdMode = null
                        holdDragY = 0f
                    },
                    onDragCancel = {
                        if (holdMode == HoldMode.SPEED && !speedLocked) onSetSpeed(1f)
                        holdMode = null
                        holdDragY = 0f
                    },
                )
            },
    ) {
        BiliPlayerSurface(
            modifier = Modifier.fillMaxSize(),
            fit = true,
            edgeToEdge = true,
        )

        AnimatedVisibility(
            visible = flashHint != null || holdMode != null,
            enter = fadeIn(tween(80)),
            exit = fadeOut(tween(220)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Text(
                text = flashHint
                    ?: if (holdMode == HoldMode.REWIND) "回退" else "2.0x",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (heartVisible && heartAlpha.value > 0.02f) {
            val sizePx = with(density) { 100.dp.toPx() }
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color(0xFFFF2D55),
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (heartPos.x - sizePx / 2f).roundToInt(),
                            (heartPos.y - sizePx / 2f).roundToInt(),
                        )
                    }
                    .size(100.dp)
                    .graphicsLayer {
                        scaleX = heartScale.value
                        scaleY = heartScale.value
                        alpha = heartAlpha.value
                        rotationZ = heartRotate.value
                    },
            )
        }

        // 锁屏：仅中间解锁
        if (screenLocked) {
            AnimatedVisibility(
                visible = showChrome,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                            .clickable {
                                screenLocked = false
                                bumpChrome(true)
                                flashHint = "已解锁"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.LockOpen, "解锁", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("点击解锁", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
            }
        }

        // 未锁屏：控件仅 showChrome 时出现
        if (!screenLocked) {
            AnimatedVisibility(
                visible = showChrome,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                            ),
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        onSetSpeed(1f)
                        onBack()
                    }) {
                        Icon(Icons.Default.FullscreenExit, "退出全屏", tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track?.title.orEmpty(),
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!track?.artist.isNullOrBlank()) {
                            Text(
                                text = track?.artist.orEmpty(),
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(onClick = {
                        screenLocked = true
                        showChrome = false
                        flashHint = "已锁屏"
                    }) {
                        Icon(Icons.Default.Lock, "锁屏", tint = Color.White)
                    }
                    IconButton(onClick = {
                        showSettings = true
                        bumpChrome(true)
                    }) {
                        Icon(Icons.Default.Settings, "设置", tint = Color.White)
                    }
                }
            }

            // 暂停时中心大播放钮（不依赖控件栏；B 站也可用此暂停后恢复）
            if (!isPlaying && !playback.isLoading) {
                IconButton(
                    onClick = {
                        onToggle()
                        bumpChrome(true)
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f)),
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "播放",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = showChrome,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                            ),
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FsBottomAction(
                            icon = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            label = if (liked) "已赞" else "赞",
                            tint = if (liked) Color(0xFFFF4D6A) else Color.White,
                            onClick = {
                                onToggleLike()
                                bumpChrome(true)
                            },
                        )
                        FsBottomAction(
                            icon = Icons.Default.ChatBubbleOutline,
                            label = "评论",
                            onClick = {
                                onComments()
                                bumpChrome(true)
                            },
                        )
                        FsBottomAction(
                            icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                            label = "收藏",
                            onClick = {
                                onCollect()
                                bumpChrome(true)
                            },
                        )
                        FsBottomAction(
                            icon = Icons.Default.Share,
                            label = "转发",
                            onClick = {
                                onShare()
                                bumpChrome(true)
                            },
                        )
                        FsBottomAction(
                            icon = Icons.Default.HighQuality,
                            label = qualityLabel,
                            onClick = {
                                onQualityClick()
                                bumpChrome(true)
                            },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                onPrevious()
                                bumpChrome(true)
                            },
                            enabled = track != null,
                        ) {
                            Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        IconButton(
                            onClick = {
                                onToggle()
                                bumpChrome(true)
                            },
                            enabled = track != null,
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                        IconButton(
                            onClick = {
                                onNext()
                                bumpChrome(true)
                            },
                            enabled = track != null,
                        ) {
                            Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    ThinVideoProgress(
                        positionMs = playback.positionMs,
                        durationMs = playback.durationMs,
                        onSeek = {
                            onSeek(it)
                            bumpChrome(true)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (showSettings && !screenLocked) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
            ) {
                Text(
                    "播放设置",
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
                // 分两行芯片，避免过挤
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
                                        lockedSpeed = sp
                                        flashHint = label
                                        showSettings = false
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
                        // 补齐空位
                        repeat(6 - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(8.dp))
                SettingsRow("连播本合集（多P/系列）") {
                    showSettings = false
                    onPlaySeries()
                }
                SettingsRow("画质 / 音质 · $qualityLabel") {
                    showSettings = false
                    onQualityClick()
                }
                SettingsRow("评论") {
                    showSettings = false
                    onComments()
                }
                SettingsRow("加入歌单") {
                    showSettings = false
                    onCollect()
                }
                SettingsRow("转发分享") {
                    showSettings = false
                    onShare()
                }
                SettingsRow("睡眠定时") {
                    showSettings = false
                    onSleepClick()
                }
                SettingsRow("缓存当前") {
                    showSettings = false
                    onCache()
                }
                SettingsRow(if (liked) "取消喜欢" else "喜欢") {
                    showSettings = false
                    onToggleLike()
                }
                SettingsRow("不喜欢") {
                    showSettings = false
                    onNotInterested()
                }
                SettingsRow("锁屏") {
                    showSettings = false
                    screenLocked = true
                    showChrome = false
                }
                if (speedLocked) {
                    SettingsRow("恢复 1.0x") {
                        speedLocked = false
                        onSetSpeed(1f)
                        showSettings = false
                    }
                }
            }
        }
    }
}

private enum class HoldMode { SPEED, REWIND }

private suspend fun runHeartAnim(
    scale: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    alpha: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    rotate: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    onEnd: () -> Unit,
) {
    scale.snapTo(0.25f)
    alpha.snapTo(1f)
    rotate.snapTo(-12f)
    kotlinx.coroutines.coroutineScope {
        launch {
            scale.animateTo(
                1.15f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
            scale.animateTo(1f, tween(120))
        }
        launch {
            rotate.animateTo(6f, tween(140))
            rotate.animateTo(0f, tween(120))
        }
        kotlinx.coroutines.delay(400)
        alpha.animateTo(0f, tween(260))
        onEnd()
    }
}

@Composable
private fun SettingsRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}

@Composable
private fun FsBottomAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}
