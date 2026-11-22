package com.madus.mobile.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.madus.mobile.data.AppearanceMode
import com.madus.mobile.domain.MusicSourceType
import com.madus.mobile.domain.Track
import com.madus.mobile.together.DEFAULT_TOGETHER_SERVER_URL
import com.madus.mobile.together.TogetherChatMessage
import com.madus.mobile.together.TogetherConnectionState
import com.madus.mobile.together.TogetherControlMode
import com.madus.mobile.together.TogetherMember
import com.madus.mobile.together.TogetherUiState
import com.madus.mobile.ui.components.BiliPlayerSurface
import com.madus.mobile.ui.components.CoverArt
import com.madus.mobile.ui.components.LineButton
import com.madus.mobile.ui.components.LineFrame
import com.madus.mobile.ui.liquid.CanvasKicker
import com.madus.mobile.ui.liquid.GlassSurface
import com.madus.mobile.ui.liquid.LocalLiquidChromeBottom
import com.madus.mobile.ui.theme.LiquidType
import com.madus.mobile.ui.theme.appearanceTokens
import com.madus.mobile.ui.theme.iosClickable
import com.madus.mobile.ui.theme.isLiquidTheme
import com.madus.mobile.ui.theme.liquidTokens
import kotlin.random.Random

@Composable
fun TogetherScreen(
    state: TogetherUiState,
    defaultNickname: String,
    currentSoloTrack: Track?,
    recentTracks: List<Track>,
    onMinimizeRoom: () -> Unit,
    onJoinRoom: (roomId: String, nickname: String, password: String?, initialTrack: Track?) -> Unit,
    onLeaveRoom: () -> Unit,
    onUpdateServerUrl: (String) -> Unit,
    onPickTrack: (Track) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekToMs: (Long) -> Unit,
    onToggleVideoExpanded: () -> Unit,
    onToggleControlMode: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleVolumeDrawer: () -> Unit,
    onToggleDanmaku: () -> Unit,
    onSetMediaVolume: (Float) -> Unit,
    onSetVoiceGain: (Float) -> Unit,
    onSendChat: (String, Boolean) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isJoined) {
        TogetherLobby(
            state = state,
            defaultNickname = defaultNickname,
            currentSoloTrack = currentSoloTrack,
            onJoinRoom = onJoinRoom,
            onUpdateServerUrl = onUpdateServerUrl,
            onClearError = onClearError,
            modifier = modifier,
        )
    } else {
        TogetherRoomStage(
            state = state,
            recentTracks = recentTracks,
            onMinimizeRoom = onMinimizeRoom,
            onLeaveRoom = onLeaveRoom,
            onPickTrack = onPickTrack,
            onTogglePlayPause = onTogglePlayPause,
            onSeekToMs = onSeekToMs,
            onToggleVideoExpanded = onToggleVideoExpanded,
            onToggleControlMode = onToggleControlMode,
            onToggleMic = onToggleMic,
            onToggleSpeaker = onToggleSpeaker,
            onToggleVolumeDrawer = onToggleVolumeDrawer,
            onToggleDanmaku = onToggleDanmaku,
            onSetMediaVolume = onSetMediaVolume,
            onSetVoiceGain = onSetVoiceGain,
            onSendChat = onSendChat,
            onClearError = onClearError,
            modifier = modifier,
        )
    }
}

