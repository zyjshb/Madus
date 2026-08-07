package com.madus.mobile.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class HummingConfigState(
    val appId: String = "",
    val apiKey: String = "",
    val acrHost: String = "",
    val acrAccessKey: String = "",
    val acrAccessSecret: String = "",
) {
    val xunfeiConfigured: Boolean get() = appId.isNotBlank() && apiKey.isNotBlank()
    val acrConfigured: Boolean get() =
        acrHost.isNotBlank() && acrAccessKey.isNotBlank() && acrAccessSecret.isNotBlank()
    val isConfigured: Boolean get() = xunfeiConfigured || acrConfigured
}

class HummingConfigStore(context: Context) {
    private val app = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        runCatching { encryptedPrefs() }.getOrElse {
            app.getSharedPreferences(FALLBACK_PREFS, Context.MODE_PRIVATE)
        }
    }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<HummingConfigState> = _state.asStateFlow()

    private fun encryptedPrefs(): SharedPreferences {
        val master = MasterKey.Builder(app)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            app,
            ENCRYPTED_PREFS,
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun load(): HummingConfigState {
        return HummingConfigState(
            appId = prefs.getString(KEY_APP_ID, null).orEmpty(),
            apiKey = prefs.getString(KEY_API_KEY, null).orEmpty(),
            acrHost = prefs.getString(KEY_ACR_HOST, null).orEmpty(),
            acrAccessKey = prefs.getString(KEY_ACR_ACCESS_KEY, null).orEmpty(),
            acrAccessSecret = prefs.getString(KEY_ACR_ACCESS_SECRET, null).orEmpty(),
        )
    }

    suspend fun save(
        appId: String,
        apiKey: String,
        acrHost: String = "",
        acrAccessKey: String = "",
        acrAccessSecret: String = "",
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val a = appId.trim()
        val k = apiKey.trim()
        val host = acrHost.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
        val ak = acrAccessKey.trim()
        val secret = acrAccessSecret.trim()
        val xunfeiOk = a.isNotEmpty() && k.isNotEmpty()
        val acrOk = host.isNotEmpty() && ak.isNotEmpty() && secret.isNotEmpty()
        if (!xunfeiOk && !acrOk) {
            return@withContext Result.failure(
                IllegalArgumentException("请至少填写一组：讯飞 AppID/API Key，或 ACRCloud 三项"),
            )
        }
        prefs.edit()
            .putString(KEY_APP_ID, a)
            .putString(KEY_API_KEY, k)
            .putString(KEY_ACR_HOST, host)
            .putString(KEY_ACR_ACCESS_KEY, ak)
            .putString(KEY_ACR_ACCESS_SECRET, secret)
            .apply()
        _state.value = HummingConfigState(a, k, host, ak, secret)
        Result.success(Unit)
    }

    fun maskedApiKey(): String {
        val k = prefs.getString(KEY_API_KEY, null).orEmpty()
        return mask(k)
    }

    fun maskedAcrAccessKey(): String {
        val k = prefs.getString(KEY_ACR_ACCESS_KEY, null).orEmpty()
        return mask(k)
    }

    private fun mask(k: String): String {
        if (k.isEmpty()) return "未配置"
        if (k.length <= 8) return "••••"
        return k.take(4) + "••••" + k.takeLast(4)
    }

    fun hasConfig(): Boolean = _state.value.isConfigured

    companion object {
        private const val ENCRYPTED_PREFS = "madus_humming_secure"
        private const val FALLBACK_PREFS = "madus_humming_secure_fallback"
        private const val KEY_APP_ID = "xfyun_app_id"
        private const val KEY_API_KEY = "xfyun_api_key"
        private const val KEY_ACR_HOST = "acr_host"
        private const val KEY_ACR_ACCESS_KEY = "acr_access_key"
        private const val KEY_ACR_ACCESS_SECRET = "acr_access_secret"
    }
}
