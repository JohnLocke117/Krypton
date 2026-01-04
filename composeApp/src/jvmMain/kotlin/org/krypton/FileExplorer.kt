package org.krypton

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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
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
import java.nio.file.Paths
import org.jetbrains.compose.resources.DrawableResource
import org.krypton.data.files.FileSystem
import org.koin.core.context.GlobalContext
import org.jetbrains.compose.resources.painterResource
import org.krypton.util.AppLogger
import org.krypton.LocalAppColors
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
import krypton.composeapp.generated.resources.unknown_document

@Composable
fun FileExplorer(
    state: org.krypton.ui.state.EditorStateHolder,
    onFolderSelected: (String?) -> Unit,
    recentFolders: List<String>,
    onRecentFolderSelected: (String) -> Unit,
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
    state: org.krypton.ui.state.EditorStateHolder,
    onFolderSelected: (String?) -> Unit,
    recentFolders: List<String>,
    onRecentFolderSelected: (String) -> Unit,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    val currentDirectory by state.currentDirectory.collectAsState()
    val deletingPath by state.deletingPath.collectAsState()
    val editingMode by state.editingMode.collectAsState()
    val editingItemPath by state.editingItemPath.collectAsState()
    val editingParentPath by state.editingParentPath.collectAsState()
    val documents by state.documents.collectAsState()
    val activeTabIndex by state.activeTabIndex.collectAsState()
    val validationError by state.validationError.collectAsState()
    val treeRefreshTrigger by state.treeRefreshTrigger.collectAsState()
    val collapseAllTrigger by state.collapseAllTrigger.collectAsState()
    
    var fileTree by remember { mutableStateOf<FileTreeNode?>(null) }
    var treeVersion by remember { mutableStateOf(0) }

    // Get FileSystem from DI
    val fileSystem = remember { GlobalContext.get().get<FileSystem>() }
    
    // Helper function to build tree with expanded state preservation
    fun buildTreeWithStatePreservation(dir: String): FileTreeNode? {
        // Collect currently expanded paths
        val expandedPaths = mutableSetOf<String>()
        fileTree?.let { tree ->
            fun collectExpandedPaths(node: FileTreeNode) {
                if (node.isExpanded) {
                    expandedPaths.add(node.path)
                }
                node.children.forEach { collectExpandedPaths(it) }
            }
            collectExpandedPaths(tree)
        }
        
        // Build new tree using FileSystem
        val newTree = FileTreeBuilder.buildTree(dir, fileSystem) ?: return null
        newTree.isExpanded = true
        
        // Restore expanded state
        fun restoreExpandedState(node: FileTreeNode) {
            if (expandedPaths.contains(node.path)) {
                node.isExpanded = true
            }
            node.children.forEach { restoreExpandedState(it) }
        }
        restoreExpandedState(newTree)
        
        return newTree
    }

    // Helper function to expand a folder by path in the tree
    fun expandFolder(path: String) {
        fileTree?.let { tree ->
            fun expandNode(node: FileTreeNode, targetPath: String): Boolean {
                if (node.path == targetPath && node.isDirectory) {
                    node.isExpanded = true
                    treeVersion++ // Trigger recomposition
                    return true
                }
                for (child in node.children) {
                    if (expandNode(child, targetPath)) {
                        return true
                    }
                }
                return false
            }
            expandNode(tree, path)
        }
    }

    // Helper function to collapse all folders in the tree
    fun collapseAllFolders() {
        fileTree?.let { tree ->
            fun collapseNode(node: FileTreeNode) {
                if (node.isDirectory) {
                    node.isExpanded = false
                }
                node.children.forEach { collapseNode(it) }
            }
            // Keep root expanded (it's not displayed anyway, but its children are)
            tree.isExpanded = true
            // Collapse all children
            tree.children.forEach { collapseNode(it) }
            treeVersion++ // Trigger recomposition
        }
    }

    // Refresh tree while preserving expanded state
    fun refreshTree() {
        currentDirectory?.let { dir ->
            fileTree = buildTreeWithStatePreservation(dir)
            treeVersion++
        }
    }

    // Delete confirmation dialog
    val currentDeletingPath = deletingPath
    if (currentDeletingPath != null) {
            DeleteConfirmationDialog(
                path = currentDeletingPath,
                onConfirm = {
                    state.confirmDelete()
                },
                onDismiss = {
                    state.cancelDelete()
                }
            )
    }

    // Rebuild tree when directory changes
    LaunchedEffect(currentDirectory) {
        currentDirectory?.let { dir ->
            fileTree = buildTreeWithStatePreservation(dir)
            treeVersion++
        } ?: run {
            fileTree = null
            treeVersion++
        }
    }

    // Refresh tree when refresh trigger changes (after create/rename/delete operations)
    LaunchedEffect(treeRefreshTrigger) {
        if (currentDirectory != null && treeRefreshTrigger > 0) {
            refreshTree()
        }
    }

    // Collapse all folders when collapse all trigger changes
    LaunchedEffect(collapseAllTrigger) {
        if (currentDirectory != null && collapseAllTrigger > 0) {
            collapseAllFolders()
        }
    }

    // Auto-expand folder when creating inside it
    LaunchedEffect(editingParentPath, editingMode, currentDirectory) {
        editingParentPath?.let { parentPath ->
            currentDirectory?.let { rootPath ->
                editingMode?.let { mode ->
                    // Only expand if it's not the root directory and we're creating something
                    if (parentPath != rootPath && 
                        (mode == org.krypton.core.domain.editor.FileTreeEditMode.CreatingFile || 
                         mode == org.krypton.core.domain.editor.FileTreeEditMode.CreatingFolder)) {
                        expandFolder(parentPath)
                    }
                }
            }
        }
    }

    // Click-outside detection for canceling editing
    val isEditing = editingMode != null
    
    // Wrap Column in ContextMenuArea for empty area right-click when folder is open
    val columnModifier = Modifier
        .fillMaxSize()
        .then(
            if (isEditing) {
                // Use pointerInput to only respond to actual pointer clicks, not keyboard events
                Modifier.pointerInput(Unit) {
                    detectTapGestures { offset ->
                        // Cancel editing when clicking on empty space (only pointer clicks, not keyboard)
                        state.cancelEditing()
                        state.clearValidationError()
                    }
                }
            } else {
                Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    // Clear selection when clicking on empty space
                    state.clearExplorerSelection()
                }
            }
        )
        .padding(ObsidianTheme.PanelPadding)
        .verticalScroll(rememberScrollState())
    
    val columnContent: @Composable () -> Unit = {
        Column(modifier = columnModifier) {
        // Validation error display
        validationError?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        // Open Folder Button and Recent Folders - only show when no folder is open
        if (currentDirectory == null) {
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
                                    onRecentFolderSelected(folderPath)
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
        if (fileTree != null && currentDirectory != null) {
            // Check if we're creating in the root directory
            // editingParentPath equals currentDirectory when creating in root
            val isCreatingInRoot = editingMode != null && 
                editingParentPath != null && 
                editingParentPath == currentDirectory
            val isCreatingFileInRoot = isCreatingInRoot && 
                editingMode == org.krypton.core.domain.editor.FileTreeEditMode.CreatingFile
            val isCreatingFolderInRoot = isCreatingInRoot && 
                editingMode == org.krypton.core.domain.editor.FileTreeEditMode.CreatingFolder
            
            // Add context menu to empty area for creating files/folders in root
            // Wrap in Box to ensure the entire area is clickable for context menu
            Box(modifier = Modifier.fillMaxSize()) {
                ContextMenuArea(
                    items = {
                        listOf(
                            ContextMenuItem("New File") {
                                state.startCreatingNewFile()
                            },
                            ContextMenuItem("New Folder") {
                                state.startCreatingNewFolder()
                            }
                            // Delete and Rename are disabled when clicking empty area
                        )
                    }
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Show create row at root level if creating in root
                        if (isCreatingInRoot && editingParentPath != null) {
                            TemporaryCreateRow(
                                isFile = isCreatingFileInRoot,
                                depth = 0,
                                indent = 0.dp,
                                theme = theme,
                                onConfirm = { name ->
                                    // Use editingParentPath to ensure consistency
                                    editingParentPath?.let { parentPath ->
                                        if (isCreatingFileInRoot) {
                                            state.confirmCreateFile(name, parentPath)
                                        } else {
                                            state.confirmCreateFolder(name, parentPath)
                                        }
                                    }
                                    // Tree will refresh automatically via LaunchedEffect(files)
                                },
                                onCancel = {
                                    state.cancelEditing()
                                    state.clearValidationError()
                                },
                                onValueChange = {
                                    // Clear validation error when user starts typing
                                    state.clearValidationError()
                                }
                            )
                        }
                        
                        key(treeVersion) { // Force recomposition when tree changes
                            // Display only the children of the root folder, not the root folder itself (VS Code behavior)
                            // Compute active tab path from activeTabIndex
                            val activeTabPath = if (activeTabIndex >= 0 && activeTabIndex < documents.size) {
                                documents[activeTabIndex].path
                            } else {
                                null
                            }
                            
                            fileTree?.children?.forEach { child ->
                                TreeItem(
                                    node = child,
                                    depth = 0,
                                    activeTabPath = activeTabPath,
                                    state = state,
                                    theme = theme,
                                    onFileClick = { pathString ->
                                        // Use FileSystem from state holder's dependency
                                        // For now, assume it's a file if it's not a directory
                                        if (!fileSystem.isDirectory(pathString)) {
                                            AppLogger.action("FileExplorer", "FileOpened", pathString)
                                            state.openTab(pathString)
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
                    }
                }
            }
        } else if (currentDirectory == null) {
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
    
    // Wrap in ContextMenuArea when folder is open to enable right-click on empty area
    Box(modifier = modifier.fillMaxSize()) {
        if (currentDirectory != null && !isEditing) {
            ContextMenuArea(
                items = {
                    listOf(
                        ContextMenuItem("New File") {
                            state.startCreatingNewFile()
                        },
                        ContextMenuItem("New Folder") {
                            state.startCreatingNewFolder()
                        }
                    )
                }
            ) {
                columnContent()
            }
        } else {
            columnContent()
        }
    }
}

/**
 * Helper function to get the appropriate icon for a file based on its extension.
 * Returns unknown_document for files that are not .md or .txt, otherwise returns description.
 */
private fun getFileIcon(path: String): DrawableResource {
    val fileName = try {
        Paths.get(path).fileName.toString()
    } catch (e: Exception) {
        path.substringAfterLast('/', path)
    }
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "md", "txt", "markdown" -> Res.drawable.description
        else -> Res.drawable.unknown_document
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TreeItem(
    node: FileTreeNode,
    depth: Int,
    activeTabPath: String?,
    state: org.krypton.ui.state.EditorStateHolder,
    theme: ObsidianThemeValues,
    onFileClick: (String) -> Unit,
    onFolderToggle: (FileTreeNode) -> Unit,
    onTreeRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = activeTabPath == node.path
    val indent = theme.SidebarIndentPerLevel * depth
    
    // Check if this item is being edited
    val editingMode by state.editingMode.collectAsState()
    val editingItemPath by state.editingItemPath.collectAsState()
    val editingParentPath by state.editingParentPath.collectAsState()
    
    val isRenaming = editingMode == org.krypton.core.domain.editor.FileTreeEditMode.Renaming && 
        editingItemPath == node.path
    
    // Check if we're creating in this parent
    val isCreatingInThisParent = node.isDirectory && 
        editingParentPath == node.path && 
        (editingMode == org.krypton.core.domain.editor.FileTreeEditMode.CreatingFile || 
         editingMode == org.krypton.core.domain.editor.FileTreeEditMode.CreatingFolder)

    Column(modifier = modifier) {
        ContextMenuArea(
            items = {
                listOf(
                    ContextMenuItem("New File") {
                        // Select the node first, then create
                        state.selectExplorerNode(node.path)
                        state.startCreatingNewFile()
                    },
                    ContextMenuItem("New Folder") {
                        // Select the node first, then create
                        state.selectExplorerNode(node.path)
                        state.startCreatingNewFolder()
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
                            // Update selection when clicking on a node
                            state.selectExplorerNode(node.path)
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
                            getFileIcon(node.path)
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
                            state.clearValidationError()
                        },
                        onValueChange = {
                            // Clear validation error when user starts typing
                            state.clearValidationError()
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
                val isCreatingFile = editingMode == org.krypton.core.domain.editor.FileTreeEditMode.CreatingFile
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
                        state.clearValidationError()
                    },
                    onValueChange = {
                        // Clear validation error when user starts typing
                        state.clearValidationError()
                    }
                )
            }
            
            node.children.forEach { child ->
                TreeItem(
                    node = child,
                    depth = depth + 1,
                    activeTabPath = activeTabPath,
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
    onValueChange: ((String) -> Unit)? = null,
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
            onValueChange = { 
                name = it
                onValueChange?.invoke(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onKeyEvent { event ->
                    // Only handle Enter and Escape, let all other keys (including Space) pass through
                    when (event.key) {
                        Key.Enter -> {
                            if (name.isNotBlank()) {
                                onConfirm(name)
                            }
                            true // Consume Enter
                        }
                        Key.Escape -> {
                            onCancel()
                            true // Consume Escape
                        }
                        else -> {
                            // Don't consume other keys - allow normal text input including spaces
                            // Return false to let the key event propagate normally
                            false
                        }
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
    onCancel: () -> Unit,
    onValueChange: ((String) -> Unit)? = null
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
                onValueChange = onValueChange,
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
    path: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val fileSystem = org.koin.core.context.GlobalContext.get().get<org.krypton.data.files.FileSystem>()
    val isDirectory = fileSystem.isDirectory(path)
    val itemName = try {
        java.nio.file.Paths.get(path).fileName.toString()
    } catch (e: Exception) {
        path.substringAfterLast('/', path)
    }
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


