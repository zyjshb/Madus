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
        fun fromId(id: String?) = entries.find { it.id == id } ?: High
    }
}

/**
 * 环境音效预设（系统 Equalizer 频段近似）。
 */
enum class SoundFx(val id: String, val label: String, val subtitle: String) {
    Studio("studio", "精听", "人声清楚 · 不轰"),
    Flat("flat", "原声", "不改音色"),
    Bass("bass", "低音增强", "鼓点更厚"),
    Vocal("vocal", "人声突出", "中频抬升"),
    Soft("soft", "柔和", "夜晚/耳机"),
    Night("night", "夜听", "压高音、护耳"),
    Live("live", "现场感", "稍亮、空间感"),
    ;

    companion object {
        fun fromId(id: String?) = entries.find { it.id == id } ?: Studio
    }
}

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

/**
 * 网络使用强度（用户可选 4 档）。
 * 只调预取 / 推荐续刷 / 边听写盘，**不关**点播、切歌、歌单等功能。
 */
enum class NetworkIntensity(
    val id: String,
    val label: String,
    val subtitle: String,
) {
    MINIMAL("minimal", "最省", "基本能播 · 少预取 · 打游戏优先"),
    BALANCED("balanced", "均衡", "日常听歌 · 预取下一首"),
    SMOOTH("smooth", "流畅", "多预取 · 切歌更跟手"),
    FULL("full", "充足", "尽量预取 · 推荐多补 · 不断播优先"),
    ;

    companion object {
        fun fromId(id: String?) = entries.find { it.id == id } ?: BALANCED
    }

    /** 前台预解析后续几首 */
    val prefetchForeground: Int
        get() = when (this) {
            MINIMAL -> 0
            BALANCED -> 1
            SMOOTH -> 2
            FULL -> 3
        }

    /** 后台预解析后续几首 */
    val prefetchBackground: Int
        get() = when (this) {
            MINIMAL -> 0
            BALANCED -> 1
            SMOOTH -> 1
            FULL -> 2
        }

    /** 进后台后延迟多久再预取 */
    val backgroundPrefetchDelayMs: Long
        get() = when (this) {
            MINIMAL -> 3_000L
            BALANCED -> 1_500L
            SMOOTH -> 800L
            FULL -> 200L
        }

    /**
     * 后台推荐流：剩余 ≤ 此值才续刷。
     * -1 = 后台不主动续（见底再播时再取）。
     */
    val backgroundFeedRemainThreshold: Int
        get() = when (this) {
            MINIMAL -> -1
            BALANCED -> 1
            SMOOTH -> 2
            FULL -> 4
        }

    val thriftFeedMinAdd: Int
        get() = when (this) {
            MINIMAL -> 2
            BALANCED -> 3
            SMOOTH -> 6
            FULL -> 12
        }

    /** 是否允许边听写盘（仍受 autoCache 总开关约束） */
    fun allowAutoCacheWrite(appInBackground: Boolean): Boolean = when (this) {
        MINIMAL -> false
        BALANCED -> !appInBackground
        SMOOTH, FULL -> true
    }

    /** 推荐续刷是否走省网路径 */
    fun thriftFeed(appInBackground: Boolean): Boolean = when (this) {
        MINIMAL -> true
        BALANCED, SMOOTH -> appInBackground
        FULL -> false
    }
}

data class PlayerSettings(
    val quality: AudioQuality = AudioQuality.High,
    val soundFx: SoundFx = SoundFx.Studio,
    /** 边听边写磁盘缓存；关=纯在线，更轻量 */
    val autoCache: Boolean = false,
    /**
     * 视频模式：开=可看 B 站视频画面；关=纯音频听歌。
     * 内核仍是 Bilibili 取流。
     */
    val videoMode: Boolean = false,
    /** 短视频手势：抖音 / B站 / 快手 */
    val gestureMode: VideoGestureMode = VideoGestureMode.DOUYIN,
    /**
     * 打游戏时继续播放：忽略游戏音效等「短暂」音频焦点抢占，避免一点按钮就暂停。
     * 其它音乐 App 长期占用时仍可能被系统打断。
     */
    val gameMixAudio: Boolean = true,
    /**
     * 旧「游戏轻量」：与 [networkIntensity] == MINIMAL 同步。
     */
    val gameLiteMode: Boolean = false,
    /** 网络使用强度（预取/续刷/写盘） */
    val networkIntensity: NetworkIntensity = NetworkIntensity.BALANCED,
)

