package com.madus.mobile.together

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.togetherPrefsStore by preferencesDataStore(name = "madus_together_prefs")

data class TogetherSettings(
    val serverUrl: String = DEFAULT_TOGETHER_SERVER_URL,
    val nickname: String = "",
    val mediaVolume: Float = 1.0f,
    val voiceGain: Float = 1.0f,
    val recentRooms: List<RecentRoomEntry> = emptyList(),
)

class TogetherPrefs(private val context: Context) {
    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val NICKNAME = stringPreferencesKey("nickname")
        val MEDIA_VOLUME = floatPreferencesKey("media_volume")
        val VOICE_GAIN = floatPreferencesKey("voice_gain")
        val RECENT_ROOMS = stringPreferencesKey("recent_rooms_json")
    }

    val flow: Flow<TogetherSettings> = context.togetherPrefsStore.data.map { p ->
        TogetherSettings(
            serverUrl = normalizeServerUrl(p[Keys.SERVER_URL]),
            nickname = p[Keys.NICKNAME].orEmpty(),
            mediaVolume = (p[Keys.MEDIA_VOLUME] ?: 1.0f).coerceIn(0f, 1f),
            voiceGain = (p[Keys.VOICE_GAIN] ?: 1.0f).coerceIn(0f, 2f),
            recentRooms = RecentRoomEntry.fromJsonList(p[Keys.RECENT_ROOMS]),
        )
    }

    suspend fun current(): TogetherSettings = flow.first()

    suspend fun setServerUrl(url: String) {
        context.togetherPrefsStore.edit { p ->
            p[Keys.SERVER_URL] = normalizeServerUrl(url)
        }
    }

    suspend fun setNickname(nickname: String) {
        context.togetherPrefsStore.edit { p ->
            p[Keys.NICKNAME] = nickname.trim().take(24)
        }
    }

    suspend fun setVolumes(mediaVolume: Float, voiceGain: Float) {
        context.togetherPrefsStore.edit { p ->
            p[Keys.MEDIA_VOLUME] = mediaVolume.coerceIn(0f, 1f)
            p[Keys.VOICE_GAIN] = voiceGain.coerceIn(0f, 2f)
        }
    }

    suspend fun recordRecentRoom(roomId: String, nickname: String) {
        val cleanId = roomId.trim()
        if (cleanId.isBlank()) return
        context.togetherPrefsStore.edit { p ->
            val current = RecentRoomEntry.fromJsonList(p[Keys.RECENT_ROOMS])
            val updated = buildList {
                add(RecentRoomEntry(cleanId, nickname.trim(), System.currentTimeMillis()))
                current.filterNot { it.roomId.equals(cleanId, ignoreCase = true) }
                    .take(4)
                    .forEach(::add)
            }
            p[Keys.RECENT_ROOMS] = RecentRoomEntry.toJsonList(updated)
        }
    }

    companion object {
        fun normalizeServerUrl(raw: String?): String {
            val trimmed = raw.orEmpty().trim().trimEnd('/')
            if (trimmed.isBlank()) return DEFAULT_TOGETHER_SERVER_URL
            return when {
                trimmed.startsWith("http://", ignoreCase = true) ||
                    trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                trimmed.startsWith("ws://", ignoreCase = true) ->
                    "http://" + trimmed.removePrefix("ws://")
                trimmed.startsWith("wss://", ignoreCase = true) ->
                    "https://" + trimmed.removePrefix("wss://")
                else -> "http://$trimmed"
            }
        }

        fun toWsUrl(serverHttpUrl: String): String {
            val norm = normalizeServerUrl(serverHttpUrl)
            return when {
                norm.startsWith("https://", ignoreCase = true) ->
                    "wss://" + norm.substringAfter("://") + "/ws"
                norm.startsWith("http://", ignoreCase = true) ->
                    "ws://" + norm.substringAfter("://") + "/ws"
                else -> "ws://$norm/ws"
            }
        }
    }
}
