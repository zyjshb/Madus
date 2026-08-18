package com.madus.mobile.domain

object ContentProfileParser {
    private val TID_TOPICS = mapOf(
        1 to "anime", 2 to "anime", 24 to "anime", 25 to "anime", 47 to "anime",
        3 to "music", 28 to "music", 29 to "music", 30 to "music", 31 to "music",
        59 to "music", 130 to "music", 193 to "music",
        4 to "gaming", 17 to "gaming", 19 to "gaming", 65 to "gaming", 171 to "gaming",
        20 to "dance", 154 to "dance", 156 to "dance", 198 to "dance",
        36 to "knowledge", 37 to "knowledge", 124 to "knowledge", 207 to "knowledge",
        208 to "knowledge", 209 to "knowledge",
        160 to "life", 138 to "life", 188 to "life", 189 to "life", 190 to "life",
        191 to "life", 192 to "life", 211 to "life",
        181 to "movie", 182 to "movie", 202 to "movie",
        95 to "digital",
        5 to "comedy", 71 to "comedy", 119 to "comedy", 129 to "comedy",
    )

    private val KEYWORD_TOPICS = listOf(
        "music" to listOf("音乐", "翻唱", "mv", "bgm", "vocaloid", "演唱", "歌曲", "live", "演奏", "cover"),
        "anime" to listOf("动漫", "番剧", "动画", "二次元", "mad", "amv", "鬼畜"),
        "gaming" to listOf("游戏", "原神", "崩坏", "王者", "我的世界", "steam", "电竞", "实况", "攻略"),
        "dance" to listOf("舞蹈", "舞见", "宅舞"),
        "knowledge" to listOf("教程", "科普", "讲解", "课程", "学习", "测评", "开箱", "知识"),
        "life" to listOf("vlog", "日常", "美食", "旅行", "生活", "记录"),
        "food" to listOf("美食", "做饭", "探店", "食谱"),
        "movie" to listOf("电影", "影视", "解说", "预告"),
        "sport" to listOf("运动", "健身", "篮球", "足球", "比赛"),
        "digital" to listOf("数码", "手机", "电脑", "科技", "评测"),
        "comedy" to listOf("搞笑", "喜剧", "段子", "整活"),
        "news" to listOf("新闻", "资讯", "热点"),
        // 细类：一首不喜欢就可以挡，不会封掉整个音乐区
        "cover" to listOf("翻唱", "cover"),
        "vocaloid" to listOf("vocaloid", "vsinger", "洛天依", "初音", "镜音"),
        "live" to listOf("live", "现场", "演唱会"),
        "instrumental" to listOf("纯音乐", "钢琴", "钢琴曲", "轻音乐", "演奏"),
        "sleep" to listOf("助眠", "白噪音", "睡眠", "解压"),
        "anime-song" to listOf("片头", "片尾", "动漫歌", "二次元"),
        "gufeng" to listOf("古风", "国风"),
        "rap" to listOf("说唱", "嘻哈"),
        "dj" to listOf("电音", "remix"),
        "jp-song" to listOf("日语", "日文", "jpop"),
        "en-song" to listOf("英语", "英文", "欧美"),
        "kpop" to listOf("韩语", "韩文", "kpop"),
        "guichu" to listOf("鬼畜"),
    )

    private val TITLE_STOP = setOf(
        "官方", "高清", "完整", "高音质", "中文", "英文", "日文", "歌曲", "视频",
        "字幕", "动态", "歌词", "官方版", "正式版", "超清", "现场", "完整版",
        "official", "lyric", "lyrics", "audio", "video", "full",
    )

