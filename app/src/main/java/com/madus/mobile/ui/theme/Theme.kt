package com.madus.mobile.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import com.madus.mobile.data.AppearanceMode
import com.madus.mobile.data.ColorTheme
import com.madus.mobile.data.LiquidAppearance
import com.madus.mobile.data.VisualTheme

@Composable
fun MadusTheme(
    visualTheme: VisualTheme = VisualTheme.Classic,
    appearance: AppearanceMode = AppearanceMode.LineSketch,
    colorTheme: ColorTheme = ColorTheme.LineSketchMono,
    liquidAppearance: LiquidAppearance = LiquidAppearance.FollowSystem,
    glassTint: Float = 0.42f,
    wallpaperPath: String? = null,
    wallpaperDim: Float = 0.55f,
    wallpaperBlur: Float = 0f,
    wallpaperStamp: Long = 0L,
    followWallpaperColor: Boolean = true,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    if (visualTheme == VisualTheme.Canvas) {
        // 壁纸当深底：字/图标永远浅色。透明 Scaffold 不会自己设 LocalContentColor，
        // 缺了这一层就会落到默认黑字，亮图上直接看不见。
        val palette = remember(wallpaperPath, wallpaperStamp) { extractCanvasPalette(wallpaperPath) }
        val ink = CanvasPaper
        val dim = maxOf(wallpaperDim, palette.minDim, 0.52f)
        val accent = if (followWallpaperColor) palette.accent else CanvasGoldSoft
        val tokens = LiquidTokens.of(
            tint = glassTint,
            dark = true,
            wallpaperPath = wallpaperPath,
            wallpaperDim = dim,
            wallpaperBlur = wallpaperBlur,
            wallpaperStamp = wallpaperStamp,
            accentColor = accent,
        )
        val appearanceForFallback = AppearanceTokens(
            mode = AppearanceMode.SoftGlass,
            cornerXs = 12.dp,
            cornerSm = 12.dp,
            cornerMd = 12.dp,
            cornerLg = 28.dp,
            borderWidth = 0.6.dp,
            panelAlpha = tokens.fillAlpha.coerceIn(0.22f, 0.38f),
            cardElevation = 0.dp,
        )
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as? Activity)?.window ?: return@SideEffect
                WindowInsetsControllerCompat(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
        CompositionLocalProvider(
            LocalVisualTheme provides VisualTheme.Canvas,
            LocalLiquidTokens provides tokens,
            LocalAppearance provides appearanceForFallback,
            LocalContentColor provides ink,
        ) {
            MaterialTheme(
                colorScheme = liquidColorScheme(dark = true, accent = accent, onBg = ink),
                typography = LiquidTypography,
                content = content,
            )
        }
    } else {
        val tokens = AppearanceTokens.forMode(appearance)
        val scheme = colorSchemeFor(colorTheme, darkTheme)
        CompositionLocalProvider(
            LocalVisualTheme provides VisualTheme.Classic,
            LocalAppearance provides tokens,
        ) {
            MaterialTheme(
                colorScheme = scheme,
                typography = MadusTypography,
                content = content,
            )
        }
    }
}
