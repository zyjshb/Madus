package com.madus.mobile.ai

import org.json.JSONArray
import org.json.JSONObject

data class SongGuessResult(
    val reply: String,
    val candidates: List<SongCandidate>,
    val fromJson: Boolean = true,
    /** 模型转写的听到歌词（哼唱用） */
    val lyricsHeard: String = "",
)

object SongGuessParser {

    private val cnTitle = Regex("""[《「『]([^》」』]{1,40})[》」』]""")
    private val labeled = Regex(
        """(?:歌名|歌曲|曲名|标题|可能是|应该是|猜是)[是为:：\s]*[《「『"]?([^\n》」』"，。；]{2,40})""",
        RegexOption.IGNORE_CASE,
    )
    /** 更像歌名：Title Case 或短专名，不是整句英文说明 */
    private val songLikeEnglish =
        Regex("""\b([A-Z][a-z0-9'&]+(?:\s+[A-Z][a-z0-9'&]+){0,5})\b""")

    fun parse(raw: String, userClue: String = ""): SongGuessResult {
        val cleaned = stripFences(raw.trim())
        val pureCn = HomophoneLocalHints.isPureChineseClue(userClue)
        val clueCandidates = chineseClueCandidates(userClue)
        val local = HomophoneLocalHints.suggest(userClue)
        // 错字/记混 → 华语金曲（如「录过了江南」≈ 青花瓷）
        val famous = ChineseFamousLyrics.suggest(userClue)
        val lyricsFallback = extractLyricsHeard(cleaned)

        // 1) 合法 JSON 候选
        val jsonText = extractJsonObject(cleaned)?.let { softenJson(it) }
        if (jsonText != null) {
            val parsed = runCatching { parseJsonObject(jsonText) }.getOrNull()
            if (parsed != null) {
                val lyrics = parsed.lyricsHeard.ifBlank { lyricsFallback }
                // 用转写歌词再匹配金曲（哼唱关键路径）
                val fromLyrics = if (lyrics.length >= 4) {
                    ChineseFamousLyrics.suggest(lyrics) +
                        lyricClueCandidates(lyrics) +
                        chineseClueCandidates(lyrics)
                } else {
                    emptyList()
                }
                val fromJson = sanitizeCandidates(parsed.candidates)
                    .let { if (pureCn) keepChineseOriented(it) else it }
                // 纯中文：猜中的歌名优先，再歌词原句（避免只搜错句出有声书）
                val merged = if (pureCn) {
                    mergeCandidates(famous, fromLyrics, fromJson, local, clueCandidates)
                } else {
                    mergeCandidates(fromJson, fromLyrics, local, famous, clueCandidates)
                }
                if (merged.isNotEmpty()) {
                    return SongGuessResult(
                        reply = if (pureCn) {
                            pureChineseReply(parsed.reply, famous)
                        } else {
                            humanReply(parsed.reply, fromJson.isNotEmpty() || fromLyrics.isNotEmpty())
                        },
                        candidates = merged,
                        fromJson = fromJson.isNotEmpty(),
                        lyricsHeard = lyrics,
                    )
                }
                // JSON 有但候选被滤空：仍带回 lyrics，供上层 recover
                if (lyrics.isNotBlank()) {
                    return SongGuessResult(
                        reply = parsed.reply,
                        candidates = emptyList(),
                        fromJson = false,
                        lyricsHeard = lyrics,
                    )
                }
            }
        }

        // 2) 单独 candidates 数组
        extractCandidatesArray(cleaned)?.let { arr ->
            val list = sanitizeCandidates(parseCandidatesArray(arr))
                .let { if (pureCn) keepChineseOriented(it) else it }
            val fromLyrics = if (lyricsFallback.length >= 4) {
                ChineseFamousLyrics.suggest(lyricsFallback) +
                    lyricClueCandidates(lyricsFallback)
            } else {
                emptyList()
            }
            val merged = if (pureCn) {
                mergeCandidates(famous, fromLyrics, list, local, clueCandidates)
            } else {
                mergeCandidates(list, fromLyrics, local, famous, clueCandidates)
            }
            if (merged.isNotEmpty()) {
                return SongGuessResult(
                    reply = if (pureCn) {
                        pureChineseReply("", famous)
                    } else {
                        "找到一些可能的歌，下面可以直接试听。"
                    },
                    candidates = merged,
                    fromJson = list.isNotEmpty(),
                    lyricsHeard = lyricsFallback,
                )
            }
        }

        // 3) 本地金曲 + 原句；纯中文不抽模型英文碎片
        val careful = if (pureCn) emptyList() else carefulExtractTitles(cleaned)
        val fromLyrics = if (lyricsFallback.length >= 4) {
            ChineseFamousLyrics.suggest(lyricsFallback) +
                lyricClueCandidates(lyricsFallback)
        } else {
            emptyList()
        }
        val merged = if (pureCn) {
            mergeCandidates(famous, fromLyrics, local, clueCandidates, careful)
        } else {
            mergeCandidates(careful, fromLyrics, local, famous, clueCandidates)
        }
        return if (merged.isNotEmpty()) {
            SongGuessResult(
                reply = when {
                    pureCn && famous.isNotEmpty() ->
                        pureChineseReply("", famous)
                    pureCn -> "先按你打的中文歌词在 B 站搜了，点下面试听。"
                    local.isNotEmpty() && careful.isEmpty() ->
                        "根据谐音猜了几首，点下面试听。"
                    else -> "整理了几首可能的歌，点下面试听。"
                },
                candidates = merged,
                fromJson = false,
                lyricsHeard = lyricsFallback,
            )
        } else {
            SongGuessResult(
                reply = "没猜出来。你可以再发一句歌词，或换个说法。",
                candidates = emptyList(),
                fromJson = false,
                lyricsHeard = lyricsFallback,
            )
        }
    }

