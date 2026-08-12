package com.madus.mobile.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.madus.mobile.domain.PlaybackState
import com.madus.mobile.domain.Track
import com.madus.mobile.ui.RecommendSegment
import com.madus.mobile.ui.RecommendUiState
import com.madus.mobile.ui.components.BiliPlayerSurface
import com.madus.mobile.ui.components.CoverArt
import com.madus.mobile.ui.components.TrackRow
import com.madus.mobile.ui.theme.appearanceTokens

/**
 * 推荐页 = 电台台面。视频模式时封面位改为视频画面。
 */
@Composable
fun RecommendScreen(
    state: RecommendUiState,
    playback: PlaybackState,
    onSegment: (RecommendSegment) -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleLike: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onOpenQueue: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onCollectCurrent: () -> Unit = {},
    onCollectTrack: (Track) -> Unit = {},
    /** 独立 B 站收藏（不经过本地歌单） */
    onBiliCollectCurrent: () -> Unit = {},
    onOpenPlaySource: () -> Unit = {},
    onRemoveRecent: (String) -> Unit = {},
    onClearRecent: () -> Unit = {},
    onImmersive: () -> Unit = {},
    onShare: () -> Unit = {},
    onComments: () -> Unit = {},
    onCache: () -> Unit = {},
    onRelatedRadio: () -> Unit = {},
    onStartRadio: () -> Unit = {},
    onLogin: () -> Unit = {},
    onQualityClick: () -> Unit = {},
    onSleepClick: () -> Unit = {},
    qualityLabel: String = "标准",
    sleepLabel: String? = null,
    videoMode: Boolean = false,
    onVideoModeChange: (Boolean) -> Unit = {},
    onFullscreen: () -> Unit = {},
    onOpenUp: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        SegmentBar(
            selected = state.segment,
            onSelect = onSegment,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        when (state.segment) {
            RecommendSegment.Recent -> RecentList(
                tracks = state.recent,
                onPlayTrack = onPlayTrack,
                onCollectTrack = onCollectTrack,
                onRemoveRecent = onRemoveRecent,
                onClearRecent = onClearRecent,
                modifier = Modifier.weight(1f),
            )
            RecommendSegment.Feed -> RadioPanel(
                state = state,
                playback = playback,
                onToggle = onToggle,
                onNext = onNext,
                onPrevious = onPrevious,
                onToggleLike = onToggleLike,
                onOpenQueue = onOpenQueue,
                onSeek = onSeek,
                onCollectCurrent = onCollectCurrent,
                onBiliCollectCurrent = onBiliCollectCurrent,
                onShare = onShare,
                onComments = onComments,
                onCache = onCache,
                onRelatedRadio = onRelatedRadio,
                onStartRadio = onStartRadio,
                onLogin = onLogin,
                onQualityClick = onQualityClick,
                onSleepClick = onSleepClick,
                qualityLabel = qualityLabel,
                sleepLabel = sleepLabel,
                videoMode = videoMode,
                onVideoModeChange = onVideoModeChange,
                onFullscreen = {
                    onFullscreen()
                    onImmersive()
                },
                onOpenUp = onOpenUp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RecentList(
    tracks: List<Track>,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onCollectTrack: (Track) -> Unit = {},
    onRemoveRecent: (String) -> Unit = {},
    onClearRecent: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (tracks.isEmpty()) {
        Text(
            text = "还没有最近播放",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(top = 12.dp),
        )
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = "清空",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onClearRecent),
                )
            }
        }
        items(tracks, key = { it.id }) { track ->
            TrackRow(
                track = track,
                onClick = { onPlayTrack(track, tracks) },
                onCollect = { onCollectTrack(track) },
                onRemove = { onRemoveRecent(track.id) },
            )
        }
    }
}

