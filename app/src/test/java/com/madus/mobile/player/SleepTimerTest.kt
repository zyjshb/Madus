package com.madus.mobile.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerTest {

    @Test
    fun presetsAreFixedFourPlusOff() {
        assertEquals(listOf(0, 15, 30, 45, 60), SleepTimer.PRESET_MINUTES)
        assertTrue(SleepTimer.isPreset(15))
        assertFalse(SleepTimer.isPreset(90))
    }

    @Test
    fun customIsAnyPositiveNonPreset() {
        assertTrue(SleepTimer.isCustom(1))
        assertTrue(SleepTimer.isCustom(90))
        assertTrue(SleepTimer.isCustom(360))
        assertFalse(SleepTimer.isCustom(0))
        assertFalse(SleepTimer.isCustom(30))
        assertFalse(SleepTimer.isCustom(-5))
    }

    @Test
    fun parseCustomAcceptsRangeOnly() {
        assertEquals(1, SleepTimer.parseCustom("1"))
        assertEquals(90, SleepTimer.parseCustom(" 90 "))
        assertEquals(360, SleepTimer.parseCustom("360"))
        assertNull(SleepTimer.parseCustom(""))
        assertNull(SleepTimer.parseCustom("abc"))
        assertNull(SleepTimer.parseCustom("0"))
        assertNull(SleepTimer.parseCustom("361"))
    }

    @Test
    fun sanitizeClampsOrTurnsOff() {
        assertEquals(0, SleepTimer.sanitize(0))
        assertEquals(0, SleepTimer.sanitize(-3))
        assertEquals(1, SleepTimer.sanitize(1))
        assertEquals(90, SleepTimer.sanitize(90))
        assertEquals(360, SleepTimer.sanitize(999))
    }
}
