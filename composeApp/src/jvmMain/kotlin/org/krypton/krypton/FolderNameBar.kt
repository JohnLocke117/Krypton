package org.krypton.krypton

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { showMenu = true }),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val colorScheme = MaterialTheme.colorScheme
                    Image(
                        painter = painterResource(Res.drawable.folder),
                        contentDescription = "Folder",
                        modifier = Modifier.size(24.dp),
                        colorFilter = ColorFilter.tint(colorScheme.onSurfaceVariant)
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
                        // TODO: Implement reveal in Finder/Explorer
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
                Image(
                    painter = painterResource(Res.drawable.settings),
                    contentDescription = "Settings",
                    modifier = Modifier.size(24.dp),
                    colorFilter = ColorFilter.tint(colorScheme.onSurfaceVariant)
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

