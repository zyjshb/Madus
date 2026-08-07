package com.madus.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.sessionDataStore by preferencesDataStore(name = "madus_session")

class SessionStore(private val context: Context) {
    private val keyBiliCookie = stringPreferencesKey("bilibili_cookie")

    suspend fun getBiliCookie(): String =
        context.sessionDataStore.data.first()[keyBiliCookie].orEmpty()

    suspend fun setBiliCookie(cookie: String) {
        context.sessionDataStore.edit { it[keyBiliCookie] = cookie }
    }

    suspend fun clearBiliCookie() {
        context.sessionDataStore.edit { it.remove(keyBiliCookie) }
    }
}
