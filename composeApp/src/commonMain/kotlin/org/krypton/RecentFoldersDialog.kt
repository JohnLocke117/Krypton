package org.krypton

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Dialog for displaying and selecting from recently opened folders.
 * 
 * Shows a scrollable list of recent folder paths with the ability to:
 * - Select a recent folder to open
 * - Open a new folder via picker
 * 
 * @param recentFolders List of recently opened folder paths
 * @param onFolderSelected Callback when user selects a folder from the list
 * @param onOpenNewFolder Callback when user clicks "Open New Folder"
 * @param onDismiss Callback when the dialog is dismissed
 * @param theme Theme values for styling
 * @param modifier Modifier to apply to the dialog
 */
@Composable
fun RecentFoldersDialog(
    recentFolders: List<String>,
    onFolderSelected: (String) -> Unit,
    onOpenNewFolder: () -> Unit,
    onDismiss: () -> Unit,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = theme.BackgroundElevated,
        title = {
            Text(
                text = "Recent Folders",
                style = MaterialTheme.typography.titleLarge,
                color = theme.TextPrimary
            )
        },
        text = {
            if (recentFolders.isEmpty()) {
                Text(
                    text = "No recent folders",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.TextSecondary
                )
            } else {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    recentFolders.forEach { folderPath ->
                        RecentFolderItem(
                            path = folderPath,
                            theme = theme,
                            onClick = {
                                try {
                                    onFolderSelected(folderPath)
                                    onDismiss()
                                } catch (e: Exception) {
                                    // Invalid path, skip
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onOpenNewFolder()
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = theme.Accent
                )
            ) {
                Text("Open New Folder")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = theme.TextSecondary
                )
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RecentFolderItem(
    path: String,
    theme: ObsidianThemeValues,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = theme.Surface,
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = path.substringAfterLast("/", path),
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
