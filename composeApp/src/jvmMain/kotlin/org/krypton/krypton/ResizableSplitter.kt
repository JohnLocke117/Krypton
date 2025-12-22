package org.krypton.krypton

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ResizableSplitter(
    onDrag: (Float) -> Unit,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(4.dp)
            .background(
                if (isDragging) {
                    theme.Accent.copy(alpha = 0.5f)
                } else {
                    Color.Transparent
                }
            )
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDrag = { change, dragAmount ->
                        onDrag(dragAmount.x)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(32.dp)
                .background(
                    if (isDragging) {
                        theme.Accent
                    } else {
                        theme.Border.copy(alpha = 0.5f)
                    },
                    shape = RoundedCornerShape(1.dp)
                )
        )
    }
}

