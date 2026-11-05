package com.nahiyan.miramessenger

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun LovelyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFFF6B91),
            onPrimary = Color(0xFF2A0714),
            secondary = Color(0xFFE9A4B7),
            background = Color(0xFF100D12),
            surface = Color(0xFF1B1720),
            surfaceVariant = Color(0xFF29222D),
            onBackground = Color(0xFFF8F1F5),
            onSurface = Color(0xFFF8F1F5)
        ),
        content = content
    )
}
