package org.krypton.krypton.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Desktop implementation of icon with tooltip using Compose Desktop's TooltipArea.
 * The tooltip is visually offset using graphicsLayer to appear above or below the icon.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
actual fun PlatformIconWithTooltip(
    tooltip: String,
    modifier: Modifier,
    enabled: Boolean,
    position: TooltipPosition,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        TooltipArea(
            tooltip = {
                DesktopTooltipBubble(
                    text = tooltip,
                    position = position
                )
            }
        ) {
            IconButton(
                onClick = onClick,
                enabled = enabled
            ) {
                content()
            }
        }
    }
}

/**
 * Custom styled tooltip bubble for desktop.
 * 
 * Features:
 * - Dark background matching app theme
 * - White text
 * - Rounded corners
 * - Shadow elevation
 * - Arrow pointing to icon (direction depends on position)
 * - Visually offset using graphicsLayer to appear above or below icon
 */
@Composable
private fun DesktopTooltipBubble(
    text: String,
    position: TooltipPosition
) {
    val bubbleColor = Color(0xFF11121A) // Dark background matching app theme
    val verticalOffset = 32.dp // Offset to position tooltip above or below icon
    
    Box(
        modifier = Modifier.graphicsLayer {
            translationY = when (position) {
                TooltipPosition.ABOVE -> -verticalOffset.toPx()
                TooltipPosition.BELOW -> verticalOffset.toPx()
            }
        }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (position == TooltipPosition.BELOW) {
                ArrowUp(bubbleColor)
                Spacer(modifier = Modifier.height(2.dp))
            }
            
            Surface(
                color = bubbleColor,
                shape = RoundedCornerShape(6.dp),
                shadowElevation = 8.dp
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            if (position == TooltipPosition.ABOVE) {
                Spacer(modifier = Modifier.height(2.dp))
                ArrowDown(bubbleColor)
            }
        }
    }
}

/**
 * Arrow pointing down (for tooltips above icons).
 */
@Composable
private fun ArrowDown(color: Color) {
    Canvas(
        modifier = Modifier.size(10.dp)
    ) {
        val path = Path().apply {
            // Arrow pointing down (tooltip above icon)
            moveTo(size.width / 2f, size.height)  // bottom center (tip)
            lineTo(0f, 0f)                        // top left
            lineTo(size.width, 0f)                // top right
            close()
        }
        drawPath(
            path = path,
            color = color
        )
    }
}

/**
 * Arrow pointing up (for tooltips below icons).
 */
@Composable
private fun ArrowUp(color: Color) {
    Canvas(
        modifier = Modifier.size(10.dp)
    ) {
        val path = Path().apply {
            // Arrow pointing up (tooltip below icon)
            moveTo(0f, size.height)               // bottom left
            lineTo(size.width, size.height)        // bottom right
            lineTo(size.width / 2f, 0f)           // top center (tip)
            close()
        }
        drawPath(
            path = path,
            color = color
        )
    }
}

