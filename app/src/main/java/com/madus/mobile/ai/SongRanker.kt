package com.madus.mobile.ai

import com.madus.mobile.domain.Track

/**
 * 候选排序 + **B 站落地相关度**（通用，不为单曲硬编码）。
 *
 * 乱搜根因（已修）：
 * 1) 裸搜短歌名（Baby）→ 全站脏结果照单全收
 * 2) rankTracks 几乎只做 contains，不降权纯音乐/合集/小时循环
 * 3) 无「歌名必须对得上」门槛
 */
object SongRanker {

    fun rankCandidates(
        userClue: String,
        cands: List<SongCandidate>,
        forceLanguage: SongLangKind? = null,
        preferForeign: Boolean = false,
    ): List<SongCandidate> {
        if (cands.isEmpty()) return emptyList()
        val compact = compact(userClue)
        val clueKind = forceLanguage ?: SongLanguage.kindOf(userClue)
        // 显式识别到中文时，即使开关还开着也不能再当外语歌处理
        val foreignIntent = preferForeign &&
            (forceLanguage == null || SongLanguage.isForeign(forceLanguage))
        return cands
            .map { it to scoreCandidate(compact, it, clueKind, foreignIntent) }
            .sortedByDescending { it.second }
            .map { (c, s) ->
                c.copy(confidence = s.coerceIn(0.2f, 0.98f))
            }
            .distinctBy { it.title.lowercase().trim() }
            .take(8)
    }

    /**
     * 按歌名候选给 B 站稿件打分排序；低分（不相关/垃圾）直接丢掉。
     * [minScore] 默认 18：至少要有明显歌名命中。
     */
    fun rankTracks(
        tracks: List<Track>,
        topSongs: List<SongCandidate>,
        minScore: Int = 18,
    ): List<Track> {
        if (tracks.isEmpty() || topSongs.isEmpty()) return emptyList()
        return tracks
            .map { t -> t to bestTrackScore(t, topSongs) }
            .filter { it.second >= minScore }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { it.id }
    }

    /** 单条稿件相对候选的最佳分 */
    fun bestTrackScore(track: Track, topSongs: List<SongCandidate>): Int {
        if (topSongs.isEmpty()) return 0
        if (isHardGarbageTitle(track.title)) return -100
        return topSongs.take(5).maxOf { scoreTrackAgainstSong(track, it) }
    }

    /**
     * 为 B 站搜索构造查询（通用策略）：
     * - 优先「歌名 歌手」
     * - 短/泛歌名禁止裸搜（必须带歌手或修饰）
     * - 附带 完整版 / official 等，减少脏结果
     */
    fun buildSearchQueries(c: SongCandidate, max: Int = 4): List<String> {
        val title = c.title.trim()
        val artist = c.artist?.trim().orEmpty()
            .takeIf { it.isNotBlank() && !SongGuessParser.isPlaceholderValue(it) && !SongNameNormalizer.isJunkFragment(it) }
            .orEmpty()
        val q0 = c.bilibiliQuery.trim()
        val out = LinkedHashSet<String>()

        val shortOrGeneric = isShortOrGenericTitle(title)

        if (artist.isNotBlank()) {
            out.add("$title $artist")
            out.add("$title $artist 完整版")
            if (!SongGuessParser.hasCjk(title)) {
                out.add("$title $artist official")
                out.add("$title $artist audio")
            } else {
                out.add("$title $artist 官方")
            }
        }

        if (q0.isNotBlank() && q0 != title && !SongGuessParser.isGarbageTitle(q0)) {
            out.add(q0)
        }

        // 裸歌名：仅当标题够具体时才允许
        if (!shortOrGeneric) {
            out.add(title)
            out.add("$title 完整版")
            if (!SongGuessParser.hasCjk(title)) {
                out.add("$title official audio")
            } else {
                out.add("$title 官方音频")
            }
        } else if (artist.isBlank()) {
            // 短名又无歌手：加音乐向后缀，避免搜到电影/游戏
            out.add("$title 歌曲")
            out.add("$title song")
            out.add("$title 完整版")
            if (!SongGuessParser.hasCjk(title)) {
                out.add("$title music audio")
            }
        }

        return out
            .map { it.trim() }
            .filter { it.length in 2..64 && !SongGuessParser.isGarbageTitle(it) }
            .take(max)
    }

