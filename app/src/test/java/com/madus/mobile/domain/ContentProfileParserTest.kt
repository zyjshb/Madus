package com.madus.mobile.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentProfileParserTest {

    @Test
    fun samePartitionCountsAsSameKind() {
        val a = Track(id = "1", title = "A", artist = "up", categoryId = 31)
        val b = Track(id = "2", title = "B", artist = "other", categoryId = 31)
        val c = Track(id = "3", title = "C", artist = "up", categoryId = 4)
        assertTrue(ContentProfileParser.sharesKind(a, b))
        assertFalse(ContentProfileParser.sharesKind(a, c))
    }

    @Test
    fun coverKeywordCountsAsSameKind() {
        val a = Track(id = "1", title = "夜曲 翻唱", artist = "up")
        val b = Track(id = "2", title = "晴天 cover", artist = "other")
        val c = Track(id = "3", title = "原神攻略", artist = "gamer")
        assertTrue(ContentProfileParser.sharesKind(a, b))
        assertFalse(ContentProfileParser.sharesKind(a, c))
    }
}
