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

    data class LatestRelease(
        val tag: String,
        val versionName: String,
        val apkUrl: String,
        val apkName: String,
        val body: String,
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
            ProbeResult.Failed(t.message ?: t.javaClass.simpleName)
        }
    }

    /** 下载正式 APK（用户点「更新到最新版」后调用）。 */
    suspend fun downloadRelease(
        context: Context,
        release: LatestRelease,
        onProgress: (String) -> Unit = {},
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            onProgress("准备下载 v${release.versionName}…")
            val dir = File(context.cacheDir, "updates").also { it.mkdirs() }
            dir.listFiles()?.forEach { runCatching { it.delete() } }
            val out = File(dir, release.apkName.ifBlank { "Madus-${release.versionName}.apk" })
            downloadFile(release.apkUrl, out) { read, total ->
                if (total > 0) {
                    val p = (read * 100 / total).toInt().coerceIn(0, 99)
                    onProgress("下载中 $p%")
                } else {
                    onProgress("下载中 ${read / 1024} KB")
                }
            }
            if (!out.exists() || out.length() < 100_000L) {
                return@withContext DownloadResult.Failed("下载失败或文件过小")
            }
            onProgress("下载完成")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                return@withContext DownloadResult.NeedInstallPermission(release.versionName, out)
            }
            DownloadResult.ReadyToInstall(release.versionName, out)
        } catch (t: Throwable) {
            DownloadResult.Failed(t.message ?: t.javaClass.simpleName)
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

    private fun fetchLatestRelease(): LatestRelease? {
        val conn = (URL(API_LATEST).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Madus-Android/${BuildConfig.VERSION_NAME}")
            instanceFollowRedirects = true
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("GitHub API $code ${text.take(120)}")
        val json = JSONObject(text)
        val tag = json.optString("tag_name", "").trim()
        val versionName = tag.removePrefix("v").removePrefix("V").trim()
        val body = json.optString("body", "")
        val assets = json.optJSONArray("assets") ?: return null
        var bestUrl = ""
        var bestName = ""
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name", "")
            val url = a.optString("browser_download_url", "")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            if (name.contains("debug", ignoreCase = true)) continue
            bestUrl = url
            bestName = name
            break
        }
        if (bestUrl.isBlank()) error("最新 Release 没有正式 APK")
        return LatestRelease(
            tag = tag,
            versionName = versionName.ifBlank { tag },
            apkUrl = bestUrl,
            apkName = bestName,
            body = body,
        )
    }

    private fun downloadFile(
        url: String,
        dest: File,
        onBytes: (read: Long, total: Long) -> Unit,
    ) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 120_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Madus-Android/${BuildConfig.VERSION_NAME}")
            instanceFollowRedirects = true
        }
        if (conn.responseCode !in 200..299) error("下载失败 HTTP ${conn.responseCode}")
        val total = conn.contentLengthLong.coerceAtLeast(-1L)
        conn.inputStream.use { input ->
            dest.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var readTotal = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    output.write(buf, 0, n)
                    readTotal += n
                    onBytes(readTotal, total)
                }
                output.flush()
            }
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
