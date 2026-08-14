package com.madus.mobile.ui.liquid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.madus.mobile.domain.PlaybackState
import com.madus.mobile.domain.Track
import com.madus.mobile.ui.RecommendSegment
import com.madus.mobile.ui.RecommendUiState
import com.madus.mobile.ui.components.BiliPlayerSurface
import com.madus.mobile.ui.components.CoverArt
import com.madus.mobile.ui.components.MadusImageLoader
import com.madus.mobile.ui.components.SeekBar
import com.madus.mobile.ui.components.normalizeCoverUrl
import com.madus.mobile.ui.theme.liquidTokens

@Composable
fun LiquidRecommendScreen(
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
    onBiliCollectCurrent: () -> Unit = {},
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
    val tokens = liquidTokens()
    val cover = playback.current?.coverUrl
    val context = LocalContext.current
    val loader = remember { MadusImageLoader.get(context) }
    val url = normalizeCoverUrl(cover)
    var more by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        if (!url.isNullOrBlank() && state.segment == RecommendSegment.Feed) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(url).crossfade(280).build(),
                contentDescription = null,
                imageLoader = loader,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(48.dp),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = if (tokens.dark) 0.45f else 0.22f),
                                Color.Black.copy(alpha = if (tokens.dark) 0.72f else 0.42f),
                            ),
                        ),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 118.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassPill("电台", selected = state.segment == RecommendSegment.Feed, onClick = {
                        onSegment(RecommendSegment.Feed)
                    })
                    GlassPill("最近", selected = state.segment == RecommendSegment.Recent, onClick = {
                        onSegment(RecommendSegment.Recent)
                    })
                }
                Spacer(Modifier.weight(1f))
                if (state.segment == RecommendSegment.Feed) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.28f))
                            .clickable { more = !more },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "更多",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            when (state.segment) {
                RecommendSegment.Recent -> LiquidRecentList(
                    tracks = state.recent,
                    onPlayTrack = onPlayTrack,
                    onCollectTrack = onCollectTrack,
                    onRemoveRecent = onRemoveRecent,
                    onClearRecent = onClearRecent,
                    modifier = Modifier.weight(1f),
                )
                RecommendSegment.Feed -> LiquidRadioPanel(
                    state = state,
                    playback = playback,
                    onToggle = onToggle,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onToggleLike = onToggleLike,
                    onOpenQueue = onOpenQueue,
                    onSeek = onSeek,
                    onCollectCurrent = onCollectCurrent,
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

        if (more && state.segment == RecommendSegment.Feed) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable { more = false },
            )
            LiquidMorePopup(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 52.dp, end = 16.dp),
                sleepLabel = sleepLabel,
                qualityLabel = qualityLabel,
                hasTrack = playback.current != null,
                onShare = { more = false; onShare() },
                onSleep = { more = false; onSleepClick() },
                onQuality = { more = false; onQualityClick() },
                onCache = { more = false; onCache() },
                onRelated = { more = false; onRelatedRadio() },
            )
        }
    }
}

@Composable
private fun LiquidRecentList(
    tracks: List<Track>,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onCollectTrack: (Track) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tracks.isEmpty()) {
        Text(
            "还没有最近播放",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(top = 24.dp),
        )
        return
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                "清空",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onClearRecent)
                    .padding(8.dp),
            )
        }
        GlassGroup {
            tracks.forEachIndexed { i, track ->
                LiquidTrackRow(
                    track = track,
                    onClick = { onPlayTrack(track, tracks) },
                    onCollect = { onCollectTrack(track) },
                    onRemove = { onRemoveRecent(track.id) },
                )
                if (i != tracks.lastIndex) GlassDivider()
            }
        }
    }
}

