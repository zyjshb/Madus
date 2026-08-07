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
 * 应用内更新：查 GitHub Releases → 用户确认后再下载正式 APK → 校验 → 调起安装。
 * **不处理 debug 包。**
 *
 * 「解析软件包失败」常见原因：下到的不是完整 APK（HTML/截断）。
 * 因此下载后强制校验：大小、ZIP 魔数、可打开 Zip、含 AndroidManifest.xml。
 */
object AppUpdate {
    const val GITHUB_OWNER = "zyjshb"
    const val GITHUB_REPO = "Madus"
    const val GITHUB_RELEASES_URL =
        "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    private const val API_LATEST =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    private const val MAX_REDIRECTS = 8
    /** 正式包约 16MB+，过小一律当无效（避免把错误页当 APK 去装）。 */
    private const val MIN_APK_BYTES = 3_000_000L
    private const val SIZE_TOLERANCE = 4096L

    data class LatestRelease(
        val tag: String,
        val versionName: String,
        val apkUrl: String,
        val apkName: String,
        val body: String,
        val apkSize: Long = -1L,
    )

    /** 下载进度：fraction 0..1（未知总长时为 -1），message 给人看。 */
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

    /** 只查询最新版，不下载。 */
    suspend fun probeLatest(
        currentVersionName: String = BuildConfig.VERSION_NAME,
    ): ProbeResult = withContext(Dispatchers.IO) {
        try {
            val latest = fetchLatestRelease()
                ?: return@withContext ProbeResult.Failed("无法获取最新版本信息")
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
     * 下载正式 APK（用户点「更新到最新版」后调用）。
     * [onProgress] 在 **Main** 线程回调，可直接改 Compose state。
     * @param forceRedownload true 时忽略本地缓存，强制重下（解析失败后可点「重新下载」）
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
                // 清理旧 cache 目录残留（1.14.5 以前下在 cache）
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

                emitProgress(
                    onProgress,
                    DownloadProgress(0f, "已开始下载 v${release.versionName}…", 0L, expected),
                )
                downloadFile(release.apkUrl, out, expected) { read, total ->
                    val frac = if (total > 0) {
                        (read.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 0.99f)
                    } else {
                        -1f
                    }
                    val msg = if (total > 0) {
                        "下载中 ${formatBytes(read)} / ${formatBytes(total)}"
                    } else {
                        "下载中 ${formatBytes(read)}"
                    }
                    emitProgress(onProgress, DownloadProgress(frac, msg, read, total))
                }

                emitProgress(onProgress, DownloadProgress(0.99f, "正在校验安装包…", out.length(), expected))
                val check = validateApkDetailed(out, expected)
                if (check != null) {
                    runCatching { out.delete() }
                    return@withContext DownloadResult.Failed(
                        "安装包校验失败：$check\n请点「重新下载」，或到网页下载：\n$GITHUB_RELEASES_URL",
                    )
                }

                emitProgress(
                    onProgress,
                    DownloadProgress(1f, "校验通过 ${formatBytes(out.length())}", out.length(), out.length()),
                )
                finishForInstall(context, release.versionName, out)
            } catch (t: Throwable) {
                DownloadResult.Failed(friendlyError(t))
            }
        }
    }

    /** 若缓存里已有**通过校验**的 APK，可直接安装。 */
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

    /** 浏览器打开 Releases 页（应用内下载失败时的备用）。 */
    fun openReleasesInBrowser(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASES_URL))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * 调起系统安装器。会：
     * - 再校验一次 APK（防止坏包进安装器 →「解析软件包失败」）
     * - 给所有能处理安装的 App 授 FileProvider 读权限
     */
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
            // 显式授权给所有包安装器（部分国产 ROM 仅靠 FLAG 不够）
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            val resolvers = context.packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY,
            )
            for (ri in resolvers) {
                val pkg = ri.activityInfo?.packageName ?: continue
                runCatching {
                    context.grantUriPermission(pkg, uri, flags)
                }
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /** 用 files 目录存 APK（比 cache 更稳，系统清理 cache 会导致安装解析失败）。 */
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

    private fun fetchLatestRelease(): LatestRelease? {
        val conn = openGet(API_LATEST) {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code == 403 || code == 429) {
            error("GitHub 请求过于频繁或被限流（HTTP $code），请稍后再试")
        }
        if (code !in 200..299) error("GitHub API HTTP $code ${text.take(160)}")
        val json = JSONObject(text)
        val tag = json.optString("tag_name", "").trim()
        val versionName = tag.removePrefix("v").removePrefix("V").trim()
        val body = json.optString("body", "")
        val assets = json.optJSONArray("assets") ?: return null
        var bestUrl = ""
        var bestName = ""
        var bestSize = -1L
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name", "")
            val url = a.optString("browser_download_url", "")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            if (name.contains("debug", ignoreCase = true)) continue
            // 优先 Madus-x.y.z.apk
            if (bestUrl.isBlank() || name.startsWith("Madus-", ignoreCase = true)) {
                bestUrl = url
                bestName = name
                bestSize = a.optLong("size", -1L)
                if (name.startsWith("Madus-", ignoreCase = true)) break
            }
        }
        if (bestUrl.isBlank()) error("最新 Release 没有正式 APK（仅支持非 debug 包）")
        return LatestRelease(
            tag = tag,
            versionName = versionName.ifBlank { tag },
            apkUrl = bestUrl,
            apkName = bestName,
            body = body,
            apkSize = bestSize,
        )
    }

    /**
     * 手动跟随重定向，保证拿到最终 CDN 的 Content-Length。
     * 进度节流：约每 150ms 或每 256KB 回调一次。
     */
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
                error("服务器返回的不是 APK（$contentType），可能被限流或网络劫持")
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
                    // 先读前几个字节，挡 HTML
                    var firstChunk: ByteArray? = null
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        if (firstChunk == null) {
                            firstChunk = buf.copyOf(n)
                            if (looksLikeHtmlOrText(firstChunk)) {
                                error("下载内容不是 APK（疑似网页/错误页），请换网络或浏览器下载")
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
            // 尽量把数据刷到磁盘，减少「装一半」
            runCatching {
                RandomAccessFile(tmp, "rw").use { it.fd.sync() }
            }
            if (dest.exists()) runCatching { dest.delete() }
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            if (total > 0 && kotlin.math.abs(dest.length() - total) > SIZE_TOLERANCE) {
                runCatching { dest.delete() }
                error("下载不完整：期望 ${formatBytes(total)}，实际 ${formatBytes(dest.length())}")
            }
            if (hintedTotal > 0 && kotlin.math.abs(dest.length() - hintedTotal) > SIZE_TOLERANCE) {
                runCatching { dest.delete() }
                error(
                    "文件大小与 Release 不符：期望 ${formatBytes(hintedTotal)}，" +
                        "实际 ${formatBytes(dest.length())}",
                )
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
            connectTimeout = 25_000
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

    /**
     * @return null 表示通过；否则为失败原因（中文）
     */
    fun validateApkDetailed(file: File, expectedSize: Long = -1L): String? {
        if (!file.exists()) return "文件不存在"
        val len = file.length()
        if (len < MIN_APK_BYTES) {
            return "文件过小（${formatBytes(len)}），可能下载不完整"
        }
        if (expectedSize > 0 && kotlin.math.abs(len - expectedSize) > SIZE_TOLERANCE) {
            return "大小不符（期望 ${formatBytes(expectedSize)}，实际 ${formatBytes(len)}）"
        }
        // ZIP local file header: PK\x03\x04
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
                // 再扫一眼至少有 classes 或 resources
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
        // UTF-8 BOM / whitespace + <
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
                "网络不通或连接超时（访问 GitHub 失败）。可稍后重试，或到网页下载：\n$GITHUB_RELEASES_URL"
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
