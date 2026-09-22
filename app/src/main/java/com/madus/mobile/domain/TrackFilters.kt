package com.madus.mobile.domain

/**
 * 推荐只要「歌」：非歌视频（教程/简介/鬼畜/杂谈）一律剔除。
 * 默认门槛较高——宁可少，不要脏。
 */
object TrackFilters {

    private val hardReject = listOf(
        "教程", "教学", "简介", "介绍", "讲解", "入门", "实操", "攻略", "技巧",
        "速成", "公开课", "网课", "课程",
        "u盘", "优盘", "硬盘", "装机", "刷机", "安装", "下载方法",
        "开发", "编程", "源码", "unity", "unreal", "ue5", "godot", "python",
        "游戏制作", "开发游戏", "制作游戏", "游戏实况", "通关", "攻略站",
        "鬼畜", "整活", "沙雕", "反应", "reaction", "测评", "评测", "开箱",
        "vlog", "纪录片", "电影解说", "影视解说", "直播回放", "直播切片",
        "合集", "专题", "全集", "连载", "第1集", "第01集", "第一季", "第二季",
        "盘点", "排行", "对比", "解析", "解读", "分析", "科普", "导读",
        "混剪合集", "万粉", "涨粉", "运营", "剪辑教程", "后期",
        "defeat", "victory", "defeat.mp3", // 常是简介/素材
        "音效素材", "素材", "音效包", "效果器", "插件",
        "杂谈", "闲聊", "访谈", "采访", "幕后", "花絮",
        "舞蹈", "翻跳", "手势舞", // 偏舞区
        "美食", "探店", "汽车", "数码",
    )

    private val musicSignal = listOf(
        "歌", "曲", "mv", "cover", "翻唱", "bgm", "纯音乐", "原曲", "单曲",
        "vocal", "钢琴", "吉他", "小提琴", "演奏", "remix", "ost",
        "live", "现场", "official audio", "官方音频", "full ver",
        "audio", "高音质", "flac", "无损", "伴奏", "karaoke",
        "主题曲", "片头曲", "片尾曲", "插曲", "推广曲",
        "music", "song", "sing", "合唱", "和声",
    )

    /** 子区 album 白名单 */
    private val songAlbums = setOf(
        "原创音乐", "翻唱", "VOCALOID", "演奏", "MV", "音乐现场",
    )
    private val songCategories = setOf(28, 29, 30, 31, 59, 193)
    private val musicCategories = songCategories + setOf(3, 130)
    private val latinSignals = musicSignal.filter { it.all { c -> c.code < 128 } }
        .map { Regex("(?<![a-z])${Regex.escape(it)}(?![a-z])") }
    private val otherSignals = musicSignal.filterNot { it.all { c -> c.code < 128 } }

    fun isLikelyMusic(
        track: Track,
        minMs: Long = 70_000L,
        maxMs: Long = 7 * 60_000L,
    ): Boolean {
        val title = track.title.trim()
        if (title.isBlank() || title.length > 160) return false

        val lower = title.lowercase()
        if (hardReject.any { lower.contains(it.lowercase()) }) return false
        if (title.endsWith("简介") || title.endsWith("介绍") || title.endsWith("教程")) return false
        if (Regex("""简介|介绍|教程|教学|攻略|讲解|入门""").containsMatchIn(title)) return false

        // 分区是真实元数据，不能让带 BGM 的游戏/生活视频冒充歌曲。
        if (track.categoryId > 0 && track.categoryId !in musicCategories) return false

        val d = track.durationMs
        // 时长未知的条目不自动推荐，避免短片/长合集混入。
        if (d <= 0) return false
        if (d < minMs || d > maxMs) return false

        // 必须：音乐信号 或 来自明确歌曲子区
        val fromSongZone = track.categoryId in songCategories ||
            songAlbums.any { track.album.equals(it, ignoreCase = true) || track.categoryName.equals(it, ignoreCase = true) }
        if (!hasMusicSignal(title) && !fromSongZone && track.tags.none { hasMusicSignal(it) }) return false

        // 仍像「说明文」的标题
        if (Regex("""[？?！!]{2,}|点击|关注|三连|弹幕""").containsMatchIn(title)) return false

        return true
    }

    fun hasMusicSignal(title: String): Boolean {
        val lower = title.lowercase()
        return otherSignals.any { lower.contains(it) } || latinSignals.any { it.containsMatchIn(lower) }
    }

    fun musicOnly(tracks: List<Track>): List<Track> =
        tracks.filter { isLikelyMusic(it) }

    fun preferMusicish(tracks: List<Track>, minScore: Int = 12): List<Track> =
        tracks
            .map { it to score(it) }
            .filter { it.second >= minScore }
            .sortedByDescending { it.second }
            .map { it.first }

    fun score(t: Track): Int {
        if (!isLikelyMusic(t)) return 0
        var s = 6
        if (hasMusicSignal(t.title)) s += 10
        if (t.durationMs in 100_000L..300_000L) s += 6
        else if (t.durationMs in 70_000L..420_000L) s += 3
        if (songAlbums.any { t.album.contains(it) }) s += 5
        if (t.album.contains("原创") || t.album.contains("翻唱")) s += 3
        if (t.title.length in 2..24) s += 2
        if (t.coverUrl != null) s += 1
        return s
    }
}
