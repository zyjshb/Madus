package com.madus.mobile.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.madus.mobile.data.NetworkIntensity
import com.madus.mobile.data.PlayerSettings
import com.madus.mobile.data.SoundFx
import com.madus.mobile.ui.components.SectionTitle
import com.madus.mobile.ui.liquid.LiquidPageHeader
import com.madus.mobile.ui.theme.appearanceTokens
import com.madus.mobile.ui.theme.isLiquidTheme

/**
 * 播放设置：音效、网络档位、边听缓存等。
 * 音质/定时在播放器界面快捷切换，这里也可从「我的」进入。
 */
@Composable
fun PlaybackPrefsScreen(
    playerSettings: PlayerSettings,
    cacheLabel: String = "0B",
    onBack: () -> Unit,
    onSoundFx: (SoundFx) -> Unit,
    onAutoCache: (Boolean) -> Unit,
    onGameMixAudio: (Boolean) -> Unit = {},
    onGameLiteMode: (Boolean) -> Unit = {},
    onNetworkIntensity: (NetworkIntensity) -> Unit = {},
    onOpenCacheManager: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tokens = appearanceTokens()

    Column(modifier = modifier.fillMaxSize()) {
        if (isLiquidTheme()) {
            LiquidPageHeader(
                title = "播放",
                subtitle = "音质 / 定时在播放页点",
                onBack = onBack,
            )
        } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column {
                Text("播放设置", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "音质 / 定时请在播放页点选",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                SectionTitle(text = "网络使用")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "只调预取和后台续刷，不关点播/切歌/歌单。打游戏卡网选「最省」。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                NetworkIntensity.entries.forEach { level ->
                    SelectRow(
                        title = level.label,
                        subtitle = level.subtitle,
                        selected = playerSettings.networkIntensity == level,
                        onClick = { onNetworkIntensity(level) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            item {
                SectionTitle(text = "边听缓存")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "关闭后纯在线播放，不写磁盘。「最省」档即使开启也不会后台写盘。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(tokens.cornerMd))
                        .border(tokens.borderWidth, MaterialTheme.colorScheme.outline, RoundedCornerShape(tokens.cornerMd))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("自动边听缓存", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (playerSettings.autoCache) "已开启" else "已关闭 · 推荐轻量",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = playerSettings.autoCache,
                        onCheckedChange = onAutoCache,
                    )
                }
            }

            item {
                SectionTitle(text = "打游戏时听歌")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "游戏音效常会抢焦点；开启后不因此暂停。抢网请到上方选「最省」。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(tokens.cornerMd))
                        .border(tokens.borderWidth, MaterialTheme.colorScheme.outline, RoundedCornerShape(tokens.cornerMd))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("打游戏时继续播放", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (playerSettings.gameMixAudio) {
                                "已开启 · 忽略游戏音效抢焦点"
                            } else {
                                "已关闭 · 按系统焦点可能暂停"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = playerSettings.gameMixAudio,
                        onCheckedChange = onGameMixAudio,
                    )
                }
            }

            item {
                SectionTitle(text = "环境音效")
                Spacer(Modifier.height(6.dp))
                SoundFx.entries.forEach { fx ->
                    SelectRow(
                        title = fx.label,
                        subtitle = fx.subtitle,
                        selected = playerSettings.soundFx == fx,
                        onClick = { onSoundFx(fx) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            item {
                SectionTitle(text = "缓存管理")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "当前约 $cacheLabel。可查看已缓存歌曲并逐首删除。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                SelectRow(
                    title = "打开缓存管理",
                    subtitle = "查看 / 删除缓存内容",
                    selected = false,
                    onClick = onOpenCacheManager,
                )
            }

            item { Spacer(Modifier.height(48.dp)) }
        }
    }
}

@Composable
private fun SelectRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = appearanceTokens()
    val shape = RoundedCornerShape(tokens.cornerMd)
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(tokens.borderWidth, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Text("●", color = MaterialTheme.colorScheme.primary)
        }
    }
}
