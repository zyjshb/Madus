package com.madus.mobile.ui.liquid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.madus.mobile.data.LikedStore
import com.madus.mobile.domain.Playlist
import com.madus.mobile.domain.Track
import com.madus.mobile.ui.HomeUiState
import com.madus.mobile.ui.theme.CanvasGold
import com.madus.mobile.ui.theme.LiquidType

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
    onOpenBiliLogin: () -> Unit = {},
    onStartRadio: () -> Unit = {},
    onNextRadio: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val recent = state.recent
    val shelfPlaylists = state.localPlaylists.filter { it.id != LikedStore.LIKED_ID }
    val bili = state.playlists
    val paper = MaterialTheme.colorScheme.onSurface
    val mute = paper.copy(alpha = 0.58f)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 22.dp,
            end = 22.dp,
            top = 18.dp,
            bottom = LocalLiquidChromeBottom.current,
        ),
    ) {
        item {
            Text(
                state.greeting,
                style = LiquidType.largeTitle,
                color = paper,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (state.biliLoggedIn) "在这里接着听，或开一条电台。" else "登录后同步收藏。先去电台，或搜一首。",
                style = LiquidType.subhead,
                color = mute,
            )
            Spacer(Modifier.height(28.dp))
        }

        item {
            StageCard(
                badge = "私人 FM",
                title = if (recent.isNotEmpty()) recent.first().title else "为你连播",
                subtitle = if (recent.isNotEmpty()) {
                    recent.first().artist.ifBlank { "点开就开始一条电台" }
                } else {
                    "根据听过的歌连续推荐"
                },
                coverUrl = recent.firstOrNull()?.coverUrl,
                live = true,
                secondaryLabel = "换台",
                onSecondary = onNextRadio,
                onPlay = onStartRadio,
            )
            Spacer(Modifier.height(26.dp))
        }

        if (recent.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        CanvasKicker("最近")
                        Spacer(Modifier.height(4.dp))
                        Text("每日推荐歌曲", style = LiquidType.title3, color = paper)
                    }
                    LiquidSeeAll(onOpenRecentTab)
                }
                Spacer(Modifier.height(8.dp))
            }
            items(recent.size.coerceAtMost(8), key = { recent[it].id }) { i ->
                val track = recent[i]
                CanvasNumberedTrack(
                    index = i + 1,
                    track = track,
                    onClick = { onPlayTrack(track, recent) },
                )
            }
            item { Spacer(Modifier.height(22.dp)) }
        }

        if (shelfPlaylists.isNotEmpty()) {
            item {
                CanvasKicker("资料库")
                Spacer(Modifier.height(4.dp))
                Text("我的歌单", style = LiquidType.title3, color = paper)
                Spacer(Modifier.height(12.dp))
                LiquidMusicShelf {
                    shelfPlaylists.forEach { pl ->
                        LiquidShelfCard(pl.title, "${pl.trackCount} 首", pl.coverUrl) {
                            onOpenPlaylist(pl)
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
            }
        }

        if (state.biliLoggedIn && bili.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        CanvasKicker("B 站")
                        Spacer(Modifier.height(4.dp))
                        Text("收藏夹", style = LiquidType.title3, color = paper)
                    }
                    LiquidSeeAll(onOpenBiliList)
                }
                Spacer(Modifier.height(12.dp))
                LiquidMusicShelf {
                    bili.forEach { pl ->
                        LiquidShelfCard(pl.title, "${pl.trackCount} 首", pl.coverUrl) {
                            onOpenPlaylist(pl)
                        }
                    }
                }
            }
        } else if (!state.biliLoggedIn) {
            item {
                Text(
                    "登录 B 站同步收藏",
                    style = LiquidType.subhead,
                    color = CanvasGold,
                    modifier = Modifier
                        .clickable(onClick = onOpenBiliLogin)
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}
