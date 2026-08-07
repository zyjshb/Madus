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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.madus.mobile.data.BilibiliApi
import com.madus.mobile.domain.Track
import com.madus.mobile.ui.UpSpaceTab
import com.madus.mobile.ui.UpSpaceUiState
import com.madus.mobile.ui.components.CoverArt
import com.madus.mobile.ui.components.MadusImageLoader
import com.madus.mobile.ui.components.normalizeCoverUrl
import com.madus.mobile.ui.theme.appearanceTokens

/**
 * UP 主页：线稿风 + 抖音式结构（头像/数据/关注 + 作品九宫格 / 合集列表）。
 */
@Composable
fun UpSpaceScreen(
    state: UpSpaceUiState,
    onBack: () -> Unit,
    onTab: (UpSpaceTab) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onOpenSeason: (BilibiliApi.UpSeason) -> Unit,
    onCloseSeason: () -> Unit,
    onPrevPage: () -> Unit = {},
    onNextPage: () -> Unit = {},
    onToggleFollow: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val profile = state.profile
    val bg = MaterialTheme.colorScheme.background
    val onBg = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val tokens = appearanceTokens()
    val context = LocalContext.current
    val loader = remember { MadusImageLoader.get(context) }
    val faceUrl = normalizeCoverUrl(profile?.face)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = profile?.name ?: "UP主",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        if (state.isLoading && profile == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载中…", color = muted)
            }
            return
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 88.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(span = { GridItemSpan(3) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!faceUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(faceUrl)
                                        .crossfade(160)
                                        .build(),
                                    contentDescription = profile?.name,
                                    imageLoader = loader,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(84.dp)
                                        .clip(CircleShape),
                                )
                            } else {
                                Text(
                                    text = profile?.name?.take(1) ?: "U",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = onBg,
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            val works = (profile?.archiveCount ?: 0)
                                .takeIf { it > 0 }
                                ?: state.videos.size
                            StatCell(value = formatCount(works.toLong()), label = "作品")
                            StatCell(value = formatCount(profile?.friend ?: 0L), label = "关注")
                            StatCell(value = formatCount(profile?.fans ?: 0L), label = "粉丝")
                            StatCell(value = formatCount(profile?.likeNum ?: 0L), label = "获赞")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = profile?.name ?: "UP主",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = onBg,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // 关注：线稿按钮，同步 B 站
                        val following = profile?.isFollowing == true
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(tokens.cornerSm))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(tokens.cornerSm),
                                )
                                .background(
                                    if (following) {
                                        MaterialTheme.colorScheme.surface
                                    } else {
                                        MaterialTheme.colorScheme.onBackground
                                    },
                                )
                                .clickable(enabled = !state.followBusy && profile != null) {
                                    onToggleFollow()
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = when {
                                    state.followBusy -> "…"
                                    following -> "已关注"
                                    else -> "关注"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (following) {
                                    MaterialTheme.colorScheme.onBackground
                                } else {
                                    MaterialTheme.colorScheme.background
                                },
                            )
                        }
                    }
                    if (!profile?.sign.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = profile?.sign.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = muted,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)),
                    ) {
                        ProfileTab(
                            text = "作品",
                            selected = state.tab == UpSpaceTab.Videos && state.selectedSeason == null,
                            onClick = {
                                onCloseSeason()
                                onTab(UpSpaceTab.Videos)
                            },
                            modifier = Modifier.weight(1f),
                        )
                        ProfileTab(
                            text = "合集",
                            selected = state.tab == UpSpaceTab.Seasons || state.selectedSeason != null,
                            onClick = { onTab(UpSpaceTab.Seasons) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (state.selectedSeason != null) {
                item(span = { GridItemSpan(3) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onCloseSeason) { Text("← 合集列表") }
                        Text(
                            text = state.selectedSeason.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                val list = state.seasonTracks
                if (state.isLoading) {
                    item(span = { GridItemSpan(3) }) {
                        Text("加载合集…", modifier = Modifier.padding(20.dp), color = muted)
                    }
                } else if (list.isEmpty()) {
                    item(span = { GridItemSpan(3) }) {
                        Text("合集暂无内容", modifier = Modifier.padding(20.dp), color = muted)
                    }
                } else {
                    items(list, key = { it.id }) { track ->
                        WorkCell(track = track, onClick = { onPlayTrack(track, list) })
                    }
                }
            } else when (state.tab) {
                UpSpaceTab.Videos -> {
                    if (state.isLoading) {
                        item(span = { GridItemSpan(3) }) {
                            Text("加载作品…", modifier = Modifier.padding(20.dp), color = muted)
                        }
                    } else if (state.videos.isEmpty()) {
                        item(span = { GridItemSpan(3) }) {
                            Text(
                                text = state.error ?: "暂无作品",
                                modifier = Modifier.padding(20.dp),
                                color = muted,
                            )
                        }
                    } else {
                        items(state.videos, key = { it.id }) { track ->
                            WorkCell(
                                track = track,
                                onClick = { onPlayTrack(track, state.videos) },
                            )
                        }
                        // 小说式翻页：与收藏夹相同，上一页/下一页替换本页
                        if (state.total > 40 || state.hasMore || state.page > 1) {
                            item(span = { GridItemSpan(3) }, key = "pager") {
                                val pageSize = 40
                                val totalPages = when {
                                    state.total > 0 ->
                                        ((state.total + pageSize - 1) / pageSize).coerceAtLeast(1)
                                    state.hasMore -> state.page + 1
                                    else -> state.page.coerceAtLeast(1)
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TextButton(
                                        onClick = onPrevPage,
                                        enabled = state.page > 1 && !state.loadingMore,
                                    ) { Text("上一页") }
                                    Text(
                                        text = if (state.loadingMore) {
                                            "加载中…"
                                        } else {
                                            "第 ${state.page} 页" +
                                                if (state.total > 0) {
                                                    " / 约 $totalPages 页 · ${state.total} 个"
                                                } else {
                                                    ""
                                                }
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = muted,
                                    )
                                    TextButton(
                                        onClick = onNextPage,
                                        enabled = (state.hasMore || state.page < totalPages) &&
                                            !state.loadingMore,
                                    ) { Text("下一页") }
                                }
                            }
                        }
                    }
                }
                UpSpaceTab.Seasons -> {
                    if (state.seasons.isEmpty()) {
                        item(span = { GridItemSpan(3) }) {
                            Text(
                                "该 UP 暂无合集/系列",
                                modifier = Modifier.padding(20.dp),
                                color = muted,
                            )
                        }
                    } else {
                        // 线稿列表：一排一条，和曲库/歌单一致
                        items(
                            state.seasons,
                            key = { it.seasonId },
                            span = { GridItemSpan(3) },
                        ) { season ->
                            SeasonRow(
                                season = season,
                                onClick = { onOpenSeason(season) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkCell(track: Track, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(0.75f)
            .clickable(onClick = onClick)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
    ) {
        CoverArt(
            coverUrl = track.coverUrl,
            modifier = Modifier.fillMaxSize(),
            size = 0.dp,
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.78f))
                .padding(horizontal = 4.dp, vertical = 3.dp),
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 线稿合集行：封面 + 标题 + 集数，贴合 Madus 曲库风格 */
@Composable
private fun SeasonRow(
    season: BilibiliApi.UpSeason,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            coverUrl = season.cover.ifBlank { null },
            size = 56.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = season.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    append(if (season.isSeries) "系列" else "合集")
                    if (season.epCount > 0) append(" · ${season.epCount} 集")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = ">",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProfileTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.background
    val fg = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .background(bg)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

private fun formatCount(n: Long): String = when {
    n >= 100_000_000 -> String.format("%.1f亿", n / 100_000_000.0)
    n >= 10_000 -> String.format("%.1f万", n / 10_000.0)
    else -> n.toString()
}
