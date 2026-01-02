package org.krypton.ui.study

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
actual fun DeleteIcon(
    contentDescription: String?,
    modifier: Modifier,
    tint: Color?
) {
    Icon(
        imageVector = Icons.Filled.Close,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint ?: Color.Unspecified
    )
}

