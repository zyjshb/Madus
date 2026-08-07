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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.random.Random

/**
 * 小恐龙：状态放在普通对象里，每帧只刷一个 tick，减轻卡顿。
 */
@Composable
fun AboutEasterEggScreen(
    version: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val game = remember { DinoGame() }
    // 仅用一个 float 驱动 Canvas 重绘
    var tick by remember { mutableFloatStateOf(0f) }
    var score by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }
    var lastNs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(running, gameOver) {
        if (!running || gameOver) return@LaunchedEffect
        lastNs = 0L
        while (isActive && running && !gameOver) {
            withFrameNanos { t ->
                if (lastNs == 0L) {
                    lastNs = t
                    return@withFrameNanos
                }
                val dt = ((t - lastNs) / 1_000_000_000f).coerceIn(0f, 0.033f)
                lastNs = t
                val ended = game.step(dt)
                score = game.score
                if (score > best) best = score
                tick = tick + dt
                if (ended) {
                    gameOver = true
                    running = false
                }
            }
        }
    }

    fun jumpOrStart() {
        if (gameOver) {
            game.reset()
            score = 0
            gameOver = false
            running = true
            return
        }
        if (!running) {
            game.reset()
            score = 0
            running = true
            return
        }
        game.jump()
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
            Column(Modifier.weight(1f)) {
                Text(
                    "小恐龙",
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

        val ink = colors.onBackground
        val accent = colors.primary
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(12.dp)
                .pointerInput(Unit) {
                    detectTapGestures { jumpOrStart() }
                },
        ) {
            // tick 参与读取，保证帧刷新
            @Suppress("UNUSED_EXPRESSION")
            tick
            Canvas(modifier = Modifier.fillMaxSize()) {
                game.ensureSize(size.width, size.height)
                val gy = game.groundY

                drawLine(ink, Offset(0f, gy), Offset(size.width, gy), 3f)
                var gx = (game.scroll % 24f)
                while (gx < size.width) {
                    drawLine(
                        ink.copy(alpha = 0.3f),
                        Offset(gx, gy + 8f),
                        Offset(gx + 10f, gy + 8f),
                        2f,
                    )
                    gx += 24f
                }

                // 恐龙
                val dl = game.dinoX
                val db = gy - game.dinoY
                val dt = db - 40f
                drawRoundRect(
                    ink,
                    Offset(dl, dt + 10f),
                    Size(28f, 30f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                    style = Stroke(2.5f),
                )
                drawRoundRect(
                    ink,
                    Offset(dl + 16f, dt),
                    Size(22f, 16f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                    style = Stroke(2.5f),
                )
                drawCircle(ink, 2.2f, Offset(dl + 30f, dt + 6f))
                if (game.dinoY < 1f) {
                    val phase = ((System.nanoTime() / 90_000_000L) % 2).toInt()
                    if (phase == 0) {
                        drawLine(ink, Offset(dl + 8f, db), Offset(dl + 4f, db + 10f), 2.5f)
                        drawLine(ink, Offset(dl + 20f, db), Offset(dl + 26f, db + 10f), 2.5f)
                    } else {
                        drawLine(ink, Offset(dl + 8f, db), Offset(dl + 14f, db + 10f), 2.5f)
                        drawLine(ink, Offset(dl + 20f, db), Offset(dl + 16f, db + 10f), 2.5f)
                    }
                } else {
                    drawLine(ink, Offset(dl + 10f, db), Offset(dl + 8f, db + 6f), 2.5f)
                    drawLine(ink, Offset(dl + 20f, db), Offset(dl + 22f, db + 6f), 2.5f)
                }

                // 仙人掌（最多几个，轻量）
                for (i in 0 until game.cactusCount) {
                    val x = game.cactusX[i]
                    val w = game.cactusW[i]
                    val h = game.cactusH[i]
                    val path = Path().apply {
                        moveTo(x + w * 0.5f, gy)
                        lineTo(x + w * 0.5f, gy - h)
                        moveTo(x + w * 0.5f, gy - h * 0.55f)
                        lineTo(x, gy - h * 0.55f)
                        lineTo(x, gy - h * 0.75f)
                        moveTo(x + w * 0.5f, gy - h * 0.4f)
                        lineTo(x + w, gy - h * 0.4f)
                        lineTo(x + w, gy - h * 0.65f)
                    }
                    drawPath(path, ink, style = Stroke(3f))
                }
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
                    color = ink.copy(alpha = 0.65f),
                )
                Text(
                    score.toString().padStart(5, '0'),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleMedium,
                    color = ink,
                )
            }

            if (!running && !gameOver) {
                Text(
                    "点屏幕开始",
                    style = MaterialTheme.typography.titleMedium,
                    color = ink,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (gameOver) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "GAME OVER",
                        style = MaterialTheme.typography.headlineSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("得分 $score", style = MaterialTheme.typography.bodyMedium, color = ink)
                    TextButton(onClick = { jumpOrStart() }) {
                        Text("再来")
                    }
                }
            }
        }
    }
}

/** 纯数据游戏逻辑，不触发 Compose 重组风暴 */
private class DinoGame {
    var groundY = 0f
    var worldW = 0f
    var dinoX = 48f
    var dinoY = 0f
    var dinoVy = 0f
    var speed = 320f
    var score = 0
    var scroll = 0f
    var spawnAcc = 0f
    val cactusX = FloatArray(6)
    val cactusW = FloatArray(6)
    val cactusH = FloatArray(6)
    var cactusCount = 0

    fun ensureSize(w: Float, h: Float) {
        worldW = w
        groundY = h * 0.72f
    }

    fun reset() {
        dinoY = 0f
        dinoVy = 0f
        speed = 320f
        score = 0
        scroll = 0f
        spawnAcc = 0f
        cactusCount = 0
    }

    fun jump() {
        if (dinoY <= 0.5f) dinoVy = 780f
    }

    /** @return true = game over */
    fun step(dt: Float): Boolean {
        if (worldW <= 1f) return false
        dinoVy -= 2200f * dt
        dinoY += dinoVy * dt
        if (dinoY < 0f) {
            dinoY = 0f
            dinoVy = 0f
        }
        speed = (320f + score * 2.2f).coerceAtMost(600f)
        scroll += speed * dt
        spawnAcc += dt
        val every = (1.3f - score * 0.008f).coerceAtLeast(0.6f)
        if (spawnAcc >= every && cactusCount < cactusX.size) {
            spawnAcc = 0f
            val i = cactusCount++
            cactusX[i] = worldW + 20f
            cactusW[i] = 14f + Random.nextFloat() * 12f
            cactusH[i] = 28f + Random.nextFloat() * 36f
        }
        var i = 0
        while (i < cactusCount) {
            cactusX[i] -= speed * dt
            if (cactusX[i] + cactusW[i] < -10f) {
                // remove i by swap last
                cactusCount--
                if (i < cactusCount) {
                    cactusX[i] = cactusX[cactusCount]
                    cactusW[i] = cactusW[cactusCount]
                    cactusH[i] = cactusH[cactusCount]
                }
                score++
                continue
            }
            // hit
            val dL = dinoX
            val dR = dinoX + 36f
            val dB = groundY - dinoY
            val dT = dB - 40f
            val cL = cactusX[i]
            val cR = cactusX[i] + cactusW[i]
            val cT = groundY - cactusH[i]
            if (dR > cL + 4f && dL < cR - 4f && dB > cT + 4f && dT < groundY - 4f) {
                return true
            }
            i++
        }
        return false
    }
}