    private fun pureChineseReply(
        modelReply: String,
        famous: List<SongCandidate> = emptyList(),
    ): String {
        if (famous.isNotEmpty()) {
            val top = famous.first()
            val who = listOfNotNull(top.title, top.artist).joinToString(" · ")
            return "你这句歌词有点记混/错字，更像是「$who」。已按歌名去 B 站搜了，点下面试听。"
        }
        val r = modelReply.trim()
        if (r.isBlank() || isMetaOrInstructionText(r)) {
            return "按中文歌词在找，点下面试听看是不是。"
        }
        val low = r.lowercase()
        if (low.contains("barney") || (low.contains("i love you") && low.contains("儿歌"))) {
            return "按中文歌词在 B 站搜了，点下面试听。"
        }
        return r.take(400)
    }

    /**
     * 纯中文线索：只保留「中文向」候选。
     * - 歌名/搜索词含汉字 → 留
     * - 纯英文 → 丢（避免任意中文歌词被认成英文歌）
     */
    fun keepChineseOriented(list: List<SongCandidate>): List<SongCandidate> {
        return list.filter { c ->
            hasCjk(c.title) || hasCjk(c.bilibiliQuery) ||
                (c.artist != null && hasCjk(c.artist))
        }
    }

    fun hasCjk(s: String): Boolean =
        s.any { it.code in 0x4E00..0x9FFF }

    private fun humanReply(modelReply: String, hadJson: Boolean): String {
        val r = modelReply.trim()
        if (r.isBlank()) return "找到一些可能的歌，点下面试听。"
        // 模型把 system 英文复读出来时换成人话
        if (isMetaOrInstructionText(r)) return "找到一些可能的歌，点下面试听。"
        return r.take(400)
    }

