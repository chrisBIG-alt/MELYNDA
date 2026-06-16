package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MelyndaColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = DeepSpace,
    secondary = SoftCyan,
    background = DeepSpace,
    surface = SlateOverlay,
    onBackground = TerminalStdout,
    onSurface = TerminalStdout,
    error = AlertOrange
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MelyndaColorScheme,
        typography = Typography,
        content = content
    )
}
