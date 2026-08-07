package com.madus.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeStore by preferencesDataStore(name = "madus_theme")

/** 外观形态：圆滑玻璃（默认） / 方角线稿 */
enum class AppearanceMode(val id: String, val label: String) {
    SoftGlass("soft_glass", "圆滑玻璃"),
    LineSketch("line_sketch", "方角线稿"),
    ;

    companion object {
        fun fromId(id: String?) = entries.find { it.id == id } ?: SoftGlass
    }
}

/** 主题色：线稿黑白（默认）+ 深色墨黑 + Catppuccin 四味 */
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
    val appearance: AppearanceMode = AppearanceMode.SoftGlass,
    val colorTheme: ColorTheme = ColorTheme.LineSketchMono,
)

class ThemePrefs(private val context: Context) {
    private val keyAppearance = stringPreferencesKey("appearance_mode")
    private val keyColor = stringPreferencesKey("color_theme")

    val flow: Flow<ThemeSettings> = context.themeStore.data.map { prefs ->
        ThemeSettings(
            appearance = AppearanceMode.fromId(prefs[keyAppearance]),
            colorTheme = ColorTheme.fromId(prefs[keyColor]),
        )
    }

    suspend fun setAppearance(mode: AppearanceMode) {
        context.themeStore.edit { it[keyAppearance] = mode.id }
    }

    suspend fun setColorTheme(theme: ColorTheme) {
        context.themeStore.edit { it[keyColor] = theme.id }
    }
}
