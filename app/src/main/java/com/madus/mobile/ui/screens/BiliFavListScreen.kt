package com.madus.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.madus.mobile.domain.Playlist
import com.madus.mobile.ui.components.CoverArt
import com.madus.mobile.ui.liquid.LiquidPageHeader
import com.madus.mobile.ui.theme.isLiquidTheme

/**
 * B站收藏夹列表：点宫格「B站收藏」进这里，再点具体夹。
 * （之前错误地直接打开第一个「默认收藏夹」）
 */
@Composable
fun BiliFavListScreen(
    playlists: List<Playlist>,
    onBack: () -> Unit,
    onOpen: (Playlist) -> Unit,
    onCleanAllInvalid: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (isLiquidTheme()) {
            LiquidPageHeader(
                title = "B站收藏",
                subtitle = if (playlists.isEmpty()) "暂无收藏夹" else "${playlists.size} 个收藏夹",
                onBack = onBack,
                action = if (onCleanAllInvalid != null && playlists.isNotEmpty()) {
                    {
                        TextButton(onClick = onCleanAllInvalid) { Text("清失效") }
                    }
                } else null,
            )
        } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("B站收藏", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = if (playlists.isEmpty()) "暂无收藏夹" else "${playlists.size} 个收藏夹",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onCleanAllInvalid != null && playlists.isNotEmpty()) {
                TextButton(onClick = onCleanAllInvalid) {
                    Text("清失效")
                }
            }
        }
        }

        if (playlists.isEmpty()) {
            Text(
                text = "登录 B 站后同步收藏夹",
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(playlists, key = { it.id }) { pl ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(pl) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoverArt(coverUrl = pl.coverUrl, size = 56.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pl.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${pl.trackCount} 首",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.padding(bottom = 72.dp)) }
            }
        }
    }
}