class PlayerPrefs(private val context: Context) {
    private val keyQuality = stringPreferencesKey("audio_quality")
    private val keyFx = stringPreferencesKey("sound_fx")
    private val keyListenDefaultsV1 = booleanPreferencesKey("listen_defaults_v1")
    private val keyAutoCache = booleanPreferencesKey("auto_cache")
    private val keyVideoMode = booleanPreferencesKey("video_mode")
    private val keyGuideSeen = booleanPreferencesKey("short_video_guide_seen_v1")
    private val keyGestureMode = stringPreferencesKey("video_gesture_mode")
    private val keyGameMixAudio = booleanPreferencesKey("game_mix_audio")
    private val keyGameLiteMode = booleanPreferencesKey("game_lite_mode")
    private val keyNetworkIntensity = stringPreferencesKey("network_intensity")
    /** 各操作模式是否看过专属指引（按 mode.id 存） */
    private fun guideModeKey(mode: VideoGestureMode) =
        booleanPreferencesKey("short_video_guide_${mode.id}_v2")

    val flow: Flow<PlayerSettings> = context.playerPrefsStore.data.map { prefs ->
        val gameLite = prefs[keyGameLiteMode] ?: false
        val hasNetKey = prefs.contains(keyNetworkIntensity)
        val storedNet = NetworkIntensity.fromId(prefs[keyNetworkIntensity])
        // 旧用户只开了游戏轻量、从未选过网络档 → 按最省
        val net = if (gameLite && !hasNetKey) NetworkIntensity.MINIMAL else storedNet
        PlayerSettings(
            quality = AudioQuality.fromId(prefs[keyQuality]),
            soundFx = SoundFx.fromId(prefs[keyFx]),
            autoCache = prefs[keyAutoCache] ?: false,
            videoMode = prefs[keyVideoMode] ?: false,
            gestureMode = VideoGestureMode.fromId(prefs[keyGestureMode]),
            gameMixAudio = prefs[keyGameMixAudio] ?: true,
            gameLiteMode = gameLite || net == NetworkIntensity.MINIMAL,
            networkIntensity = net,
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

    /**
     * 一次性：旧默认「标准 + 原声」升到「较高 + 精听」。
     * 用户已手选省流/最高/低音等则不动。
     */
    suspend fun migrateListenDefaults() {
        context.playerPrefsStore.edit { prefs ->
            if (prefs[keyListenDefaultsV1] == true) return@edit
            val q = prefs[keyQuality]
            if (q.isNullOrBlank() || q == AudioQuality.Standard.id) {
                prefs[keyQuality] = AudioQuality.High.id
            }
            val fx = prefs[keyFx]
            if (fx.isNullOrBlank() || fx == SoundFx.Flat.id) {
                prefs[keyFx] = SoundFx.Studio.id
            }
            prefs[keyListenDefaultsV1] = true
        }
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

    suspend fun setGameMixAudio(enabled: Boolean) {
        context.playerPrefsStore.edit { it[keyGameMixAudio] = enabled }
    }

    suspend fun setGameLiteMode(enabled: Boolean) {
        context.playerPrefsStore.edit {
            it[keyGameLiteMode] = enabled
            if (enabled) it[keyNetworkIntensity] = NetworkIntensity.MINIMAL.id
        }
    }

    suspend fun setNetworkIntensity(level: NetworkIntensity) {
        context.playerPrefsStore.edit {
            it[keyNetworkIntensity] = level.id
            it[keyGameLiteMode] = level == NetworkIntensity.MINIMAL
        }
    }

    suspend fun setVideoMode(enabled: Boolean) {
        context.playerPrefsStore.edit { it[keyVideoMode] = enabled }
    }

    suspend fun setGestureMode(mode: VideoGestureMode) {
        context.playerPrefsStore.edit { it[keyGestureMode] = mode.id }
    }
}
