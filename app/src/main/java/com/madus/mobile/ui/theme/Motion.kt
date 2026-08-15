package com.madus.mobile.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import com.madus.mobile.ui.navigation.RootTab
import com.madus.mobile.ui.navigation.Routes

/**
 * iOS 手感：减速曲线、轻弹簧、按压缩一点。
 * 不弹、不炫，只让进出页和点按跟手。
 */
object MadusMotion {
    val iosEase = CubicBezierEasing(0.22f, 1.00f, 0.36f, 1.00f)

    val fade = tween<Float>(durationMillis = 260, easing = iosEase)
    val tabFade = tween<Float>(durationMillis = 200, easing = iosEase)
    val color = tween<androidx.compose.ui.graphics.Color>(durationMillis = 220, easing = iosEase)

    val press = spring<Float>(
        dampingRatio = 0.78f,
        stiffness = 520f,
    )

    val pageSlide = spring(
        dampingRatio = 0.92f,
        stiffness = 380f,
        visibilityThreshold = IntOffset.VisibilityThreshold,
    )

    val sheetSlide = spring(
        dampingRatio = 0.90f,
        stiffness = 280f,
        visibilityThreshold = IntOffset.VisibilityThreshold,
    )

    fun tabIn(): EnterTransition = fadeIn(tabFade)

    fun tabOut(): ExitTransition = fadeOut(tabFade)

    fun pushIn(): EnterTransition =
        slideInHorizontally(pageSlide) { it } + fadeIn(fade)

    fun pushOut(): ExitTransition =
        slideOutHorizontally(pageSlide) { -it / 3 } + fadeOut(tween(200, easing = iosEase))

    fun popIn(): EnterTransition =
        slideInHorizontally(pageSlide) { -it / 3 } + fadeIn(fade)

    fun popOut(): ExitTransition =
        slideOutHorizontally(pageSlide) { it } + fadeOut(tween(220, easing = iosEase))

    fun sheetIn(): EnterTransition =
        slideInVertically(sheetSlide) { it } + fadeIn(tween(240, easing = iosEase))

    fun sheetOut(): ExitTransition =
        slideOutVertically(sheetSlide) { it } + fadeOut(tween(220, easing = iosEase))

    fun immersiveIn(): EnterTransition = fadeIn(tabFade)

    fun immersiveOut(): ExitTransition = fadeOut(tabFade)

    fun enterFor(from: String?, to: String?): EnterTransition = when {
        to == Routes.NOW_PLAYING -> sheetIn()
        to == Routes.FULLSCREEN_VIDEO -> immersiveIn()
        isRootTab(from) && isRootTab(to) -> tabIn()
        else -> pushIn()
    }

    fun exitFor(from: String?, to: String?): ExitTransition = when {
        from == Routes.NOW_PLAYING -> sheetOut()
        to == Routes.FULLSCREEN_VIDEO || from == Routes.FULLSCREEN_VIDEO -> immersiveOut()
        isRootTab(from) && isRootTab(to) -> tabOut()
        else -> pushOut()
    }

    fun popEnterFor(from: String?, to: String?): EnterTransition = when {
        from == Routes.NOW_PLAYING || from == Routes.FULLSCREEN_VIDEO -> fadeIn(fade)
        isRootTab(from) && isRootTab(to) -> tabIn()
        else -> popIn()
    }

    fun popExitFor(from: String?, to: String?): ExitTransition = when {
        from == Routes.NOW_PLAYING -> sheetOut()
        from == Routes.FULLSCREEN_VIDEO -> immersiveOut()
        isRootTab(from) && isRootTab(to) -> tabOut()
        else -> popOut()
    }

    private fun isRootTab(route: String?) = route != null && route in RootTab.routes
}

@Composable
fun Modifier.iosClickable(
    enabled: Boolean = true,
    pressScale: Float = 0.97f,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressScale else 1f,
        animationSpec = MadusMotion.press,
        label = "iosPress",
    )
    val liquid = isLiquidTheme()
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = source,
            indication = if (liquid) null else LocalIndication.current,
            enabled = enabled,
            onClick = onClick,
        )
}
