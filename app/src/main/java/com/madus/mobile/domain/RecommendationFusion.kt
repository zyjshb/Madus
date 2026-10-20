package com.madus.mobile.domain

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.exp
import kotlin.math.ln

/**
 * 从汽水音乐 PC 3.8.0 逆向下来的三个可移植机制。原版推荐排序全在服务端，客户端只负责
 * 上报特征，所以这里移植的是**特征口径**而不是黑盒权重。
 *
 * 1. 三档重复收听窗口（10 分钟 / 1 小时 / 1 天）
 *    原版 `PlayerService.getTrackPlayCountMap` 用 `[600, 3600, 86400]` 三个窗口统计同一首的
 *    播放次数，并把 `played_mills`（实听毫秒）+ `duration` 塞进 30 条 `played_media` 回传。
 *    服务端据此区分「单曲循环式真爱」和「当天刷太多次导致的疲劳」。
 *
 * 2. 歌手级负反馈
 *    原版 `POST /luna/dislike` 的 body 是 `{item_id, author_ids[], action_type}`，
 *    负反馈作用域是「这首歌 + 这批作者」，而不是封掉整个曲风或上传者。
 *
 * 3. 每日 5:00 切换 + 0~1 小时随机抖动
 *    原版发现页/每日推荐都是「5 点后首次访问才刷新」，并对整批用户随机错峰。
 */
data class RepeatProfile(
    val inTenMinutes: Int = 0,
    val inHour: Int = 0,
    val inDay: Int = 0,
) {
    val seen: Boolean get() = inDay > 0 || inHour > 0 || inTenMinutes > 0
}

object RecommendationFusion {
    const val TEN_MINUTES_MS = 10 * 60 * 1000L
    const val ONE_HOUR_MS = 60 * 60 * 1000L
    const val ONE_DAY_MS = 24 * 60 * 60 * 1000L

    /** 单曲循环时 listenedMs 会累加超过时长；用它还原「实际播了几遍」，上限 5 遍避免脏数据放大。 */
    private const val MAX_REPEATS_PER_SESSION = 5

    fun repeatsOf(event: RecommendationEvent): Int {
        if (event.type != RecommendationEventType.LISTEN_SAMPLE) return 1
        if (event.durationMs <= 0 || event.listenedMs <= 0) return 1
        val ratio = event.listenedMs.toDouble() / event.durationMs
        return ratio.toInt().coerceIn(1, MAX_REPEATS_PER_SESSION)
    }

    /**
     * 按曲目汇总最近一天的重复收听。同一个 `listeningSessionId` 只算一次播放
     * （事件表本身只保留该 session 的最新快照），循环播放按 listenedMs / duration 折算。
     */
    fun repeatProfiles(events: List<RecommendationEvent>, nowMs: Long): Map<String, RepeatProfile> {
        if (events.isEmpty()) return emptyMap()
        data class Window(val tenMin: Int, val hour: Int, val day: Int)

        val perTrack = linkedMapOf<String, MutableList<Pair<Long, Int>>>()
        events.forEach { event ->
            if (event.type != RecommendationEventType.LISTEN_SAMPLE &&
                event.type != RecommendationEventType.REPLAY &&
                event.type != RecommendationEventType.PLAY_START
            ) return@forEach
            val age = nowMs - event.occurredAtMs
            if (age !in 0..ONE_DAY_MS) return@forEach
            val key = event.trackId
            if (key.isBlank()) return@forEach
            // PLAY_START 只是「起播」，同一 session 的 LISTEN_SAMPLE 才是证据；两者取其一即可。
            val weight = if (event.type == RecommendationEventType.LISTEN_SAMPLE) repeatsOf(event) else 1
            perTrack.getOrPut(key) { mutableListOf() }.addFirst(age to weight)
        }
        if (perTrack.isEmpty()) return emptyMap()
        return perTrack.mapValues { (_, plays) ->
            RepeatProfile(
                inTenMinutes = plays.filter { it.first <= TEN_MINUTES_MS }.sumOf { it.second },
                inHour = plays.filter { it.first <= ONE_HOUR_MS }.sumOf { it.second },
                inDay = plays.sumOf { it.second },
            )
        }
    }

