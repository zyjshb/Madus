package com.madus.mobile.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.madus.mobile.BuildConfig
import com.madus.mobile.data.AppUpdate
import com.madus.mobile.ui.theme.appearanceTokens
import kotlinx.coroutines.launch

/**
 * 检查更新页：先展示当前/最新版与说明，**用户点「更新到最新版」才下载安装**。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    onBack: () -> Unit,
    onOpenChangelog: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val tokens = appearanceTokens()
    val shape = RoundedCornerShape(tokens.cornerMd)
    val scope = rememberCoroutineScope()

    val current = BuildConfig.VERSION_NAME.removeSuffix("-debug")
    var checking by remember { mutableStateOf(true) }
    var downloading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("正在检查最新版本…") }
    var progress by remember { mutableStateOf("") }
    var probe by remember { mutableStateOf<AppUpdate.ProbeResult?>(null) }
    var available by remember { mutableStateOf<AppUpdate.LatestRelease?>(null) }

    fun refresh() {
        scope.launch {
            checking = true
            downloading = false
            progress = ""
            status = "正在检查最新版本…"
            available = null
            val r = AppUpdate.probeLatest(current)
            probe = r
            checking = false
            when (r) {
                is AppUpdate.ProbeResult.AlreadyLatest -> {
                    status = "已是最新版本"
                    available = null
                }
                is AppUpdate.ProbeResult.UpdateAvailable -> {
                    status = "发现新版本，可选择更新"
                    available = r.release
                }
                is AppUpdate.ProbeResult.Failed -> {
                    status = "检查失败：${r.message}"
                    available = null
                }
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun startUpdate() {
        val rel = available ?: return
        if (downloading || checking) return
        scope.launch {
            downloading = true
            progress = "准备下载…"
            when (
                val r = AppUpdate.downloadRelease(context, rel) { progress = it }
            ) {
                is AppUpdate.DownloadResult.ReadyToInstall -> {
                    downloading = false
                    progress = ""
                    val ok = AppUpdate.installApk(context, r.apkFile)
                    status = if (ok) {
                        "已调起安装，请确认安装 v${r.version}"
                    } else {
                        "下载完成，但无法打开安装器"
                    }
                }
                is AppUpdate.DownloadResult.NeedInstallPermission -> {
                    downloading = false
                    progress = ""
                    AppUpdate.openInstallPermissionSettings(context)
                    status = "请允许「安装未知应用」后，再点「更新到最新版」"
                }
                is AppUpdate.DownloadResult.Failed -> {
                    downloading = false
                    status = "下载失败：${r.message}"
                    progress = ""
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("检查更新", fontWeight = FontWeight.SemiBold)
                        Text(
                            "由你决定是否升级",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 版本卡片
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(tokens.borderWidth, colors.outline, shape)
                    .padding(16.dp),
            ) {
                Text("当前版本", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Text("v$current", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text("最新版本", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                val latestLabel = when (val p = probe) {
                    is AppUpdate.ProbeResult.UpdateAvailable -> "v${p.release.versionName}"
                    is AppUpdate.ProbeResult.AlreadyLatest -> "v${p.remote}（已是最新）"
                    is AppUpdate.ProbeResult.Failed -> "—"
                    null -> if (checking) "检查中…" else "—"
                }
                Text(
                    latestLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (available != null) colors.primary else colors.onBackground,
                )
                Spacer(Modifier.height(10.dp))
                Text(status, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                if (progress.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(progress, style = MaterialTheme.typography.labelLarge, color = colors.primary)
                }
            }

            // 操作
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = { refresh() },
                    enabled = !checking && !downloading,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (checking) "检查中…" else "重新检查")
                }
                TextButton(
                    onClick = onOpenChangelog,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("更新日志")
                }
            }

            // 用户主动选择才更新
            val canUpdate = available != null && !checking && !downloading
            TextButton(
                onClick = { startUpdate() },
                enabled = canUpdate,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        tokens.borderWidth,
                        if (canUpdate) colors.primary else colors.outline.copy(alpha = 0.4f),
                        shape,
                    )
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    when {
                        downloading -> "下载中…"
                        available != null -> "更新到最新版 v${available!!.versionName}"
                        else -> "暂无可用更新"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = "不会自动下载。确认后再点上方按钮；安装时请允许「未知应用」。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )

            // 本版 Release 说明
            val notes = available?.body?.trim().orEmpty()
            if (notes.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(tokens.borderWidth, colors.outline.copy(alpha = 0.5f), shape)
                        .padding(14.dp),
                ) {
                    Text(
                        "最新版说明",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onBackground.copy(alpha = 0.9f),
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
