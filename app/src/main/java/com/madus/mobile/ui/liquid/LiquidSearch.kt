package com.madus.mobile.ui.liquid

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.madus.mobile.domain.Track
import com.madus.mobile.ui.SearchUiState
import com.madus.mobile.ui.theme.LiquidType
import com.madus.mobile.ui.theme.liquidTokens
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun LiquidSearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSuggestionClick: (String) -> Unit = {},
    onPlayTrack: (Track) -> Unit,
    onCollectTrack: (Track) -> Unit = {},
    onOpenAiSearch: (() -> Unit)? = null,
    onLoadMore: () -> Unit = {},
    replaceHintTitle: String? = null,
    onCancelReplace: (() -> Unit)? = null,
    browseRecent: List<Track> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    val tokens = liquidTokens()
    var fieldFocused by remember { mutableStateOf(false) }

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

    val showSuggestions = state.suggestions.isNotEmpty()
    val idle = state.query.isEmpty() && state.results.isEmpty() && !showSuggestions && replaceHintTitle == null

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 12.dp,
                bottom = LocalLiquidChromeBottom.current,
            ),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        if (replaceHintTitle != null) "换一首" else "搜索",
                        style = LiquidType.largeTitle,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (onOpenAiSearch != null && replaceHintTitle == null) {
                        Text(
                            "AI 搜",
                            style = LiquidType.footnote,
                            color = tokens.accent,
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .clickable(onClick = onOpenAiSearch)
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                        )
                    }
                }
            }

            if (!replaceHintTitle.isNullOrBlank()) {
                item {
                    Spacer(Modifier.height(14.dp))
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text("替换", style = LiquidType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                replaceHintTitle,
                                style = LiquidType.body,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (onCancelReplace != null) {
                                Text(
                                    "取消",
                                    color = tokens.accent,
                                    style = LiquidType.footnote,
                                    modifier = Modifier
                                        .clickable(onClick = onCancelReplace)
                                        .padding(8.dp),
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LiquidSearchField(
                        query = state.query,
                        placeholder = if (replaceHintTitle == null) "歌曲、UP、BV" else "",
                        onQueryChange = onQueryChange,
                        onSubmit = {
                            focus.clearFocus()
                            onSubmit()
                        },
                        onFocusChange = { fieldFocused = it },
                        modifier = Modifier.weight(1f),
                    )
                    if (fieldFocused || state.query.isNotEmpty()) {
                        Text(
                            "取消",
                            style = LiquidType.footnote.copy(fontWeight = FontWeight.SemiBold),
                            color = tokens.accent,
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .clickable {
                                    onQueryChange("")
                                    focus.clearFocus()
                                }
                                .padding(start = 10.dp, top = 12.dp, bottom = 12.dp),
                        )
                    }
                }
            }

            if (showSuggestions) {
                item {
                    Spacer(Modifier.height(16.dp))
                    state.suggestions.forEach { tip ->
                        Text(
                            tip,
                            style = LiquidType.body,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .clickable {
                                    focus.clearFocus()
                                    onSuggestionClick(tip)
                                }
                                .padding(vertical = 11.dp),
                        )
                    }
                }
            }

            if (idle && browseRecent.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(22.dp))
                    LiquidSectionLabel("最近听过")
                    LiquidMusicShelf {
                        browseRecent.take(12).forEach { track ->
                            LiquidShelfCard(track.title, track.artist, track.coverUrl) {
                                onPlayTrack(track)
                            }
                        }
                    }
                }
            }

            if (!showSuggestions && !idle) {
                item {
                    Spacer(Modifier.height(14.dp))
                    state.message?.let {
                        Text(it, style = LiquidType.subhead, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                    }
                    if (state.results.isNotEmpty() && state.total > 0) {
                        Text(
                            if (state.hasMore || state.results.size < state.total) {
                                "已显示 ${state.results.size} / ${state.total.coerceAtLeast(state.results.size)}"
                            } else {
                                "共 ${state.results.size} 条"
                            },
                            style = LiquidType.footnote,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
                state.results.forEach { track ->
                    item(key = track.id) {
                        LiquidTrackRow(
                            track = track,
                            onClick = { onPlayTrack(track) },
                            onMore = { onCollectTrack(track) },
                            coverSize = 56.dp,
                        )
                    }
                }
                if (state.loadingMore) {
                    item {
                        Text(
                            "加载更多…",
                            style = LiquidType.subhead,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidSearchField(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.heightIn(min = 44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = LiquidType.body.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { onFocusChange(it.isFocused) },
                decorationBox = { inner ->
                    if (query.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            placeholder,
                            style = LiquidType.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
        }
    }
}
