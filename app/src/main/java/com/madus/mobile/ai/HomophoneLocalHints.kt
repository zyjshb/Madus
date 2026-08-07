package com.madus.mobile.ai

/**
 * 本地猜歌启发（不联网）：
 * 1) 中文流行/广告歌（用户打中文歌词时优先）
 * 2) 英文歌谐音
 *
 * 纯中文线索时，中文规则优先，避免「我爱你你爱我」被模型错认成 Barney。
 */
object HomophoneLocalHints {

    private data class Rule(
        val keys: List<String>,
        val needAny: List<String> = emptyList(),
        val title: String,
        val artist: String?,
        val query: String,
        val note: String,
        val confidence: Float = 0.7f,
        /** 中文歌 / 广告曲：纯中文输入时排前面 */
        val chineseFirst: Boolean = false,
    )

    private val rules = listOf(
        // —— 中文 / 广告 ——
        Rule(
            keys = listOf("我爱你你爱我", "你爱我我爱你", "蜜雪冰城", "甜蜜蜜"),
            needAny = emptyList(),
            title = "蜜雪冰城甜蜜蜜",
            artist = "蜜雪冰城",
            query = "蜜雪冰城",
            note = "本地：我爱你你爱我 → 蜜雪冰城主题曲",
            confidence = 0.9f,
            chineseFirst = true,
        ),
        Rule(
            keys = listOf("大海航行靠舵手", "大海航线靠舵手", "万物生长靠太阳"),
            title = "大海航行靠舵手",
            artist = null,
            query = "大海航行靠舵手",
            note = "本地：经典红歌",
            confidence = 0.85f,
            chineseFirst = true,
        ),
        // —— 英文谐音 ——
        Rule(
            keys = listOf("贾似", "贾斯塞", "贾斯地", "justsayyes", "just say yes", "塞耶斯", "似地喂"),
            needAny = listOf("baby", "贝比", "欧", "哦", "love", "story", "斯多", "拉夫"),
            title = "Love Story",
            artist = "Taylor Swift",
            query = "Love Story Taylor Swift",
            note = "本地谐音：baby just say yes → Love Story",
            confidence = 0.75f,
        ),
        Rule(
            keys = listOf("love story", "拉夫斯多瑞", "拉芙丝拓瑞"),
            title = "Love Story",
            artist = "Taylor Swift",
            query = "Love Story Taylor Swift",
            note = "本地匹配 Love Story",
            confidence = 0.7f,
        ),
        Rule(
            keys = listOf("shapeofyou", "shape of you", "西破否油", "谢普奥夫尤"),
            title = "Shape of You",
            artist = "Ed Sheeran",
            query = "Shape of You Ed Sheeran",
            note = "本地谐音 Shape of You",
        ),
        Rule(
            keys = listOf("someone like you", "三门莱克油", "萨姆万莱克"),
            title = "Someone Like You",
            artist = "Adele",
            query = "Someone Like You Adele",
            note = "本地谐音 Someone Like You",
        ),
        Rule(
            keys = listOf("call me maybe", "callmemaybe", "扣我没逼", "考耳米妹比"),
            title = "Call Me Maybe",
            artist = "Carly Rae Jepsen",
            query = "Call Me Maybe",
            note = "本地谐音 Call Me Maybe",
        ),
        Rule(
            keys = listOf("let it go", "letitgo", "类特一够"),
            title = "Let It Go",
            artist = "Idina Menzel",
            query = "Let It Go 冰雪奇缘",
            note = "本地谐音 Let It Go",
        ),
    )

    fun suggest(userText: String): List<SongCandidate> {
        val norm = normalize(userText)
        if (norm.isBlank()) return emptyList()
        val pureCn = isPureChineseClue(userText)
        val out = ArrayList<SongCandidate>()

        for (r in rules) {
            // 纯中文输入：先别用「只靠爱字」的英文谐音规则乱撞
            if (pureCn && !r.chineseFirst) {
                // 仍允许：用户写了明显英文/谐音碎片
                val hasLatin = userText.any { it.code < 128 && it.isLetter() }
                if (!hasLatin) continue
            }
            val hitKey = r.keys.any { norm.contains(normalize(it)) }
            if (!hitKey) continue
            if (r.needAny.isNotEmpty()) {
                val hitNeed = r.needAny.any { norm.contains(normalize(it)) }
                val strongKey = r.keys.any {
                    val k = normalize(it)
                    k.length >= 4 && norm.contains(k)
                }
                if (!hitNeed && !strongKey) continue
            }
            // 蜜雪：只要「我爱你你爱我」就够，不必同时蜜雪
            if (r.chineseFirst && r.title.contains("蜜雪")) {
                val loveLine = norm.contains("我爱你你爱我") || norm.contains("你爱我我爱你") ||
                    norm.contains("蜜雪")
                if (!loveLine) continue
            }
            out.add(
                SongCandidate(
                    title = r.title,
                    artist = r.artist,
                    confidence = r.confidence,
                    bilibiliQuery = r.query,
                    note = r.note,
                ),
            )
        }

        // 纯中文：不要从输入里再抠英文歌名
        if (!pureCn) {
            val en = Regex("""[A-Za-z][A-Za-z0-9' ]{2,40}""").findAll(userText)
                .map { it.value.trim() }
                .filter { it.length >= 4 && (it.contains(' ') || it.length >= 6) }
                .take(3)
            for (t in en) {
                if (out.any { it.title.equals(t, true) }) continue
                if (SongGuessParser.isGarbageTitle(t)) continue
                out.add(
                    SongCandidate(
                        title = t,
                        bilibiliQuery = t,
                        confidence = 0.4f,
                        note = "从输入里抓到的英文",
                    ),
                )
            }
        }

        // 纯中文：中文规则排前
        return if (pureCn) {
            out.sortedByDescending { it.confidence ?: 0f }.distinctBy { it.title.lowercase() }.take(5)
        } else {
            out.distinctBy { it.title.lowercase() }.take(5)
        }
    }

    fun isPureChineseClue(s: String): Boolean {
        val t = s.trim()
        if (t.length < 2) return false
        val cjk = t.count { it.code in 0x4E00..0x9FFF }
        val latin = t.count { it.isLetter() && it.code < 128 }
        return cjk >= 4 && cjk >= latin * 2
    }

    private fun normalize(s: String): String =
        s.lowercase()
            .replace(Regex("""\s+"""), "")
            .replace("，", "")
            .replace(",", "")
            .replace("。", "")
            .replace(".", "")
            .replace("！", "")
            .replace("!", "")
}
