package com.madus.mobile.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.madus.mobile.data.NetworkIntensity
import com.madus.mobile.data.SoundFx
import com.madus.mobile.domain.PlaybackState
import com.madus.mobile.domain.PlayerCommand
import com.madus.mobile.domain.RepeatMode
import com.madus.mobile.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single ExoPlayer owner. UI talks only via [dispatch] + [state].
 * 曲终（非单曲循环）通过 [onPlaybackEnded] 交给 ViewModel 播下一首。
 * 锁屏/通知 next/prev 走 [onExternalNext]/[onExternalPrevious]。
 */
@UnstableApi
class PlayerEngine(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        )
        .setDefaultRequestProperties(
            mapOf(
                "Referer" to "https://www.bilibili.com/",
                "Origin" to "https://www.bilibili.com",
                "Accept" to "*/*",
                "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
                "Connection" to "keep-alive",
            ),
        )
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(30_000)

    /** 默认关：纯在线，不写边听缓存（轻量化） */
    private val autoCacheEnabled = AtomicBoolean(false)

    /**
     * 打游戏混音：true 时不让 ExoPlayer 自动处理音频焦点，
     * 避免游戏 UI 音效短暂抢焦点把音乐 pause。
     * false = 系统默认（来电/其它媒体可能暂停本 App）。
     */
    private val gameMixAudio = AtomicBoolean(true)

    /**
     * 游戏轻量 / 网络最省：暂停边听写盘等。
     */
    private val gameLiteMode = AtomicBoolean(false)

    /** 应用在后台时放慢进度轮询，省 CPU（功能不变）。 */
    private val backgroundMode = AtomicBoolean(false)

    /** 网络强度档位（0=最省 … 3=充足），用 ordinal 存 */
    private val networkIntensityOrdinal = java.util.concurrent.atomic.AtomicInteger(
        NetworkIntensity.BALANCED.ordinal,
    )

    private fun networkIntensity(): NetworkIntensity {
        val i = networkIntensityOrdinal.get().coerceIn(0, NetworkIntensity.entries.lastIndex)
        return NetworkIntensity.entries[i]
    }

    private val mediaAudioAttributes: AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

    private val switchingDataSourceFactory = DataSource.Factory {
        // 受 autoCache + 网络档位约束；最省档永不写边听缓存
        val allow = autoCacheEnabled.get() &&
            networkIntensity().allowAutoCacheWrite(backgroundMode.get())
        if (allow) {
            CacheDataSource.Factory()
                .setCache(StreamCache.get(appContext))
                .setUpstreamDataSourceFactory(httpFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .createDataSource()
        } else {
            httpFactory.createDataSource()
        }
    }

    private val mediaSourceFactory: MediaSource.Factory =
        DefaultMediaSourceFactory(switchingDataSourceFactory)

    private val audioFx = AudioFxController()

    /**
     * 只用官方 DefaultLoadControl（勿自封装：Media3 漏接口会启动即崩）。
     * 缓冲略小于官方默认 50s，减轻后台抢网；游戏轻量主要靠预取/写盘/续刷。
     */
    private fun buildSafeLoadControl(): DefaultLoadControl {
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs */ 12_000,
                /* maxBufferMs */ 20_000,
                /* bufferForPlaybackMs */ 1_000,
                /* bufferForPlaybackAfterRebufferMs */ 1_800,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    /** 自然播完且非单曲循环时回调（主线程）。 */
    var onPlaybackEnded: (() -> Unit)? = null

    /** 锁屏/耳机/通知栏切歌（主线程）。由 ViewModel 绑定。 */
    var onExternalNext: (() -> Unit)? = null
    var onExternalPrevious: (() -> Unit)? = null

    /**
     * 播放出错时回调（主线程）。
     * ViewModel 可重解析流或跳过；若未设置则仅写 errorMessage。
     */
    var onPlayerError: ((PlaybackException) -> Unit)? = null

    val player: ExoPlayer = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(mediaSourceFactory)
        .setLoadControl(buildSafeLoadControl())
        // 默认混音：不 handleAudioFocus，游戏点按钮不会把歌暂停
        .setAudioAttributes(mediaAudioAttributes, /* handleAudioFocus = */ false)
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()
        .also { exo ->
            exo.repeatMode = Player.REPEAT_MODE_OFF
            exo.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    publishFromPlayer()
                    if (isPlaying) startTicker() else stopTicker()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    publishFromPlayer()
                    if (playbackState == Player.STATE_ENDED) {
                        handleEnded()
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    publishFromPlayer()
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    audioFx.attach(audioSessionId)
                }

                override fun onPlayerError(error: PlaybackException) {
                    _state.update {
                        it.copy(
                            isPlaying = false,
                            isLoading = false,
                            errorMessage = "播放失败：${error.errorCodeName}",
                        )
                    }
                    // 交给 VM：清过期 CDN、重解析或跳下一首
                    mainHandler.post {
                        onPlayerError?.invoke(error)
                    }
                }
            })
            audioFx.attach(exo.audioSessionId)
        }

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var queue: List<Track> = emptyList()
    private var repeatMode: RepeatMode = RepeatMode.OFF
    private var shuffle: Boolean = false
    private var tickerJob: Job? = null
    private var ending = false
    private var sleepJob: Job? = null

    private val _sleepRemainingMs = MutableStateFlow(0L)
    val sleepRemainingMs: StateFlow<Long> = _sleepRemainingMs.asStateFlow()

    fun requestExternalNext() {
        mainHandler.post { onExternalNext?.invoke() ?: dispatch(PlayerCommand.Next) }
    }

    fun requestExternalPrevious() {
        mainHandler.post { onExternalPrevious?.invoke() ?: dispatch(PlayerCommand.Previous) }
    }

    fun setSoundFx(fx: SoundFx) {
        audioFx.setFx(fx)
    }

    /** 边听写盘缓存开关；关=仅网络在线播 */
    fun setAutoCache(enabled: Boolean) {
        autoCacheEnabled.set(enabled)
    }

    /**
     * 打游戏时继续播放。
     * true：不自动抢/交音频焦点（游戏音效不会 pause 本 App）
     * false：交给 ExoPlayer 处理焦点（经典行为）
     */
    fun setGameMixAudio(enabled: Boolean) {
        if (gameMixAudio.getAndSet(enabled) == enabled) return
        mainHandler.post {
            // setAudioAttributes 必须在主线程
            player.setAudioAttributes(mediaAudioAttributes, /* handleAudioFocus = */ !enabled)
        }
    }

    /**
     * 游戏轻量档：与网络「最省」同步用。
     * 不重建播放器（防崩）。
     */
    fun setGameLiteMode(enabled: Boolean) {
        gameLiteMode.set(enabled)
    }

    fun isGameLiteMode(): Boolean = gameLiteMode.get()

    fun setNetworkIntensity(level: NetworkIntensity) {
        networkIntensityOrdinal.set(level.ordinal)
        // 最省档同步 gameLite 标志，供通知节流等旧逻辑
        if (level == NetworkIntensity.MINIMAL) gameLiteMode.set(true)
    }

    fun networkIntensityLevel(): NetworkIntensity = networkIntensity()

    /** 前后台：放慢进度刷新；写盘策略由网络档位决定。不改变点播逻辑。 */
    fun setAppInBackground(inBackground: Boolean) {
        backgroundMode.set(inBackground)
    }

    /** @param minutes 0 = 取消 */
    fun setSleepTimerMinutes(minutes: Int) {
        sleepJob?.cancel()
        sleepJob = null
        if (minutes <= 0) {
            _sleepRemainingMs.value = 0L
            return
        }
        val total = minutes * 60_000L
        _sleepRemainingMs.value = total
        sleepJob = scope.launch {
            var left = total
            while (isActive && left > 0) {
                delay(1000)
                left -= 1000
                _sleepRemainingMs.value = left.coerceAtLeast(0L)
            }
            if (isActive) {
                _sleepRemainingMs.value = 0L
                player.pause()
                publishFromPlayer()
            }
        }
    }

    fun dispatch(command: PlayerCommand) {
        when (command) {
            PlayerCommand.Play -> {
                if (player.mediaItemCount > 0) player.play()
            }
            PlayerCommand.Pause -> player.pause()
            PlayerCommand.Toggle -> {
                if (player.isPlaying) player.pause() else if (player.mediaItemCount > 0) player.play()
            }
            PlayerCommand.Stop -> {
                ending = false
                stopTicker()
                player.pause()
                player.stop()
                player.clearMediaItems()
                queue = emptyList()
                _state.value = PlaybackState(
                    current = null,
                    queue = emptyList(),
                    isPlaying = false,
                    positionMs = 0L,
                    durationMs = 0L,
                    isLoading = false,
                    errorMessage = null,
                    repeatMode = repeatMode,
                    shuffle = shuffle,
                )
            }
            PlayerCommand.Next -> {
                // 无媒体时绝不回调 Ended，避免 VM 再 dispatch Next 形成死循环
                if (player.mediaItemCount <= 0) return
                if (player.mediaItemCount > 1 && player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                } else {
                    onPlaybackEnded?.invoke()
                }
            }
            PlayerCommand.Previous -> {
                // 上一首统一走外部队列回调（AppViewModel.previous），
                // 避免引擎层 3s 重头与 VM 再判一次，导致第一首要点两下。
                val ext = onExternalPrevious
                if (ext != null) {
                    ext.invoke()
                } else if (player.currentPosition > 3_000) {
                    player.seekTo(0)
                } else if (player.mediaItemCount > 1 && player.hasPreviousMediaItem()) {
                    player.seekToPreviousMediaItem()
                } else {
                    player.seekTo(0)
                }
            }
            is PlayerCommand.Seek -> {
                val target = command.positionMs.coerceAtLeast(0L)
                val dur = player.duration
                val capped = if (dur > 0) target.coerceAtMost(dur) else target
                player.seekTo(capped)
                _state.update { it.copy(positionMs = capped, errorMessage = null) }
            }
            is PlayerCommand.PlayTrack -> playTrack(
                command.track,
                command.queue,
                command.startPositionMs,
            )
        }
    }

    fun setRepeat(mode: RepeatMode) {
        repeatMode = mode
        player.repeatMode = when (mode) {
            RepeatMode.OFF, RepeatMode.ALL -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        _state.update { it.copy(repeatMode = mode) }
    }

    fun setShuffle(enabled: Boolean) {
        shuffle = enabled
        player.shuffleModeEnabled = false
        _state.update { it.copy(shuffle = enabled) }
    }

    /** 播放倍速（1f 正常；菜单可选 0.75~10） */
    fun setPlaybackSpeed(speed: Float) {
        val s = speed.coerceIn(0.25f, 10.0f)
        player.setPlaybackSpeed(s)
    }

    fun playbackSpeed(): Float = player.playbackParameters.speed

    /**
     * 点播瞬间先占位当前曲，避免 UI 还停在旧曲上（搜索点进清屏空白/错页）。
     */
    fun prepareTrack(track: Track, asVideo: Boolean = false) {
        ending = false
        queue = listOf(track)
        _state.value = PlaybackState(
            current = track.copy(isVideoStream = asVideo || track.isVideoStream),
            queue = listOf(track),
            isPlaying = false,
            positionMs = 0L,
            durationMs = track.durationMs,
            isLoading = true,
            errorMessage = null,
            repeatMode = repeatMode,
            shuffle = shuffle,
        )
    }

    fun release() {
        stopTicker()
        sleepJob?.cancel()
        audioFx.release()
        player.release()
    }

    private fun handleEnded() {
        if (ending) return
        if (repeatMode == RepeatMode.ONE) {
            player.seekTo(0)
            player.play()
            return
        }
        ending = true
        onPlaybackEnded?.invoke()
        scope.launch {
            delay(500)
            ending = false
        }
    }

    private fun playTrack(track: Track, queueIn: List<Track>, startPositionMs: Long = 0L) {
        ending = false
        val resolved = (if (queueIn.isEmpty()) listOf(track) else queueIn)
        queue = resolved

        // 本地缓存文件优先（file:// 或绝对路径）
        val playable = resolved.mapNotNull { t ->
            val url = t.streamUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            t to url
        }
        if (playable.isEmpty()) {
            _state.value = PlaybackState(
                current = track,
                queue = resolved,
                isPlaying = false,
                positionMs = 0L,
                durationMs = track.durationMs,
                isLoading = false,
                errorMessage = "暂无可播地址",
                repeatMode = repeatMode,
                shuffle = shuffle,
            )
            return
        }

        val items = playable.map { (t, url) -> t.toMediaItem(url) }
        val startIndex = playable.indexOfFirst { it.first.id == track.id }.coerceAtLeast(0)
        val startMs = startPositionMs.coerceAtLeast(0L)

        player.setMediaItems(items, startIndex, startMs)
        player.prepare()
        player.play()
        audioFx.attach(player.audioSessionId)

        _state.value = PlaybackState(
            current = playable[startIndex].first,
            queue = playable.map { it.first },
            isPlaying = true,
            positionMs = startMs,
            durationMs = playable[startIndex].first.durationMs,
            isLoading = true,
            errorMessage = null,
            repeatMode = repeatMode,
            shuffle = shuffle,
        )
        startTicker()
    }

    private fun publishFromPlayer() {
        val idx = player.currentMediaItemIndex
        val current = queue.getOrNull(idx) ?: queue.firstOrNull()
        val playerDur = player.duration
        val duration = when {
            playerDur > 0 -> playerDur
            (current?.durationMs ?: 0L) > 0L -> current!!.durationMs
            else -> 0L
        }
        val loading = player.playbackState == Player.STATE_BUFFERING ||
            player.playbackState == Player.STATE_IDLE
        _state.update {
            it.copy(
                current = current,
                queue = queue,
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = duration,
                isLoading = loading && player.playbackState == Player.STATE_BUFFERING,
                errorMessage = if (player.playerError != null) it.errorMessage else null,
                repeatMode = repeatMode,
                shuffle = shuffle,
            )
        }
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                if (player.isPlaying || player.playbackState == Player.STATE_BUFFERING) {
                    publishFromPlayer()
                }
                // 后台 / 省网档时更慢刷新进度（功能不变）
                val net = networkIntensity()
                val interval = when {
                    backgroundMode.get() && net == NetworkIntensity.MINIMAL -> 2_000L
                    backgroundMode.get() -> 1_200L
                    net == NetworkIntensity.MINIMAL -> 700L
                    net == NetworkIntensity.FULL -> 350L
                    else -> 400L
                }
                delay(interval)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
        publishFromPlayer()
    }

    private fun Track.toMediaItem(url: String): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(coverUrl?.let { runCatching { Uri.parse(it) }.getOrNull() })
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsPlayable(true)
            .build()
        val uri = when {
            url.startsWith("http") -> Uri.parse(url)
            url.startsWith("file:") -> Uri.parse(url)
            File(url).exists() -> Uri.fromFile(File(url))
            else -> Uri.parse(url)
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setMediaMetadata(meta)
            .build()
    }
}
