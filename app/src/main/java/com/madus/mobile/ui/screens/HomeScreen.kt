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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.madus.mobile.domain.Playlist
import com.madus.mobile.domain.Track
import com.madus.mobile.ui.HomeUiState
import com.madus.mobile.ui.components.CoverArt
import com.madus.mobile.ui.components.MadusImageLoader
import com.madus.mobile.ui.components.TrackRow
import com.madus.mobile.ui.components.normalizeCoverUrl
import com.madus.mobile.ui.liquid.LocalLiquidChromeBottom
import com.madus.mobile.ui.theme.LiquidType
import com.madus.mobile.ui.theme.isLiquidTheme

private enum class HomeChip(val label: String) {
    All("全部"),
    Library("音乐库"),
    Bili("B站"),
    Recent("最近"),
}

private val HomeCoverSize = 156.dp

/**
 * iOS 26 / Apple Music Listen Now：
 * 大标题 + 头像 → 胶囊筛选 → 横向封面货架 → 最近列表。
 * 不再放快捷宫格/细栏，喜欢和 B 站收藏走货架本身。
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
    onOpenLibrary: () -> Unit = {},
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

    val bili = state.playlists
    val recent = state.recent
    val liquid = isLiquidTheme()

    val showLibrary = selected == HomeChip.All || selected == HomeChip.Library
    val showBili = selected == HomeChip.All || selected == HomeChip.Bili
    val showRecent = selected == HomeChip.All || selected == HomeChip.Recent

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.greeting,
                    style = if (liquid) {
                        LiquidType.largeTitle
                    } else {
                        MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.4).sp,
                        )
                    },
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(12.dp))
                HomeAvatar(
                    avatarUrl = state.avatarUrl,
                    onClick = onOpenMe,
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 22.dp),
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

        if (showLibrary && state.localPlaylists.isNotEmpty()) {
            item {
                HomeSection(
                    title = "我的歌单",
                    action = "全部",
                    onAction = onOpenLibrary,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    state.localPlaylists.forEach { pl ->
                        CoverPlaylistCard(playlist = pl, onClick = { onOpenPlaylist(pl) })
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }

        if (showBili) {
            item {
                if (bili.isNotEmpty()) {
                    HomeSection(
                        title = "B站收藏",
                        action = "全部",
                        onAction = onOpenBiliList,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        bili.forEach { pl ->
                            CoverPlaylistCard(playlist = pl, onClick = { onOpenPlaylist(pl) })
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                } else if (selected == HomeChip.Bili) {
                    HomeSection(title = "B站收藏")
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (state.biliLoggedIn) {
                            "还没有收藏夹"
                        } else {
                            "登录后，收藏夹会出现在这里"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .then(
                                if (!state.biliLoggedIn) {
                                    Modifier.clickable(onClick = onOpenBiliLogin)
                                } else {
                                    Modifier
                                },
                            ),
                    )
                    Spacer(Modifier.height(28.dp))
                }
            }
        }

        if (showRecent) {
            item {
                HomeSection(
                    title = "最近播放",
                    action = if (recent.isNotEmpty()) "清空" else null,
                    onAction = if (recent.isNotEmpty()) onClearRecent else null,
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
                    if (liquid) LocalLiquidChromeBottom.current else 88.dp,
                ),
            )
        }
    }
}

@Composable
private fun HomeSection(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = if (isLiquidTheme()) {
                LiquidType.title3
            } else {
                MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            },
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (!action.isNullOrBlank() && onAction != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onAction)
                    .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
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
    val shape = RoundedCornerShape(999.dp)
    val bg = when {
        selected -> MaterialTheme.colorScheme.onBackground
        isLiquidTheme() -> androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val fg = if (selected) {
        MaterialTheme.colorScheme.background
    } else {
        MaterialTheme.colorScheme.onBackground
    }
    Box(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
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
            .then(
                if (isLiquidTheme()) {
                    Modifier.background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.14f))
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                },
            )
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
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onBackground,
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
            .width(HomeCoverSize)
            .clickable(onClick = onClick),
    ) {
        CoverArt(coverUrl = playlist.coverUrl, size = HomeCoverSize)
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
