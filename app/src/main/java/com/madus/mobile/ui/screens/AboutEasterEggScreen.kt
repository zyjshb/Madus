package com.madus.mobile.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** 关于连点隐藏页：三档电风扇（轻量旋转动画）。 */
@Composable
fun AboutEasterEggScreen(
    version: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // 0=关，1/2/3=档
    var speed by remember { mutableIntStateOf(0) }
    val shape = RoundedCornerShape(8.dp)
    val drawAngle = fanSpinAngle(speed)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "电风扇",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "v$version",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val ink = colors.onBackground
            val outline = colors.outline
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(
                    modifier = Modifier
                        .size(220.dp)
                        .rotate(drawAngle),
                ) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val r = size.minDimension * 0.42f
                    drawCircle(
                        color = outline,
                        radius = r,
                        center = Offset(cx, cy),
                        style = Stroke(width = 3.dp.toPx()),
                    )
                    for (i in 0 until 3) {
                        rotate(degrees = i * 120f, pivot = Offset(cx, cy)) {
                            val path = Path().apply {
                                moveTo(cx, cy)
                                val a1 = -28f * (Math.PI.toFloat() / 180f)
                                val a2 = 28f * (Math.PI.toFloat() / 180f)
                                lineTo(cx + r * 0.92f * cos(a1), cy + r * 0.92f * sin(a1))
                                quadraticTo(
                                    cx + r * 1.05f,
                                    cy,
                                    cx + r * 0.92f * cos(a2),
                                    cy + r * 0.92f * sin(a2),
                                )
                                close()
                            }
                            drawPath(path, ink, style = Stroke(width = 2.5.dp.toPx()))
                        }
                    }
                    drawCircle(
                        color = ink,
                        radius = r * 0.14f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 2.5.dp.toPx()),
                    )
                    drawCircle(
                        color = ink,
                        radius = r * 0.05f,
                        center = Offset(cx, cy),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Canvas(modifier = Modifier.size(width = 120.dp, height = 70.dp)) {
                    val cx = size.width / 2f
                    drawRect(
                        color = outline,
                        topLeft = Offset(cx - 3.dp.toPx(), 0f),
                        size = Size(6.dp.toPx(), size.height * 0.55f),
                    )
                    drawOval(
                        color = outline,
                        topLeft = Offset(cx - size.width * 0.38f, size.height * 0.55f),
                        size = Size(size.width * 0.76f, size.height * 0.35f),
                        style = Stroke(width = 2.5.dp.toPx()),
                    )
                }
            }
        }

        Text(
            text = when (speed) {
                0 -> "已关"
                1 -> "一档"
                2 -> "二档"
                else -> "三档"
            },
            style = MaterialTheme.typography.titleSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listOf(0 to "关", 1 to "1", 2 to "2", 3 to "3").forEach { (s, label) ->
                val selected = speed == s
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(shape)
                        .border(
                            width = 1.dp,
                            color = if (selected) colors.primary else colors.outline,
                            shape = shape,
                        )
                        .background(
                            if (selected) colors.primary.copy(alpha = 0.12f)
                            else colors.surface,
                        )
                        .clickable { speed = s },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) colors.primary else colors.onSurface,
                    )
                }
            }
        }
    }
}

/** 0 档不转；1/2/3 档用系统动画旋转（不逐帧手算）。 */
@Composable
private fun fanSpinAngle(speed: Int): Float {
    if (speed <= 0) return 0f
    val durationMs = when (speed) {
        1 -> 1800
        2 -> 900
        else -> 420
    }
    val transition = rememberInfiniteTransition(label = "fan_$speed")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing),
        ),
        label = "fanAngle_$speed",
    )
    return angle
}
