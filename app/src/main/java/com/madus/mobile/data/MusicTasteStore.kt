package com.madus.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.madus.mobile.domain.MusicDiscovery
import com.madus.mobile.domain.TasteMode
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.musicTasteStore by preferencesDataStore(name = "madus_music_taste")

data class MusicTaste(
    val preferredTopics: Set<String> = emptySet(),
    /** 空选择也能完成设置：让收听行为继续学习口味。 */
    val configured: Boolean = false,
    /** 熟悉 / 均衡 / 新鲜。默认均衡，等于改造前的行为。 */
    val mode: TasteMode = TasteMode.BALANCED,
)

/** 用户主动选择的口味独立保存，清空选择不会删除收听历史或喜欢的歌。 */
class MusicTasteStore(context: Context) {
    private val store = context.applicationContext.musicTasteStore
    private val topicsKey = stringSetPreferencesKey("preferred_topics_v1")
    private val configuredKey = booleanPreferencesKey("configured_v1")
    private val modeKey = stringPreferencesKey("taste_mode_v1")

    val flow: Flow<MusicTaste> = store.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            MusicTaste(
                preferredTopics = preferences[topicsKey].orEmpty()
                    .intersect(MusicDiscovery.availableTopics.keys),
                configured = preferences[configuredKey] ?: false,
                mode = TasteMode.of(preferences[modeKey]),
            )
        }

    suspend fun load(): MusicTaste = flow.first()

    suspend fun save(topics: Set<String>) {
        val supported = topics.intersect(MusicDiscovery.availableTopics.keys)
        store.edit { preferences ->
            preferences[topicsKey] = supported
            preferences[configuredKey] = true
        }
    }

    suspend fun saveMode(mode: TasteMode) {
        store.edit { preferences -> preferences[modeKey] = mode.name }
    }
}
