package com.madus.mobile.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class LlmChatResult(
    val text: String,
    val reasoning: String? = null,
    val raw: String? = null,
)

/**
 * 多协议客户端：文本 + 可选音频/图片（OpenAI 兼容多模态）。
 */
class LlmClient(
    private val http: OkHttpClient = defaultClient(),
) {
    suspend fun chat(
        profile: LlmProfile,
        apiKey: String,
        system: String,
        userText: String,
        forceJson: Boolean = true,
        multimodal: MultimodalPayload? = null,
    ): Result<LlmChatResult> = withContext(Dispatchers.IO) {
        runCatching {
            when (profile.protocol) {
                LlmProtocol.OPENAI_COMPAT, LlmProtocol.GEMINI ->
                    openAiCompat(profile, apiKey, system, userText, forceJson, multimodal)
                LlmProtocol.ANTHROPIC ->
                    anthropic(profile, apiKey, system, userText, multimodal)
            }
        }
    }

    private fun openAiCompat(
        profile: LlmProfile,
        apiKey: String,
        system: String,
        userText: String,
        forceJson: Boolean,
        multimodal: MultimodalPayload?,
    ): LlmChatResult {
        // 多模态时部分厂商不支持 response_format，先试再退
        val tryJson = forceJson && multimodal?.hasAudio != true
        if (tryJson) {
            val withJson = runCatching {
                openAiCompatOnce(profile, apiKey, system, userText, jsonMode = true, multimodal)
            }
            if (withJson.isSuccess) return withJson.getOrThrow()
            val err = withJson.exceptionOrNull()?.message.orEmpty()
            if (err.contains("鉴权") || err.contains("401") || err.contains("403")) {
                throw withJson.exceptionOrNull()!!
            }
        }
        return openAiCompatOnce(profile, apiKey, system, userText, jsonMode = false, multimodal)
    }

    private fun openAiCompatOnce(
        profile: LlmProfile,
        apiKey: String,
        system: String,
        userText: String,
        jsonMode: Boolean,
        multimodal: MultimodalPayload?,
    ): LlmChatResult {
        val base = profile.baseUrl.trimEnd('/')
        val url = when {
            base.endsWith("/chat/completions") -> base
            else -> "$base/chat/completions"
        }
        val userContent = buildOpenAiUserContent(userText, multimodal, profile)
        // 听音频：MiMo Pro 等若无多模态，回退到 v2.5
        val modelId = if (multimodal?.hasAudio == true) {
            LlmPresets.audioModelOverride(profile) ?: profile.resolveModel()
        } else {
            profile.resolveModel()
        }
        val body = JSONObject().apply {
            put("model", modelId)
            put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", userContent)),
            )
            put("temperature", 0.2)
            put("max_tokens", 4096)
            if (jsonMode) {
                put("response_format", JSONObject().put("type", "json_object"))
            }
        }
        val reqBuilder = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
        if (profile.providerId == "mimo") {
            reqBuilder.addHeader("api-key", apiKey)
        }
        val resp = http.newCall(reqBuilder.build()).execute()
        val raw = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) {
            throw IllegalStateException(httpError(resp.code, raw))
        }
        val parsed = parseOpenAiContent(raw)
            ?: throw IllegalStateException("响应无内容，请检查模型名/区域/是否支持多模态")
        return LlmChatResult(
            text = parsed.text,
            reasoning = parsed.reasoning,
            raw = raw,
        )
    }

    /**
     * OpenAI / MiMo / Qwen 兼容：content 可为 string 或 parts 数组。
     */
    private fun buildOpenAiUserContent(
        userText: String,
        multimodal: MultimodalPayload?,
        profile: LlmProfile,
    ): Any {
        if (multimodal == null || (!multimodal.hasAudio && !multimodal.hasImage)) {
            return userText
        }
        val parts = JSONArray()
        parts.put(JSONObject().put("type", "text").put("text", userText))
        val caps = profile.effectiveCapabilities()
        if (multimodal.hasImage && caps.vision) {
            val dataUrl = "data:${multimodal.imageMime};base64,${multimodal.imageBase64}"
            parts.put(
                JSONObject()
                    .put("type", "image_url")
                    .put("image_url", JSONObject().put("url", dataUrl)),
            )
        }
        if (multimodal.hasAudio && caps.audioInput) {
            // 官方要求：mp3 / flac / m4a / wav / ogg（小写）
            val fmt = when (multimodal.audioFormat.lowercase()) {
                "m4a", "mp4", "aac", "x-m4a" -> "m4a"
                "wav", "wave", "x-wav" -> "wav"
                "mp3", "mpeg", "mpga" -> "mp3"
                "flac" -> "flac"
                "ogg", "opus" -> "ogg"
                else -> "wav"
            }
            val data = multimodal.audioBase64!!.replace("\n", "").replace("\r", "")
            parts.put(
                JSONObject()
                    .put("type", "input_audio")
                    .put(
                        "input_audio",
                        JSONObject()
                            .put("data", data)
                            .put("format", fmt),
                    ),
            )
        }
        return parts
    }

    private fun anthropic(
        profile: LlmProfile,
        apiKey: String,
        system: String,
        userText: String,
        multimodal: MultimodalPayload?,
    ): LlmChatResult {
        val base = profile.baseUrl.trimEnd('/')
        val url = if (base.endsWith("/v1/messages")) base else "$base/v1/messages"
        val content = JSONArray()
        if (multimodal?.hasImage == true) {
            val media = multimodal.imageMime.removePrefix("image/")
            content.put(
                JSONObject()
                    .put("type", "image")
                    .put(
                        "source",
                        JSONObject()
                            .put("type", "base64")
                            .put("media_type", multimodal.imageMime.ifBlank { "image/jpeg" })
                            .put("data", multimodal.imageBase64),
                    ),
            )
            // media unused silence
            @Suppress("UNUSED_VARIABLE")
            val _m = media
        }
        content.put(JSONObject().put("type", "text").put("text", userText))
        if (multimodal?.hasAudio == true) {
            // Claude 主路径不支持音频；提示用户换 MiMo/Omni
            content.put(
                JSONObject()
                    .put("type", "text")
                    .put(
                        "text",
                        "\n（用户附了音频，但当前 Claude 通道不支持音频识曲，请仅根据文字线索猜歌。）",
                    ),
            )
        }
        val body = JSONObject().apply {
            put("model", profile.resolveModel())
            put("max_tokens", 4096)
            put("temperature", 0.2)
            put("system", system)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user").put("content", content),
                ),
            )
        }
        val req = Request.Builder()
            .url(url)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()
        val resp = http.newCall(req).execute()
        val raw = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) {
            throw IllegalStateException(httpError(resp.code, raw))
        }
        val parsed = parseAnthropicContent(raw)
            ?: throw IllegalStateException("Claude 响应无文本")
        return LlmChatResult(
            text = parsed.text,
            reasoning = parsed.reasoning,
            raw = raw,
        )
    }

    private fun parseOpenAiContent(raw: String): LlmChatResult? {
        val root = JSONObject(raw)
        val choices = root.optJSONArray("choices") ?: return null
        if (choices.length() == 0) return null
        val choice = choices.getJSONObject(0)
        val msg = choice.optJSONObject("message")
        val content = msg?.let { extractMessageText(it) }
            ?: choice.optString("text").takeIf { it.isNotBlank() }
        val reasoning = msg?.let { extractReasoning(it) }
        val text = content.orEmpty().takeIf { it.isNotBlank() }
            ?: reasoning?.takeIf { it.isNotBlank() }
        if (text.isNullOrBlank() && reasoning.isNullOrBlank()) return null
        return LlmChatResult(
            text = text.orEmpty(),
            reasoning = reasoning,
        )
    }

    private fun extractMessageText(msg: JSONObject): String? {
        val content = msg.opt("content")
        val contentStr = when (content) {
            is String -> content
            is JSONArray -> buildString {
                for (i in 0 until content.length()) {
                    val part = content.optJSONObject(i) ?: continue
                    val type = part.optString("type")
                    when {
                        type == "text" || part.has("text") -> append(part.optString("text"))
                        type == "output_text" -> append(part.optString("text"))
                    }
                }
            }.ifBlank { null }
            JSONObject.NULL, null -> null
            else -> content.toString()
        }
        return contentStr?.let { stripThinkTags(it) }?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun extractReasoning(msg: JSONObject): String? {
        val r = msg.optString("reasoning_content").takeIf { it.isNotBlank() }
            ?: msg.optString("reasoning").takeIf { it.isNotBlank() }
        return r?.let { stripThinkTags(it) }?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun stripThinkTags(s: String): String {
        return s
            .replace(Regex("""(?s)<think>.*?</think>"""), " ")
            .replace(Regex("""(?s)<thinking>.*?</thinking>"""), " ")
            .trim()
    }

    private fun parseAnthropicContent(raw: String): LlmChatResult? {
        val root = JSONObject(raw)
        val content = root.optJSONArray("content") ?: return null
        val text = buildString {
            for (i in 0 until content.length()) {
                val block = content.optJSONObject(i) ?: continue
                if (block.optString("type") == "text") {
                    append(block.optString("text"))
                }
            }
        }.let { stripThinkTags(it) }.takeIf { it.isNotEmpty() }
        val thinking = buildString {
            for (i in 0 until content.length()) {
                val block = content.optJSONObject(i) ?: continue
                if (block.optString("type") == "thinking") {
                    append(block.optString("thinking"))
                }
            }
        }.let { stripThinkTags(it) }.takeIf { it.isNotEmpty() }
        if (text.isNullOrBlank() && thinking.isNullOrBlank()) return null
        return LlmChatResult(
            text = text.orEmpty(),
            reasoning = thinking,
        )
    }

    private fun httpError(code: Int, raw: String): String {
        val snippet = raw
            .replace(Regex("sk-[a-zA-Z0-9]{8,}"), "sk-***")
            .replace(Regex("Bearer\\s+\\S+"), "Bearer ***")
            .take(280)
        val hint = when (code) {
            401, 403 -> "鉴权失败，请检查 API Key / 区域"
            404 -> "地址或模型不存在"
            429 -> "触发限流，请稍后重试"
            in 500..599 -> "服务端错误"
            else -> "请求失败"
        }
        return "$hint (HTTP $code) $snippet"
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
