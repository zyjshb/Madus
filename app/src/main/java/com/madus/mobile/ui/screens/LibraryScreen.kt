package com.madus.mobile.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.madus.mobile.domain.Playlist
import com.madus.mobile.domain.Track
import com.madus.mobile.ui.LibraryUiState
import com.madus.mobile.ui.components.CoverArt
import com.madus.mobile.ui.components.SectionTitle
import com.madus.mobile.ui.components.TrackRow
import com.madus.mobile.ui.liquid.LiquidLibraryScreen
import com.madus.mobile.ui.theme.appearanceTokens
import com.madus.mobile.ui.theme.isLiquidTheme

/**
 * 曲库：我的喜欢 / 本地歌单 / B站收藏 / 最近播放 的一站式入口。
 */
@Composable
fun LibraryScreen(
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Text(
                text = "曲库",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append("${liked?.trackCount ?: 0} 喜欢")
                    append(" · ${locals.size} 歌单")
                    if (bili.isNotEmpty()) append(" · ${bili.size} 收藏夹")
                    if (recent.isNotEmpty()) append(" · ${recent.size} 最近")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
        }

        // 快捷入口 2×2
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    HubCell(
                        title = "我的喜欢",
                        subtitle = "${liked?.trackCount ?: 0} 首",
                        icon = Icons.Filled.Favorite,
                        onClick = { liked?.let(onOpenPlaylist) },
                        modifier = Modifier.weight(1f),
                    )
                    HubCell(
                        title = "最近播放",
                        subtitle = if (recent.isEmpty()) "暂无" else "${recent.size} 首",
                        icon = Icons.Outlined.History,
                        onClick = onOpenRecent,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    HubCell(
                        title = "B站收藏",
                        subtitle = when {
                            !state.biliLoggedIn -> "去登录"
                            bili.isEmpty() -> "暂无"
                            else -> "${bili.size} 个"
                        },
                        icon = Icons.Outlined.Folder,
                        onClick = {
                            if (!state.biliLoggedIn) onLoginBili() else onOpenBiliList()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    HubCell(
                        title = "离线缓存",
                        subtitle = if (state.offlineCount > 0) "${state.offlineCount} 首" else "管理",
                        icon = Icons.Outlined.CloudDownload,
                        onClick = onOpenCache,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // 本地歌单
        item {
            SectionTitle(
                text = "我的歌单",
                action = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "导入",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable(onClick = onImportPlaylist)
                                .padding(4.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable {
                                    createName = ""
                                    showCreate = true
                                }
                                .padding(4.dp),
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "新建歌单",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                "新建",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
        }

        if (locals.isEmpty()) {
            item {
                EmptyCreateCard(
                    title = "还没有本地歌单",
                    subtitle = "",
                    onClick = {
                        createName = ""
                        showCreate = true
                    },
                )
                Spacer(Modifier.height(20.dp))
            }
        } else {
            items(locals, key = { it.id }) { pl ->
                PlaylistRow(
                    playlist = pl,
                    onClick = { onOpenPlaylist(pl) },
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
        }

        // B站收藏预览
        item {
            SectionTitle(
                text = "B站收藏",
                action = if (bili.isNotEmpty()) {
                    {
                        Text(
                            text = "全部",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable(onClick = onOpenBiliList)
                                .padding(4.dp),
                        )
                    }
                } else null,
            )
            Spacer(Modifier.height(10.dp))
        }

        if (!state.biliLoggedIn) {
            item {
                EmptyCreateCard(
                    title = "登录 B 站同步收藏",
                    subtitle = "",
                    icon = Icons.Outlined.Folder,
                    actionLabel = "去登录",
                    onClick = onLoginBili,
                )
                Spacer(Modifier.height(20.dp))
            }
        } else if (bili.isEmpty()) {
            item {
                Text(
                    text = "暂无收藏夹，或同步中…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Spacer(Modifier.height(20.dp))
            }
        } else {
            items(bili.take(6), key = { "bili-${it.id}" }) { pl ->
                PlaylistRow(
                    playlist = pl,
                    onClick = { onOpenPlaylist(pl) },
                )
            }
            if (bili.size > 6) {
                item {
                    Text(
                        text = "查看全部 ${bili.size} 个收藏夹 →",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable(onClick = onOpenBiliList)
                            .padding(vertical = 12.dp),
                    )
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }

        // 最近播放
        item {
            SectionTitle(
                text = "最近播放",
                action = if (recent.isNotEmpty()) {
                    {
                        Text(
                            text = "全部",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable(onClick = onOpenRecent)
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
        items(recent.take(8), key = { "r-${it.id}" }) { track ->
            TrackRow(
                track = track,
                onClick = { onPlayTrack(track, recent) },
                onCollect = { onCollectTrack(track) },
                onRemove = { onRemoveRecent(track.id) },
            )
        }

        item {
            Spacer(
                Modifier.height(
                    if (isLiquidTheme()) {
                        com.madus.mobile.ui.liquid.LocalLiquidChromeBottom.current
                    } else {
                        88.dp
                    },
                ),
            )
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
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                        .padding(12.dp),
                    decorationBox = { inner ->
                        if (createName.isEmpty()) {
                            Text(
                                "歌单名称",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
private fun HubCell(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(58.dp)
            .border(
                appearanceTokens().borderWidth,
                MaterialTheme.colorScheme.outline,
                RoundedCornerShape(appearanceTokens().cornerSm),
            )
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
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
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
private fun PlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(coverUrl = playlist.coverUrl, size = 56.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${playlist.trackCount} 首",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Outlined.LibraryMusic,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyCreateCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: ImageVector = Icons.Outlined.PlaylistAdd,
    actionLabel: String = "新建歌单",
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            actionLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
