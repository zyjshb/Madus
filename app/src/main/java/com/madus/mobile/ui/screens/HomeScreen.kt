package com.madus.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.madus.mobile.data.LikedStore
import com.madus.mobile.domain.Playlist
import com.madus.mobile.domain.Track
import com.madus.mobile.ui.HomeUiState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.madus.mobile.ui.components.CoverArt
import com.madus.mobile.ui.components.MadusImageLoader
import com.madus.mobile.ui.components.SectionTitle
import com.madus.mobile.ui.components.TrackRow
import com.madus.mobile.ui.components.normalizeCoverUrl
import com.madus.mobile.ui.liquid.LocalLiquidChromeBottom
import com.madus.mobile.ui.theme.isLiquidTheme

private enum class HomeChip(val label: String) {
    All("全部"),
    Library("音乐库"),
    Bili("B站"),
    Recent("最近"),
}

/**
 * Spotify-style Home A：
 * 问候顶栏 → chips → 2×2 快捷宫格 → 横滑歌单/B站 → 最近列表
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onCollectTrack: (Track) -> Unit = {},
    onRemoveRecent: (String) -> Unit = {},
    onClearRecent: () -> Unit = {},
    onOpenMe: () -> Unit = {},
    onOpenRecentTab: () -> Unit = {},
    onOpenBiliList: () -> Unit = {},
    onOpenRadio: () -> Unit = {},
    onOpenBiliLogin: () -> Unit = {},
    onStartRadio: () -> Unit = {},
    onNextRadio: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var chip by remember { mutableIntStateOf(0) }
    val chips = HomeChip.entries
    val selected = chips[chip.coerceIn(0, chips.lastIndex)]

    val liked = state.localPlaylists.firstOrNull { it.id == LikedStore.LIKED_ID }
    val otherLocals = state.localPlaylists.filter { it.id != LikedStore.LIKED_ID }
    val bili = state.playlists
    val recent = state.recent

    val showLibrary = selected == HomeChip.All || selected == HomeChip.Library
    val showBili = (selected == HomeChip.All || selected == HomeChip.Bili) && bili.isNotEmpty()
    val showRecent = selected == HomeChip.All || selected == HomeChip.Recent

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // 顶栏：头像 + 问候
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeAvatar(
                    avatarUrl = state.avatarUrl,
                    onClick = onOpenMe,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = state.greeting,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chips.forEachIndexed { i, c ->
                    HomeChipPill(
                        label = c.label,
                        selected = i == chip,
                        onClick = { chip = i },
                    )
                }
            }
        }

        // 2×2 快捷宫格
        item {
            QuickGrid(
                liked = liked,
                localCount = otherLocals.size,
                biliCount = bili.size,
                recentCount = recent.size,
                onOpenLiked = { liked?.let(onOpenPlaylist) },
                onOpenLibrary = {
                    // 打开第一个本地歌单，或喜欢
                    (otherLocals.firstOrNull() ?: liked)?.let(onOpenPlaylist)
                },
                onOpenBili = onOpenBiliList,
                onOpenRecent = onOpenRecentTab,
            )
            Spacer(Modifier.height(24.dp))
        }

        // 我的歌单横滑
        if (showLibrary && state.localPlaylists.isNotEmpty()) {
            item {
                SectionTitle(text = "我的歌单")
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.localPlaylists.forEach { pl ->
                        CoverPlaylistCard(playlist = pl, onClick = { onOpenPlaylist(pl) })
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        // B站收藏横滑
        if (showBili) {
            item {
                SectionTitle(text = "B站收藏")
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    bili.forEach { pl ->
                        CoverPlaylistCard(playlist = pl, onClick = { onOpenPlaylist(pl) })
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        // 最近播放
        if (showRecent) {
            item {
                SectionTitle(
                    text = "最近播放",
                    action = if (recent.isNotEmpty()) {
                        {
                            Text(
                                text = "清空",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clickable(onClick = onClearRecent)
                                    .padding(4.dp),
                            )
                        }
                    } else null,
                )
                Spacer(Modifier.height(8.dp))
                if (recent.isEmpty()) {
                    Text(
                        text = "听过的歌会出现在这里",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(recent.take(if (selected == HomeChip.Recent) 12 else 5), key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    onClick = { onPlayTrack(track, recent) },
                    onCollect = { onCollectTrack(track) },
                    onRemove = { onRemoveRecent(track.id) },
                )
            }
        }

        item {
            Spacer(
                Modifier.height(
                    if (isLiquidTheme()) LocalLiquidChromeBottom.current else 88.dp,
                ),
            )
        }
    }
}

@Composable
private fun HomeChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surface
    val fg = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
private fun QuickGrid(
    liked: Playlist?,
    localCount: Int,
    biliCount: Int,
    recentCount: Int,
    onOpenLiked: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenBili: () -> Unit,
    onOpenRecent: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            QuickCell(
                title = "我的喜欢",
                subtitle = "${liked?.trackCount ?: 0} 首",
                icon = Icons.Filled.Favorite,
                onClick = onOpenLiked,
                modifier = Modifier.weight(1f),
            )
            QuickCell(
                title = "我的歌单",
                subtitle = if (localCount > 0) "$localCount 个" else "创建",
                icon = Icons.Outlined.LibraryMusic,
                onClick = onOpenLibrary,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            QuickCell(
                title = "B站收藏",
                subtitle = if (biliCount > 0) "$biliCount 个" else "未同步",
                icon = Icons.Outlined.Folder,
                onClick = onOpenBili,
                modifier = Modifier.weight(1f),
            )
            QuickCell(
                title = "最近播放",
                subtitle = if (recentCount > 0) "$recentCount 首" else "暂无",
                icon = Icons.Outlined.History,
                onClick = onOpenRecent,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HomeAvatar(
    avatarUrl: String?,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val loader = remember { MadusImageLoader.get(context) }
    val url = normalizeCoverUrl(avatarUrl)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(160)
                    .build(),
                contentDescription = "我的",
                imageLoader = loader,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
            )
        } else {
            Icon(
                Icons.Outlined.Person,
                contentDescription = "我的",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun QuickCell(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(if (isLiquidTheme()) 12.dp else 6.dp))
            .then(
                if (isLiquidTheme()) {
                    Modifier.background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.22f))
                } else {
                    Modifier
                },
            )
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(if (isLiquidTheme()) 12.dp else 6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CoverPlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
    ) {
        CoverArt(coverUrl = playlist.coverUrl, size = 140.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            text = playlist.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${playlist.trackCount} 首",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
