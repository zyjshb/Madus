package com.madus.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.madus.mobile.ai.LlmCapabilities
import com.madus.mobile.ai.LlmConfigStore
import com.madus.mobile.ai.LlmPresets
import com.madus.mobile.ai.LlmProtocol
import com.madus.mobile.ai.LlmStrength
import com.madus.mobile.ai.HummingConfigStore
import com.madus.mobile.ui.components.LineButton
import kotlinx.coroutines.launch

/** 配置流程：先选模式 → 再进对应表单 */
private enum class ConfigStep {
    Hub,
    Simple,
    Custom,
    Humming,
}

/**
 * 模型配置：
 * 1) 入口先选「简单模式 / 自定义」两大块（醒目）
 * 2) 再进对应 API 配置页
 */
@Composable
fun AiConfigScreen(
    store: LlmConfigStore,
    hummingStore: HummingConfigStore,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableStateOf(ConfigStep.Hub) }
    val config by store.state.collectAsState()

    when (step) {
        ConfigStep.Hub -> ConfigHubPage(
            store = store,
            hummingStore = hummingStore,
            profileCount = config.profiles.size,
            onBack = onBack,
            onSimple = { step = ConfigStep.Simple },
            onCustom = { step = ConfigStep.Custom },
            onHumming = { step = ConfigStep.Humming },
            modifier = modifier,
        )
        ConfigStep.Simple -> SimpleConfigPage(
            store = store,
            onBack = { step = ConfigStep.Hub },
            onSaved = onSaved,
            modifier = modifier,
        )
        ConfigStep.Custom -> CustomConfigPage(
            store = store,
            onBack = { step = ConfigStep.Hub },
            onSaved = onSaved,
            modifier = modifier,
        )
        ConfigStep.Humming -> HummingConfigPage(
            store = hummingStore,
            onBack = { step = ConfigStep.Hub },
            onSaved = onSaved,
            modifier = modifier,
        )
    }
}

