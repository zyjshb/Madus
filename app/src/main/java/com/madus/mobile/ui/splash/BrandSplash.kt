package com.madus.mobile.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.madus.mobile.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 与 h_logo 底色一致，避免开屏出现「灰方框」 */
private val SplashBg = Color(0xFF1F2121)

/**
 * 品牌开屏：新蛇标 + 播放键。
 * 渐入并轻微放大 → 定格 → 渐出。
 */
@Composable
fun BrandSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.88f) }

    LaunchedEffect(Unit) {
        launch {
            alpha.animateTo(1f, tween(durationMillis = 520, easing = FastOutSlowInEasing))
        }
        scale.animateTo(1f, tween(durationMillis = 640, easing = FastOutSlowInEasing))
        delay(780)
        alpha.animateTo(0f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SplashBg),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_madus),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(220.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .alpha(alpha.value),
        )
    }
}
