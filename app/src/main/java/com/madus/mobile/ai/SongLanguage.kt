package com.madus.mobile.ai

/**
 * 线索/歌名语言判断：决定 AI 搜歌是否强制中文、标题长度上限、全网检索模板。
 * 纯中文路径保持原有「防英文幻觉」；其它语言走全球歌名路径。
 */
enum class SongLangKind {
    /** 以汉字为主（华语歌） */
    CHINESE,
    /** 拉丁字母为主（英/西/法等） */
    LATIN,
    /** 日文（假名±汉字） */
    JAPANESE,
    /** 韩文 */
    KOREAN,
    /** 多语混杂或看不清 */
    MIXED,
    /** 空/极短 */
    UNKNOWN,
}

object SongLanguage {

    fun isForeign(kind: SongLangKind): Boolean = when (kind) {
        SongLangKind.LATIN, SongLangKind.JAPANESE, SongLangKind.KOREAN -> true
        else -> false
    }

    fun hasJapaneseKana(s: String): Boolean =
        s.any { it.code in 0x3040..0x30FF || it.code in 0x31F0..0x31FF }

    fun hasHangul(s: String): Boolean =
        s.any { it.code in 0xAC00..0xD7AF || it.code in 0x1100..0x11FF }

    fun hasForeignScript(s: String): Boolean = hasJapaneseKana(s) || hasHangul(s)

    /**
     * 根据转写歌词/候选歌名判断这一轮实际听到的语言。
     * 只有明显多数派才返回，避免中英各一条时乱开外语模式。
     */
    fun detectLanguage(
        lyrics: String,
        candidates: List<SongCandidate>,
    ): SongLangKind? {
        val l = lyrics.trim()
        if (l.isNotEmpty()) {
            val k = kindOf(l)
            if (k != SongLangKind.UNKNOWN) return k
        }
        val counts = linkedMapOf<SongLangKind, Int>()
        for (c in candidates) {
            val t = c.title.trim()
            if (t.isEmpty()) continue
            val k = kindOf(t)
            if (k != SongLangKind.UNKNOWN && k != SongLangKind.MIXED) {
                counts[k] = (counts[k] ?: 0) + 1
            }
        }
        if (counts.isEmpty()) return null
        val best = counts.maxByOrNull { it.value } ?: return null
        return best.key.takeIf { best.value * 2 > counts.values.sum() }
    }

    fun kindOf(text: String): SongLangKind {
        val t = text.trim()
        if (t.length < 2) return SongLangKind.UNKNOWN
        val cjk = t.count { it.code in 0x4E00..0x9FFF }
        val hiraKata = t.count {
            it.code in 0x3040..0x30FF || it.code in 0x31F0..0x31FF
        }
        val hangul = t.count { it.code in 0xAC00..0xD7AF || it.code in 0x1100..0x11FF }
        val latin = t.count { it.isLetter() && it.code < 128 }
        val letters = (cjk + hiraKata + hangul + latin).coerceAtLeast(1)

        if (hangul >= 2 && hangul * 2 >= letters) return SongLangKind.KOREAN
        if (hiraKata >= 2 && hiraKata + cjk >= letters / 2) return SongLangKind.JAPANESE
        // 纯/近纯中文：汉字多且假名韩文很少
        if (cjk >= 4 && cjk >= latin * 2 && hiraKata == 0 && hangul == 0) {
            return SongLangKind.CHINESE
        }
        if (latin >= 3 && latin >= cjk * 2 && hiraKata == 0 && hangul == 0) {
            return SongLangKind.LATIN
        }
        if (cjk >= 2 && latin >= 3) return SongLangKind.MIXED
        if (cjk >= 2) return SongLangKind.CHINESE
        if (latin >= 2) return SongLangKind.LATIN
        return SongLangKind.MIXED
    }

    /** 是否走「华语优先、压英文碎片」的旧路径 */
    fun preferChinesePipeline(clue: String, isAudio: Boolean = false): Boolean {
        if (isAudio) {
            // 哼唱默认仍偏华语（历史幻觉问题），除非已写出明显外语线索
            val k = kindOf(clue)
            return k == SongLangKind.CHINESE || k == SongLangKind.UNKNOWN || clue.isBlank()
        }
        return kindOf(clue) == SongLangKind.CHINESE
    }

    /** 歌名合理长度上限（按脚本） */
    fun maxTitleLen(title: String): Int {
        val k = kindOf(title)
        return when (k) {
            SongLangKind.LATIN -> 48
            SongLangKind.JAPANESE, SongLangKind.KOREAN -> 36
            SongLangKind.MIXED -> 40
            SongLangKind.CHINESE, SongLangKind.UNKNOWN -> 20
        }
    }

    fun isPlausibleTitleLength(title: String): Boolean {
        val t = title.trim()
        if (t.length < 2) return false
        return t.length <= maxTitleLen(t)
    }

    /** 全网检索用查询模板 */
    fun webQueryVariants(seed: String, artist: String?): List<String> {
        val t = seed.trim()
        if (t.isEmpty()) return emptyList()
        val art = artist?.trim().orEmpty()
        val base = if (art.isNotEmpty()) "$t $art" else t
        val k = kindOf(t + " " + art)
        val out = LinkedHashSet<String>()
        out.add(base)
        out.add(t)
        when (k) {
            SongLangKind.CHINESE -> {
                out.add("$t 歌曲")
                out.add("$t 歌词")
                out.add("$base 歌曲")
                if (t.length <= 10) out.add("$t 是什么歌")
            }
            SongLangKind.LATIN -> {
                out.add("$t song")
                out.add("$t lyrics")
                out.add("$base song")
                out.add("\"$t\" music")
            }
            SongLangKind.JAPANESE -> {
                out.add("$t 曲")
                out.add("$t 歌詞")
                out.add("$t song")
                out.add(base)
            }
            SongLangKind.KOREAN -> {
                out.add("$t 노래")
                out.add("$t lyrics")
                out.add("$t song")
                out.add(base)
            }
            SongLangKind.MIXED, SongLangKind.UNKNOWN -> {
                out.add("$t 歌曲")
                out.add("$t song")
                out.add("$t lyrics")
                out.add(base)
            }
        }
        return out.toList()
    }
}
