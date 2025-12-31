package org.krypton.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shared scaffold component for main application layout.
 * 
 * Provides a consistent structure across platforms with:
 * - Top bar (app bar / ribbon)
 * - Bottom bar (navigation / ribbon)
 * - Left overlay (sidebar / ribbon)
 * - Right overlay (sidebar / ribbon)
 * - Main content area
 * 
 * Platform-specific implementations handle how overlays are displayed:
 * - Desktop: Overlays are docked (always visible)
 * - Android: Overlays are slide-in drawers (ModalNavigationDrawer)
 * 
 * On Android, the top bar can access drawer toggle functions via LocalDrawerState.
 */
@Composable
fun MainScaffold(
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    leftOverlay: @Composable () -> Unit = {},
    rightOverlay: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    PlatformMainScaffold(
        topBar = topBar,
        bottomBar = bottomBar,
        leftOverlay = leftOverlay,
        rightOverlay = rightOverlay,
        content = content,
        modifier = modifier
    )
}

/**
 * Platform-specific implementation of MainScaffold.
 * 
 * Desktop: Overlays are docked sidebars
 * Android: Overlays are slide-in drawers
 */
@Composable
expect fun PlatformMainScaffold(
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    leftOverlay: @Composable () -> Unit,
    rightOverlay: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
)

