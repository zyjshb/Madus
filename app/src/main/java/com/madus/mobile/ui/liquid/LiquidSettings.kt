package com.madus.mobile.ui.liquid

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.madus.mobile.data.AppearanceMode
import com.madus.mobile.data.ColorTheme
import com.madus.mobile.data.LiquidAppearance
import com.madus.mobile.data.ThemeSettings
import com.madus.mobile.data.VideoGestureMode
import com.madus.mobile.data.VisualTheme
import com.madus.mobile.ui.theme.liquidTokens

@Composable
fun LiquidSettingsScreen(
    settings: ThemeSettings,
    gestureMode: VideoGestureMode,
    onBack: () -> Unit,
    onVisualTheme: (VisualTheme) -> Unit,
    onAppearance: (AppearanceMode) -> Unit,
    onColorTheme: (ColorTheme) -> Unit,
    onLiquidAppearance: (LiquidAppearance) -> Unit,
    onGlassTint: (Float) -> Unit,
    onGestureMode: (VideoGestureMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = liquidTokens()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            LiquidPageHeader(
                title = "主题",
                subtitle = "两套排版，不是换一层颜色",
                onBack = onBack,
            )
        }

        item {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ThemePreviewCard(
                    title = "简约",
                    subtitle = "现在这套",
                    selected = settings.visualTheme == VisualTheme.Classic,
                    onClick = { onVisualTheme(VisualTheme.Classic) },
                    modifier = Modifier.weight(1f),
                    preview = { ClassicMiniPreview() },
                )
                ThemePreviewCard(
                    title = "液态玻璃",
                    subtitle = "另排一版",
                    selected = settings.visualTheme == VisualTheme.LiquidGlass,
                    onClick = { onVisualTheme(VisualTheme.LiquidGlass) },
                    modifier = Modifier.weight(1f),
                    preview = { LiquidMiniPreview() },
                )
            }
            Spacer(Modifier.height(22.dp))
        }

        if (settings.visualTheme == VisualTheme.Classic) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    LiquidSectionLabel("形态")
                    GlassGroup {
                        AppearanceMode.entries.forEachIndexed { i, mode ->
                            LiquidNavRow(
                                title = mode.label,
                                subtitle = when (mode) {
                                    AppearanceMode.LineSketch -> "近直角 · 细描边"
                                    AppearanceMode.SoftGlass -> "大圆角 · 轻透"
                                },
                                onClick = { onAppearance(mode) },
                                trailing = {
                                    if (settings.appearance == mode) {
                                        Text("●", color = tokens.accent)
                                    }
                                },
                            )
                            if (i != AppearanceMode.entries.lastIndex) GlassDivider()
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    LiquidSectionLabel("颜色")
                    GlassGroup {
                        ColorTheme.entries.forEachIndexed { i, theme ->
                            LiquidNavRow(
                                title = theme.label,
                                onClick = { onColorTheme(theme) },
                                trailing = {
                                    if (settings.colorTheme == theme) {
                                        Text("●", color = tokens.accent)
                                    }
                                },
                            )
                            if (i != ColorTheme.entries.lastIndex) GlassDivider()
                        }
                    }
                }
            }
        } else {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    LiquidSectionLabel("深浅")
                    GlassGroup {
                        LiquidAppearance.entries.forEachIndexed { i, mode ->
                            LiquidNavRow(
                                title = mode.label,
                                onClick = { onLiquidAppearance(mode) },
                                trailing = {
                                    if (settings.liquidAppearance == mode) {
                                        Text("●", color = tokens.accent)
                                    }
                                },
                            )
                            if (i != LiquidAppearance.entries.lastIndex) GlassDivider()
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    LiquidSectionLabel("玻璃")
                    GlassSurface(contentPadding = 16.dp) {
                        Column {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("通透", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("着色", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Slider(
                                value = settings.glassTint,
                                onValueChange = onGlassTint,
                                colors = SliderDefaults.colors(
                                    thumbColor = tokens.accent,
                                    activeTrackColor = tokens.accent,
                                ),
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(18.dp))
                LiquidSectionLabel("短视频手势")
                GlassGroup {
                    VideoGestureMode.entries.forEachIndexed { i, mode ->
                        LiquidNavRow(
                            title = mode.label,
                            subtitle = mode.subtitle,
                            onClick = { onGestureMode(mode) },
                            trailing = {
                                if (gestureMode == mode) Text("●", color = tokens.accent)
                            },
                        )
                        if (i != VideoGestureMode.entries.lastIndex) GlassDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePreviewCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit,
) {
    val tokens = liquidTokens()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (selected) Modifier.border(2.dp, tokens.accent, RoundedCornerShape(18.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .then(
                        if (selected) Modifier.border(1.5.dp, tokens.accent, RoundedCornerShape(16.dp))
                        else Modifier,
                    ),
            ) { preview() }
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ClassicMiniPreview() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F5F2))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(Color(0xFF111111)),
        )
        Box(
            Modifier
                .fillMaxWidth(0.7f)
                .height(8.dp)
                .border(1.dp, Color(0xFF111111)),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .border(1.dp, Color(0xFF111111)),
        )
    }
}

@Composable
private fun LiquidMiniPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFD6E4F5)),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(8.dp)
                .fillMaxWidth()
                .height(22.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color.White.copy(alpha = 0.62f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.5f)),
        )
    }
}
