package com.madus.mobile.together

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.PeerConnection
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class TogetherWsClient(
    private val scope: CoroutineScope,
    private val listener: Listener,
) {
    interface Listener {
        fun onConnecting(reconnecting: Boolean)
        fun onRoomState(state: RoomStateMessage)
        fun onMemberJoined(member: TogetherMember)
        fun onMemberLeft(userId: String, nickname: String, newHostId: String?)
        fun onHostChanged(hostId: String, nickname: String)
        fun onSyncAction(action: SyncActionMessage)
        fun onHeartbeatSync(playback: TogetherPlaybackInfo, members: List<TogetherMember>, serverTimeMs: Long)
        fun onBufferWait(bufferingMembers: List<String>, playback: TogetherPlaybackInfo?)
        fun onBufferResumed(playback: TogetherPlaybackInfo?)
        fun onChatMessage(msg: TogetherChatMessage)
        fun onVoiceState(userId: String, isMuted: Boolean, isSpeaking: Boolean, isAway: Boolean)
        fun onControlModeChanged(mode: TogetherControlMode, byNickname: String)
        fun onRtcOffer(fromUserId: String, sdpType: String, sdp: String)
        fun onRtcAnswer(fromUserId: String, sdpType: String, sdp: String)
        fun onRtcIce(fromUserId: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int)
        fun onPong(rttMs: Int, clockOffsetMs: Long)
        fun onRoomClosed(reason: String)
        fun onServerError(code: String, message: String)
        fun onDisconnected(willReconnect: Boolean, reason: String)
    }

    data class RoomStateMessage(
        val roomId: String,
        val hostId: String,
        val controlMode: TogetherControlMode,
        val maxMembers: Int,
        val selfUserId: String,
        val serverTimeMs: Long,
        val playback: TogetherPlaybackInfo,
        val members: List<TogetherMember>,
        val chatHistory: List<TogetherChatMessage>,
    )

    data class SyncActionMessage(
        val action: String,
        val currentTimeSec: Double,
        val playbackRate: Float,
        val isPlaying: Boolean,
        val serverTimeMs: Long,
        val byUserId: String,
        val byNickname: String,
        val mediaInfo: TogetherPlaybackInfo?,
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var ws: WebSocket? = null

    @Volatile
    private var serverHttpUrl: String = DEFAULT_TOGETHER_SERVER_URL

    @Volatile
    private var roomId: String = ""

    @Volatile
    private var nickname: String = ""

    @Volatile
    private var password: String? = null

    @Volatile
    private var manualLeave: Boolean = true

    @Volatile
    private var joined: Boolean = false

    @Volatile
    private var connecting: Boolean = false

    private var reconnectAttempt: Int = 0
    private var reconnectJob: Job? = null
    private var pingJob: Job? = null
    private var connectionEpoch: Long = 0L

    val isConnected: Boolean get() = ws != null && joined

    fun connect(
        serverHttpUrl: String,
        roomId: String,
        nickname: String,
        password: String?,
        isReconnect: Boolean = false,
    ) {
        this.serverHttpUrl = TogetherPrefs.normalizeServerUrl(serverHttpUrl)
        this.roomId = roomId.trim()
        this.nickname = nickname.trim()
        this.password = password?.takeIf { it.isNotBlank() }
        this.manualLeave = false
        if (!isReconnect) {
            this.reconnectAttempt = 0
        }
        openSocket(isReconnect = isReconnect)
    }

    fun reconnectNow() {
        if (manualLeave || roomId.isBlank() || nickname.isBlank()) return
        if (ws != null || connecting) return
        reconnectJob?.cancel()
        reconnectAttempt = 0
        openSocket(isReconnect = true)
    }

    fun leaveAndClose() {
        manualLeave = true
        joined = false
        connecting = false
        connectionEpoch += 1L
        reconnectJob?.cancel()
        reconnectJob = null
        pingJob?.cancel()
        pingJob = null
        val cur = ws
        ws = null
        if (cur != null) {
            runCatching { sendRaw(cur, "LEAVE_ROOM", JSONObject()) }
            runCatching { cur.close(1000, "leave") }
        }
    }

    suspend fun fetchIceServers(httpUrl: String): List<PeerConnection.IceServer> = withContext(Dispatchers.IO) {
        val base = TogetherPrefs.normalizeServerUrl(httpUrl)
        val req = Request.Builder().url("$base/api/turn").get().build()
        val fetched = runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val raw = resp.body?.string().orEmpty()
                val json = JSONObject(raw)
                val arr = json.optJSONArray("iceServers") ?: JSONArray()
                buildList {
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val urls = mutableListOf<String>()
                        val urlsAny = item.opt("urls")
                        when (urlsAny) {
                            is JSONArray -> for (j in 0 until urlsAny.length()) {
                                urlsAny.optString(j).takeIf { it.isNotBlank() }?.let(urls::add)
                            }
                            is String -> if (urlsAny.isNotBlank()) urls.add(urlsAny)
                        }
                        if (urls.isEmpty()) continue
                        val builder = PeerConnection.IceServer.builder(urls)
                        val user = item.optString("username", "")
                        val cred = item.optString("credential", "")
                        if (user.isNotBlank()) builder.setUsername(user)
                        if (cred.isNotBlank()) builder.setPassword(cred)
                        add(builder.createIceServer())
                    }
                }
            }
        }.getOrDefault(emptyList())

        if (fetched.isNotEmpty()) {
            fetched
        } else {
            val host = runCatching { android.net.Uri.parse(base).host.orEmpty() }.getOrDefault("")
            buildList {
                if (host.isNotBlank()) {
                    add(PeerConnection.IceServer.builder("stun:$host:3478").createIceServer())
                    add(
                        PeerConnection.IceServer.builder("turn:$host:3478")
                            .setUsername("syncuser")
                            .setPassword("syncpass123")
                            .createIceServer(),
                    )
                }
                add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
            }
        }
    }

    private fun openSocket(isReconnect: Boolean) {
        reconnectJob?.cancel()
        pingJob?.cancel()
        ws?.let { old -> runCatching { old.cancel() } }
        ws = null
        joined = false
        connecting = true
        val epoch = ++connectionEpoch
        listener.onConnecting(isReconnect)

        val wsUrl = TogetherPrefs.toWsUrl(serverHttpUrl)
        val req = Request.Builder().url(wsUrl).build()
        val socket = client.newWebSocket(
            req,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (epoch != connectionEpoch) {
                        runCatching { webSocket.close(1000, "stale") }
                        return
                    }
                    connecting = false
                    ws = webSocket
                    val payload = JSONObject().apply {
                        put("roomId", roomId)
                        put("nickname", nickname)
                        password?.let { put("password", it) }
                    }
                    sendRaw(webSocket, "JOIN_ROOM", payload)
                    startPingLoop(epoch)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (epoch != connectionEpoch) return
                    handleIncoming(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    runCatching { webSocket.close(1000, null) }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (epoch != connectionEpoch) return
                    onSocketLost("连接已关闭 ($code)")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (epoch != connectionEpoch) return
                    Log.w(TAG, "WS failure: ${t.message}")
                    onSocketLost(t.message ?: "网络连接失败")
                }
            },
        )
        ws = socket
    }

    private fun onSocketLost(reason: String) {
        connecting = false
        joined = false
        ws = null
        pingJob?.cancel()
        pingJob = null
        val willRetry = !manualLeave && reconnectAttempt < MAX_RECONNECT_ATTEMPTS
        listener.onDisconnected(willReconnect = willRetry, reason = reason)
        if (willRetry) {
            val delayMs = when (reconnectAttempt) {
                0 -> 1_200L
                1 -> 2_500L
                2 -> 4_500L
                else -> 8_000L
            }
            reconnectAttempt += 1
            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay(delayMs)
                if (!manualLeave) {
                    openSocket(isReconnect = true)
                }
            }
        }
    }

    private fun startPingLoop(epoch: Long) {
        pingJob?.cancel()
        pingJob = scope.launch(Dispatchers.IO) {
            while (epoch == connectionEpoch && ws != null) {
                send(
                    "PING",
                    JSONObject().apply { put("clientTime", System.currentTimeMillis()) },
                )
                delay(8_000L)
            }
        }
    }

    fun sendSyncAction(
        action: String,
        currentTimeSec: Double? = null,
        playbackRate: Float? = null,
        mediaPayload: TogetherMediaPayload? = null,
    ) {
        val payload = JSONObject().apply {
            put("action", action)
            if (currentTimeSec != null) put("currentTime", currentTimeSec.coerceAtLeast(0.0))
            if (playbackRate != null) put("playbackRate", playbackRate.toDouble())
            if (mediaPayload != null) {
                put(
                    "mediaInfo",
                    JSONObject().apply {
                        put("videoUrl", mediaPayload.videoUrl)
                        put("headers", JSONObject(mediaPayload.headers))
                        mediaPayload.title?.let { put("title", it) }
                        mediaPayload.durationSec?.let { put("duration", it) }
                        mediaPayload.source?.let { put("source", it) }
                    },
                )
            }
        }
        send("SYNC_ACTION", payload)
    }

    fun sendBufferState(isBuffering: Boolean) {
        send("BUFFER_STATE", JSONObject().apply { put("isBuffering", isBuffering) })
    }

    fun sendChatMessage(
        content: String,
        sendAsDanmaku: Boolean,
        videoTimestampSec: Double,
        color: String = "#FFFFFF",
    ) {
        send(
            "CHAT_MESSAGE",
            JSONObject().apply {
                put("content", content)
                put("sendAsDanmaku", sendAsDanmaku)
                put("color", color)
                put("videoTimestamp", videoTimestampSec.coerceAtLeast(0.0))
            },
        )
    }

    fun sendVoiceState(isMuted: Boolean, isSpeaking: Boolean, isAway: Boolean = false) {
        send(
            "VOICE_STATE",
            JSONObject().apply {
                put("isMuted", isMuted)
                put("isSpeaking", isSpeaking)
                put("isAway", isAway)
            },
        )
    }

    fun sendControlMode(mode: TogetherControlMode) {
        send("SET_CONTROL_MODE", JSONObject().apply { put("controlMode", mode.wireValue) })
    }

    fun sendRtcOffer(targetUserId: String, type: String, sdp: String) {
        send(
            "RTC_OFFER",
            JSONObject().apply {
                put("targetUserId", targetUserId)
                put(
                    "sdp",
                    JSONObject().apply {
                        put("type", type)
                        put("sdp", sdp)
                    },
                )
            },
        )
    }

    fun sendRtcAnswer(targetUserId: String, type: String, sdp: String) {
        send(
            "RTC_ANSWER",
            JSONObject().apply {
                put("targetUserId", targetUserId)
                put(
                    "sdp",
                    JSONObject().apply {
                        put("type", type)
                        put("sdp", sdp)
                    },
                )
            },
        )
    }

    fun sendRtcIce(targetUserId: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        send(
            "RTC_ICE",
            JSONObject().apply {
                put("targetUserId", targetUserId)
                put(
                    "candidate",
                    JSONObject().apply {
                        put("candidate", candidate)
                        if (sdpMid != null) put("sdpMid", sdpMid)
                        put("sdpMLineIndex", sdpMLineIndex)
                    },
                )
            },
        )
    }

    private fun send(type: String, payload: JSONObject) {
        val socket = ws ?: return
        sendRaw(socket, type, payload)
    }

    private fun sendRaw(socket: WebSocket, type: String, payload: JSONObject) {
        val envelope = JSONObject().apply {
            put("type", type)
            put("payload", payload)
            put("timestamp", System.currentTimeMillis())
        }
        socket.send(envelope.toString())
    }

    private fun handleIncoming(raw: String) {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val type = root.optString("type", "")
        val payload = root.optJSONObject("payload") ?: JSONObject()
        val senderId = root.optString("senderId", "")

        when (type) {
            "ROOM_STATE" -> {
                joined = true
                reconnectAttempt = 0
                val serverTimeMs = payload.optLong("serverTime", System.currentTimeMillis())
                val playback = parsePlayback(payload.optJSONObject("playback"), serverTimeMs)
                val members = parseMembers(payload.optJSONArray("members"))
                val chat = parseChatHistory(payload.optJSONArray("chatHistory"))
                listener.onRoomState(
                    RoomStateMessage(
                        roomId = payload.optString("roomId", roomId),
                        hostId = payload.optString("hostId", ""),
                        controlMode = TogetherControlMode.fromWire(payload.optString("controlMode")),
                        maxMembers = payload.optInt("maxMembers", 8),
                        selfUserId = payload.optString("selfUserId", ""),
                        serverTimeMs = serverTimeMs,
                        playback = playback,
                        members = members,
                        chatHistory = chat,
                    ),
                )
            }
            "ROOM_MEMBER_JOINED" -> {
                val m = payload.optJSONObject("member")?.let(::parseMember) ?: return
                listener.onMemberJoined(m)
            }
            "ROOM_MEMBER_LEFT" -> {
                val uid = payload.optString("userId", "")
                if (uid.isBlank()) return
                listener.onMemberLeft(
                    userId = uid,
                    nickname = payload.optString("nickname", ""),
                    newHostId = payload.optString("newHostId", "").takeIf { it.isNotBlank() },
                )
            }
            "ROOM_HOST_CHANGED" -> {
                val hostId = payload.optString("hostId", "")
                if (hostId.isNotBlank()) {
                    listener.onHostChanged(hostId, payload.optString("nickname", ""))
                }
            }
            "SYNC_ACTION" -> {
                val serverTimeMs = payload.optLong("serverTime", System.currentTimeMillis())
                val mediaObj = payload.optJSONObject("mediaInfo")
                val mediaInfo = mediaObj?.let { parsePlayback(it, serverTimeMs) }
                listener.onSyncAction(
                    SyncActionMessage(
                        action = payload.optString("action", ""),
                        currentTimeSec = payload.optDouble("currentTime", 0.0),
                        playbackRate = payload.optDouble("playbackRate", 1.0).toFloat(),
                        isPlaying = payload.optBoolean("isPlaying", false),
                        serverTimeMs = serverTimeMs,
                        byUserId = payload.optString("byUserId", ""),
                        byNickname = payload.optString("byNickname", ""),
                        mediaInfo = mediaInfo,
                    ),
                )
            }
            "HEARTBEAT_SYNC" -> {
                val serverTimeMs = payload.optLong("serverTime", System.currentTimeMillis())
                val playback = parsePlayback(payload.optJSONObject("playback"), serverTimeMs)
                val members = parseMembers(payload.optJSONArray("members"))
                listener.onHeartbeatSync(playback, members, serverTimeMs)
            }
            "BUFFER_WAIT" -> {
                val arr = payload.optJSONArray("bufferingMembers") ?: JSONArray()
                val names = buildList {
                    for (i in 0 until arr.length()) {
                        arr.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
                val pb = payload.optJSONObject("playback")?.let {
                    parsePlayback(it, payload.optLong("serverTime", System.currentTimeMillis()))
                }
                listener.onBufferWait(names, pb)
            }
            "BUFFER_RESUMED" -> {
                val pb = payload.optJSONObject("playback")?.let {
                    parsePlayback(it, payload.optLong("serverTime", System.currentTimeMillis()))
                }
                listener.onBufferResumed(pb)
            }
            "CHAT_MESSAGE" -> {
                val msg = parseChatMessage(payload) ?: return
                listener.onChatMessage(msg)
            }
            "VOICE_STATE" -> {
                val uid = payload.optString("userId", senderId)
                if (uid.isBlank()) return
                listener.onVoiceState(
                    userId = uid,
                    isMuted = payload.optBoolean("isMuted", false),
                    isSpeaking = payload.optBoolean("isSpeaking", false),
                    isAway = payload.optBoolean("isAway", false),
                )
            }
            "CONTROL_MODE_CHANGED" -> {
                listener.onControlModeChanged(
                    mode = TogetherControlMode.fromWire(payload.optString("controlMode")),
                    byNickname = payload.optString("byNickname", ""),
                )
            }
            "RTC_OFFER" -> {
                val from = payload.optString("fromUserId", senderId)
                val sdpObj = payload.optJSONObject("sdp") ?: return
                val sdp = sdpObj.optString("sdp", "")
                if (from.isNotBlank() && sdp.isNotBlank()) {
                    listener.onRtcOffer(from, sdpObj.optString("type", "offer"), sdp)
                }
            }
            "RTC_ANSWER" -> {
                val from = payload.optString("fromUserId", senderId)
                val sdpObj = payload.optJSONObject("sdp") ?: return
                val sdp = sdpObj.optString("sdp", "")
                if (from.isNotBlank() && sdp.isNotBlank()) {
                    listener.onRtcAnswer(from, sdpObj.optString("type", "answer"), sdp)
                }
            }
            "RTC_ICE" -> {
                val from = payload.optString("fromUserId", senderId)
                val candObj = payload.optJSONObject("candidate") ?: return
                val cand = candObj.optString("candidate", "")
                if (from.isNotBlank() && cand.isNotBlank()) {
                    listener.onRtcIce(
                        fromUserId = from,
                        candidate = cand,
                        sdpMid = candObj.optString("sdpMid", "").takeIf { it.isNotBlank() },
                        sdpMLineIndex = candObj.optInt("sdpMLineIndex", 0),
                    )
                }
            }
            "PONG" -> {
                val clientTime = payload.optLong("clientTime", 0L)
                val serverTime = payload.optLong("serverTime", System.currentTimeMillis())
                val now = System.currentTimeMillis()
                if (clientTime in 1L..now) {
                    val rtt = (now - clientTime).toInt().coerceAtLeast(1)
                    val offset = serverTime - (clientTime + rtt / 2L)
                    listener.onPong(rtt, offset)
                }
            }
            "ROOM_CLOSED" -> {
                manualLeave = true
                joined = false
                listener.onRoomClosed(payload.optString("reason", "房间已关闭"))
            }
            "ERROR" -> {
                val code = payload.optString("code", "INTERNAL")
                val msg = payload.optString("message", "请求失败")
                if (code == "WRONG_PASSWORD" || code == "ROOM_FULL") {
                    manualLeave = true
                }
                listener.onServerError(code, msg)
            }
        }
    }

    private fun parseMembers(arr: JSONArray?): List<TogetherMember> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let(::parseMember)?.let(::add)
            }
        }
    }

    private fun parseMember(o: JSONObject): TogetherMember = TogetherMember(
        userId = o.optString("userId", ""),
        nickname = o.optString("nickname", "朋友"),
        isHost = o.optBoolean("isHost", false),
        isMuted = o.optBoolean("isMuted", false),
        isSpeaking = o.optBoolean("isSpeaking", false),
        isBuffering = o.optBoolean("isBuffering", false),
        isAway = o.optBoolean("isAway", false),
        joinedAt = o.optLong("joinedAt", 0L),
    )

    private fun parsePlayback(o: JSONObject?, serverTimeMs: Long): TogetherPlaybackInfo {
        if (o == null) return TogetherPlaybackInfo()
        val headersObj = o.optJSONObject("headers")
        val headers = buildMap {
            if (headersObj != null) {
                val keys = headersObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    put(k, headersObj.optString(k, ""))
                }
            }
        }
        val info = TogetherPlaybackInfo(
            videoUrl = o.optString("videoUrl", "").takeIf { it.isNotBlank() && it != "null" },
            headers = headers,
            title = o.optString("title", "").takeIf { it.isNotBlank() && it != "null" },
            isPlaying = o.optBoolean("isPlaying", false),
            anchorTimeSec = o.optDouble("currentTime", 0.0).coerceAtLeast(0.0),
            anchorServerTimeMs = serverTimeMs,
            playbackRate = o.optDouble("playbackRate", 1.0).toFloat().coerceIn(0.25f, 4.0f),
            durationSec = if (o.has("duration") && !o.isNull("duration")) o.optDouble("duration") else null,
            source = o.optString("source", "").takeIf { it.isNotBlank() && it != "null" },
        )
        val decodedTrack = TogetherMediaCodec.decodeTrack(info)
        return info.copy(
            bvid = decodedTrack?.bvid.orEmpty(),
            cid = decodedTrack?.cid.orEmpty(),
            track = decodedTrack,
        )
    }

    private fun parseChatHistory(arr: JSONArray?): List<TogetherChatMessage> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let(::parseChatMessage)?.let(::add)
            }
        }
    }

    private fun parseChatMessage(o: JSONObject): TogetherChatMessage? {
        val content = o.optString("content", "").trim()
        if (content.isBlank()) return null
        // 跳过旧版 SYNC_AUDIO 二进制语音编码，专注实时麦位对话 + 文本聊天
        if (content.startsWith("SYNCAUDIO:")) return null
        return TogetherChatMessage(
            id = o.optString("id", "${System.currentTimeMillis()}_${content.hashCode()}"),
            userId = o.optString("userId", ""),
            nickname = o.optString("nickname", "成员"),
            content = content,
            sendAsDanmaku = o.optBoolean("sendAsDanmaku", false),
            color = o.optString("color", "#FFFFFF"),
            videoTimestamp = (o.optDouble("videoTimestamp", 0.0) * 100.0).roundToInt() / 100.0,
            timestamp = o.optLong("timestamp", System.currentTimeMillis()),
        )
    }

    companion object {
        private const val TAG = "TogetherWsClient"
        private const val MAX_RECONNECT_ATTEMPTS = 6
    }
}
