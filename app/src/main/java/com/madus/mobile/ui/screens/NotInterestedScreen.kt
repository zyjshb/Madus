package com.madus.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.madus.mobile.domain.Track
import com.madus.mobile.ui.components.TrackRow
import com.madus.mobile.ui.liquid.LiquidPageHeader
import com.madus.mobile.ui.liquid.LocalLiquidChromeBottom
import com.madus.mobile.ui.theme.isLiquidTheme

@Composable
fun NotInterestedScreen(
    tracks: List<Track>,
    onBack: () -> Unit,
    onUndo: (Track) -> Unit,
    onPlay: (Track) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (isLiquidTheme()) {
            LiquidPageHeader(
                title = "不喜欢",
                subtitle = if (tracks.isEmpty()) "还没有" else "${tracks.size} 首",
                onBack = onBack,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("不喜欢", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = if (tracks.isEmpty()) "还没有" else "${tracks.size} 首",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (tracks.isEmpty()) {
            Spacer(Modifier.height(16.dp))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = if (isLiquidTheme()) LocalLiquidChromeBottom.current else 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(tracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        onClick = { onPlay(track) },
                        trailing = {
                            TextButton(onClick = { onUndo(track) }) {
                                Text("取消")
                            }
                        },
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}
