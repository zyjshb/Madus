package com.madus.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Seek bar with local scrubbing so ticker doesn't fight the drag.
 * 拖动时高亮当前时间，方便跳到「几分几秒」。
 */
@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val safeDuration = durationMs.coerceAtLeast(0L)
    val max = if (safeDuration > 0L) safeDuration.toFloat() else 1f
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }

    val displayPos = if (scrubbing) {
        scrubValue.roundToLong()
    } else {
        positionMs.coerceIn(0L, safeDuration.coerceAtLeast(0L))
    }
    val sliderValue = if (scrubbing) {
        scrubValue.coerceIn(0f, max)
    } else {
        (positionMs.toFloat() / max).coerceIn(0f, 1f) * max
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (scrubbing && safeDuration > 0L) {
            Text(
                text = "${formatPlaybackMs(displayPos)}  /  ${formatPlaybackMs(safeDuration)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 4.dp),
            )
        }
        Slider(
            value = if (safeDuration <= 0L) 0f else sliderValue,
            onValueChange = { v ->
                if (!enabled || safeDuration <= 0L) return@Slider
                scrubbing = true
                scrubValue = v.coerceIn(0f, max)
            },
            onValueChangeFinished = {
                if (scrubbing && safeDuration > 0L) {
                    onSeek(scrubValue.roundToLong().coerceIn(0L, safeDuration))
                }
                scrubbing = false
            },
            valueRange = 0f..max,
            enabled = enabled && safeDuration > 0L,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onSurface,
                activeTrackColor = MaterialTheme.colorScheme.onSurface,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                disabledThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledActiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatPlaybackMs(displayPos),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (scrubbing) FontWeight.Bold else FontWeight.Normal,
                color = if (scrubbing) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = if (safeDuration > 0L) formatPlaybackMs(safeDuration) else "--:--",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** 毫秒 → m:ss 或 h:mm:ss */
fun formatPlaybackMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}

/**
 * 抖音式细进度条：贴底可拖；
 * **始终**显示当前/总时长；拖动时上方大号预览「跳到几分几秒」。
 */
@Composable
fun ThinVideoProgress(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White.copy(alpha = 0.92f),
    trackColor: Color = Color.White.copy(alpha = 0.28f),
    timeColor: Color = Color.White.copy(alpha = 0.88f),
) {
    val safeDuration = durationMs.coerceAtLeast(0L)
    var scrubbing by remember { mutableStateOf(false) }
    var scrubRatio by remember { mutableFloatStateOf(0f) }
    val ratio = if (safeDuration <= 0L) {
        0f
    } else if (scrubbing) {
        scrubRatio
    } else {
        (positionMs.toFloat() / safeDuration).coerceIn(0f, 1f)
    }
    val displayPos = if (safeDuration <= 0L) {
        0L
    } else if (scrubbing) {
        (scrubRatio * safeDuration).toLong().coerceIn(0L, safeDuration)
    } else {
        positionMs.coerceIn(0L, safeDuration)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // 拖动预览：大号时间气泡
        if (scrubbing && safeDuration > 0L) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${formatPlaybackMs(displayPos)}  /  ${formatPlaybackMs(safeDuration)}",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .pointerInput(safeDuration) {
                    if (safeDuration <= 0L) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            scrubbing = true
                            scrubRatio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            scrubRatio = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            onSeek((scrubRatio * safeDuration).toLong().coerceIn(0L, safeDuration))
                            scrubbing = false
                        },
                        onDragCancel = { scrubbing = false },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (scrubbing) 5.dp else 2.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(trackColor),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ratio.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(activeColor),
                )
            }
        }

        // 时间节点：当前 · 总长（始终可见）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatPlaybackMs(displayPos),
                color = if (scrubbing) Color.White else timeColor,
                fontSize = if (scrubbing) 12.sp else 11.sp,
                fontWeight = if (scrubbing) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text = if (safeDuration > 0L) formatPlaybackMs(safeDuration) else "--:--",
                color = timeColor.copy(alpha = 0.75f),
                fontSize = 11.sp,
            )
        }
    }
}
