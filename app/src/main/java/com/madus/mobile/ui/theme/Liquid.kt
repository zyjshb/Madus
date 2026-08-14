package com.madus.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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

@Immutable
data class LiquidTokens(
    val tint: Float = 0.42f,
    val dark: Boolean = false,
    val cornerXs: Dp = 12.dp,
    val cornerSm: Dp = 18.dp,
    val cornerCard: Dp = 22.dp,
    val cornerSheet: Dp = 32.dp,
    val cornerPill: Dp = 999.dp,
) {
    val fillAlpha: Float
        get() = 0.55f + tint * 0.30f

    val glassFill: Color
        get() = if (dark) {
            Color(0xFF2A2A30).copy(alpha = 0.62f + tint * 0.26f)
        } else {
            Color(0xFFF8FAFC).copy(alpha = fillAlpha)
        }

    val glassFillStrong: Color
        get() = if (dark) {
            Color(0xFF32323A).copy(alpha = 0.78f + tint * 0.16f)
        } else {
            Color.White.copy(alpha = (0.72f + tint * 0.20f).coerceAtMost(0.92f))
        }

    val rim: Color
        get() = Color.White.copy(alpha = if (dark) 0.22f else 0.70f)

    val edge: Color
        get() = Color.Black.copy(alpha = if (dark) 0.45f else 0.10f)

    val specular: Float
        get() = if (dark) 0.22f else 0.55f - tint * 0.18f

    val accent: Color
        get() = if (dark) Color(0xFF64B5FF) else Color(0xFF007AFF)

    companion object {
        fun of(tint: Float, dark: Boolean) = LiquidTokens(
            tint = tint.coerceIn(0f, 1f),
            dark = dark,
        )
    }
}

val LocalLiquidTokens = staticCompositionLocalOf { LiquidTokens() }

@Composable
fun liquidTokens(): LiquidTokens = LocalLiquidTokens.current

fun liquidDark(appearance: LiquidAppearance, systemDark: Boolean): Boolean = when (appearance) {
    LiquidAppearance.FollowSystem -> systemDark
    LiquidAppearance.Light -> false
    LiquidAppearance.Dark -> true
}

fun liquidColorScheme(dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = Color(0xFF64B5FF),
        onPrimary = Color(0xFF001422),
        secondary = Color(0xFFA1A1AA),
        onSecondary = Color.White,
        background = Color(0xFF0C0C0E),
        onBackground = Color(0xFFF4F4F5),
        surface = Color(0xFF1A1A1E),
        onSurface = Color(0xFFF4F4F5),
        surfaceVariant = Color(0xFF27272A),
        onSurfaceVariant = Color(0xFFA1A1AA),
        outline = Color(0xFF3F3F46),
        outlineVariant = Color(0xFF27272A),
        error = Color(0xFFFF453A),
        onError = Color.White,
    )
} else {
    lightColorScheme(
        primary = Color(0xFF007AFF),
        onPrimary = Color.White,
        secondary = Color(0xFF6B7280),
        onSecondary = Color.White,
        background = Color(0xFFEEF1F6),
        onBackground = Color(0xFF111113),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF111113),
        surfaceVariant = Color(0xFFE4E7EE),
        onSurfaceVariant = Color(0xFF6B7280),
        outline = Color(0xFFD0D5DD),
        outlineVariant = Color(0xFFE4E7EE),
        error = Color(0xFFFF3B30),
        onError = Color.White,
    )
}

val LiquidTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

@Composable
fun resolveLiquidDark(appearance: LiquidAppearance): Boolean =
    liquidDark(appearance, isSystemInDarkTheme())
