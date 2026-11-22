package com.madus.mobile.together

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class TogetherVoiceEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onSendOffer(targetUserId: String, type: String, sdp: String)
        fun onSendAnswer(targetUserId: String, type: String, sdp: String)
        fun onSendIce(targetUserId: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int)
        fun onLocalSpeakingChanged(speaking: Boolean)
        fun onRemoteSpeakingMap(map: Map<String, Boolean>)
        fun onVoiceReadyChanged(ready: Boolean)
        fun onVoiceError(message: String)
    }

    private val appContext = context.applicationContext
    private val peers = ConcurrentHashMap<String, PeerConnection>()
    private val remoteAudioTracks = ConcurrentHashMap<String, AudioTrack>()
    private val pendingIce = ConcurrentHashMap<String, MutableList<IceCandidate>>()
    private val remoteSpeaking = ConcurrentHashMap<String, Boolean>()

    private var factory: PeerConnectionFactory? = null
    private var audioDeviceModule: JavaAudioDeviceModule? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    @Volatile
    private var iceServers: List<PeerConnection.IceServer> = emptyList()

    @Volatile
    private var selfUserId: String = ""

    @Volatile
    private var initialized: Boolean = false

    @Volatile
    private var disposed: Boolean = false

    @Volatile
    private var micEnabled: Boolean = false

    @Volatile
    private var speakerOn: Boolean = true

    @Volatile
    private var voiceGain: Float = 1.0f

    @Volatile
    private var localSpeaking: Boolean = false

    private var statsJob: Job? = null

    fun isInitialized(): Boolean = initialized && !disposed

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    fun ensureSystemMicNotMuted() {
        runCatching {
            val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (am.isMicrophoneMute) {
                am.isMicrophoneMute = false
            }
        }
    }

    fun restoreSystemAudioMode() {
        runCatching {
            val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            am.isMicrophoneMute = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { am.clearCommunicationDevice() }
            }
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = false
            am.mode = AudioManager.MODE_NORMAL
        }
    }

    @Synchronized
    fun initialize(
        selfUserId: String,
        iceServers: List<PeerConnection.IceServer>,
        micEnabled: Boolean,
        speakerOn: Boolean,
        voiceGain: Float,
    ) {
        if (disposed) return
        this.selfUserId = selfUserId
        this.iceServers = iceServers
        this.micEnabled = micEnabled
        this.speakerOn = speakerOn
        this.voiceGain = voiceGain.coerceIn(0f, 2f)

        ensureSystemMicNotMuted()
        if (initialized) {
            applyAudioRouting()
            setMicEnabled(micEnabled)
            return
        }

        runCatching {
            ensureFactoryInitialized(appContext)
            val adm = JavaAudioDeviceModule.builder(appContext)
                .setUseHardwareAcousticEchoCanceler(JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported())
                .setUseHardwareNoiseSuppressor(JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported())
                .createAudioDeviceModule()
            audioDeviceModule = adm

            val pcFactory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(adm)
                .createPeerConnectionFactory()
            factory = pcFactory

            if (hasMicPermission()) {
                attachLocalAudioTrack(pcFactory, enabled = micEnabled)
            }
            initialized = true
            applyAudioRouting()
            startStatsLoop()
            callbacks.onVoiceReadyChanged(true)
        }.onFailure { err ->
            Log.e(TAG, "WebRTC init failed", err)
            callbacks.onVoiceReadyChanged(false)
            callbacks.onVoiceError("语音引擎初始化失败：${err.message}")
        }
    }

    @Synchronized
    fun onMicPermissionGrantedAndEnable() {
        if (disposed || !initialized || !hasMicPermission()) return
        val pcFactory = factory ?: return
        if (localAudioTrack == null) {
            attachLocalAudioTrack(pcFactory, enabled = true)
            val existingPeers = peers.toMap()
            existingPeers.forEach { (peerId, pc) ->
                localAudioTrack?.let { track ->
                    runCatching { pc.addTrack(track, listOf(STREAM_ID)) }
                }
                if (selfUserId < peerId) {
                    scope.launch { createAndSendOffer(peerId, pc) }
                }
            }
        }
        setMicEnabled(true)
    }

    private fun attachLocalAudioTrack(pcFactory: PeerConnectionFactory, enabled: Boolean) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }
        val src = pcFactory.createAudioSource(constraints)
        val track = pcFactory.createAudioTrack("madus_mic_$selfUserId", src)
        // 绝不修改系统级 AudioManager.isMicrophoneMute；仅控制当前 WebRTC 轨开关
        track.setEnabled(enabled)
        audioSource = src
        localAudioTrack = track
    }

    fun setMicEnabled(enabled: Boolean) {
        this.micEnabled = enabled
        ensureSystemMicNotMuted()
        localAudioTrack?.setEnabled(enabled)
        if (!enabled && localSpeaking) {
            localSpeaking = false
            callbacks.onLocalSpeakingChanged(false)
        }
    }

    fun setSpeakerOn(on: Boolean) {
        this.speakerOn = on
        applyAudioRouting()
    }

    fun setVoiceGain(gain: Float) {
        val clamped = gain.coerceIn(0f, 2f)
        this.voiceGain = clamped
        remoteAudioTracks.values.forEach { track ->
            runCatching { track.setVolume(clamped.toDouble()) }
        }
    }

    private fun applyAudioRouting() {
        runCatching {
            val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            am.isMicrophoneMute = false
            am.mode = AudioManager.MODE_IN_COMMUNICATION

            // 若连了蓝牙耳机或有线耳机，不强行抢回外放
            val hasExternalHeadset = hasWiredOrBluetoothOutput(am)
            if (hasExternalHeadset) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    runCatching { am.clearCommunicationDevice() }
                }
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = false
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val targetType = if (speakerOn) {
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                } else {
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }
                val device = am.availableCommunicationDevices.firstOrNull { it.type == targetType }
                if (device != null) {
                    am.setCommunicationDevice(device)
                }
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = speakerOn
            }
        }
    }

    private fun hasWiredOrBluetoothOutput(am: AudioManager): Boolean {
        val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return outputs.any { dev ->
            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                dev.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    }

    fun syncMembers(members: List<TogetherMember>, selfId: String) {
        if (!initialized || disposed || selfId.isBlank()) return
        this.selfUserId = selfId
        val activeIds = members.map { it.userId }.filter { it.isNotBlank() && it != selfId }.toSet()

        // 移除已离房成员连接
        val stale = peers.keys.filter { it !in activeIds }
        stale.forEach { removePeer(it) }

        // 只由字典序较小一方主动发 Offer，彻底避免双端同时 Offer 冲突
        activeIds.forEach { peerId ->
            if (!peers.containsKey(peerId)) {
                val initiate = selfId < peerId
                connectToPeer(peerId, initiateOffer = initiate)
            }
        }
    }

    @Synchronized
    private fun connectToPeer(peerId: String, initiateOffer: Boolean): PeerConnection? {
        if (disposed || !initialized) return null
        peers[peerId]?.let { return it }
        val pcFactory = factory ?: return null

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val pc = pcFactory.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate?) {
                    if (candidate == null || disposed) return
                    callbacks.onSendIce(peerId, candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)
                }

                override fun onTrack(transceiver: RtpTransceiver?) {
                    val track = transceiver?.receiver?.track() as? AudioTrack ?: return
                    track.setEnabled(true)
                    runCatching { track.setVolume(voiceGain.toDouble()) }
                    remoteAudioTracks[peerId] = track
                }

                override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                    val track = receiver?.track() as? AudioTrack ?: return
                    track.setEnabled(true)
                    runCatching { track.setVolume(voiceGain.toDouble()) }
                    remoteAudioTracks[peerId] = track
                }

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    if (state == PeerConnection.IceConnectionState.FAILED) {
                        if (selfUserId < peerId) {
                            scope.launch {
                                val cur = peers[peerId] ?: return@launch
                                createAndSendOffer(peerId, cur, iceRestart = true)
                            }
                        }
                    }
                }

                override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                override fun onAddStream(stream: MediaStream?) {}
                override fun onRemoveStream(stream: MediaStream?) {}
                override fun onDataChannel(dataChannel: DataChannel?) {}
                override fun onRenegotiationNeeded() {}
            },
        ) ?: return null

        localAudioTrack?.let { track ->
            runCatching { pc.addTrack(track, listOf(STREAM_ID)) }
        }
        peers[peerId] = pc

        if (initiateOffer) {
            scope.launch { createAndSendOffer(peerId, pc) }
        }
        return pc
    }

    fun handleOffer(fromUserId: String, type: String, sdp: String) {
        if (!initialized || disposed) return
        scope.launch {
            val pc = peers[fromUserId] ?: connectToPeer(fromUserId, initiateOffer = false) ?: return@launch
            val desc = SessionDescription(
                SessionDescription.Type.fromCanonicalForm(type),
                sdp,
            )
            val setOk = suspendSetRemoteDescription(pc, desc)
            if (!setOk) return@launch
            flushPendingIce(fromUserId, pc)

            val answer = suspendCreateAnswer(pc) ?: return@launch
            if (suspendSetLocalDescription(pc, answer)) {
                callbacks.onSendAnswer(fromUserId, answer.type.canonicalForm(), answer.description)
            }
        }
    }

    fun handleAnswer(fromUserId: String, type: String, sdp: String) {
        if (!initialized || disposed) return
        val pc = peers[fromUserId] ?: return
        scope.launch {
            val desc = SessionDescription(
                SessionDescription.Type.fromCanonicalForm(type),
                sdp,
            )
            if (suspendSetRemoteDescription(pc, desc)) {
                flushPendingIce(fromUserId, pc)
            }
        }
    }

    fun handleIce(fromUserId: String, candidateStr: String, sdpMid: String?, sdpMLineIndex: Int) {
        if (!initialized || disposed) return
        val candidate = IceCandidate(sdpMid.orEmpty(), sdpMLineIndex, candidateStr)
        val pc = peers[fromUserId]
        if (pc == null || pc.remoteDescription == null) {
            pendingIce.getOrPut(fromUserId) { mutableListOf() }.add(candidate)
            return
        }
        runCatching { pc.addIceCandidate(candidate) }
    }

    @Synchronized
    private fun removePeer(peerId: String) {
        remoteAudioTracks.remove(peerId)
        pendingIce.remove(peerId)
        remoteSpeaking.remove(peerId)
        peers.remove(peerId)?.let { pc ->
            runCatching { pc.close() }
            runCatching { pc.dispose() }
        }
        callbacks.onRemoteSpeakingMap(remoteSpeaking.toMap())
    }

    private fun flushPendingIce(peerId: String, pc: PeerConnection) {
        val list = pendingIce.remove(peerId) ?: return
        list.forEach { cand -> runCatching { pc.addIceCandidate(cand) } }
    }

    private suspend fun createAndSendOffer(
        peerId: String,
        pc: PeerConnection,
        iceRestart: Boolean = false,
    ) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            if (iceRestart) {
                mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            }
        }
        val offer = suspendCreateOffer(pc, constraints) ?: return
        if (suspendSetLocalDescription(pc, offer)) {
            callbacks.onSendOffer(peerId, offer.type.canonicalForm(), offer.description)
        }
    }

    private fun startStatsLoop() {
        statsJob?.cancel()
        statsJob = scope.launch(Dispatchers.Default) {
            while (isActive && initialized && !disposed) {
                delay(550L)
                val snapshot = peers.entries.toList()
                if (snapshot.isEmpty()) continue
                var measuredLocal = false
                snapshot.forEach { (peerId, pc) ->
                    runCatching {
                        pc.getStats { report ->
                            var remoteLvl = 0.0
                            var localLvl = 0.0
                            for (stat in report.statsMap.values) {
                                if (stat.type == "inbound-rtp") {
                                    val kind = stat.members["kind"]?.toString()
                                    if (kind == "audio") {
                                        val lvl = (stat.members["audioLevel"] as? Number)?.toDouble() ?: 0.0
                                        if (lvl > remoteLvl) remoteLvl = lvl
                                    }
                                } else if (stat.type == "media-source" && !measuredLocal) {
                                    val kind = stat.members["kind"]?.toString()
                                    if (kind == "audio") {
                                        val lvl = (stat.members["audioLevel"] as? Number)?.toDouble() ?: 0.0
                                        if (lvl > localLvl) localLvl = lvl
                                    }
                                }
                            }
                            val remoteNow = remoteLvl > SPEAKING_THRESHOLD
                            if (remoteSpeaking[peerId] != remoteNow) {
                                remoteSpeaking[peerId] = remoteNow
                                callbacks.onRemoteSpeakingMap(remoteSpeaking.toMap())
                            }
                            if (!measuredLocal) {
                                measuredLocal = true
                                val localNow = micEnabled && localLvl > SPEAKING_THRESHOLD
                                if (localNow != localSpeaking) {
                                    localSpeaking = localNow
                                    callbacks.onLocalSpeakingChanged(localNow)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Synchronized
    fun release() {
        if (disposed) return
        disposed = true
        initialized = false
        statsJob?.cancel()
        statsJob = null

        remoteAudioTracks.clear()
        pendingIce.clear()
        remoteSpeaking.clear()
        peers.values.forEach { pc ->
            runCatching { pc.close() }
            runCatching { pc.dispose() }
        }
        peers.clear()

        runCatching { localAudioTrack?.dispose() }
        localAudioTrack = null
        runCatching { audioSource?.dispose() }
        audioSource = null
        runCatching { audioDeviceModule?.release() }
        audioDeviceModule = null
        runCatching { factory?.dispose() }
        factory = null

        restoreSystemAudioMode()
        callbacks.onVoiceReadyChanged(false)
    }

    private suspend fun suspendCreateOffer(
        pc: PeerConnection,
        constraints: MediaConstraints,
    ): SessionDescription? = suspendCoroutine { cont ->
        pc.createOffer(
            object : SdpObserverAdapter() {
                override fun onCreateSuccess(desc: SessionDescription?) = cont.resume(desc)
                override fun onCreateFailure(err: String?) = cont.resume(null)
            },
            constraints,
        )
    }

    private suspend fun suspendCreateAnswer(pc: PeerConnection): SessionDescription? =
        suspendCoroutine { cont ->
            pc.createAnswer(
                object : SdpObserverAdapter() {
                    override fun onCreateSuccess(desc: SessionDescription?) = cont.resume(desc)
                    override fun onCreateFailure(err: String?) = cont.resume(null)
                },
                MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
                },
            )
        }

    private suspend fun suspendSetLocalDescription(
        pc: PeerConnection,
        desc: SessionDescription,
    ): Boolean = suspendCoroutine { cont ->
        pc.setLocalDescription(
            object : SdpObserverAdapter() {
                override fun onSetSuccess() = cont.resume(true)
                override fun onSetFailure(err: String?) = cont.resume(false)
            },
            desc,
        )
    }

    private suspend fun suspendSetRemoteDescription(
        pc: PeerConnection,
        desc: SessionDescription,
    ): Boolean = suspendCoroutine { cont ->
        pc.setRemoteDescription(
            object : SdpObserverAdapter() {
                override fun onSetSuccess() = cont.resume(true)
                override fun onSetFailure(err: String?) = cont.resume(false)
            },
            desc,
        )
    }

    private open class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(err: String?) {}
        override fun onSetFailure(err: String?) {}
    }

    companion object {
        private const val TAG = "TogetherVoiceEngine"
        private const val STREAM_ID = "madus_voice"
        private const val SPEAKING_THRESHOLD = 0.025

        @Volatile
        private var factoryOnce = false

        @Synchronized
        private fun ensureFactoryInitialized(appContext: Context) {
            if (factoryOnce) return
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(appContext)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions(),
            )
            factoryOnce = true
        }
    }
}
