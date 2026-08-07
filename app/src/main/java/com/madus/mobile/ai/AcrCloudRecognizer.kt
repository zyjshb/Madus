package com.madus.mobile.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * ACRCloud 识别：哼唱用 [humming]，原声/环境音用 [audio]，两者都试再合并。
 * 通用策略，禁止按歌名单曲特判。
 */
class AcrCloudRecognizer(
    private val http: OkHttpClient = defaultClient(),
) {
    data class RecognizeOutcome(
        val candidates: List<SongCandidate>,
        /** 非空表示引擎请求失败（密钥/网络/配额等） */
        val error: String? = null,
    )

    suspend fun recognize(
        file: File,
        config: HummingConfigState,
    ): RecognizeOutcome = withContext(Dispatchers.IO) {
        val host = config.acrHost.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
        if (host.isBlank()) {
            return@withContext RecognizeOutcome(emptyList(), "ACRCloud Host 未填写")
        }
        if (config.acrAccessKey.isBlank() || config.acrAccessSecret.isBlank()) {
            return@withContext RecognizeOutcome(emptyList(), "ACRCloud Access Key / Secret 未填写")
        }
        val bytes = runCatching { file.readBytes() }.getOrElse {
            return@withContext RecognizeOutcome(emptyList(), "读录音失败：${it.message}")
        }
        if (bytes.size > MAX_AUDIO_BYTES) {
            return@withContext RecognizeOutcome(emptyList(), "音频超过 1M，请录短一点")
        }

        // 麦克风场景：humming 优先。audio 指纹在纯哼唱时极易认错热门歌，
        // 仅当 humming 完全无结果时才用 audio 兜底。
        val errors = mutableListOf<String>()

        val hum = identifyOnce(
            host = host,
            accessKey = config.acrAccessKey,
            accessSecret = config.acrAccessSecret,
            bytes = bytes,
            dataType = "humming",
        )
        if (hum.error != null && hum.candidates.isEmpty()) {
            errors.add("humming: ${hum.error}")
        }

        val list = if (hum.candidates.isNotEmpty()) {
            hum.candidates
                .sortedByDescending { it.confidence ?: 0f }
                .let { all ->
                    val top = all.firstOrNull()?.confidence ?: 0f
                    // 只留相对靠谱的，避免一堆低分噪声
                    if (top >= 0.45f) {
                        all.filter { (it.confidence ?: 0f) >= (top * 0.55f).coerceAtLeast(0.32f) }
                    } else {
                        all.filter { (it.confidence ?: 0f) >= 0.28f }.ifEmpty { all.take(3) }
                    }
                }
                .take(6)
        } else {
            val audio = identifyOnce(
                host = host,
                accessKey = config.acrAccessKey,
                accessSecret = config.acrAccessSecret,
                bytes = bytes,
                dataType = "audio",
            )
            if (audio.error != null && audio.candidates.isEmpty()) {
                errors.add("audio: ${audio.error}")
            }
            // audio 兜底要求更高分，减少乱认
            audio.candidates
                .filter { (it.confidence ?: 0f) >= 0.45f }
                .sortedByDescending { it.confidence ?: 0f }
                .take(4)
                .ifEmpty {
                    audio.candidates.sortedByDescending { it.confidence ?: 0f }.take(2)
                }
        }

        when {
            list.isNotEmpty() -> RecognizeOutcome(list, error = null)
            errors.isNotEmpty() -> RecognizeOutcome(emptyList(), errors.joinToString("；"))
            else -> RecognizeOutcome(emptyList(), null)
        }
    }

    private fun identifyOnce(
        host: String,
        accessKey: String,
        accessSecret: String,
        bytes: ByteArray,
        dataType: String,
    ): RecognizeOutcome {
        return runCatching {
            val timestamp = (System.currentTimeMillis() / 1000).toString()
            val signature = sign(
                "POST\n/v1/identify\n$accessKey\n$dataType\n1\n$timestamp",
                accessSecret,
            )
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("access_key", accessKey)
                .addFormDataPart("sample_bytes", bytes.size.toString())
                .addFormDataPart("timestamp", timestamp)
                .addFormDataPart("signature", signature)
                .addFormDataPart("data_type", dataType)
                .addFormDataPart("signature_version", "1")
                .addFormDataPart(
                    "sample",
                    "sample.wav",
                    bytes.toRequestBody("audio/wav".toMediaType()),
                )
                .build()

            val req = Request.Builder()
                .url("https://$host/v1/identify")
                .post(body)
                .build()

            val resp = http.newCall(req).execute()
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return@runCatching RecognizeOutcome(
                    emptyList(),
                    "HTTP ${resp.code} ${raw.take(180)}",
                )
            }
            val json = runCatching { JSONObject(raw) }.getOrElse {
                return@runCatching RecognizeOutcome(emptyList(), "返回无法解析：${raw.take(160)}")
            }
            val status = json.optJSONObject("status")
            val code = status?.opt("code")?.toString()
            if (code != null && code != "0") {
                val msg = status?.optString("msg").orEmpty()
                // 1001 = No result：成功但无匹配，不算引擎错误
                if (code == "1001" || msg.contains("No result", ignoreCase = true)) {
                    return@runCatching RecognizeOutcome(emptyList(), null)
                }
                return@runCatching RecognizeOutcome(emptyList(), "错误 $code：$msg")
            }
            RecognizeOutcome(parseCandidates(json.optJSONObject("metadata"), dataType), null)
        }.getOrElse {
            RecognizeOutcome(emptyList(), it.message ?: it.javaClass.simpleName)
        }
    }

    private fun parseCandidates(metadata: JSONObject?, dataType: String): List<SongCandidate> {
        if (metadata == null) return emptyList()
        val buckets = listOf(
            "humming" to "ACR哼唱",
            "music" to "ACR曲库",
            "custom_files" to "ACR自定义",
        )
        val merged = LinkedHashMap<String, SongCandidate>()
        for ((key, note) in buckets) {
            val arr = metadata.optJSONArray(key) ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val title = o.optString("title").trim()
                if (title.isBlank() || SongGuessParser.isGarbageTitle(title)) continue
                val artist = o.optJSONArray("artists")
                    ?.optJSONObject(0)
                    ?.optString("name")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                val score = parseScore(o.opt("score"))
                // 过低噪声丢掉；humming 桶稍宽
                val minScore = if (key == "humming") 0.18f else 0.22f
                if (score < minScore) continue
                val id = title.lowercase() + "|" + (artist?.lowercase() ?: "")
                val cand = SongCandidate(
                    title = title,
                    artist = artist,
                    confidence = score,
                    bilibiliQuery = listOfNotNull(title, artist).joinToString(" ")
                        .ifBlank { title },
                    note = "$note·$dataType",
                )
                val old = merged[id]
                if (old == null || (old.confidence ?: 0f) < score) {
                    merged[id] = cand
                }
            }
        }
        return merged.values.sortedByDescending { it.confidence ?: 0f }.take(10)
    }

    private fun parseScore(raw: Any?): Float {
        if (raw == null || raw == JSONObject.NULL) return 0.5f
        val v = raw.toString().trim().toFloatOrNull() ?: return 0.5f
        return if (v > 1f) (v / 100f).coerceIn(0.15f, 0.99f) else v.coerceIn(0.15f, 0.99f)
    }

    private fun sign(data: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        return Base64.getEncoder().encodeToString(mac.doFinal(data.toByteArray(Charsets.UTF_8)))
    }

    companion object {
        private const val MAX_AUDIO_BYTES = 1024 * 1024

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
