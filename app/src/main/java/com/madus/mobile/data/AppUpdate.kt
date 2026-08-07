package com.madus.mobile.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.madus.mobile.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内更新：查 GitHub Releases → 用户确认后再下载正式 APK → 调起安装。
 * **不处理 debug 包。**
 */
object AppUpdate {
    const val GITHUB_OWNER = "zyjshb"
    const val GITHUB_REPO = "Madus"
    const val GITHUB_RELEASES_URL =
        "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    private const val API_LATEST =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    private const val MAX_REDIRECTS = 8
    private const val MIN_APK_BYTES = 100_000L

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
     */
    suspend fun downloadRelease(
        context: Context,
        release: LatestRelease,
        onProgress: (DownloadProgress) -> Unit = {},
    ): DownloadResult {
        return withContext(Dispatchers.IO) {
            try {
                emitProgress(onProgress, DownloadProgress(-1f, "正在连接服务器…"))
                val dir = updatesDir(context).also { it.mkdirs() }
                // 清掉其它旧包，保留同名目标（避免误删刚下完的）
                val out = File(dir, release.apkName.ifBlank { "Madus-${release.versionName}.apk" })
                dir.listFiles()?.forEach { f ->
                    if (f.absolutePath != out.absolutePath) runCatching { f.delete() }
                }
                // 已有完整缓存则直接走安装
                val expected = release.apkSize
                if (out.exists() && out.length() >= MIN_APK_BYTES &&
                    (expected <= 0L || out.length() == expected)
                ) {
                    emitProgress(
                        onProgress,
                        DownloadProgress(1f, "已有本地安装包，准备安装…", out.length(), out.length()),
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
                if (!out.exists() || out.length() < MIN_APK_BYTES) {
                    return@withContext DownloadResult.Failed("下载失败或文件过小（${out.length()} 字节）")
                }
                emitProgress(
                    onProgress,
                    DownloadProgress(1f, "下载完成 ${formatBytes(out.length())}", out.length(), out.length()),
                )
                finishForInstall(context, release.versionName, out)
            } catch (t: Throwable) {
                DownloadResult.Failed(friendlyError(t))
            }
        }
    }

    /** 若缓存里已有可用 APK，可直接安装（授权后再次点更新时复用）。 */
    fun cachedApk(context: Context, release: LatestRelease): File? {
        val f = File(
            updatesDir(context),
            release.apkName.ifBlank { "Madus-${release.versionName}.apk" },
        )
        return f.takeIf {
            it.exists() && it.length() >= MIN_APK_BYTES &&
                (release.apkSize <= 0L || it.length() == release.apkSize)
        }
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

    fun installApk(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists() || apkFile.length() < MIN_APK_BYTES) return false
        return runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile,
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
            true
        }.getOrDefault(false)
    }

    private fun updatesDir(context: Context): File = File(context.cacheDir, "updates")

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
            bestUrl = url
            bestName = name
            bestSize = a.optLong("size", -1L)
            break
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
     * 进度节流：约每 150ms 或每 256KB 回调一次，避免狂刷 UI。
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
            var total = conn.contentLengthLong
            if (total <= 0L) total = hintedTotal
            // 有些 CDN 把长度放在 header 里大小写不一
            if (total <= 0L) {
                total = conn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            }
            val tmp = File(dest.absolutePath + ".part")
            if (tmp.exists()) runCatching { tmp.delete() }
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var readTotal = 0L
                    var lastEmitAt = 0L
                    var lastEmitBytes = -1L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
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
            if (dest.exists()) runCatching { dest.delete() }
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
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
                // 关掉自动跳转，自己处理，避免丢 Content-Length / 跨协议问题
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
            readTimeout = 120_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Madus-Android/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Accept", "*/*")
            // 默认 true；跟随重定向场景里会关掉
            instanceFollowRedirects = true
            configure()
            connect()
        }
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
