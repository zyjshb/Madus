package com.madus.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.legalPrefsStore by preferencesDataStore(name = "madus_legal_prefs")

/**
 * 用户协议同意状态。
 *
 * 规则：
 * - 用户点过「我知道了」后本地记下 [CURRENT_VERSION]
 * - **普通 App 升级不会再弹**
 * - 只有开发者主动把 [CURRENT_VERSION] 调大时，才会强制再读一遍（协议正文大改时用）
 */
class LegalPrefs(private val context: Context) {
    val acceptedVersionFlow: Flow<Int> = context.legalPrefsStore.data.map { prefs ->
        prefs[keyAcceptedVersion] ?: 0
    }

    val hasAcceptedCurrentFlow: Flow<Boolean> = acceptedVersionFlow.map { it >= CURRENT_VERSION }

    suspend fun acceptCurrent() {
        context.legalPrefsStore.edit { prefs ->
            prefs[keyAcceptedVersion] = CURRENT_VERSION
            prefs[keyAcceptedAt] = System.currentTimeMillis()
        }
    }

    companion object {
        /** 协议正文有实质修改时再 +1；平时发版不要动这个数 */
        const val CURRENT_VERSION = 3

        private val keyAcceptedVersion = intPreferencesKey("user_agreement_version")
        private val keyAcceptedAt = longPreferencesKey("user_agreement_accepted_at")
    }
}
