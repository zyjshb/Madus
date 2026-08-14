package com.madus.mobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.madus.mobile.data.AppearanceMode
import com.madus.mobile.domain.PlaybackState
import com.madus.mobile.domain.Track
import com.madus.mobile.ui.theme.appearanceTokens

@Composable
fun LineFrame(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 12.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val tokens = appearanceTokens()
    val shape = RoundedCornerShape(tokens.cornerMd)
    val border = BorderStroke(
        tokens.borderWidth,
        MaterialTheme.colorScheme.outline.copy(
            alpha = if (tokens.mode == AppearanceMode.SoftGlass) 0.22f else 1f,
        ),
    )
    // 圆滑：用 surfaceVariant 圆角面板，避免 elevation 白方块 + 同色半透明糊成一片
    val bg = if (tokens.mode == AppearanceMode.SoftGlass) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = tokens.panelAlpha)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = tokens.panelAlpha)
    }
    val base = modifier
        .then(
            if (tokens.cardElevation > 0.dp) {
                Modifier.shadow(tokens.cardElevation, shape, clip = false)
            } else {
                Modifier
            },
        )
        .clip(shape)
        .border(border, shape)
        .background(bg)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(contentPadding)
    Box(modifier = base) { content() }
}

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        action?.invoke()
    }
}

@Composable
fun CoverPlaceholder(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val tokens = appearanceTokens()
    val shape = RoundedCornerShape(tokens.cornerSm)
    val sized = if (size > 0.dp) modifier.size(size) else modifier
    // 线稿：浅底；圆滑：透明底 + 细描边，避免大白方块
    val fill = if (tokens.mode == AppearanceMode.SoftGlass) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        modifier = sized
            .clip(shape)
            .border(
                tokens.borderWidth,
                MaterialTheme.colorScheme.outline.copy(
                    alpha = if (tokens.mode == AppearanceMode.SoftGlass) 0.28f else 1f,
                ),
                shape,
            )
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(if (size > 0.dp) size * 0.35f else 36.dp),
        )
    }
}

@Composable
fun TrackRow(
    track: Track,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onCollect: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onArtistClick: (() -> Unit)? = null,
    isCurrent: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(coverUrl = track.coverUrl, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist.ifBlank { track.source.displayName },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (onArtistClick != null && track.artist.isNotBlank()) {
                    Modifier.clickable(onClick = onArtistClick)
                } else {
                    Modifier
                },
            )
        }
        if (trailing != null) {
            trailing()
        } else {
            if (onCollect != null) {
                IconButton(onClick = onCollect) {
                    Icon(
                        imageVector = Icons.Default.PlaylistAdd,
                        contentDescription = "加入歌单",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "从最近移除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun MiniPlayerBar(
    playback: PlaybackState,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit = {},
    onOpenNowPlaying: () -> Unit,
    onOpenQueue: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val track = playback.current ?: return
    val switching = playback.isLoading && !playback.isPlaying
    LineFrame(
        modifier = modifier.fillMaxWidth(),
        contentPadding = 0.dp,
        onClick = onOpenNowPlaying,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverArt(coverUrl = track.coverUrl, size = 44.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onToggle, enabled = !switching) {
                if (switching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playback.isPlaying) "暂停" else "播放",
                    )
                }
            }
            IconButton(onClick = onOpenQueue) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.QueueMusic,
                    contentDescription = "播放列表",
                )
            }
        }
    }
}

@Composable
fun LineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
) {
    val tokens = appearanceTokens()
    val shape = RoundedCornerShape(tokens.cornerSm)
    val bg = if (filled) {
        MaterialTheme.colorScheme.primary
    } else if (tokens.mode == AppearanceMode.SoftGlass) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }
    val fg = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .then(
                if (tokens.cardElevation > 0.dp) {
                    Modifier.shadow(tokens.cardElevation, shape, clip = false)
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .border(
                tokens.borderWidth,
                MaterialTheme.colorScheme.outline.copy(
                    alpha = if (tokens.mode == AppearanceMode.SoftGlass && !filled) 0.22f else 1f,
                ),
                shape,
            )
            .background(bg.copy(alpha = if (filled) 1f else tokens.panelAlpha))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) fg else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
