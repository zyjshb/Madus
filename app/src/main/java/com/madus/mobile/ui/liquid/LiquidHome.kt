package com.madus.mobile.ui.liquid

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.madus.mobile.data.LikedStore
import com.madus.mobile.domain.Playlist
import com.madus.mobile.domain.Track
import com.madus.mobile.ui.HomeUiState
import com.madus.mobile.ui.components.CoverArt
import com.madus.mobile.ui.components.MadusImageLoader
import com.madus.mobile.ui.components.normalizeCoverUrl

@Composable
fun LiquidHomeScreen(
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
    modifier: Modifier = Modifier,
) {
    val bili = state.playlists
    val recent = state.recent

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 148.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.greeting,
                        style = MaterialTheme.typography.displaySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "封面滑着听",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LiquidHomeAvatar(avatarUrl = state.avatarUrl, onClick = onOpenMe)
            }
            Spacer(Modifier.height(20.dp))
        }

        if (recent.isNotEmpty()) {
            item {
                val first = recent.first()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayTrack(first, recent) },
                ) {
                    CoverArt(
                        coverUrl = first.coverUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        size = 0.dp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(first.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        first.artist.ifBlank { "最近在听" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(22.dp))
            }
        } else {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(onClick = onOpenRadio)
                        .padding(20.dp),
                ) {
                    Text("还没听过", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "去电台，或搜一首",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(22.dp))
            }
        }

        if (recent.size > 1) {
            item {
                LiquidSectionLabel(
                    "最近播放",
                    action = {
                        Text(
                            "全部",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(onClick = onOpenRecentTab),
                        )
                    },
                )
                LiquidShelf {
                    recent.drop(1).take(10).forEach { track ->
                        LiquidAlbumCard(track.title, track.artist, track.coverUrl, 148.dp) {
                            onPlayTrack(track, recent)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (state.localPlaylists.isNotEmpty()) {
            item {
                LiquidSectionLabel("我的歌单")
                LiquidShelf {
                    state.localPlaylists.forEach { pl ->
                        LiquidAlbumCard(pl.title, "${pl.trackCount} 首", pl.coverUrl) {
                            onOpenPlaylist(pl)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (bili.isNotEmpty()) {
            item {
                LiquidSectionLabel(
                    "B站收藏",
                    action = {
                        Text(
                            "全部",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(onClick = onOpenBiliList),
                        )
                    },
                )
                LiquidShelf {
                    bili.forEach { pl ->
                        LiquidAlbumCard(pl.title, "${pl.trackCount} 首", pl.coverUrl) {
                            onOpenPlaylist(pl)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidShelf(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) { content() }
}

@Composable
private fun LiquidHomeAvatar(avatarUrl: String?, onClick: () -> Unit) {
    val context = LocalContext.current
    val loader = remember { MadusImageLoader.get(context) }
    val url = normalizeCoverUrl(avatarUrl)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(url).crossfade(160).build(),
                contentDescription = "我的",
                imageLoader = loader,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
            )
        } else {
            Icon(Icons.Outlined.Person, contentDescription = "我的", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun LiquidAlbumCard(
    title: String,
    subtitle: String,
    coverUrl: String?,
    size: Dp = 156.dp,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(size)
            .clickable(onClick = onClick),
    ) {
        CoverArt(coverUrl = coverUrl, size = size)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
