package org.krypton.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Desktop implementation of MainScaffold.
 * 
 * Overlays (left/right ribbons) are docked and always visible.
 */
@Composable
actual fun PlatformMainScaffold(
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    leftOverlay: @Composable () -> Unit,
    rightOverlay: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Top bar
        topBar()
        
        // Middle row: Left overlay, content, right overlay
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Left overlay (docked)
            leftOverlay()
            
            // Main content
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
            
            // Right overlay (docked)
            rightOverlay()
        }
        
        // Bottom bar
        bottomBar()
    }
}

