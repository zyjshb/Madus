package com.madus.mobile.ui.liquid

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.madus.mobile.domain.PlaybackState
import com.madus.mobile.ui.components.CoverArt
import com.madus.mobile.ui.navigation.RootTab
import com.madus.mobile.ui.theme.LiquidChromeMetrics
import com.madus.mobile.ui.theme.LiquidType
import com.madus.mobile.ui.theme.MadusMotion
import com.madus.mobile.ui.theme.iosClickable
import com.madus.mobile.ui.theme.liquidTokens

private data class LiquidTab(
    val tab: RootTab,
    val outlined: ImageVector,
    val filled: ImageVector,
    val label: String,
)

private val liquidTabs = listOf(
    LiquidTab(RootTab.Home, Icons.Outlined.Home, Icons.Filled.Home, "首页"),
    LiquidTab(RootTab.Search, Icons.Outlined.Search, Icons.Filled.Search, "搜索"),
    LiquidTab(RootTab.Recommend, Icons.Outlined.Album, Icons.Filled.Album, "电台"),
    LiquidTab(RootTab.Library, Icons.Outlined.LibraryMusic, Icons.Filled.LibraryMusic, "曲库"),
    LiquidTab(RootTab.Me, Icons.Outlined.Person, Icons.Filled.Person, "我的"),
)

@Composable
fun LiquidFloatingChrome(
    route: String?,
    playback: PlaybackState,
    showMini: Boolean,
    showTabs: Boolean,
    onSelectTab: (RootTab) -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    onOpenMini: () -> Unit,
    modifier: Modifier = Modifier,
    onChromeHeightPx: (Int) -> Unit = {},
) {
    var lastHeight by remember { mutableIntStateOf(-1) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                val h = coords.size.height
                if (h != lastHeight) {
                    lastHeight = h
                    onChromeHeightPx(h)
                }
            }
            .padding(
                start = LiquidChromeMetrics.hInset,
                end = LiquidChromeMetrics.hInset,
                bottom = LiquidChromeMetrics.chromeBottom,
            )
            .windowInsetsPadding(WindowInsets.navigationBars),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LiquidChromeMetrics.gap),
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
                contentPadding = 0.dp,
                onClick = onOpenMini,
            ) {
                LiquidMiniRow(
                    playback = playback,
                    onToggle = onToggle,
                    onPrevious = onPrevious,
                    onNext = onNext,
                )
            }
        }
        if (showTabs) {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
                contentPadding = 0.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(LiquidChromeMetrics.tabHeight),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    liquidTabs.forEach { item ->
                        val selected = route == item.tab.route
                        val accent = liquidTokens().accent
                        val tint by animateColorAsState(
                            targetValue = if (selected) {
                                accent
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                            },
                            animationSpec = MadusMotion.color,
                            label = "tabTint",
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = if (selected) 1.08f else 1f,
                            animationSpec = MadusMotion.press,
                            label = "tabIcon",
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(LiquidChromeMetrics.tabHeight)
                                .widthIn(min = 44.dp)
                                .iosClickable(pressScale = 0.92f) { onSelectTab(item.tab) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                if (selected) item.filled else item.outlined,
                                contentDescription = item.label,
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
                                tint = tint,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                item.label,
                                style = LiquidType.caption,
                                color = tint,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidMiniRow(
    playback: PlaybackState,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val track = playback.current ?: return
    val switching = playback.isLoading && !playback.isPlaying
    val press = remember { MutableInteractionSource() }
    val down by press.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        if (down) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "miniPlay",
    )
    val tokens = liquidTokens()
    val secondary = MaterialTheme.colorScheme.onSurface.copy(
        alpha = if (tokens.dark) 0.72f else 0.62f,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(LiquidChromeMetrics.miniHeight)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(coverUrl = track.coverUrl, size = 44.dp)
        Spacer(Modifier.width(10.dp))
        AnimatedContent(
            targetState = track.id to track.title,
            transitionSpec = {
                fadeIn(MadusMotion.fade) togetherWith fadeOut(MadusMotion.tabFade)
            },
            modifier = Modifier.weight(1f),
            label = "miniTitle",
        ) { (_, title) ->
            Column {
                Text(
                    title,
                    style = LiquidType.headline,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.artist,
                    style = LiquidType.footnote,
                    color = secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable(onClick = onPrevious),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.SkipPrevious,
                contentDescription = "上一首",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer {
                    scaleX = playScale
                    scaleY = playScale
                }
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
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Icon(
                    imageVector = if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playback.isPlaying) "暂停" else "播放",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable(onClick = onNext),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.SkipNext,
                contentDescription = "下一首",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
