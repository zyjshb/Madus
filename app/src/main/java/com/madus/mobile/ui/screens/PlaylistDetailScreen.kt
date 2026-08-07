package com.madus.mobile.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.madus.mobile.domain.Track
import com.madus.mobile.ui.PlaylistDetailUiState
import com.madus.mobile.ui.components.CoverArt
import com.madus.mobile.ui.components.LineButton

/**
 * 汽水/Spotify：整页一个 LazyColumn，封面 header 跟着滚；仅顶栏钉死。
 * 管理动作在 ⋯。
 */
@Composable
fun PlaylistDetailScreen(
    state: PlaylistDetailUiState,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAll: () -> Unit = {},
    onRename: (String) -> Unit = {},
    onRemoveTrack: (String) -> Unit = {},
    onCollectTrack: (Track) -> Unit = {},
    onSetCover: (String) -> Unit = {},
    onDeletePlaylist: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onPrevPage: () -> Unit = {},
    onNextPage: () -> Unit = {},
    onOpenUp: (Track) -> Unit = {},
    isLocalPlaylist: Boolean = false,
    canChangeCover: Boolean = false,
    canRename: Boolean = false,
    canRemoveTrack: Boolean = false,
    canDeletePlaylist: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember(state.playlist?.title) {
        mutableStateOf(state.playlist?.title.orEmpty())
    }
    var headerMenu by remember { mutableStateOf(false) }
    var trackMenuId by remember { mutableStateOf<String?>(null) }

    val pickCover = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.toString()?.let { onSetCover(it) }
    }

    val hasManage = canRename || canChangeCover || canDeletePlaylist

    Column(modifier = modifier.fillMaxSize()) {
        // 仅顶栏钉死
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = state.playlist?.title ?: "歌单",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (hasManage) {
                Box {
                    IconButton(onClick = { headerMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(
                        expanded = headerMenu,
                        onDismissRequest = { headerMenu = false },
                    ) {
                        if (canRename) {
                            DropdownMenuItem(
                                text = { Text("改名") },
                                onClick = {
                                    headerMenu = false
                                    showRename = true
                                },
                            )
                        }
                        if (canChangeCover) {
                            DropdownMenuItem(
                                text = { Text("换封面") },
                                onClick = {
                                    headerMenu = false
                                    pickCover.launch("image/*")
                                },
                            )
                        }
                        if (canDeletePlaylist) {
                            DropdownMenuItem(
                                text = { Text("删除歌单") },
                                onClick = {
                                    headerMenu = false
                                    onDeletePlaylist()
                                },
                            )
                        }
                    }
                }
            }
        }

        when {
            state.isLoading -> {
                Text(
                    text = "加载中…",
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.error != null && state.tracks.isEmpty() -> {
                Text(
                    text = state.error ?: "",
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                // 封面 + 列表同一滚动（汽水式）
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    item(key = "header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CoverArt(
                                coverUrl = state.playlist?.coverUrl,
                                size = 148.dp,
                                modifier = if (canChangeCover) {
                                    Modifier.clickable { pickCover.launch("image/*") }
                                } else {
                                    Modifier
                                },
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = state.playlist?.title ?: "歌单",
                                style = MaterialTheme.typography.headlineMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = if (canRename) {
                                    Modifier.clickable { showRename = true }
                                } else {
                                    Modifier
                                },
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = buildString {
                                    if (state.total > 0) {
                                        append("第 ${state.page} 页 · 本页 ${state.tracks.size} 首")
                                        append(" · 共 ${state.total} 首")
                                    } else {
                                        append("${state.tracks.size} 首")
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))
                            if (state.tracks.isNotEmpty()) {
                                LineButton(
                                    text = "播放全部",
                                    onClick = onPlayAll,
                                    filled = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    itemsIndexed(state.tracks, key = { _, t -> t.id }) { index, track ->
                        PlaylistTrackRow(
                            index = index + 1,
                            track = track,
                            canRemove = canRemoveTrack,
                            menuOpen = trackMenuId == track.id,
                            onMenuOpen = { trackMenuId = track.id },
                            onMenuDismiss = { trackMenuId = null },
                            onClick = { onPlayTrack(track, state.tracks) },
                            onCollect = { onCollectTrack(track) },
                            onOpenUp = { onOpenUp(track) },
                            onRemove = {
                                trackMenuId = null
                                onRemoveTrack(track.id)
                            },
                        )
                    }
                    // 小说式翻页：上一页 / 下一页，当前页替换
                    if (state.total > 40 || state.hasMore || state.page > 1) {
                        item(key = "pager") {
                            val pageSize = 40
                            val totalPages = when {
                                state.total > 0 -> ((state.total + pageSize - 1) / pageSize).coerceAtLeast(1)
                                state.hasMore -> state.page + 1
                                else -> state.page.coerceAtLeast(1)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = onPrevPage,
                                    enabled = state.page > 1 && !state.loadingMore,
                                ) { Text("上一页") }
                                Text(
                                    text = if (state.loadingMore) {
                                        "加载中…"
                                    } else {
                                        "第 ${state.page} 页" +
                                            if (state.total > 0) " / 约 $totalPages 页 · ${state.total} 首"
                                            else ""
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(
                                    onClick = onNextPage,
                                    enabled = (state.hasMore || state.page < totalPages) && !state.loadingMore,
                                ) { Text("下一页") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("修改歌单名称") },
            text = {
                BasicTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                        .padding(12.dp),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(renameText)
                        showRename = false
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("取消") }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
private fun PlaylistTrackRow(
    index: Int,
    track: Track,
    canRemove: Boolean,
    menuOpen: Boolean,
    onMenuOpen: () -> Unit,
    onMenuDismiss: () -> Unit,
    onClick: () -> Unit,
    onCollect: () -> Unit,
    onOpenUp: () -> Unit = {},
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "%02d".format(index),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
        )
        CoverArt(coverUrl = track.coverUrl, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 单独点 UP，不与整行「播放」抢事件
            Text(
                text = track.artist.ifBlank { track.source.displayName },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .clickable(onClick = onOpenUp)
                    .padding(vertical = 2.dp),
            )
        }
        Box {
            IconButton(onClick = onMenuOpen, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "更多",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = onMenuDismiss) {
                DropdownMenuItem(
                    text = { Text("UP主页") },
                    onClick = {
                        onMenuDismiss()
                        onOpenUp()
                    },
                )
                DropdownMenuItem(
                    text = { Text("加入歌单") },
                    onClick = {
                        onMenuDismiss()
                        onCollect()
                    },
                )
                if (canRemove) {
                    DropdownMenuItem(
                        text = { Text("移出歌单") },
                        onClick = onRemove,
                    )
                }
            }
        }
    }
}
