package com.madus.mobile.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 多套 LLM 配置 + API Key。
 * Key 走 EncryptedSharedPreferences；配置元数据同文件（无 Key 的 profile 列表）。
 */
class LlmConfigStore(context: Context) {
    private val app = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        runCatching { encryptedPrefs() }.getOrElse {
            // 极少数机型 Keystore 异常时降级（仍仅本机）
            app.getSharedPreferences(FALLBACK_PREFS, Context.MODE_PRIVATE)
        }
    }

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<LlmConfigState> = _state.asStateFlow()

    private fun encryptedPrefs(): SharedPreferences {
        val master = MasterKey.Builder(app)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            app,
            ENCRYPTED_PREFS,
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun reload() {
        _state.value = loadState()
    }

    private fun loadState(): LlmConfigState {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return LlmConfigState()
        return runCatching {
            val arr = JSONArray(raw)
            val list = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(profileFromJson(o))
                }
            }
            val active = prefs.getString(KEY_ACTIVE, null)
            val bgm = prefs.getString(KEY_BGM_PROFILE, null)
            LlmConfigState(profiles = list, activeProfileId = active, bgmProfileId = bgm)
        }.getOrDefault(LlmConfigState())
    }

    private fun persist(state: LlmConfigState) {
        val arr = JSONArray()
        state.profiles.forEach { arr.put(profileToJson(it)) }
        prefs.edit()
            .putString(KEY_PROFILES, arr.toString())
            .putString(KEY_ACTIVE, state.activeProfileId)
            .putString(KEY_BGM_PROFILE, state.bgmProfileId)
            .apply()
        _state.value = state
    }

    /**
     * @param modelIdOverride 简单模式下可改模型名（厂商常改 ID）；空则用预设普通/超强默认
     */
    suspend fun saveSimpleProfile(
        providerId: String,
        displayName: String?,
        apiKey: String,
        strength: LlmStrength,
        modelIdOverride: String? = null,
    ): Result<LlmProfile> = withContext(Dispatchers.IO) {
        val preset = LlmPresets.byId(providerId)
            ?: return@withContext Result.failure(IllegalArgumentException("未知厂商"))
        val key = apiKey.trim()
        if (key.isEmpty()) return@withContext Result.failure(IllegalArgumentException("请填写 API Key"))
        val model = modelIdOverride?.trim()?.takeIf { it.isNotEmpty() }
            ?: LlmPresets.modelFor(preset, strength)
        val caps = LlmPresets.capabilitiesFor(preset, strength)
        val id = UUID.randomUUID().toString()
        val profile = LlmProfile(
            id = id,
            name = displayName?.trim()?.ifEmpty { null } ?: "${preset.displayName} · ${strength.label}",
            providerId = preset.id,
            protocol = preset.protocol,
            baseUrl = preset.baseUrl.trimEnd('/'),
            modelId = model,
            strength = strength,
            isCustom = false,
            capabilities = caps,
        )
        putApiKey(id, key)
        val next = _state.value.let { cur ->
            cur.copy(
                profiles = cur.profiles + profile,
                activeProfileId = id,
            )
        }
        persist(next)
        Result.success(profile)
    }

    suspend fun saveCustomProfile(
        name: String,
        protocol: LlmProtocol,
        baseUrl: String,
        modelId: String,
        apiKey: String,
        capabilities: LlmCapabilities,
        existingId: String? = null,
    ): Result<LlmProfile> = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isEmpty() && existingId == null) {
            return@withContext Result.failure(IllegalArgumentException("请填写 API Key"))
        }
        val url = baseUrl.trim().trimEnd('/')
        if (url.isEmpty()) return@withContext Result.failure(IllegalArgumentException("请填写 Base URL"))
        val model = modelId.trim()
        if (model.isEmpty()) return@withContext Result.failure(IllegalArgumentException("请填写模型名"))
        val id = existingId ?: UUID.randomUUID().toString()
        if (key.isNotEmpty()) putApiKey(id, key)
        val profile = LlmProfile(
            id = id,
            name = name.trim().ifEmpty { "自定义 · $model" },
            providerId = "custom",
            protocol = protocol,
            baseUrl = url,
            modelId = model,
            strength = LlmStrength.NORMAL,
            isCustom = true,
            capabilities = capabilities,
        )
        val cur = _state.value
        val profiles = if (existingId != null) {
            cur.profiles.map { if (it.id == id) profile else it }
        } else {
            cur.profiles + profile
        }
        persist(cur.copy(profiles = profiles, activeProfileId = id))
        Result.success(profile)
    }

    suspend fun setActive(profileId: String) = withContext(Dispatchers.IO) {
        val cur = _state.value
        if (cur.profiles.none { it.id == profileId }) return@withContext
        persist(cur.copy(activeProfileId = profileId))
    }

    /** 设置识别 BGM 使用的模型（可与 AI 聊天 active 不同） */
    suspend fun setBgmProfile(profileId: String) = withContext(Dispatchers.IO) {
        val cur = _state.value
        if (cur.profiles.none { it.id == profileId }) return@withContext
        persist(cur.copy(bgmProfileId = profileId))
    }

    suspend fun deleteProfile(profileId: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove(keyFor(profileId)).apply()
        val cur = _state.value
        val left = cur.profiles.filterNot { it.id == profileId }
        val active = when {
            cur.activeProfileId != profileId -> cur.activeProfileId
            else -> left.firstOrNull()?.id
        }
        val bgm = when {
            cur.bgmProfileId != profileId -> cur.bgmProfileId
            else -> left.firstOrNull { it.effectiveCapabilities().audioInput }?.id
        }
        persist(cur.copy(profiles = left, activeProfileId = active, bgmProfileId = bgm))
    }

    fun getApiKey(profileId: String): String? =
        prefs.getString(keyFor(profileId), null)?.takeIf { it.isNotBlank() }

    fun hasApiKey(profileId: String): Boolean = !getApiKey(profileId).isNullOrBlank()

    fun maskedKey(profileId: String): String {
        val k = getApiKey(profileId) ?: return "未配置"
        if (k.length <= 8) return "••••"
        return k.take(4) + "…" + k.takeLast(4)
    }

    private fun putApiKey(profileId: String, key: String) {
        prefs.edit().putString(keyFor(profileId), key).apply()
    }

    private fun keyFor(profileId: String) = "key_$profileId"

    private fun profileToJson(p: LlmProfile): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("providerId", p.providerId)
        put("protocol", p.protocol.id)
        put("baseUrl", p.baseUrl)
        put("modelId", p.modelId)
        put("strength", p.strength.id)
        put("isCustom", p.isCustom)
        put("createdAt", p.createdAt)
        put("capVision", p.capabilities.vision)
        put("capAudio", p.capabilities.audioInput)
        put("capVideo", p.capabilities.videoInput)
        put("capStream", p.capabilities.streaming)
    }

    private fun profileFromJson(o: JSONObject): LlmProfile = LlmProfile(
        id = o.getString("id"),
        name = o.optString("name"),
        providerId = o.optString("providerId", "custom"),
        protocol = LlmProtocol.fromId(o.optString("protocol")),
        baseUrl = o.optString("baseUrl"),
        modelId = o.optString("modelId"),
        strength = LlmStrength.fromId(o.optString("strength")),
        isCustom = o.optBoolean("isCustom", false),
        createdAt = o.optLong("createdAt", 0L),
        capabilities = LlmCapabilities(
            vision = o.optBoolean("capVision", false),
            audioInput = o.optBoolean("capAudio", false),
            videoInput = o.optBoolean("capVideo", false),
            streaming = o.optBoolean("capStream", true),
        ),
    )

    companion object {
        private const val ENCRYPTED_PREFS = "madus_llm_secure"
        private const val FALLBACK_PREFS = "madus_llm_secure_fallback"
        private const val KEY_PROFILES = "profiles_json"
        private const val KEY_ACTIVE = "active_profile"
        private const val KEY_BGM_PROFILE = "bgm_profile_id"
    }
}