    fun scoreTrackAgainstSong(track: Track, song: SongCandidate): Int {
        val songTitle = song.title.trim()
        if (songTitle.length < 2) return 0
        if (isHardGarbageTitle(track.title)) return -100

        // search 结果里的多 P 提示（title · 2P）不算歌名的一部分
        val vt = normalize(track.title.replace(Regex("""\s*·\s*\d+P\s*$"""), ""))
        val va = normalize(track.artist)
        val blob = "$vt $va"
        val st = normalize(songTitle)
        val sa = normalize(song.artist.orEmpty())

        var s = 0

        // —— 歌名命中（核心）——
        val titleHit = when {
            vt == st -> 70
            vt.startsWith(st) || vt.contains(" $st") || vt.contains(st) && st.length >= 4 -> 55
            st.length >= 2 && vt.contains(st) -> 40
            // 拉丁：按词命中
            tokenHitRatio(st, vt) >= 0.8f && st.length >= 4 -> 45
            tokenHitRatio(st, vt) >= 0.5f && st.length >= 4 -> 22
            else -> 0
        }
        s += titleHit

        // 短歌名（baby/stay/love）：必须标题里像「独立词」且最好有歌手
        if (isShortOrGenericTitle(songTitle)) {
            if (titleHit < 40) s -= 35
            if (sa.isNotBlank() && (va.contains(sa) || vt.contains(sa))) s += 35
            else if (sa.isNotBlank()) s -= 15
            // 标题仅含 baby 但夹在一堆无关词里
            if (titleHit > 0 && vt.length > st.length * 4) s -= 12
        }

        // —— 歌手命中 ——
        if (sa.length >= 2) {
            when {
                va.contains(sa) || vt.contains(sa) -> s += 28
                tokenHitRatio(sa, "$vt $va") >= 0.6f -> s += 16
                else -> s -= 8 // 有歌手却对不上，略降
            }
        }

        // —— 官方/完整向 ——
        if (listOf("完整", "官方", "official", "mv", "audio", "音频", "原曲", "高音质", "flac", "full ver", "主题曲")
                .any { blob.contains(it) }
        ) {
            s += 8
        }

        // —— 垃圾/偏题（通用降权，不为某首歌特判）——
        s += garbagePenalty(blob, track.durationMs)

        // 时长：正常单曲 1～7 分钟；超长循环/过短素材降权
        val d = track.durationMs
        when {
            d in 90_000L..420_000L -> s += 10
            d in 70_000L..600_000L -> s += 5
            d in 45_000L..90_000L -> s += 1
            d > 0 && d < 45_000L -> s -= 18
            d > 15 * 60_000L -> s -= 25 // 小时循环/合集
            d > 8 * 60_000L -> s -= 12
        }

        // 合集多分 P 往往不是单曲
        if (track.pageCount > 3) s -= 10
        if (track.album.contains("合集")) s -= 8

        // 完全无歌名命中 → 直接判死
        if (titleHit <= 0 && tokenHitRatio(st, vt) < 0.4f) {
            return minOf(s, 5)
        }

        return s
    }

    /** 明显不是「这首歌可播版」的标题 */
    fun isHardGarbageTitle(title: String): Boolean {
        val t = title.lowercase()
        val hard = listOf(
            "教程", "教学", "简介", "介绍", "讲解", "怎么", "如何",
            "有声", "小说", "八字", "命理", "连读", "英语听力",
            "反应", "reaction", "开箱", "测评", "评测",
            "鬼畜", "整活", "沙雕", "杂谈", "采访",
            "直播回放", "纪录片", "第1集", "第一季",
            "音效包", "音效素材", "素材包",
            "白噪音", "助眠", "睡眠音乐", "解压",
            "踩点合集", "卡点合集", "混剪合集",
        )
        if (hard.any { t.contains(it) }) return true
        if (WebSongSearch.isPlatformOrNoise(title)) return true
        return false
    }

