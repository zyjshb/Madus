package com.madus.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTest {

    @Test
    fun pickPrefersChineseManualThenAi() {
        val ai = SubtitleChoice("ai-zh", "中文（自动生成）", "https://a")
        val zh = SubtitleChoice("zh-CN", "中文（中国）", "https://b")
        val en = SubtitleChoice("en-US", "English", "https://c")
        val picked = Lyrics.pickSubtitle(listOf(en, ai, zh))
        assertEquals("zh-CN", picked?.lan)
        assertEquals(ai, Lyrics.pickSubtitle(listOf(en, ai)))
    }

    @Test
    fun parseBodySkipsBlankAndSorts() {
        val lines = Lyrics.fromRawLines(
            listOf(
                Triple(3.0, 5.0, "后一句"),
                Triple(0.2, 2.5, "  第一句 \n 继续 "),
                Triple(5.0, 6.0, "   "),
            ),
        )
        assertEquals(2, lines.size)
        assertEquals(200L, lines[0].fromMs)
        assertEquals("第一句 继续", lines[0].text)
        assertEquals("后一句", lines[1].text)
    }

    @Test
    fun currentAndNextFollowsPosition() {
        val lines = listOf(
            LyricLine(0, 1000, "A"),
            LyricLine(1000, 2000, "B"),
            LyricLine(2000, 3000, "C"),
        )
        val before = Lyrics.currentAndNext(lines, 0)
        assertEquals("A", before.first?.text)
        assertEquals("B", before.second?.text)
        val mid = Lyrics.currentAndNext(lines, 1500)
        assertEquals("B", mid.first?.text)
        assertEquals("C", mid.second?.text)
        val early = Lyrics.currentAndNext(lines, -10)
        assertNull(early.first)
        assertEquals("A", early.second?.text)
    }

    @Test
    fun normalizeSubtitleUrl() {
        assertEquals("https://aisubtitle.hdslb.com/a.json", Lyrics.normalizeSubtitleUrl("//aisubtitle.hdslb.com/a.json"))
        assertEquals("https://i0.hdslb.com/x.json", Lyrics.normalizeSubtitleUrl("http://i0.hdslb.com/x.json"))
        assertTrue(Lyrics.normalizeSubtitleUrl("").isEmpty())
    }
}
