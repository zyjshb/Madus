package com.madus.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.madus.mobile.domain.MusicDiscovery

/** 主页面只保留两个轻入口，解释与反馈操作在面板内呈现。 */
@Composable
fun MusicTasteBar(
    onOpen: () -> Unit,
    onOpenFeedback: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onOpenFeedback != null) {
            TextButton(onClick = onOpenFeedback) {
                Text("歌曲反馈", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else Spacer(Modifier.weight(1f))
        TextButton(onClick = onOpen) {
            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text("我的音乐口味", style = MaterialTheme.typography.labelMedium)
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MusicTasteSheet(
    preferredTopics: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(preferredTopics.toList()) }
    val groups = listOf(
        "曲风" to listOf("pop", "rock", "folk", "rnb", "rap", "jazz", "dj", "gufeng", "instrumental"),
        "语言" to listOf("mandarin", "cantonese", "en-song", "jp-song", "kpop"),
        "氛围与版本" to listOf("healing", "sleep", "anime-song", "vocaloid", "cover", "live"),
    )
    val groupedKeys = groups.flatMap { it.second }.toSet()
    val remaining = MusicDiscovery.availableTopics.keys.filterNot { it in groupedKeys }
    val allGroups = if (remaining.isEmpty()) groups else groups + ("更多" to remaining)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .padding(horizontal = 20.dp),
        ) {
            Text("我的音乐口味", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "选你愿意多听的音乐，可多选。之后会结合喜欢、听完和跳过继续调整，随时可以回来改。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                allGroups.forEach { (title, keys) ->
                    Column {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            keys.forEach topic@{ key ->
                                val label = MusicDiscovery.availableTopics[key] ?: return@topic
                                val checked = key in selected
                                FilterChip(
                                    selected = checked,
                                    onClick = {
                                        selected = if (checked) selected - key else selected + key
                                    },
                                    label = { Text(label) },
                                    leadingIcon = if (checked) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (selected.isEmpty()) {
                    "也可以不选，保存后从你的收听中学习。"
                } else {
                    "已选 ${selected.size} 项 · 保存后调整接下来的推荐"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { selected = emptyList() }, enabled = selected.isNotEmpty()) {
                    Text("清空选择")
                }
                Button(onClick = { onSave(selected.toSet()) }) {
                    Text(if (selected.isEmpty()) "保存并随听随学" else "保存口味")
                }
            }
        }
    }
}
