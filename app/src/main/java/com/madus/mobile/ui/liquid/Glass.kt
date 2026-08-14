package com.madus.mobile.ui.liquid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.madus.mobile.domain.Track
import com.madus.mobile.ui.components.CoverArt
import com.madus.mobile.ui.components.MadusImageLoader
import com.madus.mobile.ui.theme.CanvasInk
import com.madus.mobile.ui.theme.LiquidType
import com.madus.mobile.ui.theme.liquidTokens
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.CupertinoMaterials
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi

val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

val LocalLiquidChromeBottom = compositionLocalOf { 146.dp }

@Composable
fun LiquidBackground(modifier: Modifier = Modifier, coverUrl: String? = null) {
    val tokens = liquidTokens()
    val path = tokens.wallpaperPath
    val dim = tokens.wallpaperDim
    val context = LocalContext.current
    val loader = remember { MadusImageLoader.get(context) }
    Box(modifier.fillMaxSize()) {
        if (!path.isNullOrBlank()) {
            val file = java.io.File(path)
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(file)
                    .memoryCacheKey("$path-${tokens.wallpaperStamp}-${file.lastModified()}")
                    .diskCacheKey("$path-${tokens.wallpaperStamp}")
                    .crossfade(280)
                    .build(),
                contentDescription = null,
                imageLoader = loader,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1A2A38), Color(0xFF0C141C), CanvasInk),
                        ),
                    ),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = (dim * 0.72f).coerceIn(0.2f, 0.7f)),
                        0.42f to Color.Black.copy(alpha = dim),
                        1f to Color.Black.copy(alpha = (dim + 0.14f).coerceAtMost(0.86f)),
                    ),
                ),
        )
    }
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(liquidTokens().cornerGroup),
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = liquidTokens()
    val haze = LocalHazeState.current
    val hazeMod = if (haze != null) {
        @OptIn(ExperimentalHazeMaterialsApi::class)
        Modifier.hazeEffect(
            state = haze,
            style = CupertinoMaterials.regular(containerColor = tokens.hazeContainer),
        )
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (tokens.dark) 0.28f else 0.06f),
                spotColor = Color.Black.copy(alpha = if (tokens.dark) 0.32f else 0.08f),
            )
            .clip(shape)
            .then(hazeMod)
            .background(tokens.glassFill)
            .border(0.5.dp, tokens.rim, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (contentPadding > 0.dp) Modifier.padding(contentPadding) else Modifier),
        content = content,
    )
}

@Composable
fun InsetGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val tokens = liquidTokens()
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.cornerGroup))
            .background(Color.Black.copy(alpha = 0.22f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(tokens.cornerGroup)),
        content = content,
    )
}

@Composable
fun GlassDivider(start: Dp) {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
        modifier = Modifier.padding(start = start),
    )
}

object InsetDivider {
    @Composable
    fun text() = GlassDivider(start = 16.dp)

    @Composable
    fun icon40() = GlassDivider(start = 68.dp)

    @Composable
    fun cover48() = GlassDivider(start = 74.dp)
}

@Composable
fun GlassPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(999.dp)
    val bg = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surface
    val fg = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground
    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
fun LiquidPageHeader(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(onClick = onBack)
            Spacer(Modifier.weight(1f))
            action?.invoke()
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = LiquidType.headline, color = MaterialTheme.colorScheme.onSurface)
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = LiquidType.subhead,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    icon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    contentDescription: String = "返回",
) {
    GlassSurface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun LiquidSectionLabel(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, style = LiquidType.headline, color = MaterialTheme.colorScheme.onSurface)
        action?.invoke()
    }
}

@Composable
fun LiquidNavRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leading != null -> {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            icon != null -> {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = liquidTokens().accent,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(14.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = LiquidType.body, color = MaterialTheme.colorScheme.onSurface)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = LiquidType.subhead,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) trailing()
        else Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun LiquidTrackRow(
    track: Track,
    onClick: () -> Unit,
    onCollect: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    coverSize: Dp = 48.dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(coverUrl = track.coverUrl, size = coverSize)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                style = LiquidType.body,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist.ifBlank { track.source.displayName },
                style = LiquidType.subhead,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onMore != null) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable(onClick = onMore),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.MoreHoriz,
                    contentDescription = "更多",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
