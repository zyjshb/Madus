package com.madus.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.madus.mobile.data.VideoGestureMode

/**
 * 短视频新手指引：按当前操作模式展示不同说明。
 * 必须点「我知道了」才关。
 */
@Composable
fun ShortVideoGuideOverlay(
    onDismiss: () -> Unit,
    gestureMode: VideoGestureMode = VideoGestureMode.DOUYIN,
    modifier: Modifier = Modifier,
) {
    val lines = guideLinesFor(gestureMode)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f)),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1A1A1A))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .clickable(enabled = false, onClick = {})
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "${gestureMode.label}模式 · 手势说明",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "可在「设置 → 短视频操作模式」随时切换",
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            lines.forEach { line ->
                GuideLine(
                    icon = {
                        when (line.icon) {
                            GuideIcon.TAP -> Icon(
                                Icons.Default.TouchApp, null,
                                tint = Color.White, modifier = Modifier.size(22.dp),
                            )
                            GuideIcon.LIKE -> Icon(
                                Icons.Default.Favorite, null,
                                tint = Color(0xFFFF2D55), modifier = Modifier.size(22.dp),
                            )
                            GuideIcon.SWIPE -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.KeyboardArrowUp, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            GuideIcon.SPEED -> Text(
                                "2x", color = Color.White,
                                fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            )
                            GuideIcon.MENU -> Text(
                                "···", color = Color.White, fontWeight = FontWeight.Bold,
                            )
                        }
                    },
                    text = line.text,
                )
            }
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("我知道了", color = Color.Black, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private enum class GuideIcon { TAP, LIKE, SWIPE, SPEED, MENU }

private data class GuideLineData(val icon: GuideIcon, val text: String)

private fun guideLinesFor(mode: VideoGestureMode): List<GuideLineData> = when (mode) {
    VideoGestureMode.DOUYIN -> listOf(
        GuideLineData(GuideIcon.TAP, "单击  暂停 / 继续播放"),
        GuideLineData(GuideIcon.LIKE, "双击  点赞（落点红心）"),
        GuideLineData(GuideIcon.SWIPE, "上下滑  切换视频"),
        GuideLineData(GuideIcon.SPEED, "角上长按  临时 2 倍速（松手恢复）"),
        GuideLineData(GuideIcon.MENU, "中部长按  更多 / 固定倍速"),
    )
    VideoGestureMode.BILIBILI -> listOf(
        GuideLineData(GuideIcon.TAP, "单击  清屏 / 显示控件（不暂停）"),
        GuideLineData(GuideIcon.TAP, "双击  暂停 / 继续（B站默认）"),
        GuideLineData(GuideIcon.SPEED, "下半屏长按  临时 2 倍速"),
        GuideLineData(GuideIcon.MENU, "上半屏长按  菜单（固定倍速等）"),
        GuideLineData(GuideIcon.SWIPE, "上下滑  切换视频"),
        GuideLineData(GuideIcon.LIKE, "点赞  点右侧按钮"),
    )
    VideoGestureMode.KUAISHOU -> listOf(
        GuideLineData(GuideIcon.TAP, "单击  暂停 / 继续播放"),
        GuideLineData(GuideIcon.LIKE, "双击  点赞"),
        GuideLineData(GuideIcon.MENU, "长按  出菜单（含 0.75～10 倍速）"),
        GuideLineData(GuideIcon.SWIPE, "上下滑  切换视频"),
        GuideLineData(GuideIcon.SPEED, "倍速  在长按菜单里选固定档"),
    )
}

@Composable
private fun GuideLine(
    icon: @Composable () -> Unit,
    text: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Text(text, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp)
    }
}
