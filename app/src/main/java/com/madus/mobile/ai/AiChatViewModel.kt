package com.madus.mobile.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.madus.mobile.MadusApp
import com.madus.mobile.data.BilibiliApi
import com.madus.mobile.domain.MusicSourceType
import com.madus.mobile.domain.Track
import com.madus.mobile.source.SourceRegistry
import android.media.MediaPlayer
import android.net.Uri
import java.io.File
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
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

data class AiChatUiState(
    val config: LlmConfigState = LlmConfigState(),
    val messages: List<AiChatMessage> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val status: String? = null,
    /** 空状态使用说明；点过「我知道了」后本机记住 */
    val showGuide: Boolean = true,
    val sessionId: String? = null,
    val history: List<AiChatSessionSummary> = emptyList(),
    val showHistoryPanel: Boolean = false,
    /** 正在录音 */
    val isRecording: Boolean = false,
    val recordingElapsedMs: Long = 0L,
    /** 声浪：最近若干帧 0~1 */
    val waveform: List<Float> = emptyList(),
    val micLevel: Float = 0f,
    /** 最近一次录音文件，供“回听” */
    val lastRecordingPath: String? = null,
    val lastRecordingDurationMs: Long = 0L,
    val isPlayingRecording: Boolean = false,
    /** 模型过程面板：默认折叠成一行，可全屏展开 */
    val modelProcessExpanded: Boolean = false,
    /** 讯飞哼唱识别是否已配置 AppID + API Key */
    val hummingReady: Boolean = false,
)

