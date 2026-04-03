package com.range.rangeEmulator.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun RangeEmulatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedColor: Color = Color(0xFF6750A4),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> {
            darkColorScheme(
                primary = seedColor,
                onPrimary = Color.Black,
                surface = Color(0xFF121212),
                background = Color.Black
            )
        }
        else -> {
            lightColorScheme(
                primary = seedColor,
                onPrimary = Color.White,
                surface = Color(0xFFFCFDF6),
                background = Color.White
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}