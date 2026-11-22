package com.madus.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.madus.mobile.MadusApp
import com.madus.mobile.data.AppearanceMode
import com.madus.mobile.data.BilibiliApi
import com.madus.mobile.domain.MusicSourceType
import com.madus.mobile.domain.Track
import com.madus.mobile.ui.components.CoverArt
import com.madus.mobile.ui.components.TrackRow
import com.madus.mobile.ui.theme.appearanceTokens
import com.madus.mobile.ui.theme.iosClickable
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TogetherPickSongSheet(
    recentTracks: List<Track>,
    onPick: (Track) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val tokens = appearanceTokens()
    val app = MadusApp.instance

    // 0 = B站搜索, 1 = 我喜欢, 2 = 最近听过, 3 = B站收藏夹
    var tabIndex by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Track>>(emptyList()) }
    var searchLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    var likedTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var favFolders by remember { mutableStateOf<List<BilibiliApi.FavFolder>>(emptyList()) }
    var selectedFolder by remember { mutableStateOf<BilibiliApi.FavFolder?>(null) }
    var folderTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var folderLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        likedTracks = runCatching { app.likedStore.tracks() }
            .getOrDefault(emptyList())
            .filter { it.source == MusicSourceType.BILIBILI && it.bvid.isNotBlank() }
        favFolders = runCatching { app.biliApi.favFolders() }.getOrDefault(emptyList())
    }

    fun runSearch(raw: String) {
        val clean = raw.trim()
        if (clean.isEmpty()) return
        keyboard?.hide()
        searchLoading = true
        searchError = null
        scope.launch {
            val bv = BilibiliApi.parseBvid(clean)
            val list = runCatching {
                if (bv != null) {
                    val parts = app.biliApi.listArchiveParts(bv)
                    parts.ifEmpty { app.biliApi.search(clean, 24) }
                } else {
                    app.biliApi.search(clean, 24)
                }
            }.getOrElse { err ->
                searchError = err.message ?: "搜索失败"
                emptyList()
            }
            searchResults = list.filter { it.bvid.isNotBlank() }
            searchLoading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.84f)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "B 站选歌 / 选视频",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "支持搜索歌名、UP主或直接粘贴 BV 号 / B站链接",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            val tabs = listOf("B站搜索", "我喜欢", "最近听过", "收藏夹")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tabs.forEachIndexed { idx, label ->
                    val selected = tabIndex == idx
                    val shape = RoundedCornerShape(tokens.cornerSm)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(shape)
                            .border(
                                width = tokens.borderWidth,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(
                                        alpha = if (tokens.mode == AppearanceMode.SoftGlass) 0.25f else 0.7f,
                                    )
                                },
                                shape = shape,
                            )
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            )
                            .iosClickable {
                                tabIndex = idx
                                selectedFolder = null
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (tabIndex == 0) {
                val inputShape = RoundedCornerShape(tokens.cornerSm)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(inputShape)
                        .border(
                            tokens.borderWidth,
                            MaterialTheme.colorScheme.outline.copy(
                                alpha = if (tokens.mode == AppearanceMode.SoftGlass) 0.28f else 0.9f,
                            ),
                            inputShape,
                        )
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { runSearch(query) }),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text(
                                    "输入歌名 / 关键词 / BV 号…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(tokens.cornerXs))
                            .background(MaterialTheme.colorScheme.primary)
                            .iosClickable { runSearch(query) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            when (tabIndex) {
                0 -> {
                    when {
                        searchLoading -> Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }

                        searchError != null -> Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                searchError.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        searchResults.isEmpty() -> Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "搜一首 B 站歌曲或视频，马上一起听",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        else -> PickerTrackList(searchResults) { t ->
                            onPick(t)
                            onDismiss()
                        }
                    }
                }

                1 -> {
                    if (likedTracks.isEmpty()) {
                        EmptyHint("「我喜欢」里暂无 B 站曲目")
                    } else {
                        PickerTrackList(likedTracks) { t ->
                            onPick(t)
                            onDismiss()
                        }
                    }
                }

                2 -> {
                    val biliRecent = remember(recentTracks) {
                        recentTracks.filter { it.source == MusicSourceType.BILIBILI && it.bvid.isNotBlank() }
                    }
                    if (biliRecent.isEmpty()) {
                        EmptyHint("最近播放暂无 B 站曲目")
                    } else {
                        PickerTrackList(biliRecent) { t ->
                            onPick(t)
                            onDismiss()
                        }
                    }
                }

                3 -> {
                    val curFolder = selectedFolder
                    if (curFolder == null) {
                        if (favFolders.isEmpty()) {
                            EmptyHint("登录后可查看你的 B 站收藏夹")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 24.dp),
                            ) {
                                items(favFolders, key = { it.id }) { folder ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedFolder = folder
                                                folderLoading = true
                                                folderTracks = emptyList()
                                                scope.launch {
                                                    folderTracks = runCatching {
                                                        app.biliApi.favTracks(folder.id, maxPages = 5)
                                                    }.getOrDefault(emptyList()).filter { it.bvid.isNotBlank() }
                                                    folderLoading = false
                                                }
                                            }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CoverArt(coverUrl = folder.cover.takeIf { it.isNotBlank() }, size = 48.dp)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                folder.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                "${folder.count} 个内容",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    )
                                }
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                IconButton(onClick = { selectedFolder = null }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回收藏夹")
                                }
                                Text(
                                    curFolder.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (folderLoading) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else if (folderTracks.isEmpty()) {
                                EmptyHint("该收藏夹暂无可用内容")
                            } else {
                                PickerTrackList(folderTracks) { t ->
                                    onPick(t)
                                    onDismiss()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(msg: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            msg,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PickerTrackList(
    tracks: List<Track>,
    onSelect: (Track) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(tracks.size, key = { idx -> "${tracks[idx].id}#$idx" }) { idx ->
            val track = tracks[idx]
            TrackRow(
                track = track,
                onClick = { onSelect(track) },
                trailing = {
                    Text(
                        "点播",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
        }
    }
}