    /**
     * 用户输入的中文：当正经歌词/半句歌直接可搜 B 站。
     * 普通用户只会打「大海航行靠舵手…」，不会写提示词。
     */
    fun chineseClueCandidates(userClue: String): List<SongCandidate> {
        val t = userClue.trim()
        if (t.length < 4) return emptyList()
        val cjk = t.count { it.code in 0x4E00..0x9FFF }
        if (cjk < 4) return emptyList()
        // 几乎全是中文才走「原句搜」
        if (cjk.toFloat() / t.length < 0.45f) return emptyList()

        val parts = t.split(Regex("""[，,。！？?、；;\n]+"""))
            .map { it.trim() }
            .filter { it.length in 4..24 }
        val whole = t.replace(Regex("""\s+"""), "")
            .take(40)
        val queries = LinkedHashSet<String>()
        // 整句、分句都可搜——适用于任意中文歌，不靠歌名表
        if (whole.length in 4..40) queries.add(whole)
        parts.forEach { queries.add(it) }
        if (parts.size >= 2) {
            queries.add(parts.take(2).joinToString(""))
        }
        // 知名广告句额外加品牌词（增强，不是唯一路径）
        if (whole.contains("我爱你你爱我") || whole.contains("你爱我我爱你") || t.contains("蜜雪")) {
            queries.add("蜜雪冰城")
            queries.add("蜜雪冰城甜蜜蜜")
        }
        return queries.take(6).map { q ->
            SongCandidate(
                title = q,
                bilibiliQuery = q,
                confidence = 0.5f,
                note = "按你打的歌词搜",
            )
        }
    }

    /**
     * 哼唱转写兜底：中/英/日/韩都可用整句或短片段去 B 站搜，
     * 不要求本地曲库覆盖这首歌。
     */
    fun lyricClueCandidates(text: String): List<SongCandidate> {
        val t = text.trim()
        if (t.length < 4) return emptyList()
        val queries = LinkedHashSet<String>()
        val whole = t.replace(Regex("""\s+"""), "").take(24)
        if (whole.length in 4..24) queries.add(whole)
        val parts = t.split(Regex("""[\s,，。！？?、；;\n]+"""))
            .map { it.trim() }
            .filter { it.length in 2..24 }
        parts.forEach { queries.add(it) }
        if (parts.size >= 2) {
            queries.add(parts.take(2).joinToString(" "))
            if (parts.size >= 3) queries.add(parts.take(3).joinToString(" "))
        }
        return queries.take(6).map { q ->
            SongCandidate(
                title = q,
                bilibiliQuery = q,
                confidence = 0.45f,
                note = "按转写歌词搜",
            )
        }
    }

    private fun mergeCandidates(vararg lists: List<SongCandidate>): List<SongCandidate> {
        val seen = LinkedHashSet<String>()
        val out = ArrayList<SongCandidate>()
        for (list in lists) {
            for (c in list) {
                if (isGarbageTitle(c.title) || isGarbageTitle(c.bilibiliQuery)) continue
                val key = c.title.lowercase().trim()
                if (key.isEmpty() || !seen.add(key)) continue
                out.add(c)
            }
        }
        return sanitizeCandidates(out).take(6)
    }

    private fun parseJsonObject(jsonText: String): SongGuessResult {
        val o = JSONObject(jsonText)
        val reply = o.optString("reply").ifBlank {
            o.optString("message").ifBlank { "" }
        }
        val lyrics = sequenceOf(
            "lyrics_heard", "lyrics", "transcription", "heard_lyrics", "lyric", "heard",
        ).map { o.optString(it).trim() }
            .firstOrNull { it.isNotEmpty() && !isPlaceholderValue(it) }
            .orEmpty()
        val arr = o.optJSONArray("candidates")
            ?: o.optJSONArray("songs")
            ?: o.optJSONArray("results")
            ?: JSONArray()
        return SongGuessResult(
            reply = reply,
            candidates = parseCandidatesArray(arr),
            fromJson = true,
            lyricsHeard = lyrics,
        )
    }

