package com.madus.mobile.together

import com.madus.mobile.domain.MusicSourceType
import com.madus.mobile.domain.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TogetherModelsTest {
    @Test
    fun `toWsUrl converts http https ws wss cleanly`() {
        assertEquals("ws://1.14.70.149:8080/ws", TogetherPrefs.toWsUrl("http://1.14.70.149:8080"))
        assertEquals("wss://example.com/ws", TogetherPrefs.toWsUrl("https://example.com/"))
        assertEquals("ws://10.0.0.2:8080/ws", TogetherPrefs.toWsUrl("10.0.0.2:8080"))
        assertEquals("wss://room.test/ws", TogetherPrefs.toWsUrl("wss://room.test/"))
    }

    @Test
    fun `expectedPositionMs computes anchor drift only while playing`() {
        val paused = TogetherPlaybackInfo(
            isPlaying = false,
            anchorTimeSec = 42.5,
            anchorServerTimeMs = 1_000L,
            playbackRate = 1.5f,
        )
        assertEquals(42_500L, paused.expectedPositionMs(serverNowMs = 10_000L))

        val playing = paused.copy(isPlaying = true)
        // 9s elapsed * 1.5x = 13.5s -> 42.5 + 13.5 = 56.0s
        assertEquals(56_000L, playing.expectedPositionMs(serverNowMs = 10_000L))
    }

    @Test
    fun `TogetherMediaCodec preserves fallback metadata without Uri dependency`() {
        val track = Track(
            id = "BV1xx411c7mD_12345",
            title = "测试曲目",
            artist = "UP主A",
            coverUrl = "https://i0.hdslb.com/test.jpg",
            durationMs = 215_000L,
            source = MusicSourceType.BILIBILI,
            bvid = "BV1xx411c7mD",
            aid = "9988",
            cid = "12345",
        )
        // 纯 JVM 单元测试里 android.net.Uri 未 mock 时主要验证 Bilibili URL 字符串匹配与 sourceTag 规则
        val fallback = TogetherPlaybackInfo(
            videoUrl = "https://www.bilibili.com/video/BV1xx411c7mD",
            title = track.title,
            durationSec = 215.0,
            source = "bili:BV1xx411c7mD:12345",
        )
        val decoded = TogetherMediaCodec.decodeTrack(fallback)
        assertEquals("BV1xx411c7mD", decoded?.bvid)
        assertEquals(215_000L, decoded?.durationMs)
        assertTrue(decoded?.source == MusicSourceType.BILIBILI)

        assertNull(TogetherMediaCodec.decodeTrack(TogetherPlaybackInfo(videoUrl = "")))
    }
}
