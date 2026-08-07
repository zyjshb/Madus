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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.madus.mobile.data.TrackCacheStore
import com.madus.mobile.player.StreamCache
import com.madus.mobile.ui.CacheManagerUiState
import com.madus.mobile.ui.components.CoverArt
import com.madus.mobile.ui.theme.appearanceTokens

@Composable
fun CacheManagerScreen(
    state: CacheManagerUiState,
    onBack: () -> Unit,
    onRemove: (String) -> Unit,
    onClearOffline: () -> Unit,
    onClearStream: () -> Unit,
    onClearAll: () -> Unit,
    onPlay: (TrackCacheStore.CachedTrack) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tokens = appearanceTokens()
    val shape = RoundedCornerShape(tokens.cornerMd)

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("缓存管理", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "合计 ${state.totalLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .border(tokens.borderWidth, MaterialTheme.colorScheme.outline, shape)
                        .padding(14.dp),
                ) {
                    Text("占用", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "离线曲 ${state.items.size} 首 · 边听缓存 ${state.streamCacheLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onClearOffline) {
                            Text("清空离线曲")
                        }
                        TextButton(onClick = onClearStream) {
                            Text("清空边听")
                        }
                        TextButton(onClick = onClearAll) {
                            Text("全部清理")
                        }
                    }
                }
            }

            if (state.isLoading) {
                item {
                    Text(
                        "加载中…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else if (state.items.isEmpty()) {
                item {
                    Text(
                        "暂无手动缓存的歌曲\n播放时点「缓存」可保存到这里",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                item {
                    Text(
                        "已缓存歌曲",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(state.items, key = { it.track.id }) { item ->
                    CachedRow(
                        item = item,
                        onClick = { onPlay(item) },
                        onRemove = { onRemove(item.track.id) },
                    )
                }
            }
            item { Spacer(Modifier.height(48.dp)) }
        }
    }
}

@Composable
private fun CachedRow(
    item: TrackCacheStore.CachedTrack,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val tokens = appearanceTokens()
    val shape = RoundedCornerShape(tokens.cornerMd)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(tokens.borderWidth, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            coverUrl = item.track.coverUrl,
            modifier = Modifier.size(48.dp),
            size = 48.dp,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.track.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${item.track.artist} · ${StreamCache.formatSize(item.bytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
