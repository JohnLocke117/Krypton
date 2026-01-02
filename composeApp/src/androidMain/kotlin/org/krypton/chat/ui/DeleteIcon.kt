package org.krypton.chat.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
actual fun DeleteIcon(
    contentDescription: String,
    tint: Color,
    modifier: Modifier
) {
    Icon(
        imageVector = Icons.Default.Delete,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
    )
}

