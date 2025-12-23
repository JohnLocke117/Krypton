package org.krypton.krypton

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import krypton.composeapp.generated.resources.Res
import krypton.composeapp.generated.resources.file_copy
import krypton.composeapp.generated.resources.folder_copy
import krypton.composeapp.generated.resources.search
import java.nio.file.Path

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
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(animatedWidth)
                .background(CatppuccinMochaColors.Crust)
        ) {
            // Fixed Top Bar
            SidebarTopBar(
                state = state,
                theme = theme,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Divider between top bar and content area
            Divider(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(theme.SidebarSeparatorHeight),
                color = theme.BorderVariant
            )
            
            // Scrollable Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
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
            
            // Divider between content area and bottom bar
            Divider(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(theme.SidebarSeparatorHeight),
                color = theme.BorderVariant
            )
            
            // Fixed Bottom Bar
            SidebarBottomBar(
                state = state,
                onFolderSelected = onFolderSelected,
                theme = theme,
                modifier = Modifier.fillMaxWidth()
            )
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
            .verticalScroll(rememberScrollState())
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
            .verticalScroll(rememberScrollState())
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
            .verticalScroll(rememberScrollState())
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

@Composable
private fun SidebarTopBar(
    state: EditorState,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val colorScheme = MaterialTheme.colorScheme
    
    // Match the height of FolderNameBar which has 8dp vertical padding
    // Total height: 8dp (top) + 24dp (icon) + 8dp (bottom) = 40dp, but we'll use padding for consistency
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = appColors.sidebarBackground
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // New File icon button
                SidebarIconButton(
                    icon = Res.drawable.file_copy,
                    contentDescription = "New File",
                    onClick = { 
                        state.currentDirectory?.let { 
                            state.startCreatingNewFile(it) 
                        }
                    },
                    enabled = state.currentDirectory != null,
                    theme = theme,
                    modifier = Modifier
                )
                
                // New Folder icon button
                SidebarIconButton(
                    icon = Res.drawable.folder_copy,
                    contentDescription = "New Folder",
                    onClick = { 
                        state.currentDirectory?.let { 
                            state.startCreatingNewFolder(it) 
                        }
                    },
                    enabled = state.currentDirectory != null,
                    theme = theme,
                    modifier = Modifier
                )
                
                // Search icon button
                SidebarIconButton(
                    icon = Res.drawable.search,
                    contentDescription = "Search",
                    onClick = { state.updateActiveRibbonButton(RibbonButton.Search) },
                    theme = theme,
                    modifier = Modifier
                )
            }
        }
    }
}

@Composable
private fun SidebarBottomBar(
    state: EditorState,
    onFolderSelected: (Path?) -> Unit,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    
    // FolderNameBar has its own Surface with padding, so we don't need an extra Box
    // We use the same height as top bar for consistency, but FolderNameBar will determine its own height
    if (state.currentDirectory != null) {
        FolderNameBar(
            folderName = state.currentDirectory!!.fileName.toString(),
            onFolderClick = { /* Will be handled by FolderMenu */ },
            onSettingsClick = { state.openSettingsDialog() },
            onFolderSelected = onFolderSelected,
            onCloseFolder = { state.closeFolder() },
            theme = theme,
            modifier = modifier.fillMaxWidth()
        )
    } else {
        // Empty space to maintain consistent structure when no folder is open
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(theme.SidebarSectionHeight)
                .background(appColors.sidebarBackground)
        )
    }
}

@Composable
private fun SidebarIconButton(
    icon: DrawableResource,
    contentDescription: String,
    onClick: () -> Unit,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val colorScheme = MaterialTheme.colorScheme
        Image(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(
                if (enabled) {
                    colorScheme.onSurfaceVariant
                } else {
                    colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                }
            )
        )
    }
}