class AiChatViewModel(
    private val configStore: LlmConfigStore,
    private val historyStore: AiChatHistoryStore,
    private val client: LlmClient,
    private val hummingStore: HummingConfigStore,
    private val registry: SourceRegistry,
    private val biliApi: BilibiliApi,
    private val appContext: android.content.Context,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        AiChatUiState(
            config = configStore.state.value,
            showGuide = !historyStore.isGuideDismissed(),
            history = historyStore.summaries(),
            hummingReady = hummingStore.state.value.isConfigured,
        ),
    )
    val ui: StateFlow<AiChatUiState> = _ui.asStateFlow()

    private val humRecorder = HumRecorder(appContext)
    private val hummingRecognizer = XunfeiHummingRecognizer()
    private val acrRecognizer = AcrCloudRecognizer()
    private var recordTicker: Job? = null
    private var recordingPlayer: MediaPlayer? = null
    /** 防止连点导致「停→又开录」 */
    private var recordBusy = false

    init {
        viewModelScope.launch {
            configStore.state.collect { cfg ->
                _ui.update { it.copy(config = cfg) }
            }
        }
        viewModelScope.launch {
            historyStore.sessions.collect {
                _ui.update { st -> st.copy(history = historyStore.summaries()) }
            }
        }
        viewModelScope.launch {
            hummingStore.state.collect { cfg ->
                _ui.update { it.copy(hummingReady = cfg.isConfigured) }
            }
        }
    }

    fun dismissGuide() {
        historyStore.setGuideDismissed(true)
        _ui.update { it.copy(showGuide = false) }
    }

    fun expandModelProcess() {
        _ui.update { it.copy(modelProcessExpanded = true) }
    }

    fun collapseModelProcess() {
        _ui.update { it.copy(modelProcessExpanded = false) }
    }

    fun togglePlaybackRecording() {
        val path = _ui.value.lastRecordingPath ?: return
        if (_ui.value.isPlayingRecording) {
            stopPlaybackRecording()
            return
        }
        val mp = MediaPlayer()
        runCatching {
            mp.setDataSource(path)
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { stopPlaybackRecording() }
            mp.setOnErrorListener { _, _, _ ->
                stopPlaybackRecording()
                true
            }
            mp.prepareAsync()
            recordingPlayer = mp
            _ui.update { it.copy(isPlayingRecording = true) }
        }.onFailure {
            runCatching { mp.release() }
            recordingPlayer = null
            _ui.update {
                it.copy(
                    isPlayingRecording = false,
                    status = "录音文件已失效，请重新录制",
                )
            }
        }
    }

    private fun stopPlaybackRecording() {
        val p = recordingPlayer
        recordingPlayer = null
        runCatching { p?.stop() }
        runCatching { p?.release() }
        _ui.update { it.copy(isPlayingRecording = false) }
    }

    fun openHistory() {
        _ui.update {
            it.copy(
                showHistoryPanel = true,
                history = historyStore.summaries(),
            )
        }
    }

    fun closeHistory() {
        _ui.update { it.copy(showHistoryPanel = false) }
    }

    fun newChat() {
        stopPlaybackRecording()
        _ui.value.lastRecordingPath?.let { runCatching { File(it).delete() } }
        _ui.update {
            it.copy(
                messages = emptyList(),
                sessionId = null,
                input = "",
                status = null,
                isSending = false,
                showHistoryPanel = false,
                lastRecordingPath = null,
                lastRecordingDurationMs = 0L,
                modelProcessExpanded = false,
                // 已点过教学不再弹
                showGuide = !historyStore.isGuideDismissed(),
            )
        }
    }

    fun openSession(id: String) {
        val session = historyStore.get(id) ?: return
        stopPlaybackRecording()
        _ui.value.lastRecordingPath?.let { runCatching { File(it).delete() } }
        _ui.update {
            it.copy(
                sessionId = session.id,
                messages = session.messages,
                input = "",
                status = null,
                isSending = false,
                showGuide = false,
                showHistoryPanel = false,
                lastRecordingPath = null,
                lastRecordingDurationMs = 0L,
                modelProcessExpanded = false,
            )
        }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            historyStore.deleteSession(id)
            if (_ui.value.sessionId == id) {
                newChat()
            } else {
                _ui.update { it.copy(history = historyStore.summaries()) }
            }
        }
    }

    fun onInputChange(text: String) {
        _ui.update { it.copy(input = text) }
    }

    fun setActiveProfile(id: String) {
        viewModelScope.launch {
            configStore.setActive(id)
        }
    }

    fun send() {
        val text = _ui.value.input.trim()
        if (text.isEmpty() || _ui.value.isSending) return
        beginTurn(
            userDisplay = text,
            clueForRank = text,
            userEnvelope = SongIdPrompt.userTextEnvelope(text),
            multimodal = null,
        )
    }

    /** 点「录音」：开始 / 再点结束并发送（UI isRecording 与底层状态一并判断，避免误开第二段） */
    fun toggleRecording() {
        if (_ui.value.isSending || recordBusy) return
        if (!_ui.value.hummingReady) {
            _ui.update { it.copy(status = "哼唱走讯飞识别：先到「模型」配置页填写 AppID 和 API Key") }
            return
        }
        if (_ui.value.isRecording || humRecorder.isRecording) {
            stopRecordingAndSend()
        } else {
            startRecording()
        }
    }

    fun cancelRecording() {
        stopPlaybackRecording()
        _ui.value.lastRecordingPath?.let { runCatching { File(it).delete() } }
        recordBusy = true
        recordTicker?.cancel()
        recordTicker = null
        humRecorder.cancel()
        recordBusy = false
        _ui.update {
            it.copy(
                isRecording = false,
                recordingElapsedMs = 0L,
                waveform = emptyList(),
                micLevel = 0f,
                lastRecordingPath = null,
                lastRecordingDurationMs = 0L,
                status = null,
            )
        }
    }

    private fun startRecording() {
        if (recordBusy || humRecorder.isRecording || _ui.value.isRecording) return
        val r = humRecorder.start()
        if (r.isFailure) {
            _ui.update { it.copy(status = "无法开始录音：${r.exceptionOrNull()?.message}") }
            return
        }
        stopPlaybackRecording()
        _ui.value.lastRecordingPath?.let { runCatching { File(it).delete() } }
        val wave = ArrayDeque<Float>(WAVE_BARS)
        repeat(WAVE_BARS) { wave.addLast(0.08f) }
        _ui.update {
            it.copy(
                isRecording = true,
                recordingElapsedMs = 0L,
                waveform = wave.toList(),
                micLevel = 0f,
                lastRecordingPath = null,
                lastRecordingDurationMs = 0L,
                status = "录音中…点红色停止发送",
            )
        }
        recordTicker?.cancel()
        recordTicker = viewModelScope.launch {
            val t0 = humRecorder.startedAtMs.takeIf { it > 0 } ?: System.currentTimeMillis()
            while (isActive) {
                val elapsed = (System.currentTimeMillis() - t0).coerceAtLeast(0L)
                val level = humRecorder.level01
                // 声浪：推进一帧
                val prev = _ui.value.waveform.toMutableList()
                if (prev.size >= WAVE_BARS) prev.removeAt(0)
                // 平滑：新高度 = 当前电平为主，保留一点底座
                val bar = (0.12f + level * 0.88f).coerceIn(0.1f, 1f)
                prev.add(bar)
                while (prev.size < WAVE_BARS) prev.add(0, 0.1f)

                _ui.update {
                    it.copy(
                        recordingElapsedMs = elapsed.coerceAtMost(HumRecorder.MAX_MS),
                        micLevel = level,
                        waveform = prev.takeLast(WAVE_BARS),
                        isRecording = true,
                    )
                }

                // 满时长 或 底层已停：结束并上传（不要误开新录音）
                if (elapsed >= HumRecorder.MAX_MS - 30 || !humRecorder.isRecording) {
                    // 给写文件一点时间
                    if (!humRecorder.isRecording) delay(80)
                    stopRecordingAndSend()
                    break
                }
                delay(50)
            }
        }
    }

    private fun stopRecordingAndSend() {
        if (recordBusy) return
        recordBusy = true
        recordTicker?.cancel()
        recordTicker = null
        // 先锁 UI，避免连点 toggle 又 start
        _ui.update {
            it.copy(
                isRecording = false,
                recordingElapsedMs = 0L,
                waveform = emptyList(),
                micLevel = 0f,
                status = "处理录音…",
            )
        }
        val result = humRecorder.stop()
            ?: humRecorder.takePendingIfFinished()
        recordBusy = false
        if (result == null) {
            _ui.update { it.copy(status = "录音太短或失败，请再录一次（至少 1 秒）") }
            return
        }
        val (file, durMs) = result
        stopPlaybackRecording()
        _ui.update {
            it.copy(
                lastRecordingPath = file.absolutePath,
                lastRecordingDurationMs = durMs,
            )
        }
        val sec = ((durMs + 500) / 1000).coerceAtLeast(1)
        val extra = _ui.value.input.trim()
        beginHummingTurn(file, durMs, extra)
    }

    private fun beginHummingTurn(file: File, durMs: Long, extra: String) {
        val sec = ((durMs + 500) / 1000).coerceAtLeast(1)
        val userMsg = AiChatMessage.User(
            id = UUID.randomUUID().toString(),
            text = "🎤 哼唱 ${sec}s" + if (extra.isNotEmpty()) " · $extra" else "",
        )
        val pendingId = UUID.randomUUID().toString()
        _ui.update { st ->
            st.copy(
                input = "",
                isSending = true,
                status = "① 讯飞哼唱识别…",
                showGuide = false,
                isRecording = false,
                messages = st.messages + userMsg + AiChatMessage.Assistant(
                    id = pendingId,
                    text = "…",
                    isStreaming = true,
                ),
            )
        }
        viewModelScope.launch {
            try {
                val cfg = hummingStore.state.value
                if (!cfg.isConfigured) {
                    finishAssistant(
                        pendingId = pendingId,
                        reply = "哼唱识别需要先配置讯飞 AppID/API Key，或 ACRCloud Host/Access Key/Secret。",
                        candidates = emptyList(),
                        tracks = emptyList(),
                        status = "讯飞未配置",
                    )
                    return@launch
                }
                // 双引擎：有配置就跑；错误透出；通用合并（无单曲硬编码）
                var acrList = emptyList<SongCandidate>()
                var acrErr: String? = null
                var xfList = emptyList<SongCandidate>()
                var xfErr: String? = null

                if (cfg.acrConfigured) {
                    _ui.update { it.copy(status = "① ACRCloud（humming+audio）…") }
                    val acrOut = acrRecognizer.recognize(file, cfg)
                    acrList = acrOut.candidates
                    acrErr = acrOut.error
                }
                if (cfg.xunfeiConfigured) {
                    _ui.update {
                        it.copy(
                            status = if (acrList.isNotEmpty()) {
                                "① ACR ${acrList.size} 条 · 讯飞交叉…"
                            } else {
                                "① 讯飞哼唱识别…"
                            },
                        )
                    }
                    val xfOut = hummingRecognizer.recognize(
                        file = file,
                        appId = cfg.appId,
                        apiKey = cfg.apiKey,
                        hint = extra,
                    )
                    xfList = xfOut.candidates
                    xfErr = xfOut.error
                }

                if (!cfg.isConfigured) {
                    finishAssistant(
                        pendingId = pendingId,
                        reply = "哼唱识别需要先配置讯飞 AppID/API Key，或 ACRCloud Host/Access Key/Secret。",
                        candidates = emptyList(),
                        tracks = emptyList(),
                        status = "未配置哼唱引擎",
                    )
                    return@launch
                }

                val candidates = mergeHummingEngines(acrList, xfList, hint = extra)
                    .filterNot {
                        isWebNoiseTitle(it.title) ||
                            SongGuessParser.isPlaceholderValue(it.title) ||
                            SongNameNormalizer.isJunkFragment(it.title)
                    }
                    .take(8)

                if (candidates.isEmpty()) {
                    val tip = buildString {
                        appendLine("哼唱没有识别到可靠歌名。")
                        if (acrErr != null) appendLine("ACR：$acrErr")
                        if (xfErr != null) appendLine("讯飞：$xfErr")
                        if (acrErr == null && xfErr == null) {
                            appendLine("引擎已响应但无匹配。可再唱 8–12 秒副歌、环境安静一点，或改用文字歌词。")
                        } else if (acrList.isEmpty() && xfList.isEmpty()) {
                            appendLine("请检查「模型」页哼唱 Key 是否正确、网络是否可用。")
                        }
                    }
                    finishAssistant(
                        pendingId = pendingId,
                        reply = tip.trim(),
                        candidates = emptyList(),
                        tracks = emptyList(),
                        status = when {
                            acrErr != null || xfErr != null -> "哼唱引擎报错"
                            else -> "哼唱未命中"
                        },
                    )
                    return@launch
                }

                // 只对「够靠谱」的候选自动搜 B 站，避免拿错歌名刷一堆垃圾视频
                val trusted = candidates.filter { isTrustedHummingCandidate(it) }
                val autoSearch = when {
                    trusted.isNotEmpty() -> trusted.take(2)
                    // 都不稳：仍搜 top1 给个参考，但文案标明不确定
                    else -> candidates.take(1)
                }
                val certain = trusted.isNotEmpty() &&
                    (trusted.first().confidence ?: 0f) >= 0.55f

                updateAssistantText(pendingId, "① 哼唱识别 → ② B 站搜索…")
                var tracks = searchBilibiliForHumming(autoSearch)
                if (tracks.isEmpty() && certain) {
                    tracks = searchBilibiliRich(autoSearch, preferChinese = false)
                }

                val engines = buildList {
                    if (acrList.isNotEmpty()) add("ACR")
                    if (xfList.isNotEmpty()) add("讯飞")
                }.joinToString("+").ifBlank { "哼唱" }
                val reply = buildString {
                    if (certain) {
                        val top = trusted.first()
                        appendLine(
                            "较有把握：${listOfNotNull(top.title, top.artist).joinToString(" · ")}" +
                                (top.confidence?.let { "（${(it * 100).toInt()}%）" } ?: ""),
                        )
                        if (trusted.size > 1) {
                            appendLine("其它较稳候选见下方，点可选中再搜。")
                        }
                    } else {
                        appendLine("把握不大（哼唱识别常误判），请点下方候选确认后再听，不要只信第一条。")
                    }
                    if (acrErr != null && acrList.isEmpty()) appendLine("（ACR 侧：$acrErr）")
                    if (xfErr != null && xfList.isEmpty()) appendLine("（讯飞侧：$xfErr）")
                    if (tracks.isEmpty()) {
                        appendLine("可点候选 → 用该歌名搜 B 站；或改用文字歌词。")
                    }
                }
                finishAssistant(
                    pendingId = pendingId,
                    reply = reply,
                    candidates = candidates,
                    tracks = tracks.take(18),
                    status = when {
                        tracks.isEmpty() -> "请点候选确认歌名"
                        certain -> "$engines · ${trusted.first().title}"
                        else -> "$engines · 不确定，请点候选"
                    },
                )
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                finishAssistant(
                    pendingId = pendingId,
                    reply = "哼唱识别出错：${t.message ?: t.javaClass.simpleName}",
                    candidates = emptyList(),
                    tracks = emptyList(),
                    status = "哼唱识别出错",
                    error = t.message,
                )
            }
        }
    }

    /**
     * 用户上传本地视频：抽前几秒音轨 → 大模型识别 BGM。
     * 替代已下线的「播放中本片 BGM」悬浮球。
     */
    fun sendVideo(uri: Uri) {
        if (_ui.value.isSending) return
        val profile = _ui.value.config.active
        if (profile == null || !profile.effectiveCapabilities().audioInput) {
            _ui.update { it.copy(status = "视频识歌需要支持音频的模型，请换 MiMo / 千问 Omni 等") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(status = "从视频提取音轨…") }
            val wav = BgmAudioClipper.clipLocalUriToWav(
                context = appContext,
                uri = uri,
                startMs = 0L,
                durationMs = BgmAudioClipper.CLIP_MS,
            ).getOrElse { e ->
                _ui.update { it.copy(status = e.message ?: "视频音轨提取失败") }
                return@launch
            }
            val b64 = runCatching { MediaEncode.fileToBase64(wav) }.getOrNull()
            runCatching { wav.delete() }
            if (b64.isNullOrBlank()) {
                _ui.update { it.copy(status = "音频编码失败") }
                return@launch
            }
            if (b64.length > 5_500_000) {
                _ui.update { it.copy(status = "提取的音频太大，请换更短的视频") }
                return@launch
            }
            val extra = _ui.value.input.trim()
            val envelope = SongIdPrompt.audioEnvelope(
                extraText = buildString {
                    append("这是用户上传视频里的声音/BGM，请识别歌名。")
                    if (extra.isNotBlank()) {
                        append("\n用户补充：")
                        append(extra)
                    }
                },
            )
            beginTurn(
                userDisplay = "🎬 视频识歌" + if (extra.isNotEmpty()) " · $extra" else "",
                clueForRank = extra.ifBlank { "" },
                userEnvelope = envelope,
                multimodal = MultimodalPayload(
                    text = envelope,
                    audioBase64 = b64,
                    audioFormat = "wav",
                ),
                clearInput = true,
                isAudioOnly = extra.isBlank(),
            )
        }
    }

    /**
     * 轻量「B 站识曲」：读当前稿件/分 P 上创作者标注的官方 BGM 标签。
     * 有标签 → 转候选 → 走同一套 B 站相关度；无标签提示改用文字/哼唱。
     */
    fun recognizeCurrentBgm(track: Track, positionMs: Long = 0L) {
        if (_ui.value.isSending) return
        val bv = track.bvid.ifBlank { BilibiliApi.parseBvid(track.id).orEmpty() }
        if (bv.isBlank()) {
            _ui.update { it.copy(status = "当前没有可识别的 B 站视频") }
            return
        }
        val userMsg = AiChatMessage.User(
            id = UUID.randomUUID().toString(),
            text = "B站识曲 · ${track.title}",
        )
        val pendingId = UUID.randomUUID().toString()
        _ui.update { st ->
            st.copy(
                isSending = true,
                status = "B站识曲…",
                showGuide = false,
                messages = st.messages + userMsg + AiChatMessage.Assistant(
                    id = pendingId,
                    text = "…",
                    isStreaming = true,
                ),
            )
        }
        viewModelScope.launch {
            try {
                val tags = biliApi.recognizeBgm(bv, track.cid, positionMs)
                val candidates = tags.mapNotNull { tag ->
                    val title = tag.title.ifBlank { tag.tagName }.trim()
                    val artist = tag.artist?.takeIf { it.isNotBlank() }
                    if (title.isBlank() ||
                        SongGuessParser.isGarbageTitle(title) ||
                        SongNameNormalizer.isJunkFragment(title)
                    ) {
                        return@mapNotNull null
                    }
                    SongCandidate(
                        title = title,
                        artist = artist,
                        confidence = 0.9f,
                        bilibiliQuery = listOfNotNull(title, artist)
                            .joinToString(" ")
                            .ifBlank { title },
                        note = "B站官方BGM标签",
                    )
                }.distinctBy { it.title.lowercase().trim() }
                if (candidates.isEmpty()) {
                    finishAssistant(
                        pendingId = pendingId,
                        reply = "B站未识别到曲目，可改用文字/哼唱。",
                        candidates = emptyList(),
                        tracks = emptyList(),
                        status = "B站未识别到曲目",
                    )
                    return@launch
                }
                val tracks = searchBilibiliForCandidates(candidates, preferChinese = false)
                val top = candidates.first()
                val reply = buildString {
                    appendLine("B站官方识曲：${listOfNotNull(top.title, top.artist).joinToString(" · ")}")
                    if (candidates.size > 1) {
                        appendLine("其他标签：${candidates.drop(1).take(3).joinToString("、") { it.title }}")
                    }
                    if (tracks.isEmpty()) appendLine("该歌名在 B 站暂未找到可播视频。")
                }
                finishAssistant(
                    pendingId = pendingId,
                    reply = reply,
                    candidates = candidates.take(5),
                    tracks = tracks.take(24),
                    status = if (tracks.isEmpty()) "识别到歌名但 B 站没结果" else "B站识曲 · ${tracks.size} 条",
                )
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                finishAssistant(
                    pendingId = pendingId,
                    reply = "B站识曲失败：${t.message ?: t.javaClass.simpleName}\n可改用文字/哼唱。",
                    candidates = emptyList(),
                    tracks = emptyList(),
                    status = "B站识曲失败",
                    error = t.message,
                )
            }
        }
    }

    fun sendImage(uri: Uri) {
        if (_ui.value.isSending) return
        val profile = _ui.value.config.active
        if (profile == null || !profile.effectiveCapabilities().vision) {
            _ui.update { it.copy(status = "当前模型不支持图片") }
            return
        }
        viewModelScope.launch {
            val encoded = MediaEncode.imageUriToJpegBase64(appContext, uri)
            if (encoded == null) {
                _ui.update { it.copy(status = "图片读取失败或太大") }
                return@launch
            }
            val (b64, mime) = encoded
            val extra = _ui.value.input.trim()
            beginTurn(
                userDisplay = "🖼 图片" + if (extra.isNotEmpty()) " · $extra" else "",
                clueForRank = extra.ifBlank { "图片识歌" },
                userEnvelope = SongIdPrompt.imageEnvelope(extra.ifBlank { null }),
                multimodal = MultimodalPayload(
                    text = SongIdPrompt.imageEnvelope(extra.ifBlank { null }),
                    imageBase64 = b64,
                    imageMime = mime,
                ),
                clearInput = true,
            )
        }
    }

    private fun beginTurn(
        userDisplay: String,
        clueForRank: String,
        userEnvelope: String,
        multimodal: MultimodalPayload?,
        clearInput: Boolean = true,
        isAudioOnly: Boolean = false,
    ) {
        val profile = _ui.value.config.active
        if (profile == null) {
            _ui.update { it.copy(status = "请先配置并选择一个模型") }
            return
        }
        val apiKey = configStore.getApiKey(profile.id)
        if (apiKey.isNullOrBlank()) {
            _ui.update { it.copy(status = "当前模型未保存 API Key") }
            return
        }
        if (_ui.value.isSending) return

        val userMsg = AiChatMessage.User(id = UUID.randomUUID().toString(), text = userDisplay)
        val pendingId = UUID.randomUUID().toString()
        _ui.update { st ->
            st.copy(
                input = if (clearInput) "" else st.input,
                isSending = true,
                status = if (multimodal?.hasAudio == true) "① 模型听辨音频…" else "① 模型猜歌…",
                showGuide = false,
                isRecording = false,
                messages = st.messages + userMsg + AiChatMessage.Assistant(
                    id = pendingId,
                    text = "…",
                    isStreaming = true,
                ),
            )
        }
        viewModelScope.launch {
            try {
                runSongPipeline(
                    pendingId = pendingId,
                    profile = profile,
                    apiKey = apiKey,
                    clueForRank = clueForRank,
                    userEnvelope = userEnvelope,
                    multimodal = multimodal,
                    isAudioOnly = isAudioOnly,
                )
            } catch (t: Throwable) {
                finishAssistant(
                    pendingId = pendingId,
                    reply = "搜索过程出错：${t.message ?: t.javaClass.simpleName}\n请再试一次，或改用文字歌词。",
                    candidates = emptyList(),
                    tracks = emptyList(),
                    status = "出错了",
                    error = t.message,
                )
            }
        }
    }

    private suspend fun runSongPipeline(
        pendingId: String,
        profile: LlmProfile,
        apiKey: String,
        clueForRank: String,
        userEnvelope: String,
        multimodal: MultimodalPayload?,
        isAudioOnly: Boolean = false,
    ) {
        val isAudioEarly = multimodal?.hasAudio == true || isAudioOnly
        val pureCn = HomophoneLocalHints.isPureChineseClue(clueForRank) && !isAudioEarly
        // 文字纯中文走华语纠偏；上传音频由模型按听到的语言自动判断
        var globalMode = when {
            isAudioEarly -> false
            pureCn -> false
            else -> true
        }
        var detectedLanguage: SongLangKind? = null
        var rankClue = when {
            clueForRank.isNotBlank() -> clueForRank
            globalMode -> "foreign song melody english japanese korean"
            else -> ""
        }

        // ── ① 模型猜歌 ──
        _ui.update {
            it.copy(
                status = when {
                    multimodal?.hasAudio == true -> "① 模型听辨音频…"
                    else -> "① 模型猜歌…"
                },
            )
        }
        updateAssistantText(
            pendingId,
            when {
                multimodal?.hasAudio == true -> "① 模型听辨音频/视频音轨（wav）…"
                multimodal?.hasImage == true -> "① 模型看图识歌…"
                else -> "① 模型根据歌词/谐音猜歌名（支持多语言）…"
            },
        )

        var llmRaw = ""
        var llmReasoning = ""
        var llmGuess = SongGuessResult(reply = "", candidates = emptyList(), fromJson = false)
        // 音频也尽量强制 JSON，减少「歌名」占位回显
        val chat = client.chat(
            profile = profile,
            apiKey = apiKey,
            system = SongIdPrompt.SYSTEM,
            userText = userEnvelope,
            forceJson = true,
            multimodal = multimodal?.copy(text = userEnvelope),
        )
        if (chat.isSuccess) {
            val firstResult = chat.getOrThrow()
            llmRaw = firstResult.text
            llmReasoning = firstResult.reasoning.orEmpty()
            val parsedGuess = SongGuessParser.parse(llmRaw, userClue = clueForRank)
            detectedLanguage = SongLanguage.detectLanguage(
                parsedGuess.lyricsHeard,
                parsedGuess.candidates,
            )
            if (isAudioEarly) {
                when (detectedLanguage) {
                    SongLangKind.CHINESE -> globalMode = false
                    SongLangKind.JAPANESE, SongLangKind.KOREAN, SongLangKind.LATIN -> globalMode = true
                    else -> {}
                }
            }
            rankClue = when {
                clueForRank.isNotBlank() -> clueForRank
                detectedLanguage == SongLangKind.JAPANESE -> "japanese melody"
                detectedLanguage == SongLangKind.KOREAN -> "korean melody"
                detectedLanguage == SongLangKind.LATIN -> "english melody"
                globalMode -> "foreign song melody english japanese korean"
                else -> ""
            }
            llmGuess = cleanGuess(
                parsedGuess,
                pureCn,
                isAudio = isAudioEarly,
                globalMode = globalMode,
            )
            // 模型 JSON 烂掉 / 只吐 Something·Possible 时，从原文挖中文歌名
            llmGuess = enrichFromRawText(llmGuess, llmRaw, isAudio = isAudioEarly, globalMode = globalMode)
            // 哼唱：中文/未知时用转写歌词 + 幻觉映射救一次；外语已切全球模式不再硬锁华语
            if (isAudioEarly &&
                detectedLanguage != SongLangKind.JAPANESE &&
                detectedLanguage != SongLangKind.KOREAN &&
                detectedLanguage != SongLangKind.LATIN
            ) {
                llmGuess = recoverAudioGuess(llmGuess, llmRaw)
            }
            // 哼唱语言和候选语言明显对不上：清空候选，触发再听一遍/修复，而不是直接空结果
            if (isAudioEarly && detectedLanguage != null &&
                !hasLanguageCompatibleCandidates(detectedLanguage, llmGuess.candidates)
            ) {
                llmGuess = llmGuess.copy(candidates = emptyList())
            }
            // 文本 repair（无音频）
            if (llmGuess.candidates.isEmpty() && llmRaw.isNotBlank() && !isAudioEarly) {
                val repair = client.chat(
                    profile = profile,
                    apiKey = apiKey,
                    system = SongIdPrompt.REPAIR_SYSTEM,
                    userText = SongIdPrompt.repairEnvelope(clueForRank.ifBlank { "哼唱" }, llmRaw),
                    forceJson = true,
                )
                if (repair.isSuccess) {
                    val repairResult = repair.getOrThrow()
                    llmRaw = repairResult.text
                    llmReasoning = repairResult.reasoning.orEmpty()
                    llmGuess = cleanGuess(
                        SongGuessParser.parse(llmRaw, userClue = clueForRank),
                        pureCn,
                        isAudio = false,
                        globalMode = globalMode,
                    )
                    llmGuess = enrichFromRawText(
                        llmGuess,
                        llmRaw,
                        isAudio = false,
                        globalMode = globalMode,
                    )
                }
            }
            // 哼唱仍空：再听一遍（带音频二次请求）
            if (isAudioEarly && llmGuess.candidates.isEmpty() && multimodal != null) {
                updateAssistantText(pendingId, "① 第一次没听清有效歌名，再听一遍并转写歌词…")
                val retryEnv = SongIdPrompt.audioRetryEnvelope(
                    extraText = clueForRank.takeIf { it.isNotBlank() },
                    previousBad = llmRaw,
                )
                val retry = client.chat(
                    profile = profile,
                    apiKey = apiKey,
                    system = SongIdPrompt.AUDIO_RETRY_SYSTEM,
                    userText = retryEnv,
                    forceJson = false,
                    multimodal = multimodal.copy(text = retryEnv),
                )
                if (retry.isSuccess) {
                    val retryResult = retry.getOrThrow()
                    llmRaw = retryResult.text
                    llmReasoning = retryResult.reasoning.orEmpty()
                    val retryParsed = SongGuessParser.parse(llmRaw, userClue = clueForRank)
                    val retryLang = SongLanguage.detectLanguage(
                        retryParsed.lyricsHeard,
                        retryParsed.candidates,
                    )
                    if (isAudioEarly) {
                        when (retryLang) {
                            SongLangKind.CHINESE -> globalMode = false
                            SongLangKind.JAPANESE, SongLangKind.KOREAN, SongLangKind.LATIN -> globalMode = true
                            else -> {}
                        }
                    }
                    if (detectedLanguage == null) detectedLanguage = retryLang
                    rankClue = when {
                        clueForRank.isNotBlank() -> clueForRank
                        detectedLanguage == SongLangKind.JAPANESE -> "japanese melody"
                        detectedLanguage == SongLangKind.KOREAN -> "korean melody"
                        detectedLanguage == SongLangKind.LATIN -> "english melody"
                        globalMode -> "foreign song melody english japanese korean"
                        else -> ""
                    }
                    llmGuess = cleanGuess(
                        retryParsed,
                        pureCn,
                        isAudio = true,
                        globalMode = globalMode,
                    )
                    llmGuess = enrichFromRawText(
                        llmGuess,
                        llmRaw,
                        isAudio = true,
                        globalMode = globalMode,
                    )
                    if (detectedLanguage != SongLangKind.JAPANESE &&
                        detectedLanguage != SongLangKind.KOREAN &&
                        detectedLanguage != SongLangKind.LATIN
                    ) {
                        llmGuess = recoverAudioGuess(llmGuess, llmRaw)
                    }
                    if (detectedLanguage != null &&
                        !hasLanguageCompatibleCandidates(detectedLanguage, llmGuess.candidates)
                    ) {
                        llmGuess = llmGuess.copy(candidates = emptyList())
                    }
                }
                // 文本 repair 兜底（把二次听辨原文收成中文歌名）
                if (llmGuess.candidates.isEmpty() && llmRaw.isNotBlank()) {
                    val repair = client.chat(
                        profile = profile,
                        apiKey = apiKey,
                        system = SongIdPrompt.REPAIR_SYSTEM,
                        userText = SongIdPrompt.repairEnvelope("（用户哼唱/录音）", llmRaw),
                        forceJson = true,
                    )
                    if (repair.isSuccess) {
                        val repairResult = repair.getOrThrow()
                        val repaired = repairResult.text
                        llmReasoning = repairResult.reasoning.orEmpty()
                        val repairedParsed = SongGuessParser.parse(repaired, userClue = clueForRank)
                        val repairedLang = SongLanguage.detectLanguage(
                            repairedParsed.lyricsHeard,
                            repairedParsed.candidates,
                        )
                        if (isAudioEarly) {
                            when (repairedLang) {
                                SongLangKind.CHINESE -> globalMode = false
                                SongLangKind.JAPANESE, SongLangKind.KOREAN, SongLangKind.LATIN -> globalMode = true
                                else -> {}
                            }
                        }
                        if (detectedLanguage == null) detectedLanguage = repairedLang
                        rankClue = when {
                            clueForRank.isNotBlank() -> clueForRank
                            detectedLanguage == SongLangKind.JAPANESE -> "japanese melody"
                            detectedLanguage == SongLangKind.KOREAN -> "korean melody"
                            detectedLanguage == SongLangKind.LATIN -> "english melody"
                            globalMode -> "foreign song melody english japanese korean"
                            else -> ""
                        }
                        llmGuess = cleanGuess(
                            repairedParsed,
                            pureCn,
                            isAudio = true,
                            globalMode = globalMode,
                        )
                        llmGuess = enrichFromRawText(
                            llmGuess,
                            repaired + "\n" + llmRaw,
                            isAudio = true,
                            globalMode = globalMode,
                        )
                        if (detectedLanguage != SongLangKind.JAPANESE &&
                            detectedLanguage != SongLangKind.KOREAN &&
                            detectedLanguage != SongLangKind.LATIN
                        ) {
                            llmGuess = recoverAudioGuess(llmGuess, repaired + "\n" + llmRaw)
                        }
                        if (detectedLanguage != null &&
                            !hasLanguageCompatibleCandidates(detectedLanguage, llmGuess.candidates)
                        ) {
                            llmGuess = llmGuess.copy(candidates = emptyList())
                        }
                    }
                }
            }
        } else if (multimodal?.hasAudio == true) {
            // 音频 400 时不要拿「哼唱」去全网乱搜
            val err = chat.exceptionOrNull()?.message.orEmpty()
            finishAssistant(
                pendingId = pendingId,
                reply = "听音频失败：$err\n\n请确认：\n" +
                    "· 模型选 MiMo 普通/Pro 或千问 Omni\n" +
                    "· 已重新保存配置（Pro 会用 v2.5 听音频）\n" +
                    "· 录音至少 1 秒，环境别太吵\n" +
                    "也可改用文字歌词/谐音发送。",
                candidates = emptyList(),
                tracks = emptyList(),
                status = "音频识别失败",
                error = err,
            )
            return
        }

        // 听歌/哼唱：以模型识别为主，不把本地金曲表塞进前列（避免永远那几首）
        val isAudio = multimodal?.hasAudio == true || isAudioOnly

        val lyricFallback = when {
            clueForRank.isNotBlank() -> SongGuessParser.chineseClueCandidates(clueForRank)
            isAudio -> {
                val heard = llmGuess.lyricsHeard
                    .ifBlank { SongGuessParser.extractLyricsHeard(llmRaw) }
                SongGuessParser.lyricClueCandidates(heard)
            }
            else -> emptyList()
        }

        // 模型若仍无有效歌名：给可读失败原因（含模型片段），别只说「歌名」
        if (isAudio && llmGuess.candidates.isEmpty() && chat.isSuccess) {
            if (lyricFallback.isNotEmpty()) {
                llmGuess = llmGuess.copy(
                    reply = llmGuess.reply.ifBlank { "按转写歌词继续搜" },
                    candidates = lyricFallback,
                )
            } else {
                val snippet = llmRaw.replace(Regex("""\s+"""), " ").take(180)
                finishAssistant(
                    pendingId = pendingId,
                    reply = "听了两遍仍没有可用的真实歌名。\n" +
                        (if (snippet.isNotBlank()) "模型原话片段：$snippet\n\n" else "\n") +
                        "可以试：\n" +
                        "· 再录一遍，大声唱完整一句歌词（约 5～10 秒）\n" +
                        "· 或直接用文字发歌词（中/英/日/韩均可）\n" +
                        "· 配置里换「MiMo 普通 · v2.5」或「千问 Omni」",
                    candidates = emptyList(),
                    tracks = emptyList(),
                    status = "未识别出真实歌名",
                    thinking = llmReasoning.ifBlank { null },
                    lyricsHeard = llmGuess.lyricsHeard
                        .ifBlank { SongGuessParser.extractLyricsHeard(llmRaw) }
                        .takeIf { it.isNotBlank() },
                    modelRaw = llmRaw.takeIf { it.isNotBlank() }?.take(3000),
                )
                return
            }
        }

        // ── ② 全网检索：只用「真实歌名」当种子 ──
        _ui.update { it.copy(status = "② 全网检索…") }
        updateAssistantText(pendingId, "② 带着猜到的歌名去全网检索确认…")

        // 金曲/谐音表：仅华语文字线索辅助；全球语言与哼唱不用，防锁死几首中文歌
        val famous = if (!isAudio && !globalMode && clueForRank.isNotBlank()) {
            ChineseFamousLyrics.suggest(clueForRank)
        } else {
            emptyList()
        }
        val local = if (!isAudio && !globalMode && clueForRank.isNotBlank()) {
            HomophoneLocalHints.suggest(clueForRank)
        } else {
            emptyList()
        }

        val llmClean = llmGuess.candidates
            .filterNot {
                isWebNoiseTitle(it.title) ||
                    SongGuessParser.isPlaceholderValue(it.title) ||
                    SongNameNormalizer.isJunkFragment(it.title)
            }
            .let {
                // 哼唱华语路径：种子只用中文，避免 Something 污染全网
                when {
                    isAudio && !globalMode ->
                        SongNameNormalizer.preferChineseWhenPresent(it, strictAudio = true)
                    globalMode ->
                        SongNameNormalizer.preferChineseWhenPresent(
                            it,
                            strictAudio = false,
                            globalMode = true,
                        )
                    else -> it
                }
            }
        val seedForWeb = (llmClean + famous)
            .filter {
                SongLanguage.isPlausibleTitleLength(it.title) &&
                    !SongGuessParser.isGarbageTitle(it.title) &&
                    !SongGuessParser.isPlaceholderValue(it.title) &&
                    !SongNameNormalizer.isJunkFragment(it.title) &&
                    !isWebNoiseTitle(it.title) &&
                    // 哼唱华语：种子须含中文；全球模式允许外语
                    (!isAudio || globalMode || SongGuessParser.hasCjk(it.title))
            }
            .distinctBy { it.title.lowercase() }
            .take(5)

        val webHits = if (seedForWeb.isEmpty() && (isAudio || clueForRank.isBlank())) {
            emptyList()
        } else {
            runCatching {
                WebSongSearch.searchForSongClue(
                    userClue = if (isAudio) "" else clueForRank,
                    llmTitles = seedForWeb,
                )
            }.getOrDefault(emptyList())
        }
        val webCands = WebSongSearch.candidatesFromHits(webHits, userClue = clueForRank)
            .filterNot {
                isWebNoiseTitle(it.title) || SongGuessParser.isPlaceholderValue(it.title)
            }

        var refined = SongGuessResult(reply = "", candidates = emptyList())
        if (webHits.isNotEmpty() && chat.isSuccess && seedForWeb.isNotEmpty()) {
            val refineChat = client.chat(
                profile = profile,
                apiKey = apiKey,
                system = SongIdPrompt.REFINE_SYSTEM,
                userText = SongIdPrompt.refineEnvelope(
                    userText = clueForRank.ifBlank { "（用户哼唱/录音）" },
                    llmJsonOrText = llmRaw.ifBlank { llmGuess.reply },
                    webSnippets = WebSongSearch.formatSnippets(webHits),
                ),
                forceJson = true,
            )
            if (refineChat.isSuccess) {
                refined = cleanGuess(
                    SongGuessParser.parse(refineChat.getOrThrow().text, userClue = clueForRank),
                    pureCn,
                    isAudio = isAudio,
                    globalMode = globalMode,
                )
            }
        }

        // 合并优先级：
        // 哼唱 → 模型/二次确认/全网（金曲表不参与）
        // 文字 → 模型优先，金曲仅辅助
        var cands = if (isAudio) {
            mergeSongCandidates(
                refined.candidates,
                llmClean,
                webCands,
            )
        } else {
            mergeSongCandidates(
                refined.candidates,
                llmClean.let {
                    if (pureCn) SongGuessParser.keepChineseOriented(it) else it
                },
                webCands,
                famous,
                local,
                lyricFallback,
            )
        }.filterNot {
            isWebNoiseTitle(it.title) ||
                SongGuessParser.isPlaceholderValue(it.title) ||
                SongNameNormalizer.isJunkFragment(it.title)
        }

        // 拼音/混杂 → 中文；全球模式保留外语；有中文时丢掉 Something 等垃圾
        if (globalMode) {
            cands = cands.filterNot {
                SongNameNormalizer.isJunkFragment(it.title) ||
                    SongGuessParser.isGarbageTitle(it.title)
            }
            cands = SongNameNormalizer.preferChineseWhenPresent(
                cands,
                strictAudio = false,
                globalMode = true,
            )
        } else {
            cands = SongNameNormalizer.normalizeAll(cands)
            cands = SongNameNormalizer.preferChineseWhenPresent(cands, strictAudio = isAudio)
            // 再从模型原文补挖一次中文（防 JSON 只有英文碎片）
            if (llmRaw.isNotBlank()) {
                val dug = SongNameNormalizer.extractChineseFromText(llmRaw)
                if (dug.isNotEmpty()) {
                    cands = SongNameNormalizer.normalizeAll(dug + cands)
                    cands = SongNameNormalizer.preferChineseWhenPresent(cands, strictAudio = isAudio)
                }
            }
        }
        // 哼唱华语最终兜底：仍无中文 → 再扫一遍原文
        if (isAudio && !globalMode) {
            val hasZh = cands.any { SongGuessParser.hasCjk(it.title) && !SongNameNormalizer.isJunkFragment(it.title) }
            if (!hasZh && llmRaw.isNotBlank()) {
                val dug = SongNameNormalizer.extractChineseFromText(llmRaw)
                cands = SongNameNormalizer.normalizeAll(dug)
                    .let { SongNameNormalizer.preferChineseWhenPresent(it, strictAudio = true) }
            }
            cands = cands.filter {
                SongGuessParser.hasCjk(it.title) &&
                    !SongNameNormalizer.isJunkFragment(it.title) &&
                    !SongGuessParser.isGarbageTitle(it.title)
            }
        }

        if (pureCn && !isAudio) {
            cands = SongGuessParser.keepChineseOriented(cands).ifEmpty { cands }
        }
        cands = SongRanker.rankCandidates(
            rankClue.ifBlank { cands.firstOrNull()?.title.orEmpty() },
            cands,
            forceLanguage = detectedLanguage,
            preferForeign = globalMode,
        )
        // 排序后再滤 junk（rank 可能改 conf 但不该带回脏标题）
        cands = cands.filterNot { SongNameNormalizer.isJunkFragment(it.title) }

        val reply = buildPipelineReply(
            userText = clueForRank,
            ranked = cands,
            llm = llmGuess,
            refined = refined,
            webCands = webCands,
            famous = famous,
            webHitCount = webHits.size,
            llmFailed = chat.isFailure,
            llmError = chat.exceptionOrNull()?.message,
        )
        val processLyrics = llmGuess.lyricsHeard
            .ifBlank { SongGuessParser.extractLyricsHeard(llmRaw) }
            .takeIf { it.isNotBlank() }
        val processRaw = llmRaw.takeIf { it.isNotBlank() }?.take(3000)

        // ── ③ B 站 ──
        _ui.update { it.copy(status = "③ B 站搜索…") }
        updateAssistantText(pendingId, "③ 用确认后的歌名在 B 站搜索…\n\n$reply")

        if (cands.isEmpty()) {
            finishAssistant(
                pendingId = pendingId,
                reply = reply + "\n\n没有可用的真实歌名，请再唱一句或改用文字（支持多语言）。",
                candidates = emptyList(),
                tracks = emptyList(),
                status = "未识别出真实歌名",
                thinking = llmReasoning.ifBlank { null },
                lyricsHeard = processLyrics,
                modelRaw = processRaw,
            )
            return
        }

        // 全球模式不对 B 站强制中文检索
        val preferCnSearch = (pureCn || (isAudio && !globalMode)) && !globalMode
        var tracks = searchBilibiliRich(cands, preferChinese = preferCnSearch)
        if (tracks.isEmpty() && lyricFallback.isNotEmpty()) {
            tracks = searchBilibiliRich(lyricFallback, preferChinese = true)
            if (tracks.isNotEmpty()) {
                cands = SongRanker.rankCandidates(
                    rankClue.ifBlank { clueForRank },
                    cands + lyricFallback,
                    forceLanguage = detectedLanguage,
                    preferForeign = globalMode,
                )
            }
        }
        tracks = SongRanker.rankTracks(tracks, cands)

        finishAssistant(
            pendingId = pendingId,
            reply = reply,
            candidates = cands.take(6),
            tracks = tracks.take(24),
            status = when {
                tracks.isEmpty() -> "有歌名但 B 站没结果"
                else -> "①猜歌 → ②全网 → ③B站 · ${tracks.size} 条 · ${cands.firstOrNull()?.title ?: ""}"
            },
            error = if (chat.isFailure) chat.exceptionOrNull()?.message else null,
            thinking = llmReasoning.ifBlank { null },
            lyricsHeard = processLyrics,
            modelRaw = processRaw,
        )
    }

    /** 过滤「识歌网站/工具页/平台名」噪声 */
    private fun isWebNoiseTitle(title: String): Boolean {
        if (WebSongSearch.isPlatformOrNoise(title)) return true
        val t = title.lowercase()
        val bad = listOf(
            "歌曲识别", "哼唱识歌", "在线识别", "识别歌曲", "听歌识曲",
            "cp.baidu", "baidu.com", "小羿", "shazam", "acrcloud",
            "是什么歌网", "猜歌软件", "util", "工具",
            "bilibili", "b站", "哔哩", "new year",
        )
        return bad.any { t.contains(it) }
    }

    override fun onCleared() {
        cancelRecording()
        super.onCleared()
    }

    private fun updateAssistantText(pendingId: String, text: String) {
        _ui.update { st ->
            st.copy(
                messages = st.messages.map { m ->
                    if (m is AiChatMessage.Assistant && m.id == pendingId) {
                        m.copy(text = text, isStreaming = true)
                    } else {
                        m
                    }
                },
            )
        }
    }

    private fun mergeSongCandidates(vararg lists: List<SongCandidate>): List<SongCandidate> {
        val seen = LinkedHashSet<String>()
        val out = ArrayList<SongCandidate>()
        for (list in lists) {
            for (c in list) {
                if (SongGuessParser.isGarbageTitle(c.title)) continue
                val key = c.title.lowercase().trim()
                if (key.isEmpty() || !seen.add(key)) continue
                out.add(c)
            }
        }
        return out.take(10)
    }

    private fun buildPipelineReply(
        userText: String,
        ranked: List<SongCandidate>,
        llm: SongGuessResult,
        refined: SongGuessResult,
        webCands: List<SongCandidate>,
        famous: List<SongCandidate>,
        webHitCount: Int,
        llmFailed: Boolean,
        llmError: String?,
    ): String = buildString {
        appendLine("① 模型猜歌 → ② 全网确认 → ③ B 站试听")
        if (llmFailed) {
            appendLine("（模型异常：${llmError ?: "未知"}，已用全网/本地）")
        }
        val top = ranked.firstOrNull()
        if (top != null) {
            val who = listOfNotNull(top.title, top.artist).joinToString(" · ")
            appendLine("最可能：$who")
            if (ranked.size > 1) {
                val others = ranked.drop(1).take(3).joinToString("、") { it.title }
                appendLine("其他候选：$others")
            }
        }
        if (webHitCount > 0) appendLine("全网摘要 ${webHitCount} 条已参与校正。")
        val r = refined.reply.ifBlank { llm.reply }.trim()
        if (r.isNotBlank() && r.length < 160 && !r.contains("Barney", true) &&
            !r.contains("已按三步")
        ) {
            appendLine()
            append(r)
        }
    }

    /**
     * 对 Top 候选做 **少量** B 站检索（带超时），避免真机卡死在 ③。
     * 以前 3 歌 × 7 路串行搜索很容易一直转圈。
     */
    private suspend fun searchBilibiliRich(
        candidates: List<SongCandidate>,
        preferChinese: Boolean,
    ): List<Track> {
        if (candidates.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(22_000L) {
                val source = registry.get(MusicSourceType.BILIBILI) ?: return@withTimeoutOrNull emptyList()
                val seen = LinkedHashSet<String>()
                val out = ArrayList<Track>()
                // 哼唱：前 3 个候选都落地搜，降低「引擎对了歌、搜错结果」
                val top = candidates
                    .filterNot {
                        isWebNoiseTitle(it.title) ||
                            SongGuessParser.isPlaceholderValue(it.title) ||
                            SongNameNormalizer.isJunkFragment(it.title)
                    }
                    .take(3)
                for (c in top) {
                    if (SongGuessParser.isGarbageTitle(c.title)) continue
                    if (preferChinese && !SongGuessParser.hasCjk(c.title) &&
                        !SongGuessParser.hasCjk(c.bilibiliQuery)
                    ) {
                        continue
                    }
                    if (isWebNoiseTitle(c.title)) continue
                    // 通用：短名不裸搜、歌名+歌手优先、降权脏结果
                    val queries = SongRanker.buildSearchQueries(c, max = 4)
                    for (q in queries) {
                        if (isWebNoiseTitle(q)) continue
                        val list = withTimeoutOrNull(10_000L) {
                            val music = runCatching {
                                biliApi.searchMusic(q, limit = 12)
                            }.getOrDefault(emptyList())
                            if (music.size >= 4) {
                                music
                            } else {
                                val generic = runCatching {
                                    source.search(q, limit = 12)
                                }.getOrDefault(emptyList())
                                (music + generic).distinctBy { it.id }.take(16)
                            }
                        }.orEmpty()
                        for (t in list) {
                            if (SongRanker.isHardGarbageTitle(t.title)) continue
                            if (SongRanker.bestTrackScore(t, top) < 12) continue
                            if (seen.add(t.id)) {
                                out.add(t)
                                if (out.size >= 28) {
                                    return@withTimeoutOrNull SongRanker.rankTracks(out, top, minScore = 18)
                                }
                            }
                        }
                    }
                }
                val strict = SongRanker.rankTracks(out, top, minScore = 18)
                if (strict.isNotEmpty()) strict
                else SongRanker.rankTracks(out, top, minScore = 12)
            }.orEmpty()
        }
    }

    /**
     * 模型吐英文碎片时，用原文/曲库挖中文歌名补上（歌声与微笑等）。
     * 若已有干净中文候选则合并置顶。
     */
    private fun enrichFromRawText(
        guess: SongGuessResult,
        raw: String,
        isAudio: Boolean,
        globalMode: Boolean = false,
    ): SongGuessResult {
        if (raw.isBlank()) return guess
        if (globalMode) {
            // 全球：不硬挖中文；只清 junk
            val cleaned = guess.candidates.filterNot {
                SongNameNormalizer.isJunkFragment(it.title) ||
                    SongGuessParser.isGarbageTitle(it.title)
            }.let {
                SongNameNormalizer.preferChineseWhenPresent(
                    it,
                    strictAudio = false,
                    globalMode = true,
                )
            }
            return guess.copy(candidates = cleaned.ifEmpty { guess.candidates })
        }
        val dug = SongNameNormalizer.extractChineseFromText(raw)
        if (dug.isEmpty()) {
            // 哼唱且全是英文 junk：宁可空，也不展示 Something（后面 recover / 再听）
            if (isAudio) {
                val cleaned = SongNameNormalizer.normalizeAll(guess.candidates)
                    .let { SongNameNormalizer.preferChineseWhenPresent(it, strictAudio = true) }
                return guess.copy(candidates = cleaned)
            }
            return guess
        }
        val merged = SongNameNormalizer.normalizeAll(dug + guess.candidates)
            .let { SongNameNormalizer.preferChineseWhenPresent(it, strictAudio = isAudio) }
        return guess.copy(
            reply = guess.reply.ifBlank {
                if (merged.any { it.title == "歌声与微笑" }) "最可能是《歌声与微笑》（谷建芬）。"
                else "已从模型回复中提取中文歌名。"
            },
            candidates = merged,
            fromJson = guess.fromJson && guess.candidates.isNotEmpty(),
        )
    }

    /**
     * 哼唱兜底：lyrics_heard → 金曲匹配；英文幻觉组合 → 歌声与微笑等。
     */
    private fun recoverAudioGuess(
        guess: SongGuessResult,
        raw: String,
    ): SongGuessResult {
        if (guess.candidates.isNotEmpty()) {
            // 已有候选仍可补 lyrics 匹配的高置信
            val lyrics = guess.lyricsHeard.ifBlank { SongGuessParser.extractLyricsHeard(raw) }
            if (lyrics.length >= 4) {
                val fromLy = ChineseFamousLyrics.suggest(lyrics)
                if (fromLy.isNotEmpty()) {
                    val merged = SongNameNormalizer.normalizeAll(fromLy + guess.candidates)
                        .let { SongNameNormalizer.preferChineseWhenPresent(it, strictAudio = true) }
                    return guess.copy(candidates = merged, lyricsHeard = lyrics)
                }
            }
            return guess
        }
        val lyrics = guess.lyricsHeard.ifBlank { SongGuessParser.extractLyricsHeard(raw) }
        val fromLyrics = if (lyrics.length >= 4) {
            ChineseFamousLyrics.suggest(lyrics) +
                SongGuessParser.chineseClueCandidates(lyrics)
        } else {
            emptyList()
        }
        val hallu = SongNameNormalizer.recoverAudioHallucinations(raw)
        val dug = SongNameNormalizer.extractChineseFromText(raw)
        val merged = SongNameNormalizer.normalizeAll(fromLyrics + hallu + dug)
            .let { SongNameNormalizer.preferChineseWhenPresent(it, strictAudio = true) }
        if (merged.isEmpty()) return guess.copy(lyricsHeard = lyrics)
        return guess.copy(
            reply = guess.reply.ifBlank {
                when {
                    merged.any { it.title == "歌声与微笑" } ->
                        "根据哼唱特征，最可能是《歌声与微笑》（谷建芬）。"
                    lyrics.isNotBlank() -> "根据听到的歌词匹配到可能的歌。"
                    else -> "已整理中文歌名。"
                }
            },
            candidates = merged,
            lyricsHeard = lyrics,
            fromJson = false,
        )
    }

    /**
     * 听辨出的语言与候选歌名是否对得上。
     * 日语/韩语也允许官方英文标题（Dynamite、Idol 等），所以不按文字严格卡死。
     */
    private fun hasLanguageCompatibleCandidates(
        lang: SongLangKind,
        cands: List<SongCandidate>,
    ): Boolean {
        return cands.any { c ->
            val title = c.title.trim()
            if (title.isEmpty() ||
                SongNameNormalizer.isJunkFragment(title) ||
                SongGuessParser.isGarbageTitle(title)
            ) {
                return@any false
            }
            when (lang) {
                SongLangKind.CHINESE ->
                    SongGuessParser.hasCjk(title) &&
                        !SongLanguage.hasJapaneseKana(title) &&
                        !SongLanguage.hasHangul(title)
                SongLangKind.JAPANESE -> {
                    val k = SongLanguage.kindOf(title)
                    k == SongLangKind.JAPANESE ||
                        k == SongLangKind.LATIN ||
                        k == SongLangKind.MIXED ||
                        SongLanguage.hasJapaneseKana(title)
                }
                SongLangKind.KOREAN -> {
                    val k = SongLanguage.kindOf(title)
                    k == SongLangKind.KOREAN ||
                        k == SongLangKind.LATIN ||
                        k == SongLangKind.MIXED ||
                        SongLanguage.hasHangul(title)
                }
                SongLangKind.LATIN -> {
                    val k = SongLanguage.kindOf(title)
                    k == SongLangKind.LATIN || k == SongLangKind.MIXED
                }
                else -> true
            }
        }
    }

    private fun cleanGuess(
        guess: SongGuessResult,
        pureCn: Boolean,
        isAudio: Boolean = false,
        globalMode: Boolean = false,
    ): SongGuessResult {
        var list = guess.candidates.mapNotNull { c ->
            // 全球模式保留原文；华语模式混写先剥中文
            val title = if (globalMode) {
                c.title.trim()
            } else {
                SongNameNormalizer.extractChineseTitle(c.title) ?: c.title.trim()
            }
            if (title.isBlank()) return@mapNotNull null
            if (!SongLanguage.isPlausibleTitleLength(title) && title.length > 48) {
                return@mapNotNull null
            }
            if (SongGuessParser.isGarbageTitle(title) ||
                SongGuessParser.isPlaceholderValue(title) ||
                SongNameNormalizer.isJunkFragment(title) ||
                isWebNoiseTitle(title)
            ) {
                return@mapNotNull null
            }
            val artist = c.artist?.takeIf {
                !SongGuessParser.isPlaceholderValue(it) && !SongNameNormalizer.isJunkFragment(it)
            }
            c.copy(
                title = title,
                artist = artist,
                bilibiliQuery = listOfNotNull(title, artist).joinToString(" ")
                    .ifBlank { title },
            )
        }
        if (globalMode) {
            list = SongNameNormalizer.preferChineseWhenPresent(
                list,
                strictAudio = false,
                globalMode = true,
            )
        } else {
            list = SongNameNormalizer.normalizeAll(list)
            list = SongNameNormalizer.preferChineseWhenPresent(list, strictAudio = isAudio)
            // 哼唱可中英；文字纯中文线索才 keepChinese
            if (pureCn && !isAudio) {
                list = SongGuessParser.keepChineseOriented(list)
            }
            if (isAudio) {
                list = list.filter {
                    SongGuessParser.hasCjk(it.title) ||
                        // 仅当完全没有中文时才允许非 junk 英文（如真实英文歌）
                        (!SongNameNormalizer.isJunkFragment(it.title) &&
                            list.none { x -> SongGuessParser.hasCjk(x.title) })
                }
            }
        }
        // 保留 lyricsHeard 供 recover
        return guess.copy(candidates = list, lyricsHeard = guess.lyricsHeard)
    }

    private fun finishAssistant(
        pendingId: String,
        reply: String,
        candidates: List<SongCandidate>,
        tracks: List<Track>,
        status: String?,
        error: String? = null,
        thinking: String? = null,
        lyricsHeard: String? = null,
        modelRaw: String? = null,
    ) {
        _ui.update { st ->
            st.copy(
                isSending = false,
                status = status,
                messages = st.messages.map { m ->
                    if (m is AiChatMessage.Assistant && m.id == pendingId) {
                        m.copy(
                            text = reply,
                            candidates = candidates,
                            tracks = tracks,
                            isStreaming = false,
                            error = error,
                            thinking = thinking,
                            lyricsHeard = lyricsHeard,
                            modelRaw = modelRaw,
                        )
                    } else {
                        m
                    }
                },
            )
        }
        persistCurrent()
    }

    private fun persistCurrent() {
        val st = _ui.value
        viewModelScope.launch {
            val sid = historyStore.saveSession(st.sessionId, st.messages)
            if (sid.isNotBlank()) {
                _ui.update {
                    it.copy(
                        sessionId = sid,
                        history = historyStore.summaries(),
                    )
                }
            }
        }
    }

    private suspend fun searchBilibiliForCandidates(
        candidates: List<SongCandidate>,
        preferChinese: Boolean = false,
    ): List<Track> {
        if (candidates.isEmpty()) return emptyList()
        val source = registry.get(MusicSourceType.BILIBILI) ?: return emptyList()
        val seen = LinkedHashSet<String>()
        val out = ArrayList<Track>()
        val top = candidates
            .filterNot {
                SongGuessParser.isGarbageTitle(it.title) ||
                    SongNameNormalizer.isJunkFragment(it.title)
            }
            .take(6)
        val queries = LinkedHashSet<String>()
        for (c in top) {
            if (preferChinese &&
                !SongGuessParser.hasCjk(c.title) &&
                !SongGuessParser.hasCjk(c.bilibiliQuery)
            ) {
                continue
            }
            SongRanker.buildSearchQueries(c, max = 4).forEach { queries.add(it) }
        }
        val ordered = if (preferChinese) {
            queries.sortedByDescending { q -> q.count { it.code in 0x4E00..0x9FFF } }
        } else {
            queries.toList()
        }
        for (q in ordered.take(12)) {
            if (q.isBlank() || SongGuessParser.isGarbageTitle(q)) continue
            val cjk = q.count { it.code in 0x4E00..0x9FFF }
            val latin = q.count { it.isLetter() && it.code < 128 }
            if (cjk >= 18 && latin < 2 && q.length > 36) continue
            if (preferChinese && cjk == 0 && latin >= 3) continue
            val list = runCatching { source.search(q, limit = 8) }.getOrDefault(emptyList())
            for (t in list) {
                if (SongRanker.isHardGarbageTitle(t.title)) continue
                if (SongRanker.bestTrackScore(t, top) < 12) continue
                if (seen.add(t.id)) {
                    out.add(t)
                    if (out.size >= 24) {
                        return SongRanker.rankTracks(out, top, minScore = 18)
                            .ifEmpty { SongRanker.rankTracks(out, top, minScore = 12) }
                    }
                }
            }
        }
        return SongRanker.rankTracks(out, top, minScore = 18)
            .ifEmpty { SongRanker.rankTracks(out, top, minScore = 12) }
    }

    /** 双引擎交叉或置信度够高才算「可自动搜 B 站」 */
    private fun isTrustedHummingCandidate(c: SongCandidate): Boolean {
        val conf = c.confidence ?: 0f
        val dual = c.note?.contains("交叉") == true
        return dual || conf >= 0.55f
    }

    /**
     * 用户点了哼唱候选：只按这首歌搜 B 站，避免默认 top1 认错。
     */
    fun searchHummingCandidate(candidate: SongCandidate) {
        if (_ui.value.isSending) return
        val title = candidate.title.trim()
        if (title.isBlank()) return
        val pendingId = UUID.randomUUID().toString()
        val userMsg = AiChatMessage.User(
            id = UUID.randomUUID().toString(),
            text = "按候选搜：${listOfNotNull(candidate.title, candidate.artist).joinToString(" · ")}",
        )
        _ui.update { st ->
            st.copy(
                isSending = true,
                status = "按候选搜 B 站…",
                messages = st.messages + userMsg + AiChatMessage.Assistant(
                    id = pendingId,
                    text = "…",
                    isStreaming = true,
                ),
            )
        }
        viewModelScope.launch {
            try {
                var tracks = searchBilibiliForHumming(listOf(candidate))
                if (tracks.isEmpty()) {
                    tracks = searchBilibiliRich(listOf(candidate), preferChinese = false)
                }
                finishAssistant(
                    pendingId = pendingId,
                    reply = if (tracks.isEmpty()) {
                        "「$title」在 B 站暂无较相关结果，可改文字搜或换候选。"
                    } else {
                        "已按「${listOfNotNull(candidate.title, candidate.artist).joinToString(" · ")}」搜 B 站："
                    },
                    candidates = listOf(candidate),
                    tracks = tracks.take(20),
                    status = if (tracks.isEmpty()) "该候选 B 站无结果" else "候选 → B站 ${tracks.size} 条",
                )
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                finishAssistant(
                    pendingId = pendingId,
                    reply = "按候选搜索失败：${t.message ?: t.javaClass.simpleName}",
                    candidates = emptyList(),
                    tracks = emptyList(),
                    status = "搜索失败",
                    error = t.message,
                )
            }
        }
    }

    /**
     * 哼唱专用 B 站落地：音乐区优先 + 全站；相关度放宽，避免「认出歌名却一条没有」。
     * 通用策略，不按歌名单曲特判。
     */
    private suspend fun searchBilibiliForHumming(candidates: List<SongCandidate>): List<Track> {
        if (candidates.isEmpty()) return emptyList()
        val top = candidates
            .filterNot {
                SongGuessParser.isGarbageTitle(it.title) ||
                    SongNameNormalizer.isJunkFragment(it.title)
            }
            .take(5)
        if (top.isEmpty()) return emptyList()
        val seen = LinkedHashSet<String>()
        val out = ArrayList<Track>()
        val queries = LinkedHashSet<String>()
        for (c in top) {
            SongRanker.buildSearchQueries(c, max = 5).forEach { queries.add(it) }
            queries.add(c.title.trim())
            val artist = c.artist?.trim().orEmpty()
            if (artist.isNotBlank()) queries.add("${c.title.trim()} $artist")
        }
        for (q in queries.take(14)) {
            if (q.isBlank() || SongGuessParser.isGarbageTitle(q)) continue
            // 音乐区
            runCatching { biliApi.searchMusic(q, limit = 8) }
                .getOrDefault(emptyList())
                .forEach { t ->
                    if (SongRanker.isHardGarbageTitle(t.title)) return@forEach
                    if (seen.add(t.id)) out.add(t)
                }
            // 全站兜底
            if (out.size < 6) {
                runCatching { biliApi.search(q, limit = 6) }
                    .getOrDefault(emptyList())
                    .forEach { t ->
                        if (SongRanker.isHardGarbageTitle(t.title)) return@forEach
                        if (seen.add(t.id)) out.add(t)
                    }
            }
            if (out.size >= 28) break
        }
        if (out.isEmpty()) return emptyList()
        // 先严后宽，保证有结果
        return SongRanker.rankTracks(out, top, minScore = 16)
            .ifEmpty { SongRanker.rankTracks(out, top, minScore = 10) }
            .ifEmpty { out.take(12) }
    }

    /**
     * 合并 ACR + 讯飞：两边都命中的歌名加分；高分 ACR 优先；hint 纠偏。
     * 通用合并，禁止单曲 if-else。
     */
    private fun mergeHummingEngines(
        acr: List<SongCandidate>,
        xunfei: List<SongCandidate>,
        hint: String,
    ): List<SongCandidate> {
        fun keyOf(c: SongCandidate): String =
            c.title.trim().lowercase()
                .filter { it.isLetterOrDigit() || it.code in 0x4E00..0x9FFF }

        val score = HashMap<String, Float>()
        val best = LinkedHashMap<String, SongCandidate>()

        fun offer(c: SongCandidate, engineBoost: Float) {
            val k = keyOf(c)
            if (k.length < 1) return
            val conf = (c.confidence ?: 0.5f) + engineBoost
            val old = score[k] ?: -1f
            val both = if (old >= 0f) 0.2f else 0f
            val s = conf + both
            if (s >= old) {
                score[k] = s
                val prev = best[k]
                best[k] = c.copy(
                    confidence = s.coerceIn(0.2f, 0.99f),
                    artist = c.artist?.takeIf { it.isNotBlank() } ?: prev?.artist,
                    bilibiliQuery = listOfNotNull(c.title, c.artist ?: prev?.artist)
                        .joinToString(" ")
                        .ifBlank { c.title },
                    note = when {
                        both > 0f -> "ACR+讯飞交叉"
                        else -> c.note
                    },
                )
            }
        }

        // 只从原始列表统计「是否双引擎都有」
        val acrKeys = acr.map { keyOf(it) }.filter { it.isNotBlank() }.toSet()
        val xfKeys = xunfei.map { keyOf(it) }.filter { it.isNotBlank() }.toSet()

        acr.forEach { offer(it, 0.04f) }
        xunfei.forEach { offer(it, 0.0f) }

        // 双引擎同名再抬一档（通用，非单曲）
        best.keys.toList().forEach { k ->
            if (k in acrKeys && k in xfKeys) {
                val c = best[k] ?: return@forEach
                val s = (score[k] ?: 0f) + 0.22f
                score[k] = s
                best[k] = c.copy(
                    confidence = s.coerceIn(0.35f, 0.99f),
                    note = "ACR+讯飞交叉",
                )
            }
        }

        var list = best.values.sortedByDescending { score[keyOf(it)] ?: 0f }
        val h = hint.trim()
        if (h.length >= 2) {
            list = list.sortedWith(
                compareByDescending<SongCandidate> { c ->
                    val t = c.title.lowercase()
                    val hh = h.lowercase()
                    when {
                        t.contains(hh) || hh.contains(t) -> 2
                        c.artist?.contains(h, true) == true -> 1
                        else -> 0
                    }
                }.thenByDescending { it.confidence ?: 0f },
            )
        }
        // 丢掉明显垫底噪声
        val topScore = list.firstOrNull()?.confidence ?: 0f
        if (topScore >= 0.55f && list.size > 2) {
            list = list.filter {
                val s = it.confidence ?: 0f
                s >= topScore * 0.45f || s >= 0.4f || it.note?.contains("交叉") == true
            }
        }
        return list.take(6)
    }

    companion object {
        private const val WAVE_BARS = 28

        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = MadusApp.instance
                return AiChatViewModel(
                    configStore = app.llmConfigStore,
                    historyStore = app.aiChatHistoryStore,
                    client = LlmClient(),
                    hummingStore = app.hummingConfigStore,
                    registry = app.sourceRegistry,
                    biliApi = app.biliApi,
                    appContext = app,
                ) as T
            }
        }
    }
}
