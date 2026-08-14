package com.madus.mobile.ui.liquid

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UploadFile
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.madus.mobile.data.AppUpdate
import com.madus.mobile.domain.MusicSourceType
import com.madus.mobile.ui.MeUiState
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
    val aboutTapsNeeded = 7
    var aboutTaps by remember { mutableIntStateOf(0) }
    var aboutUnlocked by remember { mutableStateOf(false) }
    var lastAboutTapAt by remember { mutableLongStateOf(0L) }
    var lastUpdateTapAt by remember { mutableLongStateOf(0L) }
    var lastAboutToastAt by remember { mutableLongStateOf(0L) }
    var updateSubtitle by remember { mutableStateOf("v${state.appVersion}") }
    var updateAvailable by remember { mutableStateOf(false) }
    val tokens = liquidTokens()

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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 148.dp),
    ) {
        item {
            Text("我的", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(18.dp))
            GlassGroup {
                LiquidNavRow(
                    title = if (bili?.isLoggedIn == true) {
                        bili.displayName.ifBlank { "B站用户" }
                    } else {
                        "游客"
                    },
                    subtitle = if (bili?.isLoggedIn == true) "B站已登录" else "登录后同步收藏",
                    onClick = onOpenBiliLogin,
                    trailing = {
                        Text(
                            if (bili?.isLoggedIn == true) "重登" else "登录",
                            color = tokens.accent,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${state.likedCount} 喜欢 · ${state.recentCount} 最近",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }

        item {
            LiquidSectionLabel("常用")
            GlassGroup {
                LiquidNavRow("播放设置", "音效 · 网络 · 边听缓存", Icons.Outlined.Tune, onClick = {
                    onToolClick("playback")
                })
                GlassDivider()
                LiquidNavRow("缓存管理", state.cacheSizeLabel.ifBlank { "打开列表" }, Icons.Outlined.CloudDownload, onClick = {
                    onToolClick("cache")
                })
                GlassDivider()
                LiquidNavRow("导入音乐", "歌名歌手 / 链接", Icons.Outlined.UploadFile, onClick = {
                    onToolClick("import")
                })
                GlassDivider()
                LiquidNavRow(
                    title = "检查更新",
                    subtitle = updateSubtitle,
                    icon = Icons.Outlined.SystemUpdate,
                    onClick = {
                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTapAt < 800L) return@LiquidNavRow
                        lastUpdateTapAt = now
                        onToolClick("update")
                    },
                    trailing = if (updateAvailable) {
                        { Text("新", color = tokens.accent, style = MaterialTheme.typography.labelLarge) }
                    } else null,
                )
            }
            Spacer(Modifier.height(18.dp))
        }

        item {
            LiquidSectionLabel("账号")
            GlassGroup {
                LiquidNavRow(
                    title = "Bilibili 音源",
                    subtitle = if (bili?.isLoggedIn == true) {
                        "已登录 · ${bili.displayName.ifBlank { "B站用户" }}"
                    } else {
                        "未登录"
                    },
                    icon = Icons.Outlined.Person,
                    onClick = onOpenBiliLogin,
                )
                if (bili?.isLoggedIn == true) {
                    GlassDivider()
                    LiquidNavRow(
                        title = "退出登录",
                        subtitle = "清掉本机 B 站登录",
                        icon = Icons.AutoMirrored.Outlined.Logout,
                        onClick = onLogoutBili,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        item {
            LiquidSectionLabel("应用")
            GlassGroup {
                LiquidNavRow("主题", "简约 / 液态玻璃", Icons.Outlined.Settings, onClick = onOpenSettings)
                GlassDivider()
                LiquidNavRow(
                    title = "关于 Madus",
                    subtitle = "v${state.appVersion}",
                    icon = Icons.Outlined.Info,
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
