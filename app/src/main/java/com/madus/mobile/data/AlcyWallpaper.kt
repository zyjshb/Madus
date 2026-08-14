package com.madus.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 栗次元随机图：https://t.alcy.cc
 * 手机竖图默认分类 `mp`。
 */
object AlcyWallpaper {
    const val DEFAULT_CATEGORY = "mp"
    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    suspend fun fetchRandomUrl(category: String = DEFAULT_CATEGORY): String? = withContext(Dispatchers.IO) {
        val url = "https://t.alcy.cc/json?$category"
        val body = getText(url) ?: return@withContext null
        parseLink(body)
    }

    suspend fun download(url: String, dest: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            dest.parentFile?.mkdirs()
            val conn = open(url)
            conn.inputStream.use { inp ->
                val tmp = File(dest.absolutePath + ".part")
                tmp.outputStream().use { out -> inp.copyTo(out) }
                if (dest.exists()) dest.delete()
                tmp.renameTo(dest)
            }
            conn.disconnect()
            dest.exists() && dest.length() > 80
        }.getOrDefault(false)
    }

    private fun parseLink(body: String): String? {
        val root = JSONObject(body)
        if (root.optInt("code", 0) != 200 && !root.has("data") && !root.has("url")) return null
        root.optString("url").takeIf { it.startsWith("http") }?.let { return it }
        val data = root.opt("data") ?: return null
        if (data is JSONObject) {
            return data.optString("link").takeIf { it.startsWith("http") }
        }
        if (data is org.json.JSONArray && data.length() > 0) {
            return data.optJSONObject(0)?.optString("link")?.takeIf { it.startsWith("http") }
        }
        return null
    }

    private fun getText(url: String): String? {
        val conn = open(url)
        return try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        var current = url
        repeat(5) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 12_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Referer", "https://t.alcy.cc/")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val next = conn.getHeaderField("Location")
                conn.disconnect()
                if (next.isNullOrBlank()) return conn
                current = if (next.startsWith("http")) next else URL(URL(current), next).toString()
            } else {
                return conn
            }
        }
        return (URL(current).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", UA)
        }
    }
}
