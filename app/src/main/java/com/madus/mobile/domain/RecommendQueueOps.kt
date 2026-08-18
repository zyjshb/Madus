package com.madus.mobile.domain

/**
 * 点「不喜欢」后怎么裁队列。抖音式：只拿掉这首，再剥掉紧挨着的同 UP，
 * 后面 feed 还在，绝不把整池掏空去播点赞。
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
        if (isForYou) {
            val cur = queue.getOrNull(currentIndex)
            val mid = cur?.ownerMid.orEmpty()
            val author = cur?.artist?.trim()?.lowercase().orEmpty()
            val history = queue.take(currentIndex.coerceIn(0, queue.size))
                .filter { it.id != dislikedId && !isBlocked(it) }
            var peeled = 0
            val upcoming = queue.drop((currentIndex + 1).coerceAtMost(queue.size)).filter { t ->
                if (t.id == dislikedId || isBlocked(t)) return@filter false
                val sameUp = (mid.isNotBlank() && t.ownerMid == mid) ||
                    (author.isNotBlank() && !author.equals("bilibili") &&
                        t.artist.trim().lowercase() == author)
                if (sameUp && peeled < 2) {
                    peeled++
                    return@filter false
                }
                true
            }
            val seedKinds = cur?.let { ContentProfileParser.kindKeys(it) }.orEmpty()
            val scattered = scatterDifferentKind(upcoming, seedKinds)
            val next = history + scattered
            val playAt = if (upcoming.isNotEmpty()) history.size else -1
            return AfterNotInterested(next, playAt)
        }
        val keep = queue.mapIndexedNotNull { i, t ->
            if (t.id == dislikedId || isBlocked(t)) null else i to t
        }
        val kept = keep.map { it.second }
        if (kept.isEmpty()) return AfterNotInterested(emptyList(), -1)
        val playAt = keep.indexOfFirst { it.first > currentIndex }
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

    /** 下一首还按队列走；再后面插一条不同细类，避免连刷同一类。 */
    fun scatterDifferentKind(upcoming: List<Track>, seedKinds: Set<String>): List<Track> {
        if (upcoming.size < 3 || seedKinds.isEmpty()) return upcoming
        val idx = upcoming.indexOfFirst { t ->
            ContentProfileParser.kindKeys(t).none { it in seedKinds }
        }
        if (idx <= 1) return upcoming
        val copy = upcoming.toMutableList()
        val item = copy.removeAt(idx)
        copy.add(1, item)
        return copy
    }

    fun hasPlayableNext(queueSize: Int, currentIndex: Int): Boolean =
        queueSize > 0 && currentIndex + 1 < queueSize

    /** 电台见底或队列被掏空时，必须换源续刷，不能只暂停。 */
    fun needsForYouRefill(isForYou: Boolean, queueSize: Int, currentIndex: Int): Boolean =
        isForYou && !hasPlayableNext(queueSize, currentIndex)
}
