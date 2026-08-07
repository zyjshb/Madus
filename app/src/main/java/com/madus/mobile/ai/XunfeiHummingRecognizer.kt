package com.madus.mobile.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class XunfeiHummingRecognizer(
    private val http: OkHttpClient = defaultClient(),
) {
    data class RecognizeOutcome(
        val candidates: List<SongCandidate>,
        val error: String? = null,
    )

    /**
     * 识别哼唱。PCM 优先；预处理失败或空结果时回退原始 WAV。
     * 错误透出，禁止吞成「未命中」。
     */
    suspend fun recognize(
        file: File,
        appId: String,
        apiKey: String,
        hint: String = "",
    ): RecognizeOutcome = withContext(Dispatchers.IO) {
        if (appId.isBlank() || apiKey.isBlank()) {
            return@withContext RecognizeOutcome(emptyList(), "讯飞 AppID/API Key 未填写")
        }
        val original = runCatching { file.readBytes() }.getOrElse {
            return@withContext RecognizeOutcome(emptyList(), "读录音失败：${it.message}")
        }
        if (original.size < 1000) {
            return@withContext RecognizeOutcome(emptyList(), "录音太短")
        }
        val pcm = preparePcm(original)
        // 预处理过狠时 pcm 可能为空：直接用原始字节
        val primaryBytes = if (pcm.isNotEmpty()) pcm else original
        val primary = requestCandidates(primaryBytes, appId, apiKey)
        if (primary.isFailure) {
            // PCM 失败再试原始 WAV
            if (primaryBytes !== original) {
                val fallback = requestCandidates(original, appId, apiKey)
                if (fallback.isSuccess) {
                    return@withContext RecognizeOutcome(
                        reorderByHint(fallback.getOrDefault(emptyList()), hint),
                    )
                }
                val err = primary.exceptionOrNull()?.message
                    ?: fallback.exceptionOrNull()?.message
                    ?: "讯飞哼唱识别失败"
                return@withContext RecognizeOutcome(emptyList(), err)
            }
            return@withContext RecognizeOutcome(
                emptyList(),
                primary.exceptionOrNull()?.message ?: "讯飞哼唱识别失败",
            )
        }
        var candidates = primary.getOrDefault(emptyList())
        // 有预处理时交叉原始，重复出现的更可信
        if (pcm.isNotEmpty() && pcm.size != original.size) {
            val second = requestCandidates(original, appId, apiKey)
            if (second.isSuccess) {
                candidates = mergeCandidates(candidates, second.getOrDefault(emptyList()))
            }
        }
        RecognizeOutcome(reorderByHint(candidates, hint), null)
    }

    private fun requestCandidates(
        bytes: ByteArray,
        appId: String,
        apiKey: String,
    ): Result<List<SongCandidate>> = runCatching {
        if (bytes.size > MAX_AUDIO_BYTES) {
            error("录音超过 2M，请录短一点")
        }
        val curTime = (System.currentTimeMillis() / 1000).toString()
        val param = JSONObject()
            .put("engine_type", "afs")
            .put("aue", "raw")
            .put("sample_rate", "16000")
            .toString()
        val paramB64 = Base64.getEncoder()
            .encodeToString(param.toByteArray(Charsets.UTF_8))
        val checksum = md5(apiKey + curTime + paramB64)

        val req = Request.Builder()
            .url(ENDPOINT)
            .addHeader("X-Appid", appId)
            .addHeader("X-CurTime", curTime)
            .addHeader("X-Param", paramB64)
            .addHeader("X-CheckSum", checksum)
            .post(bytes.toRequestBody(AUDIO_MEDIA))
            .build()

        val resp = http.newCall(req).execute()
        val raw = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) {
            throw IllegalStateException("讯飞请求失败 (HTTP ${resp.code}) ${raw.take(240)}")
        }
        val json = runCatching { JSONObject(raw) }.getOrElse {
            throw IllegalStateException("讯飞返回无法解析：${raw.take(200)}")
        }
        if (json.optString("code") != "0") {
            throw IllegalStateException(
                "讯飞错误 ${json.optString("code")}：${json.optString("desc")}",
            )
        }
        parseCandidates(json.optJSONArray("data"))
    }

    private fun parseCandidates(data: JSONArray?): List<SongCandidate> {
        if (data == null || data.length() == 0) return emptyList()
        val seen = LinkedHashMap<String, JSONObject>()
        for (i in 0 until data.length()) {
            val o = data.optJSONObject(i) ?: continue
            val title = o.optString("song").trim()
            if (title.isBlank() || SongGuessParser.isGarbageTitle(title)) continue
            val singer = o.optString("singer").trim()
            val key = "${title.lowercase()}|${singer.lowercase()}"
            if (seen.containsKey(key)) continue
            seen[key] = o
        }
        return seen.values.mapIndexed { i, o ->
            val title = o.optString("song").trim()
            val artist = o.optString("singer").trim().takeIf { it.isNotBlank() }
            SongCandidate(
                title = title,
                artist = artist,
                confidence = (0.96f - i * 0.05f).coerceAtLeast(0.55f),
                bilibiliQuery = listOfNotNull(title, artist).joinToString(" ").ifBlank { title },
                note = "讯飞哼唱识别",
            )
        }.take(8)
    }

    /**
     * 同一首歌在两个请求里都出现，比只在一边出现的候选更可信。
     */
    private fun mergeCandidates(
        a: List<SongCandidate>,
        b: List<SongCandidate>,
    ): List<SongCandidate> {
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        val counts = HashMap<String, Int>()
        val firstIndex = HashMap<String, Int>()
        val byKey = LinkedHashMap<String, SongCandidate>()
        (a + b).forEachIndexed { idx, c ->
            val key = c.title.trim().lowercase()
            if (key.isBlank()) return@forEachIndexed
            counts[key] = (counts[key] ?: 0) + 1
            firstIndex.putIfAbsent(key, if (idx < a.size) 1000 + idx else idx)
            val old = byKey[key]
            if (old == null || (old.artist.isNullOrBlank() && !c.artist.isNullOrBlank())) {
                byKey[key] = c
            }
        }
        return byKey.values
            .sortedWith(
                compareByDescending<SongCandidate> { counts[it.title.trim().lowercase()] ?: 0 }
                    .thenBy { firstIndex[it.title.trim().lowercase()] ?: Int.MAX_VALUE },
            )
            .mapIndexed { i, c ->
                c.copy(
                    confidence = if ((counts[c.title.trim().lowercase()] ?: 0) >= 2) {
                        (0.97f - i * 0.03f).coerceAtLeast(0.7f)
                    } else {
                        (0.92f - i * 0.04f).coerceAtLeast(0.55f)
                    },
                )
            }
            .take(8)
    }

    private fun reorderByHint(
        candidates: List<SongCandidate>,
        hint: String,
    ): List<SongCandidate> {
        if (candidates.size <= 1) return candidates
        val q = compactHint(hint)
        if (q.length < 2) return candidates
        val scored = candidates.mapIndexed { i, c ->
            Triple(c, hintScore(q, compactHint(c.title)), i)
        }
        val best = scored.maxOfOrNull { it.second } ?: return candidates
        if (best < 0.35f) return candidates
        return scored
            .sortedWith(
                compareByDescending<Triple<SongCandidate, Float, Int>> { it.second }
                    .thenBy { it.third },
            )
            .map { it.first }
    }

    private fun hintScore(hint: String, title: String): Float {
        if (hint.isBlank() || title.isBlank()) return 0f
        if (hint == title) return 1f
        if (title.contains(hint) || hint.contains(title)) return 0.95f
        val hCjk = hint.filter { it.code in 0x4E00..0x9FFF }.toSet()
        val tCjk = title.filter { it.code in 0x4E00..0x9FFF }.toSet()
        if (hCjk.isNotEmpty() && tCjk.isNotEmpty()) {
            val common = hCjk.intersect(tCjk).size
            if (common >= 2) {
                return common.toFloat() / max(hCjk.size, tCjk.size)
            }
        }
        val hTokens = hint.split(Regex("\\s+")).filter { it.length >= 3 }
        if (hTokens.any { title.contains(it, ignoreCase = true) }) return 0.8f
        return 0f
    }

    private fun compactHint(s: String): String =
        s.lowercase()
            .filter { it.isLetterOrDigit() || it.code in 0x4E00..0x9FFF }
            .trim()

    /**
     * 读 WAV，裁掉头尾静音、限长并归一化音量，返回 16k/16bit/mono 纯 PCM。
     */
    private fun preparePcm(bytes: ByteArray): ByteArray {
        val wav = readWav(bytes) ?: return byteArrayOf()
        val trimmed = trimAndNormalize(wav.samples, wav.sampleRate)
        if (trimmed.isEmpty()) return byteArrayOf()
        val bb = ByteBuffer.allocate(trimmed.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in trimmed) bb.putShort(s)
        return bb.array()
    }

    private data class WavAudio(
        val samples: ShortArray,
        val sampleRate: Int,
    )

    private fun readWav(bytes: ByteArray): WavAudio? {
        if (bytes.size < 44) return null
        if (bytes[0] != 'R'.code.toByte() ||
            bytes[1] != 'I'.code.toByte() ||
            bytes[2] != 'F'.code.toByte() ||
            bytes[3] != 'F'.code.toByte()
        ) {
            return null
        }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val channels = bb.getShort(22).toInt()
        val sampleRate = bb.getInt(24)
        val bits = bb.getShort(34).toInt()
        if (channels != 1 || bits != 16 || sampleRate <= 0) return null

        var offset = 12
        var dataOffset = -1
        var dataSize = 0
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = bb.getInt(offset + 4)
            if (id == "data") {
                dataOffset = offset + 8
                dataSize = size
                break
            }
            offset += 8 + size + (size and 1)
        }
        if (dataOffset < 0) {
            dataOffset = 44
            dataSize = bytes.size - dataOffset
        }
        if (dataOffset < 0 || dataOffset >= bytes.size) return null
        val len = min(dataSize, bytes.size - dataOffset)
        if (len < 2) return null
        val samples = ShortArray(len / 2)
        ByteBuffer.wrap(bytes, dataOffset, len)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
            .get(samples)
        return WavAudio(samples, sampleRate)
    }

    private fun trimAndNormalize(
        samples: ShortArray,
        sampleRate: Int,
    ): ShortArray {
        if (samples.isEmpty()) return samples
        val peak = samples.maxOf { abs(it.toInt()) }
        // 极轻声仍尝试识别，别直接丢空
        if (peak < 40) return ShortArray(0)

        val frameSize = (sampleRate / 100).coerceAtLeast(1)
        // 阈值放宽：轻声哼唱也保留，避免误裁成空
        val threshold = 120
        var first = -1
        var last = -1
        var idx = 0
        while (idx < samples.size) {
            val end = min(idx + frameSize, samples.size)
            val active = (idx until end).any { abs(samples[it].toInt()) > threshold }
            if (active) {
                if (first < 0) first = idx
                last = end
            }
            idx = end
        }
        val maxSamples = (sampleRate * MAX_ACTIVE_MS / 1000).toInt()
        // 找不到活动段：仍送中间一段，别返回空导致引擎啥也收不到
        if (first < 0) {
            if (samples.size <= maxSamples) return samples
            val start = ((samples.size - maxSamples) / 2).coerceAtLeast(0)
            return samples.copyOfRange(start, start + maxSamples)
        }

        val padStart = (first - sampleRate / 5).coerceAtLeast(0)
        val padEnd = (last + sampleRate / 3).coerceAtMost(samples.size)
        var clipped = samples.copyOfRange(padStart, padEnd)
        if (clipped.size > maxSamples) {
            clipped = loudestWindow(clipped, maxSamples)
        }

        val clippedPeak = clipped.maxOf { abs(it.toInt()) }
        if (clippedPeak > 0 && clippedPeak < 24_000) {
            val gain = 0.82f * Short.MAX_VALUE / clippedPeak
            clipped = ShortArray(clipped.size) { i ->
                (clipped[i] * gain).roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
        }
        return clipped
    }

    private fun loudestWindow(samples: ShortArray, windowSize: Int): ShortArray {
        if (samples.size <= windowSize) return samples
        val frame = (samples.size / 50).coerceIn(64, 640)
        val frameCount = (samples.size + frame - 1) / frame
        val energies = LongArray(frameCount)
        for (f in 0 until frameCount) {
            val start = f * frame
            val end = min(start + frame, samples.size)
            var sum = 0L
            for (i in start until end) {
                val v = samples[i].toInt()
                sum += v.toLong() * v
            }
            energies[f] = sum
        }
        val windowFrames = max(1, windowSize / frame)
        var bestStartFrame = 0
        var bestSum = 0L
        var running = 0L
        for (f in 0 until frameCount) {
            running += energies[f]
            if (f >= windowFrames) running -= energies[f - windowFrames]
            if (running > bestSum) {
                bestSum = running
                bestStartFrame = (f - windowFrames + 1).coerceAtLeast(0)
            }
        }
        val start = (bestStartFrame * frame).coerceAtMost(samples.size - windowSize)
        return samples.copyOfRange(start, start + windowSize)
    }

    private fun md5(s: String): String {
        val digest = MessageDigest.getInstance("MD5")
            .digest(s.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    companion object {
        private const val ENDPOINT = "https://webqbh.xfyun.cn/v1/service/v1/qbh"
        private const val MAX_AUDIO_BYTES = 2 * 1024 * 1024
        private const val MAX_ACTIVE_MS = 15_000L
        private val AUDIO_MEDIA = "application/octet-stream".toMediaType()
        private val HEX = "0123456789abcdef".toCharArray()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
