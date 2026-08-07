package com.madus.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.playerPrefsStore by preferencesDataStore(name = "madus_player_prefs")

/** 取流清晰度偏好（B 站 playurl qn 档）。 */
enum class AudioQuality(val id: String, val label: String, val qn: Int) {
    DataSaver("data_saver", "省流", 16),
    Standard("standard", "标准", 64),
    High("high", "较高", 80),
    Highest("highest", "最高可用", 0),
    ;

    companion object {
        fun fromId(id: String?) = entries.find { it.id == id } ?: Standard
    }
}

/**
 * 环境音效预设（系统 Equalizer 频段近似）。
 */
enum class SoundFx(val id: String, val label: String, val subtitle: String) {
    Flat("flat", "原声", "不改音色"),
    Bass("bass", "低音增强", "鼓点更厚"),
    Vocal("vocal", "人声突出", "中频抬升"),
    Soft("soft", "柔和", "夜晚/耳机"),
    Night("night", "夜听", "压高音、护耳"),
    Live("live", "现场感", "稍亮、空间感"),
    ;

    companion object {
        fun fromId(id: String?) = entries.find { it.id == id } ?: Flat
    }
}

/**
 * 短视频手势操作模式（仅影响竖屏清屏 / 横屏全屏交互，不影响音源）。
 */
/**
 * 短视频手势模式（对照三家主流产品）：
 *
 * 抖音：单击暂停/续播；双击点赞；长按 2 倍速（松手恢复）；上下滑切条；侧栏信息常显。
 * B站：单击显隐控制条（不直接暂停）；中心/底栏播控；长按加速；控制条播放中自动藏。
 * 快手：单击暂停/续播；双击点赞；长按弹出详细菜单（含倍速档）；上下滑切条。
 */
enum class VideoGestureMode(val id: String, val label: String, val subtitle: String) {
    DOUYIN("douyin", "抖音", "单击暂停 · 双击赞 · 长按 2 倍速 · 侧栏常显"),
    BILIBILI("bilibili", "B站", "单击清屏 · 双击暂停 · 下半屏加速 · 上半屏菜单"),
    KUAISHOU("kuaishou", "快手", "单击暂停 · 双击赞 · 长按出菜单/倍速 · 侧栏常显"),
    ;

    companion object {
        fun fromId(id: String?) = entries.find { it.id == id } ?: DOUYIN
    }
}

/** 长按「更多」菜单顶部倍速档（0.75x ~ 10.0x） */
val VIDEO_SPEED_OPTIONS: List<Float> = listOf(
    0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f, 5.0f, 8.0f, 10.0f,
)

data class PlayerSettings(
    val quality: AudioQuality = AudioQuality.Standard,
    val soundFx: SoundFx = SoundFx.Flat,
    /** 边听边写磁盘缓存；关=纯在线，更轻量 */
    val autoCache: Boolean = false,
    /**
     * 视频模式：开=可看 B 站视频画面；关=纯音频听歌。
     * 内核仍是 Bilibili 取流。
     */
    val videoMode: Boolean = false,
    /** 短视频手势：抖音 / B站 / 快手 */
    val gestureMode: VideoGestureMode = VideoGestureMode.DOUYIN,
)

class PlayerPrefs(private val context: Context) {
    private val keyQuality = stringPreferencesKey("audio_quality")
    private val keyFx = stringPreferencesKey("sound_fx")
    private val keyAutoCache = booleanPreferencesKey("auto_cache")
    private val keyVideoMode = booleanPreferencesKey("video_mode")
    private val keyGuideSeen = booleanPreferencesKey("short_video_guide_seen_v1")
    private val keyGestureMode = stringPreferencesKey("video_gesture_mode")
    /** 各操作模式是否看过专属指引（按 mode.id 存） */
    private fun guideModeKey(mode: VideoGestureMode) =
        booleanPreferencesKey("short_video_guide_${mode.id}_v2")

    val flow: Flow<PlayerSettings> = context.playerPrefsStore.data.map { prefs ->
        PlayerSettings(
            quality = AudioQuality.fromId(prefs[keyQuality]),
            soundFx = SoundFx.fromId(prefs[keyFx]),
            // 默认关：轻量化在线播放
            autoCache = prefs[keyAutoCache] ?: false,
            // 默认音乐模式；用户可在推荐页切换视频模式
            videoMode = prefs[keyVideoMode] ?: false,
            gestureMode = VideoGestureMode.fromId(prefs[keyGestureMode]),
        )
    }

    /** @deprecated 旧版总开关；新逻辑用 [isGuideSeenForMode] */
    val guideSeenFlow: Flow<Boolean> = context.playerPrefsStore.data.map { prefs ->
        prefs[keyGuideSeen] ?: false
    }

    fun guideSeenForModeFlow(mode: VideoGestureMode): Flow<Boolean> =
        context.playerPrefsStore.data.map { prefs ->
            prefs[guideModeKey(mode)] ?: false
        }

    suspend fun setGuideSeen(seen: Boolean = true) {
        context.playerPrefsStore.edit { it[keyGuideSeen] = seen }
    }

    suspend fun setGuideSeenForMode(mode: VideoGestureMode, seen: Boolean = true) {
        context.playerPrefsStore.edit { it[guideModeKey(mode)] = seen }
    }

    suspend fun setQuality(q: AudioQuality) {
        context.playerPrefsStore.edit { it[keyQuality] = q.id }
    }

    suspend fun setSoundFx(fx: SoundFx) {
        context.playerPrefsStore.edit { it[keyFx] = fx.id }
    }

    suspend fun setAutoCache(enabled: Boolean) {
        context.playerPrefsStore.edit { it[keyAutoCache] = enabled }
    }

    suspend fun setVideoMode(enabled: Boolean) {
        context.playerPrefsStore.edit { it[keyVideoMode] = enabled }
    }

    suspend fun setGestureMode(mode: VideoGestureMode) {
        context.playerPrefsStore.edit { it[keyGestureMode] = mode.id }
    }
}