    /**
     * 偏题惩罚：纯音乐/钢琴/小时循环/串烧等。
     * 通用规则——用户要的是「这首歌」，不是任意 BGM 氛围。
     */
    private fun garbagePenalty(blob: String, durationMs: Long): Int {
        var p = 0
        val demoteStrong = listOf(
            "纯音乐", "钢琴曲", "钢琴版", "小提琴", "古筝", "二胡",
            "八音盒", "音乐盒", "鼓点", "节奏型", "节奏感",
            "1小时", "1 小时", "一小时", "2小时", "10小时", "小时循环",
            "循环播放", "超长", "长版合集",
            "串烧", "连播", "歌单", "合集向", "精选辑",
            "夜芯", "踩点", "卡点", "混剪", "剪辑向",
            "bgm素材", "游戏bgm", "宣传片",
            "slowed", "reverb", "sped up", "nightcore",
            "karaoke", "伴奏", "instrumental", "off vocal",
        )
        val demoteSoft = listOf(
            "纯音", "piano", "violin", "orchestral", "交响",
            "cover", "翻唱", "cover.ver", "吉他弹唱",
            "live", "现场", "演唱会",
            "抖音", "热播", "热门bgm",
        )
        for (w in demoteStrong) {
            if (blob.contains(w)) p -= 28
        }
        for (w in demoteSoft) {
            if (blob.contains(w)) p -= 10
        }
        // 标题带「纯音乐」且很长 → 更狠
        if (blob.contains("纯音乐") && durationMs > 6 * 60_000L) p -= 20
        return p
    }

    fun isShortOrGenericTitle(title: String): Boolean {
        val t = title.trim()
        if (t.isEmpty()) return true
        // 日文/韩文一个词也常是完整歌名，不能按“短英文词”的逻辑降级裸搜
        if (SongLanguage.hasJapaneseKana(t) || SongLanguage.hasHangul(t)) return false
        val cjk = t.count { it.code in 0x4E00..0x9FFF }
        val letters = t.count { it.isLetter() }
        // 单汉字 / 两汉字极泛；英文 ≤5 且词数 ≤1
        if (cjk in 1..2 && t.length <= 2) return true
        if (cjk == 0 && letters in 1..5 && t.split(Regex("\\s+")).size <= 1) return true
        // 极泛英文词
        val low = t.lowercase()
        val generic = setOf(
            "baby", "love", "stay", "hello", "sorry", "home", "girl", "boy",
            "run", "fire", "one", "two", "star", "dream", "night", "day",
            "you", "me", "us", "her", "him", "song", "music",
        )
        if (low in generic) return true
        return false
    }

