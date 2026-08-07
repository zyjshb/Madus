package com.madus.mobile.ai

/**
 * 简单模式厂商预填（Base URL / 协议 / 普通·超强模型）。
 *
 * 模型 ID 会随厂商迭代变更；发版前可对照官方 /v1/models。
 * 调研基准：2026-08（DeepSeek V4、MiMo v2.5、Kimi K3 等）。
 */
object LlmPresets {

    val all: List<LlmProviderPreset> = listOf(
        LlmProviderPreset(
            id = "deepseek",
            displayName = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            protocol = LlmProtocol.OPENAI_COMPAT,
            normalModel = "deepseek-v4-flash",
            strongModel = "deepseek-v4-pro",
            capabilities = LlmCapabilities(vision = false, audioInput = false),
            hint = "控制台申请 Key。纯文本；前缀自动缓存友好。",
            normalLabel = "普通 · Flash（快/省）",
            strongLabel = "超强 · Pro（更准/更贵）",
        ),
        LlmProviderPreset(
            id = "mimo",
            displayName = "小米 MiMo",
            baseUrl = "https://api.xiaomimimo.com/v1",
            protocol = LlmProtocol.OPENAI_COMPAT,
            // 两档均开多模态能力；超强仍用 v2.5 听音频（Pro 主攻文本时由 capabilities 保留入口）
            normalModel = "mimo-v2.5",
            strongModel = "mimo-v2.5-pro",
            capabilities = LlmCapabilities(vision = true, audioInput = true, videoInput = false),
            hint = "支持上传音频/图片。Key 可用 Bearer 或 api-key；听音轨优先 v2.5。",
            normalLabel = "普通 · v2.5 多模态",
            strongLabel = "超强 · v2.5 Pro（文本更强，也支持附件）",
        ),
        LlmProviderPreset(
            id = "qwen",
            displayName = "通义千问 Qwen",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            protocol = LlmProtocol.OPENAI_COMPAT,
            normalModel = "qwen-flash",
            strongModel = "qwen-max",
            capabilities = LlmCapabilities(vision = false, audioInput = false),
            hint = "国内 DashScope 兼容模式。音频请自定义换成 qwen3-omni-* 等 Omni 模型。",
            normalLabel = "普通 · Flash/Turbo 档",
            strongLabel = "超强 · Max 档",
        ),
        LlmProviderPreset(
            id = "qwen_omni",
            displayName = "通义千问 Omni（音频）",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            protocol = LlmProtocol.OPENAI_COMPAT,
            normalModel = "qwen3-omni-flash",
            strongModel = "qwen3.5-omni-plus",
            capabilities = LlmCapabilities(vision = true, audioInput = true, videoInput = true),
            hint = "带音频/视频理解的 Omni 线，适合上传视频识歌；模型名以百炼控制台为准。",
            normalLabel = "普通 · Omni Flash",
            strongLabel = "超强 · Omni Plus",
        ),
        LlmProviderPreset(
            id = "kimi",
            displayName = "Kimi 月之暗面",
            baseUrl = "https://api.moonshot.cn/v1",
            protocol = LlmProtocol.OPENAI_COMPAT,
            normalModel = "kimi-k3",
            strongModel = "kimi-k3",
            capabilities = LlmCapabilities(vision = true, audioInput = false),
            hint = "国内 api.moonshot.cn；国际可用 api.moonshot.ai。",
            normalLabel = "普通 · K3",
            strongLabel = "超强 · K3（同模，可开思考）",
        ),
        LlmProviderPreset(
            id = "openai",
            displayName = "ChatGPT / OpenAI",
            baseUrl = "https://api.openai.com/v1",
            protocol = LlmProtocol.OPENAI_COMPAT,
            normalModel = "gpt-4o-mini",
            strongModel = "gpt-4o",
            // 4o 系识图；音频需专用模型，自定义里可开
            capabilities = LlmCapabilities(vision = true, audioInput = false, videoInput = false),
            hint = "支持图片。上传音频识歌需自定义换成 gpt-4o-audio 等并勾选音频。",
            normalLabel = "普通 · 4o-mini（识图）",
            strongLabel = "超强 · 4o（识图）",
        ),
        LlmProviderPreset(
            id = "grok",
            displayName = "Grok (xAI)",
            baseUrl = "https://api.x.ai/v1",
            protocol = LlmProtocol.OPENAI_COMPAT,
            normalModel = "grok-4.5",
            strongModel = "grok-4.5",
            capabilities = LlmCapabilities(vision = true, audioInput = false),
            hint = "OpenAI 兼容 chat/completions。控制台：console.x.ai",
            normalLabel = "普通 · grok-4.5",
            strongLabel = "超强 · grok-4.5",
        ),
        LlmProviderPreset(
            id = "claude",
            displayName = "Claude (Anthropic)",
            baseUrl = "https://api.anthropic.com",
            protocol = LlmProtocol.ANTHROPIC,
            normalModel = "claude-haiku-4-5-20251001",
            strongModel = "claude-sonnet-4-5-20250929",
            capabilities = LlmCapabilities(vision = true, audioInput = false),
            hint = "Messages API（非 OpenAI 形）。模型 ID 以 Anthropic 控制台为准。",
            normalLabel = "普通 · Haiku 档",
            strongLabel = "超强 · Sonnet 档",
        ),
        LlmProviderPreset(
            id = "gemini",
            displayName = "Gemini (Google)",
            // OpenAI 兼容 shim，少写一套请求体
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            protocol = LlmProtocol.OPENAI_COMPAT,
            normalModel = "gemini-2.5-flash",
            strongModel = "gemini-2.5-pro",
            capabilities = LlmCapabilities(vision = true, audioInput = true, videoInput = true),
            hint = "走 Google OpenAI 兼容端；Key 为 Google AI Studio。",
            normalLabel = "普通 · Flash",
            strongLabel = "超强 · Pro",
        ),
        LlmProviderPreset(
            id = "zhipu",
            displayName = "智谱 GLM",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            protocol = LlmProtocol.OPENAI_COMPAT,
            normalModel = "glm-4-flash",
            strongModel = "glm-4.5",
            capabilities = LlmCapabilities(vision = false, audioInput = false),
            hint = "国内 OpenAI 兼容 paas/v4。",
            normalLabel = "普通 · Flash",
            strongLabel = "超强 · 4.5+",
        ),
    )

