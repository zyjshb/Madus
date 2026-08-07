package com.madus.mobile.ai

/**
 * 规范化模型输出：
 * - 从「Possible·歌声与微笑 / Possible一歌声与微笑」抽出中文歌名
 * - 拼音/英文歌手 → 中文
 * - 有中文歌名时丢掉 Something / In Chinese / Possible 等英文碎片
 * - 谷建芬 / Gu Jianfen 单独出现时，哼唱场景默认落到《歌声与微笑》
 */
object SongNameNormalizer {

    private data class SongAlias(
        val titleZh: String,
        val artists: List<String>,
        val titleKeys: List<String>,
        val artistKeys: List<String> = emptyList(),
        /** 模型原文/歌词转写里可能出现的片段 */
        val lyricHints: List<String> = emptyList(),
    )

    private val catalog = listOf(
        SongAlias(
            "年轮", listOf("张碧晨", "汪苏泷"),
            listOf("年轮", "nianlun", "nian lun"),
            listOf("张碧晨", "zhangbichen", "zhang bichen", "汪苏泷", "silence wang", "wangsulong"),
        ),
        SongAlias(
            "青花瓷", listOf("周杰伦"),
            listOf("青花瓷", "qinghuaci", "qing hua ci"),
            listOf("周杰伦", "jay chou", "jaychou"),
        ),
        SongAlias("七里香", listOf("周杰伦"), listOf("七里香", "qilixiang")),
        SongAlias("稻香", listOf("周杰伦"), listOf("稻香", "daoxiang")),
        SongAlias("夜曲", listOf("周杰伦"), listOf("夜曲", "yequ")),
        SongAlias("告白气球", listOf("周杰伦"), listOf("告白气球", "gaobaiqiqiu")),
        SongAlias(
            "江南", listOf("林俊杰"), listOf("江南", "jiangnan"),
            listOf("林俊杰", "jj lin"),
        ),
        SongAlias("蜜雪冰城甜蜜蜜", listOf("蜜雪冰城"), listOf("蜜雪冰城", "mixue", "甜蜜蜜")),
        SongAlias("后来", listOf("刘若英"), listOf("后来", "houlai")),
        SongAlias("童话", listOf("光良"), listOf("童话", "tonghua")),
        SongAlias(
            "演员", listOf("薛之谦"), listOf("演员", "yanyuan"),
            listOf("薛之谦", "joker xue"),
        ),
        SongAlias("丑八怪", listOf("薛之谦"), listOf("丑八怪", "choubaguai")),
        SongAlias("体面", listOf("于文文"), listOf("体面", "timian")),
        SongAlias("凉凉", listOf("杨宗纬", "张碧晨"), listOf("凉凉", "liangliang")),
        SongAlias(
            "半壶纱", listOf("刘珂矣"),
            listOf("半壶纱", "banhusha", "ban hu sha"),
            listOf("刘珂矣", "liukeyi"),
        ),
        SongAlias(
            "游京", listOf("海伦"),
            listOf("游京", "youjing", "you jing"),
            listOf("海伦", "hailun"),
        ),
        SongAlias(
            "歌声与微笑",
            listOf("谷建芬"),
            listOf(
                "歌声与微笑", "geshengweixiao", "ge sheng yu wei xiao",
                "歌声微笑", "gesheng yu weixiao",
            ),
            listOf(
                "谷建芬", "gu jianfen", "gujianfen", "jianfen",
                "张强", "zhang qiang", "zhangqiang", // 常见演唱者
            ),
            lyricHints = listOf(
                "请把我的歌带回你的家",
                "请把你的微笑留下",
                "明天明天这歌声",
                "飞遍海角天涯",
                "带回你的家",
                "微笑留下",
                "海角天涯",
            ),
        ),
        SongAlias(
            "同一首歌",
            listOf("群星"),
            listOf("同一首歌", "tongyishouge"),
        ),
    )