    /** 从模型全文抽「听到的歌词」字段或中文歌词句 */
    fun extractLyricsHeard(raw: String): String {
        if (raw.isBlank()) return ""
        extractJsonObject(raw)?.let { js ->
            runCatching {
                val o = JSONObject(softenJson(js))
                sequenceOf(
                    "lyrics_heard", "lyrics", "transcription", "heard_lyrics", "lyric", "heard",
                ).map { o.optString(it).trim() }
                    .firstOrNull { it.length >= 2 && !isPlaceholderValue(it) }
            }.getOrNull()?.let { return it }
        }
        // 标注：听到的歌词 / 歌词转写
        val labeled = Regex(
            """(?:lyrics_heard|听到的歌词|歌词转写|转写|听到)[是为:：\s]*["「]?([^\n"」]{2,80})""",
            RegexOption.IGNORE_CASE,
        ).find(raw)?.groupValues?.getOrNull(1)?.trim()
        if (!labeled.isNullOrBlank() && hasCjk(labeled)) return labeled
        // 连续中文歌词句（≥6 字）
        val runs = Regex("""[\u4e00-\u9fff，。、？！\s]{6,40}""")
            .findAll(raw)
            .map { it.value.replace(Regex("""\s+"""), "").trim() }
            .filter { it.count { ch -> ch.code in 0x4E00..0x9FFF } >= 6 }
            .toList()
        return runs.maxByOrNull { it.length }.orEmpty()
    }

