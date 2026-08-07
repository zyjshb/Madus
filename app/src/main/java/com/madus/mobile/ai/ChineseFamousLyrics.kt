package com.madus.mobile.ai

/**
 * 华语金曲歌词模糊匹配（本地）。
 * 用户常记错字、记混句，不能只靠「原句搜 B 站」。
 *
 * 不是完整曲库：覆盖常见高召回；其余仍靠模型 + 原句搜。
 */
object ChineseFamousLyrics {

    private data class Song(
        val title: String,
        val artist: String,
        /** 正确歌词片段，用于子串命中 */
        val lines: List<String>,
        /** 用户常写错/记混的说法 */
        val aliases: List<String> = emptyList(),
        /** 特征字（错句里仍常保留的字） */
        val signatureChars: String = "",
        val minSignatureHits: Int = 3,
    )

    private val songs = listOf(
        Song(
            title = "青花瓷",
            artist = "周杰伦",
            lines = listOf(
                "天青色等烟雨",
                "而我在等你",
                "炊烟袅袅升起",
                "隔江千万里",
                "素胚勾勒出青花",
                "笔锋浓转淡",
                "瓶身描绘的牡丹",
                "一如你初妆",
                "冉冉檀香透过窗",
                "釉色渲染仕女图",
            ),
            aliases = listOf(
                "而我录过了江南",
                "而我路过江南",
                "而我在等你",
                "天青色",
                "等烟雨",
                "如青学",
                "如初妆",
                "青花笔",
                "浓转淡",
            ),
            signatureChars = "青瓷烟雨等你素胚笔锋牡丹初妆檀香",
            minSignatureHits = 3,
        ),
        Song(
            title = "七里香",
            artist = "周杰伦",
            lines = listOf(
                "窗外的麻雀",
                "在电线杆上多嘴",
                "你说这一句很有夏天的感觉",
                "手中的铅笔",
            ),
            aliases = listOf("窗外的麻雀", "电线杆上多嘴"),
            signatureChars = "麻雀电线杆铅笔夏天",
            minSignatureHits = 3,
        ),
        Song(
            title = "稻香",
            artist = "周杰伦",
            lines = listOf(
                "对这个世界如果你有太多的抱怨",
                "不要放弃理想",
                "还记得你说家是唯一的城堡",
            ),
            aliases = listOf("不要抱怨", "家是唯一的城堡"),
            signatureChars = "稻香抱怨理想城堡",
            minSignatureHits = 3,
        ),
        Song(
            title = "夜曲",
            artist = "周杰伦",
            lines = listOf(
                "一群嗜血的蚂蚁被腐肉所吸引",
                "我面无表情看孤独的风景",
            ),
            signatureChars = "嗜血蚂蚁腐肉夜曲",
            minSignatureHits = 3,
        ),
        Song(
            title = "东风破",
            artist = "周杰伦",
            lines = listOf(
                "一盏离愁孤单伫立在窗口",
                "我在门后假装你人还没走",
                "旧地如重游月圆更寂寞",
            ),
            signatureChars = "离愁窗口门后东风破",
            minSignatureHits = 3,
        ),
        Song(
            title = "菊花台",
            artist = "周杰伦",
            lines = listOf(
                "你的泪光柔弱中带伤",
                "惨白的月弯弯勾住过往",
            ),
            signatureChars = "泪光月弯菊花台",
            minSignatureHits = 3,
        ),
        Song(
            title = "听妈妈的话",
            artist = "周杰伦",
            lines = listOf(
                "听妈妈的话别让她受伤",
                "想快快长大才能保护她",
            ),
            signatureChars = "妈妈的话快快长大保护",
            minSignatureHits = 3,
        ),
        Song(
            title = "江南",
            artist = "林俊杰",
            lines = listOf(
                "风到这里就是黏",
                "风到这里就是软",
                "底下我一再重演",
            ),
            aliases = listOf("风到这里就是黏", "乌鹊桥"),
            signatureChars = "江南风黏软乌鹊",
            minSignatureHits = 3,
        ),
        Song(
            title = "童话",
            artist = "光良",
            lines = listOf(
                "忘掉是你的错还是我的错",
                "你哭着对我说童话里都是骗人的",
            ),
            signatureChars = "童话骗人幸福结局",
            minSignatureHits = 3,
        ),
        Song(
            title = "后来",
            artist = "刘若英",
            lines = listOf(
                "后来我总算学会了如何去爱",
                "可惜你早已远去消失在人海",
            ),
            signatureChars = "后来学会爱消失人海",
            minSignatureHits = 3,
        ),
        Song(
            title = "告白气球",
            artist = "周杰伦",
            lines = listOf(
                "塞纳河畔左岸的咖啡",
                "我手一杯品尝你的美",
            ),
            signatureChars = "塞纳河咖啡告白气球",
            minSignatureHits = 3,
        ),
        Song(
            title = "年轮",
            artist = "张碧晨",
            lines = listOf(
                "一年一年",
                "风也过烟也过",
                "我最沉默",
                "何时才能看穿这份对错",
            ),
            aliases = listOf("nian lun", "nianlun", "张碧晨 年轮", "汪苏泷 年轮"),
            signatureChars = "年轮沉默对错风烟",
            minSignatureHits = 3,
        ),
        Song(
            title = "蜜雪冰城甜蜜蜜",
            artist = "蜜雪冰城",
            lines = listOf(
                "我爱你你爱我",
                "蜜雪冰城甜蜜蜜",
                "你爱我我爱你",
            ),
            aliases = listOf("蜜雪冰城", "甜蜜蜜"),
            signatureChars = "我爱你蜜雪冰城甜蜜",
            minSignatureHits = 4,
        ),
        Song(
            title = "大海航行靠舵手",
            artist = "",
            lines = listOf(
                "大海航行靠舵手",
                "万物生长靠太阳",
                "雨露滋润禾苗壮",
            ),
            aliases = listOf("大海航线靠舵手"),
            signatureChars = "大海航行舵手万物太阳",
            minSignatureHits = 4,
        ),
        Song(
            title = "歌声与微笑",
            artist = "谷建芬",
            lines = listOf(
                "请把我的歌带回你的家",
                "请把你的微笑留下",
                "明天明天这歌声",
                "飞遍海角天涯",
                "歌声与微笑",
            ),
            aliases = listOf(
                "歌声与微笑",
                "带回你的家",
                "微笑留下",
                "海角天涯",
                "谷建芬",
            ),
            signatureChars = "歌声微笑带回你家海角天涯",
            minSignatureHits = 3,
        ),
    )

