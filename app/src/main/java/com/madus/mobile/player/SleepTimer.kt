package com.madus.mobile.player

/**
 * 睡眠定时档位。预设固定四档；其它正数走用户自定义。
 */
object SleepTimer {
    val PRESET_MINUTES: List<Int> = listOf(0, 15, 30, 45, 60)

    const val CUSTOM_MIN = 1
    const val CUSTOM_MAX = 360

    fun isPreset(minutes: Int): Boolean = minutes in PRESET_MINUTES

    fun isCustom(minutes: Int): Boolean = minutes > 0 && !isPreset(minutes)

    fun sanitize(minutes: Int): Int {
        if (minutes <= 0) return 0
        return minutes.coerceIn(CUSTOM_MIN, CUSTOM_MAX)
    }

    fun parseCustom(raw: String): Int? {
        val n = raw.trim().toIntOrNull() ?: return null
        return n.takeIf { it in CUSTOM_MIN..CUSTOM_MAX }
    }
}
