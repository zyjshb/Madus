package com.madus.mobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.zIndex
import com.madus.mobile.domain.Track
import com.madus.mobile.ui.BiliRecognizeUiState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 推荐页「B站识曲」悬浮球：
 * - 有正在播放时出现；空闲几秒自动收成左侧细条
 * - 点展开球再点 → 识别当前稿件官方 BGM 标签
 * - 位置/展开状态由外部持有，切 tab 不丢
 */
@Composable
fun BiliRecognizeFab(
    enabled: Boolean,
    state: BiliRecognizeUiState,
    onRecognize: () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    offsetY: Float,
    /** 传入拖动增量；父组件负责累加并 clamp */
    onOffsetYDelta: (Float) -> Unit,
    onExpandRequest: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!enabled) return

    val density = LocalDensity.current
    var touchGen by remember { mutableStateOf(0) }

    // 仅在「当前是展开 + 有交互/识别」后空闲收起；切 tab 回来不强制弹开
    LaunchedEffect(enabled, touchGen, state.loading, state.panelVisible, expanded) {
        if (!enabled) return@LaunchedEffect
        if (state.loading || state.panelVisible) {
            if (!expanded) onExpandedChange(true)
            return@LaunchedEffect
        }
        if (!expanded) return@LaunchedEffect
        delay(3200)
        onExpandedChange(false)
    }

    val dragRangePx = with(density) { 280.dp.toPx() }

    Box(
        modifier = modifier
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .zIndex(8f)
            .pointerInput(dragRangePx) {
                detectVerticalDragGestures { _, dragAmount ->
                    touchGen++
                    if (!expanded) onExpandedChange(true)
                    // 父级累加 offset，避免 pointerInput 闭包拿到陈旧 offsetY
                    onOffsetYDelta(dragAmount)
                }
            },
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.85f) +
                slideInHorizontally { -it / 3 },
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.85f) +
                slideOutHorizontally { -it / 3 },
        ) {
            val shape = CircleShape
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(shape)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, shape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                    .clickable {
                        touchGen++
                        onRecognize()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = "B站识曲",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = if (state.loading) "识别" else "识曲",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        // 收起后：左侧细条，点一下再展开
        AnimatedVisibility(
            visible = !expanded,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(120)),
        ) {
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                    )
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                    .clickable {
                        touchGen++
                        onExpandedChange(true)
                        onExpandRequest()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "♪",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiliRecognizeResultSheet(
    state: BiliRecognizeUiState,
    onDismiss: () -> Unit,
    onPlay: (Track, List<Track>) -> Unit,
) {
    if (!state.panelVisible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "B站识曲",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    state.sourceTitle?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }

            when {
                state.loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("读取官方 BGM 标签…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                state.error != null && state.tracks.isEmpty() -> {
                    Text(
                        state.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                    Text(
                        "仅对创作者标注了 BGM 的稿件有效；冷门/魔改可改用 AI 搜哼唱。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    state.guessLabel?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    if (state.tracks.isEmpty()) {
                        Text(
                            "识别到歌名，但 B 站暂无匹配视频",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            items(state.tracks, key = { it.id }) { track ->
                                TrackRow(
                                    track = track,
                                    onClick = { onPlay(track, state.tracks) },
                                )
                            }
                        }
                    }
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("关闭")
            }
        }
    }
}
