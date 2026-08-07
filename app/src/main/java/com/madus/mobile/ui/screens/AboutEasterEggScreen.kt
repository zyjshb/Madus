package com.madus.mobile.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.random.Random

/**
 * 小恐龙：可玩版。
 * - 布局尺寸用 onSizeChanged 先记下，不依赖绘制时才赋值
 * - 点击用当前 running/gameOver，避免闭包过期导致跳不起来
 * - 碰撞缩小一点，跳跃更高，更好上手
 */
@Composable
fun AboutEasterEggScreen(
    version: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val game = remember { DinoGame() }

    var frame by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var lastNs by remember { mutableLongStateOf(0L) }

    // 尺寸就绪后写入游戏世界
    LaunchedEffect(canvasSize) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            game.setSize(canvasSize.width.toFloat(), canvasSize.height.toFloat())
        }
    }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        lastNs = 0L
        while (isActive && running) {
            withFrameNanos { t ->
                if (lastNs == 0L) {
                    lastNs = t
                    return@withFrameNanos
                }
                val dt = ((t - lastNs) / 1_000_000_000f).coerceIn(0.001f, 0.032f)
                lastNs = t
                if (game.worldW < 8f && canvasSize.width > 0) {
                    game.setSize(canvasSize.width.toFloat(), canvasSize.height.toFloat())
                }
                val hit = game.step(dt)
                score = game.score
                if (score > best) best = score
                frame++
                if (hit) {
                    gameOver = true
                    running = false
                }
            }
        }
    }

    fun startGame() {
        game.reset()
        if (canvasSize.width > 0) {
            game.setSize(canvasSize.width.toFloat(), canvasSize.height.toFloat())
        }
        score = 0
        gameOver = false
        running = true
        // 开局顺便跳一下，手感更像 Chrome
        game.jump()
    }

    fun onPlayAreaTap() {
        when {
            gameOver -> startGame()
            !running -> startGame()
            else -> game.jump()
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
                .padding(horizontal = 12.dp)
                .padding(top = 8.dp)
                .onSizeChanged { canvasSize = it }
                // 关键依赖 running/gameOver，避免点击用到旧状态
                .pointerInput(running, gameOver) {
                    detectTapGestures {
                        onPlayAreaTap()
                    }
                },
        ) {
            // 读取 frame，驱动每帧重绘
            @Suppress("UNUSED_VARIABLE")
            val drawFrame = frame

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                if (w < 1f || h < 1f) return@Canvas
                // 同步尺寸（防止 onSizeChanged 稍晚）
                if (game.worldW != w || game.groundY <= 0f) {
                    game.setSize(w, h)
                }
                val gy = game.groundY

                // 地面
                drawLine(ink, Offset(0f, gy), Offset(w, gy), 3f)
                var gx = -(game.scroll % 28f)
                while (gx < w) {
                    drawLine(
                        ink.copy(alpha = 0.28f),
                        Offset(gx, gy + 7f),
                        Offset(gx + 12f, gy + 7f),
                        2f,
                    )
                    gx += 28f
                }

                // 恐龙（脚底在 gy - dinoY）
                val dl = game.dinoX
                val footY = gy - game.dinoY
                val topY = footY - game.dinoH
                drawRoundRect(
                    color = ink,
                    topLeft = Offset(dl, topY + 12f),
                    size = Size(game.dinoW - 8f, game.dinoH - 12f),
                    cornerRadius = CornerRadius(4f, 4f),
                    style = Stroke(2.5f),
                )
                drawRoundRect(
                    color = ink,
                    topLeft = Offset(dl + 14f, topY),
                    size = Size(24f, 18f),
                    cornerRadius = CornerRadius(4f, 4f),
                    style = Stroke(2.5f),
                )
                drawCircle(ink, 2.4f, Offset(dl + 30f, topY + 7f))
                // 腿
                if (game.dinoY < 2f && running && !gameOver) {
                    val phase = (frame / 4) % 2
                    if (phase == 0) {
                        drawLine(ink, Offset(dl + 10f, footY), Offset(dl + 6f, footY + 11f), 2.5f)
                        drawLine(ink, Offset(dl + 22f, footY), Offset(dl + 28f, footY + 11f), 2.5f)
                    } else {
                        drawLine(ink, Offset(dl + 10f, footY), Offset(dl + 16f, footY + 11f), 2.5f)
                        drawLine(ink, Offset(dl + 22f, footY), Offset(dl + 18f, footY + 11f), 2.5f)
                    }
                } else {
                    drawLine(ink, Offset(dl + 12f, footY), Offset(dl + 10f, footY + 8f), 2.5f)
                    drawLine(ink, Offset(dl + 22f, footY), Offset(dl + 24f, footY + 8f), 2.5f)
                }

                // 障碍
                for (i in 0 until game.obsCount) {
                    val x = game.obsX[i]
                    val ow = game.obsW[i]
                    val oh = game.obsH[i]
                    val path = Path().apply {
                        val mid = x + ow * 0.5f
                        moveTo(mid, gy)
                        lineTo(mid, gy - oh)
                        moveTo(mid, gy - oh * 0.55f)
                        lineTo(x, gy - oh * 0.55f)
                        lineTo(x, gy - oh * 0.78f)
                        moveTo(mid, gy - oh * 0.4f)
                        lineTo(x + ow, gy - oh * 0.4f)
                        lineTo(x + ow, gy - oh * 0.68f)
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
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("点屏幕跳跃", style = MaterialTheme.typography.titleMedium, color = ink)
                    Box(
                        modifier = Modifier
                            .border(1.dp, colors.outline, RoundedCornerShape(8.dp))
                            .clickable { startGame() }
                            .padding(horizontal = 28.dp, vertical = 12.dp),
                    ) {
                        Text("开始", style = MaterialTheme.typography.titleMedium, color = accent)
                    }
                }
            }

            if (gameOver) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "GAME OVER",
                        style = MaterialTheme.typography.headlineSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("得分 $score", style = MaterialTheme.typography.bodyMedium, color = ink)
                    Box(
                        modifier = Modifier
                            .border(1.dp, colors.outline, RoundedCornerShape(8.dp))
                            .clickable { startGame() }
                            .padding(horizontal = 28.dp, vertical = 12.dp),
                    ) {
                        Text("再来", style = MaterialTheme.typography.titleMedium, color = accent)
                    }
                }
            }
        }

        // 底部大按钮：跳 / 开始（避免只靠点画布不好点）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .height(52.dp)
                .border(1.dp, colors.primary, RoundedCornerShape(8.dp))
                .background(colors.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                .clickable {
                    if (!running || gameOver) startGame() else game.jump()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when {
                    gameOver -> "再来一局"
                    !running -> "开始"
                    else -> "跳！"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary,
            )
        }
    }
}