    fun suggest(userText: String): List<SongCandidate> {
        val t = userText.trim()
        if (t.length < 4) return emptyList()
        // 外语歌词不能进华语金曲表，避免日语汉字/韩语汉字被误判成中文歌
        if (SongLanguage.isForeign(SongLanguage.kindOf(t))) return emptyList()
        if (!HomophoneLocalHints.isPureChineseClue(t) &&
            t.count { it.code in 0x4E00..0x9FFF } < 4
        ) {
            return emptyList()
        }
        val compact = t.replace(Regex("""\s+"""), "")
        val scored = songs.mapNotNull { song ->
            val score = score(compact, song)
            if (score < 0.28f) return@mapNotNull null
            score to song
        }.sortedByDescending { it.first }

        return scored.take(4).map { (score, song) ->
            val q = if (song.artist.isBlank()) song.title else "${song.title} ${song.artist}"
            SongCandidate(
                title = song.title,
                artist = song.artist.ifBlank { null },
                confidence = score.coerceIn(0.35f, 0.95f),
                bilibiliQuery = q,
                note = "歌词像「${song.title}」（允许错字/记混）",
            )
        }
    }

    private fun score(compact: String, song: Song): Float {
        var best = 0f
        // 1) 正确歌词子串
        for (line in song.lines) {
            if (compact.contains(line)) return 0.95f
            // 连续 4 字命中
            if (line.length >= 4) {
                for (i in 0..line.length - 4) {
                    val gram = line.substring(i, i + 4)
                    if (compact.contains(gram)) best = maxOf(best, 0.72f)
                }
            }
        }
        // 2) 常见错记
        for (a in song.aliases) {
            if (compact.contains(a.replace(" ", ""))) best = maxOf(best, 0.8f)
            // 别名 3 字片段
            val aa = a.replace(" ", "")
            if (aa.length >= 3) {
                for (i in 0..aa.length - 3) {
                    if (compact.contains(aa.substring(i, i + 3))) best = maxOf(best, 0.55f)
                }
            }
        }
        // 3) 特征字命中（错句仍常留下的字）
        if (song.signatureChars.isNotEmpty()) {
            val hits = song.signatureChars.count { compact.contains(it) }
            if (hits >= song.minSignatureHits) {
                val ratio = hits.toFloat() / song.signatureChars.length.coerceAtLeast(1)
                best = maxOf(best, 0.35f + ratio * 0.5f)
            }
        }
        // 4) 青花瓷：记混句强特征
        if (song.title == "青花瓷") {
            val hasQing = compact.contains('青') || compact.contains('瓷') || compact.contains("如青")
            val hasJiangNan = compact.contains("江南")
            val hasDeng = compact.contains('等') || compact.contains("烟雨")
            val hasErWo = compact.contains("而我")
            val hasLu = compact.contains("录过") || compact.contains("路过") || compact.contains("在等")
            if (compact.contains("而我录过了江南") || compact.contains("而我路过江南")) {
                best = maxOf(best, 0.96f)
            }
            if (hasQing && (hasJiangNan || hasDeng || hasErWo || hasLu)) {
                best = maxOf(best, 0.88f)
            }
            if (hasErWo && hasJiangNan) best = maxOf(best, 0.85f)
            if (compact.contains("如青") || compact.contains("初妆")) best = maxOf(best, 0.8f)
        }
        // 5) 林俊杰《江南》：仅有「江南」二字不够，避免抢走青花瓷
        if (song.title == "江南") {
            val jj = compact.contains("风到") || compact.contains('黏') || compact.contains('软') ||
                compact.contains("乌鹊") || compact.contains("一再重演")
            val blueClue = SongRanker.looksLikeBluePorcelainClue(compact) ||
                compact.contains("而我") || compact.contains("录过") || compact.contains('青')
            if (!jj && blueClue) {
                best = minOf(best, 0.2f)
            }
            if (!jj && compact.contains("江南") && best < 0.5f) {
                // 仅命中「江南」二字：压到阈值以下，默认不出
                best = 0.15f
            }
            if (jj) best = maxOf(best, 0.85f)
        }
        return best
    }
}
