package com.madus.mobile.ai

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 短录音 → WAV PCM（16kHz / mono / 16bit）。
 * 暴露实时音量 [level01] 0~1，供声浪 UI。
 */
class HumRecorder(context: Context) {
    private val app = context.applicationContext
    private var recordThread: Thread? = null
    private val recording = AtomicBoolean(false)
    private val finishedNaturally = AtomicBoolean(false)
    private var outFile: File? = null
    private var startedAt = 0L

    /** 0~100 实时音量 */
    private val levelAtomic = AtomicInteger(0)

    /** 录音结束后待取的文件（自动满时长时用） */
    private val pendingResult = AtomicReference<Pair<File, Long>?>(null)

    val isRecording: Boolean get() = recording.get()
    val level01: Float get() = levelAtomic.get() / 100f
    val startedAtMs: Long get() = startedAt

    @SuppressLint("MissingPermission")
    fun start(): Result<File> = runCatching {
        stopInternal(discard = true)
        pendingResult.set(null)
        finishedNaturally.set(false)
        levelAtomic.set(0)

        val dir = File(app.cacheDir, "ai_hum").also { it.mkdirs() }
        val file = File(dir, "hum_${System.currentTimeMillis()}.wav")
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(SAMPLE_RATE / 10)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 2,
        )
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            error("麦克风初始化失败")
        }

        FileOutputStream(file).use { it.write(ByteArray(44)) }
        outFile = file
        startedAt = System.currentTimeMillis()
        recording.set(true)

        recordThread = thread(name = "hum-wav", isDaemon = true) {
            val buf = ShortArray(minBuf / 2)
            var totalPcmBytes = 0
            try {
                audioRecord.startRecording()
                FileOutputStream(file, true).use { fos ->
                    val byteBuf = ByteBuffer.allocate(buf.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                    while (recording.get()) {
                        val n = audioRecord.read(buf, 0, buf.size)
                        if (n > 0) {
                            var peak = 0
                            byteBuf.clear()
                            for (i in 0 until n) {
                                val s = buf[i]
                                peak = max(peak, abs(s.toInt()))
                                byteBuf.putShort(s)
                            }
                            fos.write(byteBuf.array(), 0, n * 2)
                            totalPcmBytes += n * 2
                            // 归一化到 0~100（人声大约 2000~15000）
                            val lvl = min(100, (peak * 100) / 12000)
                            levelAtomic.set(lvl)
                        }
                        if (System.currentTimeMillis() - startedAt >= MAX_MS) {
                            recording.set(false)
                            finishedNaturally.set(true)
                            break
                        }
                    }
                }
                writeWavHeader(file, totalPcmBytes)
                val dur = (System.currentTimeMillis() - startedAt).coerceIn(0L, MAX_MS)
                if (file.exists() && file.length() >= 1_000L && dur >= MIN_MS) {
                    pendingResult.set(file to dur)
                } else {
                    file.delete()
                    pendingResult.set(null)
                }
            } catch (_: Exception) {
                runCatching { file.delete() }
                pendingResult.set(null)
            } finally {
                recording.set(false)
                levelAtomic.set(0)
                runCatching { audioRecord.stop() }
                runCatching { audioRecord.release() }
            }
        }
        file
    }

    /**
     * 停止并取出文件。若线程已因满时长结束，取 [pendingResult]。
     */
    fun stop(): Pair<File, Long>? {
        val wasRecording = recording.getAndSet(false)
        try {
            recordThread?.join(2_500)
        } catch (_: Exception) {
        }
        recordThread = null
        outFile = null
        levelAtomic.set(0)

        val pending = pendingResult.getAndSet(null)
        if (pending != null) return pending

        // 异常路径：线程没写 pending
        return null
    }

    fun cancel() {
        stopInternal(discard = true)
    }

    fun takePendingIfFinished(): Pair<File, Long>? {
        if (recording.get()) return null
        return pendingResult.getAndSet(null)
    }

    private fun stopInternal(discard: Boolean) {
        recording.set(false)
        try {
            recordThread?.join(1_500)
        } catch (_: Exception) {
        }
        recordThread = null
        levelAtomic.set(0)
        val p = pendingResult.getAndSet(null)
        if (discard) {
            p?.first?.delete()
            outFile?.delete()
        }
        outFile = null
    }

    private fun writeWavHeader(file: File, pcmBytes: Int) {
        val totalDataLen = pcmBytes + 36
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(totalDataLen)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(1)
        header.putInt(SAMPLE_RATE)
        header.putInt(SAMPLE_RATE * 2)
        header.putShort(2)
        header.putShort(16)
        header.put("data".toByteArray())
        header.putInt(pcmBytes)
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(header.array())
        }
    }

    companion object {
        const val MAX_MS = 15_000L
        const val MIN_MS = 800L
        private const val SAMPLE_RATE = 16_000
    }
}
