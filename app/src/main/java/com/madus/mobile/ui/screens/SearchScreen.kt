package com.madus.mobile.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.madus.mobile.domain.Track
import com.madus.mobile.ui.SearchUiState
import com.madus.mobile.ui.components.LineButton
import com.madus.mobile.ui.components.TrackRow
import com.madus.mobile.ui.liquid.LiquidSearchScreen
import com.madus.mobile.ui.theme.isLiquidTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSuggestionClick: (String) -> Unit = {},
    /** 搜索点播：只传点中的那一首；队列保留逻辑在 ViewModel.playSearchTrack */
    onPlayTrack: (Track) -> Unit,
    onCollectTrack: (Track) -> Unit = {},
    onOpenAiSearch: (() -> Unit)? = null,
    onLoadMore: () -> Unit = {},
    /** 换歌模式：顶部提示 + 点结果即替换 */
    replaceHintTitle: String? = null,
    onCancelReplace: (() -> Unit)? = null,
    browseRecent: List<Track> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()

    // 接近底部时拉下一页（对齐 B 站无限滚动）
    LaunchedEffect(listState, state.hasMore, state.loadingMore, state.results.size) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            total > 0 && last >= total - 4
        }
            .distinctUntilChanged()
            .filter { nearEnd -> nearEnd && state.hasMore && !state.loadingMore && !state.isSearching }
            .collect { onLoadMore() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (replaceHintTitle != null) "换歌" else "搜索",
                style = MaterialTheme.typography.displayLarge,
            )
            if (onOpenAiSearch != null && replaceHintTitle == null) {
                Text(
                    text = "AI 搜",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                        .clickable(onClick = onOpenAiSearch)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        if (!replaceHintTitle.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "替换",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        replaceHintTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                    )
                    Text(
                        "搜索后点结果",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onCancelReplace != null) {
                    Text(
                        "取消",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clickable(onClick = onCancelReplace)
                            .padding(8.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            BasicTextField(
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focus.clearFocus()
                        onSubmit()
                    },
                ),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    // 换歌模式：框内完全空白；普通搜索：轻提示
                    if (state.query.isEmpty() && replaceHintTitle == null) {
                        Text(
                            text = "歌名·歌手·UP·BV",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
            LineButton(
                text = if (state.isSearching) "…" else "搜",
                onClick = {
                    focus.clearFocus()
                    onSubmit()
                },
                filled = true,
                enabled = !state.isSearching,
            )
        }

        // 联想词：有结果时改词也要显示（不能再要求 results.isEmpty）
        val showSuggestions = state.suggestions.isNotEmpty()
        if (showSuggestions) {
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
            ) {
                state.suggestions.forEachIndexed { index, tip ->
                    Text(
                        text = tip,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                focus.clearFocus()
                                onSuggestionClick(tip)
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                    if (index != state.suggestions.lastIndex) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        // 正在打字看联想时，先不挤旧结果，点选/提交后再出列表
        if (!showSuggestions) {
            state.message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
            if (state.results.isNotEmpty() && state.total > 0) {
                Text(
                    text = if (state.hasMore || state.results.size < state.total) {
                        "已显示 ${state.results.size} / ${state.total.coerceAtLeast(state.results.size)}"
                    } else {
                        "共 ${state.results.size} 条"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    bottom = if (isLiquidTheme()) {
                        com.madus.mobile.ui.liquid.LocalLiquidChromeBottom.current
                    } else {
                        88.dp
                    },
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(state.results, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        onClick = { onPlayTrack(track) },
                        onCollect = { onCollectTrack(track) },
                    )
                }
                if (state.loadingMore) {
                    item(key = "search-loading-more") {
                        Text(
                            text = "加载更多…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                        )
                    }
                } else if (state.results.isNotEmpty() && !state.hasMore) {
                    item(key = "search-end") {
                        Text(
                            text = "没有更多了",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                        )
                    }
                }
            }
        }
    }
}
