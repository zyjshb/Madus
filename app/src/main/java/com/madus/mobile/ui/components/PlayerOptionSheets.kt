package com.madus.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.madus.mobile.data.AudioQuality
import com.madus.mobile.player.SleepTimer

/**
 * 主流音乐 App 做法：半高 BottomSheet 单选列表（网易云/QQ/Spotify 定时同类）。
 * 不用 AlertDialog、不用大描边胶囊。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityPickerSheet(
    current: AudioQuality,
    onSelect: (AudioQuality) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = "音质",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
            AudioQuality.entries.forEach { q ->
                OptionRow(
                    title = q.label,
                    subtitle = null,
                    selected = current == q,
                    onClick = {
                        onSelect(q)
                        onDismiss()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    activeMinutes: Int?,
    remainingLabel: String?,
    lastCustomMinutes: Int? = null,
    onSelectMinutes: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val options = listOf(
        0 to "关闭",
        15 to "15 分钟",
        30 to "30 分钟",
        45 to "45 分钟",
        60 to "60 分钟",
    )
    val customActive = SleepTimer.isCustom(activeMinutes ?: 0)
    val remembered = lastCustomMinutes?.takeIf { SleepTimer.isCustom(it) }
    var showCustomInput by remember { mutableStateOf(false) }
    var customText by remember {
        mutableStateOf((if (customActive) activeMinutes else remembered)?.toString().orEmpty())
    }
    var customError by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    fun confirmCustom() {
        val parsed = SleepTimer.parseCustom(customText)
        if (parsed == null) {
            customError = true
            return
        }
        onSelectMinutes(parsed)
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 8.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = "定时关闭",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (!remainingLabel.isNullOrBlank()) {
                Text(
                    text = "剩余 $remainingLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(4.dp))
            options.forEach { (min, label) ->
                val selected = when {
                    min == 0 -> activeMinutes == null || activeMinutes == 0
                    else -> activeMinutes == min
                }
                OptionRow(
                    title = label,
                    subtitle = null,
                    selected = selected,
                    onClick = {
                        onSelectMinutes(min)
                        onDismiss()
                    },
                )
            }
            OptionRow(
                title = "自定义",
                subtitle = when {
                    customActive -> "${activeMinutes} 分钟"
                    remembered != null -> "${remembered} 分钟"
                    else -> null
                },
                selected = customActive && !showCustomInput,
                onClick = {
                    showCustomInput = true
                    customError = false
                },
            )
            if (showCustomInput) {
                CustomMinutesRow(
                    value = customText,
                    error = customError,
                    focusRequester = focusRequester,
                    onValueChange = {
                        customText = it.filter(Char::isDigit).take(3)
                        customError = false
                    },
                    onConfirm = ::confirmCustom,
                )
                LaunchedEffect(Unit) {
                    runCatching { focusRequester.requestFocus() }
                }
            }
        }
    }
}

@Composable
private fun CustomMinutesRow(
    value: String,
    error: Boolean,
    focusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    val border = if (error) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.outline
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onConfirm() }),
                modifier = Modifier
                    .width(88.dp)
                    .focusRequester(focusRequester)
                    .border(1.dp, border, RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
            Text(
                text = "分钟",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 10.dp),
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onConfirm) {
                Text("确定")
            }
        }
        if (error) {
            Text(
                text = "${SleepTimer.CUSTOM_MIN}–${SleepTimer.CUSTOM_MAX}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .padding(top = 10.dp, bottom = 4.dp)
            .width(36.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun OptionRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "已选",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
