package com.madus.mobile.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.madus.mobile.domain.Track

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongFeedbackSheet(
    track: Track,
    direction: Int,
    notInterested: Boolean,
    onStyleFeedback: (Int) -> Unit,
    onNotInterested: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
            Text("歌曲反馈", style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 20.dp))
            Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            Text("作为辅助参考，你的收听、点赞和收藏仍会持续影响推荐。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp))
            FeedbackChoice("喜欢这类歌曲", if (direction == 1) "已选择 · 再点撤销" else "适当多推荐相近类型",
                Icons.Outlined.ThumbUp, direction == 1) { onStyleFeedback(if (direction == 1) 0 else 1) }
            FeedbackChoice("不喜欢这类歌曲", if (direction == -1) "已选择 · 再点撤销" else "适当少推荐，不屏蔽整个曲风",
                Icons.Outlined.ThumbDown, direction == -1) { onStyleFeedback(if (direction == -1) 0 else -1) }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            FeedbackChoice(if (notInterested) "撤销这首歌的不合口味" else "这首歌不合口味",
                if (notInterested) "恢复这首歌的推荐资格" else "跳过并不再推荐这首歌",
                Icons.Outlined.ThumbDown, false, onNotInterested)
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("关闭") }
        }
    }
}

@Composable
private fun FeedbackChoice(title: String, detail: String, icon: ImageVector, selected: Boolean,
    onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}
