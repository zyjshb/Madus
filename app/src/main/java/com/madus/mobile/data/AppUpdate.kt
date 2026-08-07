package com.madus.mobile.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.madus.mobile.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

/**
 * 应用内更新：查 GitHub / Gitee Releases → 用户确认后再下载 APK → 校验 → 安装。
 * **国内优先 Gitee，失败再 GitHub。**
 *
 * 下载后强制校验：大小、ZIP 魔数、可打开 Zip、含 AndroidManifest.xml。
 */
object AppUpdate {
    // —— GitHub ——
    const val GITHUB_OWNER = "zyjshb"
    const val GITHUB_REPO = "Madus"
    const val GITHUB_RELEASES_URL =
        "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    private const val GITHUB_API_LATEST =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    // —— Gitee（国内镜像）——
    const val GITEE_OWNER = "dikoklhf"
    const val GITEE_REPO = "madus"
    const val GITEE_RELEASES_URL =
        "https://gitee.com/$GITEE_OWNER/$GITEE_REPO/releases"
    private const val GITEE_API_LATEST =
        "https://gitee.com/api/v5/repos/$GITEE_OWNER/$GITEE_REPO/releases/latest"
    private const val GITEE_API_LIST =
        "https://gitee.com/api/v5/repos/$GITEE_OWNER/$GITEE_REPO/releases?page=1&per_page=5"

    private const val MAX_REDIRECTS = 8
    private const val MIN_APK_BYTES = 3_000_000L
    private const val SIZE_TOLERANCE = 4096L

    data class LatestRelease(
        val tag: String,
        val versionName: String,
        /** 首选下载地址（通常 Gitee） */
        val apkUrl: String,
        val apkName: String,
        val body: String,
        val apkSize: Long = -1L,
        /** 备用地址（GitHub 等），按顺序尝试 */
        val mirrorUrls: List<String> = emptyList(),
        /** 展示用：Gitee / GitHub / Gitee+GitHub */
        val sourceLabel: String = "Gitee",
    ) {
        fun allDownloadUrls(): List<String> =
            (listOf(apkUrl) + mirrorUrls).map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }

    data class DownloadProgress(
        val fraction: Float,
        val message: String,
        val readBytes: Long = 0L,
        val totalBytes: Long = -1L,
    )

    sealed class ProbeResult {
        data class AlreadyLatest(val current: String, val remote: String) : ProbeResult()
        data class UpdateAvailable(val current: String, val release: LatestRelease) : ProbeResult()
        data class Failed(val message: String) : ProbeResult()
    }

    sealed class DownloadResult {
        data class ReadyToInstall(val version: String, val apkFile: File) : DownloadResult()
        data class NeedInstallPermission(val version: String, val apkFile: File) : DownloadResult()
        data class Failed(val message: String) : DownloadResult()
    }

    /** 只查询最新版，不下载。Gitee + GitHub 都会问，取更高版本并合并下载链。 */
    suspend fun probeLatest(
        currentVersionName: String = BuildConfig.VERSION_NAME,
    ): ProbeResult = withContext(Dispatchers.IO) {
        try {
            val latest = fetchMergedLatestRelease()
                ?: return@withContext ProbeResult.Failed(
                    "无法获取最新版本（Gitee / GitHub 均失败）。\n" +
                        "可到网页下载：\n$GITEE_RELEASES_URL\n$GITHUB_RELEASES_URL",
                )
            val current = normalizeVersion(currentVersionName)
            val remote = normalizeVersion(latest.versionName)
            if (compareVersion(remote, current) <= 0) {
                ProbeResult.AlreadyLatest(
                    current = currentVersionName.removeSuffix("-debug"),
                    remote = latest.versionName,
                )
            } else {
                ProbeResult.UpdateAvailable(
                    current = currentVersionName.removeSuffix("-debug"),
                    release = latest,
                )
            }
        } catch (t: Throwable) {
            ProbeResult.Failed(friendlyError(t))
        }
    }

