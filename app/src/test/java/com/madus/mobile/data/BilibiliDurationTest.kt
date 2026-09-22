package com.madus.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BilibiliDurationTest {
    @Test fun musicSearchAndDirectSearchAcceptBothDurationFormats() {
        assertEquals(225_000L, BilibiliApi.parseDurationToMs("3:45"))
        assertEquals(225_000L, BilibiliApi.parseDurationToMs("225"))
        assertEquals(225_000L, BilibiliApi.parseDurationToMs(" 225 "))
        assertEquals(0L, BilibiliApi.parseDurationToMs(""))
    }
}
