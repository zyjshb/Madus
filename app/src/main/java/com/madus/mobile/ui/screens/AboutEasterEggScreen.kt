package com.madus.mobile.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 「关于 Madus」连点解锁的彩蛋（仿安卓系统版本号彩蛋：版本铭牌 + 可涂鸦画板）。
 * 不是小恐龙；风格跟 Madus 线稿一致。
 */
@Composable
fun AboutEasterEggScreen(
    version: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }

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
                    "Madus",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "开发者选项已开启（玩笑）",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
            TextButton(onClick = {
                strokes.clear()
                current = emptyList()
            }) {
                Text("清除")
            }
        }

        // 版本铭牌（仿安卓 About → 版本号页）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "v$version",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = colors.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "线稿听歌 · 端侧直连",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "在下方空白处涂鸦（像安卓旧版彩蛋画板）",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))

        // 画板
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .background(colors.surface)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            current = listOf(offset)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            current = current + change.position
                        },
                        onDragEnd = {
                            if (current.size >= 2) {
                                strokes.add(current)
                            }
                            current = emptyList()
                        },
                        onDragCancel = {
                            current = emptyList()
                        },
                    )
                },
        ) {
            val ink = colors.onBackground
            val faint = colors.outline.copy(alpha = 0.35f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                // 轻网格
                val step = 28.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(faint, Offset(x, 0f), Offset(x, size.height), 1f)
                    x += step
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(faint, Offset(0f, y), Offset(size.width, y), 1f)
                    y += step
                }
                fun drawStroke(points: List<Offset>, color: Color) {
                    if (points.size < 2) return
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            lineTo(points[i].x, points[i].y)
                        }
                    }
                    drawPath(
                        path,
                        color,
                        style = Stroke(
                            width = 3.5f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
                strokes.forEach { drawStroke(it, ink) }
                drawStroke(current, ink)
            }

            if (strokes.isEmpty() && current.isEmpty()) {
                Text(
                    "用手指画画吧",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}
