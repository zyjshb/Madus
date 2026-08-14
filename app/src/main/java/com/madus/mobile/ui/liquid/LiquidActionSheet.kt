package com.madus.mobile.ui.liquid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.madus.mobile.ui.theme.liquidTokens

data class LiquidSheetAction(
    val title: String,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * iOS 操作表：蒙层 + 一组操作 + 单独「取消」。
 * 盖在整页上，不把播放台顶上去。
 */
@Composable
fun LiquidActionSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    actions: List<LiquidSheetAction>,
    modifier: Modifier = Modifier,
) {
    val accent = liquidTokens().accent
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f))
                    .clickable(onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = 96.dp),
            enter = slideInVertically(
                animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
                initialOffsetY = { it },
            ) + fadeIn(),
            exit = slideOutVertically(
                animationSpec = spring(dampingRatio = 0.92f, stiffness = Spring.StiffnessMedium),
                targetOffsetY = { it },
            ) + fadeOut(),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column {
                        actions.forEachIndexed { i, action ->
                            Text(
                                action.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Normal,
                                ),
                                color = when {
                                    !action.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                    action.destructive -> Color(0xFFFF3B30)
                                    else -> accent
                                },
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = action.enabled) {
                                        onDismiss()
                                        action.onClick()
                                    }
                                    .padding(vertical = 16.dp),
                            )
                            if (i != actions.lastIndex) {
                                HorizontalDivider(
                                    thickness = 0.4.dp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    onClick = onDismiss,
                    contentPadding = 0.dp,
                ) {
                    Text(
                        "取消",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = accent,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
            }
        }
    }
}
