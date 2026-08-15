package com.madus.mobile.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import java.io.File

data class CanvasPalette(
    val accent: Color,
    val onBg: Color,
    val onMuted: Color,
    val isDark: Boolean,
    val minDim: Float,
) {
    companion object {
        val Fallback = CanvasPalette(
            accent = CanvasGoldSoft,
            onBg = CanvasPaper,
            onMuted = CanvasPaper.copy(alpha = 0.72f),
            isDark = true,
            minDim = 0.56f,
        )
    }
}

fun extractCanvasPalette(path: String?): CanvasPalette {
    val file = path?.takeIf { it.isNotBlank() }?.let { File(it) } ?: return CanvasPalette.Fallback
    if (!file.exists() || file.length() < 80) return CanvasPalette.Fallback
    return runCatching {
        val opts = BitmapFactory.Options().apply {
            inSampleSize = 16
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return CanvasPalette.Fallback
        try {
            val pal = Palette.from(bmp).maximumColorCount(16).clearFilters().generate()
            val dominant = pal.dominantSwatch ?: pal.mutedSwatch ?: return CanvasPalette.Fallback
            val lum = ColorUtils.calculateLuminance(dominant.rgb)
            // 压暗之后几乎总是深底，字用浅色；亮图把最低压暗抬高
            val minDim = (0.42f + lum.toFloat() * 0.48f).coerceIn(0.50f, 0.78f)
            val rawAccent = pal.vibrantSwatch?.rgb
                ?: pal.lightVibrantSwatch?.rgb
                ?: pal.darkVibrantSwatch?.rgb
                ?: dominant.rgb
            val accentRgb = liftWallpaperAccent(rawAccent)
            CanvasPalette(
                accent = Color(accentRgb or 0xFF000000.toInt()),
                onBg = CanvasPaper,
                onMuted = CanvasPaper.copy(alpha = 0.74f),
                isDark = true,
                minDim = minDim,
            )
        } finally {
            bmp.recycle()
        }
    }.getOrDefault(CanvasPalette.Fallback)
}

/** 保住图里的色相，只把饱和度和亮度抬到能当强调色。灰图才退回香槟金。 */
private fun liftWallpaperAccent(rgb: Int): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(rgb or 0xFF000000.toInt(), hsl)
    if (hsl[1] < 0.08f) return 0xE8D5B5.toInt()
    hsl[1] = (hsl[1] * 1.18f + 0.10f).coerceIn(0.38f, 0.82f)
    hsl[2] = hsl[2].coerceIn(0.48f, 0.70f)
    return ColorUtils.HSLToColor(hsl)
}
