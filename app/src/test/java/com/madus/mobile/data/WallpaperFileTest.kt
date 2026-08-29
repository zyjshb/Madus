package com.madus.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WallpaperFileTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun dailyModeDownloadsDailyEvenIfOldPinRemains() {
        val pin = tmp.newFile("wallpaper.jpg").also { it.writeBytes(ByteArray(200) { 1 }) }
        val daily = tmp.newFile("daily.webp").also { it.writeBytes(ByteArray(200) { 2 }) }
        val got = resolveCurrentWallpaperFile(
            storedPath = daily.absolutePath,
            mode = WallpaperMode.Daily,
            pin = pin,
            daily = daily,
        )
        assertEquals(daily.absolutePath, got?.absolutePath)
    }

    @Test
    fun dailyModeFallsBackToDailyWhenStoredPathMissing() {
        val pin = tmp.newFile("wallpaper.jpg").also { it.writeBytes(ByteArray(200) { 1 }) }
        val daily = tmp.newFile("daily.webp").also { it.writeBytes(ByteArray(200) { 2 }) }
        val got = resolveCurrentWallpaperFile(
            storedPath = null,
            mode = WallpaperMode.Daily,
            pin = pin,
            daily = daily,
        )
        assertEquals(daily.absolutePath, got?.absolutePath)
    }

    @Test
    fun pinnedModeKeepsPinFile() {
        val pin = tmp.newFile("wallpaper.jpg").also { it.writeBytes(ByteArray(200) { 1 }) }
        val daily = tmp.newFile("daily.webp").also { it.writeBytes(ByteArray(200) { 2 }) }
        val got = resolveCurrentWallpaperFile(
            storedPath = pin.absolutePath,
            mode = WallpaperMode.Pinned,
            pin = pin,
            daily = daily,
        )
        assertEquals(pin.absolutePath, got?.absolutePath)
    }

    @Test
    fun emptyFilesAreIgnored() {
        val pin = tmp.newFile("wallpaper.jpg")
        val daily = tmp.newFile("daily.webp")
        assertNull(
            resolveCurrentWallpaperFile(
                storedPath = pin.absolutePath,
                mode = WallpaperMode.Pinned,
                pin = pin,
                daily = daily,
            ),
        )
    }

    @Test
    fun exportTypeFollowsExtension() {
        assertEquals("jpg" to "image/jpeg", wallpaperExportType(File("theme/wallpaper.jpg")))
        assertEquals("webp" to "image/webp", wallpaperExportType(File("theme/daily.webp")))
        assertEquals("png" to "image/png", wallpaperExportType(File("album.png")))
    }
}
