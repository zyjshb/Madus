package com.madus.mobile.together

import android.net.Uri
import com.madus.mobile.domain.MusicSourceType
import com.madus.mobile.domain.Track
import org.json.JSONArray
import org.json.JSONObject

enum class TogetherControlMode(val wireValue: String, val label: String) {
    HOST_ONLY("host_only", "仅房主控制"),
    EVERYONE("everyone", "全员可控制"),
    ;

    companion object {
        fun fromWire(value: String?): TogetherControlMode =
            if (value == EVERYONE.wireValue) EVERYONE else HOST_ONLY
    }
}

enum class TogetherConnectionState(val label: String) {
    DISCONNECTED("未连接"),
    CONNECTING("连接中"),
    CONNECTED("已同步"),
    RECONNECTING("重连中"),
}

data class TogetherMember(
    val userId: String,
    val nickname: String,
    val isHost: Boolean = false,
    val isMuted: Boolean = false,
    val isSpeaking: Boolean = false,
    val isBuffering: Boolean = false,
    val isAway: Boolean = false,
    val joinedAt: Long = 0L,
)

data class TogetherChatMessage(
    val id: String,
    val userId: String,
    val nickname: String,
    val content: String,
    val sendAsDanmaku: Boolean = false,
    val color: String = "#FFFFFF",
    val videoTimestamp: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val isSystem: Boolean = false,
)

data class TogetherPlaybackInfo(
    val videoUrl: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val title: String? = null,
    val isPlaying: Boolean = false,
    val anchorTimeSec: Double = 0.0,
    val anchorServerTimeMs: Long = 0L,
    val playbackRate: Float = 1.0f,
    val durationSec: Double? = null,
    val source: String? = null,
    val bvid: String = "",
    val cid: String = "",
    val track: Track? = null,
) {
    fun expectedPositionMs(serverNowMs: Long): Long {
        val elapsedSec = if (isPlaying && anchorServerTimeMs > 0L) {
            ((serverNowMs - anchorServerTimeMs).coerceAtLeast(0L) / 1000.0) * playbackRate
        } else {
            0.0
        }
        return ((anchorTimeSec + elapsedSec).coerceAtLeast(0.0) * 1000.0).toLong()
    }
}

data class RecentRoomEntry(
    val roomId: String,
    val nickname: String,
    val joinedAtMs: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("roomId", roomId)
        put("nickname", nickname)
        put("joinedAtMs", joinedAtMs)
    }

    companion object {
        fun fromJsonList(raw: String?): List<RecentRoomEntry> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val id = o.optString("roomId", "").trim()
                        if (id.isNotBlank()) {
                            add(
                                RecentRoomEntry(
                                    roomId = id,
                                    nickname = o.optString("nickname", ""),
                                    joinedAtMs = o.optLong("joinedAtMs", 0L),
                                ),
                            )
                        }
                    }
                }
            }.getOrDefault(emptyList())
        }

        fun toJsonList(list: List<RecentRoomEntry>): String =
            JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
    }
}

data class TogetherUiState(
    val connectionState: TogetherConnectionState = TogetherConnectionState.DISCONNECTED,
    val isJoining: Boolean = false,
    val isJoined: Boolean = false,
    val serverUrl: String = DEFAULT_TOGETHER_SERVER_URL,
    val roomId: String = "",
    val selfUserId: String = "",
    val selfNickname: String = "",
    val hostId: String = "",
    val controlMode: TogetherControlMode = TogetherControlMode.HOST_ONLY,
    val maxMembers: Int = 8,
    val members: List<TogetherMember> = emptyList(),
    val chatMessages: List<TogetherChatMessage> = emptyList(),
    val playback: TogetherPlaybackInfo = TogetherPlaybackInfo(),
    val localPositionMs: Long = 0L,
    val localDurationMs: Long = 0L,
    val localIsPlaying: Boolean = false,
    val localIsLoading: Boolean = false,
    val bufferingMembers: List<String> = emptyList(),
    val rttMs: Int? = null,
    val serverClockOffsetMs: Long = 0L,
    val videoExpanded: Boolean = false,
    val micEnabled: Boolean = false,
    val micPermissionGranted: Boolean = false,
    val voiceReady: Boolean = false,
    val speakerOn: Boolean = true,
    val mediaVolume: Float = 1.0f,
    val voiceGain: Float = 1.0f,
    val showVolumeDrawer: Boolean = false,
    val danmakuEnabled: Boolean = true,
    val recentRooms: List<RecentRoomEntry> = emptyList(),
    val errorBanner: String? = null,
    val statusNotice: String? = null,
    val roomClosedReason: String? = null,
) {
    val isHost: Boolean get() = selfUserId.isNotBlank() && selfUserId == hostId
    val canControl: Boolean get() = isHost || controlMode == TogetherControlMode.EVERYONE
}

