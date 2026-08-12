package com.madus.mobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.madus.mobile.data.ThemeSettings
import com.madus.mobile.ui.BilibiliLoginActivity
import com.madus.mobile.ui.LoginCoordinator
import com.madus.mobile.ui.MadusRoot
import com.madus.mobile.ui.legal.UserAgreementScreen
import com.madus.mobile.ui.splash.BrandSplash
import com.madus.mobile.ui.theme.MadusTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val biliLoginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val cookie = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra(BilibiliLoginActivity.EXTRA_COOKIE)
        } else null
        LoginCoordinator.complete(cookie)
    }

    /** 首次播放时申请；允许后通知栏显示曲名/控制 */
    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            // 权限刚下发：若已在播，刷新一次服务通知
            runCatching { MadusApp.instance.ensurePlaybackService() }
        }
    }

    /** 系统 Splash 只挡到首帧，真正开屏在 Compose */
    @Volatile
    private var keepSplash = true

    /** 本进程只弹一次系统权限框，避免连切歌刷屏 */
    @Volatile
    private var notifPermAskedThisProcess = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { keepSplash }
        splash.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .setDuration(160L)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { provider.remove() }
                .start()
        }

        super.onCreate(savedInstanceState)
        MadusApp.instance.startBiliLogin = {
            biliLoginLauncher.launch(Intent(this, BilibiliLoginActivity::class.java))
        }
        MadusApp.instance.requestPostNotifications = {
            requestPlaybackNotificationPermission()
        }
        enableEdgeToEdge()
        // 强制 adjustResize：部分输入法不读 manifest 的软键盘模式，会导致 AI 输入框被盖住
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN,
        )
        setContent {
            val theme by MadusApp.instance.themePrefs.flow.collectAsState(
                initial = ThemeSettings(),
            )
            var brandSplashDone by remember { mutableStateOf(false) }
            // null=读取中, true/false=是否已同意当前版协议
            var legalAccepted by remember { mutableStateOf<Boolean?>(null) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                delay(40)
                keepSplash = false
                legalAccepted = MadusApp.instance.legalPrefs.hasAcceptedCurrentFlow.first()
            }

            MadusTheme(
                appearance = theme.appearance,
                colorTheme = theme.colorTheme,
            ) {
                // 开屏固定品牌底；协议/主界面跟当前主题背景，避免黑屏协议切线稿纸色跳戏
                val shellBg = when {
                    !brandSplashDone || legalAccepted == null -> Color(0xFF1F2121)
                    else -> MaterialTheme.colorScheme.background
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(shellBg),
                ) {
                    when {
                        !brandSplashDone -> {
                            BrandSplash(onFinished = { brandSplashDone = true })
                        }
                        legalAccepted == null -> {
                            // DataStore 读盘中，保持开屏同色底
                        }
                        legalAccepted == false -> {
                            UserAgreementScreen(
                                onAccepted = {
                                    scope.launch {
                                        MadusApp.instance.legalPrefs.acceptCurrent()
                                        legalAccepted = true
                                    }
                                },
                            )
                        }
                        else -> {
                            MadusRoot(modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }

    /**
     * 仅 Android 13+ 且未授权时弹出；开播时触发，不在冷启动硬弹。
     */
    private fun requestPlaybackNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        if (notifPermAskedThisProcess) return
        notifPermAskedThisProcess = true
        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onDestroy() {
        if (isFinishing) {
            MadusApp.instance.startBiliLogin = null
            MadusApp.instance.requestPostNotifications = null
        }
        super.onDestroy()
    }
}
