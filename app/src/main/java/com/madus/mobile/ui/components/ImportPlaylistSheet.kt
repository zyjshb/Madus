package com.madus.mobile.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.madus.mobile.ui.ImportPlaylistUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPlaylistSheet(
    state: ImportPlaylistUiState,
    onDismiss: () -> Unit,
    onInput: (String) -> Unit,
    onStart: () -> Unit,
    onContinue: () -> Unit = {},
) {
    if (!state.visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { if (!state.isWorking) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("外站歌单导入", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "① 多行粘贴「歌名 - 歌手」（任意平台复制都行）\n" +
                    "② 或贴网易云 / QQ 音乐 / 酷狗 / 酷我 歌单链接\n" +
                    "导入后按歌名在 B 站匹配，写入本地歌单（不接外站播放源）\n" +
                    "大歌单每次 500 首，导完一批可继续下一批",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = state.input,
                onValueChange = onInput,
                enabled = !state.isWorking && !state.canContinue,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 280.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    .padding(12.dp),
            )
            if (state.isWorking) {
                Spacer(Modifier.height(10.dp))
                Text(
                    state.progress.ifBlank { "导入中…" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            state.result?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            if (state.canContinue) {
                TextButton(
                    onClick = onContinue,
                    enabled = !state.isWorking,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val next = state.remaining.coerceAtMost(500)
                    Text(
                        if (state.isWorking) {
                            "导入中…"
                        } else {
                            "继续添加下一批（$next 首 · 还剩 ${state.remaining}）"
                        },
                    )
                }
            } else {
                TextButton(
                    onClick = onStart,
                    enabled = !state.isWorking && state.input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.isWorking) "导入中…" else "开始导入")
                }
            }
            TextButton(
                onClick = onDismiss,
                enabled = !state.isWorking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.canContinue) "先到这里" else "关闭")
            }
        }
    }
}