const val DEFAULT_TOGETHER_SERVER_URL = "http://1.14.70.149:8080"

/**
 * 仅传递 B 站逻辑标识 `bili://<bvid>?cid=<cid>&aid=<aid>&artist=...`（不转发带签名 CDN 直链），
 * 各端依据自己的登录 Cookie、画质/音视频切换状态本地调用 [com.madus.mobile.data.BilibiliApi.resolvePlayUrl] 取流。
 */
object TogetherMediaCodec {
    fun encodeTrack(track: Track): TogetherMediaPayload {
        val bv = track.bvid.trim()
        val cid = track.cid.trim()
        val aid = track.aid.trim()
        val videoUrl = if (bv.isNotBlank()) {
            Uri.Builder()
                .scheme("bili")
                .authority(bv)
                .apply {
                    if (cid.isNotBlank()) appendQueryParameter("cid", cid)
                    if (aid.isNotBlank()) appendQueryParameter("aid", aid)
                    if (track.title.isNotBlank()) appendQueryParameter("title", track.title.take(120))
                    if (track.artist.isNotBlank()) appendQueryParameter("artist", track.artist.take(60))
                    if (!track.coverUrl.isNullOrBlank()) appendQueryParameter("cover", track.coverUrl)
                }
                .build()
                .toString()
        } else {
            track.streamUrl.orEmpty()
        }
        val durationSec = (track.durationMs / 1000.0).takeIf { it > 0.0 }
        val sourceTag = if (bv.isNotBlank()) {
            listOf("bili", bv, cid).filter { it.isNotBlank() }.joinToString(":").take(64)
        } else {
            track.source.id
        }
        return TogetherMediaPayload(
            videoUrl = videoUrl,
            title = track.title.ifBlank { bv },
            durationSec = durationSec,
            source = sourceTag,
        )
    }

    fun decodeTrack(info: TogetherPlaybackInfo): Track? {
        val raw = info.videoUrl?.trim().orEmpty()
        if (raw.isBlank()) return null

        if (raw.startsWith("bili://", ignoreCase = true)) {
            val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
            val bvid = uri.host.orEmpty().trim()
            if (bvid.isBlank()) return null
            val cid = uri.getQueryParameter("cid").orEmpty().trim()
            val aid = uri.getQueryParameter("aid").orEmpty().trim()
            val titleParam = uri.getQueryParameter("title")?.takeIf { it.isNotBlank() }
            val artistParam = uri.getQueryParameter("artist")?.takeIf { it.isNotBlank() }
            val coverParam = uri.getQueryParameter("cover")?.takeIf { it.isNotBlank() }
            val id = if (cid.isNotBlank()) "${bvid}_$cid" else bvid
            return Track(
                id = id,
                title = titleParam ?: info.title?.takeIf { it.isNotBlank() } ?: bvid,
                artist = artistParam ?: "Bilibili",
                album = "Bilibili",
                coverUrl = coverParam,
                durationMs = ((info.durationSec ?: 0.0) * 1000.0).toLong().coerceAtLeast(0L),
                source = MusicSourceType.BILIBILI,
                bvid = bvid,
                aid = aid,
                cid = cid,
            )
        }

        val bvRegex = Regex("BV[0-9A-Za-z]{10}")
        val matchedBv = bvRegex.find(raw)?.value ?: bvRegex.find(info.source.orEmpty())?.value.orEmpty()
        if (matchedBv.isNotBlank() && raw.contains("bilibili.com", ignoreCase = true)) {
            return Track(
                id = matchedBv,
                title = info.title?.takeIf { it.isNotBlank() } ?: matchedBv,
                artist = "Bilibili",
                album = "Bilibili",
                durationMs = ((info.durationSec ?: 0.0) * 1000.0).toLong().coerceAtLeast(0L),
                source = MusicSourceType.BILIBILI,
                bvid = matchedBv,
            )
        }

        // Fallback for direct media URL from SyncCinema interoperability
        return Track(
            id = "together_${raw.hashCode()}",
            title = info.title?.takeIf { it.isNotBlank() } ?: "一起听媒体",
            artist = info.source?.takeIf { it.isNotBlank() } ?: "在线媒体",
            album = "一起听",
            durationMs = ((info.durationSec ?: 0.0) * 1000.0).toLong().coerceAtLeast(0L),
            source = MusicSourceType.BILIBILI,
            streamUrl = raw,
        )
    }
}

data class TogetherMediaPayload(
    val videoUrl: String,
    val title: String?,
    val durationSec: Double?,
    val source: String?,
    val headers: Map<String, String> = emptyMap(),
)
