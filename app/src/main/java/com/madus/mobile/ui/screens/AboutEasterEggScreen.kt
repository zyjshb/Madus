package com.madus.mobile.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.random.Random

/**
 * 连点「关于 Madus」10 次进入的彩蛋页。
 * 内含 Chrome 离线恐龙风格小游戏（线稿风，不依赖网络）。
 */
@Composable
fun AboutEasterEggScreen(
    version: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
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
                Text("Madus 彩蛋", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "v$version · 点屏幕跳跃",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Text(
            text = "没有网？这里有只小恐龙陪你听歌。",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        MadusDinoGame(
            ink = colors.onBackground,
            paper = colors.background,
            accent = colors.primary,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp)
                .padding(bottom = 24.dp),
        )
    }
}

private data class Cactus(
    var x: Float,
    val w: Float,
    val h: Float,
)

@Composable
private fun MadusDinoGame(
    ink: Color,
    paper: Color,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    var running by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(0) }
    var dinoY by remember { mutableFloatStateOf(0f) }
    var dinoVy by remember { mutableFloatStateOf(0f) }
    var groundY by remember { mutableFloatStateOf(0f) }
    var worldW by remember { mutableFloatStateOf(0f) }
    var speed by remember { mutableFloatStateOf(320f) }
    var spawnAcc by remember { mutableFloatStateOf(0f) }
    var lastFrame by remember { mutableLongStateOf(0L) }
    val cacti = remember { mutableStateListOf<Cactus>() }

    fun reset() {
        dinoY = 0f
        dinoVy = 0f
        speed = 320f
        spawnAcc = 0f
        score = 0
        cacti.clear()
        gameOver = false
        running = true
        lastFrame = 0L
    }

    fun jump() {
        if (gameOver) {
            reset()
            return
        }
        if (!running) {
            reset()
            return
        }
        // 仅在地面可跳
        if (dinoY <= 0.5f) {
            dinoVy = 780f
        }
    }

    LaunchedEffect(running, gameOver) {
        if (!running || gameOver) return@LaunchedEffect
        while (isActive && running && !gameOver) {
            withFrameNanos { t ->
                if (lastFrame == 0L) {
                    lastFrame = t
                    return@withFrameNanos
                }
                val dt = ((t - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastFrame = t
                if (worldW <= 1f || groundY <= 1f) return@withFrameNanos

                // 重力（y 向上为正，显示时翻转）
                dinoVy -= 2200f * dt
                dinoY += dinoVy * dt
                if (dinoY < 0f) {
                    dinoY = 0f
                    dinoVy = 0f
                }

                speed = (320f + score * 2.2f).coerceAtMost(620f)
                spawnAcc += dt
                val spawnEvery = (1.25f - score * 0.008f).coerceAtLeast(0.55f)
                if (spawnAcc >= spawnEvery) {
                    spawnAcc = 0f
                    val h = 28f + Random.nextFloat() * 36f
                    val w = 14f + Random.nextFloat() * 12f
                    cacti.add(Cactus(x = worldW + 20f, w = w, h = h))
                }

                val iter = cacti.listIterator()
                while (iter.hasNext()) {
                    val c = iter.next()
                    c.x -= speed * dt
                    if (c.x + c.w < -10f) {
                        iter.remove()
                        score += 1
                        if (score > best) best = score
                    }
                }

                // 碰撞：恐龙约 36x40
                val dinoLeft = 48f
                val dinoRight = dinoLeft + 36f
                val dinoBottom = groundY - dinoY
                val dinoTop = dinoBottom - 40f
                for (c in cacti) {
                    val cLeft = c.x
                    val cRight = c.x + c.w
                    val cBottom = groundY
                    val cTop = groundY - c.h
                    val hit = dinoRight > cLeft + 4f &&
                        dinoLeft < cRight - 4f &&
                        dinoBottom > cTop + 4f &&
                        dinoTop < cBottom - 4f
                    if (hit) {
                        gameOver = true
                        running = false
                        break
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .background(paper)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { jump() })
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            worldW = size.width
            groundY = size.height * 0.72f

            // 地面
            drawLine(
                color = ink,
                start = Offset(0f, groundY),
                end = Offset(size.width, groundY),
                strokeWidth = 3f,
            )
            // 地平线虚点
            var gx = (score * 8f) % 24f
            while (gx < size.width) {
                drawLine(
                    color = ink.copy(alpha = 0.35f),
                    start = Offset(gx, groundY + 8f),
                    end = Offset(gx + 10f, groundY + 8f),
                    strokeWidth = 2f,
                )
                gx += 24f
            }

            // 恐龙（侧视小块 + 头）
            val dinoLeft = 48f
            val dinoBottom = groundY - dinoY
            val dinoTop = dinoBottom - 40f
            drawRoundRect(
                color = ink,
                topLeft = Offset(dinoLeft, dinoTop + 10f),
                size = Size(28f, 30f),
                cornerRadius = CornerRadius(3f, 3f),
                style = Stroke(width = 2.5f),
            )
            drawRoundRect(
                color = ink,
                topLeft = Offset(dinoLeft + 16f, dinoTop),
                size = Size(22f, 16f),
                cornerRadius = CornerRadius(3f, 3f),
                style = Stroke(width = 2.5f),
            )
            // 眼睛
            drawCircle(
                color = ink,
                radius = 2.2f,
                center = Offset(dinoLeft + 30f, dinoTop + 6f),
            )
            // 腿（跑动感）
            val legPhase = ((System.nanoTime() / 80_000_000L) % 2).toInt()
            if (dinoY < 1f) {
                if (legPhase == 0) {
                    drawLine(ink, Offset(dinoLeft + 8f, dinoBottom), Offset(dinoLeft + 4f, dinoBottom + 10f), 2.5f)
                    drawLine(ink, Offset(dinoLeft + 20f, dinoBottom), Offset(dinoLeft + 26f, dinoBottom + 10f), 2.5f)
                } else {
                    drawLine(ink, Offset(dinoLeft + 8f, dinoBottom), Offset(dinoLeft + 14f, dinoBottom + 10f), 2.5f)
                    drawLine(ink, Offset(dinoLeft + 20f, dinoBottom), Offset(dinoLeft + 16f, dinoBottom + 10f), 2.5f)
                }
            } else {
                drawLine(ink, Offset(dinoLeft + 10f, dinoBottom), Offset(dinoLeft + 8f, dinoBottom + 6f), 2.5f)
                drawLine(ink, Offset(dinoLeft + 20f, dinoBottom), Offset(dinoLeft + 22f, dinoBottom + 6f), 2.5f)
            }

            // 仙人掌
            for (c in cacti) {
                val path = Path().apply {
                    moveTo(c.x + c.w * 0.5f, groundY)
                    lineTo(c.x + c.w * 0.5f, groundY - c.h)
                    moveTo(c.x + c.w * 0.5f, groundY - c.h * 0.55f)
                    lineTo(c.x, groundY - c.h * 0.55f)
                    lineTo(c.x, groundY - c.h * 0.75f)
                    moveTo(c.x + c.w * 0.5f, groundY - c.h * 0.4f)
                    lineTo(c.x + c.w, groundY - c.h * 0.4f)
                    lineTo(c.x + c.w, groundY - c.h * 0.65f)
                }
                drawPath(path, ink, style = Stroke(width = 3f))
            }

            // 云
            drawCircle(ink.copy(alpha = 0.25f), 10f, Offset(size.width * 0.7f, groundY - 120f), style = Stroke(2f))
            drawCircle(ink.copy(alpha = 0.25f), 14f, Offset(size.width * 0.72f, groundY - 124f), style = Stroke(2f))
            drawCircle(ink.copy(alpha = 0.25f), 10f, Offset(size.width * 0.75f, groundY - 118f), style = Stroke(2f))
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                "HI  ${best.toString().padStart(5, '0')}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelMedium,
                color = ink.copy(alpha = 0.7f),
            )
            Text(
                score.toString().padStart(5, '0'),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.titleMedium,
                color = ink,
            )
        }

        if (!running && !gameOver) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("点屏幕开始", style = MaterialTheme.typography.titleMedium, color = ink)
                Text("跳跃躲仙人掌", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.65f))
            }
        }
        if (gameOver) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("GAME OVER", style = MaterialTheme.typography.headlineSmall, color = accent, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("得分 $score", style = MaterialTheme.typography.bodyMedium, color = ink)
                TextButton(onClick = { reset() }) {
                    Text("再来一局")
                }
            }
        }
    }
}
