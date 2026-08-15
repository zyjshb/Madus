package com.madus.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
    val wallpaperPath: String? = null,
    val wallpaperDim: Float = 0.55f,
    val wallpaperBlur: Float = 0f,
    val wallpaperStamp: Long = 0L,
    val accentColor: Color = CanvasGoldSoft,
) {
    val fillAlpha: Float
        get() = (0.08f + tint * 0.46f).coerceIn(0.08f, 0.54f)

    /** 通透=近白薄霜，着色=强调色厚釉。两端差要一眼能看出来。 */
    val glassFill: Color
        get() {
            val clear = Color.White.copy(alpha = 0.10f)
            val stained = lerp(Color(0xE614181E), accentColor, 0.62f).copy(alpha = 0.52f)
            return lerp(clear, stained, tint)
        }

    val hazeContainer: Color
        get() = lerp(Color(0x66101820), lerp(Color(0xFF101318), accentColor, 0.48f), tint)

    val rim: Color
        get() = lerp(Color.White.copy(alpha = 0.28f), accentColor.copy(alpha = 0.72f), tint)

    val goldRim: Color
        get() = accentColor.copy(alpha = 0.55f)

    val accent: Color
        get() = accentColor

    val wallpaperBlurRadius: Dp
        get() = (wallpaperBlur * 28f).dp

    companion object {
        fun of(
            tint: Float,
            dark: Boolean,
            wallpaperPath: String? = null,
            wallpaperDim: Float = 0.55f,
            wallpaperBlur: Float = 0f,
            wallpaperStamp: Long = 0L,
            accentColor: Color = CanvasGoldSoft,
        ) = LiquidTokens(
            tint = tint.coerceIn(0f, 1f),
            dark = dark,
            wallpaperPath = wallpaperPath,
            wallpaperDim = wallpaperDim.coerceIn(0.25f, 0.82f),
            wallpaperBlur = wallpaperBlur.coerceIn(0f, 1f),
            wallpaperStamp = wallpaperStamp,
            accentColor = accentColor,
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

fun liquidColorScheme(
    dark: Boolean,
    accent: Color = CanvasGoldSoft,
    onBg: Color = CanvasPaper,
) = darkColorScheme(
    primary = accent,
    onPrimary = Color(0xFF1A140C),
    secondary = onBg.copy(alpha = 0.78f),
    onSecondary = Color.White,
    background = CanvasInk,
    onBackground = onBg,
    surface = Color(0xFF161A1F),
    onSurface = onBg,
    surfaceVariant = Color(0xFF22262C),
    onSurfaceVariant = onBg.copy(alpha = 0.72f),
    outline = accent.copy(alpha = 0.40f),
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
