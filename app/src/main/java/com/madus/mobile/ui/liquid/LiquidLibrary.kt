package com.madus.mobile.ui.liquid

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 148.dp),
    ) {
        item {
            Text("曲库", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(18.dp))
            LiquidSectionLabel("钉选")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LiquidPin(
                    title = "喜欢",
                    subtitle = "${liked?.trackCount ?: 0} 首",
                    coverUrl = liked?.coverUrl,
                    onClick = { liked?.let(onOpenPlaylist) },
                )
                LiquidPin(
                    title = "最近",
                    subtitle = if (recent.isEmpty()) "—" else "${recent.size} 首",
                    coverUrl = recent.firstOrNull()?.coverUrl,
                    onClick = onOpenRecent,
                )
                LiquidPin(
                    title = "B站",
                    subtitle = when {
                        !state.biliLoggedIn -> "登录"
                        bili.isEmpty() -> "—"
                        else -> "${bili.size} 个"
                    },
                    coverUrl = bili.firstOrNull()?.coverUrl,
                    onClick = { if (!state.biliLoggedIn) onLoginBili() else onOpenBiliList() },
                )
                LiquidPin(
                    title = "缓存",
                    subtitle = if (state.offlineCount > 0) "${state.offlineCount}" else "管理",
                    coverUrl = null,
                    onClick = onOpenCache,
                )
            }
            Spacer(Modifier.height(22.dp))
        }

        item {
            LiquidSectionLabel(
                "我的歌单",
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            "导入",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable(onClick = onImportPlaylist),
                        )
                        Text(
                            "新建",
                            style = MaterialTheme.typography.labelLarge,
                            color = tokens.accent,
                            modifier = Modifier.clickable {
                                createName = ""
                                showCreate = true
                            },
                        )
                    }
                },
            )
            GlassGroup {
                if (locals.isEmpty()) {
                    LiquidNavRow("还没有歌单", "新建，或播放时收藏", onClick = {
                        createName = ""
                        showCreate = true
                    })
                } else {
                    locals.forEachIndexed { i, pl ->
                        LiquidPlaylistRow(pl) { onOpenPlaylist(pl) }
                        if (i != locals.lastIndex) GlassDivider()
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        item {
            LiquidSectionLabel(
                "B站收藏",
                action = if (bili.isNotEmpty()) {
                    {
                        Text(
                            "全部",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable(onClick = onOpenBiliList),
                        )
                    }
                } else null,
            )
            GlassGroup {
                when {
                    !state.biliLoggedIn -> LiquidNavRow("登录后同步收藏夹", "一键听整夹", onClick = onLoginBili)
                    bili.isEmpty() -> LiquidNavRow("暂无收藏夹", "或还在同步", onClick = onOpenBiliList)
                    else -> {
                        bili.take(6).forEachIndexed { i, pl ->
                            LiquidPlaylistRow(pl) { onOpenPlaylist(pl) }
                            if (i != bili.take(6).lastIndex) GlassDivider()
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
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
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
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
private fun LiquidPin(
    title: String,
    subtitle: String,
    coverUrl: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(92.dp)
            .clickable(onClick = onClick),
    ) {
        CoverArt(coverUrl = coverUrl, size = 92.dp)
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LiquidPlaylistRow(playlist: Playlist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(coverUrl = playlist.coverUrl, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(playlist.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${playlist.trackCount} 首",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