    fun profileFromTrack(track: Track, fetchedAtMs: Long = System.currentTimeMillis()): ContentProfile {
        val text = "${track.title} ${track.album} ${track.categoryName}"
        return ContentProfile(
            trackId = track.id,
            bvid = track.bvid,
            authorId = track.ownerMid.takeIf { it.isNotBlank() },
            authorName = track.artist.takeIf { it.isNotBlank() },
            categoryId = track.categoryId.takeIf { it > 0 },
            categoryName = track.categoryName.takeIf { it.isNotBlank() },
            tags = track.tags.toSet(),
            topicKeys = parseTopicKeys(
                categoryId = track.categoryId.takeIf { it > 0 },
                categoryName = track.categoryName,
                tags = track.tags,
                text = text,
            ),
            fetchedAtMs = fetchedAtMs,
        )
    }

    fun parseTopicKeys(
        categoryId: Int?,
        categoryName: String?,
        tags: Collection<String>,
        text: String,
    ): Set<String> {
        val topics = linkedSetOf<String>()
        categoryId?.let { TID_TOPICS[it]?.let { topics.add(it) } }
        val name = categoryName.orEmpty().lowercase()
        if (name.contains("音乐") || name.contains("翻唱")) topics.add("music")
        if (name.contains("动画") || name.contains("番剧")) topics.add("anime")
        if (name.contains("游戏")) topics.add("gaming")
        if (name.contains("舞蹈")) topics.add("dance")
        if (name.contains("知识") || name.contains("科技")) topics.add("knowledge")
        if (name.contains("生活") || name.contains("美食")) topics.add("life")
        if (name.contains("电影")) topics.add("movie")
        if (name.contains("娱乐") || name.contains("搞笑")) topics.add("comedy")

        val lower = text.lowercase()
        for ((topic, words) in KEYWORD_TOPICS) {
            if (words.any { lower.contains(it) }) topics.add(topic)
        }
        for (tag in tags) {
            val t = tag.lowercase()
            if (t.contains("音乐") || t.contains("翻唱") || t.contains("vocaloid") ||
                t.contains("bgm") || t == "歌" || t.contains("歌曲")
            ) {
                topics.add("music")
            }
            if (t.contains("动漫") || t.contains("动画") || t.contains("番剧")) topics.add("anime")
            if (t.contains("游戏")) topics.add("gaming")
            if (t.contains("舞蹈") || t.contains("舞见") || t.contains("宅舞")) topics.add("dance")
            if (t.contains("知识") || t.contains("科普") || t.contains("教程")) topics.add("knowledge")
            if (t.contains("美食") || t.contains("vlog") || t.contains("日常")) topics.add("life")
            if (t.contains("电影") || t.contains("影视")) topics.add("movie")
            if (t.contains("数码") || t.contains("科技")) topics.add("digital")
            if (t.contains("搞笑") || t.contains("整活") || t.contains("喜剧")) topics.add("comedy")
            if (t.contains("新闻") || t.contains("资讯") || t.contains("热点")) topics.add("news")
        }
        return topics.ifEmpty { setOf("unknown") }
    }

    fun titleKey(title: String): String =
        title.lowercase()
            .replace(Regex("【.*?】|\\[.*?]|\\(.*?\\)|（.*?）"), " ")
            .replace(Regex("[\\s|｜/\\\\·•~～!！?？.。,，、:：;；\"'“”‘’]+"), " ")
            .trim()

    fun titleTokens(title: String): Set<String> {
        val key = titleKey(title)
        if (key.isBlank()) return emptySet()
        val out = linkedSetOf<String>()
        Regex("[\\u4e00-\\u9fff]{2,8}").findAll(key).forEach { m ->
            val w = m.value
            if (w !in TITLE_STOP) out.add("kw:$w")
        }
        Regex("[a-z0-9]{3,16}").findAll(key).forEach { m ->
            val w = m.value
            if (w !in TITLE_STOP) out.add("kw:$w")
        }
        return out
    }

    fun titlesOverlap(blockedKey: String, title: String): Boolean {
        val a = blockedKey.ifBlank { return false }
        val b = titleKey(title)
        if (b.isBlank()) return false
        if (a == b) return true
        if (a.length >= 4 && b.contains(a)) return true
        if (b.length >= 4 && a.contains(b)) return true
        return false
    }
}
