package org.krypton.krypton

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
import java.nio.file.Path
import java.nio.file.Paths

@Composable
fun RecentFoldersDialog(
    recentFolders: List<String>,
    onFolderSelected: (Path) -> Unit,
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
                                    val path = Paths.get(folderPath)
                                    onFolderSelected(path)
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
                    text = Paths.get(path).fileName.toString(),
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