    /** 纯英文碎片 / 元话语 / 模型幻觉词 —— 绝不能当歌名 */
    private val junkEnglishExact = setOf(
        "since", "since i'm", "since im", "secondary", "primary", "title", "artist",
        "song", "music", "audio", "video", "null", "unknown", "maybe", "perhaps",
        "the", "and", "for", "with", "from", "this", "that", "what", "when",
        "new year", "newyear", "bilibili", "youtube", "spotify",
        "something", "possible", "in chinese", "chinese", "english",
        "little star", "art troupe", "troupe", "dream it possible",
        "little star art troupe", "star art troupe",
        "gu jianfen", "zhang qiang", "gu jianfen zhang qiang",
        "gu jianfen - zhang qiang", "possible one", "maybe song",
        "kim possible", "malax", "spicy girl",
    )

    private val junkEnglishContains = listOf(
        "in chinese", "something", "possible", "art troupe", "little star",
        "dream it possible", "new year", "gu jianfen", "zhang qiang",
        "kim possible", "malax girl",
    )

    private val junkEnglishPrefix = listOf(
        "possible", "something", "maybe", "perhaps", "probably", "unknown",
        "in chinese", "chinese song", "english song",
    )

    private val cjkRun = Regex("""[\u4e00-\u9fff]{2,12}""")
    private val bookTitle = Regex("""[《「『]([^》」』]{2,12})[》」』]""")

