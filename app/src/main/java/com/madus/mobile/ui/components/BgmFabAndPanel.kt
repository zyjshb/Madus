package com.madus.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.madus.mobile.ai.LlmProfile
import com.madus.mobile.ai.SongCandidate
import com.madus.mobile.domain.Track
import com.madus.mobile.ui.BgmUiState
import kotlin.math.roundToInt

/**
 * 识曲悬浮球：固定圆形球，贴左侧，可上下拖。
 * 点一下开始识别 / 打开结果面板（逻辑在 onClick）。
 */
@Composable
fun BgmSideFab(
    state: BgmUiState,
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val density = LocalDensity.current
    var offsetY by remember { mutableFloatStateOf(0f) }

    val dragRangePx = with(density) { 320.dp.toPx() }
    val dragMod = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures { _, dragAmount ->
            offsetY = (offsetY + dragAmount).coerceIn(-dragRangePx, dragRangePx)
        }
    }

    val shape = RoundedCornerShape(50)
    val label = when {
        state.loading -> "识别"
        state.hasContent -> "BGM"
        else -> "识曲"
    }

    Box(
        modifier = modifier
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .zIndex(6f)
            .then(dragMod)
            .size(56.dp)
            .clip(shape)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                state.loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                else -> {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = "识别 BGM",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * 线稿结果面板：封面列表 + 模型切换。
 */
@Composable
fun BgmResultPanel(
    state: BgmUiState,
    audioProfiles: List<LlmProfile>,
    selectedProfileId: String?,
    onDismiss: () -> Unit,
    onPlayNow: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onCollect: (Track) -> Unit,
    onReidentify: () -> Unit,
    onSelectModel: (String) -> Unit,
    onTogglePreferForeign: () -> Unit = {},
    onCancelIdentify: () -> Unit = {},
    onStartIdentify: () -> Unit = onReidentify,
) {
    if (!state.visible) return

    val ink = MaterialTheme.colorScheme.onSurface
    val paper = MaterialTheme.colorScheme.surface
    val line = MaterialTheme.colorScheme.outline
    val panelShape = RoundedCornerShape(2.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(8f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ink.copy(alpha = 0.18f))
                .clickable(onClick = onDismiss),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.78f)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .clip(panelShape)
                .border(1.dp, line, panelShape)
                .background(paper)
                .clickable(enabled = false, onClick = {})
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "本片 BGM",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ink,
                    )
                    state.sourceTitle?.takeIf { it.isNotBlank() }?.let { src ->
                        Text(
                            text = src,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!state.loading) {
                    LineIconBtn(onClick = onReidentify, contentDescription = "重新识别") {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                }
                LineIconBtn(onClick = onDismiss, contentDescription = "关闭") {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            ModelSwitcherRow(
                profiles = audioProfiles,
                selectedId = selectedProfileId,
                currentLabel = state.modelLabel,
                // 识别中也可换模型，下次开始生效
                enabled = true,
                onSelect = onSelectModel,
            )
            Spacer(Modifier.height(8.dp))
            BgmForeignToggleRow(
                on = state.preferForeignSong,
                // 识别中也可改；下次开始/重新识别生效
                enabled = true,
                onToggle = onTogglePreferForeign,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(line),
            )
            Spacer(Modifier.height(10.dp))

            when {
                state.loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 1.5.dp,
                            color = ink,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = state.status ?: "识别中…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ink,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (state.preferForeignSong) {
                                "外语模式 · 不想等了就点取消"
                            } else {
                                "截取/听辨中 · 可随时取消"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        LineTextBtn("取消识别", onClick = onCancelIdentify)
                    }
                }
                state.error != null && !state.hasContent -> {
                    Text(
                        text = state.error ?: "识别失败",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(12.dp))
                    LineTextBtn("开始识别", onClick = onStartIdentify)
                }
                !state.hasContent -> {
                    // 空闲：先调外语/模型，再手动开始
                    Text(
                        text = state.reply.ifBlank {
                            "先选模型、需要时打开「外语 BGM」，再点开始。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "不会自动搜索，避免误开。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    LineTextBtn("开始识别", onClick = onStartIdentify)
                }
                else -> {
                    if (state.guessLabel.isNotBlank()) {
                        Text(
                            text = state.guessLabel,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium,
                            color = ink,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    state.reply.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    if (state.candidates.isNotEmpty()) {
                        Text(
                            text = "候选",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        state.candidates.take(4).forEach { c ->
                            CandidateLine(c)
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    if (state.tracks.isEmpty()) {
                        Text(
                            text = "认出了歌名，但暂时没有可播版本。可换模型后重新识别。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ink,
                        )
                    } else {
                        Text(
                            text = "可播版本",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .heightIn(max = 400.dp),
                        ) {
                            items(state.tracks, key = { it.id }) { track ->
                                TrackRow(
                                    track = track,
                                    onClick = { onPlayNow(track) },
                                    trailing = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = "播放",
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clickable { onPlayNow(track) }
                                                    .padding(2.dp),
                                            )
                                            Icon(
                                                Icons.Default.SkipNext,
                                                contentDescription = "下一首",
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clickable { onPlayNext(track) }
                                                    .padding(2.dp),
                                            )
                                            Icon(
                                                Icons.AutoMirrored.Filled.PlaylistAdd,
                                                contentDescription = "收藏",
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clickable { onCollect(track) }
                                                    .padding(2.dp),
                                            )
                                        }
                                    },
                                )
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(line.copy(alpha = 0.45f)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 线稿「外语 BGM」开关 */
@Composable
private fun BgmForeignToggleRow(
    on: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val line = MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .border(
                1.dp,
                if (on) MaterialTheme.colorScheme.primary else line.copy(alpha = 0.55f),
                RoundedCornerShape(2.dp),
            )
            .background(
                if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = if (on) "外语 BGM · 已开" else "外语 BGM",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (on) MaterialTheme.colorScheme.primary else ink,
            )
            Text(
                text = if (on) {
                    "按英/日/韩等识别 · 改后点开始/重新识别"
                } else {
                    "片中是外语歌时点开，再点「开始识别」"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(2.dp))
                .border(1.dp, if (on) MaterialTheme.colorScheme.primary else line, RoundedCornerShape(2.dp))
                .background(
                    if (on) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                )
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Text(
                text = if (on) "开" else "关",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (on) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModelSwitcherRow(
    profiles: List<LlmProfile>,
    selectedId: String?,
    currentLabel: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    val line = MaterialTheme.colorScheme.outline
    Column {
        Text(
            text = "识别模型" + if (currentLabel.isNotBlank()) " · 当前" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (profiles.isEmpty()) {
            Text(
                text = "暂无支持音频的模型，请先在 AI 配置里添加 MiMo / 千问 Omni",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
            return
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            profiles.forEach { p ->
                val selected = p.id == selectedId ||
                    (selectedId == null && profiles.firstOrNull()?.id == p.id)
                val shape = RoundedCornerShape(2.dp)
                Text(
                    text = p.name.ifBlank { p.modelId }.take(18),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .clip(shape)
                        .then(
                            if (selected) {
                                Modifier.background(MaterialTheme.colorScheme.onSurface)
                            } else {
                                Modifier
                                    .border(1.dp, line, shape)
                                    .background(MaterialTheme.colorScheme.surface)
                            },
                        )
                        .clickable(enabled = enabled) { onSelect(p.id) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (currentLabel.isNotBlank()) {
            Text(
                text = currentLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun CandidateLine(c: SongCandidate) {
    val conf = c.confidence?.let { "  ${(it * 100).toInt()}%" }.orEmpty()
    val line = buildString {
        append(c.title)
        if (!c.artist.isNullOrBlank()) append("  ·  ${c.artist}")
        append(conf)
    }
    Text(
        text = "·  $line",
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@Composable
private fun LineIconBtn(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier = Modifier
            .size(32.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun LineTextBtn(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(2.dp)
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
