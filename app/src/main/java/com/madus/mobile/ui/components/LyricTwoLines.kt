package com.madus.mobile.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.madus.mobile.domain.Lyrics
import com.madus.mobile.domain.LyricsUiState
import com.madus.mobile.ui.theme.MadusMotion

@Composable
fun LyricTwoLines(
    state: LyricsUiState,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    if (state.loading || state.unavailable || state.lines.isEmpty()) return
    val (cur, next) = Lyrics.currentAndNext(state.lines, positionMs)
    val currentText = cur?.text.orEmpty()
    val nextText = next?.text.orEmpty()
    if (currentText.isBlank() && nextText.isBlank()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(
            targetState = currentText,
            transitionSpec = { fadeIn(MadusMotion.fade) togetherWith fadeOut(MadusMotion.tabFade) },
            label = "lyricNow",
        ) { line ->
            Text(
                text = line.ifBlank { " " },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (nextText.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = nextText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

fun LyricsUiState.hasVisibleLines(): Boolean =
    !loading && !unavailable && lines.isNotEmpty()