@Composable
private fun LiquidRadioPanel(
    state: RecommendUiState,
    playback: PlaybackState,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleLike: () -> Unit,
    onOpenQueue: () -> Unit,
    onSeek: (Long) -> Unit,
    onCollectCurrent: () -> Unit,
    onShare: () -> Unit,
    onComments: () -> Unit,
    onCache: () -> Unit,
    onRelatedRadio: () -> Unit,
    onStartRadio: () -> Unit,
    onLogin: () -> Unit,
    onQualityClick: () -> Unit,
    onSleepClick: () -> Unit,
    qualityLabel: String,
    sleepLabel: String?,
    videoMode: Boolean,
    onVideoModeChange: (Boolean) -> Unit,
    onFullscreen: () -> Unit,
    onOpenUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = playback.current
    val showVideo = videoMode && track != null && track.isVideoStream
    val liked = state.likedIds.contains(track?.id)
    val hasWall = !track?.coverUrl.isNullOrBlank()
    val onMedia = if (hasWall) Color.White else MaterialTheme.colorScheme.onBackground
    val mute = if (hasWall) Color.White.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            buildString {
                append(state.sourceLabel.ifBlank { "推荐电台" })
                if (videoMode) append(" · 视频")
            },
            style = MaterialTheme.typography.labelLarge,
            color = mute,
            modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (showVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(20.dp)),
                ) {
                    BiliPlayerSurface(modifier = Modifier.fillMaxSize(), fit = false)
                }
            } else {
                CoverArt(
                    coverUrl = track?.coverUrl,
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .heightIn(max = 320.dp)
                        .aspectRatio(1f),
                    size = 0.dp,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                track?.title ?: when {
                    state.isLoading || state.isStartingPlayback -> "加载中…"
                    else -> "还没在播"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = onMedia,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                track?.artist ?: "从首页或搜索点一首",
                style = MaterialTheme.typography.bodyMedium,
                color = mute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = if (track != null && track.artist.isNotBlank()) {
                    Modifier.clickable(onClick = onOpenUp)
                } else Modifier,
            )
            if (track != null) {
                Spacer(Modifier.height(14.dp))
                SeekBar(
                    positionMs = playback.positionMs,
                    durationMs = playback.durationMs,
                    onSeek = onSeek,
                    enabled = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            playback.errorMessage?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = mute)
            }
            if (track == null && !state.isLoading && !state.isStartingPlayback && state.feed.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                GlassPill("播放 ${state.sourceLabel}", selected = true, onClick = onStartRadio)
            }
            if (track == null && !state.isLoading && state.feed.isEmpty() && state.sourceLabel == "请先登录") {
                Spacer(Modifier.height(16.dp))
                GlassPill("登录 B 站听推荐", selected = true, onClick = onLogin)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "喜欢",
                tint = onMedia,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onToggleLike),
            )
            Icon(
                Icons.Default.SkipPrevious,
                contentDescription = "上一首",
                tint = onMedia,
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = onPrevious),
            )
            val playPress = remember { MutableInteractionSource() }
            val playDown by playPress.collectIsPressedAsState()
            val playScale by animateFloatAsState(
                if (playDown) 0.88f else 1f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
                label = "playScale",
            )
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .graphicsLayer {
                        scaleX = playScale
                        scaleY = playScale
                    }
                    .clip(CircleShape)
                    .background(onMedia)
                    .clickable(
                        interactionSource = playPress,
                        indication = null,
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
                    if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playback.isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(32.dp),
                    tint = Color.Black.copy(alpha = 0.86f),
                )
            }
            Icon(
                Icons.Default.SkipNext,
                contentDescription = "下一首",
                tint = onMedia,
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = onNext),
            )
            Icon(
                Icons.AutoMirrored.Outlined.QueueMusic,
                contentDescription = "队列",
                tint = onMedia,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onOpenQueue),
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            LiquidMiniAction(Icons.Default.Fullscreen, "全屏", track != null, onFullscreen, onMedia)
            LiquidMiniAction(Icons.AutoMirrored.Filled.PlaylistAdd, "收藏", track != null, onCollectCurrent, onMedia)
            LiquidMiniAction(Icons.Default.ChatBubbleOutline, "评论", track != null, onComments, onMedia)
            LiquidMiniAction(
                if (videoMode) Icons.Filled.Videocam else Icons.Filled.MusicNote,
                if (videoMode) "视频" else "音乐",
                true,
                { onVideoModeChange(!videoMode) },
                onMedia,
            )
        }
    }
}

@Composable
private fun LiquidMiniAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = if (enabled) tint else tint.copy(alpha = 0.35f),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) tint.copy(alpha = 0.72f) else tint.copy(alpha = 0.35f),
        )
    }
}

@Composable
private fun LiquidMorePopup(
    sleepLabel: String?,
    qualityLabel: String,
    hasTrack: Boolean,
    onShare: () -> Unit,
    onSleep: () -> Unit,
    onQuality: () -> Unit,
    onCache: () -> Unit,
    onRelated: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = liquidTokens().dark
    val bg = if (dark) Color(0xFF2C2C2E) else Color(0xFFF7F7F8)
    val fg = if (dark) Color(0xFFF2F2F7) else Color(0xFF111111)
    val mute = if (dark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    val line = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .width(196.dp)
            .shadow(16.dp, shape, clip = false)
            .clip(shape)
            .background(bg),
    ) {
        LiquidMoreItem("分享", fg, onShare)
        HorizontalDivider(thickness = 0.5.dp, color = line)
        LiquidMoreItem(
            if (sleepLabel.isNullOrBlank()) "定时关闭" else "定时  $sleepLabel",
            fg,
            onSleep,
        )
        HorizontalDivider(thickness = 0.5.dp, color = line)
        LiquidMoreItem("音质  $qualityLabel", fg, onQuality)
        HorizontalDivider(thickness = 0.5.dp, color = line)
        LiquidMoreItem("缓存这首", if (hasTrack) fg else mute, onCache, enabled = hasTrack)
        HorizontalDivider(thickness = 0.5.dp, color = line)
        LiquidMoreItem("相关电台", if (hasTrack) fg else mute, onRelated, enabled = hasTrack)
    }
}

@Composable
private fun LiquidMoreItem(
    title: String,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Text(
        title,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    )
}
