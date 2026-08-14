package com.madus.mobile.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.madus.mobile.domain.Track
import com.madus.mobile.ui.PlayModeLabel
import com.madus.mobile.ui.QueueSearchUiState
import com.madus.mobile.ui.components.CoverArt
import com.madus.mobile.ui.liquid.LiquidPageHeader
import com.madus.mobile.ui.theme.isLiquidTheme
import kotlin.math.roundToInt

/**
 * Spotify / 汽水 style queue:
 * - Header + mode + clear
 * - 队列内搜索：筛当前队列 + B 站搜新歌插播（不顶掉原歌单）
 * - 「正在播放」anchor row
 * - 「接下来」list with cover thumbs, no per-row button wall
 * - 长按拖动调整顺序；整表可见（含已播），不把上一首藏掉
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueueScreen(
    tracks: List<Track>,
    currentId: String?,
    playMode: PlayModeLabel,
    sourceLabel: String = "当前队列",
    queueSearch: QueueSearchUiState = QueueSearchUiState(),
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onClear: () -> Unit,
    onRemove: (String) -> Unit,
    /** 听错了：去搜索换一首（导入歌单常用） */
    onReplaceTrack: (Track) -> Unit = {},
    onPlayNext: (Track) -> Unit,
    onShuffle: () -> Unit,
    onCycleMode: () -> Unit,
    onOpenPlaySource: () -> Unit = {},
    onCollectCurrent: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onSearchSubmit: () -> Unit = {},
    onSearchClear: () -> Unit = {},
    onPlaySearchResult: (Track) -> Unit = {},
    onMove: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val itemH = with(density) { 64.dp.toPx() }
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragTo by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val query = queueSearch.query
    val displayTracks = remember(tracks, query) {
        val q = query.trim()
        if (q.isEmpty()) tracks
        else tracks.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true)
        }
    }
    val current = tracks.firstOrNull { it.id == currentId }
        ?: tracks.firstOrNull()
    val showRemote = query.trim().isNotEmpty() &&
        (queueSearch.results.isNotEmpty() || queueSearch.isSearching || queueSearch.message != null)
    val canDrag = query.trim().isEmpty()

    val liquid = isLiquidTheme()
    Column(
        modifier = modifier
            .fillMaxSize()
            .then(if (liquid) Modifier else Modifier.background(MaterialTheme.colorScheme.background)),
    ) {
        if (liquid) {
            LiquidPageHeader(
                title = "队列",
                subtitle = if (tracks.isEmpty()) "空" else "共 ${tracks.size} 首 · ${playMode.label}",
                onBack = onBack,
                action = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onCycleMode) {
                            Icon(
                                imageVector = when (playMode) {
                                    PlayModeLabel.SHUFFLE -> Icons.Default.Shuffle
                                    PlayModeLabel.SINGLE -> Icons.Default.RepeatOne
                                    PlayModeLabel.LOOP -> Icons.Default.Repeat
                                },
                                contentDescription = playMode.label,
                            )
                        }
                        if (tracks.isNotEmpty()) {
                            TextButton(onClick = onClear) {
                                Text("清空", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                },
            )
        } else {
        // Grab handle visual (sheet language)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }

        // Title bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "播放队列",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (tracks.isEmpty()) "空" else "共 ${tracks.size} 首 · ${playMode.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onCycleMode) {
                Icon(
                    imageVector = when (playMode) {
                        PlayModeLabel.SHUFFLE -> Icons.Default.Shuffle
                        PlayModeLabel.SINGLE -> Icons.Default.RepeatOne
                        PlayModeLabel.LOOP -> Icons.Default.Repeat
                    },
                    contentDescription = playMode.label,
                )
            }
            if (tracks.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text("清空", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        }

        // 搜索条：过滤当前队列 + 回车搜 B 站新歌（插播）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onSearchQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            "搜队列 / 回车搜新歌（插播）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onSearchClear,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "清除",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (queueSearch.isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            }
        }

        // 换歌单 + 加入歌单：只在队列页，不占推荐播放台面
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                    .clickable(onClick = onOpenPlaySource)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "来源",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = sourceLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text("换歌单", style = MaterialTheme.typography.labelLarge)
            }
            if (current != null) {
                TextButton(onClick = onCollectCurrent) {
                    Text("收藏", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (tracks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "队列是空的",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "在搜索或歌单里点一首歌即可加入",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // 远程搜索结果：插播，不替换队列
            if (showRemote) {
                item(key = "section-search") {
                    QueueSectionLabel("搜索结果 · 点播插播到当前队列")
                }
                queueSearch.message?.let { msg ->
                    item(key = "search-msg") {
                        Text(
                            text = msg,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(queueSearch.results, key = { "sr-${it.id}" }) { track ->
                    QueueTrackRow(
                        track = track,
                        isCurrent = false,
                        onClick = { onPlaySearchResult(track) },
                        onRemove = null,
                    )
                }
                item(key = "section-queue-div") {
                    QueueSectionLabel(
                        if (query.trim().isEmpty()) "当前队列" else "队列内匹配",
                    )
                }
            }

            // 完整队列（含已播 / 正在播 / 未播），长按拖到目标位置
            item(key = "section-all") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (query.trim().isEmpty()) {
                            "播放列表 · 长按拖动排序"
                        } else {
                            "匹配结果"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (displayTracks.size > 1 && query.trim().isEmpty()) {
                        Text(
                            text = "打乱",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable(onClick = onShuffle)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            if (displayTracks.isEmpty()) {
                item(key = "empty-all") {
                    Text(
                        text = if (query.trim().isNotEmpty()) "队列里没有匹配" else "队列是空的",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                itemsIndexed(displayTracks, key = { _, t -> "q-${t.id}" }) { index, track ->
                    val realIndex = tracks.indexOfFirst { it.id == track.id }
                        .takeIf { it >= 0 } ?: index
                    val isDragging = dragFrom == realIndex
                    val visualTo = if (dragFrom >= 0) dragTo else -1
                    QueueTrackRow(
                        track = track,
                        isCurrent = track.id == currentId,
                        indexLabel = "${realIndex + 1}",
                        dragging = isDragging,
                        dragOffsetY = if (isDragging) dragOffsetY else 0f,
                        onClick = { onPlayTrack(track, tracks) },
                        onRemove = if (track.id == currentId) null else ({ onRemove(track.id) }),
                        onReplace = { onReplaceTrack(track) },
                        onPlayNext = { onPlayNext(track) },
                        modifier = if (canDrag && realIndex >= 0) {
                            Modifier
                                .zIndex(if (isDragging) 2f else 0f)
                                .pointerInput(realIndex, tracks.size) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            dragFrom = realIndex
                                            dragTo = realIndex
                                            dragOffsetY = 0f
                                        },
                                        onDragEnd = {
                                            val from = dragFrom
                                            val to = dragTo
                                            dragFrom = -1
                                            dragTo = -1
                                            dragOffsetY = 0f
                                            if (from >= 0 && to >= 0 && from != to) {
                                                onMove(from, to)
                                            }
                                        },
                                        onDragCancel = {
                                            dragFrom = -1
                                            dragTo = -1
                                            dragOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y
                                            val from = dragFrom
                                            if (from < 0) return@detectDragGesturesAfterLongPress
                                            val steps = (dragOffsetY / itemH).roundToInt()
                                            dragTo = (from + steps)
                                                .coerceIn(0, tracks.lastIndex)
                                        },
                                    )
                                }
                        } else {
                            Modifier
                        },
                    )
                    // 拖动落点提示
                    if (visualTo == realIndex && dragFrom >= 0 && dragFrom != visualTo) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.onBackground),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun QueueTrackRow(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
    onReplace: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER")
    onPlayNext: (() -> Unit)? = null,
    indexLabel: String = "",
    dragging: Boolean = false,
    dragOffsetY: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(2.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                if (dragging) {
                    shadowElevation = 8f
                    alpha = 0.95f
                }
            }
            .offset { IntOffset(0, if (dragging) dragOffsetY.roundToInt() else 0) }
            .then(
                if (isCurrent) {
                    Modifier
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                } else {
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                },
            )
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (indexLabel.isNotBlank()) {
            Text(
                text = indexLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp),
            )
        }
        if (isCurrent) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.onBackground),
            )
            Spacer(Modifier.width(8.dp))
        }
        CoverArt(coverUrl = track.coverUrl, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    if (isCurrent) append("正在播放 · ")
                    append(track.artist.ifBlank { track.source.displayName })
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onReplace != null) {
            IconButton(
                onClick = onReplace,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索换歌",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            Icons.Default.DragHandle,
            contentDescription = "长按拖动",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(22.dp),
        )
        if (!isCurrent && onRemove != null) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "移除",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (!isCurrent && !dragging) {
        HorizontalDivider(
            modifier = Modifier.padding(start = 76.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        )
    }
}
