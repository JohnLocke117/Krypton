package org.krypton.krypton

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import krypton.composeapp.generated.resources.Res
import krypton.composeapp.generated.resources.folder
import krypton.composeapp.generated.resources.settings
import java.nio.file.Path

@Composable
fun FolderNameBar(
    folderName: String,
    onFolderClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onFolderSelected: (Path?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = ObsidianTheme.SurfaceContainer
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
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { showMenu = true }),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Image(
                        painter = painterResource(Res.drawable.folder),
                        contentDescription = "Folder",
                        modifier = Modifier.size(16.dp),
                        colorFilter = ColorFilter.tint(ObsidianTheme.TextSecondary)
                    )
                    Text(
                        text = folderName,
                        style = MaterialTheme.typography.bodySmall,
                        color = ObsidianTheme.TextSecondary,
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
                    onRevealInFinder = {
                        showMenu = false
                        // TODO: Implement reveal in Finder/Explorer
                    }
                )
            }
            
            // Settings icon button
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(32.dp)
            ) {
                Image(
                    painter = painterResource(Res.drawable.settings),
                    contentDescription = "Settings",
                    modifier = Modifier.size(18.dp),
                    colorFilter = ColorFilter.tint(ObsidianTheme.TextSecondary)
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
            text = { Text("Reveal in Finder") },
            onClick = onRevealInFinder
        )
    }
}

