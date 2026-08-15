package com.madus.mobile.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.madus.mobile.data.AppearanceMode

/**
 * 形态 token：与色板正交。
 * 方角线稿保持 0dp；圆滑玻璃使用大圆角、轻透明和阴影。
 */
@Immutable
data class AppearanceTokens(
    val mode: AppearanceMode = AppearanceMode.LineSketch,
    val cornerXs: Dp = 2.dp,
    val cornerSm: Dp = 2.dp,
    val cornerMd: Dp = 2.dp,
    val cornerLg: Dp = 4.dp,
    val borderWidth: Dp = 1.dp,
    val panelAlpha: Float = 1f,
    val cardElevation: Dp = 0.dp,
) {
    companion object {
        fun forMode(mode: AppearanceMode): AppearanceTokens = when (mode) {
            AppearanceMode.LineSketch -> AppearanceTokens(
                mode = mode,
                cornerXs = 2.dp,
                cornerSm = 2.dp,
                cornerMd = 2.dp,
                cornerLg = 4.dp,
                borderWidth = 1.dp,
                panelAlpha = 1f,
                cardElevation = 0.dp,
            )
            AppearanceMode.SoftGlass -> AppearanceTokens(
                mode = mode,
                cornerXs = 10.dp,
                cornerSm = 14.dp,
                cornerMd = 18.dp,
                cornerLg = 22.dp,
                borderWidth = 1.dp,
                // 半透明面板；elevation 在部分模拟器会画出「白色方块」阴影，故不用
                panelAlpha = 0.92f,
                cardElevation = 0.dp,
            )
        }
    }
}

val LocalAppearance = staticCompositionLocalOf { AppearanceTokens() }

@Composable
fun appearanceTokens(): AppearanceTokens = LocalAppearance.current
