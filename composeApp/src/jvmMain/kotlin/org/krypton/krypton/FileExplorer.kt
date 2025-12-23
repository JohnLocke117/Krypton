package org.krypton.krypton

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.file.Path
import java.nio.file.Paths
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.krypton.krypton.util.AppLogger
import krypton.composeapp.generated.resources.Res
import krypton.composeapp.generated.resources.add
import krypton.composeapp.generated.resources.chevron_right as chevronRight
import krypton.composeapp.generated.resources.close
import krypton.composeapp.generated.resources.description
import krypton.composeapp.generated.resources.file_copy
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

    // Click-outside detection for canceling editing
    val isEditing = state.editingMode != null
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (isEditing) {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        // Cancel editing when clicking on empty space
                        state.cancelEditing()
                    }
                } else {
                    Modifier
                }
            )
            .padding(ObsidianTheme.PanelPadding)
            .verticalScroll(rememberScrollState())
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

        // File Tree
        if (fileTree != null) {
            key(treeVersion) { // Force recomposition when tree changes
                TreeItem(
                    node = fileTree!!,
                    depth = 0,
                    activeTabPaths = setOfNotNull(
                        state.documents.getOrNull(state.activeTabIndex)?.path
                    ),
                    state = state,
                    theme = theme,
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
        } else if (state.currentDirectory == null) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val colorScheme = MaterialTheme.colorScheme
                Text(
                    text = "No folder selected\n\nClick 'Open Folder' to browse",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
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
    theme: ObsidianThemeValues,
    onFileClick: (Path) -> Unit,
    onFolderToggle: (FileTreeNode) -> Unit,
    onTreeRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = activeTabPaths.contains(node.path)
    val indent = theme.SidebarIndentPerLevel * depth
    
    // Check if this item is being edited
    val isRenaming = state.editingMode == FileTreeEditMode.Renaming && state.editingItemPath == node.path
    val isCreatingInThisParent = node.isDirectory && 
        state.editingParentPath == node.path && 
        (state.editingMode == FileTreeEditMode.CreatingFile || state.editingMode == FileTreeEditMode.CreatingFolder)
    
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
            val appColors = LocalAppColors.current
            val colorScheme = MaterialTheme.colorScheme
            val interactionSource = remember { MutableInteractionSource() }
            val isHovered by interactionSource.collectIsHoveredAsState()
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(theme.SidebarItemHeight)
                    .hoverable(interactionSource = interactionSource)
                    .clip(
                        if (isSelected || isHovered || isRenaming) RoundedCornerShape(6.dp) else RoundedCornerShape(0.dp)
                    )
                    .then(
                        if (isRenaming) {
                            Modifier.border(
                                width = 1.dp,
                                color = colorScheme.primary,
                                shape = RoundedCornerShape(6.dp)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .background(
                        when {
                            isSelected -> appColors.sidebarActiveItem
                            isHovered -> appColors.hoverBackground
                            else -> androidx.compose.ui.graphics.Color.Transparent
                        }
                    )
                    .clickable(enabled = !isRenaming) {
                        if (!isRenaming) {
                            if (node.isDirectory) {
                                onFolderToggle(node)
                            } else {
                                onFileClick(node.path)
                            }
                        }
                    }
                    .padding(
                        start = indent + theme.SidebarHorizontalPadding,
                        end = theme.SidebarHorizontalPadding,
                        top = theme.SidebarVerticalPadding,
                        bottom = theme.SidebarVerticalPadding
                    )
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
                        modifier = Modifier.size(18.dp)
                    )
                    // Reduced spacing between chevron and folder icon (2dp instead of 6dp)
                    Spacer(modifier = Modifier.width(2.dp))
                } else {
                    Spacer(modifier = Modifier.width(18.dp))
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
                    modifier = Modifier.size(18.dp)
                )
                
                // Spacing between icon and text
                Spacer(modifier = Modifier.width(theme.SidebarIconTextSpacing))

                // Name - show TextField if renaming, otherwise Text
                if (isRenaming) {
                    InlineTreeTextField(
                        initialName = node.name,
                        onConfirm = { newName ->
                            state.confirmRename(node.path, newName)
                            onTreeRefresh()
                        },
                        onCancel = {
                            state.cancelEditing()
                        },
                        isSelected = isSelected,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    val colorScheme = MaterialTheme.colorScheme
                    Text(
                        text = node.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) {
                            androidx.compose.ui.graphics.Color.White
                        } else {
                            colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            }
        }

            // Children (if expanded)
        if (node.isDirectory && node.isExpanded) {
            // Show temporary create row if creating in this parent
            if (isCreatingInThisParent) {
                val isCreatingFile = state.editingMode == FileTreeEditMode.CreatingFile
                TemporaryCreateRow(
                    isFile = isCreatingFile,
                    depth = depth + 1,
                    indent = indent,
                    theme = theme,
                    onConfirm = { name ->
                        if (isCreatingFile) {
                            state.confirmCreateFile(name, node.path)
                        } else {
                            state.confirmCreateFolder(name, node.path)
                        }
                        onTreeRefresh()
                    },
                    onCancel = {
                        state.cancelEditing()
                    }
                )
            }
            
            node.children.forEach { child ->
                TreeItem(
                    node = child,
                    depth = depth + 1,
                    activeTabPaths = activeTabPaths,
                    state = state,
                    theme = theme,
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
private fun InlineTreeTextField(
    initialName: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
    placeholder: String = "Name...",
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(initialName) }
    val focusRequester = remember { FocusRequester() }
    val colorScheme = MaterialTheme.colorScheme
    
    // Use white text if selected, otherwise use onSurface
    val textColor = if (isSelected) {
        Color.White
    } else {
        colorScheme.onSurface
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        // Select all text for easy replacement
        name = initialName
    }

    Box(modifier = modifier) {
        if (name.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor.copy(alpha = 0.6f)
            )
        }
        BasicTextField(
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
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
            singleLine = true,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(textColor),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (name.isNotBlank()) {
                        onConfirm(name)
                    }
                }
            )
        )
    }
}

@Composable
private fun TemporaryCreateRow(
    isFile: Boolean,
    depth: Int,
    indent: androidx.compose.ui.unit.Dp,
    theme: ObsidianThemeValues,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    val appColors = LocalAppColors.current
    val colorScheme = MaterialTheme.colorScheme
    val childIndent = theme.SidebarIndentPerLevel * depth
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(theme.SidebarItemHeight)
            .clip(RoundedCornerShape(6.dp))
            .border(
                width = 1.dp,
                color = colorScheme.primary,
                shape = RoundedCornerShape(6.dp)
            )
            .background(Color.Transparent)
            .padding(
                start = childIndent + theme.SidebarHorizontalPadding,
                end = theme.SidebarHorizontalPadding,
                top = theme.SidebarVerticalPadding,
                bottom = theme.SidebarVerticalPadding
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Spacer for chevron (folders have chevron, files don't, but we show icon)
            Spacer(modifier = Modifier.width(18.dp))
            
            // Icon
            Image(
                painter = painterResource(
                    if (isFile) Res.drawable.description else Res.drawable.folder
                ),
                contentDescription = if (isFile) "File" else "Folder",
                modifier = Modifier.size(18.dp)
            )
            
            // Spacing between icon and text
            Spacer(modifier = Modifier.width(theme.SidebarIconTextSpacing))
            
            // TextField for name input
            InlineTreeTextField(
                initialName = "",
                onConfirm = onConfirm,
                onCancel = onCancel,
                placeholder = if (isFile) "File name..." else "Folder name...",
                isSelected = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PanelIconButton(
    icon: DrawableResource,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        val colorScheme = MaterialTheme.colorScheme
        Image(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(colorScheme.onSurfaceVariant)
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