@Composable
private fun ConfigHubPage(
    store: LlmConfigStore,
    hummingStore: HummingConfigStore,
    profileCount: Int,
    onBack: () -> Unit,
    onSimple: () -> Unit,
    onCustom: () -> Unit,
    onHumming: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val profiles = store.state.collectAsState().value.profiles
    val humCfg by hummingStore.state.collectAsState()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        ConfigTopBar(title = "AI 模型配置", onBack = onBack)
        Spacer(Modifier.height(24.dp))

        ModeCard(
            title = "简单模式",
            subtitle = "选厂商，填 Key",
            onClick = onSimple,
        )
        Spacer(Modifier.height(12.dp))
        ModeCard(
            title = "自定义模式",
            subtitle = "自己填接口",
            onClick = onCustom,
        )
        Spacer(Modifier.height(12.dp))
        ModeCard(
            title = "哼唱识别",
            subtitle = if (humCfg.isConfigured) {
                "已配置：讯飞 ${if (humCfg.xunfeiConfigured) humCfg.appId else "未填"} · " +
                    "ACR ${if (humCfg.acrConfigured) hummingStore.maskedAcrAccessKey() else "未填"}"
            } else {
                "讯飞 / ACRCloud"
            },
            onClick = onHumming,
        )

        if (profiles.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("已保存（$profileCount）", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            profiles.forEach { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${p.modelId} · ${store.maskedKey(p.id)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "删除",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable {
                            scope.launch { store.deleteProfile(p.id) }
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "进入",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SimpleConfigPage(
    store: LlmConfigStore,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var providerId by remember { mutableStateOf(LlmPresets.all.first().id) }
    var strength by remember { mutableStateOf(LlmStrength.NORMAL) }
    // 普通 / 超强各自可改模型名（切换厂商时重置为预设默认）
    var normalModel by remember { mutableStateOf(LlmPresets.all.first().normalModel) }
    var strongModel by remember { mutableStateOf(LlmPresets.all.first().strongModel) }
    var apiKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val preset = LlmPresets.byId(providerId)

    fun applyProvider(id: String) {
        providerId = id
        LlmPresets.byId(id)?.let {
            normalModel = it.normalModel
            strongModel = it.strongModel
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        ConfigTopBar(title = "简单模式", onBack = onBack)
        Spacer(Modifier.height(8.dp))
        Text(
            "预填网址与协议；模型名可改（厂商常更新 ID）。选「普通」或「超强」后填 Key 即可。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        Text("1. 选择厂商", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LlmPresets.all.forEach { p ->
            val selected = p.id == providerId
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(
                        if (selected) 2.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(2.dp),
                    )
                    .clickable { applyProvider(p.id) }
                    .padding(12.dp),
            ) {
                Text(p.displayName, style = MaterialTheme.typography.bodyLarge)
                if (p.hint.isNotBlank()) {
                    Text(
                        p.hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "URL ${p.baseUrl}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(12.dp))
        Text("2. 普通 / 超强（可改模型名）", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            LlmStrength.NORMAL.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))

        StrengthSelectable(
            title = preset?.normalLabel ?: "普通",
            selected = strength == LlmStrength.NORMAL,
            onSelect = { strength = LlmStrength.NORMAL },
        )
        Spacer(Modifier.height(4.dp))
        FieldLabel("普通 · 模型名")
        OutlineField(normalModel, { normalModel = it }, "例如 deepseek-v4-flash")
        Spacer(Modifier.height(10.dp))

        Text(
            LlmStrength.STRONG.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        StrengthSelectable(
            title = preset?.strongLabel ?: "超强",
            selected = strength == LlmStrength.STRONG,
            onSelect = { strength = LlmStrength.STRONG },
        )
        Spacer(Modifier.height(4.dp))
        FieldLabel("超强 · 模型名")
        OutlineField(strongModel, { strongModel = it }, "例如 deepseek-v4-pro")

        Spacer(Modifier.height(16.dp))
        Text("3. API Key", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        OutlineField(
            value = apiKey,
            onChange = { apiKey = it },
            placeholder = "粘贴密钥",
            password = !showKey,
        )
        Text(
            if (showKey) "隐藏密钥" else "显示密钥",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 6.dp)
                .clickable { showKey = !showKey },
        )

        val usingModel = if (strength == LlmStrength.NORMAL) normalModel else strongModel
        Spacer(Modifier.height(8.dp))
        Text(
            "将保存：${preset?.displayName ?: "?"} · ${strength.label} · $usingModel",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(20.dp))
        LineButton(
            text = if (busy) "保存中…" else "保存并使用",
            onClick = {
                if (busy) return@LineButton
                busy = true
                error = null
                scope.launch {
                    val model = if (strength == LlmStrength.NORMAL) normalModel else strongModel
                    val result = store.saveSimpleProfile(
                        providerId = providerId,
                        displayName = null,
                        apiKey = apiKey,
                        strength = strength,
                        modelIdOverride = model,
                    )
                    busy = false
                    result.onSuccess { onSaved() }
                        .onFailure { error = it.message }
                }
            },
            filled = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun CustomConfigPage(
    store: LlmConfigStore,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var customName by remember { mutableStateOf("") }
    var customBase by remember { mutableStateOf("https://api.deepseek.com") }
    var customModel by remember { mutableStateOf("") }
    var customProtocol by remember { mutableStateOf(LlmProtocol.OPENAI_COMPAT) }
    var capVision by remember { mutableStateOf(false) }
    var capAudio by remember { mutableStateOf(false) }
    var capVideo by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        ConfigTopBar(title = "自定义模式", onBack = onBack)
        Spacer(Modifier.height(8.dp))
        Text(
            "完全手填接口参数。协议选错会导致 401/404。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        FieldLabel("显示名称")
        OutlineField(customName, { customName = it }, "例如 公司中转")
        Spacer(Modifier.height(10.dp))

        FieldLabel("协议")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LlmProtocol.entries.forEach { p ->
                SelectableRow(
                    label = p.label,
                    selected = customProtocol == p,
                    onClick = { customProtocol = p },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        FieldLabel("Base URL")
        OutlineField(customBase, { customBase = it }, "https://…/v1")
        Spacer(Modifier.height(10.dp))
        FieldLabel("模型名")
        OutlineField(customModel, { customModel = it }, "model-id")
        Spacer(Modifier.height(10.dp))
        Text("能力（控制对话页是否显示录音/图片等）", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleChip("图片", capVision) { capVision = !capVision }
            ToggleChip("音频", capAudio) { capAudio = !capAudio }
            ToggleChip("视频", capVideo) { capVideo = !capVideo }
        }
        Spacer(Modifier.height(12.dp))
        FieldLabel("API Key")
        OutlineField(
            value = apiKey,
            onChange = { apiKey = it },
            placeholder = "粘贴密钥",
            password = !showKey,
        )
        Text(
            if (showKey) "隐藏密钥" else "显示密钥",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 6.dp)
                .clickable { showKey = !showKey },
        )

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(20.dp))
        LineButton(
            text = if (busy) "保存中…" else "保存并使用",
            onClick = {
                if (busy) return@LineButton
                busy = true
                error = null
                scope.launch {
                    val result = store.saveCustomProfile(
                        name = customName,
                        protocol = customProtocol,
                        baseUrl = customBase,
                        modelId = customModel,
                        apiKey = apiKey,
                        capabilities = LlmCapabilities(
                            vision = capVision,
                            audioInput = capAudio,
                            videoInput = capVideo,
                        ),
                    )
                    busy = false
                    result.onSuccess { onSaved() }
                        .onFailure { error = it.message }
                }
            },
            filled = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun HummingConfigPage(
    store: HummingConfigStore,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cfg by store.state.collectAsState()
    var appId by remember { mutableStateOf(cfg.appId) }
    var apiKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var acrHost by remember { mutableStateOf(cfg.acrHost) }
    var acrAccessKey by remember { mutableStateOf("") }
    var acrAccessSecret by remember { mutableStateOf("") }
    var showAcrSecret by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        ConfigTopBar(title = "哼唱识别", onBack = onBack)
        Spacer(Modifier.height(8.dp))
        Text(
            "讯飞：开放平台「语音扩展 → 歌曲识别」，免费 500 次。\nACRCloud 可填可不填；填了会优先用 ACRCloud 哼唱识别，识别率更好。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (cfg.isConfigured) {
            Spacer(Modifier.height(6.dp))
            Text(
                "已配置：讯飞 ${if (cfg.xunfeiConfigured) "AppID ${cfg.appId}" else "未填"} · " +
                    "ACR ${if (cfg.acrConfigured) store.maskedAcrAccessKey() else "未填"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))

        FieldLabel("AppID")
        OutlineField(appId, { appId = it }, "例如 5b8f3c2a")
        Spacer(Modifier.height(10.dp))
        FieldLabel("API Key")
        OutlineField(
            value = apiKey,
            onChange = { apiKey = it },
            placeholder = "粘贴接口密钥",
            password = !showKey,
        )
        Text(
            if (showKey) "隐藏密钥" else "显示密钥",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 6.dp)
                .clickable { showKey = !showKey },
        )

        Spacer(Modifier.height(18.dp))
        FieldLabel("ACRCloud 增强（可选）")
        FieldLabel("Host")
        OutlineField(
            value = acrHost,
            onChange = { acrHost = it },
            placeholder = "identify-cn-north-1.acrcloud.cn",
        )
        Spacer(Modifier.height(10.dp))
        FieldLabel("Access Key")
        OutlineField(
            value = acrAccessKey,
            onChange = { acrAccessKey = it },
            placeholder = "粘贴 Access Key",
            password = true,
        )
        Spacer(Modifier.height(10.dp))
        FieldLabel("Access Secret")
        OutlineField(
            value = acrAccessSecret,
            onChange = { acrAccessSecret = it },
            placeholder = "粘贴 Access Secret",
            password = !showAcrSecret,
        )
        Text(
            if (showAcrSecret) "隐藏 Secret" else "显示 Secret",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 6.dp)
                .clickable { showAcrSecret = !showAcrSecret },
        )

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(20.dp))
        LineButton(
            text = if (busy) "保存中…" else "保存并使用",
            onClick = {
                if (busy) return@LineButton
                busy = true
                error = null
                scope.launch {
                    val key = apiKey.trim().ifBlank { cfg.apiKey }
                    val host = acrHost.trim().ifBlank { cfg.acrHost }
                    val ak = acrAccessKey.trim().ifBlank { cfg.acrAccessKey }
                    val secret = acrAccessSecret.trim().ifBlank { cfg.acrAccessSecret }
                    val result = store.save(
                        appId = appId,
                        apiKey = key,
                        acrHost = host,
                        acrAccessKey = ak,
                        acrAccessSecret = secret,
                    )
                    busy = false
                    result.onSuccess { onSaved() }
                        .onFailure { error = it.message }
                }
            },
            filled = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ConfigTopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "← 返回",
            Modifier.clickable(onClick = onBack),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun StrengthSelectable(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(2.dp),
            )
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(onClick = onSelect)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (selected) "● $title" else "○ $title",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SelectableRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = if (selected) "● $label" else "○ $label",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(2.dp),
            )
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(2.dp),
            )
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun OutlineField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            inner()
        },
    )
}
