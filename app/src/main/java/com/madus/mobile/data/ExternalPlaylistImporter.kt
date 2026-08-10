package com.madus.mobile.data

import android.util.Log
import com.madus.mobile.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.regex.Pattern

/**
 * 外站歌单导入：解析主流平台链接，或「歌名 - 歌手」多行文本，
 * 再用 B 站搜索落到可播 Track（不做第三方播放源）。
 *
 * 支持：网易云 / QQ 音乐 / 酷狗 / 酷我 链接；其它平台请粘贴多行歌名歌手。
 */
class ExternalPlaylistImporter(
    private val biliApi: BilibiliApi,
) {
    data class SourceSong(
        val title: String,
        val artist: String = "",
    )

    data class ParsedPlaylist(
        val platform: String,
        val title: String,
        val songs: List<SourceSong>,
    )

    data class ImportResult(
        val playlistTitle: String,
        val platform: String,
        val matched: List<Track>,
        val failed: List<SourceSong>,
        val message: String,
    )

    suspend fun importPlaylist(
        input: String,
        /** 默认 2000：网易/QQ 大歌单可进；过大易被 B 站搜索限流且导入很久 */
        maxSongs: Int = 2000,
        onProgress: (done: Int, total: Int, label: String) -> Unit = { _, _, _ -> },
    ): ImportResult = withContext(Dispatchers.IO) {
        val raw = input.trim()
        if (raw.isBlank()) {
            return@withContext ImportResult("", "", emptyList(), emptyList(), "请粘贴链接或歌名歌手文本")
        }
        val parsed = parse(raw)
            ?: return@withContext ImportResult(
                "",
                "",
                emptyList(),
                emptyList(),
                "无法识别。可贴网易云/QQ/酷狗/酷我歌单链接，或每行「歌名 - 歌手」",
            )
        val cap = maxSongs.coerceIn(1, 3000)
        val totalParsed = parsed.songs.size
        val songs = parsed.songs.take(cap)
        if (songs.isEmpty()) {
            return@withContext ImportResult(
                parsed.title,
                parsed.platform,
                emptyList(),
                emptyList(),
                "歌单是空的或未能解析曲目（可改贴「歌名 - 歌手」多行）",
            )
        }
        val matched = mutableListOf<Track>()
        val failed = mutableListOf<SourceSong>()
        songs.forEachIndexed { index, song ->
            onProgress(index, songs.size, song.title)
            val hit = matchToBili(song)
            if (hit != null) {
                matched.add(hit.copy(album = "导入·${parsed.platform}"))
            } else {
                failed.add(song)
            }
            // 大歌单略加快，减轻总耗时；仍留间隔降低 B 站搜索压力
            delay(if (songs.size > 400) 60L else 100L)
        }
        onProgress(songs.size, songs.size, "完成")
        val msg = buildString {
            append("${parsed.platform}「${parsed.title}」")
            append(" · 命中 ${matched.size}/${songs.size}")
            if (failed.isNotEmpty()) append(" · 未匹配 ${failed.size}")
            if (totalParsed > songs.size) {
                append(" · 原歌单 ${totalParsed} 首，本次导入前 ${songs.size} 首")
            }
        }
        ImportResult(
            playlistTitle = parsed.title.ifBlank { "导入歌单" },
            platform = parsed.platform,
            matched = matched,
            failed = failed,
            message = msg,
        )
    }

    /**
     * 外站歌名 → B 站可播（恢复早期高召回逻辑）。
     * 搜「歌名 歌手」拿前几条，优先标题含歌名的；没有再裸搜歌名。
     * 仅轻度清洗 VIP/括号噪声，不再做严苛打分阉割。
     */
    private suspend fun matchToBili(song: SourceSong): Track? {
        val title = cleanSongTitle(song.title).ifBlank { song.title.trim() }
        if (title.isBlank()) return null
        val artist = song.artist
            .split(Regex("""[/、&,，]|feat\.?|ft\.?""", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

        val primaryQ = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
        val hits = runCatching { biliApi.search(primaryQ, limit = 6) }
            .getOrDefault(emptyList())
            .ifEmpty {
                if (artist.isNotBlank()) {
                    runCatching { biliApi.search(title, limit = 6) }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
            }
        if (hits.isEmpty()) return null

        // 在结果里轻轻挑：标题含歌名优先，否则就用第一条（和最初版一致）
        val titleKey = title.lowercase().filter {
            it.isLetterOrDigit() || it.code in 0x4E00..0x9FFF
        }
        return hits.firstOrNull { t ->
            if (titleKey.length < 2) return@firstOrNull true
            val ht = t.title.lowercase().filter {
                it.isLetterOrDigit() || it.code in 0x4E00..0x9FFF
            }
            ht.contains(titleKey)
        } ?: hits.first()
    }

    /** 轻度清洗：VIP 角标 / 括号备注，避免搜词过脏；不过度剥离 */
    private fun cleanSongTitle(raw: String): String {
        var t = raw.trim()
        t = t.replace(Regex("""[（(【\[]\s*(?i)(vip|付费|数字专辑|独家|flac|sq|hq)\s*[）)】\]]"""), " ")
        t = t.replace(Regex("""(?i)\bvip\b"""), " ")
        t = t.replace(Regex("""^\d{1,3}[\.、\)\]\s]+"""), "")
        t = t.replace(Regex("""\s+"""), " ").trim()
        if (t.isBlank()) t = raw.trim()
        return t.take(80)
    }

    fun parse(input: String): ParsedPlaylist? {
        val text = input.trim()
        // 1) URL
        extractUrl(text)?.let { url ->
            when {
                isNetease(url) -> return parseNetease(url)
                isQq(url) -> return parseQq(url)
                isKugou(url) -> return parseKugou(url, text)
                isKuwo(url) -> return parseKuwo(url)
                isMigu(url) -> return parseMigu(url, text)
                else -> {
                    // 未知平台链接：尝试从分享正文抠歌名；链本身跳过
                    val fromBody = parseTextSongs(text)
                    if (fromBody.isNotEmpty()) {
                        return ParsedPlaylist("外站文本", "导入歌单", fromBody)
                    }
                }
            }
        }
        // 2) 纯文本多行「歌名 - 歌手」
        val lines = parseTextSongs(text)
        if (lines.size >= 1 && (text.lines().size >= 2 || lines.first().artist.isNotBlank())) {
            return ParsedPlaylist("文本", "导入歌单", lines)
        }
        if (lines.size == 1) {
            return ParsedPlaylist("文本", lines.first().title, lines)
        }
        return null
    }

    private fun extractUrl(text: String): String? {
        val m = Pattern.compile(
            """https?://[^\s<>"']+""",
            Pattern.CASE_INSENSITIVE,
        ).matcher(text)
        return if (m.find()) m.group().trimEnd('.', ',', ')', ']', '」', '》') else null
    }

    private fun isNetease(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("music.163.com") || u.contains("163cn.tv") || u.contains("y.music.163")
    }

    private fun isQq(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("y.qq.com") || u.contains("i.y.qq.com") || u.contains("c.y.qq.com")
    }

    private fun isKugou(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("kugou.com") || u.contains("t1.kugou.com")
    }

    private fun isKuwo(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("kuwo.cn") || u.contains("kwcdn")
    }

    private fun isMigu(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("migu.cn") || u.contains("music.migu")
    }

    private fun parseNetease(url: String): ParsedPlaylist? {
        val id = extractQueryId(url, "id")
            ?: Regex("""playlist(?:/|\\?id=)(\d+)""", RegexOption.IGNORE_CASE)
                .find(url)?.groupValues?.getOrNull(1)
            ?: return null
        // playlist/detail 的 tracks 常只返回前 10 首；完整列表在 trackIds，需 song/detail 批量补全
        val endpoints = listOf(
            "https://music.163.com/api/v6/playlist/detail?id=$id&n=100000",
            "https://music.163.com/api/playlist/detail?id=$id&n=100000",
            "https://music.163.com/api/v6/playlist/detail?id=$id",
        )
        for (ep in endpoints) {
            val json = httpGetJson(ep, referer = "https://music.163.com/")
            val playlist = json?.optJSONObject("playlist")
                ?: json?.optJSONObject("result")
            if (playlist == null) continue
            val title = playlist.optString("name", "网易云歌单")
            val songs = linkedMapOf<Long, SourceSong>()

            fun absorbTrackObj(t: JSONObject) {
                val name = t.optString("name", "")
                if (name.isBlank()) return
                val tid = t.optLong("id", 0L)
                val ar = t.optJSONArray("ar") ?: t.optJSONArray("artists")
                val artist = buildString {
                    if (ar != null) {
                        for (j in 0 until ar.length()) {
                            val n = ar.optJSONObject(j)?.optString("name", "").orEmpty()
                            if (n.isNotBlank()) {
                                if (isNotEmpty()) append(" / ")
                                append(n)
                            }
                        }
                    }
                }
                val key = if (tid > 0) tid else (name.hashCode().toLong() and 0x7fffffff)
                songs[key] = SourceSong(name, artist)
            }

            val tracks = playlist.optJSONArray("tracks")
            if (tracks != null) {
                for (i in 0 until tracks.length()) {
                    tracks.optJSONObject(i)?.let { absorbTrackObj(it) }
                }
            }

            val trackIds = playlist.optJSONArray("trackIds") ?: JSONArray()
            val allIds = buildList {
                for (i in 0 until trackIds.length()) {
                    val o = trackIds.optJSONObject(i)
                    val tid = o?.optLong("id", 0L) ?: trackIds.optLong(i, 0L)
                    if (tid > 0) add(tid)
                }
            }
            val missing = allIds.filter { it !in songs }
            if (missing.isNotEmpty()) {
                for (chunk in missing.chunked(80)) {
                    val idsParam = chunk.joinToString(",")
                    val cJson = chunk.joinToString(",", prefix = "[", postfix = "]") { """{"id":$it}""" }
                    val detailUrls = listOf(
                        "https://music.163.com/api/v3/song/detail?c=${URLEncoder.encode(cJson, "UTF-8")}",
                        "https://music.163.com/api/song/detail?ids=$idsParam",
                    )
                    for (du in detailUrls) {
                        val djson = httpGetJson(du, referer = "https://music.163.com/")
                        val songsArr = djson?.optJSONArray("songs") ?: continue
                        for (i in 0 until songsArr.length()) {
                            songsArr.optJSONObject(i)?.let { absorbTrackObj(it) }
                        }
                        if (songs.size >= allIds.size.coerceAtLeast(songs.size)) break
                    }
                }
            }

            val list = if (allIds.isNotEmpty()) {
                allIds.mapNotNull { songs[it] }
            } else {
                songs.values.toList()
            }
            if (list.isNotEmpty()) {
                Log.i(TAG, "netease playlist $id songs=${list.size} trackIds=${allIds.size}")
                return ParsedPlaylist("网易云", title, list)
            }
        }
        Log.w(TAG, "netease parse empty id=$id")
        return ParsedPlaylist("网易云", "网易云歌单", emptyList())
    }

    private fun parseQq(url: String): ParsedPlaylist? {
        val id = extractQueryId(url, "id")
            ?: extractQueryId(url, "disstid")
            ?: Regex("""playlist/([0-9A-Za-z]+)""").find(url)?.groupValues?.getOrNull(1)
            ?: Regex("""taoge.*?[?&]id=([0-9]+)""").find(url)?.groupValues?.getOrNull(1)
            ?: return null
        val endpoints = listOf(
            "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg" +
                "?type=1&json=1&utf8=1&onlysong=0&new_format=1&disstid=$id",
            "https://c.y.qq.com/v8/fcg-bin/fcg_v8_playlist_cp.fcg" +
                "?id=$id&format=json&newsong=1",
        )
        for (ep in endpoints) {
            val body = httpGetText(ep, referer = "https://y.qq.com/")
            if (body.isNullOrBlank()) continue
            val jsonText = body
                .removePrefix("callback(")
                .removePrefix("jsonCallback(")
                .trim()
                .let { if (it.endsWith(")")) it.dropLast(1) else it }
            val json = runCatching { JSONObject(jsonText) }.getOrNull() ?: continue
            val cdlist = json.optJSONArray("cdlist")?.optJSONObject(0)
            val data = json.optJSONObject("data")
            val title = cdlist?.optString("dissname")
                ?: cdlist?.optString("nickname")
                ?: data?.optString("dissname", "QQ音乐歌单")
                ?: "QQ音乐歌单"
            val songlist = cdlist?.optJSONArray("songlist")
                ?: data?.optJSONArray("songlist")
                ?: JSONArray()
            val songs = mutableListOf<SourceSong>()
            for (i in 0 until songlist.length()) {
                val s = songlist.optJSONObject(i) ?: continue
                val name = s.optString("songname", s.optString("name", ""))
                if (name.isBlank()) continue
                val singerArr = s.optJSONArray("singer")
                val artist = buildString {
                    if (singerArr != null) {
                        for (j in 0 until singerArr.length()) {
                            val n = singerArr.optJSONObject(j)?.optString("name", "").orEmpty()
                            if (n.isNotBlank()) {
                                if (isNotEmpty()) append(" / ")
                                append(n)
                            }
                        }
                    } else {
                        append(s.optString("singername", ""))
                    }
                }
                songs.add(SourceSong(name, artist))
            }
            if (songs.isNotEmpty()) {
                return ParsedPlaylist("QQ音乐", title.ifBlank { "QQ音乐歌单" }, songs)
            }
        }
        return ParsedPlaylist("QQ音乐", "QQ音乐歌单", emptyList())
    }

    /** 酷狗歌单：specialid / global_collection_id */
    private fun parseKugou(url: String, fullText: String): ParsedPlaylist? {
        val specialId = extractQueryId(url, "specialid")
            ?: extractQueryId(url, "specialId")
            ?: Regex("""special/single/(\d+)""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.getOrNull(1)
            ?: Regex("""/songlist/(\d+)""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.getOrNull(1)
        val globalId = extractQueryId(url, "global_collection_id")
            ?: extractQueryId(url, "globalCollectionId")
            ?: Regex("""collection/([0-9a-zA-Z_\-]+)""").find(url)?.groupValues?.getOrNull(1)

        val endpoints = buildList {
            if (!specialId.isNullOrBlank()) {
                add(
                    "https://mobilecdnbj.kugou.com/api/v3/special/song" +
                        "?version=9108&specialid=$specialId&page=1&pagesize=300&area_code=1&plat=0",
                )
            }
            if (!globalId.isNullOrBlank()) {
                add(
                    "https://m.kugou.com/plist/list?json=true&specialid=$globalId",
                )
            }
        }
        for (ep in endpoints) {
            val json = httpGetJson(ep, referer = "https://www.kugou.com/") ?: continue
            val data = json.optJSONObject("data") ?: json
            val list = data.optJSONArray("info")
                ?: data.optJSONArray("list")
                ?: data.optJSONObject("info")?.optJSONArray("list")
                ?: JSONArray()
            val songs = mutableListOf<SourceSong>()
            for (i in 0 until list.length()) {
                val s = list.optJSONObject(i) ?: continue
                // filename 常为 "歌手 - 歌名"
                val filename = s.optString("filename", s.optString("fileName", ""))
                val name = s.optString("songname", s.optString("song_name", ""))
                val author = s.optString("author_name", s.optString("singername", ""))
                when {
                    name.isNotBlank() -> songs.add(SourceSong(name, author))
                    filename.contains(" - ") -> {
                        val parts = filename.split(" - ", limit = 2)
                        songs.add(SourceSong(parts.getOrElse(1) { filename }.trim(), parts[0].trim()))
                    }
                    filename.isNotBlank() -> songs.add(SourceSong(filename, author))
                }
            }
            if (songs.isNotEmpty()) {
                val title = data.optString("specialname", data.optString("name", "酷狗歌单"))
                return ParsedPlaylist("酷狗", title.ifBlank { "酷狗歌单" }, songs)
            }
        }
        // 链接解析失败：从分享文案抠
        val fromText = parseTextSongs(fullText)
        if (fromText.isNotEmpty()) {
            return ParsedPlaylist("酷狗", "酷狗导入", fromText)
        }
        Log.w(TAG, "kugou parse empty url=$url")
        return ParsedPlaylist("酷狗", "酷狗歌单", emptyList())
    }

    /** 酷我歌单 pid */
    private fun parseKuwo(url: String): ParsedPlaylist? {
        val pid = extractQueryId(url, "pid")
            ?: extractQueryId(url, "id")
            ?: Regex("""playlist_detail/(\d+)""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.getOrNull(1)
            ?: Regex("""/playlist/(\d+)""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.getOrNull(1)
            ?: Regex("""pl\.?id=(\d+)""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.getOrNull(1)
            ?: return null
        val endpoints = listOf(
            "http://nplserver.kuwo.cn/pl.svc?op=getlistinfo&pid=$pid&pn=0&rn=300" +
                "&encode=utf8&keyset=pl2012&identity=kuwo&pcmp4=1&vipver=1&newver=1",
            "https://wapi.kuwo.cn/api/www/playlist/playListInfo?pid=$pid&pn=1&rn=300",
        )
        for (ep in endpoints) {
            val body = httpGetText(ep, referer = "https://www.kuwo.cn/")
            if (body.isNullOrBlank()) continue
            val json = runCatching { JSONObject(body) }.getOrNull() ?: continue
            val title = json.optString("title", json.optString("name", ""))
                .ifBlank {
                    json.optJSONObject("data")?.optString("name", "酷我歌单") ?: "酷我歌单"
                }
            val musiclist = json.optJSONArray("musiclist")
                ?: json.optJSONObject("data")?.optJSONArray("musicList")
                ?: json.optJSONObject("data")?.optJSONArray("musiclist")
                ?: JSONArray()
            val songs = mutableListOf<SourceSong>()
            for (i in 0 until musiclist.length()) {
                val s = musiclist.optJSONObject(i) ?: continue
                val name = s.optString("name", s.optString("SONGNAME", s.optString("songName", "")))
                if (name.isBlank()) continue
                val artist = s.optString(
                    "artist",
                    s.optString("ARTIST", s.optString("artistName", "")),
                )
                songs.add(SourceSong(name, artist))
            }
            if (songs.isNotEmpty()) {
                return ParsedPlaylist("酷我", title.ifBlank { "酷我歌单" }, songs)
            }
        }
        return ParsedPlaylist("酷我", "酷我歌单", emptyList())
    }

    /** 咪咕：公开接口不稳，优先分享文案 */
    private fun parseMigu(url: String, fullText: String): ParsedPlaylist? {
        val fromText = parseTextSongs(fullText)
        if (fromText.isNotEmpty()) {
            return ParsedPlaylist("咪咕", "咪咕导入", fromText)
        }
        // 尝试 playlist id
        val id = extractQueryId(url, "id")
            ?: Regex("""playlist/([0-9]+)""").find(url)?.groupValues?.getOrNull(1)
        if (!id.isNullOrBlank()) {
            val ep =
                "https://app.pd.nf.migu.cn/MIGUM3.0/resource/playlist/v2.0" +
                    "?playlistId=$id&pageNo=1&pageSize=100"
            val json = httpGetJson(ep, referer = "https://music.migu.cn/")
            val list = json?.optJSONObject("data")?.optJSONArray("songList")
                ?: json?.optJSONArray("data")
                ?: JSONArray()
            val songs = mutableListOf<SourceSong>()
            for (i in 0 until list.length()) {
                val s = list.optJSONObject(i) ?: continue
                val name = s.optString("songName", s.optString("name", ""))
                if (name.isBlank()) continue
                val singers = s.optJSONArray("singerList") ?: s.optJSONArray("artists")
                val artist = buildString {
                    if (singers != null) {
                        for (j in 0 until singers.length()) {
                            val n = singers.optJSONObject(j)?.optString("name", "").orEmpty()
                            if (n.isNotBlank()) {
                                if (isNotEmpty()) append(" / ")
                                append(n)
                            }
                        }
                    } else {
                        append(s.optString("singer", ""))
                    }
                }
                songs.add(SourceSong(name, artist))
            }
            if (songs.isNotEmpty()) {
                return ParsedPlaylist("咪咕", "咪咕歌单", songs)
            }
        }
        Log.w(TAG, "migu parse empty, paste 歌名-歌手 text")
        return ParsedPlaylist("咪咕", "咪咕歌单", emptyList())
    }

    private fun parseTextSongs(text: String): List<SourceSong> {
        val out = mutableListOf<SourceSong>()
        // 《歌名》歌手 / 《歌名》 - 歌手
        val book = Regex("""《([^》]+)》\s*[-–—]?\s*([^\n《]+)""")
        book.findAll(text).forEach { m ->
            val title = m.groupValues[1].trim()
            val artist = m.groupValues[2].trim()
                .removePrefix("-").removePrefix("–").trim()
            if (title.isNotBlank()) out.add(SourceSong(title, artist.take(40)))
        }
        if (out.isNotEmpty()) return out.distinctBy { it.title to it.artist }

        for (line in text.lines()) {
            var t = line.trim()
            if (t.isBlank()) continue
            if (t.startsWith("http", ignoreCase = true)) continue
            if (t.length > 120) continue
            // 分享尾巴
            if (t.contains("分享") && t.length < 24) continue
            if (t.contains("复制此链接") || t.contains("打开") && t.contains("音乐")) continue
            // 编号：1. / 01、 / 1) / (1)
            t = t.replace(Regex("""^[\(（]?\d{1,3}[\)）.、\.\]\s]+"""), "").trim()
            t = t.removePrefix("·").removePrefix("•").removePrefix("-").trim()
            if (t.isBlank()) continue
            val parts = t.split(Regex("""\s*[-–—|/｜]\s*"""), limit = 2)
            when {
                parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank() -> {
                    // 默认「歌名 - 歌手」；若左侧像歌手（更短且含常见特征）也接受
                    out.add(SourceSong(parts[0].trim(), parts[1].trim()))
                }
                t.isNotBlank() && !t.contains("http") ->
                    out.add(SourceSong(t, ""))
            }
        }
        return out.distinctBy { it.title to it.artist }.take(3000)
    }

    private fun extractQueryId(url: String, key: String): String? {
        val decoded = runCatching { URLDecoder.decode(url, "UTF-8") }.getOrDefault(url)
        val m = Regex("""[?&#]$key=([^&#\s]+)""", RegexOption.IGNORE_CASE).find(decoded)
            ?: Regex("""[?&]$key=([^&#\s]+)""", RegexOption.IGNORE_CASE).find(decoded)
        return m?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    private fun httpGetJson(url: String, referer: String): JSONObject? {
        val text = httpGetText(url, referer) ?: return null
        return runCatching { JSONObject(text) }.getOrNull()
    }

    private fun httpGetText(url: String, referer: String): String? {
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 18_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                )
                setRequestProperty("Referer", referer)
                setRequestProperty("Accept", "application/json, text/plain, */*")
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            stream?.use { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() }
        }.onFailure { Log.w(TAG, "http fail ${it.message}") }.getOrNull()
    }

    companion object {
        private const val TAG = "ExtPlImport"
    }
}
