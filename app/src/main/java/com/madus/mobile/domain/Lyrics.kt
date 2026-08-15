package com.madus.mobile.domain

import org.json.JSONObject

data class LyricLine(
    val fromMs: Long,
    val toMs: Long,
    val text: String,
)

data class LyricSheet(
    val key: String,
    val language: String,
    val lines: List<LyricLine>,
)

data class LyricsUiState(
    val key: String = "",
    val lines: List<LyricLine> = emptyList(),
    val loading: Boolean = false,
    val unavailable: Boolean = false,
    val language: String = "",
)

data class SubtitleChoice(
    val lan: String,
    val lanDoc: String,
    val url: String,
)

object Lyrics {
    fun normalizeSubtitleUrl(raw: String): String {
        val t = raw.trim()
        if (t.isBlank()) return ""
        return when {
            t.startsWith("//") -> "https:$t"
            t.startsWith("http://") -> "https://${t.removePrefix("http://")}"
            else -> t
        }
    }

    fun pickSubtitle(choices: List<SubtitleChoice>): SubtitleChoice? =
        choices.filter { it.url.isNotBlank() }.maxByOrNull { score(it) }

    fun score(choice: SubtitleChoice): Int {
        val lan = choice.lan.lowercase()
        val doc = choice.lanDoc
        return when {
            lan == "zh-cn" || lan == "zh-hans" -> 100
            lan == "ai-zh" || lan.startsWith("ai-zh") -> 92
            doc.contains("中文") -> 88
            lan.startsWith("zh") -> 80
            lan.startsWith("en") -> 20
            else -> 10
        }
    }

    fun parseBody(json: JSONObject): List<LyricLine> {
        val body = json.optJSONArray("body") ?: return emptyList()
        return buildList {
            for (i in 0 until body.length()) {
                val o = body.optJSONObject(i) ?: continue
                add(
                    Triple(
                        o.optDouble("from", 0.0),
                        o.optDouble("to", 0.0),
                        o.optString("content"),
                    ),
                )
            }
        }.let(::fromRawLines)
    }

    fun fromRawLines(raw: List<Triple<Double, Double, String>>): List<LyricLine> =
        raw.mapNotNull { (fromSec, toSec, content) ->
            val text = content.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
            if (text.isBlank()) return@mapNotNull null
            val from = (fromSec * 1000.0).toLong().coerceAtLeast(0L)
            val to = (toSec * 1000.0).toLong().coerceAtLeast(from)
            LyricLine(fromMs = from, toMs = to, text = text)
        }.sortedBy { it.fromMs }

    fun currentAndNext(lines: List<LyricLine>, posMs: Long): Pair<LyricLine?, LyricLine?> {
        if (lines.isEmpty()) return null to null
        val i = lines.indexOfLast { posMs >= it.fromMs }
        if (i < 0) return null to lines.first()
        return lines[i] to lines.getOrNull(i + 1)
    }
}
