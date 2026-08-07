package com.madus.mobile.ui.legal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.madus.mobile.ui.theme.appearanceTokens
import kotlinx.coroutines.launch

/**
 * 首次启动强制阅读：必须滑到协议底部，「我知道了」才可点。
 * 样式跟 [MadusTheme]（线稿/玻璃 + 当前色板），避免硬编码黑白跳戏。
 */
@Composable
fun UserAgreementScreen(
    onAccepted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val atBottom by remember {
        derivedStateOf {
            val max = scroll.maxValue
            // 内容尚未测量完时 max==0，不算到底
            max > 0 && scroll.value >= max - 48
        }
    }

    BackHandler {
        // 未同意不允许返回进主页；若已在根 Activity，用户可按 Home 离开
    }

    val colors = MaterialTheme.colorScheme
    val tokens = appearanceTokens()
    val shape = RoundedCornerShape(tokens.cornerMd)
    val btnShape = RoundedCornerShape(tokens.cornerLg.coerceAtLeast(tokens.cornerMd))

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            Text(
                text = UserAgreementText.TITLE,
                color = colors.onBackground,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 30.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = UserAgreementText.VERSION_LABEL,
                color = colors.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier
                    .border(tokens.borderWidth, colors.outline.copy(alpha = 0.45f), shape)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = UserAgreementText.INTRO,
                color = colors.onBackground.copy(alpha = 0.92f),
                fontSize = 14.sp,
                lineHeight = 22.sp,
            )

            UserAgreementText.sections.forEach { section ->
                Spacer(Modifier.height(22.dp))
                // 小节标题：线稿左边框，跟软件线框气质一致
                Text(
                    text = section.title,
                    color = colors.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 24.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = tokens.borderWidth,
                            color = colors.outline.copy(alpha = 0.55f),
                            shape = shape,
                        )
                        .background(colors.surfaceVariant.copy(alpha = 0.55f * tokens.panelAlpha), shape)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = section.body,
                    color = colors.onBackground.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                )
            }

            Spacer(Modifier.height(28.dp))
            Text(
                text = "— 正文结束，请点击下方按钮 —",
                color = colors.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface.copy(alpha = tokens.panelAlpha))
                .border(
                    width = tokens.borderWidth,
                    color = colors.outline.copy(alpha = 0.35f),
                )
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!atBottom) {
                Text(
                    text = "请先滑动阅读至协议底部",
                    color = colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Button(
                onClick = {
                    if (atBottom) {
                        onAccepted()
                    } else {
                        scope.launch {
                            scroll.animateScrollTo(scroll.maxValue)
                        }
                    }
                },
                enabled = atBottom,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(btnShape),
                shape = btnShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                    disabledContainerColor = colors.surfaceVariant,
                    disabledContentColor = colors.onSurfaceVariant.copy(alpha = 0.55f),
                ),
            ) {
                Text(
                    text = if (atBottom) "我知道了" else "请滑至底部后确认",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "点击即表示您已阅读并同意本协议",
                color = colors.onSurfaceVariant,
                fontSize = 11.sp,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
