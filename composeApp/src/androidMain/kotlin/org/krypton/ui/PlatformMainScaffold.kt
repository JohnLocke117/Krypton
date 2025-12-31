package org.krypton.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

/**
 * Android implementation of MainScaffold.
 * 
 * Overlays (left/right ribbons) are slide-in drawers using ModalNavigationDrawer.
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
    val leftDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val rightDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    // Toggle functions exposed via CompositionLocal
    val leftToggle: () -> Unit = {
        scope.launch {
            if (leftDrawerState.isOpen) {
                leftDrawerState.close()
            } else {
                leftDrawerState.open()
            }
        }
    }
    
    val rightToggle: () -> Unit = {
        scope.launch {
            if (rightDrawerState.isOpen) {
                rightDrawerState.close()
            } else {
                rightDrawerState.open()
            }
        }
    }
    
    // Handle back button - close drawers before exiting
    BackHandler(enabled = leftDrawerState.isOpen || rightDrawerState.isOpen) {
        scope.launch {
            when {
                rightDrawerState.isOpen -> rightDrawerState.close()
                leftDrawerState.isOpen -> leftDrawerState.close()
            }
        }
    }
    
    // Left drawer
    ModalNavigationDrawer(
        drawerState = leftDrawerState,
        drawerContent = {
            Box(modifier = Modifier.fillMaxHeight()) {
                leftOverlay()
            }
        }
    ) {
        // Right drawer (nested)
        ModalNavigationDrawer(
            drawerState = rightDrawerState,
            drawerContent = {
                Box(modifier = Modifier.fillMaxHeight()) {
                    rightOverlay()
                }
            }
        ) {
            Column(modifier = modifier.fillMaxSize()) {
                // Top bar (with drawer state access)
                CompositionLocalProvider(
                    LocalDrawerState provides DrawerStates(
                        leftDrawerState = leftDrawerState,
                        rightDrawerState = rightDrawerState,
                        scope = scope,
                        onLeftToggle = leftToggle,
                        onRightToggle = rightToggle
                    )
                ) {
                    topBar()
                }
                
                // Main content
                Box(modifier = Modifier.weight(1f)) {
                    content()
                }
                
                // Bottom bar
                bottomBar()
            }
        }
    }
}

/**
 * Local composition value to provide drawer state to top bar.
 */
data class DrawerStates(
    val leftDrawerState: DrawerState,
    val rightDrawerState: DrawerState,
    val scope: kotlinx.coroutines.CoroutineScope,
    val onLeftToggle: () -> Unit,
    val onRightToggle: () -> Unit
)

val LocalDrawerState = compositionLocalOf<DrawerStates?> { null }

