package com.madus.mobile.ui.liquid

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.madus.mobile.domain.PlaybackState
import com.madus.mobile.ui.components.CoverArt
import com.madus.mobile.ui.navigation.RootTab
import com.madus.mobile.ui.theme.liquidTokens

private data class LiquidTab(
    val tab: RootTab,
    val icon: ImageVector,
)

private val liquidTabs = listOf(
    LiquidTab(RootTab.Home, Icons.Outlined.Home),
    LiquidTab(RootTab.Search, Icons.Outlined.Search),
    LiquidTab(RootTab.Recommend, Icons.Outlined.Album),
    LiquidTab(RootTab.Library, Icons.Outlined.LibraryMusic),
    LiquidTab(RootTab.Me, Icons.Outlined.Person),
)

@Composable
fun LiquidFloatingChrome(
    route: String?,
    playback: PlaybackState,
    showMini: Boolean,
    showTabs: Boolean,
    onSelectTab: (RootTab) -> Unit,
    onToggle: () -> Unit,
    onOpenMini: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(
            visible = showMini && playback.current != null,
            enter = slideInVertically(
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                initialOffsetY = { it },
            ) + fadeIn(tween(180)),
            exit = slideOutVertically(
                animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium),
                targetOffsetY = { it / 2 },
            ) + fadeOut(tween(140)),
        ) {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
                contentPadding = 8.dp,
                onClick = onOpenMini,
            ) {
                LiquidMiniRow(playback = playback, onToggle = onToggle)
            }
        }
        if (showTabs) {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
                contentPadding = 4.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    liquidTabs.forEach { item ->
                        val selected = route == item.tab.route
                        val accent = liquidTokens().accent
                        val scale by animateFloatAsState(
                            if (selected) 1.1f else 1f,
                            animationSpec = spring(
                                dampingRatio = 0.62f,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                            label = "tabScale",
                        )
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .clip(CircleShape)
                                .then(
                                    if (selected) {
                                        Modifier.background(accent.copy(alpha = 0.16f))
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { onSelectTab(item.tab) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = item.tab.label,
                                modifier = Modifier.size(24.dp),
                                tint = if (selected) {
                                    accent
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                                },
                            )
                        }
                    }
                }
            }
        }
        // 全面屏提示条，像安卓手势条
        Box(
            modifier = Modifier
                .padding(top = 4.dp, bottom = 6.dp)
                .width(108.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.28f)),
        )
    }
}

@Composable
private fun LiquidMiniRow(
    playback: PlaybackState,
    onToggle: () -> Unit,
) {
    val track = playback.current ?: return
    val switching = playback.isLoading && !playback.isPlaying
    val press = remember { MutableInteractionSource() }
    val down by press.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        if (down) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "miniPlay",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(coverUrl = track.coverUrl, size = 36.dp)
        Spacer(Modifier.width(10.dp))
        AnimatedContent(
            targetState = track.id to track.title,
            transitionSpec = {
                fadeIn(tween(180)) togetherWith fadeOut(tween(140))
            },
            modifier = Modifier.weight(1f),
            label = "miniTitle",
        ) { (_, title) ->
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .graphicsLayer {
                    scaleX = playScale
                    scaleY = playScale
                }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onBackground)
                .clickable(
                    interactionSource = press,
                    indication = null,
                    enabled = !switching,
                    onClick = onToggle,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (switching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.background,
                )
            } else {
                Icon(
                    imageVector = if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playback.isPlaying) "暂停" else "播放",
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
