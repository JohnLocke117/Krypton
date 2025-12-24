package org.krypton.krypton

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ResizableSplitter(
    onDrag: (Float) -> Unit,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier,
    onDragStart: (() -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null
) {
    var isDragging by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    // Show divider only when hovered or dragging
    val shouldShowDivider = isHovered || isDragging
    val dividerColor = if (shouldShowDivider) {
        theme.Accent
    } else {
        Color.Transparent
    }
    
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(4.dp)
            .hoverable(interactionSource = interactionSource)
            .background(dividerColor)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { 
                        isDragging = true
                        onDragStart?.invoke()
                    },
                    onDragEnd = { 
                        isDragging = false
                        onDragEnd?.invoke()
                    },
                    onDrag = { change, dragAmount ->
                        onDrag(dragAmount.x)
                    }
                )
            }
    )
}

