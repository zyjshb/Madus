package com.madus.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.madus.mobile.data.AppearanceMode
import com.madus.mobile.data.ColorTheme
import com.madus.mobile.data.ThemeSettings
import com.madus.mobile.data.VideoGestureMode
import com.madus.mobile.ui.components.SectionTitle
import com.madus.mobile.ui.theme.appearanceTokens

/** 外观 + 短视频操作模式（视频/音乐开关仍在推荐页）。 */
@Composable
fun SettingsScreen(
    settings: ThemeSettings,
    videoMode: Boolean = false,
    gestureMode: VideoGestureMode = VideoGestureMode.DOUYIN,
    onBack: () -> Unit,
    onAppearance: (AppearanceMode) -> Unit,
    onColorTheme: (ColorTheme) -> Unit,
    onVideoMode: (Boolean) -> Unit = {},
    onGestureMode: (VideoGestureMode) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tokens = appearanceTokens()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("设置", style = MaterialTheme.typography.headlineMedium)
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Text(
                    text = "音乐/视频模式：推荐页上滑菜单里切换。音质与音效：「我的 → 播放设置」。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                SectionTitle(text = "短视频操作模式")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "对照抖音 / B站 / 快手的手势习惯。影响清屏与横屏全屏的单击、长按与控件显隐。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                VideoGestureMode.entries.forEach { mode ->
                    SelectRow(
                        title = mode.label,
                        subtitle = mode.subtitle,
                        selected = gestureMode == mode,
                        onClick = { onGestureMode(mode) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            item {
                SectionTitle(text = "外观形态")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "形态与颜色分开选。默认保持方角线稿。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                AppearanceMode.entries.forEach { mode ->
                    SelectRow(
                        title = mode.label,
                        subtitle = when (mode) {
                            AppearanceMode.LineSketch -> "近直角 · 1px 描边 · 纸感（默认）"
                            AppearanceMode.SoftGlass -> "大圆角 · 轻透面板 · 圆滑玻璃"
                        },
                        selected = settings.appearance == mode,
                        onClick = { onAppearance(mode) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            item {
                SectionTitle(text = "主题色")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "线稿黑白为默认；深色墨黑为纯黑主体。其余参考 Catppuccin。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                ColorTheme.entries.forEach { theme ->
                    ColorThemeRow(
                        theme = theme,
                        selected = settings.colorTheme == theme,
                        onClick = { onColorTheme(theme) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            item {
                SectionTitle(text = "预览")
                Spacer(Modifier.height(10.dp))
                val shape = RoundedCornerShape(tokens.cornerMd)
                val previewBg = if (tokens.mode == AppearanceMode.SoftGlass) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = tokens.panelAlpha)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = tokens.panelAlpha)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(shape)
                        .border(
                            tokens.borderWidth,
                            MaterialTheme.colorScheme.outline.copy(
                                alpha = if (tokens.mode == AppearanceMode.SoftGlass) 0.22f else 1f,
                            ),
                            shape,
                        )
                        .background(previewBg)
                        .padding(16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Column {
                        Text(
                            "当前：${settings.appearance.label} · ${settings.colorTheme.label}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "圆角 ${tokens.cornerMd.value.toInt()}dp",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(48.dp)) }
        }
    }
}

@Composable
private fun SelectRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = appearanceTokens()
    val shape = RoundedCornerShape(tokens.cornerMd)
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(tokens.borderWidth, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Text("●", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ColorThemeRow(
    theme: ColorTheme,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = appearanceTokens()
    val shape = RoundedCornerShape(tokens.cornerMd)
    val swatches = themeSwatches(theme)
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(tokens.borderWidth, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            swatches.forEach { c ->
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(tokens.cornerXs))
                        .background(c)
                        .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(tokens.cornerXs)),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            theme.label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (selected) Text("●", color = MaterialTheme.colorScheme.primary)
    }
}

private fun themeSwatches(theme: ColorTheme): List<Color> = when (theme) {
    ColorTheme.LineSketchMono -> listOf(Color(0xFFF7F5F2), Color(0xFF111111), Color(0xFF7A7A7A))
    ColorTheme.InkDark -> listOf(Color(0xFF0A0A0A), Color(0xFFF0EDE8), Color(0xFF4A4A4A))
    ColorTheme.Latte -> listOf(Color(0xFFEFF1F5), Color(0xFF8839EF), Color(0xFF4C4F69))
    ColorTheme.Frappe -> listOf(Color(0xFF303446), Color(0xFFCA9EE6), Color(0xFFC6D0F5))
    ColorTheme.Macchiato -> listOf(Color(0xFF24273A), Color(0xFFC6A0F6), Color(0xFFCAD3F5))
    ColorTheme.Mocha -> listOf(Color(0xFF1E1E2E), Color(0xFFCBA6F7), Color(0xFFCDD6F4))
}
