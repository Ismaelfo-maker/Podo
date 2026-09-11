package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.data.AppThemeStyle

@Composable
fun StepCounterTheme(
    themeStyle: AppThemeStyle = AppThemeStyle.OCEAN_BLUE,
    content: @Composable () -> Unit
) {
    val primary = Color(themeStyle.primaryColorHex)
    val secondary = Color(themeStyle.secondaryColorHex)

    val colorScheme = if (themeStyle.isDarkOled) {
        darkColorScheme(
            primary = primary,
            secondary = secondary,
            background = Color.Black,
            surface = Color(0xFF121212),
            onBackground = Color.White,
            onSurface = Color.White,
            surfaceVariant = Color(0xFF1E1E1E)
        )
    } else {
        darkColorScheme(
            primary = primary,
            secondary = secondary,
            background = Color(0xFF12141C),
            surface = Color(0xFF1E2230),
            onBackground = Color(0xFFF0F2F8),
            onSurface = Color(0xFFF0F2F8),
            surfaceVariant = Color(0xFF282D3F),
            primaryContainer = primary.copy(alpha = 0.2f),
            onPrimaryContainer = primary
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