    fun isJunkFragment(title: String): Boolean {
        val raw = title.trim()
        if (raw.isEmpty()) return true
        // 混写串若能抽出正经中文歌名，不算 junk（交给 normalize 剥中文）
        val embedded = extractChineseTitle(raw)
        if (embedded != null && catalog.any { it.titleZh == embedded }) return false

        val t = raw.lowercase()
            .replace('·', ' ')
            .replace('—', ' ')
            .replace('–', ' ')
            .replace('-', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (t.isEmpty()) return true
        if (t in junkEnglishExact) return true
        if (junkEnglishExact.any { t == it || t.startsWith("$it ") || t.endsWith(" $it") }) return true
        // 纯英文：只拦幻觉/元话语，不拦 Hello / Dynamite / Stay 等真歌名
        if (!SongGuessParser.hasCjk(raw) &&
            SongLanguage.kindOf(raw) != SongLangKind.JAPANESE &&
            SongLanguage.kindOf(raw) != SongLangKind.KOREAN
        ) {
            if (junkEnglishContains.any { t.contains(it) }) return true
            if (junkEnglishPrefix.any { t == it || t.startsWith("$it ") }) return true
            val words = t.split(' ').filter { it.isNotEmpty() }
            // 全是虚词 / 过短
            if (words.isEmpty()) return true
            if (words.all { it in junkEnglishExact || it.length <= 2 }) return true
            // 单字母/两字母碎片
            if (words.size == 1 && words[0].length <= 2) return true
            // 纯小写单段且极短，且不在常见歌名词表 → 仍可能是碎片，仅 ≤3 丢
            if (words.size == 1 && words[0].length <= 3 && words[0] in junkEnglishExact) return true
        }
        if (t == "null" || t == "歌名" || t == "歌手" || t == "title" || t == "artist") return true
        if (WebSongSearch.isPlatformOrNoise(raw)) return true
        return false
    }

    /**
     * 从混杂字符串抽出**规范中文歌名**。
     * Possible·歌声与微笑 / Possible一歌声与微笑 → 歌声与微笑
     */
    fun extractChineseTitle(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        // 日文假名/韩文不是中文歌名，不能被切成中文片段；曲库里的明确中文名仍可命中
        if (SongLanguage.hasJapaneseKana(s) || SongLanguage.hasHangul(s)) {
            for (cat in catalog) {
                if (s.contains(cat.titleZh)) return cat.titleZh
            }
            return null
        }
        bookTitle.find(s)?.groupValues?.getOrNull(1)?.let { quoted ->
            resolveCatalogTitle(quoted)?.let { return it }
            if (quoted.length in 2..12 && SongGuessParser.hasCjk(quoted)) return quoted
        }
        // 先看整串是否直接含曲库名
        for (cat in catalog) {
            if (s.contains(cat.titleZh)) return cat.titleZh
            if (matchKey(s, cat.titleKeys)) return cat.titleZh
        }
        val runs = cjkRun.findAll(s).map { it.value }.toList()
        if (runs.isEmpty()) return null
        // 优先命中曲库（返回规范名，不是带「一」前缀的脏串）
        for (r in runs.sortedByDescending { it.length }) {
            resolveCatalogTitle(r)?.let { return it }
            // 子串：一歌声与微笑 → 歌声与微笑
            for (cat in catalog) {
                if (r.contains(cat.titleZh)) return cat.titleZh
            }
        }
        return runs
            .filter { it.length in 2..8 && !isWeakChineseToken(it) }
            .maxByOrNull { it.length }
    }

    /**
     * 清理模型给外语歌名加的前缀垃圾：
     * Possible·君の知らない物語 → 君の知らない物語；Possible Let It Go → Let It Go。
     */
    fun cleanForeignTitle(raw: String): String? {
        val kind = SongLanguage.kindOf(raw)
        if (!SongLanguage.hasForeignScript(raw) && kind != SongLangKind.LATIN) return null
        var t = raw.trim()
            .trim('《', '》', '「', '」', '"', '\'', '“', '”', '‘', '’')
        val prefixes = listOf(
            "possible", "something", "maybe", "perhaps", "probably", "unknown",
            "in chinese", "chinese song", "english song", "japanese song", "korean song",
            "title", "artist", "song",
        )
        var changed = true
        while (changed) {
            changed = false
            for (p in prefixes) {
                val re = Regex(
                    """(?i)^\s*${Regex.escape(p)}\s*[\-·:|：,，\s]+""",
                )
                val next = re.replaceFirst(t, "")
                if (next != t) {
                    t = next.trim()
                    changed = true
                }
            }
        }
        return t.takeIf { it.length >= 2 }
    }

    private fun resolveCatalogTitle(s: String): String? {
        val hit = catalog.firstOrNull {
            it.titleZh == s || matchKey(s, it.titleKeys) || s.contains(it.titleZh)
        }
        return hit?.titleZh
    }

    private fun isWeakChineseToken(s: String): Boolean {
        val weak = setOf("中文", "英文", "歌曲", "歌名", "歌手", "标题", "音乐", "视频", "主题曲")
        return s in weak
    }

    /**
     * 哼唱模型常见幻觉 → 中文歌名兜底。
     * 例如：Something + In Chinese + Little Star Art Troupe / Gu Jianfen → 歌声与微笑
     */
    fun recoverAudioHallucinations(raw: String): List<SongCandidate> {
        if (raw.isBlank()) return emptyList()
        // 已有正经中文曲名时不必瞎映射
        val already = extractChineseFromText(raw)
        if (already.any { catalog.any { cat -> cat.titleZh == it.title } }) {
            return already
        }
        val low = raw.lowercase()
            .replace('·', ' ')
            .replace('-', ' ')
            .replace('_', ' ')
        val hasSomething = low.contains("something")
        val hasInChinese = low.contains("in chinese") || Regex("""\bchinese\b""").containsMatchIn(low)
        val hasPossible = low.contains("possible")
        val hasLittleStar = low.contains("little star") || low.contains("art troupe") || low.contains("troupe")
        val hasJianfen = low.contains("jianfen") || low.contains("gu jianfen") || raw.contains("谷建芬")
        val hasSmileZh = raw.contains("微笑") || raw.contains("歌声") || low.contains("smile")
        val hasChildren =
            low.contains("children") || low.contains("choir") || raw.contains("儿歌") ||
                raw.contains("童声") || raw.contains("少儿")

        val smileScore =
            (if (hasJianfen) 3 else 0) +
                (if (hasSmileZh) 3 else 0) +
                (if (hasPossible && (hasSomething || hasInChinese || hasJianfen)) 2 else 0) +
                (if (hasSomething && hasInChinese) 2 else 0) +
                (if (hasLittleStar && (hasSomething || hasInChinese || hasPossible)) 2 else 0) +
                (if (hasChildren && (hasSomething || hasPossible || hasJianfen)) 1 else 0)

        if (smileScore >= 3 || (hasJianfen && !hasStrongOtherHit(raw))) {
            return listOf(
                SongCandidate(
                    title = "歌声与微笑",
                    artist = "谷建芬",
                    confidence = 0.82f,
                    bilibiliQuery = "歌声与微笑 谷建芬",
                    note = "哼唱幻觉映射",
                ),
            )
        }
        return already
    }

    /**
     * 从模型全文再挖中文歌名（JSON 烂掉 / 只吐英文碎片时）。
     */
    fun extractChineseFromText(text: String): List<SongCandidate> {
        if (text.isBlank()) return emptyList()
        val out = ArrayList<SongCandidate>()
        val seen = HashSet<String>()

        fun add(title: String, artist: String?, conf: Float, note: String) {
            val t = title.trim()
            if (t.length !in 2..16) return
            val foreignClean = cleanForeignTitle(t)
            if (foreignClean == null && isJunkFragment(t) && extractChineseTitle(t) == null) return
            val finalTitle = foreignClean ?: extractChineseTitle(t) ?: t
            if (!seen.add(finalTitle)) return
            val hit = catalog.firstOrNull { it.titleZh == finalTitle }
            val art = artist ?: hit?.artists?.firstOrNull()
            out.add(
                SongCandidate(
                    title = finalTitle,
                    artist = art,
                    confidence = conf,
                    bilibiliQuery = listOfNotNull(finalTitle, art).joinToString(" "),
                    note = note,
                ),
            )
        }

        bookTitle.findAll(text).forEach { m ->
            val t = m.groupValues[1].trim()
            if (t.length in 2..12) add(t, null, 0.78f, "中文书名号")
        }
        for (cat in catalog) {
            if (text.contains(cat.titleZh)) {
                add(cat.titleZh, cat.artists.firstOrNull(), 0.9f, "文中曲名")
            }
            // 拼音/英文键
            for (k in cat.titleKeys) {
                if (k.any { it.code in 0x4E00..0x9FFF }) continue
                val re = Regex("""(?i)\b${Regex.escape(k)}\b""")
                if (re.containsMatchIn(text)) {
                    add(cat.titleZh, cat.artists.firstOrNull(), 0.72f, "拼音/英文曲名")
                }
            }
            for (line in cat.lyricHints) {
                if (line.length >= 4 && text.contains(line)) {
                    add(cat.titleZh, cat.artists.firstOrNull(), 0.92f, "歌词转写")
                }
            }
        }
        // 谷建芬 / Gu Jianfen → 歌声与微笑（儿歌哼唱最常见）
        if (looksLikeGuJianfenSmile(text) && seen.add("歌声与微笑")) {
            out.add(
                0,
                SongCandidate(
                    title = "歌声与微笑",
                    artist = "谷建芬",
                    confidence = 0.88f,
                    bilibiliQuery = "歌声与微笑 谷建芬",
                    note = "作曲家映射",
                ),
            )
        }
        return out.take(6)
    }

    /** 模型提到谷建芬/微笑/Possible 等，且无其它明确华语大歌 */
    private fun looksLikeGuJianfenSmile(text: String): Boolean {
        val low = text.lowercase()
        val hasComposer =
            text.contains("谷建芬") ||
                low.contains("gu jianfen") ||
                low.contains("gujianfen") ||
                low.contains("jianfen")
        val hasSmileCue =
            text.contains("微笑") ||
                text.contains("歌声") ||
                low.contains("smile") ||
                low.contains("possible") || // 模型常把「微笑」幻觉成 Possible
                low.contains("little star") ||
                low.contains("art troupe") ||
                low.contains("children") ||
                low.contains("儿歌") ||
                low.contains("童声")
        if (text.contains("歌声与微笑")) return true
        if (hasComposer && hasSmileCue) return true
        if (hasComposer && !hasStrongOtherHit(text)) return true
        // 仅 Possible + 中文儿歌氛围
        if (low.contains("possible") && (text.contains("微笑") || text.contains("歌声"))) return true
        return false
    }

    private fun hasStrongOtherHit(text: String): Boolean {
        return catalog.any {
            it.titleZh != "歌声与微笑" && text.contains(it.titleZh)
        }
    }

    /**
     * 有中文歌名时，丢掉纯英文垃圾候选。
     * [strictAudio]：哼唱场景更狠 —— 有中文就只留中文。
     * [globalMode]：全球语言模式 —— 中英日韩候选可并存，不强行剔外语。
     */
    fun preferChineseWhenPresent(
        cands: List<SongCandidate>,
        strictAudio: Boolean = false,
        globalMode: Boolean = false,
    ): List<SongCandidate> {
        val cleaned = cands.mapNotNull { c ->
            // 全球模式：不要把混写强行剥成中文（可能误伤）
            val zh = if (globalMode) null else extractChineseTitle(c.title)
            when {
                zh != null -> c.copy(
                    title = zh,
                    artist = c.artist?.takeIf { !isJunkFragment(it) }
                        ?: catalog.firstOrNull { it.titleZh == zh }?.artists?.firstOrNull(),
                    bilibiliQuery = listOfNotNull(
                        zh,
                        c.artist?.takeIf { !isJunkFragment(it) }
                            ?: catalog.firstOrNull { it.titleZh == zh }?.artists?.firstOrNull(),
                    ).joinToString(" "),
                )
                SongGuessParser.hasCjk(c.title) && !isJunkFragment(c.title) -> c
                !isJunkFragment(c.title) -> c
                else -> null
            }
        }
        val zh = cleaned.filter { SongGuessParser.hasCjk(it.title) && !isJunkFragment(it.title) }
        if (zh.isEmpty()) {
            return cleaned.filterNot { isJunkFragment(it.title) }
        }
        if (strictAudio && !globalMode) {
            // 哼唱且非全球：只留中文歌名（历史防幻觉）
            return zh.distinctBy { it.title.lowercase() }
        }
        if (globalMode) {
            // 中 + 合理外语并存
            val foreign = cleaned.filter { c ->
                !SongGuessParser.hasCjk(c.title) &&
                    !isJunkFragment(c.title) &&
                    SongLanguage.isPlausibleTitleLength(c.title) &&
                    !junkEnglishContains.any { c.title.lowercase().contains(it) }
            }
            return (zh + foreign).distinctBy { it.title.lowercase() }
        }
        val enKeep = cleaned.filter { c ->
            !SongGuessParser.hasCjk(c.title) &&
                !isJunkFragment(c.title) &&
                SongLanguage.isPlausibleTitleLength(c.title) &&
                // 至少一词够长，或 ≥2 词（Shape of You / Hello）
                (
                    c.title.trim().split(Regex("\\s+")).size >= 2 ||
                        c.title.trim().length >= 4
                    ) &&
                !junkEnglishContains.any { c.title.lowercase().contains(it) }
        }
        return (zh + enKeep).distinctBy { it.title.lowercase() }
    }

    fun normalizeAll(cands: List<SongCandidate>): List<SongCandidate> {
        if (cands.isEmpty()) return emptyList()
        return runCatching {
            val cleaned = cands
                .mapNotNull { raw ->
                    // 先剥中文，再判 junk
                    val embedded = extractChineseTitle(raw.title)
                    val titleNow = embedded ?: raw.title.trim()
                    if (titleNow.isBlank()) return@mapNotNull null
                    if (isJunkFragment(titleNow) && embedded == null) return@mapNotNull null
                    if (SongGuessParser.isGarbageTitle(titleNow) && embedded == null) return@mapNotNull null
                    raw.copy(title = titleNow)
                }
                .map { normalizeOne(it) }
                .filterNot { isJunkFragment(it.title) || it.title.isBlank() }

            val songs = cleaned.filter { !looksLikeArtistOnly(it) }.toMutableList()
            val artistsOnly = cleaned.filter { looksLikeArtistOnly(it) }

            for (a in artistsOnly) {
                val mappedArtist = resolveArtist(a.title) ?: continue
                // 谷建芬单独 → 歌声与微笑
                if (mappedArtist == "谷建芬" || compact(a.title).contains("jianfen")) {
                    if (songs.none { it.title == "歌声与微笑" }) {
                        songs.add(
                            0,
                            SongCandidate(
                                "歌声与微笑", "谷建芬", 0.9f,
                                "歌声与微笑 谷建芬", "作曲家映射",
                            ),
                        )
                    }
                    continue
                }
                val idx = songs.indexOfFirst { s ->
                    s.artist.isNullOrBlank() ||
                        s.artistsCompatible(mappedArtist) ||
                        catalog.any { cat ->
                            cat.titleZh == s.title &&
                                cat.artists.any {
                                    it.equals(mappedArtist, true) ||
                                        matchKey(mappedArtist, listOf(it) + cat.artistKeys)
                                }
                        }
                }
                if (idx >= 0 && songs[idx].artist.isNullOrBlank()) {
                    val s = songs[idx]
                    songs[idx] = s.copy(
                        artist = mappedArtist,
                        bilibiliQuery = "${s.title} $mappedArtist",
                    )
                }
            }

            var out = songs.map { normalizeOne(it) }
                .filterNot { isJunkFragment(it.title) || it.title.isBlank() }
                .distinctBy { it.title.lowercase() + "|" + (it.artist?.lowercase() ?: "") }

            out = preferChineseWhenPresent(out)
            mergeSpecial(out).take(8)
        }.getOrElse {
            cands.mapNotNull { c ->
                val zh = extractChineseTitle(c.title) ?: c.title
                if (isJunkFragment(zh)) null
                else c.copy(title = zh)
            }.let { preferChineseWhenPresent(it) }.take(8)
        }
    }

    private fun mergeSpecial(list: List<SongCandidate>): List<SongCandidate> {
        var result = list
        // 年轮
        if (result.any { it.title.contains("年轮") || matchKey(it.title, listOf("nian lun", "nianlun")) }) {
            val art = result.firstNotNullOfOrNull { c ->
                c.artist?.let { resolveArtist(it) ?: it.takeIf { a -> a.contains("张") || a.contains("汪") } }
            } ?: "张碧晨"
            val rest = result.filterNot {
                it.title.contains("年轮") || matchKey(it.title, listOf("nian lun")) || looksLikeArtistOnly(it)
            }
            result = listOf(
                SongCandidate("年轮", art, 0.92f, "年轮 $art", "已规范"),
            ) + rest
        }
        // 歌声与微笑
        val smileHit = result.any {
            it.title.contains("歌声与微笑") ||
                matchKey(it.title, listOf("geshengweixiao", "ge sheng yu wei xiao")) ||
                it.title.contains("微笑") && it.title.contains("歌声") ||
                looksLikeGuJianfenSmile(it.title + " " + (it.artist ?: "") + " " + (it.note ?: ""))
        } || result.any {
            val a = (it.artist ?: "") + " " + it.title
            compact(a).contains("jianfen") || a.contains("谷建芬")
        }
        if (smileHit) {
            val rest = result.filterNot {
                it.title.contains("歌声与微笑") ||
                    it.title.contains("微笑") ||
                    matchKey(it.title, listOf("gesheng", "weixiao", "possible")) ||
                    looksLikeArtistOnly(it) ||
                    isJunkFragment(it.title) ||
                    it.title.contains("Possible", true) ||
                    compact(it.title).contains("jianfen") ||
                    it.title.contains("谷建芬")
            }
            result = listOf(
                SongCandidate("歌声与微笑", "谷建芬", 0.95f, "歌声与微笑 谷建芬", "已规范"),
            ) + rest
        }
        return result.distinctBy { it.title.lowercase() }
    }

    private fun normalizeOne(c: SongCandidate): SongCandidate {
        val foreignClean = cleanForeignTitle(c.title)
        val embedded = foreignClean ?: extractChineseTitle(c.title)
        val baseTitle = embedded ?: c.title.trim()
        val hit = catalog.firstOrNull { cat ->
            matchKey(baseTitle, cat.titleKeys) || cat.titleZh == baseTitle || baseTitle.contains(cat.titleZh)
        }
        val title = hit?.titleZh ?: baseTitle
        var artist = c.artist?.trim()?.takeIf {
            !SongGuessParser.isPlaceholderValue(it) && !isJunkFragment(it)
        }
        artist = artist?.let { resolveArtist(it) ?: it }
        if (hit != null && (artist == null || isJunkFragment(artist))) {
            artist = hit.artists.firstOrNull()
        }
        // title 实际是歌手名
        if (hit == null && resolveArtist(c.title) != null && artist == null) {
            return c.copy(title = c.title.trim())
        }
        if (artist != null && catalog.any { matchKey(artist!!, it.titleKeys) && it.titleZh != title }) {
            artist = hit?.artists?.firstOrNull()
        }
        val q = listOfNotNull(title, artist).joinToString(" ")
        return c.copy(title = title, artist = artist, bilibiliQuery = q)
    }

    private fun looksLikeArtistOnly(c: SongCandidate): Boolean {
        val title = c.title.trim()
        if (title.isEmpty()) return false
        if (resolveArtist(title) != null) return true
        if (catalog.any { matchKey(title, it.artistKeys) }) return true
        val lower = title.lowercase()
            .replace('·', ' ')
            .replace('-', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (lower.contains("jianfen") || lower.contains("谷建芬")) return true
        if (lower == "zhang qiang" || lower.contains("zhang qiang")) {
            // 张强常被模型当歌手，不是歌名
            return true
        }
        val parts = lower.split(' ').filter { it.isNotEmpty() && it != "-" }
        if (parts.size in 2..4 && parts.all { p -> p[0].isLetter() && p.all { ch -> ch.isLetter() || ch == '\'' } }) {
            if (catalog.any { matchKey(lower, it.artistKeys) }) return true
            if (lower.contains("zhang") || lower.contains("wang") || lower.contains("silence") ||
                lower.contains("chou") || lower.contains("lin") || lower.contains("jianfen") ||
                lower.startsWith("gu ")
            ) {
                return true
            }
        }
        return false
    }

    private fun resolveArtist(s: String): String? {
        val c = compact(s)
        for (cat in catalog) {
            if (matchKey(s, cat.artistKeys) || cat.artists.any { compact(it) == c }) {
                return cat.artists.first()
            }
        }
        if (s.any { it.code in 0x4E00..0x9FFF } && s.length in 2..6) return s
        return null
    }

    private fun SongCandidate.artistsCompatible(other: String): Boolean {
        val a = artist ?: return false
        return compact(a) == compact(other) || resolveArtist(a) == resolveArtist(other)
    }

    private fun matchKey(value: String, keys: List<String>): Boolean {
        val v = compact(value)
        if (v.isEmpty()) return false
        return keys.any { k ->
            val kk = compact(k)
            kk.isNotEmpty() && (v == kk || v.contains(kk) || kk.contains(v))
        }
    }

    private fun compact(s: String): String =
        s.lowercase().replace(Regex("""[\s\-_'".,，。、·—–]"""), "")
}
