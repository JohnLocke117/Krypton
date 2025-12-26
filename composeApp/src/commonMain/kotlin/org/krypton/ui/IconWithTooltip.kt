package org.krypton.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Enum to indicate tooltip placement relative to the icon.
 */
enum class TooltipPosition {
    /**
     * Tooltip appears above the icon with arrow pointing down.
     * Use for icons in bottom toolbars.
     */
    ABOVE,
    
    /**
     * Tooltip appears below the icon with arrow pointing up.
     * Use for icons in top toolbars/app bars.
     */
    BELOW
}

/**
 * Common multiplatform API for icons with tooltips.
 * 
 * This composable provides a stable API that delegates to platform-specific implementations.
 * All call sites should use this instead of platform-specific tooltip APIs.
 * 
 * @param tooltip The tooltip text to display
 * @param modifier Modifier to apply to the icon button
 * @param enabled Whether the icon button is enabled
 * @param position Tooltip position relative to the icon (default: ABOVE)
 * @param onClick Click handler for the icon button
 * @param content The icon content (usually an Icon or Image composable)
 */
@Composable
fun AppIconWithTooltip(
    tooltip: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    position: TooltipPosition = TooltipPosition.ABOVE,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit
) {
    PlatformIconWithTooltip(
        tooltip = tooltip,
        modifier = modifier,
        enabled = enabled,
        position = position,
        onClick = onClick,
        content = content
    )
}

/**
 * Platform-specific implementation of icon with tooltip.
 * 
 * Desktop: Uses TooltipArea with custom styled tooltip bubble.
 * Android: Will use Material3 TooltipBox when implemented.
 */
@Composable
expect fun PlatformIconWithTooltip(
    tooltip: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    position: TooltipPosition = TooltipPosition.ABOVE,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit
)