/** 游戏状态：普通字段，每帧由 Compose 用 frame 触发绘制 */
private class DinoGame {
    var worldW = 0f
    var worldH = 0f
    var groundY = 0f
    var dinoX = 56f
    var dinoY = 0f // 离地高度
    var dinoVy = 0f
    val dinoW = 36f
    val dinoH = 42f
    var speed = 280f
    var score = 0
    var scroll = 0f
    var spawnT = 0f
    val obsX = FloatArray(5)
    val obsW = FloatArray(5)
    val obsH = FloatArray(5)
    var obsCount = 0
    private var nextSpawnIn = 1.4f

    fun setSize(w: Float, h: Float) {
        worldW = w
        worldH = h
        groundY = h * 0.75f
    }

    fun reset() {
        dinoY = 0f
        dinoVy = 0f
        speed = 280f
        score = 0
        scroll = 0f
        spawnT = 0f
        obsCount = 0
        nextSpawnIn = 1.2f
    }

    fun jump() {
        // 允许在接近地面时跳（容错）
        if (dinoY <= 8f) {
            dinoVy = 920f
        }
    }

    /** @return true 撞到障碍 */
    fun step(dt: Float): Boolean {
        if (worldW < 8f) return false

        // 重力
        dinoVy -= 2400f * dt
        dinoY += dinoVy * dt
        if (dinoY < 0f) {
            dinoY = 0f
            dinoVy = 0f
        }

        speed = (280f + score * 8f).coerceAtMost(520f)
        scroll += speed * dt

        // 生成障碍
        spawnT += dt
        if (spawnT >= nextSpawnIn && obsCount < obsX.size) {
            spawnT = 0f
            nextSpawnIn = 1.0f + Random.nextFloat() * 0.9f
            val i = obsCount++
            obsX[i] = worldW + 30f
            obsW[i] = 16f + Random.nextFloat() * 10f
            // 不要太高，方便跳过
            obsH[i] = 32f + Random.nextFloat() * 22f
        }

        // 移动 + 碰撞（缩小碰撞盒）
        val pad = 6f
        val dL = dinoX + pad
        val dR = dinoX + dinoW - pad
        val dB = groundY - dinoY - 2f
        val dT = dB - dinoH + pad + 8f

        var i = 0
        while (i < obsCount) {
            obsX[i] -= speed * dt
            if (obsX[i] + obsW[i] < 0f) {
                // 移出屏幕：得分并移除
                obsCount--
                if (i < obsCount) {
                    obsX[i] = obsX[obsCount]
                    obsW[i] = obsW[obsCount]
                    obsH[i] = obsH[obsCount]
                }
                score++
                continue
            }
            val cL = obsX[i] + 3f
            val cR = obsX[i] + obsW[i] - 3f
            val cT = groundY - obsH[i] + 4f
            val cB = groundY
            val overlap = dR > cL && dL < cR && dB > cT && dT < cB
            if (overlap) return true
            i++
        }
        return false
    }
}
