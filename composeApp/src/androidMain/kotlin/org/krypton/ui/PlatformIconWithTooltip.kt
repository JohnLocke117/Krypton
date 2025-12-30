package org.krypton.ui

import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Android implementation of PlatformIconWithTooltip.
 * Uses Material Icons and a simple IconButton.
 */
@Composable
actual fun PlatformIconWithTooltip(
    tooltip: String,
    modifier: Modifier,
    enabled: Boolean,
    position: TooltipPosition,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        content()
    }
}

