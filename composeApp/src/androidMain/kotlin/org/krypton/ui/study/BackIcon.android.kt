package org.krypton.ui.study

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
actual fun BackIcon(
    contentDescription: String?,
    modifier: Modifier,
    tint: Color?
) {
    Icon(
        imageVector = Icons.Filled.ArrowBack,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint ?: Color.Unspecified
    )
}

