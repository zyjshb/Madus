package com.madus.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.madus.mobile.data.AppearanceMode
import com.madus.mobile.data.ColorTheme

@Composable
fun MadusTheme(
    appearance: AppearanceMode = AppearanceMode.LineSketch,
    colorTheme: ColorTheme = ColorTheme.LineSketchMono,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val tokens = AppearanceTokens.forMode(appearance)
    val scheme = colorSchemeFor(colorTheme, darkTheme)
    CompositionLocalProvider(LocalAppearance provides tokens) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MadusTypography,
            content = content,
        )
    }
}
