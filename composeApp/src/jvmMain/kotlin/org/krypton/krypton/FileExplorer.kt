package org.krypton.krypton

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import krypton.composeapp.generated.resources.Res
import krypton.composeapp.generated.resources._file
import krypton.composeapp.generated.resources._folder
import krypton.composeapp.generated.resources._folder_open
import java.nio.file.Path

@Composable
fun FileExplorer(
    state: EditorState,
    onFolderSelected: (Path?) -> Unit,
    modifier: Modifier = Modifier
) {
    var fileTree by remember { mutableStateOf<FileTreeNode?>(null) }
    var treeVersion by remember { mutableStateOf(0) } // Force recomposition on tree changes

    // Rebuild tree when directory changes
    LaunchedEffect(state.currentDirectory) {
        state.currentDirectory?.let { dir ->
            fileTree = FileTreeBuilder.buildTree(dir)?.apply {
                // Expand root by default
                isExpanded = true
            }
            treeVersion++
        } ?: run {
            fileTree = null
            treeVersion++
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        // Top Header with Icons
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Dummy Icons (4 icons)
                IconButton(onClick = { /* TODO */ }) {
                    Text("🔍", fontSize = 20.sp)
                }
                IconButton(onClick = { /* TODO */ }) {
                    Text("⭐", fontSize = 20.sp)
                }
                IconButton(onClick = { /* TODO */ }) {
                    Text("⚙️", fontSize = 20.sp)
                }
                IconButton(onClick = { /* TODO */ }) {
                    Text("📌", fontSize = 20.sp)
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
        ) {
            // Open Folder Button
            Button(
                onClick = { onFolderSelected(null) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Open Folder",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // New File Button
            OutlinedButton(
                onClick = { state.startCreatingNewFile() },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.currentDirectory != null,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = "New File",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // New File Input (when creating)
            if (state.isCreatingNewFile) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        OutlinedTextField(
                            value = state.newFileName,
                            onValueChange = { state.updateNewFileName(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { 
                                Text(
                                    "File name...",
                                    style = MaterialTheme.typography.bodyMedium
                                ) 
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { state.cancelCreatingNewFile() }
                            ) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (state.newFileName.isNotBlank()) {
                                        state.createNewFile()
                                        // Rebuild tree after creating file
                                        state.currentDirectory?.let { dir ->
                                            fileTree = FileTreeBuilder.buildTree(dir)
                                        }
                                    }
                                },
                                enabled = state.newFileName.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Create")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // File Tree
            if (fileTree != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    key(treeVersion) { // Force recomposition when tree changes
                        TreeItem(
                            node = fileTree!!,
                            depth = 0,
                            activeTabPaths = state.tabs.map { it.path }.toSet(),
                            onFileClick = { path ->
                                if (FileManager.isFile(path)) {
                                    state.openTab(path)
                                }
                            },
                            onFolderToggle = { node ->
                                node.toggleExpanded()
                                treeVersion++ // Trigger recomposition
                            }
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = "No folder selected\n\nClick 'Open Folder' to browse",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        // Bottom Strip with Current Folder Name
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp
        ) {
            Text(
                text = state.currentDirectory?.fileName?.toString() ?: "No folder selected",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TreeItem(
    node: FileTreeNode,
    depth: Int,
    activeTabPaths: Set<Path>,
    onFileClick: (Path) -> Unit,
    onFolderToggle: (FileTreeNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = activeTabPaths.contains(node.path)
    val indent = (depth * 16).dp

    Column(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (node.isDirectory) {
                        onFolderToggle(node)
                    } else {
                        onFileClick(node.path)
                    }
                },
            color = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            } else {
                androidx.compose.ui.graphics.Color.Transparent
            },
            shape = MaterialTheme.shapes.small
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = indent, end = 8.dp, top = 1.dp, bottom = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Chevron for folders
                if (node.isDirectory) {
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (node.isExpanded) "▼" else "▶",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(20.dp))
                }

                // Icon
                Image(
                    painter = painterResource(
                        if (node.isDirectory) {
                            if (node.isExpanded) Res.drawable._folder_open else Res.drawable._folder
                        } else {
                            Res.drawable._file
                        }
                    ),
                    contentDescription = if (node.isDirectory) "Folder" else "File",
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 8.dp)
                )

                // Name
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 15.sp
                )
            }
        }

        // Children (if expanded)
        if (node.isDirectory && node.isExpanded) {
            node.children.forEach { child ->
                TreeItem(
                    node = child,
                    depth = depth + 1,
                    activeTabPaths = activeTabPaths,
                    onFileClick = onFileClick,
                    onFolderToggle = onFolderToggle
                )
            }
        }
    }
}


