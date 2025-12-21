package org.krypton.krypton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Composable
fun ResizableSplitter(
    onDrag: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(4.dp)
            .background(
                if (isDragging) ObsidianTheme.Accent.copy(alpha = 0.5f) else Color.Transparent
            )
            .pointerInput(Unit) {
                // Simple drag handling - Compose Desktop will handle this
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(32.dp)
                .background(
                    if (isDragging) ObsidianTheme.Accent else ObsidianTheme.Border,
                    shape = RoundedCornerShape(1.dp)
                )
        )
    }
}

