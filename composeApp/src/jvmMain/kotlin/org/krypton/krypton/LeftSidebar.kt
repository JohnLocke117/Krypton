package org.krypton.krypton

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LeftSidebar(
    state: EditorState,
    onFolderSelected: (java.nio.file.Path?) -> Unit,
    theme: ObsidianThemeValues,
    settingsRepository: SettingsRepository?,
    modifier: Modifier = Modifier
) {
    val targetWidth = if (state.leftSidebarVisible) state.leftSidebarWidth else 0.dp
    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = tween(durationMillis = 300),
        label = "sidebar_width"
    )

    val appColors = LocalAppColors.current
    val colorScheme = MaterialTheme.colorScheme
    AnimatedVisibility(
        visible = state.leftSidebarVisible,
        modifier = modifier.width(animatedWidth)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(animatedWidth)
                .background(appColors.sidebarBackground)
                .border(ObsidianTheme.PanelBorderWidth, appColors.sidebarBorder, RoundedCornerShape(0.dp))
        ) {
            when (state.activeRibbonButton) {
                RibbonButton.Files -> {
                    FilesPanel(
                        state = state,
                        onFolderSelected = onFolderSelected,
                        theme = theme,
                        settingsRepository = settingsRepository,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                RibbonButton.Search -> {
                    SearchPanel(modifier = Modifier.fillMaxSize())
                }
                RibbonButton.Bookmarks -> {
                    BookmarksPanel(modifier = Modifier.fillMaxSize())
                }
                RibbonButton.Settings -> {
                    SettingsPanel(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun FilesPanel(
    state: EditorState,
    onFolderSelected: (java.nio.file.Path?) -> Unit,
    theme: ObsidianThemeValues,
    settingsRepository: SettingsRepository?,
    modifier: Modifier = Modifier
) {
    val recentFolders = settingsRepository?.settingsFlow?.collectAsState()?.value?.app?.recentFolders ?: emptyList()
    
    // This will contain the FileExplorer content without the card wrapper
    FileExplorerContent(
        state = state,
        onFolderSelected = onFolderSelected,
        recentFolders = recentFolders,
        onRecentFolderSelected = { path ->
            state.changeDirectoryWithHistory(path, settingsRepository)
        },
        theme = theme,
        modifier = modifier
    )
}

@Composable
private fun SearchPanel(
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ObsidianTheme.PanelPadding)
    ) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.titleMedium,
            color = colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Search functionality coming soon...",
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BookmarksPanel(
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ObsidianTheme.PanelPadding)
    ) {
        Text(
            text = "Bookmarks",
            style = MaterialTheme.typography.titleMedium,
            color = colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No bookmarks yet",
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsPanel(
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ObsidianTheme.PanelPadding)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.titleMedium,
            color = colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Settings panel coming soon...",
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant
        )
    }
}

