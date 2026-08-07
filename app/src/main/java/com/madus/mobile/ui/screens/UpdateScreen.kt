package com.madus.mobile.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.madus.mobile.BuildConfig
import com.madus.mobile.data.AppUpdate
import com.madus.mobile.ui.theme.appearanceTokens
import kotlinx.coroutines.launch
import java.io.File

/**
 * 检查更新页：先展示当前/最新版与说明，**用户点「更新到最新版」才下载安装**。
 * 下载过程显示真实进度条；下载后校验 APK，降低「解析软件包失败」。
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
    val lifecycleOwner = LocalLifecycleOwner.current

    val current = BuildConfig.VERSION_NAME.removeSuffix("-debug")
    var checking by remember { mutableStateOf(true) }
    var downloading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("正在检查最新版本…") }
    var progressText by remember { mutableStateOf("") }
    var progressFraction by remember { mutableFloatStateOf(-1f) } // -1 = indeterminate / idle
    var probe by remember { mutableStateOf<AppUpdate.ProbeResult?>(null) }
    var available by remember { mutableStateOf<AppUpdate.LatestRelease?>(null) }
    var pendingApk by remember { mutableStateOf<File?>(null) }
    var pendingVersion by remember { mutableStateOf<String?>(null) }
    var needInstallPermission by remember { mutableStateOf(false) }

    fun resetProgress() {
        progressText = ""
        progressFraction = -1f
    }

    fun tryInstallPending(): Boolean {
        val file = pendingApk ?: return false
        if (!file.exists()) {
            pendingApk = null
            return false
        }
        val check = AppUpdate.validateApkDetailed(file)
        if (check != null) {
            pendingApk = null
            status = "本地安装包无效：$check。请点「重新下载」。"
            progressText = ""
            progressFraction = -1f
            return false
        }
        if (!AppUpdate.canInstallPackages(context)) {
            needInstallPermission = true
            status = "请允许「安装未知应用」后返回本页，将自动继续安装"
            return false
        }
        needInstallPermission = false
        val ok = AppUpdate.installApk(context, file)
        status = if (ok) {
            "已调起安装，请确认安装 v${pendingVersion.orEmpty()}。" +
                "若仍提示「解析失败」，请点「重新下载」或用浏览器下载正式包。"
        } else {
            "无法打开安装器。请点「重新下载」，或用浏览器下载安装。"
        }
        if (ok) {
            progressText = "安装界面已打开"
            progressFraction = 1f
        }
        return ok
    }

    fun refresh() {
        scope.launch {
            checking = true
            downloading = false
            resetProgress()
            status = "正在检查最新版本…"
            available = null
            needInstallPermission = false
            val r = AppUpdate.probeLatest(current)
            probe = r
            checking = false
            when (r) {
                is AppUpdate.ProbeResult.AlreadyLatest -> {
                    status = "已是最新版本"
                    available = null
                    pendingApk = null
                    pendingVersion = null
                }
                is AppUpdate.ProbeResult.UpdateAvailable -> {
                    status = "发现新版本，可选择更新"
                    available = r.release
                    val cached = AppUpdate.cachedApk(context, r.release)
                    if (cached != null) {
                        pendingApk = cached
                        pendingVersion = r.release.versionName
                        progressFraction = 1f
                        progressText = "本地安装包已校验 ${AppUpdate.formatBytes(cached.length())}"
                        if (!AppUpdate.canInstallPackages(context)) {
                            needInstallPermission = true
                            status = "安装包已就绪，需先允许「安装未知应用」"
                        } else {
                            status = "安装包已校验通过，可直接安装"
                        }
                    }
                }
                is AppUpdate.ProbeResult.Failed -> {
                    status = "检查失败：${r.message}"
                    available = null
                }
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (needInstallPermission && pendingApk != null && !downloading) {
                    if (AppUpdate.canInstallPackages(context)) {
                        needInstallPermission = false
                        tryInstallPending()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun startUpdate(forceRedownload: Boolean = false) {
        val rel = available ?: return
        if (downloading || checking) return
        if (!forceRedownload) {
            val cached = AppUpdate.cachedApk(context, rel)
            if (cached != null) {
                pendingApk = cached
                pendingVersion = rel.versionName
                if (AppUpdate.canInstallPackages(context)) {
                    tryInstallPending()
                    return
                }
                needInstallPermission = true
                AppUpdate.openInstallPermissionSettings(context)
                status = "请允许「安装未知应用」后返回本页"
                return
            }
        }
        scope.launch {
            downloading = true
            needInstallPermission = false
            if (forceRedownload) {
                pendingApk = null
            }
            progressFraction = 0f
            progressText = if (forceRedownload) "强制重新下载…" else "准备下载…"
            status = "正在下载更新包…"
            when (
                val r = AppUpdate.downloadRelease(
                    context = context,
                    release = rel,
                    forceRedownload = forceRedownload,
                ) { p ->
                    progressFraction = p.fraction
                    progressText = p.message
                }
            ) {
                is AppUpdate.DownloadResult.ReadyToInstall -> {
                    downloading = false
                    pendingApk = r.apkFile
                    pendingVersion = r.version
                    progressFraction = 1f
                    progressText = "校验通过，正在打开安装…"
                    val ok = AppUpdate.installApk(context, r.apkFile)
                    status = if (ok) {
                        "已调起安装，请确认安装 v${r.version}"
                    } else {
                        "下载已校验通过，但无法打开安装器。请重试或浏览器下载。"
                    }
                }
                is AppUpdate.DownloadResult.NeedInstallPermission -> {
                    downloading = false
                    pendingApk = r.apkFile
                    pendingVersion = r.version
                    needInstallPermission = true
                    progressFraction = 1f
                    progressText = "校验通过 ${AppUpdate.formatBytes(r.apkFile.length())}"
                    AppUpdate.openInstallPermissionSettings(context)
                    status = "请允许「安装未知应用」后返回本页，将继续安装"
                }
                is AppUpdate.DownloadResult.Failed -> {
                    downloading = false
                    pendingApk = null
                    status = "下载失败：${r.message}"
                    progressText = ""
                    progressFraction = -1f
                }
            }
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction.coerceIn(0f, 1f).takeIf { progressFraction >= 0f } ?: 0f,
        label = "updateProgress",
    )
    val showBar = downloading || progressFraction >= 0f || progressText.isNotBlank()

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
                    is AppUpdate.ProbeResult.UpdateAvailable -> {
                        val size = p.release.apkSize
                        if (size > 0) {
                            "v${p.release.versionName}（${AppUpdate.formatBytes(size)}）"
                        } else {
                            "v${p.release.versionName}"
                        }
                    }
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

                if (showBar) {
                    Spacer(Modifier.height(14.dp))
                    if (downloading && progressFraction < 0f) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = colors.primary,
                            trackColor = colors.outline.copy(alpha = 0.25f),
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = colors.primary,
                            trackColor = colors.outline.copy(alpha = 0.25f),
                        )
                    }
                    if (progressText.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                progressText,
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.primary,
                                modifier = Modifier.weight(1f),
                            )
                            if (progressFraction >= 0f && downloading) {
                                Text(
                                    "${(progressFraction * 100).toInt().coerceIn(0, 100)}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.primary,
                                )
                            }
                        }
                    }
                }
            }

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

            val hasCached = pendingApk != null && pendingApk!!.exists()
            val canUpdate = available != null && !checking && !downloading
            val buttonLabel = when {
                downloading -> "下载中…"
                needInstallPermission && hasCached -> "去开启安装权限"
                hasCached && available != null -> "安装已下载的 v${available!!.versionName}"
                available != null -> "更新到最新版 v${available!!.versionName}"
                else -> "暂无可用更新"
            }
            TextButton(
                onClick = {
                    when {
                        needInstallPermission && hasCached -> {
                            AppUpdate.openInstallPermissionSettings(context)
                            status = "请允许「安装未知应用」后返回本页"
                        }
                        hasCached && available != null && !downloading -> {
                            if (AppUpdate.canInstallPackages(context)) {
                                tryInstallPending()
                            } else {
                                needInstallPermission = true
                                AppUpdate.openInstallPermissionSettings(context)
                            }
                        }
                        else -> startUpdate(forceRedownload = false)
                    }
                },
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
                    buttonLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // 解析失败 / 坏包 时用
            if (available != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = { startUpdate(forceRedownload = true) },
                        enabled = canUpdate,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("重新下载")
                    }
                    TextButton(
                        onClick = { AppUpdate.openReleasesInBrowser(context, preferGitee = true) },
                        enabled = !downloading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Gitee 下载")
                    }
                }
            }

            if (available != null) {
                TextButton(
                    onClick = { AppUpdate.openGithubReleasesInBrowser(context) },
                    enabled = !downloading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("GitHub 下载（备用）")
                }
            }

            Text(
                text = "国内优先从 Gitee 下载，失败自动改试 GitHub。" +
                    "下载后会校验安装包再打开系统安装。安装时需允许「未知应用」。" +
                    (available?.let { " 当前源：${it.sourceLabel}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )

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