    fun byId(id: String): LlmProviderPreset? = all.find { it.id == id }

    fun modelFor(preset: LlmProviderPreset, strength: LlmStrength): String =
        when (strength) {
            LlmStrength.NORMAL -> preset.normalModel
            LlmStrength.STRONG -> preset.strongModel
        }

    fun capabilitiesFor(preset: LlmProviderPreset, strength: LlmStrength): LlmCapabilities {
        // 两档都跟预设能力走；再按模型名纠一次（防漏）
        return inferCapabilities(preset.capabilities, modelFor(preset, strength))
    }

    /**
     * 根据模型名推断多模态能力（自定义/改名后仍尽量显示对的按钮）。
     */
    fun inferCapabilities(base: LlmCapabilities, modelId: String): LlmCapabilities {
        val m = modelId.lowercase()
        var vision = base.vision
        var audio = base.audioInput
        var video = base.videoInput
        if (m.contains("omni") || m.contains("gemini") || m.contains("gpt-4o") ||
            m.contains("claude") || m.contains("kimi") || m.contains("vl")
        ) {
            vision = true
        }
        // MiMo：v2.5 / omni 听音频；仅 -pro 且无 omni 时也保留音频入口（API 若拒再提示）
        if (m.contains("mimo") || m.contains("omni") || m.contains("audio") ||
            m.contains("gemini") || m.contains("whisper")
        ) {
            audio = true
        }
        if (m.contains("omni") || m.contains("gemini")) {
            video = true
        }
        // 纯文本常见
        if (m.contains("deepseek") && !m.contains("vl") ||
            m.contains("glm-4-flash") ||
            (m.contains("qwen") && !m.contains("omni") && !m.contains("vl"))
        ) {
            // 不强行关，以 base 为准；deepseek 明确无音频
            if (m.contains("deepseek")) {
                vision = false
                audio = false
                video = false
            }
        }
        return base.copy(vision = vision, audioInput = audio, videoInput = video)
    }

    /** 听音频时若 Pro 不支持，可回退到同厂多模态模型 id */
    fun audioModelOverride(profile: LlmProfile): String? {
        if (profile.providerId == "mimo" &&
            profile.modelId.contains("pro", ignoreCase = true) &&
            !profile.modelId.contains("omni", ignoreCase = true)
        ) {
            return "mimo-v2.5"
        }
        return null
    }
}