@Composable
private fun RadioPanel(
    state: RecommendUiState,
    playback: PlaybackState,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleLike: () -> Unit,
    onOpenQueue: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onCollectCurrent: () -> Unit = {},
    onBiliCollectCurrent: () -> Unit = {},
    onShare: () -> Unit = {},
    onComments: () -> Unit = {},
    onCache: () -> Unit = {},
    onRelatedRadio: () -> Unit = {},
    onStartRadio: () -> Unit = {},
    onLogin: () -> Unit = {},
    onQualityClick: () -> Unit = {},
    onSleepClick: () -> Unit = {},
    qualityLabel: String = "标准",
    sleepLabel: String? = null,
    videoMode: Boolean = false,
    onVideoModeChange: (Boolean) -> Unit = {},
    onFullscreen: () -> Unit = {},
    onOpenUp: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val track = playback.current
    val showVideo = videoMode && track != null && track.isVideoStream
    // 上滑显示 / 下滑隐藏次要操作条（不再用上下滑切歌）
    var actionsExpanded by remember { mutableStateOf(false) }
    var dragAcc by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 来源小标签
        Text(
            text = buildString {
                append(state.sourceLabel.ifBlank { "推荐电台" })
                if (videoMode) append(" · 视频")
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(bottom = 4.dp),
        )

        if (com.madus.mobile.BuildConfig.DEBUG && state.debugRows.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 76.dp)
                    .padding(bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                state.debugRows.take(8).forEach { row ->
                    Text(
                        text = row,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // 竖滑只挂在封面/信息区：避免抢走底部菜单的左右滑
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(actionsExpanded) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            when {
                                dragAcc < -56f -> actionsExpanded = true   // 上滑 → 展开
                                dragAcc > 56f -> actionsExpanded = false  // 下滑 → 收起
                            }
                            dragAcc = 0f
                        },
                        onVerticalDrag = { _, amount -> dragAcc += amount },
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (showVideo) {
                // 电台预览小窗；点「清屏」才进心动模式（无边框全屏）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(appearanceTokens().cornerMd)),
                ) {
                    BiliPlayerSurface(modifier = Modifier.fillMaxSize(), fit = false)
                }
            } else {
                CoverArt(
                    coverUrl = track?.coverUrl,
                    modifier = Modifier
                        .fillMaxWidth(if (track != null) 0.62f else 0.48f)
                        .heightIn(max = 240.dp)
                        .aspectRatio(1f),
                    size = 0.dp,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = track?.title
                    ?: when {
                        state.isLoading || state.isStartingPlayback -> "加载中…"
                        else -> "未在播放"
                    },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = track?.artist
                    ?: "从首页歌单或搜索点一首开始",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = if (track != null && track.artist.isNotBlank()) {
                    Modifier.clickable(onClick = onOpenUp)
                } else {
                    Modifier
                },
            )

            if (track != null) {
                Spacer(Modifier.height(12.dp))
                com.madus.mobile.ui.components.SeekBar(
                    positionMs = playback.positionMs,
                    durationMs = playback.durationMs,
                    onSeek = onSeek,
                    enabled = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                )
            }

            playback.errorMessage?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            // 有推荐队列时：轻量「播放」；起播中绝不展示（避免二次闪现还要点第二次）
            if (track == null &&
                !state.isLoading &&
                !state.isStartingPlayback &&
                state.feed.isNotEmpty()
            ) {
                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(appearanceTokens().cornerSm))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(appearanceTokens().cornerSm),
                        )
                        .clickable(onClick = onStartRadio)
                        .padding(horizontal = 28.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "播放 ${state.sourceLabel}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            if (track == null && !state.isLoading && state.feed.isEmpty() &&
                state.sourceLabel == "请先登录"
            ) {
                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(appearanceTokens().cornerSm))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(appearanceTokens().cornerSm),
                        )
                        .clickable(onClick = onLogin)
                        .padding(horizontal = 28.dp, vertical = 12.dp),
                ) {
                    Text(text = "登录 B 站听推荐", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        // 主控：爱心 · 上一首 · 播放 · 下一首 · 队列（切歌只靠这里）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggleLike, enabled = track != null) {
                Icon(
                    imageVector = if (state.likedIds.contains(track?.id)) {
                        Icons.Filled.Favorite
                    } else {
                        Icons.Filled.FavoriteBorder
                    },
                    contentDescription = "喜欢",
                )
            }
            IconButton(onClick = onPrevious, enabled = track != null) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(34.dp))
            }
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                    .clickable(
                        enabled = !state.isStartingPlayback && !state.isLoading,
                        onClick = {
                            when {
                                track != null -> onToggle()
                                state.feed.isNotEmpty() -> onStartRadio()
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playback.isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(34.dp),
                )
            }
            IconButton(onClick = onNext, enabled = track != null) {
                Icon(Icons.Default.SkipNext, contentDescription = "下一首", modifier = Modifier.size(34.dp))
            }
            IconButton(onClick = onOpenQueue) {
                Icon(Icons.AutoMirrored.Outlined.QueueMusic, contentDescription = "队列")
            }
        }

        // 上滑展开提示条 / 次要操作（歌单·评论等）
        Spacer(Modifier.height(2.dp))
        if (!actionsExpanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { actionsExpanded = true }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = "上滑显示更多",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "上滑 · 全屏/收藏/评论…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedVisibility(
            visible = actionsExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                // 横向滑动菜单，避免图标挤成一团
                val menuScroll = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(menuScroll)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SecondaryAction(
                        icon = Icons.Default.Fullscreen,
                        label = "全屏",
                        onClick = onFullscreen,
                        enabled = track != null,
                        contentDescription = "全屏播放",
                    )
                    SecondaryAction(
                        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                        label = "收藏",
                        onClick = onCollectCurrent,
                        enabled = track != null,
                        contentDescription = "收藏（本地/B站）",
                    )
                    SecondaryAction(
                        icon = Icons.Default.Share,
                        label = "分享",
                        onClick = onShare,
                        enabled = track != null,
                    )
                    SecondaryAction(
                        icon = Icons.Default.ChatBubbleOutline,
                        label = "评论",
                        onClick = onComments,
                        enabled = track != null,
                    )
                    SecondaryAction(
                        icon = if (sleepLabel.isNullOrBlank()) Icons.Outlined.Bedtime else Icons.Filled.Bedtime,
                        label = if (sleepLabel.isNullOrBlank()) "定时" else sleepLabel!!,
                        onClick = onSleepClick,
                        active = !sleepLabel.isNullOrBlank(),
                        contentDescription = if (sleepLabel.isNullOrBlank()) {
                            "睡眠定时"
                        } else {
                            "睡眠定时，剩余 $sleepLabel"
                        },
                    )
                    SecondaryAction(
                        icon = if (qualityLabel == "标准") Icons.Outlined.HighQuality else Icons.Filled.HighQuality,
                        label = qualityLabel,
                        onClick = onQualityClick,
                        active = qualityLabel != "标准",
                        contentDescription = "音质，当前$qualityLabel",
                    )
                    SecondaryAction(
                        icon = if (videoMode) Icons.Filled.Videocam else Icons.Filled.MusicNote,
                        label = if (videoMode) "视频" else "音乐",
                        onClick = { onVideoModeChange(!videoMode) },
                        active = videoMode,
                        contentDescription = if (videoMode) "当前视频模式，点切换音乐" else "当前音乐模式，点切换视频",
                    )
                    SecondaryAction(
                        icon = Icons.Default.CloudDownload,
                        label = "缓存",
                        onClick = onCache,
                        enabled = track != null,
                    )
                    SecondaryAction(
                        icon = Icons.Default.AutoAwesome,
                        label = "推荐",
                        onClick = onStartRadio,
                        contentDescription = "为你推荐",
                    )
                    SecondaryAction(
                        icon = Icons.Default.Radio,
                        label = "相关",
                        onClick = onRelatedRadio,
                        enabled = track != null,
                        contentDescription = "相关电台",
                    )
                }
                Text(
                    text = "菜单可左右滑 · 点此或下滑收起",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable { actionsExpanded = false }
                        .padding(bottom = 4.dp, top = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun SecondaryAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    active: Boolean = false,
    contentDescription: String? = null,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        active -> MaterialTheme.colorScheme.onBackground
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription ?: label,
            modifier = Modifier.size(22.dp),
            tint = tint,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
        )
    }
}

@Composable
private fun SegmentBar(
    selected: RecommendSegment,
    onSelect: (RecommendSegment) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            .padding(2.dp),
    ) {
        SegmentChip(
            text = "推荐",
            selected = selected == RecommendSegment.Feed,
            onClick = { onSelect(RecommendSegment.Feed) },
            modifier = Modifier.weight(1f),
        )
        SegmentChip(
            text = "最近",
            selected = selected == RecommendSegment.Recent,
            onClick = { onSelect(RecommendSegment.Recent) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SegmentChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.background
    val fg = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(1.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}
