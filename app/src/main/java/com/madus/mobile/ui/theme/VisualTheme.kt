package com.madus.mobile.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.madus.mobile.data.VisualTheme

val LocalVisualTheme = staticCompositionLocalOf { VisualTheme.Classic }

@Composable
fun isLiquidTheme(): Boolean = LocalVisualTheme.current == VisualTheme.Canvas
