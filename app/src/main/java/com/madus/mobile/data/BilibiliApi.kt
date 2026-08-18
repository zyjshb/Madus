package com.madus.mobile.data

import android.util.Log
import com.madus.mobile.domain.MusicSourceType
import com.madus.mobile.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.TreeMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

/**
 * Direct Bilibili HTTP API.
 * 播放链路：view 取 cid → playurl 多策略取流（优先 html5 mp4，再 dash audio）。
 */
class BilibiliApi(
    private val cookieProvider: suspend () -> String,
) {
    data class NavInfo(
        val isLogin: Boolean,
        val uname: String = "",
        val mid: String = "",
        /** 头像 URL */
        val face: String = "",
    )

    data class FavFolder(
        val id: String,
        val title: String,
        val cover: String = "",
        val count: Int = 0,
    )

    /** 稿件/分 P 上创作者标注的 BGM（B 站官方「识曲」数据来源） */
    data class BgmTag(
        val tagName: String,
        val musicId: String = "",
        val title: String = "",
        val artist: String = "",
    )

    /** 访客 buvid 等，提高 playurl 成功率 */
    private val guestCookie = AtomicReference("")

    /** bvid -> (bvid, aid, cid) 缓存，避免每次 view 再打一轮 */
    private val viewMetaCache = java.util.concurrent.ConcurrentHashMap<String, Triple<String, String, String>>()

    /** mid -> face，短视频头像 / UP 页复用 */
    private val ownerFaceCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** WBI 签名 mixin key 缓存（约每日轮换） */
    private val wbiMixinKey = AtomicReference("")
    private val wbiMixinAt = AtomicLong(0L)

    suspend fun ensureGuestCookies() = withContext(Dispatchers.IO) {
        if (guestCookie.get().isNotBlank()) return@withContext
        runCatching {
            val conn = open("https://www.bilibili.com/", cookie = "")
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = true
            conn.connect()
            val set = conn.headerFields["Set-Cookie"].orEmpty()
            val jar = set.mapNotNull { line ->
                val part = line.substringBefore(';').trim()
                if (part.contains('=')) part else null
            }
            if (jar.isNotEmpty()) {
                guestCookie.set(jar.joinToString("; "))
                Log.i(TAG, "guest cookies: ${jar.size} keys")
            }
            conn.inputStream?.close()
        }.onFailure { Log.w(TAG, "guest cookie fail: ${it.message}") }
    }

    private suspend fun mergedCookie(): String {
        ensureGuestCookies()
        val user = cookieProvider().trim()
        val guest = guestCookie.get()
        return when {
            user.isBlank() -> guest
            guest.isBlank() -> user
            else -> "$user; $guest"
        }
    }

    suspend fun nav(cookie: String): NavInfo = withContext(Dispatchers.IO) {
        val json = getJson("https://api.bilibili.com/x/web-interface/nav", cookie)
        val data = json.optJSONObject("data") ?: JSONObject()
        NavInfo(
            isLogin = data.optBoolean("isLogin", false),
            uname = data.optString("uname", ""),
            mid = data.opt("mid")?.toString()?.takeIf { it != "null" }.orEmpty(),
            face = normalizeUrl(data.optString("face", "")).orEmpty(),
        )
    }

    /**
     * 搜索联想（输入「老人」→「老人与海」「老人と海」…）
     */
    suspend fun searchSuggest(term: String, limit: Int = 10): List<String> = withContext(Dispatchers.IO) {
        val t = term.trim()
        if (t.isEmpty()) return@withContext emptyList()
        val encoded = URLEncoder.encode(t, "UTF-8")
        val url =
            "https://s.search.bilibili.com/main/suggest?" +
                "term=$encoded&main_ver=v1&highlight=&userid=0&bangumi_acc_num=0&special_acc_num=0" +
                "&topic_acc_num=0&upuser_acc_num=0&tag_num=10"
        val text = runCatching {
            val conn = open(url, mergedCookie(), referer = "https://search.bilibili.com/")
            conn.requestMethod = "GET"
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            stream?.use { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() }.orEmpty()
        }.getOrDefault("")
        if (text.isBlank()) return@withContext emptyList()
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return@withContext emptyList()
        // 结构：result.tag[].value  或 result 为数组
        val out = linkedSetOf<String>()
        val result = json.opt("result")
        when (result) {
            is JSONObject -> {
                val tag = result.optJSONArray("tag") ?: JSONArray()
                for (i in 0 until tag.length()) {
                    if (out.size >= limit) break
                    val item = tag.optJSONObject(i) ?: continue
                    val v = stripHtml(item.optString("value", item.optString("name", ""))).trim()
                    if (v.isNotBlank()) out.add(v)
                }
            }
            is JSONArray -> {
                for (i in 0 until result.length()) {
                    if (out.size >= limit) break
                    val item = result.optJSONObject(i) ?: continue
                    val v = stripHtml(item.optString("value", item.optString("name", ""))).trim()
                    if (v.isNotBlank()) out.add(v)
                }
            }
        }
        out.toList()
    }

    data class SearchPage(
        val tracks: List<Track>,
        val hasMore: Boolean,
        val page: Int,
        val total: Int = 0,
        /** 实际命中关键词（纠错后可能与输入不同） */
        val keywordUsed: String = "",
    )

    /**
     * 全站视频搜索一页，尽量对齐 B 站网页：
     * - 默认 order=totalrank（综合）
     * - 每页 42 条（与 search.bilibili.com 一致）
     * - 优先 WBI 签名接口；失败再走经典接口
     * - 支持 page 翻页（加载更多）
     */
    suspend fun searchPage(
        keyword: String,
        page: Int = 1,
        pageSize: Int = 42,
        order: String = "totalrank",
        /** 首页空结果时是否走联想/软化纠错（翻页勿开） */
        allowCorrect: Boolean = page <= 1,
    ): SearchPage = withContext(Dispatchers.IO) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return@withContext SearchPage(emptyList(), false, page)
        val cookie = mergedCookieWithSystem().ifBlank { mergedCookie() }
        val pn = page.coerceAtLeast(1)
        val ps = pageSize.coerceIn(1, 50)

        parseBvid(kw)?.let { bvid ->
            val tracks = viewTracks(bvid, cookie)
            return@withContext SearchPage(
                tracks = tracks,
                hasMore = false,
                page = 1,
                total = tracks.size,
                keywordUsed = bvid,
            )
        }

        suspend fun fetchOnce(q: String, pageNo: Int, ord: String): SearchPage? {
            val referer =
                "https://search.bilibili.com/all?keyword=${URLEncoder.encode(q, "UTF-8")}"
            val params = linkedMapOf(
                    "category_id" to "",
                    "search_type" to "video",
                    "ad_resource" to "5654",
                    "__refresh__" to "true",
                    "_extra" to "",
                    "context" to "",
                    "page" to pageNo.toString(),
                    "page_size" to ps.toString(),
                    "order" to ord,
                    "duration" to "",
                    "from_source" to "",
                    "from_spmid" to "333.337",
                    "platform" to "pc",
                    "highlight" to "1",
                    "single_column" to "0",
                    "keyword" to q,
                    "qv_id" to "",
                    "source_tag" to "3",
                    "gaia_vtoken" to "",
                    "dynamic_offset" to "0",
                    "web_location" to "1430654",
                )
            val wbi = runCatching {
                val signed = signWbiQuery(params, cookie)
                getJson(
                    "https://api.bilibili.com/x/web-interface/wbi/search/type?$signed",
                    cookie,
                    referer = referer,
                )
            }.getOrNull()
            val wbiCode = wbi?.optInt("code", -1) ?: -1
            if (wbiCode == -412 || wbiCode == 412 || wbiCode == -352) {
                wbiMixinKey.set("")
                wbiMixinAt.set(0L)
                val retry = runCatching {
                    val signed = signWbiQuery(params, cookie)
                    getJson(
                        "https://api.bilibili.com/x/web-interface/wbi/search/type?$signed",
                        cookie,
                        referer = referer,
                    )
                }.getOrNull()
                parseSearchJson(retry, pageNo, ps, q)?.let { return it }
            } else {
                parseSearchJson(wbi, pageNo, ps, q)?.let { return it }
            }

            // 2) 经典无签名
            val encoded = URLEncoder.encode(q, "UTF-8")
            val classic = runCatching {
                getJson(
                    "https://api.bilibili.com/x/web-interface/search/type" +
                        "?search_type=video&keyword=$encoded&page=$pageNo&page_size=$ps&order=$ord",
                    cookie,
                    referer = referer,
                )
            }.getOrNull()
            return parseSearchJson(classic, pageNo, ps, q)
        }

        // 1) 原词 + 综合排序
        var pageResult = fetchOnce(kw, pn, order)
        // 2) 首页仍空：换 order 再试（仅首页）
        if (allowCorrect && (pageResult == null || pageResult.tracks.isEmpty())) {
            for (ord in listOf("click", "pubdate")) {
                if (ord == order) continue
                pageResult = fetchOnce(kw, pn, ord)
                if (pageResult != null && pageResult.tracks.isNotEmpty()) break
            }
        }
        // 3) 首页空 → 联想纠错
        if (allowCorrect && (pageResult == null || pageResult.tracks.isEmpty())) {
            val tips = runCatching { searchSuggest(kw, limit = 6) }.getOrDefault(emptyList())
            for (tip in tips) {
                if (tip.equals(kw, ignoreCase = true)) continue
                pageResult = fetchOnce(tip, pn, order)
                if (pageResult != null && pageResult.tracks.isNotEmpty()) break
            }
        }
        // 4) 仍空：去掉标点/空格
        if (allowCorrect && (pageResult == null || pageResult.tracks.isEmpty())) {
            val soft = kw.replace(Regex("[\\s\\p{Punct}，。！？、·…]+"), "")
            if (soft.length >= 2 && soft != kw) {
                pageResult = fetchOnce(soft, pn, order)
            }
        }
        pageResult ?: SearchPage(emptyList(), false, pn, keywordUsed = kw)
    }

    private fun parseSearchJson(
        json: JSONObject?,
        pageNo: Int,
        pageSize: Int,
        keywordUsed: String,
    ): SearchPage? {
        if (json == null) return null
        val code = json.optInt("code", -1)
        if (code != 0) {
            Log.w(TAG, "search code=$code ${json.optString("message")}")
            return null
        }
        val data = json.optJSONObject("data") ?: return null
        val tracks = parseSearchData(data)
        val total = data.optInt("numResults", 0)
            .coerceAtLeast(data.optInt("numresults", 0))
        val numPages = data.optInt("numPages", 0)
            .coerceAtLeast(data.optInt("numpages", 0))
        val hasMore = when {
            numPages > 0 -> pageNo < numPages
            total > 0 -> pageNo * pageSize < total
            else -> tracks.size >= pageSize
        }
        return SearchPage(
            tracks = tracks,
            hasMore = hasMore && tracks.isNotEmpty(),
            page = pageNo,
            total = total,
            keywordUsed = keywordUsed,
        )
    }

    private fun parseSearchData(data: JSONObject): List<Track> {
        val buckets = mutableListOf<JSONArray>()
        when (val raw = data.opt("result")) {
            is JSONArray -> buckets.add(raw)
            is JSONObject -> {
                raw.optJSONArray("video")?.let { buckets.add(it) }
                raw.optJSONArray("data")?.let { buckets.add(it) }
            }
        }
        data.optJSONArray("items")?.let { buckets.add(it) }
        val flat = buckets.flatMap { parseSearchResultArray(it) }
        if (flat.isNotEmpty()) return flat.distinctBy { it.id }
        val grouped = data.optJSONArray("result") ?: return emptyList()
        return buildList {
            for (i in 0 until grouped.length()) {
                val block = grouped.optJSONObject(i) ?: continue
                val kind = block.optString("result_type", block.optString("type", "video"))
                if (kind.isNotBlank() && kind != "video") continue
                val nested = block.optJSONArray("data") ?: block.optJSONArray("items") ?: continue
                addAll(parseSearchResultArray(nested))
            }
        }.distinctBy { it.id }
    }

    private fun parseSearchResultArray(result: JSONArray): List<Track> = buildList {
        for (i in 0 until result.length()) {
            val item = result.optJSONObject(i) ?: continue
            val nested = item.optJSONArray("data") ?: item.optJSONArray("items")
            if (nested != null && item.optString("bvid").isBlank()) {
                addAll(parseSearchResultArray(nested))
                continue
            }
            val type = item.optString("type", item.optString("result_type", "video"))
            if (type.isNotBlank() && type != "video" && item.optString("bvid").isBlank()) continue
            val aid = item.opt("aid")?.toString()?.takeIf { it != "null" && it != "0" }.orEmpty()
            val bvid = item.optString("bvid", "").ifBlank {
                parseBvid(item.optString("arcurl", item.optString("uri", ""))).orEmpty()
            }
            if (bvid.isBlank() && aid.isBlank()) continue
            val title = stripHtml(item.optString("title", bvid.ifBlank { aid }))
            val author = item.optString("author", item.optString("uname", "Bilibili"))
            val mid = item.opt("mid")?.toString()?.takeIf { it != "null" }.orEmpty()
            val cover = normalizeUrl(
                item.optString("pic").ifBlank { item.optString("cover") },
            )
            val durationMs = parseDurationToMs(item.optString("duration", "0"))
            val pageCount = item.optInt("videos", 1).coerceAtLeast(1)
            val album = if (pageCount > 1) "合集·${pageCount}P" else "Bilibili"
            val displayTitle = if (pageCount > 1) "$title · ${pageCount}P" else title
            add(
                Track(
                    id = bvid.ifBlank { "av$aid" },
                    title = displayTitle,
                    artist = author,
                    album = album,
                    coverUrl = cover,
                    durationMs = durationMs,
                    source = MusicSourceType.BILIBILI,
                    bvid = bvid,
                    aid = aid,
                    ownerMid = mid,
                    pageCount = pageCount,
                    categoryId = item.optInt("typeid", item.optInt("tid", 0)),
                    categoryName = stripHtml(item.optString("typename", item.optString("tname", ""))),
                    tags = parseTags(item),
                ),
            )
        }
    }

    /** 兼容旧调用：取首页若干条 */
    suspend fun search(keyword: String, limit: Int = 20): List<Track> {
        val page = searchPage(
            keyword = keyword,
            page = 1,
            pageSize = limit.coerceIn(4, 50),
            allowCorrect = true,
        )
        return page.tracks.take(limit)
    }

    /**
     * B 站官方 BGM 标签识曲：读稿件/分 P 上创作者标注的 BGM 元数据。
     *
     * 说明：这不是音频指纹接口，只对标注了 BGM 标签的稿件有效；
     * progressMs 仅作调用语义保留，官方接口不按播放进度识曲。
     */
    suspend fun recognizeBgm(
        bvid: String,
        cid: String = "",
        progressMs: Long = 0L,
    ): List<BgmTag> = withContext(Dispatchers.IO) {
        val bv = parseBvid(bvid) ?: return@withContext emptyList()
        val cookie = mergedCookie()
        val out = linkedMapOf<String, BgmTag>()
        val query = buildString {
            append("bvid=").append(URLEncoder.encode(bv, "UTF-8"))
            if (cid.isNotBlank()) {
                append("&cid=").append(URLEncoder.encode(cid, "UTF-8"))
            }
        }
        val json = runCatching {
            getJson(
                "https://api.bilibili.com/x/web-interface/view/detail/tag?$query",
                cookie,
                referer = "https://www.bilibili.com/video/$bv",
            )
        }.getOrNull()
        if (json != null && json.optInt("code", -1) == 0) {
            val data = json.opt("data")
            val arr = when (data) {
                is JSONArray -> data
                is JSONObject -> {
                    data.optJSONArray("tags")
                        ?: data.optJSONArray("data")
                        ?: data.optJSONArray("list")
                        ?: JSONArray()
                }
                else -> JSONArray()
            }
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val tagType = item.optString("tag_type", "")
                if (tagType.isNotBlank() && tagType != "bgm") continue
                val tagName = stripHtml(item.optString("tag_name", "")).trim()
                if (tagName.isBlank()) continue
                val parsed = parseBgmTagName(tagName)
                val key = parsed.first.ifBlank { tagName }
                if (key.isBlank() || out.containsKey(key)) continue
                out[key] = BgmTag(
                    tagName = tagName,
                    musicId = item.optString("music_id", ""),
                    title = parsed.first,
                    artist = parsed.second,
                )
            }
        }

        // 补充 player/v2 的 bgm_info（部分稿件在这里也有创作者标注）
        if (cid.isNotBlank() && out.size < 3) {
            val pv2 = runCatching {
                getJson(
                    "https://api.bilibili.com/x/web-interface/player/v2?bvid=$bv&cid=${URLEncoder.encode(cid, "UTF-8")}",
                    cookie,
                    referer = "https://www.bilibili.com/video/$bv",
                )
            }.getOrNull()
            val info = pv2?.optJSONObject("data")?.optJSONObject("bgm_info")
            val title = stripHtml(info?.optString("music_title", "").orEmpty()).trim()
            if (title.isNotBlank()) {
                val parsed = parseBgmTagName(title)
                val key = parsed.first.ifBlank { title }
                if (key.isNotBlank() && !out.containsKey(key)) {
                    out[key] = BgmTag(
                        tagName = title,
                        musicId = info?.optString("music_id", "").orEmpty(),
                        title = parsed.first,
                        artist = parsed.second,
                    )
                }
            }
        }

        out.values.toList().take(5)
    }

    /** 从「发现《Other Side》」「BGM: xxx - 歌手」等标签里抽歌名/歌手 */
    private fun parseBgmTagName(raw: String): Pair<String, String> {
        val book = Regex("""《([^》]+)》""").find(raw)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (book.isNotBlank()) {
            val after = raw.substringAfter(book, "").trim()
            val artist = after
                .trimStart('-', '—', '–', '|', '：', ':')
                .removePrefix("歌手")
                .trimStart('：', ':')
                .trim()
            return book to artist
        }
        val parts = raw.split(Regex("""\s+[-—–|]\s+""")).map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size >= 2 && parts[0].length in 1..60) {
            return parts[0] to parts.drop(1).joinToString(" ")
        }
        return raw.trim() to ""
    }

    /**
     * 展开稿件：多 P 全部分集 + 若有 ugc_season 合集则拉整季。
     * 用于「收藏整部合集 / 整夹进队列」。
     */
    suspend fun expandSeries(track: Track): List<Track> = withContext(Dispatchers.IO) {
        val cookie = mergedCookie()
        val bvid = track.bvid.ifBlank { parseBvid(track.id).orEmpty() }
        if (bvid.isBlank()) return@withContext listOf(track)
        val season = expandUgcSeason(bvid, cookie)
        if (season.size > 1) return@withContext season
        val parts = viewTracks(bvid, cookie)
        if (parts.isNotEmpty()) parts else listOf(track)
    }

    /** ugc 合集（系列视频） */
    private fun expandUgcSeason(bvid: String, cookie: String): List<Track> {
        val url = "https://api.bilibili.com/x/web-interface/view?bvid=${URLEncoder.encode(bvid, "UTF-8")}"
        val data = runCatching { getJson(url, cookie).optJSONObject("data") }.getOrNull()
            ?: return emptyList()
        val season = data.optJSONObject("ugc_season") ?: return emptyList()
        val seasonTitle = season.optString("title", "合集")
        val sections = season.optJSONArray("sections") ?: return emptyList()
        return buildList {
            for (si in 0 until sections.length()) {
                val sec = sections.optJSONObject(si) ?: continue
                val eps = sec.optJSONArray("episodes") ?: continue
                for (ei in 0 until eps.length()) {
                    val ep = eps.optJSONObject(ei) ?: continue
                    val bv = ep.optString("bvid", "")
                    if (bv.isBlank()) continue
                    val page = ep.optJSONObject("page")
                    val cid = page?.opt("cid")?.toString().orEmpty()
                        .ifBlank { ep.opt("cid")?.toString().orEmpty() }
                    val aid = ep.opt("aid")?.toString()?.takeIf { it != "null" }.orEmpty()
                    val part = page?.optString("part", "").orEmpty()
                        .ifBlank { ep.optString("title", "") }
                    val title = ep.optString("title", part).ifBlank { bv }
                    val owner = data.optJSONObject("owner")?.optString("name", "Bilibili") ?: "Bilibili"
                    add(
                        Track(
                            id = if (cid.isNotBlank()) "${bv}_$cid" else bv,
                            title = title,
                            artist = owner,
                            album = seasonTitle,
                            coverUrl = normalizeUrl(
                                ep.optString("cover", data.optString("pic", "")),
                            ),
                            durationMs = (page?.optLong("duration", 0L) ?: ep.optLong("duration", 0L))
                                .let { if (it > 10_000) it else it * 1000 },
                            source = MusicSourceType.BILIBILI,
                            bvid = bv,
                            aid = aid,
                            cid = cid,
                            pageCount = 1,
                        ),
                    )
                }
            }
        }
    }

    suspend fun favFolders(): List<FavFolder> = withContext(Dispatchers.IO) {
        val cookie = mergedCookie()
        if (!cookie.contains("SESSDATA")) return@withContext emptyList()
        val nav = nav(cookie)
        val mid = nav.mid
        if (mid.isBlank()) return@withContext emptyList()
        val url =
            "https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=${URLEncoder.encode(mid, "UTF-8")}"
        val json = getJson(url, cookie, referer = "https://space.bilibili.com/$mid/favlist")
        val list = json.optJSONObject("data")?.optJSONArray("list") ?: JSONArray()
        val folders = buildList {
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: continue
                val id = item.opt("id")?.toString()?.takeIf { it != "null" }.orEmpty()
                val title = item.optString("title", "")
                if (id.isBlank() || title.isBlank()) continue
                // list-all 有时 cover 为空，下面用夹内第一首补
                val cover = normalizeUrl(
                    item.optString("cover", "")
                        .ifBlank { item.optString("cover_url", "") }
                        .ifBlank {
                            item.optJSONObject("cover")?.optString("url", "").orEmpty()
                        },
                ).orEmpty()
                add(
                    FavFolder(
                        id = id,
                        title = title,
                        cover = cover,
                        count = item.optInt("media_count", item.optInt("count", 0)),
                    ),
                )
            }
        }
        // 封面为空时取夹内最新一条的封面（并行上限控制：最多 8 个空封面夹）
        val needFill = folders.filter { it.cover.isBlank() && it.count > 0 }.take(8)
        if (needFill.isEmpty()) return@withContext folders
        val filled = needFill.associate { f ->
            val pic = runCatching {
                favTracksPage(f.id, page = 1, pageSize = 1).tracks.firstOrNull()?.coverUrl.orEmpty()
            }.getOrDefault("")
            f.id to pic
        }
        folders.map { f ->
            val extra = filled[f.id].orEmpty()
            if (f.cover.isBlank() && extra.isNotBlank()) f.copy(cover = extra) else f
        }
    }

    /**
     * 新建 B 站收藏夹（需登录 + csrf）。
     * @return 成功返回夹 id；失败 null（错误信息看 Log / 调用方 toast）
     */
    suspend fun createFavFolder(title: String, privacy: Int = 0): Pair<String?, String?> =
        withContext(Dispatchers.IO) {
            val name = title.trim()
            if (name.isBlank()) return@withContext null to "名称不能为空"
            val cookie = mergedCookieWithSystem()
            if (!cookie.contains("SESSDATA")) return@withContext null to "请先登录 B 站"
            val csrf = extractCookieValue(cookie, "bili_jct")
            if (csrf.isBlank()) return@withContext null to "缺少 bili_jct，请重新登录"
            val body =
                "title=${URLEncoder.encode(name, "UTF-8")}" +
                    "&privacy=$privacy" +
                    "&csrf=${URLEncoder.encode(csrf, "UTF-8")}"
            val json = postForm(
                "https://api.bilibili.com/x/v3/fav/folder/add",
                body,
                cookie,
                referer = "https://www.bilibili.com",
            )
            val code = json.optInt("code", -1)
            if (code != 0) {
                return@withContext null to json.optString("message", "创建失败 code=$code")
            }
            val id = json.optJSONObject("data")?.opt("id")?.toString()
                ?.takeIf { it != "null" && it.isNotBlank() }
            if (id.isNullOrBlank()) null to "创建成功但未返回夹 id" else id to null
        }

    /**
     * 收藏夹单页（默认 40 首/页，接口 ps 最大常按 20 时内部拼两页）。
     * @param excludeInvalid 默认 true：列表不展示已失效/已删除稿（仍可走 purge 从 B 站真正删掉）
     */
    suspend fun favTracksPage(
        mediaId: String,
        page: Int = 1,
        pageSize: Int = 40,
        excludeInvalid: Boolean = true,
    ): FavPage = withContext(Dispatchers.IO) {
        val cookie = mergedCookieWithSystem().ifBlank { mergedCookie() }
        if (!cookie.contains("SESSDATA") || mediaId.isBlank()) {
            return@withContext FavPage(emptyList(), false, page)
        }
        val want = pageSize.coerceIn(1, 40)
        val apiPs = 20
        // 用户页 page 按 want 计；内部用 apiPs 取
        val startApiPn = ((page - 1) * want) / apiPs + 1
        // 过滤失效后本页可能不足 want，多拉几页 API 补满
        val needApiPages = (((want + apiPs - 1) / apiPs) + if (excludeInvalid) 4 else 0).coerceAtLeast(1)
        val out = mutableListOf<Track>()
        var total = 0
        var hasMore = false
        var lastMedias = 0
        var invalidSkipped = 0
        for (i in 0 until needApiPages) {
            val pn = startApiPn + i
            val url =
                "https://api.bilibili.com/x/v3/fav/resource/list" +
                    "?media_id=${URLEncoder.encode(mediaId.trim(), "UTF-8")}" +
                    "&pn=$pn&ps=$apiPs&keyword=&order=mtime&type=0&tid=0&platform=web"
            val json = getJson(url, cookie, referer = "https://www.bilibili.com")
            val data = json.optJSONObject("data")
            val medias = data?.optJSONArray("medias")
            if (medias == null || medias.length() == 0) {
                hasMore = false
                break
            }
            lastMedias = medias.length()
            total = data.optJSONObject("info")?.optInt("media_count", 0)
                ?: data.optInt("media_count", total)
            for (j in 0 until medias.length()) {
                val raw = medias.optJSONObject(j) ?: continue
                if (excludeInvalid && isInvalidFavJson(raw)) {
                    invalidSkipped++
                    continue
                }
                parseFavMedia(raw)?.let { t ->
                    if (excludeInvalid && isInvalidTrack(t)) {
                        invalidSkipped++
                    } else {
                        out.add(t)
                    }
                }
                if (out.size >= want) break
            }
            hasMore = when {
                data.has("has_more") -> data.optBoolean("has_more", false)
                else -> lastMedias >= apiPs
            }
            if (!hasMore || out.size >= want) break
        }
        // 用户页还有没有下一页（按接口 total 粗算；过滤失效后 hasMore 以接口为准）
        val loadedThrough = (page - 1) * want + out.size + invalidSkipped
        if (total > 0) hasMore = loadedThrough < total || hasMore
        FavPage(
            tracks = out.take(want),
            hasMore = hasMore && out.isNotEmpty(),
            page = page,
            total = (total - invalidSkipped).coerceAtLeast(out.size),
        )
    }

    private fun parseFavMedia(m: JSONObject?): Track? {
        if (m == null) return null
        if (m.has("type") && m.optInt("type") != 2) return null
        val bvid = m.optString("bvid", "")
        val title = stripHtml(m.optString("title", bvid.ifBlank { "已失效视频" }))
        val invalid = isInvalidFavJson(m)
        if (bvid.isBlank() && !invalid) return null
        val cover = normalizeUrl(
            m.optString("cover", "")
                .ifBlank { m.optString("pic", "") }
                .ifBlank { m.optJSONObject("cover")?.optString("url", "").orEmpty() },
        )
        val upperObj = m.optJSONObject("upper")
        val upper = upperObj?.optString("name", "Bilibili") ?: "Bilibili"
        val mid = upperObj?.opt("mid")?.toString()?.takeIf { it != "null" }.orEmpty()
        val durationMs = m.optLong("duration", 0L) * 1000
        val aid = m.opt("aid")?.toString()?.takeIf { it != "null" && it.isNotBlank() }
            ?: m.opt("id")?.toString()?.takeIf { it != "null" }.orEmpty()
        val cid = m.opt("cid")?.toString()?.takeIf { it != "null" }.orEmpty()
        if (invalid) {
            val idBase = bvid.ifBlank { aid.ifBlank { "invalid" } }
            return Track(
                id = "invalid-$idBase",
                title = title.ifBlank { "已失效视频" },
                artist = "已失效",
                album = "失效",
                coverUrl = cover,
                durationMs = 0L,
                source = MusicSourceType.BILIBILI,
                bvid = bvid,
                aid = aid.filter { it.isDigit() },
            )
        }
        return Track(
            id = if (cid.isNotBlank()) "${bvid}_$cid" else bvid,
            title = title,
            artist = upper,
            album = "收藏",
            coverUrl = cover,
            durationMs = durationMs,
            source = MusicSourceType.BILIBILI,
            bvid = bvid,
            aid = aid,
            cid = cid,
            ownerMid = mid,
        )
    }

    /** 接口 JSON 判定失效稿 */
    private fun isInvalidFavJson(m: JSONObject): Boolean {
        val title = stripHtml(m.optString("title", ""))
        val attr = m.optInt("attr", 0)
        val bvid = m.optString("bvid", "")
        val cover = m.optString("cover", "").ifBlank { m.optString("pic", "") }
        // attr 最低位为 1 常表示失效；标题/无 bvid/空封面+零时长 兜底
        if ((attr and 1) != 0) return true
        if (isInvalidFavTitle(title)) return true
        if (bvid.isBlank() && isInvalidFavTitle(title.ifBlank { "已失效视频" })) return true
        // 失效稿常见：无 bvid 或标题为空
        if (bvid.isBlank() && title.isBlank()) return true
        if (bvid.isBlank() && cover.isBlank() && m.optLong("duration", 0L) <= 0L) return true
        return false
    }

    fun isInvalidTrack(t: Track): Boolean {
        if (t.album == "失效" || t.artist == "已失效") return true
        if (t.id.startsWith("invalid-")) return true
        if (isInvalidFavTitle(t.title)) return true
        if (t.bvid.isBlank() && t.aid.isBlank()) return true
        return false
    }

    /**
     * 拉取收藏夹全部稿件（分页）。
     * @param maxPages 页数上限；每页 20 条。默认 100 页 ≈ 2000 首。
     *                 传 [Int.MAX_VALUE] 或很大值时仍受安全上限 500 页保护。
     */
    suspend fun favTracks(mediaId: String, maxPages: Int = 100): List<Track> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Track>()
        val pageCap = maxPages.coerceIn(1, 500)
        // 用户页 40 条 ≈ 2 个 API 页；这里按 API 页 cap
        var userPage = 1
        var apiPages = 0
        while (apiPages < pageCap) {
            val page = favTracksPage(mediaId, page = userPage, pageSize = 40)
            if (page.tracks.isEmpty()) break
            out.addAll(page.tracks)
            apiPages += 2
            if (!page.hasMore) break
            userPage++
        }
        out
    }

    /**
     * @param preferredQn 音质/画质偏好：0=尽量高；16 省流；64 标准；80 较高。
     * @param videoMode true=优先可播视频画面（progressive mp4）；false=纯音频。
     */
    suspend fun resolvePlayUrl(
        track: Track,
        preferredQn: Int = 64,
        videoMode: Boolean = false,
    ): Track = withContext(Dispatchers.IO) {
        val cookie = mergedCookie()
        var bvid = track.bvid.ifBlank { parseBvid(track.id).orEmpty() }
        var aid = track.aid
        var cid = track.cid
        var ownerMid = track.ownerMid.filter { it.isDigit() }
        var ownerFace = track.ownerFace.ifBlank {
            ownerFaceCache[ownerMid].orEmpty()
        }
        var ownerName = track.artist

        // 多 P 的 id 形如 BV1xx_cid
        if (cid.isBlank() && track.id.contains('_')) {
            val after = track.id.substringAfter('_', "")
            if (after.all { it.isDigit() }) cid = after
        }

        // 缓存命中可跳过 view 的 cid/aid
        if (bvid.isNotBlank()) {
            viewMetaCache[bvid]?.let { (cb, ca, cc) ->
                if (bvid.isBlank()) bvid = cb
                if (aid.isBlank()) aid = ca
                if (cid.isBlank()) cid = cc
            }
        }

        val needPlayMeta = cid.isBlank() || bvid.isBlank() || aid.isBlank()
        val needOwner = ownerFace.isBlank() || ownerMid.isBlank()
        if (needPlayMeta || needOwner) {
            if (bvid.isBlank() && aid.isBlank()) {
                if (needPlayMeta) error("缺少 bvid")
            } else {
                val viewUrl = buildString {
                    append("https://api.bilibili.com/x/web-interface/view?")
                    if (bvid.isNotBlank()) append("bvid=${URLEncoder.encode(bvid, "UTF-8")}")
                    else append("aid=${URLEncoder.encode(aid, "UTF-8")}")
                }
                val viewRoot = runCatching { getJson(viewUrl, cookie) }.getOrNull()
                if (viewRoot != null && viewRoot.optInt("code", -1) == 0) {
                    val view = viewRoot.optJSONObject("data")
                    if (view != null) {
                        bvid = view.optString("bvid", bvid)
                        aid = view.opt("aid")?.toString()?.takeIf { it != "null" }.orEmpty().ifBlank { aid }
                        if (cid.isBlank()) {
                            val pages = view.optJSONArray("pages")
                            if (pages != null && pages.length() > 0) {
                                cid = pages.optJSONObject(0)?.opt("cid")?.toString().orEmpty()
                            }
                        }
                        if (cid.isBlank()) cid = view.opt("cid")?.toString().orEmpty()
                        val ownerObj = view.optJSONObject("owner")
                        if (ownerMid.isBlank()) {
                            ownerMid = ownerObj?.opt("mid")?.toString()
                                ?.takeIf { it != "null" }
                                ?.filter { it.isDigit() }
                                .orEmpty()
                        }
                        if (ownerFace.isBlank()) {
                            ownerFace = normalizeUrl(ownerObj?.optString("face", "").orEmpty()).orEmpty()
                        }
                        if (ownerName.isBlank()) {
                            ownerName = ownerObj?.optString("name", "").orEmpty()
                        }
                        if (ownerMid.isNotBlank() && ownerFace.isNotBlank()) {
                            ownerFaceCache[ownerMid] = ownerFace
                        }
                        if (bvid.isNotBlank() && cid.isNotBlank()) {
                            viewMetaCache[bvid] = Triple(bvid, aid, cid)
                        }
                    }
                } else if (needPlayMeta && viewRoot != null) {
                    error(viewRoot.optString("message", "稿件不可用"))
                }
            }
        }
        if (cid.isBlank()) error("无法获取 cid")
        if (bvid.isBlank() && aid.isBlank()) error("缺少稿件 id")

        // 只作 playurl 画质/音质档，不要塞 30280 这类音频 id（会被截掉）
        val qn = preferredQn.coerceIn(0, 127)
        val attempts = if (videoMode) {
            // 视频：少打几枪，优先 html5 progressive（更快起播）
            buildList {
                if (qn > 0) add("qn=$qn&fnval=1&fourk=0&platform=html5&high_quality=1")
                add("qn=64&fnval=1&fourk=0&platform=html5&high_quality=1")
                add("qn=32&fnval=1&fourk=0&platform=html5&high_quality=1")
                // 兜底 dash
                add("qn=0&fnval=16&fourk=0&platform=html5&high_quality=1")
            }.distinct()
        } else {
            // 听歌：先 dash 音频，progressive 视频音轨只作兜底
            buildList {
                if (qn > 0) add("qn=$qn&fnval=16&fourk=0&platform=html5&high_quality=1")
                add("qn=0&fnval=16&fourk=0&platform=html5&high_quality=1")
                add("qn=0&fnval=16&fourk=1")
                if (qn > 0) add("qn=$qn&fnval=1&fourk=0&platform=html5&high_quality=1")
                add("qn=80&fnval=1&fourk=0&platform=html5&high_quality=1")
            }.distinct()
        }

        var streamUrl = ""
        var gotVideo = false
        var lastMsg = if (videoMode) "未拿到视频流" else "未拿到音频流"
        for (extra in attempts) {
            val u = buildString {
                append("https://api.bilibili.com/x/player/playurl?")
                if (bvid.isNotBlank()) append("bvid=${URLEncoder.encode(bvid, "UTF-8")}&")
                if (aid.isNotBlank()) append("avid=${URLEncoder.encode(aid, "UTF-8")}&")
                append("cid=${URLEncoder.encode(cid, "UTF-8")}&$extra")
            }
            val data = runCatching {
                getJson(u, cookie, referer = "https://www.bilibili.com/video/$bvid")
            }.getOrNull()
            if (data == null) {
                lastMsg = "网络错误"
                continue
            }
            val code = data.optInt("code", -1)
            if (code != 0) {
                lastMsg = data.optString("message", "code $code")
                Log.w(TAG, "playurl $code $lastMsg extra=$extra")
                continue
            }
            val body = data.optJSONObject("data") ?: continue
            if (videoMode) {
                val v = pickVideoUrl(body, preferHigher = qn == 0 || qn >= 64)
                if (v.isNotBlank()) {
                    streamUrl = v
                    gotVideo = true
                    Log.i(TAG, "got video via $extra")
                    break
                }
                // 视频失败可暂用 progressive 整段（若有）
                val any = pickProgressiveUrl(body)
                if (any.isNotBlank()) {
                    streamUrl = any
                    gotVideo = true
                    Log.i(TAG, "got progressive via $extra")
                    break
                }
            } else {
                // 省流才捡最低码率；标准/较高/最高都走高码
                streamUrl = pickPlayableUrl(body, preferHigher = qn == 0 || qn >= 64)
                if (streamUrl.isNotBlank()) {
                    gotVideo = false
                    Log.i(TAG, "got audio via $extra len=${streamUrl.length}")
                    break
                }
            }
            lastMsg = if (videoMode) "响应无视频地址" else "响应无音频地址"
        }
        if (streamUrl.isBlank()) error(lastMsg)

        // 已有封面不再打 view，加快起播
        val cover = track.coverUrl?.takeIf { it.isNotBlank() }

        track.copy(
            bvid = bvid,
            aid = aid,
            cid = cid,
            streamUrl = streamUrl,
            coverUrl = cover ?: track.coverUrl,
            artist = ownerName.ifBlank { track.artist },
            ownerMid = ownerMid.ifBlank { track.ownerMid },
            ownerFace = ownerFace.ifBlank { track.ownerFace },
            source = MusicSourceType.BILIBILI,
            isVideoStream = videoMode && gotVideo,
        )
    }

    /**
     * 按 mid 拉 UP 头像（短视频右侧球）；带内存缓存。
     */
    suspend fun resolveOwnerFace(mid: String): String = withContext(Dispatchers.IO) {
        val id = mid.filter { it.isDigit() }
        if (id.isBlank()) return@withContext ""
        ownerFaceCache[id]?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        val cookie = mergedCookie()
        val cardJson = runCatching {
            getJson(
                "https://api.bilibili.com/x/web-interface/card?mid=$id&photo=true",
                cookie,
                referer = "https://space.bilibili.com/$id",
            )
        }.getOrNull()
        val face = normalizeUrl(
            cardJson?.optJSONObject("data")
                ?.optJSONObject("card")
                ?.optString("face", "")
                .orEmpty(),
        ).orEmpty()
        if (face.isNotBlank()) ownerFaceCache[id] = face
        face
    }

    data class Comment(
        val rpid: String,
        val mid: String,
        val uname: String,
        val message: String,
        val like: Int,
        val ctime: Long,
        val avatar: String = "",
        /** 根评论 rpid；楼中楼回复时与 root 一致 */
        val rootRpid: String = "",
        /** 被回复评论 rpid；根评论为 "0" */
        val parentRpid: String = "0",
        /** 被回复者昵称（楼中楼） */
        val replyToUname: String = "",
        /** B 站声明的子回复总数（接口常只带前 3 条） */
        val rcount: Int = 0,
        /** 评论文字（已尽量把表情换成可读文本） */
        /** 评论配图 URL */
        val pictureUrls: List<String> = emptyList(),
        /** 表情 [doge] → 图片 URL（B 站 emote） */
        val emotes: Map<String, String> = emptyMap(),
        /** 是否还有更多楼中楼未加载 */
        val repliesHasMore: Boolean = false,
        /** 楼中楼下一页 pn */
        val repliesNextPn: Int = 1,
        /** 直接子回复（仅根评论填充，不扁平展开） */
        val children: List<Comment> = emptyList(),
    )

    data class FavPage(
        val tracks: List<Track>,
        val hasMore: Boolean,
        val page: Int,
        val total: Int = 0,
    )

    data class UpProfile(
        val mid: String,
        val name: String,
        val face: String = "",
        val sign: String = "",
        val fans: Long = 0L,
        val friend: Long = 0L,
        val likeNum: Long = 0L,
        /** 投稿总数（card.archive_count） */
        val archiveCount: Int = 0,
        /** 是否已关注（需登录） */
        val isFollowing: Boolean = false,
    )

    data class VideoMeta(
        val bvid: String,
        val tid: Int,
        val tname: String,
        val ownerMid: String,
        val ownerName: String,
        val tags: List<String>,
    )

    data class UpSeason(
        val seasonId: String,
        val title: String,
        val cover: String = "",
        val epCount: Int = 0,
        val isSeries: Boolean = false,
    )

    data class UpVideosPage(
        val tracks: List<Track>,
        val hasMore: Boolean,
        val page: Int,
        val total: Int = 0,
    )

    data class CommentPage(
        val comments: List<Comment>,
        val hasMore: Boolean,
        val error: String? = null,
        /** 下一页 pn（经典接口） */
        val nextPn: Int = 1,
        /** main 接口 cursor.next */
        val nextCursor: Long = 0L,
        val total: Int = 0,
        val usedMainApi: Boolean = false,
    )

    /**
     * 稿件评论分页。
     * - 优先经典 `/x/v2/reply` + pn 翻页（可稳定拉上千条）
     * - 失败再走 main + cursor
     */
    suspend fun listComments(
        aid: String,
        page: Int = 1,
        pageSize: Int = 40,
        cursorNext: Long = 0L,
        preferMain: Boolean = false,
    ): CommentPage = withContext(Dispatchers.IO) {
        val oid = aid.filter { it.isDigit() }
        if (oid.isBlank()) {
            return@withContext CommentPage(emptyList(), false, "缺少 avid，请先成功播放一次")
        }
        val cookie = mergedCookieWithSystem()
        val ps = pageSize.coerceIn(10, 49)
        val pn = page.coerceAtLeast(1)

        // 1) 经典接口：按时间/热度 pn 翻页最稳
        if (!preferMain) {
            // sort=0 时间，sort=2 热度；时间更利于翻页
            for (sort in listOf(0, 2)) {
                val url =
                    "https://api.bilibili.com/x/v2/reply?" +
                        "type=1&oid=$oid&sort=$sort&nohot=0&ps=$ps&pn=$pn"
                val json = runCatching {
                    getJson(url, cookie, referer = "https://www.bilibili.com/video/")
                }.getOrNull() ?: continue
                val code = json.optInt("code", -1)
                if (code != 0) {
                    Log.w(TAG, "comments reply sort=$sort code=$code ${json.optString("message")}")
                    continue
                }
                val data = json.optJSONObject("data") ?: continue
                val arr = data.optJSONArray("replies") ?: JSONArray()
                val list = parseReplies(arr)
                val pageInfo = data.optJSONObject("page")
                val total = pageInfo?.optInt("count", 0) ?: 0
                val acount = pageInfo?.optInt("acount", total) ?: total
                val hasMore = when {
                    total > 0 -> pn * ps < total.coerceAtLeast(acount)
                    list.size >= ps -> true
                    else -> false
                }
                return@withContext CommentPage(
                    comments = list,
                    hasMore = hasMore,
                    error = null,
                    nextPn = pn + 1,
                    nextCursor = 0L,
                    total = total.coerceAtLeast(acount),
                    usedMainApi = false,
                )
            }
        }

        // 2) main 接口 cursor
        val next = if (cursorNext > 0) cursorNext else 0L
        val url =
            "https://api.bilibili.com/x/v2/reply/main?" +
                "type=1&oid=$oid&mode=3&next=$next&ps=$ps"
        val json = runCatching {
            getJson(url, cookie, referer = "https://www.bilibili.com/video/")
        }.getOrNull()
        if (json == null) {
            return@withContext CommentPage(emptyList(), false, "网络错误")
        }
        val code = json.optInt("code", -1)
        if (code != 0) {
            return@withContext CommentPage(
                emptyList(),
                false,
                json.optString("message", "评论加载失败 code=$code"),
            )
        }
        val data = json.optJSONObject("data")
            ?: return@withContext CommentPage(emptyList(), false, "空数据")
        val arr = data.optJSONArray("replies") ?: JSONArray()
        val list = parseReplies(arr)
        val cursor = data.optJSONObject("cursor")
        val isEnd = cursor?.optBoolean("is_end", list.size < ps) ?: (list.size < ps)
        val nextVal = cursor?.optLong("next", 0L) ?: 0L
        val allCount = cursor?.optInt("all_count", 0)
            ?: data.optJSONObject("cursor")?.optInt("all_count", 0)
            ?: 0
        CommentPage(
            comments = list,
            hasMore = !isEnd && (list.isNotEmpty() || nextVal > next),
            error = null,
            nextPn = pn + 1,
            nextCursor = nextVal,
            total = allCount,
            usedMainApi = true,
        )
    }

    private fun parseReplies(replies: JSONArray): List<Comment> = buildList {
        for (i in 0 until replies.length()) {
            val r = replies.optJSONObject(i) ?: continue
            parseOneComment(r, isRoot = true)?.let { add(it) }
        }
    }

    private fun parseOneComment(r: JSONObject, isRoot: Boolean): Comment? {
        val member = r.optJSONObject("member")
        val content = r.optJSONObject("content")
        var msg = content?.optString("message", "").orEmpty()
        // 表情包：content.emote 里 key 形如 [doge] → url
        val emoteMap = linkedMapOf<String, String>()
        val emote = content?.optJSONObject("emote")
        if (emote != null) {
            val keys = emote.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val meta = emote.optJSONObject(key) ?: continue
                val url = normalizeUrl(
                    meta.optString("url", meta.optString("gif_url", "")),
                ).orEmpty()
                if (url.isNotBlank()) {
                    emoteMap[key] = url
                    // 有的 message 用不带括号的 text
                    val text = meta.optString("text", "")
                    if (text.isNotBlank() && text != key) emoteMap[text] = url
                }
            }
        }
        val pictures = buildList {
            val pics = content?.optJSONArray("pictures")
            if (pics != null) {
                for (i in 0 until pics.length()) {
                    val p = pics.optJSONObject(i) ?: continue
                    val u = normalizeUrl(
                        p.optString("img_src", p.optString("src", "")),
                    ).orEmpty()
                    if (u.isNotBlank()) add(u)
                }
            }
        }
        if (msg.isBlank() && pictures.isEmpty() && emoteMap.isEmpty()) return null
        if (msg.isBlank() && pictures.isNotEmpty()) msg = "[图片]"
        val rpid = r.opt("rpid")?.toString().orEmpty()
        val root = r.opt("root")?.toString()?.takeIf { it != "null" && it != "0" }.orEmpty()
        val parent = r.opt("parent")?.toString()?.takeIf { it != "null" }.orEmpty().ifBlank { "0" }
        val replyToUname = when {
            parent == "0" || parent.isBlank() -> ""
            else -> {
                val members = content?.optJSONArray("members")
                val fromMembers = members?.optJSONObject(0)?.optString("uname", "").orEmpty()
                fromMembers.ifBlank {
                    Regex("^回复\\s*@([^:：\\s]+)").find(msg)?.groupValues?.getOrNull(1).orEmpty()
                }
            }
        }
        val nested = r.optJSONArray("replies")
        val children = if (isRoot && nested != null && nested.length() > 0) {
            buildList {
                for (j in 0 until nested.length()) {
                    val c = nested.optJSONObject(j) ?: continue
                    parseOneComment(c, isRoot = false)?.let { add(it) }
                }
            }
        } else {
            emptyList()
        }
        val rcount = r.optInt("rcount", 0).coerceAtLeast(children.size)
        return Comment(
            rpid = rpid,
            mid = member?.opt("mid")?.toString().orEmpty(),
            uname = member?.optString("uname", "用户").orEmpty(),
            message = msg,
            like = r.optInt("like", 0),
            ctime = r.optLong("ctime", 0L),
            avatar = normalizeUrl(member?.optString("avatar", "")).orEmpty(),
            rootRpid = if (root.isBlank() || root == "0") rpid else root,
            parentRpid = parent,
            replyToUname = replyToUname.ifBlank {
                Regex("^回复\\s*@([^:：\\s]+)").find(msg)?.groupValues?.getOrNull(1).orEmpty()
            },
            rcount = rcount,
            pictureUrls = pictures,
            emotes = emoteMap,
            repliesHasMore = rcount > children.size,
            repliesNextPn = 1,
            children = children,
        )
    }

    /**
     * 展开某条根评论的全部楼中楼（分页拉齐）。
     */
    suspend fun listCommentReplies(
        aid: String,
        rootRpid: String,
        page: Int = 1,
        pageSize: Int = 20,
    ): CommentPage = withContext(Dispatchers.IO) {
        val oid = aid.filter { it.isDigit() }
        val root = rootRpid.filter { it.isDigit() || it.isLetter() }
        if (oid.isBlank() || root.isBlank()) {
            return@withContext CommentPage(emptyList(), false, "缺少 aid/rpid")
        }
        val cookie = mergedCookieWithSystem()
        val ps = pageSize.coerceIn(10, 49)
        val pn = page.coerceAtLeast(1)
        val url =
            "https://api.bilibili.com/x/v2/reply/reply?" +
                "type=1&oid=$oid&root=$root&ps=$ps&pn=$pn"
        val json = runCatching {
            getJson(url, cookie, referer = "https://www.bilibili.com/video/")
        }.getOrNull()
            ?: return@withContext CommentPage(emptyList(), false, "网络错误")
        val code = json.optInt("code", -1)
        if (code != 0) {
            return@withContext CommentPage(
                emptyList(),
                false,
                json.optString("message", "回复加载失败 code=$code"),
            )
        }
        val data = json.optJSONObject("data")
            ?: return@withContext CommentPage(emptyList(), false, "空数据")
        val arr = data.optJSONArray("replies") ?: JSONArray()
        val list = buildList {
            for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                parseOneComment(r, isRoot = false)?.let { add(it) }
            }
        }
        val pageInfo = data.optJSONObject("page")
        val count = pageInfo?.optInt("count", 0) ?: 0
        val hasMore = when {
            count > 0 -> pn * ps < count
            list.size >= ps -> true
            else -> false
        }
        CommentPage(
            comments = list,
            hasMore = hasMore,
            nextPn = pn + 1,
            total = count,
        )
    }

    /**
     * UP 主资料卡。
     * 优先 card；粉丝/关注用 relation/stat 再补一层，避免全 0。
     */
    suspend fun upProfile(mid: String): UpProfile? = withContext(Dispatchers.IO) {
        val id = mid.filter { it.isDigit() }
        if (id.isBlank()) return@withContext null
        val cookie = mergedCookieWithSystem()
        val referer = "https://space.bilibili.com/$id"

        fun jsonLong(o: JSONObject?, key: String): Long {
            if (o == null) return 0L
            val v = o.opt(key) ?: return 0L
            return when (v) {
                is Number -> v.toLong()
                else -> v.toString().toLongOrNull() ?: 0L
            }
        }

        var name = ""
        var face = ""
        var sign = ""
        var fans = 0L
        var friend = 0L
        var likeNum = 0L
        var archiveCount = 0
        var following = false

        // 1) card
        val cardJson = runCatching {
            getJson(
                "https://api.bilibili.com/x/web-interface/card?mid=$id&photo=true",
                cookie,
                referer = referer,
            )
        }.getOrNull()
        if (cardJson != null && cardJson.optInt("code", -1) == 0) {
            val data = cardJson.optJSONObject("data")
            val card = data?.optJSONObject("card")
            if (card != null) {
                name = card.optString("name", "")
                face = normalizeUrl(card.optString("face", "")).orEmpty()
                sign = card.optString("sign", "")
                fans = jsonLong(data, "follower").takeIf { it > 0 }
                    ?: jsonLong(card, "fans")
                friend = jsonLong(card, "attention").takeIf { it > 0 }
                    ?: jsonLong(card, "friend")
                likeNum = jsonLong(data, "like_num")
                archiveCount = data?.optInt("archive_count", 0) ?: 0
                following = data?.optBoolean("following", false) == true
            }
        }

        // 2) relation/stat 补粉丝/关注数
        val stat = runCatching {
            getJson(
                "https://api.bilibili.com/x/relation/stat?vmid=$id",
                cookie,
                referer = referer,
            )
        }.getOrNull()
        if (stat != null && stat.optInt("code", -1) == 0) {
            val sd = stat.optJSONObject("data")
            val f = jsonLong(sd, "follower")
            val g = jsonLong(sd, "following")
            if (f > 0) fans = f
            if (g > 0) friend = g
        }

        // 3) 登录态是否已关注（比 card.following 更准）
        if (cookie.contains("SESSDATA")) {
            following = runCatching { isFollowingUp(id) }.getOrDefault(following)
        }

        // 4) acc/info 只补名字/头像/签名
        if (name.isBlank() || face.isBlank()) {
            val acc = runCatching {
                getJson(
                    "https://api.bilibili.com/x/space/acc/info?mid=$id",
                    cookie,
                    referer = referer,
                )
            }.getOrNull()
            if (acc != null && acc.optInt("code", -1) == 0) {
                val d = acc.optJSONObject("data")
                if (name.isBlank()) name = d?.optString("name", "").orEmpty()
                if (face.isBlank()) face = normalizeUrl(d?.optString("face", "")).orEmpty()
                if (sign.isBlank()) sign = d?.optString("sign", "").orEmpty()
            }
        }

        if (name.isBlank() && face.isBlank() && fans == 0L && archiveCount == 0) {
            return@withContext null
        }
        UpProfile(
            mid = id,
            name = name.ifBlank { "UP主" },
            face = face,
            sign = sign,
            fans = fans,
            friend = friend,
            likeNum = likeNum,
            archiveCount = archiveCount,
            isFollowing = following,
        )
    }

    /** 是否已关注该 mid（需登录） */
    suspend fun isFollowingUp(mid: String): Boolean = withContext(Dispatchers.IO) {
        val id = mid.filter { it.isDigit() }
        if (id.isBlank()) return@withContext false
        val cookie = mergedCookieWithSystem()
        if (!cookie.contains("SESSDATA")) return@withContext false
        val json = runCatching {
            getJson(
                "https://api.bilibili.com/x/relation?fid=$id",
                cookie,
                referer = "https://space.bilibili.com/$id",
            )
        }.getOrNull() ?: return@withContext false
        if (json.optInt("code", -1) != 0) return@withContext false
        // attribute: 0 无关系 1 悄悄关注 2 关注 6 好友 128 拉黑
        val attr = json.optJSONObject("data")?.optInt("attribute", 0) ?: 0
        attr == 2 || attr == 6 || attr == 1
    }

    /**
     * 关注 / 取消关注（同步 B 站，需登录 + csrf）。
     * @return null 成功；否则错误信息
     */
    suspend fun setFollowUp(mid: String, follow: Boolean): String? = withContext(Dispatchers.IO) {
        val id = mid.filter { it.isDigit() }
        if (id.isBlank()) return@withContext "无效 mid"
        val cookie = mergedCookieWithSystem()
        if (!cookie.contains("SESSDATA")) return@withContext "请先登录 B 站"
        val csrf = extractCookieValue(cookie, "bili_jct")
        if (csrf.isBlank()) return@withContext "缺少 bili_jct，请重新登录 B 站"
        val act = if (follow) 1 else 2
        val body = "fid=$id&act=$act&re_src=11&csrf=$csrf&spmid=333.999&jsonp=jsonp"
        val json = postForm(
            "https://api.bilibili.com/x/relation/modify",
            body,
            cookie,
            referer = "https://space.bilibili.com/$id",
        )
        val code = json.optInt("code", -1)
        if (code != 0) {
            return@withContext json.optString("message", "操作失败 code=$code")
        }
        null
    }

    /**
     * UP 投稿列表。优先 WBI 签名的 space 投稿接口（可分页拿全）；失败再昵称搜索兜底。
     */
    suspend fun upVideos(mid: String, page: Int = 1, pageSize: Int = 30): UpVideosPage =
        withContext(Dispatchers.IO) {
            val id = mid.filter { it.isDigit() }
            if (id.isBlank()) return@withContext UpVideosPage(emptyList(), false, page)
            val cookie = mergedCookieWithSystem()
            val ps = pageSize.coerceIn(1, 50)
            val pn = page.coerceAtLeast(1)
            val referer = "https://space.bilibili.com/$id"

            // 1) WBI 投稿列表（完整分页）
            val wbi = runCatching {
                val q = linkedMapOf(
                    "mid" to id,
                    "ps" to ps.toString(),
                    "tid" to "0",
                    "pn" to pn.toString(),
                    "keyword" to "",
                    "order" to "pubdate",
                    "platform" to "web",
                    "web_location" to "1550101",
                    "order_avoided" to "true",
                )
                val signed = signWbiQuery(q, cookie)
                getJson(
                    "https://api.bilibili.com/x/space/wbi/arc/search?$signed",
                    cookie,
                    referer = referer,
                )
            }.getOrNull()
            if (wbi != null && wbi.optInt("code", -1) == 0) {
                val data = wbi.optJSONObject("data")
                val vlist = data?.optJSONObject("list")?.optJSONArray("vlist")
                    ?: data?.optJSONArray("vlist")
                    ?: JSONArray()
                val tracks = parseUpVlist(vlist, id)
                val count = data?.optJSONObject("page")?.optInt("count", 0) ?: 0
                val hasMore = if (count > 0) pn * ps < count else tracks.size >= ps
                if (tracks.isNotEmpty() || count == 0) {
                    return@withContext UpVideosPage(tracks, hasMore, pn, count)
                }
            } else if (wbi != null) {
                Log.w(TAG, "upVideos wbi code=${wbi.optInt("code")} ${wbi.optString("message")}")
            }

            // 2) 无签名经典接口
            val classic = runCatching {
                getJson(
                    "https://api.bilibili.com/x/space/arc/search?" +
                        "mid=$id&ps=$ps&tid=0&pn=$pn&keyword=&order=pubdate&platform=web",
                    cookie,
                    referer = referer,
                )
            }.getOrNull()
            if (classic != null && classic.optInt("code", -1) == 0) {
                val data = classic.optJSONObject("data")
                val vlist = data?.optJSONObject("list")?.optJSONArray("vlist")
                    ?: data?.optJSONArray("vlist")
                    ?: JSONArray()
                val tracks = parseUpVlist(vlist, id)
                if (tracks.isNotEmpty()) {
                    val count = data?.optJSONObject("page")?.optInt("count", 0) ?: 0
                    val hasMore = if (count > 0) pn * ps < count else tracks.size >= ps
                    return@withContext UpVideosPage(tracks, hasMore, pn, count)
                }
            }

            // 3) 昵称搜索 + 严格 mid 过滤（不完整，仅兜底）
            val profileName = runCatching { upProfile(id)?.name }.getOrNull().orEmpty()
            if (profileName.isBlank()) {
                return@withContext UpVideosPage(emptyList(), false, pn)
            }
            val encoded = URLEncoder.encode(profileName, "UTF-8")
            // 搜多几页结果再滤 mid，尽量凑满一页
            val tracks = mutableListOf<Track>()
            var searchPn = pn
            var guard = 0
            var totalHits = 0
            while (tracks.size < ps && guard < 5) {
                guard++
                val sjson = runCatching {
                    getJson(
                        "https://api.bilibili.com/x/web-interface/search/type" +
                            "?search_type=video&keyword=$encoded&page=$searchPn&page_size=50&order=pubdate",
                        cookie,
                        referer = "https://search.bilibili.com/all?keyword=$encoded",
                    )
                }.getOrNull()
                if (sjson == null || sjson.optInt("code", -1) != 0) break
                val result = sjson.optJSONObject("data")?.optJSONArray("result") ?: JSONArray()
                totalHits = sjson.optJSONObject("data")?.optInt("numResults", totalHits) ?: totalHits
                if (result.length() == 0) break
                for (i in 0 until result.length()) {
                    val item = result.optJSONObject(i) ?: continue
                    val itemMid = item.opt("mid")?.toString()?.takeIf { it != "null" }.orEmpty()
                    // 严格 mid，避免同名 UP 污染
                    if (itemMid != id) continue
                    val bvid = item.optString("bvid", "")
                    if (bvid.isBlank()) continue
                    if (tracks.any { it.bvid == bvid }) continue
                    tracks.add(
                        Track(
                            id = bvid,
                            title = stripHtml(item.optString("title", bvid)),
                            artist = item.optString("author", profileName),
                            album = "投稿",
                            coverUrl = normalizeUrl(item.optString("pic", "")),
                            durationMs = parseDurationToMs(item.optString("duration", "0")),
                            source = MusicSourceType.BILIBILI,
                            bvid = bvid,
                            aid = item.opt("aid")?.toString()?.takeIf { it != "null" }.orEmpty(),
                            ownerMid = id,
                        ),
                    )
                    if (tracks.size >= ps) break
                }
                searchPn++
            }
            if (tracks.isNotEmpty()) {
                return@withContext UpVideosPage(
                    tracks = tracks.take(ps),
                    hasMore = tracks.size >= ps || searchPn * 50 < totalHits,
                    page = pn,
                    total = totalHits,
                )
            }
            UpVideosPage(emptyList(), false, pn)
        }

    // ── WBI 签名（投稿列表等） ──────────────────────────────────

    private suspend fun signWbiQuery(
        params: Map<String, String>,
        cookie: String,
    ): String {
        val mixin = ensureWbiMixin(cookie)
        val sorted = TreeMap<String, String>()
        params.forEach { (k, v) -> sorted[k] = v }
        val wts = (System.currentTimeMillis() / 1000).toString()
        sorted["wts"] = wts
        val query = sorted.entries.joinToString("&") { (k, v) ->
            val cleaned = v.filter { ch -> ch !in "!'()*" }
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(cleaned, "UTF-8")}"
        }
        // 注意：签名原文用未编码的值拼接（与官方实现一致用 encode 后的 query 也常见；用 cleaned 拼）
        val raw = sorted.entries.joinToString("&") { (k, v) ->
            val cleaned = v.filter { ch -> ch !in "!'()*" }
            "$k=$cleaned"
        }
        val wrid = md5Hex(raw + mixin)
        return "$query&w_rid=$wrid"
    }

    private suspend fun ensureWbiMixin(cookie: String): String {
        val cached = wbiMixinKey.get()
        val age = System.currentTimeMillis() - wbiMixinAt.get()
        if (cached.isNotBlank() && age < 12 * 60 * 60 * 1000L) return cached
        val nav = runCatching {
            getJson("https://api.bilibili.com/x/web-interface/nav", cookie)
        }.getOrNull()
        val img = nav?.optJSONObject("data")?.optJSONObject("wbi_img")
        val imgUrl = img?.optString("img_url").orEmpty()
        val subUrl = img?.optString("sub_url").orEmpty()
        val imgKey = imgUrl.substringAfterLast('/').substringBefore('.')
        val subKey = subUrl.substringAfterLast('/').substringBefore('.')
        if (imgKey.isBlank() || subKey.isBlank()) {
            Log.w(TAG, "wbi keys empty")
            return cached
        }
        val raw = imgKey + subKey
        val mixin = buildString {
            for (i in WBI_MIXIN_TABLE) {
                if (i < raw.length) append(raw[i])
            }
        }.take(32)
        wbiMixinKey.set(mixin)
        wbiMixinAt.set(System.currentTimeMillis())
        return mixin
    }

    private fun md5Hex(s: String): String {
        val dig = MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
        return dig.joinToString("") { b -> "%02x".format(b) }
    }

    private fun parseUpVlist(vlist: JSONArray, mid: String): List<Track> = buildList {
        for (i in 0 until vlist.length()) {
            val v = vlist.optJSONObject(i) ?: continue
            val bvid = v.optString("bvid", "")
            if (bvid.isBlank()) continue
            val title = stripHtml(v.optString("title", bvid))
            val author = v.optString("author", "Bilibili")
            val cover = normalizeUrl(v.optString("pic", ""))
            val aid = v.opt("aid")?.toString()?.takeIf { it != "null" }.orEmpty()
            val durationMs = parseDurationToMs(v.optString("length", "0"))
            add(
                Track(
                    id = bvid,
                    title = title,
                    artist = author,
                    album = "投稿",
                    coverUrl = cover,
                    durationMs = durationMs,
                    source = MusicSourceType.BILIBILI,
                    bvid = bvid,
                    aid = aid,
                    ownerMid = mid,
                ),
            )
        }
    }

    /** 从稿件拉 owner.mid（点 UP 名最准） */
    suspend fun resolveOwnerMid(track: Track): Pair<String, String> = withContext(Dispatchers.IO) {
        if (track.ownerMid.filter { it.isDigit() }.isNotBlank()) {
            return@withContext track.ownerMid.filter { it.isDigit() } to track.artist
        }
        val bv = track.bvid.ifBlank { parseBvid(track.id).orEmpty() }
        if (bv.isBlank() && track.aid.filter { it.isDigit() }.isBlank()) {
            return@withContext "" to track.artist
        }
        val cookie = mergedCookie()
        val q = if (bv.isNotBlank()) {
            "bvid=${URLEncoder.encode(bv, "UTF-8")}"
        } else {
            "aid=${track.aid.filter { it.isDigit() }}"
        }
        val view = runCatching {
            getJson("https://api.bilibili.com/x/web-interface/view?$q", cookie)
        }.getOrNull()
        val owner = view?.optJSONObject("data")?.optJSONObject("owner")
        val mid = owner?.opt("mid")?.toString()?.takeIf { it != "null" }.orEmpty()
        val name = owner?.optString("name", track.artist).orEmpty()
        mid to name.ifBlank { track.artist }
    }

    /** UP 合集 / 系列 */
    suspend fun upSeasons(mid: String, page: Int = 1, pageSize: Int = 20): List<UpSeason> =
        withContext(Dispatchers.IO) {
            val id = mid.filter { it.isDigit() }
            if (id.isBlank()) return@withContext emptyList()
            val cookie = mergedCookie()
            val url =
                "https://api.bilibili.com/x/polymer/web-space/seasons_series_list?" +
                    "mid=$id&page_num=${page.coerceAtLeast(1)}&page_size=${pageSize.coerceIn(1, 50)}"
            val json = getJson(url, cookie, referer = "https://space.bilibili.com/$id")
            if (json.optInt("code", -1) != 0) return@withContext emptyList()
            val items = json.optJSONObject("data")
                ?.optJSONObject("items_lists")
                ?.optJSONArray("seasons_list")
                ?: JSONArray()
            val series = json.optJSONObject("data")
                ?.optJSONObject("items_lists")
                ?.optJSONArray("series_list")
                ?: JSONArray()
            buildList {
                for (i in 0 until items.length()) {
                    val o = items.optJSONObject(i) ?: continue
                    val meta = o.optJSONObject("meta") ?: o
                    val sid = meta.opt("season_id")?.toString()
                        ?: meta.opt("id")?.toString()
                        ?: continue
                    if (sid == "null" || sid.isBlank()) continue
                    add(
                        UpSeason(
                            seasonId = sid,
                            title = meta.optString("name", meta.optString("title", "合集")),
                            cover = normalizeUrl(
                                meta.optString("cover", meta.optString("pic", "")),
                            ).orEmpty(),
                            epCount = meta.optInt("total", meta.optInt("ep_count", 0)),
                            isSeries = false,
                        ),
                    )
                }
                for (i in 0 until series.length()) {
                    val o = series.optJSONObject(i) ?: continue
                    val meta = o.optJSONObject("meta") ?: o
                    val sid = meta.opt("series_id")?.toString()
                        ?: meta.opt("id")?.toString()
                        ?: continue
                    if (sid == "null" || sid.isBlank()) continue
                    add(
                        UpSeason(
                            seasonId = "series_$sid",
                            title = meta.optString("name", meta.optString("title", "系列")),
                            cover = normalizeUrl(
                                meta.optString("cover", meta.optString("pic", "")),
                            ).orEmpty(),
                            epCount = meta.optInt("total", 0),
                            isSeries = true,
                        ),
                    )
                }
            }
        }

    /** 合集内视频 */
    suspend fun seasonArchives(
        mid: String,
        seasonId: String,
        page: Int = 1,
        pageSize: Int = 30,
    ): UpVideosPage = withContext(Dispatchers.IO) {
        val id = mid.filter { it.isDigit() }
        val sid = seasonId.removePrefix("series_").filter { it.isDigit() }
        if (id.isBlank() || sid.isBlank()) return@withContext UpVideosPage(emptyList(), false, page)
        val cookie = mergedCookie()
        val isSeries = seasonId.startsWith("series_")
        val url = if (isSeries) {
            "https://api.bilibili.com/x/series/archives?" +
                "mid=$id&series_id=$sid&pn=${page.coerceAtLeast(1)}&ps=${pageSize.coerceIn(1, 50)}"
        } else {
            "https://api.bilibili.com/x/polymer/web-space/seasons_archives_list?" +
                "mid=$id&season_id=$sid&page_num=${page.coerceAtLeast(1)}" +
                "&page_size=${pageSize.coerceIn(1, 50)}"
        }
        val json = getJson(url, cookie, referer = "https://space.bilibili.com/$id")
        if (json.optInt("code", -1) != 0) {
            return@withContext UpVideosPage(emptyList(), false, page)
        }
        val data = json.optJSONObject("data")
        val arr = data?.optJSONArray("archives")
            ?: data?.optJSONArray("aids")
            ?: JSONArray()
        // series 返回 archives 数组对象
        val tracks = buildList {
            for (i in 0 until arr.length()) {
                val v = arr.optJSONObject(i) ?: continue
                val bvid = v.optString("bvid", "")
                if (bvid.isBlank()) continue
                val title = stripHtml(v.optString("title", bvid))
                val cover = normalizeUrl(v.optString("pic", v.optString("cover", "")))
                val aid = v.opt("aid")?.toString()?.takeIf { it != "null" }.orEmpty()
                add(
                    Track(
                        id = bvid,
                        title = title,
                        artist = "",
                        album = "合集",
                        coverUrl = cover,
                        source = MusicSourceType.BILIBILI,
                        bvid = bvid,
                        aid = aid,
                        ownerMid = id,
                    ),
                )
            }
        }
        val pageObj = data?.optJSONObject("page")
        val total = pageObj?.optInt("total", 0) ?: data?.optInt("total", 0) ?: 0
        val hasMore = if (total > 0) page * pageSize < total else tracks.size >= pageSize
        UpVideosPage(tracks = tracks, hasMore = hasMore, page = page, total = total)
    }

    /**
     * 将稿件加入指定收藏夹（需登录 + csrf）。
     * @param mediaId B 站收藏夹 media_id；空则失败（不再默默塞进默认夹）
     * @return null 成功；否则错误信息
     */
    suspend fun addToFavFolder(track: Track, mediaId: String): String? = withContext(Dispatchers.IO) {
        val folderId = mediaId.trim()
        if (folderId.isBlank()) return@withContext "请选择 B 站收藏夹"
        val cookie = mergedCookieWithSystem()
        if (!cookie.contains("SESSDATA")) return@withContext "请先登录 B 站"
        val csrf = extractCookieValue(cookie, "bili_jct")
        if (csrf.isBlank()) return@withContext "缺少 bili_jct，请重新登录 B 站"

        var aid = track.aid.filter { it.isDigit() }
        if (aid.isBlank()) {
            val bv = track.bvid.ifBlank { parseBvid(track.id).orEmpty() }
            if (bv.isBlank()) return@withContext "缺少稿件 id"
            val view = getJson(
                "https://api.bilibili.com/x/web-interface/view?bvid=${URLEncoder.encode(bv, "UTF-8")}",
                cookie,
            )
            if (view.optInt("code", -1) != 0) {
                return@withContext view.optString("message", "无法解析稿件")
            }
            aid = view.optJSONObject("data")?.opt("aid")?.toString()?.filter { it.isDigit() }.orEmpty()
        }
        if (aid.isBlank()) return@withContext "缺少 aid"

        val body =
            "rid=$aid&type=2" +
                "&add_media_ids=${URLEncoder.encode(folderId, "UTF-8")}" +
                "&csrf=${URLEncoder.encode(csrf, "UTF-8")}"
        val json = postForm(
            "https://api.bilibili.com/x/v3/fav/resource/deal",
            body,
            cookie,
            referer = "https://www.bilibili.com",
        )
        val code = json.optInt("code", -1)
        if (code == 0) null else json.optString("message", "同步失败 code=$code")
    }

    /** 兼容旧调用：写入第一个收藏夹 */
    suspend fun addToDefaultFav(track: Track): String? {
        val id = favFolders().firstOrNull()?.id.orEmpty()
        return addToFavFolder(track, id)
    }

    /**
     * 清空收藏夹内全部失效/已删除稿件（B 站官方 clean 接口）。
     * @return null 成功；否则错误信息
     */
    suspend fun cleanInvalidFavResources(mediaId: String): String? = withContext(Dispatchers.IO) {
        val id = mediaId.trim()
        if (id.isBlank()) return@withContext "收藏夹 id 为空"
        val cookie = mergedCookieWithSystem()
        if (!cookie.contains("SESSDATA")) return@withContext "请先登录 B 站"
        val csrf = extractCookieValue(cookie, "bili_jct")
        if (csrf.isBlank()) return@withContext "缺少 bili_jct，请重新登录 B 站"
        val body =
            "media_id=${URLEncoder.encode(id, "UTF-8")}" +
                "&platform=web" +
                "&csrf=${URLEncoder.encode(csrf, "UTF-8")}"
        val json = postForm(
            "https://api.bilibili.com/x/v3/fav/resource/clean",
            body,
            cookie,
            referer = "https://www.bilibili.com",
        )
        val code = json.optInt("code", -1)
        if (code == 0) null else json.optString("message", "清理失败 code=$code")
    }

    /**
     * 批量从收藏夹删除指定稿件（resources = aid:2）。
     */
    suspend fun batchDelFavResources(mediaId: String, aids: List<String>): String? =
        withContext(Dispatchers.IO) {
            val id = mediaId.trim()
            val list = aids.map { it.filter { c -> c.isDigit() } }.filter { it.isNotBlank() }.distinct()
            if (id.isBlank() || list.isEmpty()) return@withContext null
            val cookie = mergedCookieWithSystem()
            if (!cookie.contains("SESSDATA")) return@withContext "请先登录 B 站"
            val csrf = extractCookieValue(cookie, "bili_jct")
            if (csrf.isBlank()) return@withContext "缺少 bili_jct，请重新登录 B 站"
            val resources = list.joinToString(",") { "$it:2" }
            val body =
                "resources=${URLEncoder.encode(resources, "UTF-8")}" +
                    "&media_id=${URLEncoder.encode(id, "UTF-8")}" +
                    "&platform=web" +
                    "&csrf=${URLEncoder.encode(csrf, "UTF-8")}"
            val json = postForm(
                "https://api.bilibili.com/x/v3/fav/resource/batch-del",
                body,
                cookie,
                referer = "https://www.bilibili.com",
            )
            val code = json.optInt("code", -1)
            if (code == 0) null else json.optString("message", "删除失败 code=$code")
        }

    /**
     * 扫描收藏夹内仍存在的失效稿 aid（最多扫 50 个 API 页 ≈ 1000 条）。
     */
    suspend fun listInvalidFavAids(mediaId: String, maxApiPages: Int = 50): List<String> =
        withContext(Dispatchers.IO) {
            val id = mediaId.trim()
            if (id.isBlank()) return@withContext emptyList()
            val cookie = mergedCookieWithSystem().ifBlank { mergedCookie() }
            if (!cookie.contains("SESSDATA")) return@withContext emptyList()
            val aids = linkedSetOf<String>()
            val apiPs = 20
            var pn = 1
            while (pn <= maxApiPages) {
                val url =
                    "https://api.bilibili.com/x/v3/fav/resource/list" +
                        "?media_id=${URLEncoder.encode(id, "UTF-8")}" +
                        "&pn=$pn&ps=$apiPs&keyword=&order=mtime&type=0&tid=0&platform=web"
                val json = getJson(url, cookie, referer = "https://www.bilibili.com")
                val data = json.optJSONObject("data")
                val medias = data?.optJSONArray("medias")
                if (medias == null || medias.length() == 0) break
                for (j in 0 until medias.length()) {
                    val m = medias.optJSONObject(j) ?: continue
                    if (!isInvalidFavJson(m)) continue
                    val aid = m.opt("id")?.toString()?.filter { it.isDigit() }.orEmpty()
                        .ifBlank { m.opt("aid")?.toString()?.filter { it.isDigit() }.orEmpty() }
                    if (aid.isNotBlank()) aids.add(aid)
                }
                val hasMore = when {
                    data.has("has_more") -> data.optBoolean("has_more", false)
                    else -> medias.length() >= apiPs
                }
                if (!hasMore) break
                pn++
            }
            aids.toList()
        }

    /**
     * 彻底清除夹内失效：先官方 clean，再扫列表 batch-del 残留。
     */
    suspend fun purgeInvalidFromFav(mediaId: String): PurgeInvalidResult = withContext(Dispatchers.IO) {
        val id = mediaId.trim()
        if (id.isBlank()) {
            return@withContext PurgeInvalidResult(0, "收藏夹 id 为空")
        }
        // 1) 官方一键清失效
        val cleanErr = cleanInvalidFavResources(id)
        // 2) 扫残留失效 aid，批量删
        val leftover = listInvalidFavAids(id)
        var batchOk = 0
        var batchFail = 0
        for (chunk in leftover.chunked(20)) {
            val err = batchDelFavResources(id, chunk)
            if (err == null) batchOk += chunk.size else batchFail += chunk.size
            kotlinx.coroutines.delay(220)
        }
        val msg = when {
            cleanErr == null && leftover.isEmpty() -> "已清除失效视频"
            cleanErr == null && batchOk > 0 -> "已清除失效视频（含 $batchOk 条）"
            cleanErr == null && batchFail > 0 -> "部分失效未能删除"
            cleanErr != null && batchOk > 0 -> "已删除 $batchOk 条失效（clean: $cleanErr）"
            cleanErr != null && leftover.isEmpty() -> "清理完成"
            else -> cleanErr ?: "清理失败"
        }
        PurgeInvalidResult(
            removedCount = batchOk,
            message = msg,
            cleanApiOk = cleanErr == null,
        )
    }

    /**
     * 对当前账号所有自建收藏夹执行失效清理。
     */
    suspend fun cleanInvalidInAllFavFolders(): CleanInvalidResult = withContext(Dispatchers.IO) {
        val folders = favFolders()
        if (folders.isEmpty()) {
            return@withContext CleanInvalidResult(0, 0, "没有可清理的收藏夹")
        }
        var ok = 0
        var fail = 0
        var totalRemoved = 0
        val errors = mutableListOf<String>()
        for (f in folders) {
            val r = runCatching { purgeInvalidFromFav(f.id) }.getOrElse {
                PurgeInvalidResult(0, it.message ?: "失败", false)
            }
            if (r.cleanApiOk || r.removedCount > 0) {
                ok++
                totalRemoved += r.removedCount
            } else {
                fail++
                if (errors.size < 3) errors.add("${f.title}: ${r.message}")
            }
            kotlinx.coroutines.delay(320)
        }
        CleanInvalidResult(
            cleanedFolders = ok,
            failedFolders = fail,
            message = when {
                fail == 0 && totalRemoved > 0 -> "已清理 $ok 个夹，去掉约 $totalRemoved 条失效"
                fail == 0 -> "已处理 $ok 个收藏夹中的失效视频"
                ok == 0 -> errors.firstOrNull() ?: "清理失败"
                else -> "已处理 $ok 个夹，失败 $fail 个"
            },
        )
    }

    data class CleanInvalidResult(
        val cleanedFolders: Int,
        val failedFolders: Int,
        val message: String,
    )

    data class PurgeInvalidResult(
        val removedCount: Int,
        val message: String,
        val cleanApiOk: Boolean = true,
    )

    /** 列表项是否为失效稿件（已删 / 不可见等） */
    fun isInvalidFavTitle(title: String): Boolean {
        val t = title.trim()
        if (t.isEmpty()) return true
        return t.contains("已失效") ||
            t.contains("已删除") ||
            t.equals("失效视频", ignoreCase = true) ||
            t.contains("视频不见了") ||
            t.contains("内容失效") ||
            t.contains("up主已删除") ||
            t.contains("UP主已删除") ||
            t.contains("稿件不可见") ||
            t.contains("视频已失效")
    }

    /**
     * 发表评论 / 回复（需登录 Cookie + csrf）。
     * @param root 根评论 rpid；发一级评论传 "0"
     * @param parent 被回复评论 rpid；发一级评论传 "0"；回复楼中楼时 = 该条子评 rpid
     * @return null 成功；否则错误信息
     */
    suspend fun postComment(
        aid: String,
        message: String,
        root: String = "0",
        parent: String = "0",
    ): String? = withContext(Dispatchers.IO) {
        val text = message.trim()
        val oid = aid.filter { it.isDigit() }
        if (oid.isBlank() || text.isEmpty()) return@withContext "内容为空"
        val cookie = mergedCookieWithSystem()
        if (!cookie.contains("SESSDATA")) return@withContext "请先登录 B 站（未检测到 SESSDATA）"
        val csrf = extractCookieValue(cookie, "bili_jct")
        if (csrf.isBlank()) return@withContext "缺少 bili_jct，请到「我的」重新登录 B 站"
        val rootId = root.filter { it.isDigit() }.ifBlank { "0" }
        val parentId = parent.filter { it.isDigit() }.ifBlank { "0" }
        val body =
            "type=1&oid=$oid" +
                "&message=${URLEncoder.encode(text, "UTF-8")}" +
                "&root=$rootId&parent=$parentId" +
                "&plat=1&csrf=${URLEncoder.encode(csrf, "UTF-8")}"
        val json = postForm(
            "https://api.bilibili.com/x/v2/reply/add",
            body,
            cookie,
            referer = "https://www.bilibili.com",
        )
        val code = json.optInt("code", -1)
        if (code == 0) null else json.optString("message", "发送失败 code=$code")
    }

    /** 是否已带登录 Cookie（含系统 WebView 残留）。 */
    suspend fun isLoggedInCookie(): Boolean = withContext(Dispatchers.IO) {
        mergedCookieWithSystem().contains("SESSDATA")
    }

    private fun extractCookieValue(cookie: String, key: String): String =
        cookie.split(';')
            .map { it.trim() }
            .firstOrNull { it.startsWith("$key=") }
            ?.substringAfter('=')
            .orEmpty()

    /** DataStore Cookie + 系统 CookieManager 合并（登录后发评论更稳）。 */
    private suspend fun mergedCookieWithSystem(): String {
        val base = mergedCookie()
        val system = runCatching {
            val cm = android.webkit.CookieManager.getInstance()
            val map = linkedMapOf<String, String>()
            // 先放 base
            base.split(';').forEach { piece ->
                val kv = piece.trim()
                val eq = kv.indexOf('=')
                if (eq > 0) map[kv.substring(0, eq).trim()] = kv.substring(eq + 1).trim()
            }
            listOf(
                "https://www.bilibili.com",
                "https://bilibili.com",
                "https://api.bilibili.com",
                "https://m.bilibili.com",
            ).forEach { domain ->
                cm.getCookie(domain)?.split(';')?.forEach { piece ->
                    val kv = piece.trim()
                    val eq = kv.indexOf('=')
                    if (eq > 0) map[kv.substring(0, eq).trim()] = kv.substring(eq + 1).trim()
                }
            }
            map.entries.joinToString("; ") { "${it.key}=${it.value}" }
        }.getOrDefault(base)
        return system.ifBlank { base }
    }

    /**
     * B 站首页「推荐」流（登录 Cookie 会按账号兴趣个性化，最接近官方为你推荐）。
     * 未登录时退化为热门偏综合内容，不再强制音乐区。
     */
    suspend fun homepageRcmd(limit: Int = 24, freshIdx: Int = 1): List<Track> = withContext(Dispatchers.IO) {
        val cookie = mergedCookieWithSystem()
        val ps = limit.coerceIn(8, 30)
        val url =
            "https://api.bilibili.com/x/web-interface/index/top/feed/rcmd?" +
                "fresh_idx=${freshIdx.coerceAtLeast(1)}" +
                "&fresh_type=4&feed_version=V8&homepage_ver=1" +
                "&ps=$ps&y_num=0&brush=0&web_location=333.1007"
        val json = runCatching {
            getJson(url, cookie, referer = "https://www.bilibili.com/")
        }.getOrNull()
        if (json == null || json.optInt("code", -1) != 0) {
            Log.w(TAG, "homepageRcmd fail code=${json?.optInt("code")} ${json?.optString("message")}")
            return@withContext emptyList()
        }
        val items = json.optJSONObject("data")?.optJSONArray("item") ?: JSONArray()
        buildList {
            for (i in 0 until items.length()) {
                if (size >= limit) break
                val item = items.optJSONObject(i) ?: continue
                val goto = item.optString("goto", "")
                // 只收普通稿件 av / vertical_av
                if (goto.isNotBlank() && goto != "av" && goto != "vertical_av") continue
                val bv = item.optString("bvid", "")
                if (bv.isBlank()) continue
                val owner = item.optJSONObject("owner")?.optString("name", "Bilibili")
                    ?: item.optString("author", "Bilibili")
                val duration = item.optLong("duration", 0L).let { d ->
                    if (d > 10_000) d else d * 1000
                }
                add(
                    Track(
                        id = bv,
                        title = stripHtml(item.optString("title", bv)),
                        artist = owner,
                        album = "首页推荐",
                        coverUrl = normalizeUrl(item.optString("pic", item.optString("cover", ""))),
                        durationMs = duration,
                        source = MusicSourceType.BILIBILI,
                        bvid = bv,
                        aid = item.opt("id")?.toString()?.takeIf { it != "null" && it.all(Char::isDigit) }
                            ?: item.opt("aid")?.toString()?.takeIf { it != "null" }.orEmpty(),
                        ownerMid = item.optJSONObject("owner")?.opt("mid")?.toString()
                            ?.takeIf { it != "null" }.orEmpty(),
                        categoryId = item.optInt("tid", item.optInt("typeid", 0)),
                        categoryName = stripHtml(
                            item.optString("tname", item.optString("typename", "")),
                        ),
                        tags = parseTags(item),
                    ),
                )
            }
        }
    }

    /**
     * 观看历史（登录后），用作兴趣种子，避免推荐只会刷音乐。
     */
    suspend fun watchHistory(limit: Int = 24): List<Track> = withContext(Dispatchers.IO) {
        val cookie = mergedCookieWithSystem()
        if (!cookie.contains("SESSDATA")) return@withContext emptyList()
        val url =
            "https://api.bilibili.com/x/web-interface/history/cursor?" +
                "max=0&view_at=0&business=archive&ps=${limit.coerceIn(8, 30)}"
        val json = runCatching {
            getJson(url, cookie, referer = "https://www.bilibili.com/account/history")
        }.getOrNull() ?: return@withContext emptyList()
        if (json.optInt("code", -1) != 0) return@withContext emptyList()
        val list = json.optJSONObject("data")?.optJSONArray("list") ?: JSONArray()
        buildList {
            for (i in 0 until list.length()) {
                if (size >= limit) break
                val item = list.optJSONObject(i) ?: continue
                val history = item.optJSONObject("history") ?: item
                val bv = history.optString("bvid", item.optString("bvid", ""))
                if (bv.isBlank()) continue
                val business = history.optString("business", item.optString("business", "archive"))
                if (business.isNotBlank() && business != "archive" && business != "pgc") {
                    // 仍允许普通视频；跳过 live 等
                    if (business == "live" || business == "article") continue
                }
                val author = item.optString("author_name", item.optString("name", "Bilibili"))
                val duration = item.optLong("duration", 0L).let { d ->
                    if (d > 10_000) d else d * 1000
                }
                add(
                    Track(
                        id = bv,
                        title = stripHtml(item.optString("title", bv)),
                        artist = author,
                        album = "观看历史",
                        coverUrl = normalizeUrl(item.optString("cover", item.optString("pic", ""))),
                        durationMs = duration,
                        source = MusicSourceType.BILIBILI,
                        bvid = bv,
                        aid = history.opt("oid")?.toString()?.filter { it.isDigit() }.orEmpty(),
                        categoryId = item.optInt("tid", item.optInt("typeid", 0)),
                        categoryName = stripHtml(
                            item.optString("tname", item.optString("typename", "")),
                        ),
                        tags = parseTags(item),
                    ),
                )
            }
        }
    }

    /**
     * 游客可听的「热门」流（全站，非音乐区）。
     */
    suspend fun popularTracks(limit: Int = 24): List<Track> = withContext(Dispatchers.IO) {
        val cookie = mergedCookie()
        val url =
            "https://api.bilibili.com/x/web-interface/popular?" +
                "ps=${limit.coerceIn(8, 40)}&pn=1"
        val json = getJson(url, cookie)
        if (json.optInt("code", -1) != 0) {
            // 兜底：全站排行 rid=0 若不可用再试热门分区混合
            return@withContext rankingTracks(rid = 0, limit = limit).ifEmpty {
                rankingTracks(rid = 1, limit = limit) // 动画区兜底，总比硬塞音乐好一点
            }
        }
        val list = json.optJSONObject("data")?.optJSONArray("list") ?: JSONArray()
        parseArchiveList(list, limit, album = "热门")
    }

    /** 分区排行（rid 可选；默认 0=全站若可用，3=音乐）。游客可用。 */
    suspend fun rankingTracks(rid: Int = 3, limit: Int = 24): List<Track> = withContext(Dispatchers.IO) {
        val cookie = mergedCookie()
        val url =
            "https://api.bilibili.com/x/web-interface/ranking/v2?" +
                "rid=$rid&type=all"
        val json = getJson(url, cookie)
        if (json.optInt("code", -1) != 0) return@withContext emptyList()
        val list = json.optJSONObject("data")?.optJSONArray("list") ?: JSONArray()
        val album = when (rid) {
            0 -> "全站热榜"
            1 -> "动画热榜"
            3 -> "音乐热榜"
            4 -> "游戏热榜"
            5 -> "娱乐热榜"
            36 -> "知识热榜"
            129 -> "舞蹈热榜"
            160 -> "生活热榜"
            else -> "分区热榜"
        }
        parseArchiveList(list, limit, album = album)
    }

    /**
     * 仅 B 站「像歌」的音乐子区（原创/翻唱/VOCALOID/演奏/MV）。
     * 不含：音乐综合(130 太杂)、教学、音游、乐评盘点。
     */
    suspend fun musicRegionFeed(limit: Int = 36): List<Track> = withContext(Dispatchers.IO) {
        val cookie = mergedCookie()
        val pool = linkedMapOf<String, Track>()
        // 各子区排行更干净
        for (rid in MUSIC_SUB_RIDS) {
            for (t in rankingTracks(rid = rid, limit = 20)) {
                pool.putIfAbsent(t.id, t.copy(album = albumForRid(rid)))
            }
        }
        // 子区最新补量
        for (rid in MUSIC_SUB_RIDS) {
            if (pool.size >= limit * 3) break
            val part = regionLatest(rid, ps = 12, cookie = cookie)
            for (t in part) {
                pool.putIfAbsent(t.id, t.copy(album = albumForRid(rid)))
            }
        }
        // 父区热榜兜底
        if (pool.size < limit) {
            for (t in rankingTracks(rid = 3, limit = limit)) {
                pool.putIfAbsent(t.id, t.copy(album = "音乐热榜"))
            }
        }
        // 出口再滤一遍
        com.madus.mobile.domain.TrackFilters
            .preferMusicish(pool.values.toList(), minScore = 12)
            .shuffled()
            .take(limit.coerceAtLeast(8))
    }

    private fun albumForRid(rid: Int): String = when (rid) {
        28 -> "原创音乐"
        31 -> "翻唱"
        30 -> "VOCALOID"
        59 -> "演奏"
        193 -> "MV"
        29 -> "音乐现场"
        else -> "音乐区"
    }

    /** 分区最新列表 */
    private fun regionLatest(rid: Int, ps: Int, cookie: String): List<Track> {
        val url =
            "https://api.bilibili.com/x/web-interface/dynamic/region?" +
                "ps=${ps.coerceIn(6, 30)}&rid=$rid"
        val json = getJson(url, cookie)
        if (json.optInt("code", -1) != 0) {
            // 兜底 newlist
            val url2 =
                "https://api.bilibili.com/x/web-interface/newlist?" +
                    "rid=$rid&type=0&ps=${ps.coerceIn(6, 30)}&pn=1"
            val j2 = getJson(url2, cookie)
            if (j2.optInt("code", -1) != 0) return emptyList()
            val list = j2.optJSONObject("data")?.optJSONArray("archives")
                ?: j2.optJSONObject("data")?.optJSONArray("vlist")
                ?: JSONArray()
            return parseArchiveList(list, ps, album = "音乐区")
        }
        val list = json.optJSONObject("data")?.optJSONArray("archives")
            ?: json.optJSONObject("data")?.optJSONArray("list")
            ?: JSONArray()
        return parseArchiveList(list, ps, album = "音乐区")
    }

    /**
     * 音乐区搜索（tids=3），个性化补歌用。
     */
    suspend fun searchMusic(keyword: String, limit: Int = 12): List<Track> = withContext(Dispatchers.IO) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return@withContext emptyList()
        val cookie = mergedCookie()
        val encoded = URLEncoder.encode(kw, "UTF-8")
        val url =
            "https://api.bilibili.com/x/web-interface/search/type" +
                "?search_type=video&keyword=$encoded" +
                "&tids=3&page=1&page_size=${limit.coerceIn(4, 30)}&order=totalrank"
        val json = getJson(
            url,
            cookie,
            referer = "https://search.bilibili.com/all?keyword=$encoded",
        )
        val result = json.optJSONObject("data")?.optJSONArray("result") ?: JSONArray()
        val raw = buildList {
            for (i in 0 until result.length()) {
                if (size >= limit * 2) break
                val item = result.optJSONObject(i) ?: continue
                val bv = item.optString("bvid", "")
                if (bv.isBlank()) continue
                val tid = item.optInt("typeid", item.optInt("tid", 0))
                if (tid > 0 && tid !in MUSIC_TID_SET) continue
                val duration = parseSearchDuration(item.optString("duration", ""))
                add(
                    Track(
                        id = bv,
                        title = stripHtml(item.optString("title", bv)),
                        artist = item.optString("author", "Bilibili"),
                        album = "音乐搜索",
                        coverUrl = normalizeUrl(item.optString("pic", "")),
                        durationMs = duration,
                        source = MusicSourceType.BILIBILI,
                        bvid = bv,
                        aid = item.opt("aid")?.toString()?.takeIf { it != "null" }.orEmpty(),
                    ),
                )
            }
        }
        // 搜索结果杂，强制 prefer 过滤
        com.madus.mobile.domain.TrackFilters.preferMusicish(raw, minScore = 10).take(limit)
    }

    private fun parseSearchDuration(raw: String): Long {
        // "3:45" or "03:45"
        val parts = raw.split(':').mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60 + parts[1]) * 1000
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
            else -> 0L
        }
    }

    private fun parseArchiveList(list: JSONArray, limit: Int, album: String): List<Track> =
        buildList {
            for (i in 0 until list.length()) {
                if (size >= limit) break
                val item = list.optJSONObject(i) ?: continue
                val bv = item.optString("bvid", "")
                if (bv.isBlank()) continue
                // 分区字段过滤（若有）
                val tid = item.optInt("tid", item.optInt("typeid", 0))
                if (tid > 0 && tid !in MUSIC_TID_SET && album.contains("音乐")) {
                    // 来自音乐区接口时仍可能混入，严格一点
                    if (tid !in MUSIC_TID_SET && tid != 3) continue
                }
                val owner = item.optJSONObject("owner")?.optString("name", "Bilibili")
                    ?: item.optString("author", "Bilibili")
                val duration = item.optLong("duration", 0L).let { d ->
                    if (d > 10_000) d else d * 1000 // 有的接口已是秒
                }
                add(
                    Track(
                        id = bv,
                        title = stripHtml(item.optString("title", bv)),
                        artist = owner,
                        album = album,
                        coverUrl = normalizeUrl(
                            item.optString("pic", item.optString("cover", "")),
                        ),
                        durationMs = duration,
                        source = MusicSourceType.BILIBILI,
                        bvid = bv,
                        aid = item.opt("aid")?.toString()?.takeIf { it != "null" }.orEmpty(),
                        categoryId = item.optInt("tid", item.optInt("typeid", 0)),
                        categoryName = stripHtml(
                            item.optString("tname", item.optString("typename", "")),
                        ),
                        tags = parseTags(item),
                    ),
                )
            }
        }

    private fun parseTags(item: JSONObject): List<String> {
        val out = linkedSetOf<String>()
        val direct = item.optString("tag", "").trim()
        if (direct.isNotBlank()) {
            direct.split(',', '，', ' ', '/', '|').map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { out.add(it) }
        }
        item.optJSONArray("tags")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i)
                val name = stripHtml(
                    o?.optString("tag_name", o?.optString("name", "").orEmpty()).orEmpty(),
                ).trim()
                if (name.isNotBlank()) out.add(name)
            }
        }
        return out.toList()
    }

    /** 相关推荐（B 站官方 related），用作轻量「电台/相似」。 */
    suspend fun relatedTracks(bvid: String, limit: Int = 20): List<Track> = withContext(Dispatchers.IO) {
        if (bvid.isBlank()) return@withContext emptyList()
        val cookie = mergedCookie()
        val url =
            "https://api.bilibili.com/x/web-interface/archive/related?bvid=${URLEncoder.encode(bvid, "UTF-8")}"
        val json = getJson(url, cookie)
        if (json.optInt("code", -1) != 0) return@withContext emptyList()
        val list = json.optJSONArray("data") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until list.length()) {
                if (size >= limit) break
                val item = list.optJSONObject(i) ?: continue
                val bv = item.optString("bvid", "")
                if (bv.isBlank()) continue
                val ownerObj = item.optJSONObject("owner")
                val owner = ownerObj?.optString("name", "Bilibili") ?: "Bilibili"
                val mid = ownerObj?.opt("mid")?.toString()?.takeIf { it != "null" }.orEmpty()
                add(
                    Track(
                        id = bv,
                        title = stripHtml(item.optString("title", bv)),
                        artist = owner,
                        album = "相关推荐",
                        coverUrl = normalizeUrl(item.optString("pic", "")),
                        durationMs = item.optLong("duration", 0L) * 1000,
                        source = MusicSourceType.BILIBILI,
                        bvid = bv,
                        aid = item.opt("aid")?.toString()?.takeIf { it != "null" }.orEmpty(),
                        ownerMid = mid,
                        categoryId = item.optInt("tid", item.optInt("typeid", 0)),
                        categoryName = stripHtml(
                            item.optString("tname", item.optString("typename", "")),
                        ),
                        tags = parseTags(item),
                    ),
                )
            }
        }
    }

    /**
     * 拉单稿详情元数据（分区 + 标签 + 作者），供内容画像缓存使用。
     * 失败返回 null，不抛异常。
     */
    suspend fun videoMeta(bvid: String): VideoMeta? = withContext(Dispatchers.IO) {
        val bv = parseBvid(bvid) ?: return@withContext null
        val cookie = mergedCookie()
        val view = runCatching {
            getJson(
                "https://api.bilibili.com/x/web-interface/view?bvid=${URLEncoder.encode(bv, "UTF-8")}",
                cookie,
                referer = "https://www.bilibili.com/video/$bv",
            )
        }.getOrNull()
        val data = view?.optJSONObject("data") ?: return@withContext null
        val ownerObj = data.optJSONObject("owner")
        val tags = runCatching {
            val tagJson = getJson(
                "https://api.bilibili.com/x/web-interface/view/detail/tag?bvid=${URLEncoder.encode(bv, "UTF-8")}",
                cookie,
                referer = "https://www.bilibili.com/video/$bv",
            )
            val tagData = tagJson.optJSONObject("data")
            val arr = when (tagData) {
                is JSONArray -> tagData
                else -> tagData?.optJSONArray("tags") ?: JSONArray()
            }
            buildList {
                for (i in 0 until arr.length()) {
                    val name = stripHtml(arr.optJSONObject(i)?.optString("tag_name", "").orEmpty()).trim()
                    if (name.isNotBlank()) add(name)
                }
            }
        }.getOrDefault(emptyList())
        VideoMeta(
            bvid = bv,
            tid = data.optInt("tid", data.optInt("typeid", 0)),
            tname = stripHtml(data.optString("tname", data.optString("typename", ""))).trim(),
            ownerMid = ownerObj?.opt("mid")?.toString()?.takeIf { it != "null" }.orEmpty(),
            ownerName = ownerObj?.optString("name", "Bilibili").orEmpty(),
            tags = tags,
        )
    }

    /** 公开：按 bvid 拉多分 P 列表 */
    suspend fun listArchiveParts(bvid: String): List<Track> = withContext(Dispatchers.IO) {
        val bv = bvid.ifBlank { return@withContext emptyList() }
        viewTracks(bv, mergedCookie())
    }

    private fun viewTracks(bvid: String, cookie: String): List<Track> {
        val url = "https://api.bilibili.com/x/web-interface/view?bvid=${URLEncoder.encode(bvid, "UTF-8")}"
        val view = getJson(url, cookie).optJSONObject("data") ?: return emptyList()
        val title = view.optString("title", bvid)
        val ownerObj = view.optJSONObject("owner")
        val owner = ownerObj?.optString("name", "Bilibili") ?: "Bilibili"
        val ownerMid = ownerObj?.opt("mid")?.toString()?.takeIf { it != "null" }.orEmpty()
        val ownerFace = normalizeUrl(ownerObj?.optString("face", "").orEmpty()).orEmpty()
        if (ownerMid.isNotBlank() && ownerFace.isNotBlank()) {
            ownerFaceCache[ownerMid] = ownerFace
        }
        val cover = normalizeUrl(view.optString("pic", ""))
        val pages = view.optJSONArray("pages") ?: JSONArray()
        val aid = view.opt("aid")?.toString()?.takeIf { it != "null" }.orEmpty()
        val totalPages = pages.length().coerceAtLeast(1)
        if (pages.length() == 0) {
            val cid = view.opt("cid")?.toString().orEmpty()
            return listOf(
                Track(
                    id = if (cid.isNotBlank()) "${bvid}_$cid" else bvid,
                    title = title,
                    artist = owner,
                    album = "Bilibili",
                    coverUrl = cover,
                    durationMs = view.optLong("duration", 0L) * 1000,
                    source = MusicSourceType.BILIBILI,
                    bvid = bvid,
                    aid = aid,
                    cid = cid,
                    ownerMid = ownerMid,
                    ownerFace = ownerFace,
                    pageCount = 1,
                    categoryId = view.optInt("tid", view.optInt("typeid", 0)),
                    categoryName = stripHtml(
                        view.optString("tname", view.optString("typename", "")),
                    ),
                ),
            )
        }
        return buildList {
            for (i in 0 until pages.length()) {
                val p = pages.optJSONObject(i) ?: continue
                val cid = p.opt("cid")?.toString().orEmpty()
                val part = p.optString("part", "")
                val pageNo = p.optInt("page", i + 1)
                val multi = pages.length() > 1
                val name = if (multi) {
                    if (part.isNotBlank()) "$title · P$pageNo $part" else "$title · P$pageNo"
                } else title
                add(
                    Track(
                        id = if (cid.isNotBlank()) "${bvid}_$cid" else "$bvid-$pageNo",
                        title = name,
                        artist = owner,
                        album = if (multi) title else "Bilibili",
                        pageCount = totalPages,
                        coverUrl = cover,
                        durationMs = p.optLong("duration", 0L) * 1000,
                        source = MusicSourceType.BILIBILI,
                        bvid = bvid,
                        aid = aid,
                        cid = cid,
                        ownerMid = ownerMid,
                        ownerFace = ownerFace,
                        categoryId = view.optInt("tid", view.optInt("typeid", 0)),
                        categoryName = stripHtml(
                            view.optString("tname", view.optString("typename", "")),
                        ),
                    ),
                )
            }
        }
    }

    /** 纯 progressive（音画同文件），适合视频模式。 */
    private fun pickProgressiveUrl(data: JSONObject): String {
        val durl = data.optJSONArray("durl")
        if (durl != null && durl.length() > 0) {
            val u = durl.optJSONObject(0)?.optString("url", "").orEmpty()
            if (u.isNotBlank()) return u
        }
        return ""
    }

    /**
     * 视频模式取流：优先 progressive mp4；否则 dash 视频轨（可能无音，尽量 progressive）。
     */
    private fun pickVideoUrl(data: JSONObject, preferHigher: Boolean = true): String {
        val progressive = pickProgressiveUrl(data)
        if (progressive.isNotBlank()) return progressive
        val dash = data.optJSONObject("dash") ?: return ""
        val videos = dash.optJSONArray("video") ?: return ""
        var bestUrl = ""
        var bestBw = if (preferHigher) -1L else Long.MAX_VALUE
        for (i in 0 until videos.length()) {
            val v = videos.optJSONObject(i) ?: continue
            // 跳过纯杜比视界等特殊轨
            val codecs = v.optString("codecs", "")
            if (codecs.contains("dvh1", ignoreCase = true)) continue
            val bw = v.optLong("bandwidth", 0L)
            val candidates = buildList {
                add(v.optString("baseUrl", v.optString("base_url", "")))
                val bu = v.optJSONArray("backupUrl") ?: v.optJSONArray("backup_url")
                if (bu != null) {
                    for (j in 0 until bu.length()) add(bu.optString(j, ""))
                }
            }.filter { it.isNotBlank() }
            if (candidates.isEmpty()) continue
            val better = if (preferHigher) bw >= bestBw else bw <= bestBw
            if (better) {
                bestBw = bw
                bestUrl = candidates.first()
            }
        }
        return bestUrl
    }

    /** 听歌：dash.audio / flac / dolby 按码率选；progressive 仅兜底。 */
    private fun pickPlayableUrl(data: JSONObject, preferHigher: Boolean = true): String {
        val dash = data.optJSONObject("dash")
        if (dash != null) {
            val tracks = ArrayList<JSONObject>(8)
            dash.optJSONArray("audio")?.let { arr ->
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let(tracks::add)
            }
            dash.optJSONObject("flac")?.let { flac ->
                flac.optJSONObject("audio")?.let(tracks::add)
                if (flacUrl(flac).isNotBlank()) tracks.add(flac)
            }
            dash.optJSONObject("dolby")?.optJSONArray("audio")?.let { arr ->
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let(tracks::add)
            }
            var bestUrl = ""
            var bestBw = if (preferHigher) -1L else Long.MAX_VALUE
            for (a in tracks) {
                val bw = a.optLong("bandwidth", 0L)
                val candidates = dashAudioUrls(a)
                if (candidates.isEmpty()) continue
                val better = if (preferHigher) bw >= bestBw else bw <= bestBw
                if (better) {
                    bestBw = bw
                    bestUrl = candidates.first()
                }
            }
            if (bestUrl.isNotBlank()) return bestUrl
        }
        return pickProgressiveUrl(data)
    }

    private fun flacUrl(o: JSONObject): String =
        o.optString("baseUrl", o.optString("base_url", ""))

    private fun dashAudioUrls(a: JSONObject): List<String> = buildList {
        add(a.optString("baseUrl", a.optString("base_url", "")))
        val bu = a.optJSONArray("backupUrl") ?: a.optJSONArray("backup_url")
        if (bu != null) {
            for (j in 0 until bu.length()) add(bu.optString(j, ""))
        }
    }.filter { it.isNotBlank() }

    private fun getJson(url: String, cookie: String, referer: String = "https://www.bilibili.com"): JSONObject {
        val conn = open(url, cookie, referer)
        conn.requestMethod = "GET"
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.use { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() }.orEmpty()
        if (text.isBlank()) return JSONObject().put("code", code).put("message", "EMPTY_HTTP_$code")
        return runCatching { JSONObject(text) }.getOrElse {
            JSONObject().put("code", -1).put("message", "BAD_JSON")
        }
    }

    private fun postForm(
        url: String,
        formBody: String,
        cookie: String,
        referer: String = "https://www.bilibili.com",
    ): JSONObject {
        val conn = open(url, cookie, referer)
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        conn.outputStream.use { it.write(formBody.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.use { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() }.orEmpty()
        if (text.isBlank()) return JSONObject().put("code", code).put("message", "EMPTY_HTTP_$code")
        return runCatching { JSONObject(text) }.getOrElse {
            JSONObject().put("code", -1).put("message", "BAD_JSON")
        }
    }

    private fun open(url: String, cookie: String, referer: String = "https://www.bilibili.com"): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 25_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Referer", referer)
            setRequestProperty("Origin", "https://www.bilibili.com")
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            if (cookie.isNotBlank()) setRequestProperty("Cookie", cookie)
        }
    }

    companion object {
        private const val TAG = "BilibiliApi"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        /**
         * 只取「像歌」的子区：
         * 28 原创 / 31 翻唱 / 30 VOCALOID / 59 演奏 / 193 MV
         * 不要 130 音乐综合（杂）、244 教学、26 音游、243 乐评
         */
        private val MUSIC_SUB_RIDS = intArrayOf(28, 31, 30, 59, 193)

        /** 合法音乐 tid */
        val MUSIC_TID_SET: Set<Int> = setOf(3, 28, 31, 30, 59, 193, 29)

        private val BV_PATTERN = Pattern.compile("BV[0-9A-Za-z]+")

        /** WBI mixin 乱序表（官方固定） */
        private val WBI_MIXIN_TABLE = intArrayOf(
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
            27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
            37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
            22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52,
        )

        fun parseBvid(input: String): String? {
            val m = BV_PATTERN.matcher(input)
            return if (m.find()) m.group() else null
        }

        fun normalizeUrl(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            var u = raw.trim()
            if (u.startsWith("//")) u = "https:$u"
            if (u.startsWith("http://")) u = "https://" + u.removePrefix("http://")
            return u
        }

        fun stripHtml(s: String): String =
            s.replace(Regex("<[^>]+>"), "").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")

        fun parseDurationToMs(text: String): Long {
            val t = text.trim()
            if (t.isEmpty()) return 0L
            if (t.all { it.isDigit() }) return t.toLongOrNull()?.times(1000) ?: 0L
            val parts = t.split(':').mapNotNull { it.toLongOrNull() }
            return when (parts.size) {
                3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
                2 -> (parts[0] * 60 + parts[1]) * 1000
                1 -> parts[0] * 1000
                else -> 0L
            }
        }
    }
}
