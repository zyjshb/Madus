package com.madus.mobile.data

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地诊断日志：崩溃堆栈 + 周期性内存/队列快照。
 *
 * 为什么需要它：`卡退` 可能是异常（有堆栈）、ANR（主线程卡死）或 OOM 被系统杀（无堆栈）。
 * 三者表现一样，只看现象分不出来。这里把能落盘的信息都写下来：
 * - 未捕获异常 → 完整堆栈；
 * - 每 60 秒一条 `mem` 心跳（堆占用、队列长度、事件条数、候选池大小），能看到是不是内存一路涨；
 * - 写两份：应用私有目录（一定成功）与 `下载/Madus/`（用户能直接取到发给我）。
 */
object Diagnostics {
    private const val TAG = "MadusDiag"
    private const val FILE_NAME = "madus-diag.log"
    private const val MAX_BYTES = 256 * 1024L
    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val lock = Any()
    private var installed = false
    private var appContext: Context? = null

    fun install(app: Application) {
        if (installed) return
        installed = true
        appContext = app.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { recordCrash(thread, error) }
            // 协程里没接住的普通异常（网络/解析/存储等）只记日志不杀进程；
            // OOM 等 Error 以及真正的线程崩溃仍交给系统默认处理。
            if (error is Exception && isFromCoroutineHandler()) return@setDefaultUncaughtExceptionHandler
            previous?.uncaughtException(thread, error)
        }
        log(
            "boot",
            "version=${com.madus.mobile.BuildConfig.VERSION_NAME}(${com.madus.mobile.BuildConfig.VERSION_CODE}) " +
                "sdk=${Build.VERSION.SDK_INT} model=${Build.MANUFACTURER}/${Build.MODEL}",
        )
    }

    /** 普通埋点：低开销，出错时静默丢弃。 */
    fun log(tag: String, message: String) {
        runCatching {
            val line = "${stamp.format(Date())} [$tag] $message\n"
            synchronized(lock) { append(line) }
        }
    }

    /** kotlinx.coroutines 在未处理异常时会在当前线程同步调用默认 handler，调用栈里可识别。 */
    private fun isFromCoroutineHandler(): Boolean =
        Throwable().stackTrace.any {
            it.className.startsWith("kotlinx.coroutines.CoroutineExceptionHandlerImplKt") ||
                (it.className.startsWith("kotlinx.coroutines.CoroutineExceptionHandlerKt") &&
                    it.methodName.startsWith("handleCoroutineException"))
        }

    private fun recordCrash(thread: Thread, error: Throwable) {
        val sw = StringWriter()
        PrintWriter(sw).use { error.printStackTrace(it) }
        val line = "${stamp.format(Date())} [crash] thread=${thread.name} ${error.javaClass.name}: ${error.message}\n" +
            sw.toString() + "\n"
        synchronized(lock) {
            append(line)
            runCatching { publishToDownloads() }
        }
    }

    /** 把日志复制到系统「下载/Madus/」，用户可以直接用文件管理取出来。 */
    fun publishToDownloads(): Boolean {
        // MediaStore.Downloads 从 API 29 才有，低版本只保留应用私有日志，避免类加载问题。
        if (Build.VERSION.SDK_INT < 29) return false
        val context = appContext ?: return false
        val src = logFile(context).takeIf { it.exists() && it.length() > 0 } ?: return false
        return runCatching {
            val name = "madus-diag-${System.currentTimeMillis()}.log"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Madus")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
            resolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    private fun logFile(context: Context): File {
        val dir = File(context.filesDir, "logs").apply { mkdirs() }
        return File(dir, FILE_NAME)
    }

    private fun append(line: String) {
        val context = appContext ?: return
        val file = logFile(context)
        runCatching {
            if (file.exists() && file.length() > MAX_BYTES) {
                val keep = File(file.parentFile, "$FILE_NAME.old")
                runCatching { keep.delete() }
                runCatching { file.renameTo(keep) }
            }
            file.appendText(line)
        }
        // 崩溃与启动这类低频事件才同步镜像一份，心跳不镜像。
        if (line.contains("[crash]") || line.contains("[boot]")) {
            runCatching { publishToDownloads() }
        }
    }
}
