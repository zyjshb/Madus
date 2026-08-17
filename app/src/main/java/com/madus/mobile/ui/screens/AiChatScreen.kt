package com.madus.mobile.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.madus.mobile.ai.AiChatMessage
import com.madus.mobile.ai.AiChatSessionSummary
import com.madus.mobile.ai.AiChatUiState
import com.madus.mobile.ai.SongCandidate
import com.madus.mobile.domain.Track
import com.madus.mobile.ui.components.LineButton
import com.madus.mobile.ui.components.TrackRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AiChatScreen(
    state: AiChatUiState,
    onBack: () -> Unit,
    onOpenConfig: () -> Unit,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onDismissGuide: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onCloseHistory: () -> Unit = {},
    onNewChat: () -> Unit = {},
    onOpenSession: (String) -> Unit = {},
    onDeleteSession: (String) -> Unit = {},
    onToggleRecord: () -> Unit = {},
    onCancelRecord: () -> Unit = {},
    onPickImage: (Uri) -> Unit = {},
    onPickVideo: (Uri) -> Unit = {},
    currentTrack: Track? = null,
    currentPositionMs: Long = 0L,
    onTogglePlaybackRecording: () -> Unit = {},
    onExpandModelProcess: () -> Unit = {},
    onCollapseModelProcess: () -> Unit = {},
    onPlayTrack: (Track) -> Unit,
    onCollectTrack: (Track) -> Unit = {},
    onSearchCandidate: (SongCandidate) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var modelMenu by remember { mutableStateOf(false) }
    var attachMenu by remember { mutableStateOf(false) }
    val active = state.config.active
    val caps = active?.effectiveCapabilities()
    val canUploadAudio = caps?.audioInput == true
    val canVision = caps?.vision == true
    // 视频识歌抽音轨，需要音频能力
    val canVideo = canUploadAudio
    val canHum = state.hummingReady
    val processMsg = remember(state.messages) {
        state.messages
            .filterIsInstance<AiChatMessage.Assistant>()
            .lastOrNull { it.thinking != null || it.lyricsHeard != null || it.modelRaw != null }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) onPickImage(uri)
    }
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) onPickVideo(uri)
    }
    val audioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onToggleRecord()
    }

    fun requestRecord() {
        val ok = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (ok) onToggleRecord()
        else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    val showEmptyGuide = state.showGuide && state.messages.none {
        it is AiChatMessage.User || it is AiChatMessage.Assistant
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex.coerceAtLeast(0))
        }
    }

    // enableEdgeToEdge 下需 imePadding，否则输入框被键盘盖住
    Box(
        modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "← 返回",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.clickable(onClick = onBack),
                )
                Text("AI 搜歌", style = MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "历史",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onOpenHistory),
                    )
                    Text(
                        "模型",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onOpenConfig),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        active?.name ?: "未配置模型",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        active?.modelId ?: "点右上角「模型」添加 API Key",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "新对话",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clickable(onClick = onNewChat),
                    )
                    if (state.config.profiles.size > 1) {
                        Text(
                            "切换",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clickable { modelMenu = true },
                        )
                        DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                            state.config.profiles.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.name) },
                                    onClick = {
                                        modelMenu = false
                                        onSelectProfile(p.id)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            state.status?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!state.modelProcessExpanded && processMsg != null) {
                Spacer(Modifier.height(6.dp))
                ModelProcessBar(
                    lyrics = processMsg.lyricsHeard,
                    thinking = processMsg.thinking,
                    raw = processMsg.modelRaw,
                    onExpand = onExpandModelProcess,
                )
            }

            Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f)) {
                if (showEmptyGuide) {
                    AiSearchGuideCard(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 8.dp),
                        onDismiss = onDismissGuide,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                    ) {
                        items(state.messages, key = { it.id }) { msg ->
                            when (msg) {
                                is AiChatMessage.SystemNote -> Unit
                                is AiChatMessage.User -> {
                                    Text(
                                        msg.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                                            .padding(12.dp),
                                    )
                                }
                                is AiChatMessage.Assistant -> {
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp))
                                            .padding(12.dp),
                                    ) {
                                        Text(msg.text, style = MaterialTheme.typography.bodyLarge)
                                        if (msg.candidates.isNotEmpty()) {
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                "候选 · 点一项按该歌搜 B 站",
                                                style = MaterialTheme.typography.labelLarge,
                                            )
                                            msg.candidates.forEach { c ->
                                                val conf = c.confidence?.let { " · ${(it * 100).toInt()}%" } ?: ""
                                                val label = "${c.title}${c.artist?.let { " — $it" } ?: ""}$conf"
                                                Text(
                                                    label,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable(enabled = !state.isSending) {
                                                            onSearchCandidate(c)
                                                        }
                                                        .padding(vertical = 6.dp),
                                                )
                                                c.note?.let {
                                                    Text(
                                                        "  $it",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                        if (msg.tracks.isNotEmpty()) {
                                            Spacer(Modifier.height(10.dp))
                                            Text("B 站结果 · 点按试听/播放", style = MaterialTheme.typography.labelLarge)
                                            Spacer(Modifier.height(6.dp))
                                            msg.tracks.forEach { track ->
                                                TrackRow(
                                                    track = track,
                                                    onClick = { onPlayTrack(track) },
                                                    onCollect = { onCollectTrack(track) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 附件面板（展开时）
            if (attachMenu && !state.isRecording) {
                AttachPanel(
                    canHum = canHum,
                    canVision = canVision,
                    canVideo = canVideo,
                    onRecord = {
                        attachMenu = false
                        requestRecord()
                    },
                    onImage = {
                        attachMenu = false
                        imagePicker.launch("image/*")
                    },
                    onVideo = {
                        attachMenu = false
                        videoPicker.launch("video/*")
                    },
                    onDismiss = { attachMenu = false },
                )
                Spacer(Modifier.height(8.dp))
            }

            // —— 底部输入区 ——
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                if (state.isRecording) {
                    RecordingWavePanel(
                        elapsedMs = state.recordingElapsedMs,
                        waveform = state.waveform,
                        micLevel = state.micLevel,
                        onCancel = onCancelRecord,
                        onStop = onToggleRecord,
                    )
                } else {
                    BasicTextField(
                        value = state.input,
                        onValueChange = onInputChange,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSend() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        decorationBox = { inner ->
                            if (state.input.isEmpty()) {
                                Text(
                                    "描述歌词，或点 + 添加哼唱/图片…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (attachMenu) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    },
                                )
                                .clickable(enabled = !state.isSending) {
                                    attachMenu = !attachMenu
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (attachMenu) Icons.Outlined.Close else Icons.Outlined.Add,
                                contentDescription = "添加附件",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (state.lastRecordingPath != null) {
                            Text(
                                if (state.isPlayingRecording) "停止" else "回听",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                                    .clickable(onClick = onTogglePlaybackRecording)
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (!state.isSending && state.input.isNotBlank() && active != null) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                )
                                .clickable(
                                    enabled = !state.isSending && state.input.isNotBlank() && active != null,
                                    onClick = onSend,
                                )
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                        ) {
                            Text(
                                if (state.isSending) "…" else "发送",
                                color = if (!state.isSending && state.input.isNotBlank() && active != null) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            if (active != null && !canVision && !canVideo) {
                Text(
                    "图片/视频识歌需要对应能力的模型；哼唱走讯飞/ACRCloud 识别。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        if (state.showHistoryPanel) {
            HistoryPanel(
                items = state.history,
                currentId = state.sessionId,
                onClose = onCloseHistory,
                onOpen = onOpenSession,
                onDelete = onDeleteSession,
                onNew = {
                    onNewChat()
                    onCloseHistory()
                },
            )
        }
        if (state.modelProcessExpanded && processMsg != null) {
            ModelProcessFullScreen(
                lyrics = processMsg.lyricsHeard,
                thinking = processMsg.thinking,
                raw = processMsg.modelRaw,
                onCollapse = onCollapseModelProcess,
            )
        }
    }
}

@Composable
private fun AttachPanel(
    canHum: Boolean,
    canVision: Boolean,
    canVideo: Boolean,
    onRecord: () -> Unit,
    onImage: () -> Unit,
    onVideo: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("添加内容", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "关闭",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onDismiss),
            )
        }
        Spacer(Modifier.height(10.dp))
        AttachCard(
            icon = Icons.Outlined.Mic,
            iconBg = Color(0xFF2D6A4F).copy(alpha = 0.18f),
            iconTint = Color(0xFF2D6A4F),
            title = "哼唱 / 录音",
            subtitle = if (canHum) "最长 15 秒" else "未配置",
            onClick = onRecord,
        )
        Spacer(Modifier.height(8.dp))
        if (canVideo) {
            AttachCard(
                icon = Icons.Outlined.Videocam,
                iconBg = Color(0xFF6B3FA0).copy(alpha = 0.15f),
                iconTint = Color(0xFF6B3FA0),
                title = "视频识歌",
                subtitle = "上传视频",
                onClick = onVideo,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (canVision) {
            AttachCard(
                icon = Icons.Outlined.Image,
                iconBg = Color(0xFF1D4E89).copy(alpha = 0.15f),
                iconTint = Color(0xFF1D4E89),
                title = "图片",
                subtitle = "截图",
                onClick = onImage,
            )
        }
    }
}

@Composable
private fun AttachCard(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 录音声浪 + 计时 + 停止 */
@Composable
private fun RecordingWavePanel(
    elapsedMs: Long,
    waveform: List<Float>,
    micLevel: Float,
    onCancel: () -> Unit,
    onStop: () -> Unit,
) {
    val sec = (elapsedMs / 1000).coerceAtMost(15)
    val remain = (15 - sec).coerceAtLeast(0)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "录音 $sec″ · 还剩 ${remain}″",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                "取消",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onCancel),
            )
        }
        Spacer(Modifier.height(12.dp))
        // 声浪条
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val bars = if (waveform.isEmpty()) List(28) { 0.15f } else waveform
            bars.forEach { h ->
                val heightFrac = h.coerceIn(0.12f, 1f)
                // 用固定高度比例，避免 fillMaxHeight 在部分机型上异常
                Box(
                    Modifier
                        .weight(1f)
                        .height((44f * heightFrac).dp.coerceAtLeast(4.dp))
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            MaterialTheme.colorScheme.error.copy(
                                alpha = (0.35f + micLevel * 0.55f).coerceIn(0.35f, 0.95f),
                            ),
                        ),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (micLevel < 0.08f) "音量很低 · 靠近麦克风或大声一点"
            else if (micLevel > 0.75f) "音量很大"
            else "正在拾音",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.error)
                .clickable(onClick = onStop)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Stop, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "停止并发送",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ModelProcessBar(
    lyrics: String?,
    thinking: String?,
    raw: String?,
    onExpand: () -> Unit,
) {
    val summary = remember(lyrics, thinking, raw) {
        buildString {
            if (!lyrics.isNullOrBlank()) {
                append("听到：${lyrics.replace(Regex("\\s+"), " ").take(50)}")
            }
            if (!thinking.isNullOrBlank()) {
                if (isNotEmpty()) append(" · ")
                append("思考：${thinking.replace(Regex("\\s+"), " ").take(60)}")
            } else if (!raw.isNullOrBlank()) {
                if (isNotEmpty()) append(" · ")
                append("输出：${raw.replace(Regex("\\s+"), " ").take(50)}")
            }
        }.ifBlank { "点击展开" }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            .clickable(onClick = onExpand)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "模型过程",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            "展开",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ModelProcessFullScreen(
    lyrics: String?,
    thinking: String?,
    raw: String?,
    onCollapse: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = {}),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("模型过程", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "收起",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onCollapse),
                )
            }
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                ModelProcessLabel("听到的歌词 / 旋律")
                Text(
                    lyrics?.trim() ?: "未转写出歌词",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(18.dp))
                ModelProcessLabel("模型思考")
                Text(
                    thinking?.trim() ?: "模型未返回思考过程",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(18.dp))
                ModelProcessLabel("模型原始输出")
                Text(
                    raw?.trim() ?: "无原始输出",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ModelProcessLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun HistoryPanel(
    items: List<AiChatSessionSummary>,
    currentId: String?,
    onClose: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNew: () -> Unit,
) {
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClose),
    ) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxSize(0.88f)
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = false, onClick = {})
                .padding(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("历史对话", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "关闭",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onClose),
                )
            }
            Spacer(Modifier.height(8.dp))
            LineButton(text = "开新对话", onClick = onNew, filled = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            if (items.isEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "还没有历史",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(items, key = { it.id }) { item ->
                        val selected = item.id == currentId
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .border(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(2.dp),
                                )
                                .clickable { onOpen(item.id) }
                                .padding(12.dp),
                        ) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                item.preview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    fmt.format(Date(item.updatedAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "删除",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.clickable { onDelete(item.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiSearchGuideCard(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1A1A1A))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.MusicNote,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "想不起歌名？",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "随便打歌词就行，不用学提示词",
            color = Color.White.copy(alpha = 0.55f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        GuideTipLine("文字：歌词/谐音随便打")
        GuideTipLine("哼唱：讯飞/ACRCloud，点 + → 录（最长 15 秒）")
        GuideTipLine("视频：点 + → 视频识歌，上传本地片抽音轨")
        GuideTipLine("截图：支持识图的模型点「图」")
        GuideTipLine("流程：猜歌 → 全网 → B 站试听")
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .clickable(onClick = onDismiss)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("我知道了", color = Color.Black, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun GuideTipLine(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("·  ", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        Text(
            text,
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
    }
}
