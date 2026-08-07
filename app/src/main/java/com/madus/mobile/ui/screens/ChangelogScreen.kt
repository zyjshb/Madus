package com.madus.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.madus.mobile.BuildConfig
import com.madus.mobile.ui.changelog.AppChangelog
import com.madus.mobile.ui.theme.appearanceTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val tokens = appearanceTokens()
    val shape = RoundedCornerShape(tokens.cornerMd)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("更新日志", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "当前 v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    text = "按「新增 / 变更 / 修复」分类（Keep a Changelog）。\n" +
                        "打开方式：我的 → 检查更新 → 连点 3 次。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            itemsIndexed(AppChangelog.entries) { index, entry ->
                val isLatest = index == 0
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = tokens.borderWidth,
                            color = if (isLatest) {
                                colors.primary.copy(alpha = 0.7f)
                            } else {
                                colors.outline.copy(alpha = 0.4f)
                            },
                            shape = shape,
                        )
                        .background(
                            if (isLatest) {
                                colors.surfaceVariant.copy(alpha = 0.45f * tokens.panelAlpha)
                            } else {
                                colors.surface.copy(alpha = tokens.panelAlpha)
                            },
                            shape,
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "v${entry.version}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isLatest) colors.primary else colors.onBackground,
                        )
                        Text(
                            text = entry.date,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    if (isLatest) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "当前版本",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primary,
                        )
                    }
                    ChangelogSection("新增", entry.added)
                    ChangelogSection("变更", entry.changed)
                    ChangelogSection("修复", entry.fixed)
                    ChangelogSection("已知问题", entry.known)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ChangelogSection(title: String, lines: List<String>) {
    if (lines.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    Spacer(Modifier.height(10.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = colors.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
    lines.forEach { line ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "·",
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp, top = 1.dp),
            )
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onBackground.copy(alpha = 0.92f),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
