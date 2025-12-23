package org.krypton.krypton

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.file.Path
import java.nio.file.Paths
import org.jetbrains.compose.resources.painterResource
import org.krypton.krypton.util.AppLogger
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

@Composable
fun FileExplorer(
    state: EditorState,
    onFolderSelected: (Path?) -> Unit,
    recentFolders: List<String>,
    onRecentFolderSelected: (Path) -> Unit,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    FileExplorerContent(
        state = state,
        onFolderSelected = onFolderSelected,
        recentFolders = recentFolders,
        onRecentFolderSelected = onRecentFolderSelected,
        theme = theme,
        modifier = modifier
    )
}

@Composable
fun FileExplorerContent(
    state: EditorState,
    onFolderSelected: (Path?) -> Unit,
    recentFolders: List<String>,
    onRecentFolderSelected: (Path) -> Unit,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    var fileTree by remember { mutableStateOf<FileTreeNode?>(null) }
    var treeVersion by remember { mutableStateOf(0) } // Force recomposition on tree changes

    // Helper function to refresh tree
    fun refreshTree() {
        state.currentDirectory?.let { dir ->
            fileTree = FileTreeBuilder.buildTree(dir)?.apply {
                isExpanded = true
            }
            treeVersion++
        }
    }

    // Delete confirmation dialog
    val deletingPath = state.deletingPath
    if (deletingPath != null) {
        DeleteConfirmationDialog(
            path = deletingPath,
            onConfirm = {
                state.confirmDelete()
                // Refresh tree after deletion
                refreshTree()
            },
            onDismiss = {
                state.cancelDelete()
            }
        )
    }

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
            // Open Folder Button and Recent Folders - only show when no folder is open
            if (state.currentDirectory == null) {
                Button(
                    onClick = { onFolderSelected(null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.Accent
                    )
                ) {
                    Text(
                        text = "Open Folder",
                        style = MaterialTheme.typography.labelLarge,
                        color = theme.TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                
                // Recent Folders List
                if (recentFolders.isNotEmpty()) {
                    Text(
                        text = "Recent Folders",
                        style = MaterialTheme.typography.labelMedium,
                        color = theme.TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    recentFolders.forEach { folderPath ->
                        RecentFolderButton(
                            path = folderPath,
                            theme = theme,
                            onClick = {
                                try {
                                    val path = java.nio.file.Paths.get(folderPath)
                                    val file = path.toFile()
                                    if (file.exists() && file.isDirectory) {
                                        onRecentFolderSelected(path)
                                    }
                                } catch (e: Exception) {
                                    // Invalid path, skip
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

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
                            activeTabPaths = state.documents.mapNotNull { it.path }.toSet(),
                            state = state,
                            onFileClick = { path ->
                                if (FileManager.isFile(path)) {
                                    AppLogger.action("FileExplorer", "FileOpened", path.toString())
                                    state.openTab(path)
                                }
                            },
                            onFolderToggle = { node ->
                                node.toggleExpanded()
                                treeVersion++ // Trigger recomposition
                            },
                            onTreeRefresh = { refreshTree() }
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

        // Bottom Strip with Current Folder Name and Settings - only show when folder is open
        if (state.currentDirectory != null) {
            FolderNameBar(
                folderName = state.currentDirectory!!.fileName.toString(),
                onFolderClick = { /* Will be handled by FolderMenu */ },
                onSettingsClick = { state.openSettingsDialog() },
                onFolderSelected = onFolderSelected,
                onCloseFolder = { state.closeFolder() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TreeItem(
    node: FileTreeNode,
    depth: Int,
    activeTabPaths: Set<Path>,
    state: EditorState,
    onFileClick: (Path) -> Unit,
    onFolderToggle: (FileTreeNode) -> Unit,
    onTreeRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = activeTabPaths.contains(node.path)
    val indent = (depth * 16).dp
    
    // Determine parent path for "New" operations
    val parentPath = if (node.isDirectory) {
        node.path
    } else {
        node.path.parent
    }

    Column(modifier = modifier) {
        ContextMenuArea(
            items = {
                listOf(
                    ContextMenuItem("New File") {
                        parentPath?.let { state.startCreatingNewFile(it) }
                    },
                    ContextMenuItem("New Folder") {
                        parentPath?.let { state.startCreatingNewFolder(it) }
                    },
                    ContextMenuItem("Rename") {
                        state.startRenamingItem(node.path)
                    },
                    ContextMenuItem("Delete") {
                        state.deleteItem(node.path)
                    }
                )
            }
        ) {
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
        }
        
        // Inline editor for create/rename
        if (state.renamingPath == node.path) {
            InlineNameEditor(
                initialName = state.renamingName,
                onConfirm = { newName: String ->
                    state.confirmRename(node.path, newName)
                    onTreeRefresh()
                },
                onCancel = {
                    state.cancelRenaming()
                },
                indent = indent + 24.dp
            )
        } else if (state.creatingNewFileParentPath == node.path && node.isDirectory) {
            InlineNameEditor(
                initialName = "",
                onConfirm = { name: String ->
                    state.confirmCreateFile(name, node.path)
                    onTreeRefresh()
                },
                onCancel = {
                    state.cancelCreatingNewFile()
                },
                indent = indent + 24.dp,
                placeholder = "File name..."
            )
        } else if (state.creatingNewFolderParentPath == node.path && node.isDirectory) {
            InlineNameEditor(
                initialName = "",
                onConfirm = { name: String ->
                    state.confirmCreateFolder(name, node.path)
                    onTreeRefresh()
                },
                onCancel = {
                    state.cancelCreatingNewFolder()
                },
                indent = indent + 24.dp,
                placeholder = "Folder name..."
            )
        }

        // Children (if expanded)
        if (node.isDirectory && node.isExpanded) {
            node.children.forEach { child ->
                TreeItem(
                    node = child,
                    depth = depth + 1,
                    activeTabPaths = activeTabPaths,
                    state = state,
                    onFileClick = onFileClick,
                    onFolderToggle = onFolderToggle,
                    onTreeRefresh = onTreeRefresh
                )
            }
        }
    }
}

@Composable
private fun RecentFolderButton(
    path: String,
    theme: ObsidianThemeValues,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val folderName = try {
        Paths.get(path).fileName.toString()
    } catch (e: Exception) {
        path
    }
    
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = theme.TextPrimary
        ),
        border = BorderStroke(1.dp, theme.Border)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = folderName,
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

@Composable
private fun InlineNameEditor(
    initialName: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
    indent: androidx.compose.ui.unit.Dp,
    placeholder: String = "Name..."
) {
    var name by remember { mutableStateOf(initialName) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent, end = 8.dp, top = 2.dp, bottom = 2.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onKeyEvent { event ->
                    when (event.key) {
                        Key.Enter -> {
                            if (name.isNotBlank()) {
                                onConfirm(name)
                            }
                            true
                        }
                        Key.Escape -> {
                            onCancel()
                            true
                        }
                        else -> false
                    }
                },
            placeholder = { Text(placeholder) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

@Composable
private fun DeleteConfirmationDialog(
    path: Path,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDirectory = FileManager.isDirectory(path)
    val itemName = path.fileName.toString()
    val message = if (isDirectory) {
        "Are you sure you want to delete the folder \"$itemName\" and all its contents?\n\nThis action cannot be undone."
    } else {
        "Are you sure you want to delete the file \"$itemName\"?\n\nThis action cannot be undone."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${if (isDirectory) "Folder" else "File"}") },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


