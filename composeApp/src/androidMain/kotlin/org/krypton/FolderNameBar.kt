
package org.krypton

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.krypton.util.PathUtils
import org.krypton.LocalAppColors

/**
 * Folder name bar composable displayed at the bottom of the left sidebar.
 * 
 * Shows the current folder name with a dropdown menu providing:
 * - Open another folder
 * - Close folder
 * - Reveal in Finder/Explorer (future enhancement)
 * 
 * Also includes a settings button for quick access to app settings.
 * 
 * @param folderName The name of the currently open folder
 * @param onFolderClick Callback when the folder name is clicked (opens menu)
 * @param onSettingsClick Callback when the settings button is clicked
 * @param onFolderSelected Callback when user selects "Open another folder"
 * @param onCloseFolder Callback when user selects "Close folder"
 * @param theme Theme values for styling
 * @param modifier Modifier to apply to the folder name bar
 */
@Composable
fun FolderNameBar(
    folderName: String,
    onFolderClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onFolderSelected: (String?) -> Unit,
    onCloseFolder: () -> Unit,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    
    val appColors = LocalAppColors.current
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = appColors.sidebarBackground // Crust for sidebar bottom bar
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Folder name with icon - clickable (anchor for menu)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            ) {
                val interactionSource = remember { MutableInteractionSource() }
                val isHovered by interactionSource.collectIsHoveredAsState()
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .hoverable(interactionSource = interactionSource)
                        .clip(RoundedCornerShape(4.dp))
                        .then(
                            if (isHovered) {
                                Modifier.border(1.dp, theme.BorderVariant, RoundedCornerShape(4.dp))
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clickable(onClick = { showMenu = true }),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val colorScheme = MaterialTheme.colorScheme
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Folder",
                        modifier = Modifier.size(24.dp),
                        tint = colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = folderName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Folder menu
                FolderMenu(
                    expanded = showMenu,
                    onDismiss = { showMenu = false },
                    onOpenAnotherFolder = {
                        showMenu = false
                        onFolderSelected(null)
                    },
                    onCloseFolder = {
                        showMenu = false
                        onCloseFolder()
                    },
                    onRevealInFinder = {
                        showMenu = false
                        // Future enhancement: Implement platform-specific "reveal in Finder/Explorer" functionality
                        // This would require a platform abstraction (expect/actual) for file manager integration
                    }
                )
            }
            
            // Settings icon button (ribbon style)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Transparent)
                    .clickable(onClick = onSettingsClick)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                val colorScheme = MaterialTheme.colorScheme
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(24.dp),
                    tint = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FolderMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onOpenAnotherFolder: () -> Unit,
    onCloseFolder: () -> Unit,
    onRevealInFinder: () -> Unit,
    modifier: Modifier = Modifier
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        DropdownMenuItem(
            text = { Text("Open another folder...") },
            onClick = onOpenAnotherFolder
        )
        DropdownMenuItem(
            text = { Text("Close folder") },
            onClick = onCloseFolder
        )
        DropdownMenuItem(
            text = { Text("Reveal in Finder") },
            onClick = onRevealInFinder
        )
    }
}

