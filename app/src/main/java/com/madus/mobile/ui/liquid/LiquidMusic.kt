package com.madus.mobile.ui.liquid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.madus.mobile.domain.Track
import com.madus.mobile.ui.components.CoverArt
import com.madus.mobile.ui.theme.CanvasGold
import com.madus.mobile.ui.theme.CanvasGoldSoft
import com.madus.mobile.ui.theme.LiquidType
import com.madus.mobile.ui.theme.liquidTokens

@Composable
fun LiquidSeeAll(onClick: () -> Unit) {
    Text(
        "查看全部",
        style = LiquidType.footnote,
        color = CanvasGoldSoft,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    )
}

@Composable
fun LiquidMusicShelf(
    gap: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) { content() }
}

@Composable
fun LiquidShelfCard(
    title: String,
    subtitle: String,
    coverUrl: String?,
    size: Dp = 148.dp,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(size)
            .clickable(onClick = onClick),
    ) {
        CoverArt(
            coverUrl = coverUrl,
            size = size,
            shape = RoundedCornerShape(10.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = LiquidType.subhead,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                style = LiquidType.footnote,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun CanvasOutlinePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, CanvasGold.copy(alpha = if (enabled) 0.72f else 0.28f), RoundedCornerShape(999.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = LiquidType.subhead,
            color = CanvasGoldSoft.copy(alpha = if (enabled) 1f else 0.45f),
        )
    }
}

@Composable
fun CanvasKicker(text: String) {
    Text(
        text,
        style = LiquidType.caption,
        color = CanvasGold.copy(alpha = 0.78f),
    )
}

fun formatTrackDuration(ms: Long): String {
    if (ms <= 0L) return ""
    val total = (ms / 1000).toInt()
    val m = total / 60
    val s = total % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

@Composable
fun CanvasNumberedTrack(
    index: Int,
    track: Track,
    onClick: () -> Unit,
    trailing: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            index.toString().padStart(2, '0'),
            style = LiquidType.footnote,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
            modifier = Modifier.width(28.dp),
        )
        CoverArt(coverUrl = track.coverUrl, size = 48.dp, shape = RoundedCornerShape(8.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                style = LiquidType.body,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                style = LiquidType.footnote,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val time = trailing ?: formatTrackDuration(track.durationMs)
        if (time.isNotBlank()) {
            Text(
                time,
                style = LiquidType.footnote,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
            )
        }
    }
}

@Composable
fun LiquidPinTile(
    title: String,
    subtitle: String,
    coverUrl: String?,
    fallback: ImageVector,
    onClick: () -> Unit,
    size: Dp = 104.dp,
) {
    Column(
        modifier = Modifier
            .width(size)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.28f))
                .border(0.5.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (!coverUrl.isNullOrBlank()) {
                CoverArt(coverUrl = coverUrl, size = size, shape = RoundedCornerShape(10.dp))
            } else {
                Icon(fallback, contentDescription = null, tint = CanvasGoldSoft, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = LiquidType.subhead,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            style = LiquidType.footnote,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 16:9 封面舞台：整卡可点，中间播放，底下叠字。 */
@Composable
fun StageCard(
    badge: String,
    title: String,
    subtitle: String,
    coverUrl: String?,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    playing: Boolean = false,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(shape)
            .clickable(onClick = onPlay),
    ) {
        CoverArt(
            coverUrl = coverUrl,
            size = 0.dp,
            modifier = Modifier.fillMaxSize(),
            shape = shape,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.12f),
                        0.45f to Color.Black.copy(alpha = 0.20f),
                        1f to Color.Black.copy(alpha = 0.78f),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (live) Color(0xFFE24B4B) else Color.White.copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    badge,
                    style = LiquidType.caption,
                    color = Color.White,
                )
            }
            if (!secondaryLabel.isNullOrBlank() && onSecondary != null) {
                Text(
                    secondaryLabel,
                    style = LiquidType.caption,
                    color = Color.White.copy(alpha = 0.82f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(onClick = onSecondary)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = if (playing) "打开播放页" else "播放",
                tint = Color.Black,
                modifier = Modifier.size(36.dp),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Text(
                title,
                style = LiquidType.title2,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = LiquidType.subhead,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 电台墙：封面铺满，底下压歌名。 */
@Composable
fun MosaicTile(
    title: String,
    subtitle: String,
    coverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        CoverArt(
            coverUrl = coverUrl,
            size = 0.dp,
            modifier = Modifier.fillMaxSize(),
            shape = shape,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Black.copy(alpha = 0.08f),
                        1f to Color.Black.copy(alpha = 0.72f),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
        ) {
            Text(
                title,
                style = LiquidType.subhead,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = LiquidType.caption,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
