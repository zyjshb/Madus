package com.madus.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
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
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    if (visualTheme == VisualTheme.LiquidGlass) {
        val liquidDark = liquidDark(liquidAppearance, darkTheme)
        val tokens = LiquidTokens.of(glassTint, liquidDark)
        val appearanceForFallback = AppearanceTokens(
            mode = AppearanceMode.SoftGlass,
            cornerXs = tokens.cornerXs,
            cornerSm = tokens.cornerSm,
            cornerMd = tokens.cornerCard,
            cornerLg = tokens.cornerSheet,
            borderWidth = 0.6.dp,
            panelAlpha = tokens.fillAlpha,
            cardElevation = 0.dp,
        )
        CompositionLocalProvider(
            LocalVisualTheme provides VisualTheme.LiquidGlass,
            LocalLiquidTokens provides tokens,
            LocalAppearance provides appearanceForFallback,
        ) {
            MaterialTheme(
                colorScheme = liquidColorScheme(liquidDark),
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
