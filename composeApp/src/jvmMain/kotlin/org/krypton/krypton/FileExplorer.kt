package org.krypton.krypton

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import krypton.composeapp.generated.resources.Res
import krypton.composeapp.generated.resources.add
import krypton.composeapp.generated.resources.chevron_right as chevronRight
import krypton.composeapp.generated.resources.close
import krypton.composeapp.generated.resources.description
import krypton.composeapp.generated.resources.folder
import krypton.composeapp.generated.resources.folder_open as folderOpen
import krypton.composeapp.generated.resources.keep
import krypton.composeapp.generated.resources.keyboard_arrow_down as keyboardArrowDown
import krypton.composeapp.generated.resources.search
import krypton.composeapp.generated.resources.settings
import krypton.composeapp.generated.resources.star
import java.nio.file.Path

@Composable
fun FileExplorer(
    state: EditorState,
    onFolderSelected: (Path?) -> Unit,
    modifier: Modifier = Modifier
) {
    FileExplorerContent(
        state = state,
        onFolderSelected = onFolderSelected,
        modifier = modifier
    )
}

@Composable
fun FileExplorerContent(
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
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(ObsidianTheme.PanelPadding)
        ) {
            // Open Folder Button
            Button(
                onClick = { onFolderSelected(null) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ObsidianTheme.Accent
                )
            ) {
                Text(
                    text = "Open Folder",
                    style = MaterialTheme.typography.labelLarge,
                    color = ObsidianTheme.TextPrimary
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
                                            treeVersion++ // Trigger recomposition
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
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No folder selected\n\nClick 'Open Folder' to browse",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ObsidianTheme.TextSecondary,
                        modifier = Modifier
                            .padding(16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        // Bottom Strip with Current Folder Name
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = ObsidianTheme.SurfaceContainer
        ) {
            Text(
                text = state.currentDirectory?.fileName?.toString() ?: "No folder selected",
                style = MaterialTheme.typography.bodySmall,
                color = ObsidianTheme.TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isSelected) {
                        ObsidianTheme.SelectionAccent
                    } else {
                        androidx.compose.ui.graphics.Color.Transparent
                    }
                )
                .clickable {
                    if (node.isDirectory) {
                        onFolderToggle(node)
                    } else {
                        onFileClick(node.path)
                    }
                }
                .padding(start = indent, end = 8.dp, top = 2.dp, bottom = 2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Chevron for folders
                if (node.isDirectory) {
                    Image(
                        painter = painterResource(
                            if (node.isExpanded) Res.drawable.keyboardArrowDown else Res.drawable.chevronRight
                        ),
                        contentDescription = if (node.isExpanded) "Expanded" else "Collapsed",
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.width(24.dp))
                }

                // Icon
                Image(
                    painter = painterResource(
                        if (node.isDirectory) {
                            if (node.isExpanded) Res.drawable.folderOpen else Res.drawable.folder
                        } else {
                            Res.drawable.description
                        }
                    ),
                    contentDescription = if (node.isDirectory) "Folder" else "File",
                    modifier = Modifier
                        .size(22.dp)
                        .padding(end = 8.dp)
                )

                // Name
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) {
                        ObsidianTheme.Accent
                    } else {
                        ObsidianTheme.TextPrimary
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp
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