    /**
     * 下载 APK：按 [LatestRelease.allDownloadUrls] 依次尝试（Gitee → GitHub）。
     */
    suspend fun downloadRelease(
        context: Context,
        release: LatestRelease,
        forceRedownload: Boolean = false,
        onProgress: (DownloadProgress) -> Unit = {},
    ): DownloadResult {
        return withContext(Dispatchers.IO) {
            try {
                emitProgress(onProgress, DownloadProgress(-1f, "正在连接服务器…"))
                val dir = updatesDir(context).also { it.mkdirs() }
                runCatching {
                    File(context.cacheDir, "updates").listFiles()?.forEach { it.delete() }
                }
                val out = File(dir, safeApkName(release))
                dir.listFiles()?.forEach { f ->
                    if (f.absolutePath != out.absolutePath) runCatching { f.delete() }
                }

                val expected = release.apkSize
                if (!forceRedownload && isValidApkFile(out, expected)) {
                    emitProgress(
                        onProgress,
                        DownloadProgress(1f, "本地安装包校验通过，准备安装…", out.length(), out.length()),
                    )
                    return@withContext finishForInstall(context, release.versionName, out)
                }
                if (out.exists()) runCatching { out.delete() }

                val urls = release.allDownloadUrls()
                if (urls.isEmpty()) {
                    return@withContext DownloadResult.Failed("没有可用的下载地址")
                }

                var lastError: String? = null
                for ((index, url) in urls.withIndex()) {
                    val hostHint = when {
                        url.contains("gitee.com", ignoreCase = true) -> "Gitee"
                        url.contains("github", ignoreCase = true) -> "GitHub"
                        else -> "镜像${index + 1}"
                    }
                    try {
                        if (out.exists()) runCatching { out.delete() }
                        emitProgress(
                            onProgress,
                            DownloadProgress(
                                0f,
                                "从 $hostHint 下载 v${release.versionName}…",
                                0L,
                                expected,
                            ),
                        )
                        downloadFile(url, out, expected) { read, total ->
                            val frac = if (total > 0) {
                                (read.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 0.99f)
                            } else {
                                -1f
                            }
                            val msg = if (total > 0) {
                                "$hostHint ${formatBytes(read)} / ${formatBytes(total)}"
                            } else {
                                "$hostHint ${formatBytes(read)}"
                            }
                            emitProgress(onProgress, DownloadProgress(frac, msg, read, total))
                        }
                        emitProgress(
                            onProgress,
                            DownloadProgress(0.99f, "正在校验安装包…", out.length(), expected),
                        )
                        val check = validateApkDetailed(out, expected)
                        if (check != null) {
                            runCatching { out.delete() }
                            lastError = "$hostHint 校验失败：$check"
                            continue
                        }
                        emitProgress(
                            onProgress,
                            DownloadProgress(
                                1f,
                                "校验通过 ${formatBytes(out.length())}（$hostHint）",
                                out.length(),
                                out.length(),
                            ),
                        )
                        return@withContext finishForInstall(context, release.versionName, out)
                    } catch (t: Throwable) {
                        lastError = "$hostHint：${friendlyError(t)}"
                        runCatching { out.delete() }
                    }
                }
                DownloadResult.Failed(
                    "全部镜像下载失败。\n${lastError.orEmpty()}\n" +
                        "请到网页下载：\n$GITEE_RELEASES_URL\n$GITHUB_RELEASES_URL",
                )
            } catch (t: Throwable) {
                DownloadResult.Failed(friendlyError(t))
            }
        }
    }

