package com.madus.mobile.ui.screens

import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.madus.mobile.domain.MusicSourceType
import com.madus.mobile.ui.MeUiState
import com.madus.mobile.ui.components.LineButton
import com.madus.mobile.ui.components.LineFrame
import com.madus.mobile.ui.components.MadusImageLoader
import com.madus.mobile.ui.components.SectionTitle
import com.madus.mobile.ui.components.normalizeCoverUrl
import com.madus.mobile.ui.theme.appearanceTokens

@Composable
fun MeScreen(
    state: MeUiState,
    onOpenBiliLogin: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onToolClick: (String) -> Unit = {},
    onOpenEasterEgg: () -> Unit = {},
    onAboutSystemToast: (message: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val bili = state.sessions.firstOrNull { it.source == MusicSourceType.BILIBILI }
    val loggedInCount = state.sessions.count { it.isLoggedIn }

    val aboutTapsNeeded = 7
    var aboutTaps by remember { mutableIntStateOf(0) }
    var aboutUnlocked by remember { mutableStateOf(false) }
    var lastAboutTapAt by remember { mutableLongStateOf(0L) }
    var lastUpdateTapAt by remember { mutableLongStateOf(0L) }
    var lastAboutToastAt by remember { mutableLongStateOf(0L) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text(
                text = "我的",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            LineFrame(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 18.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MeAvatar(
                        avatarUrl = bili?.avatarUrl,
                        loggedIn = bili?.isLoggedIn == true,
                        uname = bili?.displayName.orEmpty(),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (bili?.isLoggedIn == true) {
                                bili.displayName.ifBlank { "B站用户" }
                            } else {
                                "游客"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (bili?.isLoggedIn == true) {
                                "B站已登录 · 收藏可同步"
                            } else {
                                "登录音源，开始同步你的音乐"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                    LineButton(
                        text = if (bili?.isLoggedIn == true) "重登" else "登录",
                        onClick = onOpenBiliLogin,
                        filled = bili?.isLoggedIn != true,
                    )
                }
            }
        }

        item {
            SectionTitle(text = "听歌概览")
            Spacer(Modifier.height(8.dp))
            LineFrame(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 16.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatCell("喜欢", state.likedCount.toString(), Icons.Outlined.FavoriteBorder)
                    StatCell("最近", state.recentCount.toString(), Icons.Outlined.History)
                    StatCell("账号", loggedInCount.toString(), Icons.Outlined.Person)
                }
            }
        }

        item {
            SectionTitle(text = "常用功能")
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionCell(
                        icon = Icons.Outlined.Settings,
                        title = "播放设置",
                        subtitle = "音效 · 边听缓存",
                        onClick = { onToolClick("playback") },
                        modifier = Modifier.weight(1f),
                    )
                    ActionCell(
                        icon = Icons.Outlined.CloudDownload,
                        title = "缓存管理",
                        subtitle = state.cacheSizeLabel.ifBlank { "打开列表" },
                        onClick = { onToolClick("cache") },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionCell(
                        icon = Icons.Outlined.UploadFile,
                        title = "导入音乐",
                        subtitle = "歌名歌手 / 链接",
                        onClick = { onToolClick("import") },
                        modifier = Modifier.weight(1f),
                    )
                    ActionCell(
                        icon = Icons.Outlined.SystemUpdate,
                        title = "检查更新",
                        subtitle = "v${state.appVersion} · 可选升级",
                        onClick = {
                            val now = System.currentTimeMillis()
                            // 800ms 内重复点击忽略，避免连点狂进页面
                            if (now - lastUpdateTapAt < 800L) return@ActionCell
                            lastUpdateTapAt = now
                            onToolClick("update")
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            SectionTitle(text = "账号与同步")
            Spacer(Modifier.height(8.dp))
            LineFrame(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                MeNavRow(
                    icon = Icons.Outlined.Person,
                    title = "Bilibili 音源",
                    subtitle = if (bili?.isLoggedIn == true) {
                        "已登录 · ${bili.displayName.ifBlank { "B站用户" }}"
                    } else {
                        "未登录 · 登录后同步收藏"
                    },
                    onClick = onOpenBiliLogin,
                    trailing = {
                        Text(
                            text = if (bili?.isLoggedIn == true) "已连接" else "去登录",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }
        }

        item {
            SectionTitle(text = "应用")
            Spacer(Modifier.height(8.dp))
            // 外观与关于拆开两块，连点关于时不易误触外观
            LineFrame(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                MeNavRow(
                    icon = Icons.Outlined.Settings,
                    title = "外观设置",
                    subtitle = "形态与主题色",
                    onClick = onOpenSettings,
                )
            }
            Spacer(Modifier.height(12.dp))
            LineFrame(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                MeNavRow(
                    icon = Icons.Outlined.Info,
                    title = "关于 Madus",
                    subtitle = "v${state.appVersion}",
                    onClick = {
                        val now = System.currentTimeMillis()
                        if (aboutUnlocked) {
                            if (now - lastAboutToastAt > 1_500L) {
                                lastAboutToastAt = now
                                onAboutSystemToast("不用再点了")
                            }
                            return@MeNavRow
                        }
                        if (now - lastAboutTapAt > 1_500L) aboutTaps = 0
                        lastAboutTapAt = now
                        aboutTaps += 1
                        val remaining = aboutTapsNeeded - aboutTaps
                        when {
                            remaining <= 0 -> {
                                aboutUnlocked = true
                                aboutTaps = 0
                                lastAboutToastAt = now
                                onOpenEasterEgg()
                            }
                            remaining in 1..3 -> {
                                if (now - lastAboutToastAt > 400L) {
                                    lastAboutToastAt = now
                                    onAboutSystemToast("还差 $remaining 次")
                                }
                            }
                        }
                    },
                )
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun MeAvatar(
    avatarUrl: String?,
    loggedIn: Boolean,
    uname: String,
) {
    val context = LocalContext.current
    val loader = remember { MadusImageLoader.get(context) }
    val url = normalizeCoverUrl(avatarUrl)
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .border(
                appearanceTokens().borderWidth,
                MaterialTheme.colorScheme.outline,
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (loggedIn && !url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(180)
                    .build(),
                contentDescription = uname,
                imageLoader = loader,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(5.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionCell(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LineFrame(
        modifier = modifier.height(84.dp),
        contentPadding = 12.dp,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun MeNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
