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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.madus.mobile.domain.Track
import com.madus.mobile.ui.components.CoverArt
import com.madus.mobile.ui.theme.liquidTokens
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.CupertinoMaterials
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi

val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

@Composable
fun LiquidBackground(modifier: Modifier = Modifier, coverUrl: String? = null) {
    val tokens = liquidTokens()
    val top = if (tokens.dark) Color(0xFF16161A) else Color(0xFFE4EAF3)
    val bot = if (tokens.dark) Color(0xFF0C0C0E) else Color(0xFFF4F6F9)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, bot))),
    )
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(liquidTokens().cornerCard),
    strong: Boolean = true,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = liquidTokens()
    val haze = LocalHazeState.current
    val container = if (tokens.dark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val tintColor = if (tokens.dark) {
        Color.White.copy(alpha = 0.06f + tokens.tint * 0.10f)
    } else {
        Color.White.copy(alpha = 0.10f + tokens.tint * 0.18f)
    }
    val fill = if (haze != null) {
        Color.Transparent
    } else if (strong) {
        tokens.glassFillStrong
    } else {
        tokens.glassFill
    }
    val hazeMod = if (haze != null) {
        @OptIn(ExperimentalHazeMaterialsApi::class)
        Modifier.hazeEffect(
            state = haze,
            style = CupertinoMaterials.regular(containerColor = container),
        )
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .shadow(
                elevation = 12.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (tokens.dark) 0.35f else 0.08f),
                spotColor = Color.Black.copy(alpha = if (tokens.dark) 0.40f else 0.10f),
            )
            .clip(shape)
            .then(hazeMod)
            .background(fill)
            .border(1.15.dp, tokens.edge.copy(alpha = if (tokens.dark) 0.50f else 0.16f), shape)
            .border(0.6.dp, tokens.rim.copy(alpha = if (tokens.dark) 0.28f else 0.55f), shape)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = tokens.specular),
                        0.14f to Color.Transparent,
                    ),
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        0.88f to Color.Transparent,
                        1f to tokens.edge.copy(alpha = tokens.edge.alpha * 0.85f),
                    ),
                )
            }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (contentPadding > 0.dp) Modifier.padding(contentPadding) else Modifier),
        content = content,
    )
}

@Composable
fun GlassGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface),
        content = content,
    )
}

@Composable
fun GlassDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
        modifier = Modifier.padding(start = 54.dp),
    )
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
        Text(title, style = MaterialTheme.typography.displaySmall)
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
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
            Icon(icon, contentDescription, modifier = Modifier.size(20.dp))
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
        Text(text, style = MaterialTheme.typography.headlineSmall)
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = liquidTokens().accent,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
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
    coverSize: Dp = 48.dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(coverUrl = track.coverUrl, size = coverSize)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist.ifBlank { track.source.displayName },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onCollect != null) {
            Text(
                "加",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onCollect)
                    .padding(8.dp),
            )
        }
        if (onRemove != null) {
            Text(
                "移除",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onRemove)
                    .padding(8.dp),
            )
        }
    }
}