    fun cachedApk(context: Context, release: LatestRelease): File? {
        val f = File(updatesDir(context), safeApkName(release))
        return f.takeIf { isValidApkFile(it, release.apkSize) }
    }

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** 浏览器打开下载页：优先 Gitee（国内快），可再开 GitHub。 */
    fun openReleasesInBrowser(context: Context, preferGitee: Boolean = true) {
        val url = if (preferGitee) GITEE_RELEASES_URL else GITHUB_RELEASES_URL
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun openGithubReleasesInBrowser(context: Context) = openReleasesInBrowser(context, preferGitee = false)

    fun installApk(context: Context, apkFile: File): Boolean {
        val err = validateApkDetailed(apkFile, expectedSize = -1L)
        if (err != null) return false
        return runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                clipData = ClipData.newRawUri("apk", uri)
            }
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            val resolvers = context.packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY,
            )
            for (ri in resolvers) {
                val pkg = ri.activityInfo?.packageName ?: continue
                runCatching { context.grantUriPermission(pkg, uri, flags) }
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun updatesDir(context: Context): File = File(context.filesDir, "updates")

    private fun safeApkName(release: LatestRelease): String {
        val raw = release.apkName.ifBlank { "Madus-${release.versionName}.apk" }
        return raw.replace(Regex("""[^\w.\-]+"""), "_")
    }

    private fun finishForInstall(
        context: Context,
        version: String,
        apkFile: File,
    ): DownloadResult {
        return if (!canInstallPackages(context)) {
            DownloadResult.NeedInstallPermission(version, apkFile)
        } else {
            DownloadResult.ReadyToInstall(version, apkFile)
        }
    }

    private suspend fun emitProgress(
        onProgress: (DownloadProgress) -> Unit,
        progress: DownloadProgress,
    ) {
        withContext(Dispatchers.Main.immediate) {
            onProgress(progress)
        }
    }

    /**
     * 合并 Gitee / GitHub 最新版：
     * - 版本号取较高者
     * - 下载链：**Gitee 优先**，GitHub 作备用
     */
    private fun fetchMergedLatestRelease(): LatestRelease? {
        val gitee = runCatching { fetchGiteeLatest() }.getOrNull()
        val github = runCatching { fetchGithubLatest() }.getOrNull()
        if (gitee == null && github == null) return null
        if (gitee == null) return github
        if (github == null) return gitee

        val cmp = compareVersion(
            normalizeVersion(gitee.versionName),
            normalizeVersion(github.versionName),
        )
        return when {
            cmp > 0 -> gitee.copy(
                mirrorUrls = (gitee.mirrorUrls + github.allDownloadUrls()).distinct()
                    .filter { it != gitee.apkUrl },
                sourceLabel = "Gitee",
            )
            cmp < 0 -> {
                // GitHub 更新，仍把 Gitee 同名路径作镜像尝试（若已同步）
                val tag = if (github.tag.startsWith("v")) github.tag else "v${github.versionName}"
                val giteeGuess = giteeDownloadUrl(tag, github.apkName)
                github.copy(
                    apkUrl = giteeGuess, // 国内先试 Gitee 约定路径
                    mirrorUrls = (listOf(github.apkUrl) + github.mirrorUrls + gitee.allDownloadUrls())
                        .distinct()
                        .filter { it != giteeGuess },
                    sourceLabel = "GitHub（Gitee 优先镜像）",
                    apkSize = if (github.apkSize > 0) github.apkSize else gitee.apkSize,
                )
            }
            else -> {
                // 同版本：Gitee 主链 + GitHub 备用
                val urls = (gitee.allDownloadUrls() + github.allDownloadUrls()).distinct()
                gitee.copy(
                    apkUrl = urls.first(),
                    mirrorUrls = urls.drop(1),
                    apkSize = when {
                        gitee.apkSize > 0 -> gitee.apkSize
                        else -> github.apkSize
                    },
                    body = gitee.body.ifBlank { github.body },
                    sourceLabel = "Gitee + GitHub",
                )
            }
        }
    }

    private fun fetchGithubLatest(): LatestRelease {
        val text = httpGetText(GITHUB_API_LATEST) {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        val json = JSONObject(text)
        val tag = json.optString("tag_name", "").trim()
        val versionName = tag.removePrefix("v").removePrefix("V").trim()
        val body = json.optString("body", "")
        val (name, url, size) = pickApkAsset(json.optJSONArray("assets"))
            ?: error("GitHub 最新 Release 没有正式 APK")
        val giteeMirror = giteeDownloadUrl(
            tag = if (tag.startsWith("v")) tag else "v$versionName",
            apkName = name,
        )
        return LatestRelease(
            tag = tag,
            versionName = versionName.ifBlank { tag },
            apkUrl = giteeMirror,
            apkName = name,
            body = body,
            apkSize = size,
            mirrorUrls = listOf(url),
            sourceLabel = "GitHub",
        )
    }

    private fun fetchGiteeLatest(): LatestRelease {
        // 先 latest，失败再 list 取第一条
        val text = runCatching {
            httpGetText(GITEE_API_LATEST)
        }.getOrElse {
            val listText = httpGetText(GITEE_API_LIST)
            val arr = JSONArray(listText)
            if (arr.length() == 0) error("Gitee 尚无 Release")
            arr.getJSONObject(0).toString()
        }
        val json = JSONObject(text)
        val tag = json.optString("tag_name", "").trim()
        val versionName = tag.removePrefix("v").removePrefix("V").trim()
        val body = json.optString("body", "").ifBlank { json.optString("description", "") }
        // Gitee 附件字段：assets 或 attach_files
        val assets = json.optJSONArray("assets")
            ?: json.optJSONArray("attach_files")
        val picked = pickApkAsset(assets)
        val name: String
        val url: String
        val size: Long
        if (picked != null) {
            name = picked.first
            url = picked.second
            size = picked.third
        } else {
            // 无附件元数据时按约定文件名拼下载链
            name = "Madus-$versionName.apk"
            url = giteeDownloadUrl(
                tag = if (tag.startsWith("v")) tag else "v$versionName",
                apkName = name,
            )
            size = -1L
        }
        if (url.isBlank()) error("Gitee 最新 Release 没有 APK")
        return LatestRelease(
            tag = tag,
            versionName = versionName.ifBlank { tag },
            apkUrl = url,
            apkName = name,
            body = body,
            apkSize = size,
            mirrorUrls = emptyList(),
            sourceLabel = "Gitee",
        )
    }

    private fun giteeDownloadUrl(tag: String, apkName: String): String =
        "https://gitee.com/$GITEE_OWNER/$GITEE_REPO/releases/download/$tag/$apkName"

    /** @return Triple(name, url, size) */
    private fun pickApkAsset(assets: JSONArray?): Triple<String, String, Long>? {
        if (assets == null) return null
        var bestUrl = ""
        var bestName = ""
        var bestSize = -1L
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name", "")
                .ifBlank { a.optString("file_name", "") }
            val url = a.optString("browser_download_url", "")
                .ifBlank { a.optString("download_url", "") }
                .ifBlank {
                    // 部分 Gitee 只有 path
                    val path = a.optString("browser_download_url", "")
                    path
                }
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            if (name.contains("debug", ignoreCase = true)) continue
            if (url.isBlank()) continue
            if (bestUrl.isBlank() || name.startsWith("Madus-", ignoreCase = true)) {
                bestUrl = url
                bestName = name
                bestSize = a.optLong("size", a.optLong("file_size", -1L))
                if (name.startsWith("Madus-", ignoreCase = true)) break
            }
        }
        if (bestUrl.isBlank()) return null
        return Triple(bestName, bestUrl, bestSize)
    }

    private fun httpGetText(
        url: String,
        configure: HttpURLConnection.() -> Unit = {},
    ): String {
        val conn = openGet(url, configure)
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code == 403 || code == 429) {
                error("请求过于频繁或被限流（HTTP $code）")
            }
            if (code !in 200..299) error("HTTP $code ${text.take(160)}")
            return text
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun downloadFile(
        url: String,
        dest: File,
        hintedTotal: Long,
        onBytes: suspend (read: Long, total: Long) -> Unit,
    ) {
        val conn = openGetFollowingRedirects(url)
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = runCatching {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(120)
                }.getOrNull().orEmpty()
                error("下载失败 HTTP $code $err")
            }
            val contentType = conn.contentType?.lowercase().orEmpty()
            if (contentType.contains("text/html") || contentType.contains("application/json")) {
                error("服务器返回的不是 APK（$contentType）")
            }
            var total = conn.contentLengthLong
            if (total <= 0L) total = hintedTotal
            if (total <= 0L) {
                total = conn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            }
            val tmp = File(dest.absolutePath + ".part")
            if (tmp.exists()) runCatching { tmp.delete() }
            BufferedInputStream(conn.inputStream, 64 * 1024).use { input ->
                BufferedOutputStream(tmp.outputStream(), 64 * 1024).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var readTotal = 0L
                    var lastEmitAt = 0L
                    var lastEmitBytes = -1L
                    var firstChunk: ByteArray? = null
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        if (firstChunk == null) {
                            firstChunk = buf.copyOf(n)
                            if (looksLikeHtmlOrText(firstChunk)) {
                                error("下载内容不是 APK（疑似网页/错误页）")
                            }
                            if (!looksLikeZipApk(firstChunk)) {
                                error("下载内容缺少 ZIP 头（不是有效 APK）")
                            }
                        }
                        output.write(buf, 0, n)
                        readTotal += n
                        val now = System.currentTimeMillis()
                        val due = now - lastEmitAt >= 150L ||
                            readTotal - lastEmitBytes >= 256 * 1024L
                        if (due || lastEmitBytes < 0L) {
                            onBytes(readTotal, total)
                            lastEmitAt = now
                            lastEmitBytes = readTotal
                        }
                    }
                    output.flush()
                    onBytes(readTotal, if (total > 0) total else readTotal)
                }
            }
            runCatching {
                RandomAccessFile(tmp, "rw").use { it.fd.sync() }
            }
            if (dest.exists()) runCatching { dest.delete() }
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            // 仅当 Content-Length 可信时检查完整性；不强制与 Release size 完全一致（镜像可能差元数据）
            if (total > 0 && kotlin.math.abs(dest.length() - total) > SIZE_TOLERANCE) {
                runCatching { dest.delete() }
                error("下载不完整：期望 ${formatBytes(total)}，实际 ${formatBytes(dest.length())}")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun openGetFollowingRedirects(startUrl: String): HttpURLConnection {
        var current = startUrl
        var hops = 0
        while (hops < MAX_REDIRECTS) {
            hops++
            val conn = openGet(current) {
                instanceFollowRedirects = false
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc.isNullOrBlank()) error("下载重定向缺少 Location（HTTP $code）")
                current = if (loc.startsWith("http://") || loc.startsWith("https://")) {
                    loc
                } else {
                    URL(URL(current), loc).toString()
                }
            } else {
                return conn
            }
        }
        error("下载重定向次数过多")
    }

    private fun openGet(
        url: String,
        configure: HttpURLConnection.() -> Unit = {},
    ): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 180_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Madus-Android/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Accept", "application/octet-stream,*/*")
            instanceFollowRedirects = true
            configure()
            connect()
        }
    }

    fun isValidApkFile(file: File, expectedSize: Long = -1L): Boolean =
        validateApkDetailed(file, expectedSize) == null

    fun validateApkDetailed(file: File, expectedSize: Long = -1L): String? {
        if (!file.exists()) return "文件不存在"
        val len = file.length()
        if (len < MIN_APK_BYTES) {
            return "文件过小（${formatBytes(len)}），可能下载不完整"
        }
        // 有 expected 时允许镜像 size 元数据不准：只作弱提示，不硬失败（避免 Gitee/GitHub size 字段不一致）
        if (expectedSize > 0 && len < expectedSize / 2) {
            return "大小明显不符（期望约 ${formatBytes(expectedSize)}，实际 ${formatBytes(len)}）"
        }
        val header = ByteArray(4)
        runCatching {
            FileInputStream(file).use { n ->
                val r = n.read(header)
                if (r < 4) return "文件头不完整"
            }
        }.onFailure { return "无法读取文件：${it.message}" }
        if (!looksLikeZipApk(header)) {
            if (looksLikeHtmlOrText(header)) return "内容是网页/文本，不是 APK"
            return "不是有效的 ZIP/APK 文件头"
        }
        return runCatching {
            ZipFile(file).use { zip ->
                val hasManifest = zip.getEntry("AndroidManifest.xml") != null
                if (!hasManifest) return@runCatching "缺少 AndroidManifest.xml（损坏的 APK）"
                val hasPayload = zip.entries().asSequence().any { e ->
                    val n = e.name
                    n == "classes.dex" || n.startsWith("classes") && n.endsWith(".dex") ||
                        n == "resources.arsc"
                }
                if (!hasPayload) return@runCatching "APK 内缺少 dex/resources"
                null
            }
        }.getOrElse { "无法打开为 ZIP：${it.message ?: it.javaClass.simpleName}" }
    }

    private fun looksLikeZipApk(head: ByteArray): Boolean {
        if (head.size < 4) return false
        return head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() &&
            (head[2] == 0x03.toByte() || head[2] == 0x05.toByte() || head[2] == 0x07.toByte())
    }

    private fun looksLikeHtmlOrText(head: ByteArray): Boolean {
        if (head.isEmpty()) return false
        var i = 0
        if (head.size >= 3 && head[0] == 0xEF.toByte() && head[1] == 0xBB.toByte() && head[2] == 0xBF.toByte()) {
            i = 3
        }
        while (i < head.size && head[i].toInt().toChar().isWhitespace()) i++
        if (i >= head.size) return false
        val c = head[i].toInt().toChar()
        return c == '<' || c == '{' || c == '['
    }

    private fun friendlyError(t: Throwable): String {
        val m = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
        val low = m.lowercase()
        return when {
            low.contains("unable to resolve host") ||
                low.contains("failed to connect") ||
                low.contains("network is unreachable") ||
                low.contains("timeout") ||
                low.contains("timed out") ->
                "网络不通或连接超时。可换网络，或到 Gitee 下载：\n$GITEE_RELEASES_URL"
            else -> m
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "—"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    fun normalizeVersion(raw: String): String =
        raw.trim()
            .removePrefix("v").removePrefix("V")
            .substringBefore("-").substringBefore("_")
            .trim()

    fun compareVersion(a: String, b: String): Int {
        val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
        val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }
}
