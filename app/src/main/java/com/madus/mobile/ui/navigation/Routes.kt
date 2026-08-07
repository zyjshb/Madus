package com.madus.mobile.ui.navigation

enum class RootTab(val route: String, val label: String) {
    Home("home", "首页"),
    Search("search", "搜索"),
    Recommend("recommend", "推荐"),
    Library("library", "曲库"),
    Me("me", "我的");

    companion object {
        val routes: Set<String> = entries.map { it.route }.toSet()
    }
}

object Routes {
    const val NOW_PLAYING = "now_playing"
    const val PLAYLIST = "playlist"
    const val QUEUE = "queue"
    /** B站收藏夹列表（不是某个默认夹） */
    const val BILI_FAVS = "bili_favs"
    const val SETTINGS = "settings"
    /** 音效 / 边听缓存开关等 */
    const val PLAYBACK_PREFS = "playback_prefs"
    /** 缓存列表管理 */
    const val CACHE_MANAGER = "cache_manager"
    /** 视频模式全屏 */
    const val FULLSCREEN_VIDEO = "fullscreen_video"
    /** 检查更新页（展示版本，用户选择是否升级） */
    const val UPDATE = "update"
    /** 更新日志 */
    const val CHANGELOG = "changelog"
    /** 关于 Madus 连点 10 次彩蛋（小恐龙） */
    const val ABOUT_EASTER_EGG = "about_easter_egg"
    /** AI 搜歌对话 */
    const val AI_CHAT = "ai_chat"
    /** AI 模型 / API Key 配置 */
    const val AI_CONFIG = "ai_config"
    /** UP 主页 */
    const val UP_SPACE = "up_space"
}
