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
 * 应用内检查更新：请求 GitHub Releases → 下载**正式** APK → 调起系统安装。
 * **不上传 / 不下载 debug 包。**
 */
object AppUpdate {
    const val GITHUB_OWNER = "zyjshb"
    const val GITHUB_REPO = "Madus"
    const val GITHUB_RELEASES_URL =
        "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    private const val API_LATEST =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    fun isPlaceholderUrl(): Boolean = false

    sealed class CheckResult {
        data class AlreadyLatest(val current: String) : CheckResult()
        data class ReadyToInstall(
            val version: String,
            val apkFile: File,
            val releaseNotes: String,
        ) : CheckResult()
        data class NeedInstallPermission(val version: String, val apkFile: File) : CheckResult()
        data class Failed(val message: String) : CheckResult()
    }

    data class LatestRelease(
        val tag: String,
        val versionName: String,
        val apkUrl: String,
        val apkName: String,
        val body: String,
    )

    /**
     * 检查最新版并下载正式 APK（跳过 debug）。
     * [onProgress]：0f～1f，文案提示用。
     */
    suspend fun checkAndDownload(
        context: Context,
        currentVersionName: String = BuildConfig.VERSION_NAME,
        onProgress: (String) -> Unit = {},
    ): CheckResult = withContext(Dispatchers.IO) {
        try {
            onProgress("正在检查更新…")
            val latest = fetchLatestRelease()
                ?: return@withContext CheckResult.Failed("无法获取最新版本信息，请稍后重试")

            val current = normalizeVersion(currentVersionName)
            val remote = normalizeVersion(latest.versionName)
            if (compareVersion(remote, current) <= 0) {
                return@withContext CheckResult.AlreadyLatest(currentVersionName.removeSuffix("-debug"))
            }

            onProgress("发现 v${latest.versionName}，正在下载…")
            val dir = File(context.cacheDir, "updates").also { it.mkdirs() }
            // 清旧包
            dir.listFiles()?.forEach { runCatching { it.delete() } }
            val out = File(dir, latest.apkName.ifBlank { "Madus-${latest.versionName}.apk" })
            downloadFile(latest.apkUrl, out) { read, total ->
                if (total > 0) {
                    val p = (read * 100 / total).toInt().coerceIn(0, 99)
                    onProgress("下载中 $p% · v${latest.versionName}")
                } else {
                    onProgress("下载中 ${(read / 1024)} KB · v${latest.versionName}")
                }
            }
            if (!out.exists() || out.length() < 100_000L) {
                return@withContext CheckResult.Failed("下载失败或文件过小")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                return@withContext CheckResult.NeedInstallPermission(latest.versionName, out)
            }
            CheckResult.ReadyToInstall(latest.versionName, out, latest.body)
        } catch (t: Throwable) {
            CheckResult.Failed(t.message ?: t.javaClass.simpleName)
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun installApk(context: Context, apkFile: File): Boolean {
        return runCatching {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
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
        if (code !in 200..299) {
            error("GitHub API $code ${text.take(120)}")
        }
        val json = JSONObject(text)
        val tag = json.optString("tag_name", "").trim()
        val versionName = tag.removePrefix("v").removePrefix("V").trim()
        val body = json.optString("body", "")
        val assets = json.optJSONArray("assets") ?: return null
        // 只认正式包：含 Madus、.apk，排除 debug
        var bestUrl = ""
        var bestName = ""
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name", "")
            val url = a.optString("browser_download_url", "")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            if (name.contains("debug", ignoreCase = true)) continue
            if (!name.contains("Madus", ignoreCase = true) && !name.contains("madus", ignoreCase = true)) {
                // 仍允许通用 release.apk
                if (!name.contains("release", ignoreCase = true) && !name.contains("app", ignoreCase = true)) {
                    continue
                }
            }
            bestUrl = url
            bestName = name
            break
        }
        if (bestUrl.isBlank()) error("最新 Release 没有正式 APK（已忽略 debug）")
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
        val code = conn.responseCode
        if (code !in 200..299) {
            error("下载失败 HTTP $code")
        }
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

    /** 去掉 -debug 等后缀，只留数字段比较 */
    fun normalizeVersion(raw: String): String {
        return raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("-")
            .substringBefore("_")
            .trim()
    }

    /** a > b → 正；相等 0；a < b → 负 */
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
