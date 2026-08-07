package com.madus.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.madus.mobile.domain.Playlist

/**
 * Spotify-like「选择播放来源」— 换听什么歌单，不是加入歌单。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaySourceSheet(
    currentSourceLabel: String,
    sources: List<PlaySourceItem>,
    onDismiss: () -> Unit,
    onSelect: (PlaySourceItem) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
            Text("选择播放来源", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "当前：$currentSourceLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(360.dp),
            ) {
                items(sources, key = { it.id }) { item ->
                    val enabled = item.enabled
                    LineFrame(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = if (enabled) {
                            { onSelect(item) }
                        } else {
                            null
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (enabled) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (item.selected) {
                                Text("●", style = MaterialTheme.typography.labelLarge)
                            } else if (!enabled) {
                                Text("空", style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text("播放", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

data class PlaySourceItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: PlaySourceKind,
    val playlist: Playlist? = null,
    val enabled: Boolean = true,
    val selected: Boolean = false,
)

enum class PlaySourceKind {
    Recommend,
    Recent,
    LocalPlaylist,
    BiliFav,
}
