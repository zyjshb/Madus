package com.madus.mobile.domain

/**
 * 推荐只要「歌」：非歌视频（教程/简介/鬼畜/杂谈）一律剔除。
 * 默认门槛较高——宁可少，不要脏。
 */
object TrackFilters {

    private val hardReject = listOf(
        "教程", "教学", "简介", "介绍", "讲解", "入门", "实操", "攻略", "技巧",
        "怎么", "如何", "怎样", "学会", "速成", "公开课", "网课", "课程", "课",
        "u盘", "优盘", "硬盘", "装机", "刷机", "安装", "下载方法",
        "开发", "编程", "源码", "unity", "unreal", "ue5", "godot", "python",
        "游戏制作", "开发游戏", "制作游戏", "游戏", "通关", "攻略站",
        "鬼畜", "整活", "沙雕", "反应", "reaction", "测评", "评测", "开箱",
        "vlog", "纪录片", "电影", "电视剧", "番剧", "直播回放", "直播",
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
        "live", "现场", "official", "官方音频", "官方", "完整版", "full ver",
        "audio", "高音质", "flac", "无损", "伴奏", "karaoke",
        "op", "ed", "主题曲", "片头曲", "片尾曲", "插曲", "推广曲",
        "music", "song", "sing", "合唱", "和声",
    )

    /** 子区 album 白名单 */
    private val songAlbums = setOf(
        "原创音乐", "翻唱", "VOCALOID", "演奏", "MV", "音乐热榜", "音乐区", "音乐搜索",
    )

    fun isLikelyMusic(
        track: Track,
        minMs: Long = 70_000L,
        maxMs: Long = 7 * 60_000L,
    ): Boolean {
        val title = track.title.trim()
        if (title.isBlank() || title.length > 60) return false

        val lower = title.lowercase()
        if (hardReject.any { lower.contains(it.lowercase()) }) return false
        if (title.endsWith("简介") || title.endsWith("介绍") || title.endsWith("教程")) return false
        if (Regex("""简介|介绍|教程|教学|攻略|讲解|入门""").containsMatchIn(title)) return false

        val d = track.durationMs
        // 无时长：必须有强音乐信号
        if (d <= 0) return hasMusicSignal(title)
        if (d < minMs || d > maxMs) return false

        // 必须：音乐信号 或 来自明确歌曲子区
        val fromSongZone = songAlbums.any { track.album.contains(it) }
        if (!hasMusicSignal(title) && !fromSongZone) return false

        // 仍像「说明文」的标题
        if (Regex("""[？?！!]{2,}|点击|关注|三连|弹幕""").containsMatchIn(title)) return false

        return true
    }

    fun hasMusicSignal(title: String): Boolean {
        val lower = title.lowercase()
        return musicSignal.any { lower.contains(it.lowercase()) }
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
