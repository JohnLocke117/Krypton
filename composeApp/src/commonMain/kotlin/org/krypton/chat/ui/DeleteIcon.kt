package org.krypton.chat.ui

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Platform-specific delete icon.
 * Desktop uses SVG, Android uses Material Icons.
 */
@Composable
expect fun DeleteIcon(
    contentDescription: String = "Delete",
    tint: Color,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
)

