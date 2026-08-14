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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.madus.mobile.domain.Track
import com.madus.mobile.ui.SearchUiState
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
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    val tokens = liquidTokens()

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
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 148.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                if (replaceHintTitle != null) "换一首" else "搜索",
                style = MaterialTheme.typography.displaySmall,
            )
            if (onOpenAiSearch != null && replaceHintTitle == null) {
                GlassPill("AI 搜", selected = false, onClick = onOpenAiSearch)
            }
        }

        if (!replaceHintTitle.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            GlassSurface(contentPadding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("替换", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(replaceHintTitle, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (onCancelReplace != null) {
                        Text(
                            "取消",
                            color = tokens.accent,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .clickable(onClick = onCancelReplace)
                                .padding(8.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focus.clearFocus()
                        onSubmit()
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (state.query.isEmpty() && replaceHintTitle == null) {
                        Text(
                            "歌名、歌手、UP、BV",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
        }

        val showSuggestions = state.suggestions.isNotEmpty()
        if (showSuggestions) {
            Spacer(Modifier.height(12.dp))
            GlassGroup {
                state.suggestions.forEachIndexed { index, tip ->
                    Text(
                        tip,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                focus.clearFocus()
                                onSuggestionClick(tip)
                            }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                    )
                    if (index != state.suggestions.lastIndex) GlassDivider()
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        if (!showSuggestions) {
            state.message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }
            if (state.results.isNotEmpty() && state.total > 0) {
                Text(
                    if (state.hasMore || state.results.size < state.total) {
                        "已显示 ${state.results.size} / ${state.total.coerceAtLeast(state.results.size)}"
                    } else {
                        "共 ${state.results.size} 条"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                if (state.results.isNotEmpty()) {
                    item {
                        GlassGroup {
                            state.results.forEachIndexed { i, track ->
                                LiquidTrackRow(
                                    track = track,
                                    onClick = { onPlayTrack(track) },
                                    onCollect = { onCollectTrack(track) },
                                )
                                if (i != state.results.lastIndex) GlassDivider()
                            }
                        }
                    }
                }
                if (state.loadingMore) {
                    item {
                        Text(
                            "加载更多…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
            }
        }
    }
}
