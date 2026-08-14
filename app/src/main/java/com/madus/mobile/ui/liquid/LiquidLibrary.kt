package com.madus.mobile.ui.liquid

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.madus.mobile.domain.Playlist
import com.madus.mobile.domain.Track
import com.madus.mobile.ui.LibraryUiState
import com.madus.mobile.ui.components.CoverArt
import com.madus.mobile.ui.theme.LiquidType
import com.madus.mobile.ui.theme.liquidTokens

@Composable
fun LiquidLibraryScreen(
    state: LibraryUiState,
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenRecent: () -> Unit,
    onOpenBiliList: () -> Unit,
    onOpenCache: () -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onCollectTrack: (Track) -> Unit = {},
    onRemoveRecent: (String) -> Unit = {},
    onLoginBili: () -> Unit = {},
    onImportPlaylist: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showCreate by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf("") }
    val liked = state.liked
    val locals = state.localPlaylists
    val bili = state.biliPlaylists
    val recent = state.recent
    val tokens = liquidTokens()
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 12.dp,
                bottom = LocalLiquidChromeBottom.current,
            ),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        "曲库",
                        style = LiquidType.largeTitle,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "导入",
                        style = LiquidType.footnote,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clickable(onClick = onImportPlaylist)
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                    Text(
                        "新建",
                        style = LiquidType.footnote,
                        color = tokens.accent,
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clickable {
                                createName = ""
                                showCreate = true
                            }
                            .padding(start = 4.dp, top = 12.dp, bottom = 12.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                LiquidMusicShelf(gap = 12.dp) {
                    LiquidPinTile(
                        title = "喜欢",
                        subtitle = "${liked?.trackCount ?: 0} 首",
                        coverUrl = liked?.coverUrl,
                        fallback = Icons.Outlined.FavoriteBorder,
                        onClick = { liked?.let(onOpenPlaylist) },
                    )
                    LiquidPinTile(
                        title = "最近",
                        subtitle = if (recent.isEmpty()) "—" else "${recent.size} 首",
                        coverUrl = recent.firstOrNull()?.coverUrl,
                        fallback = Icons.Outlined.Album,
                        onClick = onOpenRecent,
                    )
                    LiquidPinTile(
                        title = "B 站",
                        subtitle = when {
                            !state.biliLoggedIn -> "登录"
                            bili.isEmpty() -> "—"
                            else -> "${bili.size} 个"
                        },
                        coverUrl = bili.firstOrNull()?.coverUrl,
                        fallback = Icons.Outlined.Album,
                        onClick = { if (!state.biliLoggedIn) onLoginBili() else onOpenBiliList() },
                    )
                    LiquidPinTile(
                        title = "缓存",
                        subtitle = if (state.offlineCount > 0) "${state.offlineCount}" else "管理",
                        coverUrl = null,
                        fallback = Icons.Outlined.CloudDownload,
                        onClick = onOpenCache,
                    )
                }
                Spacer(Modifier.height(26.dp))
            }

            item {
                LiquidSectionLabel("我的歌单")
                if (locals.isEmpty()) {
                    Text(
                        "还没有歌单",
                        style = LiquidType.subhead,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable {
                                createName = ""
                                showCreate = true
                            }
                            .padding(vertical = 8.dp),
                    )
                } else {
                    locals.forEach { pl ->
                        MusicPlaylistRow(pl) { onOpenPlaylist(pl) }
                    }
                }
                Spacer(Modifier.height(22.dp))
            }

            item {
                LiquidSectionLabel(
                    "B站收藏",
                    action = if (state.biliLoggedIn && bili.isNotEmpty()) {
                        { LiquidSeeAll(onOpenBiliList) }
                    } else {
                        null
                    },
                )
                when {
                    !state.biliLoggedIn -> Text(
                        "登录后同步收藏夹",
                        style = LiquidType.subhead,
                        color = tokens.accent,
                        modifier = Modifier
                            .clickable(onClick = onLoginBili)
                            .padding(vertical = 8.dp),
                    )
                    bili.isEmpty() -> Text(
                        "暂无收藏夹",
                        style = LiquidType.subhead,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> bili.take(6).forEach { pl ->
                        MusicPlaylistRow(pl) { onOpenPlaylist(pl) }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建歌单") },
            text = {
                BasicTextField(
                    value = createName,
                    onValueChange = { createName = it },
                    singleLine = true,
                    textStyle = LiquidType.body.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.6.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    decorationBox = { inner ->
                        if (createName.isEmpty()) {
                            Text("歌单名称", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreatePlaylist(createName.trim())
                        showCreate = false
                    },
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun MusicPlaylistRow(playlist: Playlist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            coverUrl = playlist.coverUrl,
            size = 64.dp,
            shape = RoundedCornerShape(10.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                playlist.title,
                style = LiquidType.body,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${playlist.trackCount} 首",
                style = LiquidType.footnote,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
