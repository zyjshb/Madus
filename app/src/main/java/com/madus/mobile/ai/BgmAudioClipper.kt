package com.madus.mobile.ai

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 从音频流 / 本地视频文件截取一小段 WAV，供多模态听曲。
 * 播放中「本片 BGM」悬浮球已下线；本地上传视频走 [clipLocalUriToWav]。
 *
 * 稳定性：全程有墙钟超时，避免 CDN / MediaExtractor 卡死导致 UI 永久转圈。
 */
object BgmAudioClipper {

    /** 默认截取时长 */
    const val CLIP_MS = 12_000L

    /** 整段截取（含下载+解码）上限 */
    const val TOTAL_TIMEOUT_MS = 35_000L

    /** 单次解码循环上限 */
    private const val DECODE_DEADLINE_MS = 18_000L

    private val httpHeaders = mapOf(
        "User-Agent" to
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer" to "https://www.bilibili.com/",
        "Origin" to "https://www.bilibili.com",
    )

    /**
     * 用户上传的本地视频/音频 Uri → 截取前几秒 WAV。
     * 用于 AI 搜「视频识歌」（不走播放器悬浮球）。
     */
    suspend fun clipLocalUriToWav(
        context: Context,
        uri: Uri,
        startMs: Long = 0L,
        durationMs: Long = CLIP_MS,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            withTimeout(TOTAL_TIMEOUT_MS) {
                val dir = File(context.cacheDir, "bgm_clip").also { it.mkdirs() }
                val raw = File(dir, "upload_${System.currentTimeMillis()}.bin")
                val out = File(dir, "clip_${System.currentTimeMillis()}.wav")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(raw).use { fos ->
                        val buf = ByteArray(64 * 1024)
                        var total = 0
                        val maxBytes = 12_000_000
                        while (total < maxBytes) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            fos.write(buf, 0, n)
                            total += n
                        }
                    }
                } ?: error("无法读取视频文件")
                if (raw.length() < 2048) {
                    raw.delete()
                    error("文件太小或无法读取")
                }
                val ok = decodeFileToWav(raw, out, startMs.coerceAtLeast(0L), durationMs) ||
                    decodeFileToWav(raw, out, 0L, durationMs)
                raw.delete()
                if (!ok || !out.exists() || out.length() < 1024) {
                    out.delete()
                    error("无法从视频提取音轨（可能无音频或编码不支持）")
                }
                out
            }
        }.recoverCatching { e ->
            when (e) {
                is kotlinx.coroutines.TimeoutCancellationException ->
                    error("提取音轨超时，请换短一点的视频")
                else -> throw e
            }
        }
    }

    /**
     * @param streamUrl 纯音频或 progressive 可播地址
     * @param startMs 起点（通常用当前播放进度略回退）
     * @return WAV 文件（调用方负责删除）
     */
    suspend fun clipToWav(
        context: Context,
        streamUrl: String,
        startMs: Long,
        durationMs: Long = CLIP_MS,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(streamUrl.isNotBlank()) { "无音频地址" }
            withTimeout(TOTAL_TIMEOUT_MS) {
                val dir = File(context.cacheDir, "bgm_clip").also { it.mkdirs() }
                // 清理旧临时文件，避免缓存堆积
                dir.listFiles()?.filter {
                    it.name.startsWith("clip_") || it.name.startsWith("raw_")
                }?.sortedByDescending { it.lastModified() }
                    ?.drop(6)
                    ?.forEach { runCatching { it.delete() } }

                val out = File(dir, "clip_${System.currentTimeMillis()}.wav")
                val start = startMs.coerceAtLeast(0L)

                // ① 优先：限量下载再解码（CDN 可控、有超时，不易卡死）
                val raw = File(dir, "raw_${System.currentTimeMillis()}.bin")
                var downloaded = false
                runCatching {
                    downloadLimited(streamUrl, raw, maxBytes = 3_000_000)
                    downloaded = raw.exists() && raw.length() >= 1024
                }
                if (downloaded) {
                    // 文件从头下的：若起点较后，解码时 seek；seek 失败则从 0 截
                    val ok = decodeFileToWav(raw, out, start, durationMs) ||
                        (start > 0 && decodeFileToWav(raw, out, 0L, durationMs))
                    raw.delete()
                    if (ok && out.exists() && out.length() >= 1024) return@withTimeout out
                } else {
                    runCatching { raw.delete() }
                }

                // ② 回退：MediaExtractor 直接读 URL（带解码墙钟）
                val okUrl = decodeUrlToWav(streamUrl, out, start, durationMs) ||
                    (start > 0 && decodeUrlToWav(streamUrl, out, 0L, durationMs))
                if (okUrl && out.exists() && out.length() >= 1024) return@withTimeout out

                out.delete()
                error("截取超时或音轨不可读，请换进度再试或稍后重试")
            }
        }.recoverCatching { e ->
            when (e) {
                is kotlinx.coroutines.TimeoutCancellationException ->
                    error("截取超时（网络/CDN 过慢），请换进度或稍后重试")
                else -> throw e
            }
        }
    }

    private fun downloadLimited(url: String, out: File, maxBytes: Int) {
        val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 25_000
            instanceFollowRedirects = true
            httpHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("下载音频 HTTP $code")
            conn.inputStream.use { input ->
                FileOutputStream(out).use { fos ->
                    val buf = ByteArray(16 * 1024)
                    var total = 0
                    val wallStart = System.currentTimeMillis()
                    while (total < maxBytes) {
                        if (System.currentTimeMillis() - wallStart > 25_000L) {
                            error("下载音频超时")
                        }
                        val n = input.read(buf, 0, minOf(buf.size, maxBytes - total))
                        if (n <= 0) break
                        fos.write(buf, 0, n)
                        total += n
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (out.length() < 1024) error("下载音频失败或太短")
    }

    private fun decodeUrlToWav(
        url: String,
        outWav: File,
        startMs: Long,
        durationMs: Long,
    ): Boolean = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(url, httpHeaders)
            decodeExtractorToWav(extractor, outWav, startMs, durationMs)
        } finally {
            runCatching { extractor.release() }
        }
    }.getOrDefault(false)

    private fun decodeFileToWav(
        file: File,
        outWav: File,
        startMs: Long,
        durationMs: Long,
    ): Boolean = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            decodeExtractorToWav(extractor, outWav, startMs, durationMs)
        } finally {
            runCatching { extractor.release() }
        }
    }.getOrDefault(false)

    private fun decodeExtractorToWav(
        extractor: MediaExtractor,
        outWav: File,
        startMs: Long,
        durationMs: Long,
    ): Boolean {
        val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
            mime.startsWith("audio/")
        } ?: return false
        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return false
        runCatching {
            extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        }

        val codec = MediaCodec.createDecoderByType(mime)
        val wallStart = System.currentTimeMillis()
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            val pcm = ArrayList<ByteArray>(256)
            var totalPcmBytes = 0
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)
            val maxPcmBytes = (sampleRate * channels * 2 * (durationMs / 1000.0)).toInt()
                .coerceIn(sampleRate, sampleRate * channels * 2 * 15)

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val timeoutUs = 10_000L
            var idleRounds = 0

            while (!outputDone && totalPcmBytes < maxPcmBytes) {
                if (System.currentTimeMillis() - wallStart > DECODE_DEADLINE_MS) {
                    break // 墙钟到：有数据则尽量写出，否则失败
                }
                var progressed = false
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(timeoutUs)
                    if (inIndex >= 0) {
                        progressed = true
                        val buffer = codec.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            val pts = extractor.sampleTime
                            codec.queueInputBuffer(inIndex, 0, sampleSize, pts, 0)
                            extractor.advance()
                            if (pts >= 0 && pts > (startMs + durationMs) * 1000L) {
                                inputDone = true
                            }
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, timeoutUs)
                when {
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> progressed = true
                    outIndex >= 0 -> {
                        progressed = true
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            val outBuf = codec.getOutputBuffer(outIndex)!!
                            val chunk = ByteArray(info.size)
                            outBuf.position(info.offset)
                            outBuf.get(chunk)
                            pcm.add(chunk)
                            totalPcmBytes += chunk.size
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                        if (totalPcmBytes >= maxPcmBytes) outputDone = true
                    }
                }
                if (!progressed) {
                    idleRounds++
                    if (idleRounds > 200 && totalPcmBytes == 0) break // ~2s 无进展
                    if (idleRounds > 800) break
                } else {
                    idleRounds = 0
                }
            }
            runCatching { codec.stop() }
            if (totalPcmBytes < sampleRate) return false // <0.5s
            writeWav(outWav, pcm, sampleRate, channels, totalPcmBytes.coerceAtMost(maxPcmBytes))
            return true
        } finally {
            runCatching { codec.release() }
        }
    }

    private fun writeWav(
        file: File,
        chunks: List<ByteArray>,
        sampleRate: Int,
        channels: Int,
        maxBytes: Int,
    ) {
        val dataSize = maxBytes.coerceAtMost(chunks.sumOf { it.size })
        FileOutputStream(file).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(36 + dataSize)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16)
            header.putShort(1) // PCM
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(sampleRate * channels * 2)
            header.putShort((channels * 2).toShort())
            header.putShort(16)
            header.put("data".toByteArray())
            header.putInt(dataSize)
            fos.write(header.array())
            var written = 0
            for (c in chunks) {
                if (written >= dataSize) break
                val n = minOf(c.size, dataSize - written)
                fos.write(c, 0, n)
                written += n
            }
        }
    }
}