    private fun parseCandidatesArray(arr: JSONArray): List<SongCandidate> {
        return buildList {
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                // 只读英文 key，避免把「歌名」字段名当值
                val titleRaw = sequenceOf("title", "name", "song")
                    .map { c.optString(it).trim() }
                    .firstOrNull { it.isNotEmpty() && !isPlaceholderValue(it) }
                    ?: continue
                // Possible·歌声与微笑 → 歌声与微笑；纯 Something → 丢
                val foreignClean = SongNameNormalizer.cleanForeignTitle(titleRaw)
                val title = foreignClean
                    ?: SongNameNormalizer.extractChineseTitle(titleRaw)
                    ?: titleRaw.takeIf {
                        !SongNameNormalizer.isJunkFragment(it) && !isGarbageTitle(it)
                    }
                    ?: continue
                if (isGarbageTitle(title) || isPlaceholderValue(title) ||
                    SongNameNormalizer.isJunkFragment(title)
                ) {
                    continue
                }
                val artistRaw = sequenceOf("artist", "singer", "author")
                    .map { c.optString(it).trim() }
                    .firstOrNull { it.isNotEmpty() && !isPlaceholderValue(it) }
                val artist = artistRaw?.takeIf {
                    !isPlaceholderValue(it) && !SongNameNormalizer.isJunkFragment(it)
                }
                var q = sequenceOf("bilibili_query", "query", "search", "keyword")
                    .map { c.optString(it).trim() }
                    .firstOrNull { it.isNotEmpty() && !isPlaceholderValue(it) }
                    ?: listOfNotNull(title, artist).joinToString(" ")
                if (isPlaceholderValue(q) || isGarbageTitle(q)) {
                    q = listOfNotNull(title, artist).joinToString(" ")
                }
                add(
                    SongCandidate(
                        title = title,
                        artist = artist,
                        confidence = c.optDouble("confidence", Double.NaN)
                            .takeIf { !it.isNaN() }?.toFloat(),
                        bilibiliQuery = q,
                        note = c.optString("note").takeIf {
                            it.isNotBlank() && !isPlaceholderValue(it)
                        },
                    ),
                )
            }
        }
    }

    private fun sanitizeCandidates(list: List<SongCandidate>): List<SongCandidate> {
        return list.map { c ->
            val title = c.title.trim()
            val artist = c.artist?.trim()?.takeIf { !isPlaceholderValue(it) }
            var q = c.bilibiliQuery.trim()
            if (isBadSearchQuery(q) || isGarbageTitle(q) || isPlaceholderValue(q)) {
                q = listOfNotNull(title, artist)
                    .filter { !isBadSearchQuery(it) && !isGarbageTitle(it) && !isPlaceholderValue(it) }
                    .joinToString(" ")
            }
            if (q.isBlank() || isBadSearchQuery(q) || isPlaceholderValue(q)) {
                q = listOfNotNull(title, artist).joinToString(" ")
            }
            c.copy(
                bilibiliQuery = q.ifBlank { title },
                title = title,
                artist = artist,
            )
        }.filter { c ->
            c.title.isNotBlank() &&
                !isGarbageTitle(c.title) &&
                !isPlaceholderValue(c.title) &&
                !isPlaceholderValue(c.bilibiliQuery) &&
                !isGarbageTitle(c.bilibiliQuery) &&
                !isBadSearchQuery(c.bilibiliQuery.ifBlank { c.title })
        }.distinctBy { it.bilibiliQuery.lowercase() + "|" + it.title.lowercase() }
            .take(6)
    }

    /** 模型照抄 schema 时的占位值 */
    fun isPlaceholderValue(s: String): Boolean {
        val t = s.trim().lowercase()
        if (t.isEmpty() || t == "null" || t == "none" || t == "n/a" || t == "未知") return true
        val bad = setOf(
            "歌名", "歌手", "可选", "歌曲", "曲名", "标题", "作者",
            "真实歌名", "真实歌手", "一两句中文", "中文", "说明",
            "title", "artist", "song", "name", "bilibili_query", "note",
            "歌名 歌手", "真实歌名 真实歌手", "actual song name", "actual artist",
            "secondary", "primary", "since i'm", "since im",
        )
        if (t in bad) return true
        if (t.contains("歌名") && t.contains("歌手")) return true
        if (SongNameNormalizer.isJunkFragment(s)) return true
        return false
    }

    /**
     * 从烂输出里抽歌名。
     * **有中文就只抽中文**，禁止再抽 Something / In Chinese 这类英文碎片。
     */
    private fun carefulExtractTitles(text: String): List<SongCandidate> {
        // 1) 中文曲名（优先）
        val fromZh = SongNameNormalizer.extractChineseFromText(text)
        if (fromZh.isNotEmpty()) {
            return SongNameNormalizer.normalizeAll(fromZh)
        }
        val out = ArrayList<SongCandidate>()
        val seen = HashSet<String>()
        fun add(title: String, note: String) {
            val t = title.trim().trim('"', '\'', '“', '”', '‘', '’')
            val finalT = SongNameNormalizer.cleanForeignTitle(t) ?: t
            if (finalT.length < 2 || isGarbageTitle(finalT) ||
                SongNameNormalizer.isJunkFragment(finalT)
            ) {
                return
            }
            if (!seen.add(finalT.lowercase())) return
            out.add(SongCandidate(title = finalT, bilibiliQuery = finalT, note = note))
        }
        cnTitle.findAll(text).forEach { add(it.groupValues[1], "书名号") }
        labeled.findAll(text).forEach {
            val t = it.groupValues[1].trim()
            if (!isMetaOrInstructionText(t) && hasCjk(t)) add(t, "标注")
        }
        // 2) 全文几乎无中文，或已有拉丁/假名/韩文时，抽外语歌名
        val cjkCount = text.count { it.code in 0x4E00..0x9FFF }
        val hiraKata = text.count { it.code in 0x3040..0x30FF }
        val hangul = text.count { it.code in 0xAC00..0xD7AF }
        if (cjkCount < 4 || hiraKata + hangul >= 2) {
            songLikeEnglish.findAll(text).forEach { m ->
                val p = m.value.trim()
                // 单名至少 4 字（Hello），或多词
                if (p.split(Regex("\\s+")).size < 2 && p.length < 4) return@forEach
                if (isGarbageTitle(p) || SongNameNormalizer.isJunkFragment(p)) return@forEach
                if (!SongLanguage.isPlausibleTitleLength(p)) return@forEach
                add(p, "英文歌名")
            }
            // 日文书名号已在 cnTitle；再抓连续假名/汉字歌名片段
            Regex("""[\u3040-\u30ff\u4e00-\u9fff]{2,20}""").findAll(text).forEach { m ->
                val p = m.value.trim()
                if (p.any { it.code in 0x3040..0x30FF } &&
                    !isGarbageTitle(p) &&
                    !SongNameNormalizer.isJunkFragment(p)
                ) {
                    add(p, "日文歌名")
                }
            }
            Regex("""[\uac00-\ud7af]{2,20}""").findAll(text).forEach { m ->
                val p = m.value.trim()
                if (!isGarbageTitle(p) && !SongNameNormalizer.isJunkFragment(p)) {
                    add(p, "韩文歌名")
                }
            }
        }
        return SongNameNormalizer.normalizeAll(out).take(6)
    }

    fun isGarbageTitle(s: String): Boolean {
        val t = s.trim()
        if (t.length < 2) return true
        if (isPlaceholderValue(t)) return true
        if (isMetaOrInstructionText(t)) return true
        val lower = t.lowercase()
        val junkStarts = listOf(
            "we are", "this is", "the task", "so we", "need to", "you are", "i am",
            "here is", "there is", "for chinese", "for a", "output", "schema",
            "return ", "always", "never", "identify", "interpret", "given",
        )
        if (junkStarts.any { lower.startsWith(it) }) return true
        val metaHits = META_WORDS.count { lower.contains(it) }
        if (metaHits >= 2) return true
        if (lower in IGNORE_EN) return true
        val words = t.split(Regex("\\s+"))
        if (words.size >= 7 && t.any { it.isLowerCase() }) return true
        return false
    }

    private fun isMetaOrInstructionText(s: String): Boolean {
        val lower = s.lowercase()
        if (META_WORDS.count { lower.contains(it) } >= 2) return true
        if (lower.contains("sound-alike") || lower.contains("homophone")) return true
        if (lower.contains("bilibili_query") || lower.contains("json")) return true
        if (lower.contains("chinese users") || lower.contains("english lyrics")) return true
        return false
    }

    private fun isBadSearchQuery(s: String): Boolean {
        if (s.isBlank()) return true
        if (isGarbageTitle(s)) return true
        val cjk = s.count { it.code in 0x4E00..0x9FFF }
        val latin = s.count { it.isLetter() && it.code < 128 }
        if (latin >= 3) return false
        if (s.length <= 40 && cjk >= 2) return false
        if (cjk >= 14 && latin < 2 && s.length > 48) return true
        return false
    }

    private fun stripFences(s: String): String {
        var t = s.trim()
        t = t.replace(Regex("""(?s)<think>.*?</think>"""), " ")
        t = t.replace(Regex("""(?s)<thinking>.*?</thinking>"""), " ")
        if (t.startsWith("```")) {
            t = t.removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            val end = t.lastIndexOf("```")
            if (end >= 0) t = t.substring(0, end)
        }
        return t.trim()
    }

    private fun extractJsonObject(s: String): String? {
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return s.substring(start, end + 1)
    }

    private fun extractCandidatesArray(s: String): JSONArray? {
        val key = Regex("""(?i)"candidates"\s*:\s*\[""")
        val m = key.find(s) ?: return null
        val from = m.range.last
        var depth = 0
        for (i in from until s.length) {
            when (s[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        val slice = s.substring(from, i + 1)
                        return runCatching { JSONArray(softenJson(slice)) }.getOrNull()
                    }
                }
            }
        }
        return null
    }

    private fun softenJson(s: String): String {
        var t = s.trim()
        t = t.replace(Regex(""",\s*([}\]])"""), "$1")
        return t
    }

    private val META_WORDS = listOf(
        "users who", "sound-alike", "homophone", "identify songs", "task is",
        "we are given", "this is", "chinese phrase", "english lyrics", "as chinese",
        "characters", "interpret", "schema", "candidates", "bilibili", "json object",
        "output must", "no markdown", "system", "assistant", "clue",
    )

    private val IGNORE_EN = setOf(
        "reply", "candidates", "title", "artist", "confidence", "bilibili_query", "note",
        "null", "true", "false", "json", "http", "https", "user", "clue", "message",
        "content", "role", "system", "assistant", "schema", "songs", "results",
        "we", "are", "given", "this", "is", "the", "task", "for", "to", "of", "as",
        "so", "need", "only", "with", "from", "that", "who", "type", "text",
    )
}