@Composable
private fun TogetherLobby(
    state: TogetherUiState,
    defaultNickname: String,
    currentSoloTrack: Track?,
    onJoinRoom: (roomId: String, nickname: String, password: String?, initialTrack: Track?) -> Unit,
    onUpdateServerUrl: (String) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val liquid = isLiquidTheme()
    val tokens = appearanceTokens()
    val bottomPad = if (liquid) LocalLiquidChromeBottom.current + 12.dp else 20.dp

    var roomId by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf(state.selfNickname.ifBlank { defaultNickname }) }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var carryCurrentTrack by rememberSaveable { mutableStateOf(true) }
    var showServerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.selfNickname, defaultNickname) {
        if (nickname.isBlank()) {
            nickname = state.selfNickname.ifBlank { defaultNickname }.ifBlank { "听歌朋友" }
        }
    }

    val eligibleInitial = currentSoloTrack?.takeIf {
        it.source == MusicSourceType.BILIBILI && it.bvid.isNotBlank()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = if (liquid) 22.dp else 16.dp,
                end = if (liquid) 22.dp else 16.dp,
                top = 18.dp,
                bottom = bottomPad,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (liquid) {
                    CanvasKicker("B 站同步 · 开麦对话")
                    Spacer(Modifier.height(4.dp))
                    Text("一起听", style = LiquidType.largeTitle, color = MaterialTheme.colorScheme.onSurface)
                } else {
                    Text(
                        text = "一起听",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "B 站同步听歌 · 可展开视频 · 实时开麦聊天",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { showServerDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "同步服务器设置",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val alertMsg = state.roomClosedReason ?: state.errorBanner
        if (!alertMsg.isNullOrBlank()) {
            TogetherAdaptiveCard(
                onClick = onClearError,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = alertMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭提示",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        TogetherAdaptiveCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "加入或创建房间",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // 房间号 + 随机生成
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MadusInputBox(
                        value = roomId,
                        onValueChange = { roomId = it },
                        placeholder = "房间号（如 listen01）",
                        modifier = Modifier.weight(1f),
                    )
                    TogetherSmallChip(
                        label = "随机",
                        icon = Icons.Default.Refresh,
                        onClick = { roomId = generateRoomCode() },
                    )
                }

                // 昵称
                MadusInputBox(
                    value = nickname,
                    onValueChange = { nickname = it },
                    placeholder = "你的昵称（默认使用 B 站昵称）",
                    modifier = Modifier.fillMaxWidth(),
                )

                // 可选密码折叠
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPassword = !showPassword }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (showPassword) "收起房间密码" else "设置 / 输入房间密码（可选）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (showPassword) {
                    MadusInputBox(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = "留空则公开无密码",
                        isPassword = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (eligibleInitial != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(tokens.cornerSm))
                            .border(
                                tokens.borderWidth,
                                if (carryCurrentTrack) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                },
                                RoundedCornerShape(tokens.cornerSm),
                            )
                            .background(
                                if (carryCurrentTrack) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                } else {
                                    Color.Transparent
                                },
                            )
                            .iosClickable { carryCurrentTrack = !carryCurrentTrack }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (carryCurrentTrack) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (carryCurrentTrack) {
                                "开房时同步播放：《${eligibleInitial.title}》"
                            } else {
                                "空房进入，进去后再选歌"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                LineButton(
                    text = if (state.isJoining) "正在进入房间…" else "创建 / 加入房间",
                    filled = true,
                    enabled = !state.isJoining,
                    onClick = {
                        val finalId = roomId.trim().ifBlank {
                            generateRoomCode().also { roomId = it }
                        }
                        val finalNick = nickname.trim().ifBlank { "听歌朋友" }
                        onJoinRoom(
                            finalId,
                            finalNick,
                            password.takeIf { it.isNotBlank() },
                            if (carryCurrentTrack) eligibleInitial else null,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "当前节点：${state.serverUrl.removePrefix("http://").removePrefix("https://")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.recentRooms.isNotEmpty()) {
            Text(
                text = "最近去过的房间",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.recentRooms.forEach { entry ->
                    TogetherAdaptiveCard(
                        onClick = {
                            roomId = entry.roomId
                            if (entry.nickname.isNotBlank()) nickname = entry.nickname
                            onJoinRoom(
                                entry.roomId,
                                nickname.ifBlank { entry.nickname }.ifBlank { "听歌朋友" },
                                password.takeIf { it.isNotBlank() },
                                null,
                            )
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "房间 ${entry.roomId}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (entry.nickname.isNotBlank()) {
                                    Text(
                                        text = "使用昵称：${entry.nickname}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(
                                text = "进入",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showServerDialog) {
        ServerConfigDialog(
            currentUrl = state.serverUrl,
            onSave = {
                onUpdateServerUrl(it)
                showServerDialog = false
            },
            onDismiss = { showServerDialog = false },
        )
    }
}

@Composable
private fun TogetherRoomStage(
    state: TogetherUiState,
    recentTracks: List<Track>,
    onMinimizeRoom: () -> Unit,
    onLeaveRoom: () -> Unit,
    onPickTrack: (Track) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekToMs: (Long) -> Unit,
    onToggleVideoExpanded: () -> Unit,
    onToggleControlMode: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleVolumeDrawer: () -> Unit,
    onToggleDanmaku: () -> Unit,
    onSetMediaVolume: (Float) -> Unit,
    onSetVoiceGain: (Float) -> Unit,
    onSendChat: (String, Boolean) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val liquid = isLiquidTheme()
    val tokens = appearanceTokens()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var showSongSheet by remember { mutableStateOf(false) }
    var chatDraft by remember { mutableStateOf("") }
    var sendAsDanmaku by remember { mutableStateOf(false) }
    var copiedTip by remember { mutableStateOf(false) }
    var fullscreenVideo by remember { mutableStateOf(false) }

    // 拖动进度条本地态
    var draggingSlider by remember { mutableStateOf(false) }
    var sliderPosFraction by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(state.chatMessages.size) {
        if (state.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(state.chatMessages.lastIndex)
        }
    }

    LaunchedEffect(copiedTip) {
        if (copiedTip) {
            kotlinx.coroutines.delay(1500)
            copiedTip = false
        }
    }

    if (fullscreenVideo && state.videoExpanded) {
        TogetherFullscreenVideoOverlay(
            state = state,
            onExitFullscreen = { fullscreenVideo = false },
            onTogglePlayPause = onTogglePlayPause,
            onSeekToMs = onSeekToMs,
            onToggleMic = onToggleMic,
            onToggleDanmaku = onToggleDanmaku,
        )
        return
    }

    val hPad = if (liquid) 18.dp else 14.dp

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = hPad, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 1. 顶部房间栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onMinimizeRoom,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "收起房间继续逛",
                )
            }
            Spacer(Modifier.width(4.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(tokens.cornerSm))
                    .border(
                        tokens.borderWidth,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        RoundedCornerShape(tokens.cornerSm),
                    )
                    .clickable {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("roomId", state.roomId))
                        copiedTip = true
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (copiedTip) "已复制" else state.roomId,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "复制房间号",
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = when {
                    state.bufferingMembers.isNotEmpty() -> "等待缓冲…"
                    state.connectionState == TogetherConnectionState.CONNECTED && state.rttMs != null ->
                        "同步 ${state.rttMs}ms"
                    else -> state.connectionState.label
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (state.isHost) {
                TogetherSmallChip(
                    label = if (state.controlMode == TogetherControlMode.HOST_ONLY) "仅房主" else "全员点歌",
                    onClick = onToggleControlMode,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = "退房",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .iosClickable(onClick = onLeaveRoom)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }

        // 非阻断式内联错误提示
        AnimatedVisibility(visible = !state.errorBanner.isNullOrBlank()) {
            TogetherAdaptiveCard(onClick = onClearError) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = state.errorBanner.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }

        // 2. 舞台区（默认简约唱片卡片，一键展开 16:9 B 站视频画面）
        val track = state.playback.track
        val durMs = state.localDurationMs.coerceAtLeast(0L)
        val posMs = if (draggingSlider && durMs > 0L) {
            (sliderPosFraction * durMs).toLong()
        } else {
            state.localPositionMs.coerceAtLeast(0L)
        }
        val progress = if (durMs > 0L) (posMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f) else 0f

        TogetherAdaptiveCard {
            if (!state.videoExpanded) {
                // —— 听歌模式主舞台 ——
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(tokens.cornerSm))
                                .border(
                                    width = if (state.localIsPlaying) 1.5.dp else tokens.borderWidth,
                                    color = if (state.localIsPlaying) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                    },
                                    shape = RoundedCornerShape(tokens.cornerSm),
                                ),
                        ) {
                            CoverArt(coverUrl = track?.coverUrl, size = 64.dp)
                            if (state.localIsLoading) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.45f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = track?.title ?: state.playback.title ?: "暂无播放内容",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = track?.artist?.takeIf { it.isNotBlank() }
                                    ?: if (state.canControl) "点「选歌」挑一首 B 站音乐或视频" else "等待房主点播 B 站歌曲",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        if (!state.playback.videoUrl.isNullOrBlank()) {
                            TogetherSmallChip(
                                label = "看视频",
                                icon = Icons.Default.Videocam,
                                onClick = onToggleVideoExpanded,
                            )
                        }
                    }

                    if (!state.playback.videoUrl.isNullOrBlank()) {
                        Column {
                            Slider(
                                value = if (draggingSlider) sliderPosFraction else progress,
                                onValueChange = { frac ->
                                    if (!state.canControl || durMs <= 0L) return@Slider
                                    draggingSlider = true
                                    sliderPosFraction = frac
                                },
                                onValueChangeFinished = {
                                    if (draggingSlider && durMs > 0L) {
                                        onSeekToMs((sliderPosFraction * durMs).toLong())
                                    }
                                    draggingSlider = false
                                },
                                enabled = state.canControl && durMs > 0L,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${formatMillis(posMs)} / ${formatMillis(durMs)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (state.canControl) {
                                        TogetherSmallChip(
                                            label = if (state.playback.isPlaying) "暂停" else "播放",
                                            icon = if (state.playback.isPlaying) {
                                                Icons.Default.Pause
                                            } else {
                                                Icons.Default.PlayArrow
                                            },
                                            active = state.playback.isPlaying,
                                            onClick = onTogglePlayPause,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // —— 展开视频模式 ——
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(tokens.cornerSm))
                            .background(Color.Black),
                    ) {
                        BiliPlayerSurface(fit = true, modifier = Modifier.fillMaxSize())
                        if (state.danmakuEnabled) {
                            TogetherDanmakuPreview(
                                messages = state.chatMessages,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        if (state.localIsLoading) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                            }
                        }
                        // 顶角快捷操作
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            VideoOverlayBadge(
                                text = if (state.danmakuEnabled) "弹幕开" else "弹幕关",
                                onClick = onToggleDanmaku,
                            )
                            VideoOverlayBadge(
                                text = "回听歌",
                                onClick = onToggleVideoExpanded,
                            )
                            VideoOverlayBadge(
                                icon = Icons.Default.Fullscreen,
                                onClick = { fullscreenVideo = true },
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.canControl) {
                            IconButton(
                                onClick = onTogglePlayPause,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = if (state.playback.isPlaying) {
                                        Icons.Default.Pause
                                    } else {
                                        Icons.Default.PlayArrow
                                    },
                                    contentDescription = if (state.playback.isPlaying) "暂停" else "播放",
                                )
                            }
                        }
                        Slider(
                            value = if (draggingSlider) sliderPosFraction else progress,
                            onValueChange = { frac ->
                                if (!state.canControl || durMs <= 0L) return@Slider
                                draggingSlider = true
                                sliderPosFraction = frac
                            },
                            onValueChangeFinished = {
                                if (draggingSlider && durMs > 0L) {
                                    onSeekToMs((sliderPosFraction * durMs).toLong())
                                }
                                draggingSlider = false
                            },
                            enabled = state.canControl && durMs > 0L,
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = formatMillis(posMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 3. 成员麦位横条
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.members, key = { it.userId }) { member ->
                MemberVoiceChip(
                    member = member,
                    isSelf = member.userId == state.selfUserId,
                )
            }
        }

        // 4. 麦克风 / 扬声器 / 音量折叠 / 点歌 工具条
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TogetherSmallChip(
                label = if (state.micEnabled) "开麦中" else "已闭麦",
                icon = if (state.micEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                active = state.micEnabled,
                onClick = onToggleMic,
                modifier = Modifier.weight(1f),
            )
            TogetherSmallChip(
                label = if (state.speakerOn) "外放" else "听筒",
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                active = state.speakerOn,
                onClick = onToggleSpeaker,
            )
            TogetherSmallChip(
                label = "音量",
                icon = Icons.Default.Tune,
                active = state.showVolumeDrawer,
                onClick = onToggleVolumeDrawer,
            )
            TogetherSmallChip(
                label = "点歌",
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                active = state.canControl,
                onClick = { showSongSheet = true },
            )
        }

        // 可展开 / 隐藏的音量独立调节条（不遮挡聊天输入框）
        AnimatedVisibility(
            visible = state.showVolumeDrawer,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            TogetherAdaptiveCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "音乐 ${(state.mediaVolume * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(82.dp),
                        )
                        Slider(
                            value = state.mediaVolume,
                            onValueChange = onSetMediaVolume,
                            valueRange = 0f..1f,
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "人声 ${(state.voiceGain * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(82.dp),
                        )
                        Slider(
                            value = state.voiceGain,
                            onValueChange = onSetVoiceGain,
                            valueRange = 0f..2f,
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp),
                        )
                    }
                }
            }
        }

        // 5. 聊天对话流
        TogetherAdaptiveCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = 10.dp,
        ) {
            if (state.chatMessages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "房间已就绪，可以开麦聊天或发文字消息",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(state.chatMessages, key = { it.id }) { msg ->
                        ChatBubbleRow(
                            msg = msg,
                            isSelf = msg.userId == state.selfUserId,
                        )
                    }
                }
            }
        }

        // 6. 底部输入栏
        val inputShape = RoundedCornerShape(tokens.cornerSm)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(inputShape)
                .border(
                    tokens.borderWidth,
                    MaterialTheme.colorScheme.outline.copy(
                        alpha = if (tokens.mode == AppearanceMode.SoftGlass) 0.25f else 0.85f,
                    ),
                    inputShape,
                )
                .background(
                    if (liquid) {
                        liquidTokens().glassFill
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.videoExpanded) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(tokens.cornerXs))
                        .background(
                            if (sendAsDanmaku) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                        )
                        .clickable { sendAsDanmaku = !sendAsDanmaku }
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "弹幕",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (sendAsDanmaku) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Spacer(Modifier.width(8.dp))
            }

            BasicTextField(
                value = chatDraft,
                onValueChange = { chatDraft = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        val txt = chatDraft.trim()
                        if (txt.isNotEmpty()) {
                            onSendChat(txt, sendAsDanmaku && state.videoExpanded)
                            chatDraft = ""
                        }
                    },
                ),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (chatDraft.isEmpty()) {
                        Text(
                            text = if (state.videoExpanded && sendAsDanmaku) "发一条弹幕聊天…" else "聊几句…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
            IconButton(
                onClick = {
                    val txt = chatDraft.trim()
                    if (txt.isNotEmpty()) {
                        onSendChat(txt, sendAsDanmaku && state.videoExpanded)
                        chatDraft = ""
                    }
                },
                enabled = chatDraft.isNotBlank(),
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    modifier = Modifier.size(18.dp),
                    tint = if (chatDraft.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    },
                )
            }
        }
    }

    if (showSongSheet) {
        TogetherPickSongSheet(
            recentTracks = recentTracks,
            onPick = onPickTrack,
            onDismiss = { showSongSheet = false },
        )
    }
}

@Composable
private fun TogetherFullscreenVideoOverlay(
    state: TogetherUiState,
    onExitFullscreen: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekToMs: (Long) -> Unit,
    onToggleMic: () -> Unit,
    onToggleDanmaku: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity
    var showControls by remember { mutableStateOf(true) }

    BackHandler { onExitFullscreen() }

    DisposableEffect(activity) {
        val window = activity?.window
        val prevOri = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity?.requestedOrientation = prevOri
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.getInsetsController(window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val durMs = state.localDurationMs.coerceAtLeast(0L)
    val posMs = state.localPositionMs.coerceAtLeast(0L)
    val progress = if (durMs > 0L) (posMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f) else 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { showControls = !showControls },
    ) {
        BiliPlayerSurface(fit = true, modifier = Modifier.fillMaxSize())
        if (state.danmakuEnabled) {
            TogetherDanmakuPreview(messages = state.chatMessages, modifier = Modifier.fillMaxSize())
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onExitFullscreen) {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = "退出全屏",
                            tint = Color.White,
                        )
                    }
                    Text(
                        text = state.playback.track?.title ?: state.playback.title.orEmpty(),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    VideoOverlayBadge(
                        text = if (state.danmakuEnabled) "弹幕开" else "弹幕关",
                        onClick = onToggleDanmaku,
                    )
                    Spacer(Modifier.width(8.dp))
                    VideoOverlayBadge(
                        text = if (state.micEnabled) "开麦中" else "已闭麦",
                        onClick = onToggleMic,
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.canControl) {
                        IconButton(onClick = onTogglePlayPause) {
                            Icon(
                                imageVector = if (state.playback.isPlaying) {
                                    Icons.Default.Pause
                                } else {
                                    Icons.Default.PlayArrow
                                },
                                contentDescription = null,
                                tint = Color.White,
                            )
                        }
                    }
                    Slider(
                        value = progress,
                        onValueChange = { if (state.canControl && durMs > 0L) onSeekToMs((it * durMs).toLong()) },
                        enabled = state.canControl && durMs > 0L,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${formatMillis(posMs)} / ${formatMillis(durMs)}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun TogetherDanmakuPreview(
    messages: List<TogetherChatMessage>,
    modifier: Modifier = Modifier,
) {
    val recentDanmaku = remember(messages) {
        messages.filter { !it.isSystem && it.sendAsDanmaku }.takeLast(4)
    }
    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        recentDanmaku.forEach { msg ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "${msg.nickname}: ${msg.content}",
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MemberVoiceChip(
    member: TogetherMember,
    isSelf: Boolean,
) {
    val tokens = appearanceTokens()
    val speaking = member.isSpeaking && !member.isMuted
    val shape = RoundedCornerShape(tokens.cornerSm)
    Row(
        modifier = Modifier
            .clip(shape)
            .border(
                width = if (speaking) 1.5.dp else tokens.borderWidth,
                color = when {
                    speaking -> MaterialTheme.colorScheme.primary
                    isSelf -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                },
                shape = shape,
            )
            .background(
                if (speaking) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                },
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(
                    if (speaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when {
                    speaking -> Icons.Default.GraphicEq
                    member.isMuted -> Icons.Default.MicOff
                    else -> Icons.Default.Mic
                },
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = if (speaking) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = buildString {
                if (member.isHost) append("房主·")
                append(member.nickname)
                if (isSelf) append("(我)")
                if (member.isAway) append(" 暂离")
                if (member.isBuffering) append(" 缓冲")
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun ChatBubbleRow(
    msg: TogetherChatMessage,
    isSelf: Boolean,
) {
    val tokens = appearanceTokens()
    if (msg.isSystem) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = msg.content,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = "${msg.nickname} · ${formatMillis((msg.videoTimestamp * 1000).toLong())}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        val shape = RoundedCornerShape(tokens.cornerSm)
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .border(
                    tokens.borderWidth,
                    if (isSelf) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    },
                    shape,
                )
                .background(
                    if (isSelf) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    },
                )
                .padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
            Text(
                text = msg.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TogetherAdaptiveCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: androidx.compose.ui.unit.Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (isLiquidTheme()) {
        GlassSurface(
            modifier = modifier.fillMaxWidth(),
            onClick = onClick,
            contentPadding = contentPadding,
        ) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    } else {
        LineFrame(
            modifier = modifier.fillMaxWidth(),
            onClick = onClick,
            contentPadding = contentPadding,
        ) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun TogetherSmallChip(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val tokens = appearanceTokens()
    val shape = RoundedCornerShape(tokens.cornerSm)
    Row(
        modifier = modifier
            .clip(shape)
            .border(
                tokens.borderWidth,
                if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                },
                shape,
            )
            .background(
                if (active) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                },
            )
            .iosClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun VideoOverlayBadge(
    text: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.58f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        } else if (text != null) {
            Text(text, color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MadusInputBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
) {
    val tokens = appearanceTokens()
    val shape = RoundedCornerShape(tokens.cornerSm)
    Box(
        modifier = modifier
            .clip(shape)
            .border(
                tokens.borderWidth,
                MaterialTheme.colorScheme.outline.copy(
                    alpha = if (tokens.mode == AppearanceMode.SoftGlass) 0.25f else 0.75f,
                ),
                shape,
            )
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            },
        )
    }
}

@Composable
private fun ServerConfigDialog(
    currentUrl: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(currentUrl) { mutableStateOf(currentUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("同步服务器地址") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "默认连接你的 SyncCinema 同步与语音中继服务器：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MadusInputBox(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = DEFAULT_TOGETHER_SERVER_URL,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) { Text("保存") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { draft = DEFAULT_TOGETHER_SERVER_URL }) { Text("恢复默认") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

private fun generateRoomCode(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return buildString {
        repeat(6) {
            append(chars[Random.nextInt(chars.length)])
        }
    }
}

private fun formatMillis(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%02d:%02d".format(m, s)
    }
}
