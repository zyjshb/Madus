package com.madus.mobile.player

import android.media.audiofx.Equalizer
import android.util.Log
import com.madus.mobile.data.SoundFx

/**
 * 系统 Equalizer 环境音效。会话级；设备/模拟器不支持时静默降级，绝不抛到主线程。
 */
class AudioFxController {
    private var equalizer: Equalizer? = null
    private var currentSession: Int = 0
    private var currentFx: SoundFx = SoundFx.Flat
    private var supported: Boolean = true

    fun attach(audioSessionId: Int) {
        if (!supported) return
        if (audioSessionId <= 0) return
        if (audioSessionId == currentSession && equalizer != null) {
            apply(currentFx)
            return
        }
        release()
        currentSession = audioSessionId
        equalizer = try {
            Equalizer(0, audioSessionId).also {
                // 部分模拟器 create 成功但 enable 即崩
                it.enabled = false
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Equalizer unsupported: ${t.message}")
            supported = false
            null
        }
        apply(currentFx)
    }

    fun setFx(fx: SoundFx) {
        currentFx = fx
        apply(fx)
    }

    fun release() {
        try {
            equalizer?.enabled = false
            equalizer?.release()
        } catch (_: Throwable) {
        }
        equalizer = null
        currentSession = 0
    }

    private fun apply(fx: SoundFx) {
        if (!supported) return
        val eq = equalizer ?: return
        try {
            val n = eq.numberOfBands.toInt()
            if (n <= 0) return
            val min = eq.bandLevelRange[0]
            val max = eq.bandLevelRange[1]
            val mid = 0.toShort()

            fun level(milliBel: Int): Short =
                milliBel.coerceIn(min.toInt(), max.toInt()).toShort()

            val shape: ShortArray = when (fx) {
                SoundFx.Flat -> ShortArray(5) { mid }
                SoundFx.Bass -> shortArrayOf(level(600), level(300), mid, level(-100), level(-150))
                SoundFx.Vocal -> shortArrayOf(level(-200), level(100), level(450), level(250), level(-100))
                SoundFx.Soft -> shortArrayOf(level(150), mid, level(-100), level(-250), level(-400))
                SoundFx.Night -> shortArrayOf(level(200), level(100), mid, level(-350), level(-550))
                SoundFx.Live -> shortArrayOf(level(150), level(100), mid, level(250), level(350))
            }

            for (i in 0 until n) {
                val src = if (n == 1) {
                    shape[2]
                } else {
                    val t = i.toFloat() / (n - 1).coerceAtLeast(1)
                    val idx = (t * (shape.size - 1)).toInt().coerceIn(0, shape.lastIndex)
                    shape[idx]
                }
                eq.setBandLevel(i.toShort(), src)
            }
            if (fx == SoundFx.Flat) {
                for (i in 0 until n) eq.setBandLevel(i.toShort(), mid)
                eq.enabled = false
            } else {
                eq.enabled = true
            }
        } catch (t: Throwable) {
            Log.w(TAG, "apply fx fail: ${t.message}")
            supported = false
            release()
        }
    }

    companion object {
        private const val TAG = "AudioFx"
    }
}
