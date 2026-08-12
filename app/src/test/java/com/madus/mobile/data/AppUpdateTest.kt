package com.madus.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {

    @Test
    fun compareVersionTreatsPatchAsNumber() {
        assertTrue(AppUpdate.compareVersion("1.14.42", "1.14.40") > 0)
        assertTrue(AppUpdate.compareVersion("1.14.40", "1.14.9") > 0)
        assertTrue(AppUpdate.compareVersion("1.14.1", "1.14.40") < 0)
        assertEquals(0, AppUpdate.compareVersion("1.14.40", "1.14.40"))
    }

    @Test
    fun pickHighestIgnoresGiteeTagStringOrder() {
        val listedAsGiteeDoes = listOf(
            release("v1.14.1"),
            release("v1.14.10"),
            release("v1.14.11"),
            release("v1.14.2"),
            release("v1.14.42"),
            release("v1.14.5"),
        )
        val best = AppUpdate.pickHighestRelease(listedAsGiteeDoes)
        assertEquals("1.14.42", best?.versionName)
    }

    @Test
    fun firstListItemIsNotLatest() {
        val first = release("v1.14.1")
        val current = "1.14.40"
        assertFalse(
            AppUpdate.compareVersion(
                AppUpdate.normalizeVersion(first.versionName),
                AppUpdate.normalizeVersion(current),
            ) > 0,
        )
    }

    @Test
    fun normalizeStripsPrefixAndSuffix() {
        assertEquals("1.14.42", AppUpdate.normalizeVersion("v1.14.42"))
        assertEquals("1.14.42", AppUpdate.normalizeVersion("1.14.42-debug"))
        assertEquals("1.14.42", AppUpdate.normalizeVersion("V1.14.42_release"))
    }

    private fun release(tag: String) = AppUpdate.LatestRelease(
        tag = tag,
        versionName = tag.removePrefix("v"),
        apkUrl = "https://example.com/$tag.apk",
        apkName = "Madus-${tag.removePrefix("v")}.apk",
        body = "",
    )
}
