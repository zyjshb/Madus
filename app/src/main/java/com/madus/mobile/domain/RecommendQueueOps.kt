package com.madus.mobile.domain

/**
 * 点「不喜欢」后怎么裁队列。
 * 电台：丢掉当前 + 被挡的后续（同类 / 同 UP），**留下还能播的下一首**，才能马上划走。
 * 普通歌单：整表去掉被挡的。
 */
object RecommendQueueOps {
    data class AfterNotInterested(
        val queue: List<Track>,
        /** 立刻该播的下标；-1 = 后面没了，需要换源 */
        val playIndex: Int,
    )

    fun afterNotInterested(
        queue: List<Track>,
        currentIndex: Int,
        dislikedId: String,
        isForYou: Boolean,
        isBlocked: (Track) -> Boolean,
    ): AfterNotInterested {
        if (queue.isEmpty()) return AfterNotInterested(emptyList(), -1)
        val keep = queue.mapIndexedNotNull { i, t ->
            if (t.id == dislikedId || isBlocked(t)) null else i to t
        }
        val kept = keep.map { it.second }
        if (kept.isEmpty()) return AfterNotInterested(emptyList(), -1)
        val playAt = keep.indexOfFirst { it.first > currentIndex }
        if (!isForYou && playAt < 0 && kept.isNotEmpty()) {
            // 歌单：后面没了就停在留下的最后一首之前，交给循环逻辑
            return AfterNotInterested(kept, -1)
        }
        return AfterNotInterested(kept, playAt)
    }

    /** @deprecated 兼容旧测试名，语义改为 afterNotInterested.queue */
    fun historyAfterNotInterested(
        queue: List<Track>,
        currentIndex: Int,
        dislikedId: String,
        isForYou: Boolean,
        isBlocked: (Track) -> Boolean,
    ): List<Track> = afterNotInterested(queue, currentIndex, dislikedId, isForYou, isBlocked).queue

    fun hasPlayableNext(queueSize: Int, currentIndex: Int): Boolean =
        queueSize > 0 && currentIndex + 1 < queueSize

    /** 电台见底或队列被掏空时，必须换源续刷，不能只暂停。 */
    fun needsForYouRefill(isForYou: Boolean, queueSize: Int, currentIndex: Int): Boolean =
        isForYou && !hasPlayableNext(queueSize, currentIndex)
}
