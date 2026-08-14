package com.madus.mobile.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

enum class WallpaperMode(val id: String, val label: String) {
    Daily("daily", "每日随机"),
    Pinned("pinned", "固定这张"),
    Custom("custom", "相册自选"),
    ;

    companion object {
        fun fromId(id: String?) = entries.find { it.id == id } ?: Daily
    }
}

data class ThemeSettings(
    val visualTheme: VisualTheme = VisualTheme.Classic,
    val appearance: AppearanceMode = AppearanceMode.SoftGlass,
    val colorTheme: ColorTheme = ColorTheme.LineSketchMono,
    val liquidAppearance: LiquidAppearance = LiquidAppearance.FollowSystem,
    val glassTint: Float = 0.42f,
    val wallpaperPath: String? = null,
    val wallpaperRemoteUrl: String? = null,
    val wallpaperMode: WallpaperMode = WallpaperMode.Daily,
    val wallpaperDay: String? = null,
    val wallpaperDim: Float = 0.55f,
    val wallpaperStamp: Long = 0L,
)

class ThemePrefs(private val context: Context) {
    private val wallpaperLock = Mutex()

    private val keyVisual = stringPreferencesKey("visual_theme")
    private val keyAppearance = stringPreferencesKey("appearance_mode")
    private val keyColor = stringPreferencesKey("color_theme")
    private val keyLiquidAppearance = stringPreferencesKey("liquid_appearance")
    private val keyTint = floatPreferencesKey("glass_tint")
    private val keyWallpaper = stringPreferencesKey("wallpaper_path")
    private val keyWallpaperUrl = stringPreferencesKey("wallpaper_remote")
    private val keyWallpaperMode = stringPreferencesKey("wallpaper_mode")
    private val keyWallpaperDay = stringPreferencesKey("wallpaper_day")
    private val keyWallpaperStamp = androidx.datastore.preferences.core.longPreferencesKey("wallpaper_stamp")
    private val keyDim = floatPreferencesKey("wallpaper_dim")

    val flow: Flow<ThemeSettings> = context.themeStore.data.map { prefs ->
        ThemeSettings(
            visualTheme = VisualTheme.fromId(prefs[keyVisual]),
            appearance = AppearanceMode.fromId(prefs[keyAppearance]),
            colorTheme = ColorTheme.fromId(prefs[keyColor]),
            liquidAppearance = LiquidAppearance.fromId(prefs[keyLiquidAppearance]),
            glassTint = (prefs[keyTint] ?: 0.42f).coerceIn(0f, 1f),
            wallpaperPath = prefs[keyWallpaper],
            wallpaperRemoteUrl = prefs[keyWallpaperUrl],
            wallpaperMode = WallpaperMode.fromId(prefs[keyWallpaperMode]),
            wallpaperDay = prefs[keyWallpaperDay],
            wallpaperDim = (prefs[keyDim] ?: 0.55f).coerceIn(0.25f, 0.82f),
            wallpaperStamp = prefs[keyWallpaperStamp] ?: 0L,
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

    suspend fun setWallpaperMode(mode: WallpaperMode) {
        context.themeStore.edit { it[keyWallpaperMode] = mode.id }
        if (mode == WallpaperMode.Daily) ensureDailyWallpaper(force = false)
    }

    suspend fun setWallpaperFromUri(uri: String) = wallpaperLock.withLock {
        val persisted = runCatching {
            val input = context.contentResolver.openInputStream(Uri.parse(uri)) ?: return@runCatching null
            val dir = File(context.filesDir, "theme").also { it.mkdirs() }
            val out = File(dir, "wallpaper.jpg")
            input.use { inp -> out.outputStream().use { o -> inp.copyTo(o) } }
            out.absolutePath
        }.getOrNull()
        if (persisted != null) {
            context.themeStore.edit {
                it[keyWallpaper] = persisted
                it[keyWallpaperMode] = WallpaperMode.Custom.id
                it[keyWallpaperStamp] = System.currentTimeMillis()
                it.remove(keyWallpaperUrl)
            }
        }
    }

    suspend fun pinCurrentWallpaper() = wallpaperLock.withLock {
        val prefs = context.themeStore.data.first()
        val stored = prefs[keyWallpaper]?.let { File(it) }?.takeIf { it.exists() && it.length() > 80 }
        val src = stored ?: currentWallpaperFile() ?: return@withLock
        val pin = File(context.filesDir, "theme/wallpaper.jpg")
        pin.parentFile?.mkdirs()
        runCatching {
            if (src.canonicalPath != pin.canonicalPath) {
                src.copyTo(pin, overwrite = true)
            }
        }.onFailure { return@withLock }
        context.themeStore.edit {
            it[keyWallpaper] = pin.absolutePath
            it[keyWallpaperMode] = WallpaperMode.Pinned.id
            it[keyWallpaperStamp] = System.currentTimeMillis()
        }
    }

    suspend fun ensureDailyWallpaper(force: Boolean = false): Boolean = wallpaperLock.withLock {
        val today = java.time.LocalDate.now().toString()
        val dest = File(context.filesDir, "theme/daily.webp")
        val prefs = context.themeStore.data.first()
        val mode = WallpaperMode.fromId(prefs[keyWallpaperMode])
        if (!force && mode != WallpaperMode.Daily) return@withLock false
        if (!force && prefs[keyWallpaperDay] == today && dest.exists() && dest.length() > 80) {
            if (prefs[keyWallpaper] != dest.absolutePath) {
                context.themeStore.edit { it[keyWallpaper] = dest.absolutePath }
            }
            return@withLock true
        }
        val url = AlcyWallpaper.fetchRandomUrl() ?: return@withLock false
        if (!AlcyWallpaper.download(url, dest)) return@withLock false
        dest.setLastModified(System.currentTimeMillis())
        context.themeStore.edit {
            it[keyWallpaper] = dest.absolutePath
            it[keyWallpaperUrl] = url
            it[keyWallpaperDay] = today
            it[keyWallpaperStamp] = System.currentTimeMillis()
            if (mode != WallpaperMode.Custom && mode != WallpaperMode.Pinned) {
                it[keyWallpaperMode] = WallpaperMode.Daily.id
            }
        }
        true
    }

    suspend fun rollNewDailyWallpaper(): Boolean {
        context.themeStore.edit { it[keyWallpaperMode] = WallpaperMode.Daily.id }
        return ensureDailyWallpaper(force = true)
    }

    suspend fun saveCurrentWallpaperToGallery(): Boolean {
        val src = currentWallpaperFile() ?: return false
        return runCatching {
            val name = "Madus-${System.currentTimeMillis()}.webp"
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/webp")
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    put(
                        android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_PICTURES + "/Madus",
                    )
                }
            }
            val uri = context.contentResolver.insert(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values,
            ) ?: return@runCatching false
            context.contentResolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    private fun currentWallpaperFile(): File? {
        val pin = File(context.filesDir, "theme/wallpaper.jpg")
        val daily = File(context.filesDir, "theme/daily.webp")
        return when {
            pin.exists() && pin.length() > 80 -> pin
            daily.exists() && daily.length() > 80 -> daily
            else -> null
        }
    }

    suspend fun clearWallpaper() {
        runCatching { File(context.filesDir, "theme/wallpaper.jpg").delete() }
        runCatching { File(context.filesDir, "theme/daily.webp").delete() }
        context.themeStore.edit {
            it.remove(keyWallpaper)
            it.remove(keyWallpaperUrl)
            it.remove(keyWallpaperDay)
            it[keyWallpaperMode] = WallpaperMode.Daily.id
        }
        ensureDailyWallpaper(force = true)
    }
}
