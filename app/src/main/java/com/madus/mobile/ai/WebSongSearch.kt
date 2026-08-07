package com.madus.mobile.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class WebSearchHit(
    val title: String,
    val snippet: String = "",
    val source: String = "",
)

/**
 * 全网检索（无需额外 Key）：DuckDuckGo HTML + 百度结果标题。
 * 支持中/英/日/韩等查询模板；链路：模型猜歌 → 这里确认歌名 → B 站搜。
 */
object WebSongSearch {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val ua =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"

    suspend fun search(query: String, limit: Int = 8): List<WebSearchHit> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isEmpty()) return@withContext emptyList()
            val merged = LinkedHashMap<String, WebSearchHit>()
            runCatching { searchDuckDuckGo(q, limit) }.getOrDefault(emptyList()).forEach {
                merged.putIfAbsent(it.title, it)
            }
            runCatching { searchBaidu(q, limit) }.getOrDefault(emptyList()).forEach {
                merged.putIfAbsent(it.title, it)
            }
            merged.values.take(limit)
        }

    suspend fun searchForSongClue(
        userClue: String,
        llmTitles: List<SongCandidate>,
    ): List<WebSearchHit> {
        val queries = LinkedHashSet<String>()
        val clue = userClue.trim().take(60)
        if (clue.isNotEmpty()) {
            SongLanguage.webQueryVariants(clue, null).forEach { queries.add(it) }
        }
        llmTitles.take(4).forEach { c ->
            val t = c.title.trim()
            if (t.isEmpty() || SongGuessParser.isGarbageTitle(t)) return@forEach
            if (!looksLikeSongTitle(t) && t.length > SongLanguage.maxTitleLen(t)) return@forEach
            SongLanguage.webQueryVariants(t, c.artist).forEach { queries.add(it) }
        }
        val hits = ArrayList<WebSearchHit>()
        for (q in queries.take(6)) {
            hits += search(q, limit = 6)
            if (hits.size >= 24) break
        }
        return hits.distinctBy { it.title }.take(24)
    }

    fun candidatesFromHits(
        hits: List<WebSearchHit>,
        userClue: String = "",
    ): List<SongCandidate> {
        val out = ArrayList<SongCandidate>()
        val seen = HashSet<String>()
        val blueClue = SongRanker.looksLikeBluePorcelainClue(userClue.replace(Regex("\\s+"), ""))
        val clueKind = SongLanguage.kindOf(userClue)
        fun add(title: String, artist: String?, note: String, conf: Float) {
            val t = title.trim().trim('《', '》', '「', '」', '"', '\'')
            if (t.length < 2) return
            if (!SongLanguage.isPlausibleTitleLength(t)) return
            // 纯中文线索：中文歌名仍限制较短，防整句当歌名
            if (clueKind == SongLangKind.CHINESE && SongGuessParser.hasCjk(t) && t.length > 20) return
            if (isPlatformOrNoise(t)) return
            if (artist != null && isPlatformOrNoise(artist)) {
                // 「Bilibili - 周杰伦」这类：左边是平台，不能当歌名
                return
            }
            if (SongGuessParser.isGarbageTitle(t) || isNoiseTitle(t) || !looksLikeSongTitle(t)) return
            if (SongNameNormalizer.isJunkFragment(t)) return
            // 青花瓷线索下，丢掉裸「江南」网页噪声
            if (blueClue && (t == "江南" || t.contains("江南三部曲") || t.contains("江南八字"))) {
                return
            }
            if (!seen.add(t.lowercase())) return
            var c = conf
            if (blueClue && t.contains("青花瓷")) c = maxOf(c, 0.9f)
            val art = artist?.takeIf { !isPlatformOrNoise(it) && !SongNameNormalizer.isJunkFragment(it) }
            out.add(
                SongCandidate(
                    title = t,
                    artist = art,
                    confidence = c,
                    bilibiliQuery = listOfNotNull(t, art).joinToString(" "),
                    note = note,
                ),
            )
        }

        val book = Pattern.compile("""[《「『]([^》」』]{1,36})[》」』]""")
        val named = Pattern.compile(
            """(?:歌名|歌曲|曲名|song|title|track)[是为:：\s]*[《「『"]?([^》」』"\n，。；]{2,40})""",
            Pattern.CASE_INSENSITIVE,
        )
        // 支持较长英文 Title - Artist
        val dash = Pattern.compile(
            """([^\s\-_|/·][^\-_|/·]{0,40}?)\s*[-–—_|·]\s*([^\s\-_|/·][^\-_|/·]{0,28})""",
        )
        val titleCaseEn = Pattern.compile(
            """\b([A-Z][a-z0-9'&]+(?:\s+[A-Z][a-z0-9'&]+){0,6})\b""",
        )

        for (h in hits) {
            // 平台推荐页直接跳过
            if (isPlatformOrNoise(h.title)) continue
            val blob = h.title + " " + h.snippet
            val mBook = book.matcher(blob)
            while (mBook.find()) add(mBook.group(1) ?: continue, null, "全网《歌名》", 0.78f)
            val mNamed = named.matcher(blob)
            while (mNamed.find()) add(mNamed.group(1) ?: continue, null, "全网摘要", 0.72f)
            val mDash = dash.matcher(h.title)
            while (mDash.find()) {
                val a = (mDash.group(1) ?: "").trim()
                val b = (mDash.group(2) ?: "").trim()
                // 平台 - 歌手：两边都不是歌名
                if (isPlatformOrNoise(a) || isPlatformOrNoise(b)) continue
                if (looksLikeSongTitle(a) && b.length in 2..28) {
                    add(a, b, "全网检索", 0.76f)
                }
            }
            var t = h.title
                .replace(Regex("""【.*?】"""), "")
                .replace(Regex("""\[.*?\]"""), "")
                .trim()
            t = t.split(Regex("""\s*[-_|\u2014·]\s*""")).firstOrNull()?.trim().orEmpty()
            // 绝不要把「我的bilibili推荐」截成歌名
            if (isPlatformOrNoise(t)) continue
            if (SongGuessParser.hasCjk(t) && t.length in 2..16 && looksLikeSongTitle(t)) {
                add(t, null, "全网标题", 0.55f)
            }
            // 英文/拉丁：Title Case 短语
            if (clueKind != SongLangKind.CHINESE || !SongGuessParser.hasCjk(userClue)) {
                val mEn = titleCaseEn.matcher(h.title)
                while (mEn.find()) {
                    val p = (mEn.group(1) ?: "").trim()
                    if (p.split(Regex("\\s+")).size >= 2 || p.length >= 5) {
                        add(p, null, "全网英文标题", 0.58f)
                    }
                }
            }
            // 日/韩：标题截断后仍像歌名
            if (SongLanguage.kindOf(t) == SongLangKind.JAPANESE ||
                SongLanguage.kindOf(t) == SongLangKind.KOREAN
            ) {
                if (t.length in 2..28 && looksLikeSongTitle(t)) {
                    add(t, null, "全网外语标题", 0.56f)
                }
            }
        }
        return out.take(10)
    }

    fun formatSnippets(hits: List<WebSearchHit>, max: Int = 12): String =
        hits.take(max).joinToString("\n") { h ->
            buildString {
                append("- ")
                append(h.title)
                if (h.snippet.isNotBlank()) {
                    append(" | ")
                    append(h.snippet.take(80))
                }
            }
        }

    private fun looksLikeSongTitle(s: String): Boolean {
        if (!SongLanguage.isPlausibleTitleLength(s)) return false
        if (isPlatformOrNoise(s)) return false
        if (SongNameNormalizer.isJunkFragment(s)) return false
        val low = s.lowercase()
        if (s.contains("是什么") || s.contains("歌词大全") || s.contains("有声") ||
            s.contains("小说") || s.contains("八字") || s.contains("下载") ||
            s.contains("全集") || s.contains("百科") || s.contains("怎么") ||
            s.contains("推荐") || s.contains("热门") || s.contains("排行") ||
            (s.contains("主题曲") && s.length > 10) ||
            low.contains("lyrics meaning") || low.contains("what song") ||
            low.contains("how to") || low.contains("download") ||
            low.contains("wikipedia") || low.contains("chord")
        ) {
            return false
        }
        // 整句说明文（过长拉丁）
        if (!SongGuessParser.hasCjk(s) && s.split(Regex("\\s+")).size >= 8) return false
        return true
    }

    private fun isNoiseTitle(s: String): Boolean = isPlatformOrNoise(s)

    /** 平台名 / 推荐页 / 工具站，绝不能当歌名 */
    fun isPlatformOrNoise(s: String): Boolean {
        val t = s.trim().lowercase()
        if (t.isEmpty()) return true
        val n = listOf(
            "bilibili", "b站", "哔哩", "bilibil", "bili",
            "youtube", "youtu", "网易云", "qq音乐", "酷狗", "酷我", "spotify",
            "抖音", "快手", "apple music", "itunes",
            "百度", "知乎", "百科", "词典", "翻译", "是什么意思",
            "有声书", "小说", "命理", "八字", "登录", "首页", "地图",
            "歌曲识别", "哼唱识歌", "在线识别", "识别歌曲", "听歌识曲",
            "小羿", "cp.baidu", "shazam", "acrcloud", "工具",
            "我的推荐", "推荐页", "热门", "new year", // 弱噪声，常被误抽
        )
        return n.any { t == it || t.contains(it) }
    }

    private fun searchDuckDuckGo(query: String, limit: Int): List<WebSearchHit> {
        val url = "https://html.duckduckgo.com/html/?q=" +
            URLEncoder.encode(query, "UTF-8")
        val req = Request.Builder().url(url).header("User-Agent", ua).get().build()
        val html = http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            resp.body?.string().orEmpty()
        }
        val titles = ArrayList<String>()
        val p = Pattern.compile(
            """class="result__a"[^>]*>([^<]{2,80})</a>""",
            Pattern.CASE_INSENSITIVE,
        )
        val m = p.matcher(html)
        while (m.find() && titles.size < limit) {
            titles.add(decodeHtml(m.group(1)))
        }
        return titles.map { WebSearchHit(title = it, source = "ddg") }
    }

    private fun searchBaidu(query: String, limit: Int): List<WebSearchHit> {
        val url = "https://www.baidu.com/s?wd=" + URLEncoder.encode(query, "UTF-8")
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", ua)
            // 中英日韩都接受，便于外语歌结果
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,ja;q=0.7,ko;q=0.6")
            .get()
            .build()
        val html = http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            resp.body?.string().orEmpty()
        }
        val hits = ArrayList<WebSearchHit>()
        val p1 = Pattern.compile("""data-title="([^"]{2,60})"""")
        val m1 = p1.matcher(html)
        while (m1.find() && hits.size < limit) {
            hits.add(WebSearchHit(title = decodeHtml(m1.group(1)), source = "baidu"))
        }
        val p2 = Pattern.compile(
            """<h3[^>]*>\s*<a[^>]*>(.*?)</a>""",
            Pattern.CASE_INSENSITIVE or Pattern.DOTALL,
        )
        val m2 = p2.matcher(html)
        while (m2.find() && hits.size < limit * 2) {
            val raw = m2.group(1).replace(Regex("<[^>]+>"), "").trim()
            if (raw.length in 2..60) {
                hits.add(WebSearchHit(title = decodeHtml(raw), source = "baidu"))
            }
        }
        return hits.distinctBy { it.title }.take(limit)
    }

    private fun decodeHtml(s: String): String =
        s.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
}
