package com.madus.mobile.ui.liquid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.madus.mobile.data.AppUpdate
import com.madus.mobile.domain.MusicSourceType
import com.madus.mobile.ui.MeUiState
import com.madus.mobile.ui.components.MadusImageLoader
import com.madus.mobile.ui.components.normalizeCoverUrl
import com.madus.mobile.ui.theme.LiquidType
import com.madus.mobile.ui.theme.liquidTokens

@Composable
fun LiquidMeScreen(
    state: MeUiState,
    onOpenBiliLogin: () -> Unit,
    onLogoutBili: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onToolClick: (String) -> Unit = {},
    onOpenEasterEgg: () -> Unit = {},
    onAboutSystemToast: (message: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val bili = state.sessions.firstOrNull { it.source == MusicSourceType.BILIBILI }
    val loggedIn = bili?.isLoggedIn == true
    val avatar = bili?.avatarUrl
    val aboutTapsNeeded = 7
    var aboutTaps by remember { mutableIntStateOf(0) }
    var aboutUnlocked by remember { mutableStateOf(false) }
    var lastAboutTapAt by remember { mutableLongStateOf(0L) }
    var lastUpdateTapAt by remember { mutableLongStateOf(0L) }
    var lastAboutToastAt by remember { mutableLongStateOf(0L) }
    var updateSubtitle by remember { mutableStateOf("v${state.appVersion}") }
    var updateAvailable by remember { mutableStateOf(false) }
    val tokens = liquidTokens()
    val destructive = if (tokens.dark) Color(0xFFFF453A) else Color(0xFFFF3B30)

    LaunchedEffect(state.appVersion) {
        val current = state.appVersion.substringBefore("-")
        when (val r = AppUpdate.probeLatest(current)) {
            is AppUpdate.ProbeResult.UpdateAvailable -> {
                updateAvailable = true
                updateSubtitle = "有新版本 v${r.release.versionName}"
            }
            is AppUpdate.ProbeResult.AlreadyLatest -> {
                updateAvailable = false
                updateSubtitle = if (r.remoteLooksOld) {
                    "v${state.appVersion} · 可到网页确认"
                } else {
                    "v${state.appVersion} · 已是最新"
                }
            }
            is AppUpdate.ProbeResult.Failed -> {
                updateAvailable = false
                updateSubtitle = "v${state.appVersion}"
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 12.dp,
            bottom = LocalLiquidChromeBottom.current,
        ),
    ) {
        item {
            Text(
                "我的",
                style = LiquidType.largeTitle,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(18.dp))
            InsetGroup {
                AccountRow(
                    loggedIn = loggedIn,
                    displayName = if (loggedIn) bili?.displayName.orEmpty().ifBlank { "B站用户" } else "游客",
                    subtitle = buildString {
                        append(if (loggedIn) "已登录" else "未登录")
                        append(" · ${state.likedCount} 喜欢 · ${state.recentCount} 最近")
                    },
                    avatarUrl = if (loggedIn) avatar else null,
                    onClick = onOpenBiliLogin,
                )
                if (loggedIn) {
                    InsetDivider.text()
                    Text(
                        "退出",
                        style = LiquidType.body,
                        color = destructive,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .clickable(onClick = onLogoutBili)
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }

        item {
            LiquidSectionLabel("播放")
            InsetGroup {
                LiquidNavRow("播放设置", "音效 · 网络 · 边听缓存", onClick = {
                    onToolClick("playback")
                })
                InsetDivider.text()
                LiquidNavRow("缓存管理", state.cacheSizeLabel.ifBlank { "打开列表" }, onClick = {
                    onToolClick("cache")
                })
                InsetDivider.text()
                LiquidNavRow("导入音乐", "歌名歌手 / 链接", onClick = {
                    onToolClick("import")
                })
            }
            Spacer(Modifier.height(28.dp))
        }

        item {
            LiquidSectionLabel("应用")
            InsetGroup {
                LiquidNavRow("主题", "简约 / 画境", onClick = onOpenSettings)
                InsetDivider.text()
                LiquidNavRow(
                    title = "检查更新",
                    subtitle = updateSubtitle,
                    onClick = {
                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTapAt < 800L) return@LiquidNavRow
                        lastUpdateTapAt = now
                        onToolClick("update")
                    },
                    trailing = if (updateAvailable) {
                        { Text("新", color = tokens.accent, style = LiquidType.footnote) }
                    } else null,
                )
                InsetDivider.text()
                LiquidNavRow(
                    title = "关于 Madus",
                    subtitle = "v${state.appVersion}",
                    onClick = {
                        val now = System.currentTimeMillis()
                        if (aboutUnlocked) {
                            if (now - lastAboutToastAt > 1_500L) {
                                lastAboutToastAt = now
                                onAboutSystemToast("不用再点了")
                            }
                            return@LiquidNavRow
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
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun AccountRow(
    loggedIn: Boolean,
    displayName: String,
    subtitle: String,
    avatarUrl: String?,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val loader = remember { MadusImageLoader.get(context) }
    val url = normalizeCoverUrl(avatarUrl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (loggedIn && !url.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(url).crossfade(160).build(),
                    contentDescription = null,
                    imageLoader = loader,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                )
            } else {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(displayName, style = LiquidType.body, color = MaterialTheme.colorScheme.onSurface)
            Text(
                subtitle,
                style = LiquidType.subhead,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
