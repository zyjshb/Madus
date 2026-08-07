package com.madus.mobile.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.random.Random

/** 关于页连点后的隐藏页：版本号 + 点一下掉音符。 */
@Composable
fun AboutEasterEggScreen(
    version: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    data class Note(
        var x: Float,
        var y: Float,
        var vy: Float,
        var rot: Float,
        val size: Float,
    )
    val notes = remember { mutableStateListOf<Note>() }
    var worldW by remember { mutableFloatStateOf(0f) }
    var worldH by remember { mutableFloatStateOf(0f) }
    var lastFrame by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { t ->
                if (lastFrame == 0L) {
                    lastFrame = t
                    return@withFrameNanos
                }
                val dt = ((t - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastFrame = t
                val iter = notes.listIterator()
                while (iter.hasNext()) {
                    val n = iter.next()
                    n.vy += 520f * dt
                    n.y += n.vy * dt
                    n.rot += 40f * dt
                    if (n.y > worldH + 40f) iter.remove()
                }
            }
        }
    }

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
            Text(
                "Madus",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "v$version",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = colors.onBackground,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "点空白处",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }

        val ink = colors.onBackground
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp)
                .padding(bottom = 20.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (worldW <= 0f) return@detectTapGestures
                        repeat(3 + Random.nextInt(3)) {
                            notes.add(
                                Note(
                                    x = offset.x + Random.nextFloat() * 40f - 20f,
                                    y = offset.y,
                                    vy = -80f - Random.nextFloat() * 120f,
                                    rot = Random.nextFloat() * 40f,
                                    size = 14f + Random.nextFloat() * 12f,
                                ),
                            )
                        }
                    }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                worldW = size.width
                worldH = size.height
                // 地面线
                val gy = size.height * 0.88f
                drawLine(ink.copy(alpha = 0.35f), Offset(0f, gy), Offset(size.width, gy), 2f)
                for (n in notes) {
                    val s = n.size
                    // 八分音符简化：圆头 + 竖杆
                    drawCircle(
                        color = ink,
                        radius = s * 0.35f,
                        center = Offset(n.x, n.y),
                        style = Stroke(width = 2.2f),
                    )
                    drawLine(
                        ink,
                        Offset(n.x + s * 0.32f, n.y),
                        Offset(n.x + s * 0.32f, n.y - s * 1.4f),
                        2.2f,
                    )
                    drawRect(
                        ink,
                        topLeft = Offset(n.x + s * 0.32f, n.y - s * 1.55f),
                        size = Size(s * 0.55f, s * 0.35f),
                        style = Stroke(width = 2f),
                    )
                }
            }
        }
    }
}