    private fun normalize(s: String): String =
        s.lowercase()
            .replace('　', ' ')
            .replace('・', ' ')
            .replace(Regex("""【.*?】|\(.*?\)|\[.*?\]|（.*?）"""), " ")
            .replace(Regex("""[^\p{L}\p{N}\s]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    /** 拉丁/分词命中比例 */
    private fun tokenHitRatio(needle: String, hay: String): Float {
        val n = normalize(needle)
        val h = normalize(hay)
        if (n.isEmpty() || h.isEmpty()) return 0f
        if (h.contains(n)) return 1f
        val tokens = n.split(' ').filter { it.length >= 2 }
        if (tokens.isEmpty()) return if (h.contains(n)) 1f else 0f
        val hit = tokens.count { h.contains(it) }
        return hit.toFloat() / tokens.size
    }

    private fun scoreCandidate(
        compactClue: String,
        c: SongCandidate,
        clueKind: SongLangKind,
        foreignIntent: Boolean,
    ): Float {
        val title = c.title.trim()
        val artist = c.artist?.trim().orEmpty()
        var score = c.confidence ?: 0.4f
        val preferCn = clueKind == SongLangKind.CHINESE || clueKind == SongLangKind.UNKNOWN
        val titleKind = SongLanguage.kindOf(title)

        when {
            clueKind == SongLangKind.JAPANESE -> when (titleKind) {
                SongLangKind.JAPANESE -> score += 0.2f
                SongLangKind.CHINESE -> {
                    if (SongLanguage.hasJapaneseKana(title)) score += 0.2f
                    else score -= 0.08f
                }
                SongLangKind.KOREAN, SongLangKind.LATIN -> score -= 0.15f
                else -> {}
            }
            clueKind == SongLangKind.KOREAN -> when (titleKind) {
                SongLangKind.KOREAN -> score += 0.2f
                else -> score -= 0.15f
            }
            clueKind == SongLangKind.LATIN || foreignIntent -> when (titleKind) {
                SongLangKind.LATIN -> score += 0.16f
                SongLangKind.JAPANESE, SongLangKind.KOREAN -> score += 0.1f
                SongLangKind.CHINESE -> score -= 0.18f
                SongLangKind.MIXED -> {
                    if (SongLanguage.hasJapaneseKana(title) || SongLanguage.hasHangul(title)) {
                        score += 0.08f
                    } else {
                        score -= 0.08f
                    }
                }
                else -> {}
            }
            clueKind == SongLangKind.CHINESE -> when (titleKind) {
                SongLangKind.CHINESE -> score += 0.12f
                SongLangKind.LATIN, SongLangKind.JAPANESE, SongLangKind.KOREAN -> score -= 0.12f
                else -> {}
            }
            else -> {}
        }

        if (preferCn && title.length >= 12 && compactClue.contains(compact(title).take(8))) {
            score -= 0.45f
        }
        if (titleKind == SongLangKind.LATIN) {
            if (title.length > 48) score -= 0.3f
            else if (title.length in 4..32) score += 0.1f
        } else {
            if (title.length > 16) score -= 0.25f
            if (title.length in 2..8) score += 0.12f
        }

        if (SongGuessParser.isPlaceholderValue(title) || title == "歌名") {
            return -1f
        }
        if (compactClue.isNotBlank() &&
            (c.note?.contains("歌词像") == true || c.note?.contains("错字") == true)
        ) {
            score += 0.12f
        }
        if (c.note?.contains("全网") == true) score += 0.1f

        if (compactClue.isNotBlank()) {
            val looksQingHua = looksLikeBluePorcelainClue(compactClue)
            if (looksQingHua) {
                when {
                    title.contains("青花瓷") -> score += 0.35f
                    title == "江南" || (title.contains("江南") && !title.contains("青花")) ->
                        score -= 0.45f
                }
            }
            if (compactClue.contains("我爱你你爱我") || compactClue.contains("蜜雪")) {
                if (title.contains("蜜雪")) score += 0.35f
            }
        }
        if (artist.isNotBlank() && !SongGuessParser.isPlaceholderValue(artist) &&
            !SongNameNormalizer.isJunkFragment(artist)
        ) {
            score += 0.08f
        }

        if (SongNameNormalizer.isJunkFragment(title)) {
            score -= 0.8f
        }
        if (!SongGuessParser.hasCjk(title)) {
            val low = title.lowercase()
            if (low.contains("something") || low.contains("possible") ||
                low.contains("in chinese") || low.contains("troupe") ||
                low.contains("little star")
            ) {
                score -= 0.55f
            } else if (preferCn) {
                score -= 0.2f
            } else {
                score += 0.12f
                if (titleKind == SongLangKind.JAPANESE || titleKind == SongLangKind.KOREAN) {
                    score += 0.05f
                }
            }
        } else {
            if (preferCn) score += 0.12f
            if (title == "歌声与微笑" || title == "青花瓷" || title == "年轮") {
                score += 0.08f
            }
        }

        val q = c.bilibiliQuery
        val qMax = if (titleKind == SongLangKind.LATIN) 56 else 28
        if (q.length > qMax) score -= 0.15f
        if (SongGuessParser.hasCjk(title) && title.length <= 10) score += 0.05f

        return score
    }

    fun looksLikeBluePorcelainClue(compact: String): Boolean {
        val hasQing = compact.contains('青') || compact.contains('瓷') || compact.contains("如青")
        val hasErWo = compact.contains("而我")
        val hasJiangNan = compact.contains("江南")
        val hasDeng = compact.contains('等') || compact.contains("烟雨") || compact.contains("录过")
        val hasLu = compact.contains("录过") || compact.contains("路过") || compact.contains("在等")
        val jjMarkers = compact.contains("风到") || compact.contains('黏') || compact.contains('软')
        if (jjMarkers) return false
        return (hasQing && (hasErWo || hasDeng || hasJiangNan)) ||
            (hasErWo && hasLu && hasJiangNan) ||
            (hasQing && hasJiangNan)
    }

    private fun compact(s: String): String =
        s.replace('　', ' ')
            .replace(Regex("""\s+"""), "")
            .replace("，", "")
            .replace(",", "")
            .replace("。", "")
            .replace("…", "")
            .replace(".", "")
}