    /** 十分钟内反复播放 = 单曲循环式喜欢，值得在下一轮继续给。 */
    fun loveBoost(profile: RepeatProfile?): Double {
        if (profile == null) return 0.0
        return when {
            profile.inTenMinutes >= 2 -> 0.55
            profile.inHour >= 3 -> 0.30
            profile.inTenMinutes >= 1 && profile.inDay >= 2 -> 0.20
            else -> 0.0
        }
    }

    /**
     * 当天第 4 次之后再出现就打疲劳分。它和 [loveBoost] 会互相抵消：
     * 短时间内循环听的仍然是喜欢，隔一阵子反复刷的才会被压。
     */
    fun fatiguePenalty(profile: RepeatProfile?): Double {
        if (profile == null) return 0.0
        val excess = (profile.inDay - 3).coerceAtLeast(0)
        return -(excess * 0.25).coerceAtMost(1.0)
    }

    /**
     * 歌手级负反馈：作用域是「作者」而不是曲风。
     * 显式「不喜欢」按 30 天半衰期保留，快速跳过按 7 天，全部累加后封顶 0.5。
     */
    fun artistNegatives(events: List<RecommendationEvent>, nowMs: Long): Map<String, Double> {
        if (events.isEmpty()) return emptyMap()
        val result = linkedMapOf<String, Double>()
        events.forEach { event ->
            if (event.type.weight >= 0.0) return@forEach
            val author = event.authorKey?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return@forEach
            val explicit = event.type == RecommendationEventType.NOT_INTERESTED
            val ttl = if (explicit) NOT_INTERESTED_ARTIST_TTL_MS else ARTIST_TTL_MS
            val age = nowMs - event.occurredAtMs
            if (age !in 0..ttl) return@forEach
            val halfLife = if (explicit) 30 * ONE_DAY_MS else 7 * ONE_DAY_MS
            val strength = decayed(-event.type.weight * ARTIST_NEGATIVE_UNIT, age, halfLife)
            if (strength <= 0.0) return@forEach
            result[author] = ((result[author] ?: 0.0) + strength).coerceAtMost(MAX_ARTIST_NEGATIVE)
        }
        return result.mapValues { it.value.coerceIn(0.0, MAX_ARTIST_NEGATIVE) }
    }

    private fun decayed(base: Double, ageMs: Long, halfLifeMs: Long): Double =
        if (halfLifeMs <= 0L) 0.0 else base * exp(-ln(2.0) * ageMs.toDouble() / halfLifeMs)

    private const val ARTIST_NEGATIVE_UNIT = 0.12
    private const val MAX_ARTIST_NEGATIVE = 0.5
    private const val ARTIST_TTL_MS = 7 * ONE_DAY_MS
    private const val NOT_INTERESTED_ARTIST_TTL_MS = 180 * ONE_DAY_MS
}

/**
 * 每日刷新锚点：5:00 切换 + 0~1 小时随机抖动。
 * 原版把「跨夜听歌算前一天」做成行业惯例，同时用抖动避免整批用户在同一秒打服务端。
 */
object DailyRefresh {
    const val RESET_HOUR = 5
    const val MAX_JITTER_MS = 60 * 60 * 1000L

    fun windowStartMs(nowMs: Long, zone: TimeZone = TimeZone.getDefault()): Long {
        val cal = Calendar.getInstance(zone)
        cal.timeInMillis = nowMs
        cal.set(Calendar.HOUR_OF_DAY, RESET_HOUR)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        return if (start > nowMs) start - RecommendationFusion.ONE_DAY_MS else start
    }

    /** 0 ~ 59 分钟的确定性抖动，同一 seed 结果稳定，便于测试与复现。 */
    fun jitterMs(seed: Long): Long = Math.floorMod(seed, MAX_JITTER_MS)

    fun refreshAtMs(nowMs: Long, seed: Long): Long =
        windowStartMs(nowMs) + jitterMs(seed)

    /** 距上次刷新已跨过 5:00 窗口起点就刷新一次。 */
    fun shouldRefresh(lastRefreshMs: Long, nowMs: Long): Boolean =
        lastRefreshMs <= 0L || lastRefreshMs < windowStartMs(nowMs)
}
