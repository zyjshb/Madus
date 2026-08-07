package com.madus.mobile.ai

/**
 * LLM 协议族。简单模式按厂商预填；自定义可改。
 */
enum class LlmProtocol(val id: String, val label: String) {
    OPENAI_COMPAT("openai", "OpenAI 兼容 /chat/completions"),
    ANTHROPIC("anthropic", "Anthropic Messages"),
    GEMINI("gemini", "Google Gemini generateContent"),
    ;

    companion object {
        fun fromId(id: String?) = entries.find { it.id == id } ?: OPENAI_COMPAT
    }
}

/** 思考/档位：简单模式映射到不同 modelId */
enum class LlmStrength(val id: String, val label: String, val subtitle: String) {
    NORMAL(
        "normal",
        "普通",
        "更快更省：日常歌词/谐音猜歌够用",
    ),
    STRONG(
        "strong",
        "超强",
        "更慢更贵：难认谐音、半句歌词时更稳",
    ),
    ;

    companion object {
        fun fromId(id: String?) = entries.find { it.id == id } ?: NORMAL
    }
}

/**
 * 厂商能力（驱动 UI：录音/图片/音视频上传是否显示）。
 * 以公开文档为准；实际以所选 model 为准。
 */
data class LlmCapabilities(
    val text: Boolean = true,
    val vision: Boolean = false,
    val audioInput: Boolean = false,
    val videoInput: Boolean = false,
    val streaming: Boolean = true,
)

/**
 * 简单模式预置。model 名会变，见 [LlmPresets] 注释。
 */
data class LlmProviderPreset(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val protocol: LlmProtocol,
    val normalModel: String,
    val strongModel: String,
    val capabilities: LlmCapabilities,
    /** 给用户看的说明（申请 Key、区域等） */
    val hint: String = "",
    /** 普通档说明（替代笼统 flash/pro） */
    val normalLabel: String = "普通",
    val strongLabel: String = "超强",
)

/**
 * 用户保存的一套配置（可多套，对话里切换）。
 */
data class LlmProfile(
    val id: String,
    val name: String,
    val providerId: String,
    val protocol: LlmProtocol,
    val baseUrl: String,
    val modelId: String,
    val strength: LlmStrength = LlmStrength.NORMAL,
    val isCustom: Boolean = false,
    /** 能力覆盖（自定义可手调；简单模式跟 preset） */
    val capabilities: LlmCapabilities = LlmCapabilities(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun resolveModel(): String = modelId.trim()

    /** UI/发送时用：按模型名再推断一次多模态能力 */
    fun effectiveCapabilities(): LlmCapabilities =
        LlmPresets.inferCapabilities(capabilities, modelId)
}

data class LlmConfigState(
    val profiles: List<LlmProfile> = emptyList(),
    val activeProfileId: String? = null,
    /** 识别 BGM 专用模型；空则回退 active / 第一个支持音频的 */
    val bgmProfileId: String? = null,
) {
    val active: LlmProfile? get() = profiles.find { it.id == activeProfileId } ?: profiles.firstOrNull()

    val bgmProfile: LlmProfile?
        get() {
            bgmProfileId?.let { id -> profiles.find { it.id == id } }?.let { return it }
            val a = active
            if (a != null && a.effectiveCapabilities().audioInput) return a
            return profiles.firstOrNull { it.effectiveCapabilities().audioInput } ?: a
        }

    /** 支持听音频的配置（BGM 可选列表） */
    val audioProfiles: List<LlmProfile>
        get() = profiles.filter { it.effectiveCapabilities().audioInput }
}

/** 对话消息 */
sealed class AiChatMessage {
    abstract val id: String
    abstract val createdAt: Long

    data class User(
        override val id: String,
        override val createdAt: Long = System.currentTimeMillis(),
        val text: String,
    ) : AiChatMessage()

    data class Assistant(
        override val id: String,
        override val createdAt: Long = System.currentTimeMillis(),
        val text: String,
        val candidates: List<SongCandidate> = emptyList(),
        val tracks: List<com.madus.mobile.domain.Track> = emptyList(),
        val isStreaming: Boolean = false,
        val error: String? = null,
        /** 模型思考/推理过程（reasoning_content / thinking 块） */
        val thinking: String? = null,
        /** 模型从音频里转写出的歌词/旋律描述 */
        val lyricsHeard: String? = null,
        /** 模型原始输出，便于在“模型过程”里复查 */
        val modelRaw: String? = null,
    ) : AiChatMessage()

    data class SystemNote(
        override val id: String,
        override val createdAt: Long = System.currentTimeMillis(),
        val text: String,
    ) : AiChatMessage()
}

/** LLM 解析出的候选歌 */
data class SongCandidate(
    val title: String,
    val artist: String? = null,
    val confidence: Float? = null,
    val bilibiliQuery: String,
    val note: String? = null,
)
