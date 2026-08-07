package com.madus.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.madus.mobile.domain.MusicSourceType
import com.madus.mobile.domain.Playlist
import com.madus.mobile.domain.Track
import com.madus.mobile.ui.BiliFavOption
import com.madus.mobile.ui.CollectTab

/**
 * 两个独立分页：
 * 1. 本地歌单 — 新建/加入本地
 * 2. B 站收藏 — 选夹后一键收藏（不强迫先建本地歌单）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectSheet(
    track: Track,
    playlists: List<Playlist>,
    tab: CollectTab,
    onTabChange: (CollectTab) -> Unit,
    onDismiss: () -> Unit,
    onAddTo: (playlistId: String) -> Unit,
    onCreateAndAdd: (title: String) -> Unit,
    /** 整部合集（多 P / 系列）加入本地 */
    onAddSeriesTo: (playlistId: String) -> Unit = {},
    onCreateSeriesAndAdd: (title: String) -> Unit = {},
    biliLoggedIn: Boolean = false,
    biliFolders: List<BiliFavOption> = emptyList(),
    selectedBiliFolderId: String? = null,
    biliSyncing: Boolean = false,
    onSelectBiliFolder: (String) -> Unit = {},
    onConfirmBiliCollect: () -> Unit = {},
    /** 整部合集同步 B 站 */
    onConfirmBiliSeriesCollect: () -> Unit = {},
    onLoginBili: () -> Unit = {},
    /** 新建 B 站收藏夹并加入当前曲 */
    onCreateBiliFolderAndCollect: (String) -> Unit = {},
    /** 打开收藏时探测到的合集集数；本地未登录也可整部收藏 */
    seriesCount: Int = 1,
) {
    // B 站稿件一律露出「整部合集」：未登录也能进本地歌单；探测失败时仍可点（自动再展开）
    val isBili = track.source == MusicSourceType.BILIBILI &&
        (track.bvid.isNotBlank() || track.id.startsWith("BV") || track.id.contains("bili", ignoreCase = true))
    val maybeSeries = isBili ||
        seriesCount > 1 ||
        track.pageCount > 1 ||
        track.album.startsWith("合集") ||
        track.title.contains("合集") ||
        Regex("""·\s*\d+P""", RegexOption.IGNORE_CASE).containsMatchIn(track.title)
    val seriesHint = when {
        seriesCount > 1 -> "约 $seriesCount 集"
        track.pageCount > 1 -> "约 ${track.pageCount}P"
        isBili -> "自动探测多P/系列"
        else -> null
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var creatingBili by remember { mutableStateOf(false) }
    var newBiliName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text("收藏", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${track.title} · ${track.artist}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            if (maybeSeries) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "整部合集${seriesHint?.let { "（$it）" } ?: ""}：本地歌单无需登录；同步 B 站需登录",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(12.dp))

            // 分页切换
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                    .padding(2.dp),
            ) {
                TabChip(
                    text = "本地歌单",
                    selected = tab == CollectTab.Local,
                    onClick = { onTabChange(CollectTab.Local) },
                    modifier = Modifier.weight(1f),
                )
                TabChip(
                    text = "B 站收藏",
                    selected = tab == CollectTab.Bili,
                    onClick = { onTabChange(CollectTab.Bili) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))

            when (tab) {
                CollectTab.Local -> LocalCollectPage(
                    playlists = playlists,
                    creating = creating,
                    newName = newName,
                    onCreatingChange = { creating = it },
                    onNewNameChange = { newName = it },
                    onAddTo = onAddTo,
                    onCreateAndAdd = onCreateAndAdd,
                    maybeSeries = maybeSeries,
                    onAddSeriesTo = onAddSeriesTo,
                    onCreateSeriesAndAdd = onCreateSeriesAndAdd,
                )
                CollectTab.Bili -> BiliCollectPage(
                    loggedIn = biliLoggedIn,
                    folders = biliFolders,
                    selectedId = selectedBiliFolderId,
                    syncing = biliSyncing,
                    creating = creatingBili,
                    newName = newBiliName,
                    onCreatingChange = { creatingBili = it },
                    onNewNameChange = { newBiliName = it },
                    onSelect = onSelectBiliFolder,
                    onConfirm = onConfirmBiliCollect,
                    onConfirmSeries = onConfirmBiliSeriesCollect,
                    maybeSeries = maybeSeries,
                    onLogin = onLoginBili,
                    onCreateAndCollect = onCreateBiliFolderAndCollect,
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun TabChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.background
    val fg = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(1.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
private fun LocalCollectPage(
    playlists: List<Playlist>,
    creating: Boolean,
    newName: String,
    onCreatingChange: (Boolean) -> Unit,
    onNewNameChange: (String) -> Unit,
    onAddTo: (String) -> Unit,
    onCreateAndAdd: (String) -> Unit,
    maybeSeries: Boolean = false,
    onAddSeriesTo: (String) -> Unit = {},
    onCreateSeriesAndAdd: (String) -> Unit = {},
) {
    if (!creating) {
        LineButton(
            text = if (maybeSeries) "+ 新建本地歌单（可整部合集）" else "+ 新建本地歌单并加入",
            onClick = { onCreatingChange(true) },
            filled = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (maybeSeries) {
            Spacer(Modifier.height(8.dp))
            LineButton(
                text = "一键新建并收藏整部合集（无需登录）",
                filled = true,
                onClick = { onCreateSeriesAndAdd("合集") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "无需登录 B 站：整部合集可进本地歌单。下方也可选已有歌单「整部合集」。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        val sorted = remember(playlists) { playlists.sortedByDescending { it.trackCount } }
        if (sorted.isEmpty()) {
            Text(
                "还没有本地歌单。点上面新建一个。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(260.dp),
            ) {
                items(sorted, key = { it.id }) { pl ->
                    LineFrame(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CoverArt(coverUrl = pl.coverUrl, size = 40.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pl.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        if (pl.trackCount == 0) "0 首" else "${pl.trackCount} 首",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LineButton(text = "只加本集", onClick = { onAddTo(pl.id) })
                                if (maybeSeries) {
                                    LineButton(
                                        text = "整部合集",
                                        filled = true,
                                        onClick = { onAddSeriesTo(pl.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        Text("歌单名称", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = newName,
            onValueChange = onNewNameChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                .padding(12.dp),
            decorationBox = { inner ->
                if (newName.isEmpty()) {
                    Text(
                        "例如：通勤 / 深夜 BGM",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            },
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LineButton(text = "取消", onClick = { onCreatingChange(false); onNewNameChange("") })
            LineButton(
                text = "创建并加入本集",
                filled = !maybeSeries,
                onClick = {
                    onCreateAndAdd(newName.trim().ifBlank { "收藏" })
                    onCreatingChange(false)
                    onNewNameChange("")
                },
            )
            if (maybeSeries) {
                LineButton(
                    text = "创建并整部",
                    filled = true,
                    onClick = {
                        onCreateSeriesAndAdd(newName.trim().ifBlank { "合集" })
                        onCreatingChange(false)
                        onNewNameChange("")
                    },
                )
            }
        }
    }
}

@Composable
private fun BiliCollectPage(
    loggedIn: Boolean,
    folders: List<BiliFavOption>,
    selectedId: String?,
    syncing: Boolean,
    creating: Boolean,
    newName: String,
    onCreatingChange: (Boolean) -> Unit,
    onNewNameChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onConfirm: () -> Unit,
    onConfirmSeries: () -> Unit = {},
    maybeSeries: Boolean = false,
    onLogin: () -> Unit,
    onCreateAndCollect: (String) -> Unit,
) {
    when {
        !loggedIn -> {
            Text(
                "登录 B 站后，可把当前曲收藏到任意收藏夹，也可新建夹。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            LineButton(text = "去登录 B 站", filled = true, onClick = onLogin, modifier = Modifier.fillMaxWidth())
        }
        creating -> {
            Text("新收藏夹名称（会同步到 B 站）", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = newName,
                onValueChange = onNewNameChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                    .padding(12.dp),
                decorationBox = { inner ->
                    if (newName.isEmpty()) {
                        Text(
                            "例如：Madus 短视频 / 通勤",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LineButton(
                    text = "取消",
                    onClick = {
                        onCreatingChange(false)
                        onNewNameChange("")
                    },
                )
                LineButton(
                    text = if (syncing) "创建中…" else "创建并收藏",
                    filled = true,
                    enabled = !syncing && newName.isNotBlank(),
                    onClick = {
                        onCreateAndCollect(newName.trim())
                    },
                )
            }
        }
        else -> {
            LineButton(
                text = "+ 新建 B 站收藏夹并加入",
                onClick = { onCreatingChange(true) },
                filled = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !syncing,
            )
            Spacer(Modifier.height(12.dp))
            if (folders.isEmpty()) {
                Text(
                    "还没有收藏夹。点上面新建一个，会同步到 B 站。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "或选择已有收藏夹",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)),
                ) {
                    items(folders, key = { it.id }) { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(folder.id) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedId == folder.id,
                                onClick = { onSelect(folder.id) },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(folder.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(
                                    "${folder.count} 首",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (syncing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(28.dp).width(28.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                } else {
                    LineButton(
                        text = "收藏本集到所选夹",
                        filled = true,
                        onClick = onConfirm,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !selectedId.isNullOrBlank(),
                    )
                    if (maybeSeries) {
                        Spacer(Modifier.height(8.dp))
                        LineButton(
                            text = "整部合集同步到所选夹",
                            onClick = onConfirmSeries,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !selectedId.isNullOrBlank() && !syncing,
                        )
                    }
                }
            }
        }
    }
}
