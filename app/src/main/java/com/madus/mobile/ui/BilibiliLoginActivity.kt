package com.madus.mobile.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.setPadding

/**
 * Bilibili WebView login. SESSDATA is HttpOnly — must use CookieManager.
 */
class BilibiliLoginActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = TextView(this).apply {
            text = "登录 B 站后点「完成」"
            textSize = 14f
            setPadding(28)
            setTextColor(0xFF111111.toInt())
            setBackgroundColor(0xFFF7F5F2.toInt())
        }
        val done = Button(this).apply {
            text = "完成"
            setOnClickListener { finishWithCookie() }
        }
        val cancel = Button(this).apply {
            text = "取消"
            setOnClickListener {
                setResult(RESULT_CANCELED)
                finish()
            }
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFFF7F5F2.toInt())
            setPadding(12)
            addView(cancel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(done, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        CookieManager.getInstance().setAcceptCookie(true)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 Mobile Safari/537.36"
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val c = collectCookie()
                    status.text = if (c.contains("SESSDATA")) {
                        "已检测到登录，点「完成」"
                    } else {
                        "登录后点完成 · ${url?.take(36) ?: ""}"
                    }
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    status.text = "加载中…"
                }
            }
            // Warm www for buvid, then passport
            loadUrl("https://passport.bilibili.com/login")
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF7F5F2.toInt())
            addView(status)
            addView(bar)
            addView(webView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView.canGoBack()) webView.goBack()
                    else {
                        setResult(RESULT_CANCELED)
                        finish()
                    }
                }
            },
        )
    }

    private fun collectCookie(): String {
        val cm = CookieManager.getInstance()
        val map = linkedMapOf<String, String>()
        listOf(
            "https://www.bilibili.com",
            "https://bilibili.com",
            "https://passport.bilibili.com",
            "https://api.bilibili.com",
            "https://m.bilibili.com",
        ).forEach { domain ->
            cm.getCookie(domain)?.split(';')?.forEach { piece ->
                val kv = piece.trim()
                val eq = kv.indexOf('=')
                if (eq > 0) map[kv.substring(0, eq).trim()] = kv.substring(eq + 1).trim()
            }
        }
        return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    private fun finishWithCookie() {
        CookieManager.getInstance().flush()
        // Visit www once so buvid merges
        runCatching {
            webView.loadUrl("https://www.bilibili.com")
        }
        webView.postDelayed({
            val cookie = collectCookie()
            if (!cookie.contains("SESSDATA")) {
                status.text = "还没有 SESSDATA，请先登录"
                Toast.makeText(this, "未检测到登录", Toast.LENGTH_SHORT).show()
                return@postDelayed
            }
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_COOKIE, cookie))
            finish()
        }, 600)
    }

    companion object {
        const val EXTRA_COOKIE = "cookie"
    }
}
