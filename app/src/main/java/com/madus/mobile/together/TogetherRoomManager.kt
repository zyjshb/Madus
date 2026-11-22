package com.madus.mobile.together

import android.content.Context
import android.util.Log
import com.madus.mobile.data.BilibiliApi
import com.madus.mobile.domain.PlayerCommand
import com.madus.mobile.domain.Track
import com.madus.mobile.player.PlayerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class TogetherRoomManager(
    context: Context,
    private val scope: CoroutineScope,
    private val playerEngine: PlayerEngine,
    private val biliApi: BilibiliApi,
    private val qualityQnProvider: () -> Int,
    private val ensurePlaybackService: () -> Unit,
) {
    private val appContext = context.applicationContext
    val prefs = TogetherPrefs(appContext)

    private val _ui = MutableStateFlow(TogetherUiState())
    val ui: StateFlow<TogetherUiState> = _ui.asStateFlow()

    private var voiceEngine: TogetherVoiceEngine? = null
    private var syncLoopJob: Job? = null
    private var resolveMediaJob: Job? = null
    private var rateRestoreJob: Job? = null
    private var pendingInitialTrack: Track? = null
    private var lastLoadedMediaKey: String = ""
    private var applyingRemoteSync: Boolean = false
    private var localBufferingReported: Boolean = false
    private var mediaLoadGeneration: Int = 0
    private val metaCache = HashMap<String, Track>()

    private val wsListener: TogetherWsClient.Listener = object : TogetherWsClient.Listener {
        override fun onConnecting(reconnecting: Boolean) {
            _ui.update {
                it.copy(
                    connectionState = if (reconnecting) {
                        TogetherConnectionState.RECONNECTING
                    } else {
                        TogetherConnectionState.CONNECTING
                    },
                    isJoining = !reconnecting,
                    errorBanner = null,
                )
            }
        }

        override fun onRoomState(state: TogetherWsClient.RoomStateMessage) {
            scope.launch {
                val turnPromise = wsClient.fetchIceServers(_ui.value.serverUrl)
                val isFirstJoin = !_ui.value.isJoined
                _ui.update { cur ->
                    cur.copy(
                        connectionState = TogetherConnectionState.CONNECTED,
                        isJoining = false,
                        isJoined = true,
                        roomId = state.roomId,
                        selfUserId = state.selfUserId,
                        hostId = state.hostId,
                        controlMode = state.controlMode,
                        maxMembers = state.maxMembers,
                        members = state.members,
                        chatMessages = state.chatHistory.ifEmpty { cur.chatMessages },
                        playback = state.playback,
                        roomClosedReason = null,
                        errorBanner = null,
                    )
                }

                // 初始化语音引擎（即使用户先闭麦，也能收听队友说话）
                ensureVoiceInitialized(state.selfUserId, turnPromise, state.members)

                val initial = pendingInitialTrack
                pendingInitialTrack = null
                if (isFirstJoin && initial != null && state.playback.videoUrl.isNullOrBlank()) {
                    pickTrack(initial)
                } else {
                    handlePlaybackChanged(state.playback, forceReload = false)
                }
                startSyncLoop()
            }
        }

        override fun onMemberJoined(member: TogetherMember) {
            _ui.update { cur ->
                val list = cur.members.filterNot { it.userId == member.userId } + member
                cur.copy(members = list)
            }
            appendSystemMessage("${member.nickname} 加入了房间")
            voiceEngine?.syncMembers(_ui.value.members, _ui.value.selfUserId)
        }

        override fun onMemberLeft(userId: String, nickname: String, newHostId: String?) {
            _ui.update { cur ->
                val updated = cur.members
                    .filterNot { it.userId == userId }
                    .map { m -> if (newHostId != null) m.copy(isHost = m.userId == newHostId) else m }
                cur.copy(
                    members = updated,
                    hostId = newHostId ?: cur.hostId,
                )
            }
            if (nickname.isNotBlank()) {
                appendSystemMessage("$nickname 离开了房间")
            }
            voiceEngine?.syncMembers(_ui.value.members, _ui.value.selfUserId)
        }

        override fun onHostChanged(hostId: String, nickname: String) {
            _ui.update { cur ->
                cur.copy(
                    hostId = hostId,
                    members = cur.members.map { it.copy(isHost = it.userId == hostId) },
                )
            }
            if (nickname.isNotBlank()) {
                appendSystemMessage("$nickname 成为新房主")
            }
        }

        override fun onSyncAction(action: TogetherWsClient.SyncActionMessage) {
            val curPb = _ui.value.playback
            val nextPb = if (action.action == "change_media" && action.mediaInfo != null) {
                action.mediaInfo
            } else {
                curPb.copy(
                    isPlaying = action.isPlaying,
                    anchorTimeSec = action.currentTimeSec,
                    anchorServerTimeMs = action.serverTimeMs,
                    playbackRate = action.playbackRate,
                )
            }
            _ui.update { it.copy(playback = nextPb) }

            if (action.action == "change_media") {
                val label = nextPb.title ?: nextPb.bvid.ifBlank { "新曲目" }
                if (action.byNickname.isNotBlank()) {
                    appendSystemMessage("${action.byNickname} 点播了《$label》")
                }
                handlePlaybackChanged(nextPb, forceReload = true)
            } else {
                applyAnchorImmediate(nextPb)
            }
        }

        override fun onHeartbeatSync(
            playback: TogetherPlaybackInfo,
            members: List<TogetherMember>,
            serverTimeMs: Long,
        ) {
            val cur = _ui.value
            // 若本地还没切过媒体或媒体变了，按房态补加载
            val urlChanged = !playback.videoUrl.isNullOrBlank() && playback.videoUrl != cur.playback.videoUrl
            val mergedPlayback = playback.copy(
                track = playback.track ?: cur.playback.track?.takeIf {
                    playback.bvid.isNotBlank() && it.bvid == playback.bvid && it.cid == playback.cid
                },
            )
            // 心跳保留已有成员的 isAway 状态（若服务端心跳未携带最新字段）
            val prevAway = cur.members.associate { it.userId to it.isAway }
            val mergedMembers = members.map { m ->
                m.copy(isAway = m.isAway || (prevAway[m.userId] == true))
            }
            _ui.update {
                it.copy(
                    playback = mergedPlayback,
                    members = mergedMembers,
                    connectionState = TogetherConnectionState.CONNECTED,
                )
            }
            if (urlChanged) {
                handlePlaybackChanged(mergedPlayback, forceReload = true)
            }
        }

        override fun onBufferWait(bufferingMembers: List<String>, playback: TogetherPlaybackInfo?) {
            _ui.update { cur ->
                cur.copy(
                    bufferingMembers = bufferingMembers,
                    playback = playback ?: cur.playback.copy(isPlaying = false),
                )
            }
            if (playerEngine.isPlayerPlaying()) {
                withRemoteSyncGuard { playerEngine.dispatch(PlayerCommand.Pause) }
            }
        }

        override fun onBufferResumed(playback: TogetherPlaybackInfo?) {
            val pb = playback ?: _ui.value.playback
            _ui.update { cur ->
                cur.copy(
                    bufferingMembers = emptyList(),
                    playback = pb,
                )
            }
            applyAnchorImmediate(pb)
        }

        override fun onChatMessage(msg: TogetherChatMessage) {
            _ui.update { cur ->
                if (cur.chatMessages.any { it.id == msg.id }) return@update cur
                val updated = (cur.chatMessages + msg).takeLast(120)
                cur.copy(chatMessages = updated)
            }
        }

        override fun onVoiceState(userId: String, isMuted: Boolean, isSpeaking: Boolean, isAway: Boolean) {
            _ui.update { cur ->
                cur.copy(
                    members = cur.members.map { m ->
                        if (m.userId == userId) {
                            m.copy(isMuted = isMuted, isSpeaking = isSpeaking, isAway = isAway)
                        } else {
                            m
                        }
                    },
                )
            }
        }

        override fun onControlModeChanged(mode: TogetherControlMode, byNickname: String) {
            _ui.update { it.copy(controlMode = mode) }
            appendSystemMessage(
                if (byNickname.isNotBlank()) "$byNickname 切换为「${mode.label}」" else "房间模式：${mode.label}",
            )
        }

        override fun onRtcOffer(fromUserId: String, sdpType: String, sdp: String) {
            voiceEngine?.handleOffer(fromUserId, sdpType, sdp)
        }

        override fun onRtcAnswer(fromUserId: String, sdpType: String, sdp: String) {
            voiceEngine?.handleAnswer(fromUserId, sdpType, sdp)
        }

        override fun onRtcIce(fromUserId: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
            voiceEngine?.handleIce(fromUserId, candidate, sdpMid, sdpMLineIndex)
        }

        override fun onPong(rttMs: Int, clockOffsetMs: Long) {
            _ui.update { it.copy(rttMs = rttMs, serverClockOffsetMs = clockOffsetMs) }
        }

        override fun onRoomClosed(reason: String) {
            cleanupLocalRoomResources()
            _ui.update { cur ->
                cur.copy(
                    isJoined = false,
                    isJoining = false,
                    connectionState = TogetherConnectionState.DISCONNECTED,
                    roomClosedReason = reason,
                    members = emptyList(),
                    bufferingMembers = emptyList(),
                )
            }
        }

        override fun onServerError(code: String, message: String) {
            _ui.update { cur ->
                cur.copy(
                    isJoining = if (code == "WRONG_PASSWORD" || code == "ROOM_FULL") false else cur.isJoining,
                    errorBanner = message,
                )
            }
        }

        override fun onDisconnected(willReconnect: Boolean, reason: String) {
            _ui.update { cur ->
                cur.copy(
                    connectionState = if (willReconnect) {
                        TogetherConnectionState.RECONNECTING
                    } else {
                        TogetherConnectionState.DISCONNECTED
                    },
                    isJoining = false,
                    errorBanner = if (!willReconnect && cur.isJoined) reason else cur.errorBanner,
                )
            }
        }
    }

    private val wsClient: TogetherWsClient = TogetherWsClient(scope, wsListener)

    init {
        scope.launch {
            prefs.flow.collect { s ->
                _ui.update { cur ->
                    cur.copy(
                        serverUrl = s.serverUrl,
                        selfNickname = cur.selfNickname.ifBlank { s.nickname },
                        mediaVolume = s.mediaVolume,
                        voiceGain = s.voiceGain,
                        recentRooms = s.recentRooms,
                    )
                }
            }
        }
    }

    fun isInRoom(): Boolean = _ui.value.isJoined

    fun updateServerUrl(url: String) {
        scope.launch { prefs.setServerUrl(url) }
    }

    fun clearError() {
        _ui.update { it.copy(errorBanner = null, roomClosedReason = null) }
    }

    fun joinRoom(
        roomId: String,
        nickname: String,
        password: String? = null,
        initialTrack: Track? = null,
    ) {
        val cleanId = roomId.trim()
        val cleanNick = nickname.trim()
        if (!ROOM_ID_REGEX.matches(cleanId)) {
            _ui.update { it.copy(errorBanner = "房间号需为 3~32 位字母、数字、下划线或短横线") }
            return
        }
        if (cleanNick.isEmpty() || cleanNick.length > 24) {
            _ui.update { it.copy(errorBanner = "昵称需为 1~24 个字") }
            return
        }

        if (_ui.value.isJoined) {
            leaveRoom()
        }

        pendingInitialTrack = initialTrack
        lastLoadedMediaKey = ""
        playerEngine.setRoomMode(true)
        playerEngine.dispatch(PlayerCommand.Stop)
        playerEngine.setVolume(_ui.value.mediaVolume)

        _ui.update {
            it.copy(
                roomId = cleanId,
                selfNickname = cleanNick,
                chatMessages = emptyList(),
                errorBanner = null,
                roomClosedReason = null,
                playback = TogetherPlaybackInfo(),
            )
        }

        scope.launch {
            prefs.setNickname(cleanNick)
            prefs.recordRecentRoom(cleanId, cleanNick)
        }
        wsClient.connect(
            serverHttpUrl = _ui.value.serverUrl,
            roomId = cleanId,
            nickname = cleanNick,
            password = password,
            isReconnect = false,
        )
    }

    fun leaveRoom() {
        wsClient.leaveAndClose()
        cleanupLocalRoomResources()
        _ui.update { cur ->
            TogetherUiState(
                serverUrl = cur.serverUrl,
                selfNickname = cur.selfNickname,
                mediaVolume = cur.mediaVolume,
                voiceGain = cur.voiceGain,
                recentRooms = cur.recentRooms,
            )
        }
    }

    private fun cleanupLocalRoomResources() {
        syncLoopJob?.cancel()
        syncLoopJob = null
        resolveMediaJob?.cancel()
        resolveMediaJob = null
        rateRestoreJob?.cancel()
        rateRestoreJob = null
        pendingInitialTrack = null
        lastLoadedMediaKey = ""
        localBufferingReported = false

        runCatching { voiceEngine?.release() }
        voiceEngine = null

        playerEngine.dispatch(PlayerCommand.Stop)
        playerEngine.setPlaybackSpeed(1.0f)
        playerEngine.setVolume(1.0f)
        playerEngine.setRoomMode(false)
    }

    private fun ensureVoiceInitialized(
        selfUserId: String,
        iceServers: List<org.webrtc.PeerConnection.IceServer>,
        members: List<TogetherMember>,
    ) {
        if (voiceEngine == null) {
            voiceEngine = TogetherVoiceEngine(
                context = appContext,
                scope = scope,
                callbacks = object : TogetherVoiceEngine.Callbacks {
                    override fun onSendOffer(targetUserId: String, type: String, sdp: String) {
                        wsClient.sendRtcOffer(targetUserId, type, sdp)
                    }

                    override fun onSendAnswer(targetUserId: String, type: String, sdp: String) {
                        wsClient.sendRtcAnswer(targetUserId, type, sdp)
                    }

                    override fun onSendIce(
                        targetUserId: String,
                        candidate: String,
                        sdpMid: String?,
                        sdpMLineIndex: Int,
                    ) {
                        wsClient.sendRtcIce(targetUserId, candidate, sdpMid, sdpMLineIndex)
                    }

                    override fun onLocalSpeakingChanged(speaking: Boolean) {
                        val muted = !_ui.value.micEnabled
                        wsClient.sendVoiceState(isMuted = muted, isSpeaking = speaking)
                        _ui.update { cur ->
                            cur.copy(
                                members = cur.members.map { m ->
                                    if (m.userId == cur.selfUserId) {
                                        m.copy(isMuted = muted, isSpeaking = speaking)
                                    } else m
                                },
                            )
                        }
                    }

                    override fun onRemoteSpeakingMap(map: Map<String, Boolean>) {
                        _ui.update { cur ->
                            cur.copy(
                                members = cur.members.map { m ->
                                    if (m.userId == cur.selfUserId) m
                                    else m.copy(isSpeaking = map[m.userId] == true)
                                },
                            )
                        }
                    }

                    override fun onVoiceReadyChanged(ready: Boolean) {
                        _ui.update { cur ->
                            cur.copy(
                                voiceReady = ready,
                                micPermissionGranted = voiceEngine?.hasMicPermission() == true,
                            )
                        }
                    }

                    override fun onVoiceError(message: String) {
                        _ui.update { it.copy(errorBanner = message) }
                    }
                },
            )
        }
        val engine = voiceEngine ?: return
        val hasPerm = engine.hasMicPermission()
        val cur = _ui.value
        val micOn = cur.micEnabled && hasPerm
        _ui.update { it.copy(micPermissionGranted = hasPerm, micEnabled = micOn) }
        engine.initialize(
            selfUserId = selfUserId,
            iceServers = iceServers,
            micEnabled = micOn,
            speakerOn = cur.speakerOn,
            voiceGain = cur.voiceGain,
        )
        engine.syncMembers(members, selfUserId)
        wsClient.sendVoiceState(isMuted = !micOn, isSpeaking = false)
    }

    fun toggleMic(requestPermission: () -> Unit) {
        val cur = _ui.value
        val engine = voiceEngine
        if (!cur.micEnabled) {
            if (engine == null || !engine.hasMicPermission()) {
                requestPermission()
                return
            }
            engine.onMicPermissionGrantedAndEnable()
            _ui.update { curState ->
                curState.copy(
                    micEnabled = true,
                    micPermissionGranted = true,
                    members = curState.members.map { m ->
                        if (m.userId == curState.selfUserId) m.copy(isMuted = false) else m
                    },
                )
            }
            wsClient.sendVoiceState(isMuted = false, isSpeaking = false)
        } else {
            engine?.setMicEnabled(false)
            _ui.update { curState ->
                curState.copy(
                    micEnabled = false,
                    members = curState.members.map { m ->
                        if (m.userId == curState.selfUserId) m.copy(isMuted = true, isSpeaking = false) else m
                    },
                )
            }
            wsClient.sendVoiceState(isMuted = true, isSpeaking = false)
        }
    }

    fun onMicPermissionGranted() {
        val engine = voiceEngine ?: return
        engine.onMicPermissionGrantedAndEnable()
        _ui.update { cur ->
            cur.copy(
                micEnabled = true,
                micPermissionGranted = true,
                members = cur.members.map { m ->
                    if (m.userId == cur.selfUserId) m.copy(isMuted = false) else m
                },
            )
        }
        wsClient.sendVoiceState(isMuted = false, isSpeaking = false)
    }

    fun toggleSpeaker() {
        val next = !_ui.value.speakerOn
        _ui.update { it.copy(speakerOn = next) }
        voiceEngine?.setSpeakerOn(next)
    }

    fun toggleVolumeDrawer() {
        _ui.update { it.copy(showVolumeDrawer = !it.showVolumeDrawer) }
    }

    fun toggleDanmaku() {
        _ui.update { it.copy(danmakuEnabled = !it.danmakuEnabled) }
    }

    fun setMediaVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        _ui.update { it.copy(mediaVolume = v) }
        playerEngine.setVolume(v)
        scope.launch { prefs.setVolumes(v, _ui.value.voiceGain) }
    }

    fun setVoiceGain(gain: Float) {
        val g = gain.coerceIn(0f, 2f)
        _ui.update { it.copy(voiceGain = g) }
        voiceEngine?.setVoiceGain(g)
        scope.launch { prefs.setVolumes(_ui.value.mediaVolume, g) }
    }

    fun toggleVideoExpanded() {
        val next = !_ui.value.videoExpanded
        _ui.update { it.copy(videoExpanded = next) }
        val pb = _ui.value.playback
        if (!pb.videoUrl.isNullOrBlank()) {
            // 仅切换本地画/音流模式并对齐到房间当前时间戳
            handlePlaybackChanged(pb, forceReload = true)
        }
    }

    fun toggleControlMode() {
        val cur = _ui.value
        if (!cur.isHost) return
        val next = if (cur.controlMode == TogetherControlMode.HOST_ONLY) {
            TogetherControlMode.EVERYONE
        } else {
            TogetherControlMode.HOST_ONLY
        }
        wsClient.sendControlMode(next)
    }

    fun pickTrack(track: Track) {
        val cur = _ui.value
        if (!cur.isJoined) return
        if (!cur.canControl) {
            _ui.update { it.copy(errorBanner = "当前仅房主可点歌或切歌") }
            return
        }
        if (track.bvid.isBlank() && track.streamUrl.isNullOrBlank()) {
            _ui.update { it.copy(errorBanner = "该曲目缺少 B 站稿件信息") }
            return
        }

        val media = TogetherMediaCodec.encodeTrack(track)
        val serverNow = System.currentTimeMillis() + cur.serverClockOffsetMs
        val newPb = TogetherPlaybackInfo(
            videoUrl = media.videoUrl,
            headers = media.headers,
            title = media.title,
            isPlaying = true,
            anchorTimeSec = 0.0,
            anchorServerTimeMs = serverNow,
            playbackRate = 1.0f,
            durationSec = media.durationSec,
            source = media.source,
            bvid = track.bvid,
            cid = track.cid,
            track = track,
        )
        _ui.update { it.copy(playback = newPb, errorBanner = null) }
        wsClient.sendSyncAction("change_media", mediaPayload = media)
        wsClient.sendSyncAction("play", currentTimeSec = 0.0)
        handlePlaybackChanged(newPb, forceReload = true, enrichedSeed = track)
    }

    fun togglePlayPause() {
        val cur = _ui.value
        if (!cur.canControl) {
            _ui.update { it.copy(errorBanner = "当前仅房主可控制播放") }
            return
        }
        if (cur.playback.videoUrl.isNullOrBlank()) return
        val wantPlay = !cur.playback.isPlaying
        val posSec = playerEngine.currentPositionMs() / 1000.0
        val serverNow = System.currentTimeMillis() + cur.serverClockOffsetMs
        val updated = cur.playback.copy(
            isPlaying = wantPlay,
            anchorTimeSec = posSec,
            anchorServerTimeMs = serverNow,
        )
        _ui.update { it.copy(playback = updated) }
        wsClient.sendSyncAction(if (wantPlay) "play" else "pause", currentTimeSec = posSec)
        applyAnchorImmediate(updated)
    }

    fun seekToMs(targetMs: Long) {
        val cur = _ui.value
        if (!cur.canControl) {
            _ui.update { it.copy(errorBanner = "当前仅房主可调整进度") }
            return
        }
        if (cur.playback.videoUrl.isNullOrBlank()) return
        val safeMs = targetMs.coerceAtLeast(0L)
        val sec = safeMs / 1000.0
        val serverNow = System.currentTimeMillis() + cur.serverClockOffsetMs
        val updated = cur.playback.copy(
            anchorTimeSec = sec,
            anchorServerTimeMs = serverNow,
        )
        _ui.update { it.copy(playback = updated, localPositionMs = safeMs) }
        withRemoteSyncGuard {
            playerEngine.dispatch(PlayerCommand.Seek(safeMs))
        }
        wsClient.sendSyncAction("seek", currentTimeSec = sec)
    }

    fun sendChat(content: String, sendAsDanmaku: Boolean) {
        val clean = content.trim()
        if (clean.isEmpty() || !_ui.value.isJoined) return
        val videoTsSec = playerEngine.currentPositionMs() / 1000.0
        wsClient.sendChatMessage(
            content = clean.take(200),
            sendAsDanmaku = sendAsDanmaku,
            videoTimestampSec = videoTsSec,
        )
    }

    fun onHostLifecycle(inBackground: Boolean) {
        voiceEngine?.ensureSystemMicNotMuted()
        if (!_ui.value.isJoined) return
        wsClient.sendVoiceState(
            isMuted = !_ui.value.micEnabled,
            isSpeaking = false,
            isAway = inBackground,
        )
        if (!inBackground) {
            wsClient.reconnectNow()
        }
    }

    fun ensureSystemMicSafe() {
        voiceEngine?.ensureSystemMicNotMuted()
        if (!_ui.value.isJoined) {
            voiceEngine?.restoreSystemAudioMode()
        }
    }

    private fun handlePlaybackChanged(
        playback: TogetherPlaybackInfo,
        forceReload: Boolean,
        enrichedSeed: Track? = null,
    ) {
        val rawUrl = playback.videoUrl?.takeIf { it.isNotBlank() } ?: return
        val videoExpanded = _ui.value.videoExpanded
        val key = "$rawUrl#video=$videoExpanded"
        if (!forceReload && key == lastLoadedMediaKey) {
            applyAnchorImmediate(playback)
            return
        }
        lastLoadedMediaKey = key
        val gen = ++mediaLoadGeneration

        resolveMediaJob?.cancel()
        resolveMediaJob = scope.launch {
            _ui.update { it.copy(localIsLoading = true, errorBanner = null) }
            val baseTrack = enrichedSeed
                ?: playback.track
                ?: TogetherMediaCodec.decodeTrack(playback)
                ?: run {
                    _ui.update { it.copy(localIsLoading = false) }
                    return@launch
                }

            val resolvedTrack = runCatching {
                resolveTrackWithMeta(baseTrack, videoExpanded)
            }.getOrElse { err ->
                Log.w(TAG, "Resolve room media failed: ${err.message}")
                _ui.update {
                    it.copy(
                        localIsLoading = false,
                        errorBanner = "取流失败：${err.message ?: "请检查 B 站登录或网络"}",
                    )
                }
                return@launch
            }

            if (gen != mediaLoadGeneration || !_ui.value.isJoined) return@launch

            val serverNow = System.currentTimeMillis() + _ui.value.serverClockOffsetMs
            val latestPb = _ui.value.playback.copy(track = resolvedTrack)
            val startPosMs = latestPb.expectedPositionMs(serverNow)
                .let { target ->
                    val dur = resolvedTrack.durationMs
                    if (dur > 0L) target.coerceIn(0L, (dur - 500L).coerceAtLeast(0L)) else target
                }

            _ui.update {
                it.copy(
                    playback = latestPb,
                    localDurationMs = resolvedTrack.durationMs,
                    localIsLoading = false,
                )
            }

            ensurePlaybackService()
            withRemoteSyncGuard {
                playerEngine.setVolume(_ui.value.mediaVolume)
                playerEngine.prepareTrack(resolvedTrack, asVideo = videoExpanded)
                playerEngine.dispatch(
                    PlayerCommand.PlayTrack(
                        track = resolvedTrack,
                        queue = listOf(resolvedTrack),
                        startPositionMs = startPosMs,
                    ),
                )
                if (!latestPb.isPlaying) {
                    playerEngine.dispatch(PlayerCommand.Pause)
                }
            }
        }
    }

    private suspend fun resolveTrackWithMeta(seed: Track, videoMode: Boolean): Track =
        withContext(Dispatchers.IO) {
            if (seed.bvid.isBlank() && !seed.streamUrl.isNullOrBlank()) {
                return@withContext seed
            }
            var enriched = metaCache[seed.bvid]?.let { cached ->
                seed.copy(
                    title = if (seed.title.isBlank() || seed.title == seed.bvid) cached.title else seed.title,
                    artist = if (seed.artist == "Bilibili" || seed.artist.isBlank()) cached.artist else seed.artist,
                    coverUrl = seed.coverUrl ?: cached.coverUrl,
                    durationMs = if (seed.durationMs <= 0L) cached.durationMs else seed.durationMs,
                    aid = seed.aid.ifBlank { cached.aid },
                    cid = seed.cid.ifBlank { cached.cid },
                    ownerMid = seed.ownerMid.ifBlank { cached.ownerMid },
                )
            } ?: seed

            if (enriched.bvid.isNotBlank() &&
                (enriched.coverUrl.isNullOrBlank() || enriched.cid.isBlank() || enriched.artist == "Bilibili")
            ) {
                val parts = runCatching { biliApi.listArchiveParts(enriched.bvid) }.getOrDefault(emptyList())
                val matched = if (enriched.cid.isNotBlank()) {
                    parts.firstOrNull { it.cid == enriched.cid } ?: parts.firstOrNull()
                } else {
                    parts.firstOrNull()
                }
                if (matched != null) {
                    metaCache[enriched.bvid] = matched
                    enriched = matched.copy(
                        title = if (enriched.title.isBlank() || enriched.title == enriched.bvid) matched.title else enriched.title,
                    )
                }
            }

            biliApi.resolvePlayUrl(
                track = enriched,
                preferredQn = qualityQnProvider(),
                videoMode = videoMode,
            )
        }

    private fun applyAnchorImmediate(pb: TogetherPlaybackInfo) {
        if (pb.videoUrl.isNullOrBlank()) return
        val serverNow = System.currentTimeMillis() + _ui.value.serverClockOffsetMs
        val expectedMs = pb.expectedPositionMs(serverNow)
        val curPos = playerEngine.currentPositionMs()
        val diff = kotlin.math.abs(curPos - expectedMs)

        withRemoteSyncGuard {
            if (diff > 1_200L) {
                playerEngine.dispatch(PlayerCommand.Seek(expectedMs))
            }
            playerEngine.setPlaybackSpeed(pb.playbackRate)
            if (pb.isPlaying && !playerEngine.isPlayerPlaying()) {
                playerEngine.dispatch(PlayerCommand.Play)
            } else if (!pb.isPlaying && playerEngine.isPlayerPlaying()) {
                playerEngine.dispatch(PlayerCommand.Pause)
            }
        }
    }

    private fun startSyncLoop() {
        syncLoopJob?.cancel()
        syncLoopJob = scope.launch {
            while (isActive && _ui.value.isJoined) {
                delay(650L)
                val cur = _ui.value
                val posMs = playerEngine.currentPositionMs()
                val durMs = playerEngine.currentDurationMs().takeIf { it > 0L }
                    ?: cur.playback.track?.durationMs
                    ?: ((cur.playback.durationSec ?: 0.0) * 1000.0).toLong()
                val playing = playerEngine.isPlayerPlaying()
                val buffering = playerEngine.isPlayerBuffering() && !cur.playback.videoUrl.isNullOrBlank()

                if (buffering != localBufferingReported && !cur.localIsLoading) {
                    localBufferingReported = buffering
                    wsClient.sendBufferState(buffering)
                }

                _ui.update {
                    it.copy(
                        localPositionMs = posMs,
                        localDurationMs = durMs,
                        localIsPlaying = playing,
                    )
                }

                if (cur.localIsLoading || cur.playback.videoUrl.isNullOrBlank() || applyingRemoteSync) {
                    continue
                }
                if (cur.bufferingMembers.isNotEmpty()) {
                    continue
                }

                val serverNow = System.currentTimeMillis() + cur.serverClockOffsetMs
                val expectedMs = cur.playback.expectedPositionMs(serverNow)
                val boundedExpected = if (durMs > 1_000L) {
                    expectedMs.coerceIn(0L, durMs)
                } else {
                    expectedMs
                }

                if (cur.playback.isPlaying) {
                    // 若曲终附近则停留在结尾不硬追
                    if (durMs > 1_000L && boundedExpected >= durMs - 400L) {
                        if (playing && posMs >= durMs - 300L) {
                            withRemoteSyncGuard { playerEngine.dispatch(PlayerCommand.Pause) }
                        }
                        continue
                    }
                    if (!playing && !buffering) {
                        withRemoteSyncGuard { playerEngine.dispatch(PlayerCommand.Play) }
                    }
                    val drift = posMs - boundedExpected
                    val absDrift = kotlin.math.abs(drift)
                    if (absDrift > 2_300L) {
                        rateRestoreJob?.cancel()
                        withRemoteSyncGuard {
                            playerEngine.setPlaybackSpeed(cur.playback.playbackRate)
                            playerEngine.dispatch(PlayerCommand.Seek(boundedExpected))
                        }
                    } else if (absDrift > 480L && rateRestoreJob?.isActive != true) {
                        val factor = if (drift < 0L) 1.07f else 0.93f
                        val nudgeRate = (cur.playback.playbackRate * factor).coerceIn(0.5f, 2.0f)
                        withRemoteSyncGuard { playerEngine.setPlaybackSpeed(nudgeRate) }
                        rateRestoreJob = scope.launch {
                            delay(1_400L)
                            withRemoteSyncGuard {
                                playerEngine.setPlaybackSpeed(_ui.value.playback.playbackRate)
                            }
                        }
                    }
                } else {
                    if (playing) {
                        withRemoteSyncGuard { playerEngine.dispatch(PlayerCommand.Pause) }
                    }
                    if (kotlin.math.abs(posMs - boundedExpected) > 1_200L) {
                        withRemoteSyncGuard { playerEngine.dispatch(PlayerCommand.Seek(boundedExpected)) }
                    }
                }
            }
        }
    }

    private inline fun withRemoteSyncGuard(block: () -> Unit) {
        applyingRemoteSync = true
        try {
            block()
        } finally {
            applyingRemoteSync = false
        }
    }

    private fun appendSystemMessage(text: String) {
        val msg = TogetherChatMessage(
            id = "sys_${UUID.randomUUID()}",
            userId = "system",
            nickname = "系统",
            content = text,
            isSystem = true,
            timestamp = System.currentTimeMillis(),
        )
        _ui.update { cur ->
            cur.copy(chatMessages = (cur.chatMessages + msg).takeLast(120))
        }
    }

    companion object {
        private const val TAG = "TogetherRoomManager"
        private val ROOM_ID_REGEX = Regex("^[A-Za-z0-9_-]{3,32}$")
    }
}
