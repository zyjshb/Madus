package com.madus.mobile.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.madus.mobile.data.ColorTheme

/**
 * Line-sketch palette: paper + ink only (default).
 */
object LineSketch {
    val Paper = Color(0xFFF7F5F2)
    val PaperDim = Color(0xFFEFECE7)
    val Ink = Color(0xFF111111)
    val InkSoft = Color(0xFF3A3A3A)
    val InkMute = Color(0xFF7A7A7A)
    val Line = Color(0xFF111111)
    val LineSoft = Color(0xFFB8B4AD)
    val Danger = Color(0xFF111111)
}

/** Catppuccin 核心角色 — https://catppuccin.com/palette/ */
private data class CatPalette(
    val base: Color,
    val mantle: Color,
    val surface0: Color,
    val surface1: Color,
    val text: Color,
    val subtext0: Color,
    val overlay0: Color,
    val mauve: Color,
    val red: Color,
    val light: Boolean,
)

private val Latte = CatPalette(
    base = Color(0xFFEFF1F5),
    mantle = Color(0xFFE6E9EF),
    surface0 = Color(0xFFCCD0DA),
    surface1 = Color(0xFFBCC0CC),
    text = Color(0xFF4C4F69),
    subtext0 = Color(0xFF6C6F85),
    overlay0 = Color(0xFF9CA0B0),
    mauve = Color(0xFF8839EF),
    red = Color(0xFFD20F39),
    light = true,
)

private val Frappe = CatPalette(
    base = Color(0xFF303446),
    mantle = Color(0xFF292C3C),
    surface0 = Color(0xFF414559),
    surface1 = Color(0xFF51576D),
    text = Color(0xFFC6D0F5),
    subtext0 = Color(0xFFA5ADCE),
    overlay0 = Color(0xFF737994),
    mauve = Color(0xFFCA9EE6),
    red = Color(0xFFE78284),
    light = false,
)

private val Macchiato = CatPalette(
    base = Color(0xFF24273A),
    mantle = Color(0xFF1E2030),
    surface0 = Color(0xFF363A4F),
    surface1 = Color(0xFF494D64),
    text = Color(0xFFCAD3F5),
    subtext0 = Color(0xFFA5ADCB),
    overlay0 = Color(0xFF6E738D),
    mauve = Color(0xFFC6A0F6),
    red = Color(0xFFED8796),
    light = false,
)

private val Mocha = CatPalette(
    base = Color(0xFF1E1E2E),
    mantle = Color(0xFF181825),
    surface0 = Color(0xFF313244),
    surface1 = Color(0xFF45475A),
    text = Color(0xFFCDD6F4),
    subtext0 = Color(0xFFA6ADC8),
    overlay0 = Color(0xFF6C7086),
    mauve = Color(0xFFCBA6F7),
    red = Color(0xFFF38BA8),
    light = false,
)

fun lineSketchLightScheme() = lightColorScheme(
    primary = LineSketch.Ink,
    onPrimary = LineSketch.Paper,
    secondary = LineSketch.InkSoft,
    onSecondary = LineSketch.Paper,
    background = LineSketch.Paper,
    onBackground = LineSketch.Ink,
    surface = LineSketch.Paper,
    onSurface = LineSketch.Ink,
    surfaceVariant = LineSketch.PaperDim,
    onSurfaceVariant = LineSketch.InkSoft,
    outline = LineSketch.Line,
    outlineVariant = LineSketch.LineSoft,
    error = LineSketch.Danger,
    onError = LineSketch.Paper,
)

fun lineSketchDarkScheme() = darkColorScheme(
    primary = Color(0xFFF2F0EB),
    onPrimary = Color(0xFF111111),
    secondary = Color(0xFFC8C4BC),
    onSecondary = Color(0xFF111111),
    background = Color(0xFF111111),
    onBackground = Color(0xFFF2F0EB),
    surface = Color(0xFF161616),
    onSurface = Color(0xFFF2F0EB),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFB8B4AD),
    outline = Color(0xFFF2F0EB),
    outlineVariant = Color(0xFF5A5A5A),
    error = Color(0xFFF2F0EB),
    onError = Color(0xFF111111),
)

private fun catScheme(p: CatPalette) = if (p.light) {
    lightColorScheme(
        primary = p.mauve,
        onPrimary = p.base,
        secondary = p.overlay0,
        onSecondary = p.base,
        background = p.base,
        onBackground = p.text,
        surface = p.surface0,
        onSurface = p.text,
        surfaceVariant = p.surface1,
        onSurfaceVariant = p.subtext0,
        outline = p.overlay0,
        outlineVariant = p.surface1,
        error = p.red,
        onError = p.base,
    )
} else {
    darkColorScheme(
        primary = p.mauve,
        onPrimary = p.base,
        secondary = p.overlay0,
        onSecondary = p.text,
        background = p.base,
        onBackground = p.text,
        surface = p.surface0,
        onSurface = p.text,
        surfaceVariant = p.surface1,
        onSurfaceVariant = p.subtext0,
        outline = p.overlay0,
        outlineVariant = p.surface1,
        error = p.red,
        onError = p.base,
    )
}

fun colorSchemeFor(
    theme: ColorTheme,
    systemDark: Boolean,
) = when (theme) {
    ColorTheme.LineSketchMono -> if (systemDark) lineSketchDarkScheme() else lineSketchLightScheme()
    // 显式深色：纯黑主体，不跟系统浅色走
    ColorTheme.InkDark -> inkDarkScheme()
    ColorTheme.Latte -> catScheme(Latte)
    ColorTheme.Frappe -> catScheme(Frappe)
    ColorTheme.Macchiato -> catScheme(Macchiato)
    ColorTheme.Mocha -> catScheme(Mocha)
}

/** 深色墨黑：黑底 + 浅字，线稿风 */
fun inkDarkScheme() = darkColorScheme(
    primary = Color(0xFFF0EDE8),
    onPrimary = Color(0xFF0A0A0A),
    secondary = Color(0xFFB8B4AD),
    onSecondary = Color(0xFF0A0A0A),
    background = Color(0xFF0A0A0A),
    onBackground = Color(0xFFF0EDE8),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFF0EDE8),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFA8A49C),
    outline = Color(0xFFE8E4DE),
    outlineVariant = Color(0xFF4A4A4A),
    error = Color(0xFFE8E4DE),
    onError = Color(0xFF0A0A0A),
)
