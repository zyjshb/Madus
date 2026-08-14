package com.madus.mobile.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

private val Context.themeStore by preferencesDataStore(name = "madus_theme")

/** 顶层主题：简约（默认，现有排版）/ 画境（壁纸 + 玻璃） */
enum class VisualTheme(val id: String, val label: String) {
    Classic("classic", "简约"),
    Canvas("canvas", "画境"),
    ;

    companion object {
        fun fromId(id: String?) = when (id) {
            "liquid_glass" -> Canvas
            else -> entries.find { it.id == id } ?: Classic
        }
    }
}

/** 画境的深浅（玻璃层） */
enum class LiquidAppearance(val id: String, val label: String) {
    FollowSystem("system", "跟随系统"),
    Light("light", "浅色"),
    Dark("dark", "深色"),
    ;

    companion object {
        fun fromId(id: String?) = entries.find { it.id == id } ?: FollowSystem
    }
}

/** 简约主题下的外观形态：圆滑玻璃 / 方角线稿 */
enum class AppearanceMode(val id: String, val label: String) {
    SoftGlass("soft_glass", "圆滑玻璃"),
    LineSketch("line_sketch", "方角线稿"),
    ;

    companion object {
        fun fromId(id: String?) = entries.find { it.id == id } ?: SoftGlass
    }
}

/** 简约主题色：线稿黑白（默认）+ 深色墨黑 + Catppuccin 四味 */
enum class ColorTheme(val id: String, val label: String) {
    LineSketchMono("mono", "线稿黑白"),
    InkDark("ink_dark", "深色墨黑"),
    Latte("latte", "Catppuccin Latte"),
    Frappe("frappe", "Catppuccin Frappé"),
    Macchiato("macchiato", "Catppuccin Macchiato"),
    Mocha("mocha", "Catppuccin Mocha"),
    ;

    companion object {
        fun fromId(id: String?) = entries.find { it.id == id } ?: LineSketchMono
    }
}

data class ThemeSettings(
    val visualTheme: VisualTheme = VisualTheme.Classic,
    val appearance: AppearanceMode = AppearanceMode.SoftGlass,
    val colorTheme: ColorTheme = ColorTheme.LineSketchMono,
    val liquidAppearance: LiquidAppearance = LiquidAppearance.FollowSystem,
    /** 0 = 通透，1 = 着色。默认略实，字好认。 */
    val glassTint: Float = 0.42f,
    val wallpaperPath: String? = null,
    /** 壁纸压暗 0–1，越高字越好认 */
    val wallpaperDim: Float = 0.55f,
)

class ThemePrefs(private val context: Context) {
    private val keyVisual = stringPreferencesKey("visual_theme")
    private val keyAppearance = stringPreferencesKey("appearance_mode")
    private val keyColor = stringPreferencesKey("color_theme")
    private val keyLiquidAppearance = stringPreferencesKey("liquid_appearance")
    private val keyTint = floatPreferencesKey("glass_tint")
    private val keyWallpaper = stringPreferencesKey("wallpaper_path")
    private val keyDim = floatPreferencesKey("wallpaper_dim")

    val flow: Flow<ThemeSettings> = context.themeStore.data.map { prefs ->
        ThemeSettings(
            visualTheme = VisualTheme.fromId(prefs[keyVisual]),
            appearance = AppearanceMode.fromId(prefs[keyAppearance]),
            colorTheme = ColorTheme.fromId(prefs[keyColor]),
            liquidAppearance = LiquidAppearance.fromId(prefs[keyLiquidAppearance]),
            glassTint = (prefs[keyTint] ?: 0.42f).coerceIn(0f, 1f),
            wallpaperPath = prefs[keyWallpaper],
            wallpaperDim = (prefs[keyDim] ?: 0.55f).coerceIn(0.25f, 0.82f),
        )
    }

    suspend fun setVisualTheme(theme: VisualTheme) {
        context.themeStore.edit { it[keyVisual] = theme.id }
    }

    suspend fun setAppearance(mode: AppearanceMode) {
        context.themeStore.edit { it[keyAppearance] = mode.id }
    }

    suspend fun setColorTheme(theme: ColorTheme) {
        context.themeStore.edit { it[keyColor] = theme.id }
    }

    suspend fun setLiquidAppearance(mode: LiquidAppearance) {
        context.themeStore.edit { it[keyLiquidAppearance] = mode.id }
    }

    suspend fun setGlassTint(tint: Float) {
        context.themeStore.edit { it[keyTint] = tint.coerceIn(0f, 1f) }
    }

    suspend fun setWallpaperDim(dim: Float) {
        context.themeStore.edit { it[keyDim] = dim.coerceIn(0.25f, 0.82f) }
    }

    suspend fun setWallpaperFromUri(uri: String) {
        val persisted = runCatching {
            val input = context.contentResolver.openInputStream(Uri.parse(uri)) ?: return@runCatching null
            val dir = File(context.filesDir, "theme").also { it.mkdirs() }
            val out = File(dir, "wallpaper.jpg")
            input.use { inp -> out.outputStream().use { o -> inp.copyTo(o) } }
            out.absolutePath
        }.getOrNull()
        if (persisted != null) {
            context.themeStore.edit { it[keyWallpaper] = persisted }
        }
    }

    suspend fun clearWallpaper() {
        runCatching { File(context.filesDir, "theme/wallpaper.jpg").delete() }
        context.themeStore.edit { it.remove(keyWallpaper) }
    }
}
