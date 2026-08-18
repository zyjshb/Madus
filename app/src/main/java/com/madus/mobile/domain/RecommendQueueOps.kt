package com.madus.mobile.domain

/**
 * 点「不喜欢」后怎么裁队列。
 * 电台：只留当前之前的历史（后面 related 整段丢掉）。
 * 普通歌单：整表去掉被挡的。
 */
object RecommendQueueOps {
    fun historyAfterNotInterested(
        queue: List<Track>,
        currentIndex: Int,
        dislikedId: String,
        isForYou: Boolean,
        isBlocked: (Track) -> Boolean,
    ): List<Track> {
        if (queue.isEmpty()) return emptyList()
        val head = if (isForYou) {
            queue.take(currentIndex.coerceIn(0, queue.size))
        } else {
            queue
        }
        return head.filter { it.id != dislikedId && !isBlocked(it) }
    }

    fun hasPlayableNext(queueSize: Int, currentIndex: Int): Boolean =
        queueSize > 0 && currentIndex + 1 < queueSize
}
