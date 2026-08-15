package com.madus.mobile.ui.liquid

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.madus.mobile.data.AppearanceMode
import com.madus.mobile.data.ColorTheme
import com.madus.mobile.data.LiquidAppearance
import com.madus.mobile.data.ThemeSettings
import com.madus.mobile.data.VideoGestureMode
import com.madus.mobile.data.VisualTheme
import com.madus.mobile.data.WallpaperMode
import com.madus.mobile.ui.components.MadusImageLoader
import com.madus.mobile.ui.theme.CanvasGold
import com.madus.mobile.ui.theme.LiquidType
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
    onPickWallpaper: (String) -> Unit = {},
    onClearWallpaper: () -> Unit = {},
    onWallpaperDim: (Float) -> Unit = {},
    onWallpaperBlur: (Float) -> Unit = {},
    onFollowWallpaperColor: (Boolean) -> Unit = {},
    onWallpaperMode: (WallpaperMode) -> Unit = {},
    onPinWallpaper: () -> Unit = {},
    onRollWallpaper: () -> Unit = {},
    onDownloadWallpaper: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tokens = liquidTokens()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onPickWallpaper(uri.toString())
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            LiquidPageHeader(
                title = "主题",
                subtitle = "简约是线稿。画境是壁纸上的玻璃。",
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
                    title = "画境",
                    subtitle = "壁纸 + 玻璃",
                    selected = settings.visualTheme == VisualTheme.Canvas,
                    onClick = { onVisualTheme(VisualTheme.Canvas) },
                    modifier = Modifier.weight(1f),
                    preview = { CanvasMiniPreview(settings.wallpaperPath) },
                )
            }
            Spacer(Modifier.height(22.dp))
        }

        if (settings.visualTheme == VisualTheme.Classic) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    LiquidSectionLabel("形态")
                    InsetGroup {
                        AppearanceMode.entries.forEachIndexed { i, mode ->
                            LiquidNavRow(
                                title = mode.label,
                                subtitle = when (mode) {
                                    AppearanceMode.LineSketch -> "近直角 · 细描边"
                                    AppearanceMode.SoftGlass -> "大圆角 · 轻透"
                                },
                                onClick = { onAppearance(mode) },
                                trailing = {
                                    if (settings.appearance == mode) Text("●", color = tokens.accent)
                                },
                            )
                            if (i != AppearanceMode.entries.lastIndex) InsetDivider.text()
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    LiquidSectionLabel("颜色")
                    InsetGroup {
                        ColorTheme.entries.forEachIndexed { i, theme ->
                            LiquidNavRow(
                                title = theme.label,
                                onClick = { onColorTheme(theme) },
                                trailing = {
                                    if (settings.colorTheme == theme) Text("●", color = tokens.accent)
                                },
                            )
                            if (i != ColorTheme.entries.lastIndex) InsetDivider.text()
                        }
                    }
                }
            }
        } else {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    LiquidSectionLabel("壁纸")
                    InsetGroup {
                        LiquidNavRow(
                            title = "每日随机",
                            subtitle = "每天从 t.alcy.cc 换一张竖图",
                            onClick = { onWallpaperMode(WallpaperMode.Daily) },
                            trailing = {
                                if (settings.wallpaperMode == WallpaperMode.Daily) Text("●", color = tokens.accent)
                            },
                        )
                        InsetDivider.text()
                        LiquidNavRow(
                            title = "固定这张",
                            subtitle = "一直用现在这张",
                            onClick = onPinWallpaper,
                            trailing = {
                                if (settings.wallpaperMode == WallpaperMode.Pinned) Text("●", color = tokens.accent)
                            },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    InsetGroup {
                        LiquidNavRow("换一张", "再抽一张今日图", onClick = onRollWallpaper)
                        InsetDivider.text()
                        LiquidNavRow("下载这张", "存到相册 / Madus", onClick = onDownloadWallpaper)
                        InsetDivider.text()
                        LiquidNavRow(
                            title = "从相册选择",
                            subtitle = "自己挑一张",
                            onClick = { pick.launch("image/*") },
                            trailing = {
                                if (settings.wallpaperMode == WallpaperMode.Custom) Text("●", color = tokens.accent)
                            },
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    LiquidSectionLabel("配色")
                    InsetGroup {
                        LiquidNavRow(
                            title = "跟随壁纸",
                            subtitle = "强调色从图里抽",
                            onClick = { onFollowWallpaperColor(true) },
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(tokens.accent),
                                    )
                                    if (settings.followWallpaperColor) {
                                        Text("  ●", color = tokens.accent)
                                    }
                                }
                            },
                        )
                        InsetDivider.text()
                        LiquidNavRow(
                            title = "香槟金",
                            subtitle = "不跟图走",
                            onClick = { onFollowWallpaperColor(false) },
                            trailing = {
                                if (!settings.followWallpaperColor) Text("●", color = tokens.accent)
                            },
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    LiquidSectionLabel("压暗")
                    InsetGroup {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("透", style = LiquidType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("实", style = LiquidType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Slider(
                                value = settings.wallpaperDim,
                                onValueChange = onWallpaperDim,
                                valueRange = 0.25f..0.82f,
                                colors = SliderDefaults.colors(
                                    thumbColor = tokens.accent,
                                    activeTrackColor = tokens.accent,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    LiquidSectionLabel("模糊")
                    InsetGroup {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("清", style = LiquidType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("糊", style = LiquidType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Slider(
                                value = settings.wallpaperBlur,
                                onValueChange = onWallpaperBlur,
                                colors = SliderDefaults.colors(
                                    thumbColor = tokens.accent,
                                    activeTrackColor = tokens.accent,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    LiquidSectionLabel("玻璃")
                    InsetGroup {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("通透", style = LiquidType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("着色", style = LiquidType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Slider(
                                value = settings.glassTint,
                                onValueChange = onGlassTint,
                                colors = SliderDefaults.colors(
                                    thumbColor = tokens.accent,
                                    activeTrackColor = tokens.accent,
                                ),
                            )
                            Text(
                                "通透看得到后面。着色带强调色。",
                                style = LiquidType.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            GlassSurface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("预览", style = LiquidType.footnote, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(18.dp))
                LiquidSectionLabel("短视频手势")
                InsetGroup {
                    VideoGestureMode.entries.forEachIndexed { i, mode ->
                        LiquidNavRow(
                            title = mode.label,
                            subtitle = mode.subtitle,
                            onClick = { onGestureMode(mode) },
                            trailing = {
                                if (gestureMode == mode) Text("●", color = tokens.accent)
                            },
                        )
                        if (i != VideoGestureMode.entries.lastIndex) InsetDivider.text()
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
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.22f))
            .then(if (selected) Modifier.border(2.dp, tokens.accent, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .clip(RoundedCornerShape(12.dp)),
        ) { preview() }
        Spacer(Modifier.height(10.dp))
        Text(title, style = LiquidType.headline, color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, style = LiquidType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Box(Modifier.fillMaxWidth().height(10.dp).background(Color(0xFF111111)))
        Box(Modifier.fillMaxWidth(0.7f).height(8.dp).border(1.dp, Color(0xFF111111)))
        Box(Modifier.fillMaxWidth().height(28.dp).border(1.dp, Color(0xFF111111)))
    }
}

@Composable
private fun CanvasMiniPreview(wallpaperPath: String?) {
    val context = LocalContext.current
    val loader = androidx.compose.runtime.remember { MadusImageLoader.get(context) }
    Box(Modifier.fillMaxSize()) {
        if (!wallpaperPath.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(java.io.File(wallpaperPath)).build(),
                contentDescription = null,
                imageLoader = loader,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF1A2A38), Color(0xFF07090C)))),
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(8.dp)
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.18f)),
        ) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(CanvasGold))
                Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.35f)))
            }
        }
    }
}
