package com.madus.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.madus.mobile.data.LiquidAppearance

/** 画境：香槟金压在照片上，不是系统蓝。 */
val CanvasGold = Color(0xFFD8C4A4)
val CanvasGoldSoft = Color(0xFFE8D5B5)
val CanvasInk = Color(0xFF07090C)
val CanvasPaper = Color(0xFFF4EFE6)

@Immutable
data class LiquidTokens(
    val tint: Float = 0.42f,
    val dark: Boolean = true,
    val cornerCover: Dp = 10.dp,
    val cornerGroup: Dp = 14.dp,
    val cornerSheet: Dp = 28.dp,
    val cornerAction: Dp = 14.dp,
    val cornerNowPlaying: Dp = 22.dp,
    val cornerPill: Dp = 999.dp,
    val hazeContainer: Color = Color(0xFF0A0C10),
    val wallpaperPath: String? = null,
    val wallpaperDim: Float = 0.55f,
) {
    val fillAlpha: Float
        get() = (0.22f + tint * 0.36f).coerceIn(0.22f, 0.58f)

    val glassFill: Color
        get() = Color(0xFF101318).copy(alpha = fillAlpha)

    val rim: Color
        get() = Color.White.copy(alpha = 0.16f)

    val goldRim: Color
        get() = CanvasGold.copy(alpha = 0.55f)

    val accent: Color
        get() = CanvasGoldSoft

    companion object {
        fun of(
            tint: Float,
            dark: Boolean,
            wallpaperPath: String? = null,
            wallpaperDim: Float = 0.55f,
        ) = LiquidTokens(
            tint = tint.coerceIn(0f, 1f),
            dark = dark,
            wallpaperPath = wallpaperPath,
            wallpaperDim = wallpaperDim.coerceIn(0.25f, 0.82f),
        )
    }
}

val LocalLiquidTokens = staticCompositionLocalOf { LiquidTokens() }

@Composable
fun liquidTokens(): LiquidTokens = LocalLiquidTokens.current

object LiquidType {
    val largeTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.3).sp,
    )
    val title2 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    )
    val title3 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 25.sp,
    )
    val headline = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    )
    val body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    )
    val subhead = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 19.sp,
    )
    val footnote = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
    val caption = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 13.sp,
    )
}

object LiquidChromeMetrics {
    val hInset = 16.dp
    val gap = 8.dp
    val tabHeight = 58.dp
    val miniHeight = 60.dp
    val chromeBottom = 8.dp
    val contentExtra = 16.dp
    val edgeFade = 24.dp
    fun contentBottom(showMini: Boolean): Dp =
        (if (showMini) miniHeight + gap else 0.dp) + tabHeight + chromeBottom + contentExtra
}

fun liquidDark(appearance: LiquidAppearance, systemDark: Boolean): Boolean = when (appearance) {
    LiquidAppearance.FollowSystem -> true
    LiquidAppearance.Light -> false
    LiquidAppearance.Dark -> true
}

fun liquidColorScheme(dark: Boolean) = darkColorScheme(
    primary = CanvasGoldSoft,
    onPrimary = Color(0xFF1A140C),
    secondary = Color(0xFFC8BBA8),
    onSecondary = Color.White,
    background = CanvasInk,
    onBackground = CanvasPaper,
    surface = Color(0xFF161A1F),
    onSurface = CanvasPaper,
    surfaceVariant = Color(0xFF22262C),
    onSurfaceVariant = Color(0xFFC8BBA8),
    outline = Color(0x66D8C4A4),
    outlineVariant = Color(0x3322262C),
    error = Color(0xFFFF6B5C),
    onError = Color.White,
)

val LiquidTypography = Typography(
    displayLarge = LiquidType.largeTitle,
    displaySmall = LiquidType.largeTitle,
    headlineMedium = LiquidType.title2,
    headlineSmall = LiquidType.title3,
    titleLarge = LiquidType.headline,
    titleMedium = LiquidType.headline,
    bodyLarge = LiquidType.body,
    bodyMedium = LiquidType.subhead,
    bodySmall = LiquidType.footnote,
    labelLarge = LiquidType.footnote,
    labelMedium = LiquidType.footnote,
    labelSmall = LiquidType.caption,
)

@Composable
fun resolveLiquidDark(appearance: LiquidAppearance): Boolean =
    liquidDark(appearance, isSystemInDarkTheme())
