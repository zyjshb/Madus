package com.madus.mobile.ui.liquid

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.madus.mobile.domain.PlaybackState
import com.madus.mobile.domain.Track
import com.madus.mobile.ui.RecommendSegment
import com.madus.mobile.ui.RecommendUiState
import com.madus.mobile.ui.components.BiliPlayerSurface
import com.madus.mobile.ui.theme.LiquidType

@Composable
fun LiquidRecommendScreen(
    state: RecommendUiState,
    playback: PlaybackState,
    onSegment: (RecommendSegment) -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleLike: () -> Unit,
    onNotInterested: () -> Unit = {},
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
    onOpenNowPlaying: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var more by remember { mutableStateOf(false) }
    val playingThisRadio = playback.current != null && state.sourceId == "recommend"
    val loading = state.isLoading || state.isStartingPlayback
    val needLogin = state.sourceLabel == "请先登录"
    val track = playback.current
    val showVideo = videoMode && track?.isVideoStream == true
    val has = track != null
    val heroCover = track?.coverUrl ?: state.feed.firstOrNull()?.coverUrl
    val heroTitle = when {
        loading -> "加载中…"
        track != null -> track.title
        needLogin -> "登录后开电台"
        else -> "为你连播"
    }
    val heroSub = track?.artist?.ifBlank { state.sourceLabel }
        ?: if (needLogin) "先登录 B 站" else state.sourceLabel.ifBlank { "点封面开播" }
    val stations = state.feed
    val paper = MaterialTheme.colorScheme.onBackground

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 10.dp,
                    bottom = LocalLiquidChromeBottom.current,
                )
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("电台", style = LiquidType.largeTitle, color = paper, modifier = Modifier.weight(1f))
                GlassIconButton(
                    onClick = { more = true },
                    icon = Icons.Default.MoreVert,
                    contentDescription = "更多",
                )
            }
            Spacer(Modifier.height(14.dp))

            if (showVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onOpenNowPlaying),
                ) {
                    BiliPlayerSurface(modifier = Modifier.fillMaxSize(), fit = false)
                }
                Spacer(Modifier.height(10.dp))
                Text(heroTitle, style = LiquidType.title2, color = paper, maxLines = 2)
                Text(
                    heroSub,
                    style = LiquidType.subhead,
                    color = paper.copy(alpha = 0.62f),
                    modifier = if (track?.artist.orEmpty().isNotBlank()) {
                        Modifier.clickable(onClick = onOpenUp)
                    } else {
                        Modifier
                    },
                )
            } else {
                StageCard(
                    badge = if (playingThisRadio) "LIVE" else "FM",
                    title = heroTitle,
                    subtitle = heroSub,
                    coverUrl = heroCover,
                    live = playingThisRadio,
                    playing = playingThisRadio,
                    secondaryLabel = when {
                        loading -> null
                        needLogin -> "登录"
                        playingThisRadio -> "下一首"
                        else -> null
                    },
                    onSecondary = when {
                        needLogin -> onLogin
                        playingThisRadio -> onNext
                        else -> null
                    },
                    onPlay = when {
                        loading -> ({})
                        needLogin -> onLogin
                        playingThisRadio -> onOpenNowPlaying
                        else -> onStartRadio
                    },
                )
            }

            playback.errorMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = LiquidType.footnote, color = paper.copy(alpha = 0.62f))
            }

            if (stations.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Text("接下来", style = LiquidType.headline, color = paper)
                Spacer(Modifier.height(12.dp))
                stations.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        pair.forEach { item ->
                            MosaicTile(
                                title = item.title,
                                subtitle = item.artist,
                                coverUrl = item.coverUrl,
                                onClick = { onPlayTrack(item, stations) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
        }

        LiquidActionSheet(
            visible = more,
            onDismiss = { more = false },
            actions = listOf(
                LiquidSheetAction("全屏", enabled = has) { onFullscreen(); onImmersive() },
                LiquidSheetAction("收藏", enabled = has) { onCollectCurrent() },
                LiquidSheetAction(
                    title = if (track != null && state.notInterestedIds.contains(track.id)) {
                        "取消不喜欢"
                    } else {
                        "不喜欢"
                    },
                    enabled = has,
                ) { onNotInterested() },
                LiquidSheetAction("评论", enabled = has) { onComments() },
                LiquidSheetAction(
                    title = if (videoMode) "封面" else "视频",
                    enabled = track?.isVideoStream == true,
                ) { onVideoModeChange(!videoMode) },
                LiquidSheetAction("分享", enabled = has) { onShare() },
                LiquidSheetAction(
                    if (sleepLabel.isNullOrBlank()) "定时关闭" else "定时 $sleepLabel",
                ) { onSleepClick() },
                LiquidSheetAction("音质 $qualityLabel") { onQualityClick() },
                LiquidSheetAction("缓存", enabled = has) { onCache() },
                LiquidSheetAction("相关电台", enabled = has) { onRelatedRadio() },
            ),
        )
    }
}
