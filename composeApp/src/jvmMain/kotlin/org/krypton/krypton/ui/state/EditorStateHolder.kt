package org.krypton.krypton.ui.state

import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.krypton.krypton.core.domain.editor.*
import org.krypton.krypton.data.files.FileSystem
import org.krypton.krypton.data.repository.SettingsRepository
import org.krypton.krypton.util.AppLogger
import java.nio.file.Path
import java.nio.file.Paths

/**
 * State holder for editor UI state using StateFlow pattern.
 * 
 * Manages both domain state (documents, tabs) and UI-specific state (sidebar visibility, widths).
 * Coordinates with EditorDomain for business logic.
 */
class EditorStateHolder(
    private val editorDomain: EditorDomain,
    private val fileSystem: FileSystem,
    private val coroutineScope: CoroutineScope,
    private val settingsRepository: SettingsRepository?
) {
    // Domain state - maintain our own StateFlow
    private val _domainState = MutableStateFlow(EditorDomainState())
    val domainState: StateFlow<EditorDomainState> = _domainState.asStateFlow()
    
    // UI-specific state
    private val _leftSidebarVisible = MutableStateFlow(true)
    val leftSidebarVisible: StateFlow<Boolean> = _leftSidebarVisible.asStateFlow()
    
    private val _rightSidebarVisible = MutableStateFlow(true)
    val rightSidebarVisible: StateFlow<Boolean> = _rightSidebarVisible.asStateFlow()
    
    private val _leftSidebarWidth = MutableStateFlow(280.0)
    val leftSidebarWidth: StateFlow<Double> = _leftSidebarWidth.asStateFlow()
    
    private val _rightSidebarWidth = MutableStateFlow(280.0)
    val rightSidebarWidth: StateFlow<Double> = _rightSidebarWidth.asStateFlow()
    
    private val _activeRibbonButton = MutableStateFlow(RibbonButton.Files)
    val activeRibbonButton: StateFlow<RibbonButton> = _activeRibbonButton.asStateFlow()
    
    private val _activeRightPanel = MutableStateFlow(RightPanelType.Outline)
    val activeRightPanel: StateFlow<RightPanelType> = _activeRightPanel.asStateFlow()
    
    // Settings dialog state
    private val _settingsDialogOpen = MutableStateFlow(false)
    val settingsDialogOpen: StateFlow<Boolean> = _settingsDialogOpen.asStateFlow()
    
    private val _selectedSettingsCategory = MutableStateFlow(SettingsCategory.General)
    val selectedSettingsCategory: StateFlow<SettingsCategory> = _selectedSettingsCategory.asStateFlow()
    
    // Optional callback for auto-indexing on save
    var onFileSaved: ((String) -> Unit)? = null
    
    // Expose domain state properties as StateFlow for convenience
    val currentDirectory: StateFlow<Path?> = domainState.map { it.currentDirectory?.let { Paths.get(it) } }.stateIn(coroutineScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)
    val files: StateFlow<List<Path>> = domainState.map { it.files.map { Paths.get(it) } }.stateIn(coroutineScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())
    val documents: StateFlow<List<org.krypton.krypton.core.domain.editor.MarkdownDocument>> = domainState.map { it.documents }.stateIn(coroutineScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())
    val activeTabIndex: StateFlow<Int> = domainState.map { it.activeTabIndex }.stateIn(coroutineScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, -1)
    val activeDocument: StateFlow<org.krypton.krypton.core.domain.editor.MarkdownDocument?> = domainState.map { it.activeDocument }.stateIn(coroutineScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)
    val editingMode: StateFlow<org.krypton.krypton.core.domain.editor.FileTreeEditMode?> = domainState.map { it.editingMode }.stateIn(coroutineScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)
    val editingItemPath: StateFlow<Path?> = domainState.map { it.editingItemPath?.let { Paths.get(it) } }.stateIn(coroutineScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)
    val editingParentPath: StateFlow<Path?> = domainState.map { it.editingParentPath?.let { Paths.get(it) } }.stateIn(coroutineScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)
    val deletingPath: StateFlow<Path?> = domainState.map { it.deletingPath?.let { Paths.get(it) } }.stateIn(coroutineScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)
    val showRecentFoldersDialog: StateFlow<Boolean> = domainState.map { it.showRecentFoldersDialog }.stateIn(coroutineScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)
    
    // UI status for error handling
    private val _status = MutableStateFlow<UiStatus>(UiStatus.Idle)
    val status: StateFlow<UiStatus> = _status.asStateFlow()
    
    /**
     * Changes the current directory.
     */
    fun changeDirectory(path: Path?) {
        coroutineScope.launch {
            try {
                _status.value = UiStatus.Loading
                val pathString = path?.toString()
                val files = withContext(Dispatchers.IO) {
                    if (pathString != null) {
                        fileSystem.listFiles(pathString)
                    } else {
                        emptyList()
                    }
                }
                
                val newState = editorDomain.changeDirectory(_domainState.value, pathString, files)
                _domainState.value = newState
                _status.value = UiStatus.Idle
                
                if (path != null) {
                    AppLogger.action("FileExplorer", "FolderSelected", path.toString())
                }
            } catch (e: Exception) {
                _status.value = UiStatus.Error("Failed to change directory: ${e.message}", recoverable = true)
                AppLogger.e("EditorStateHolder", "Failed to change directory: ${e.message}", e)
            }
        }
    }
    
    /**
     * Changes directory and updates recent folders history.
     */
    fun changeDirectoryWithHistory(path: Path?) {
        changeDirectory(path)
        
        if (path != null && settingsRepository != null) {
            val pathString = path.toString()
            coroutineScope.launch {
                try {
                    val recent = settingsRepository.settingsFlow.value.app.recentFolders
                    val updated = (listOf(pathString) + recent)
                        .distinct()
                        .take(5)
                    settingsRepository.update { settings ->
                        settings.copy(
                            app = settings.app.copy(recentFolders = updated)
                        )
                    }
                } catch (e: Exception) {
                    AppLogger.e("EditorStateHolder", "Failed to update recent folders: ${e.message}", e)
                }
            }
        }
    }
    
    /**
     * Closes the current folder and shows recent folders dialog.
     */
    fun closeFolder() {
        val newState = editorDomain.changeDirectory(_domainState.value, null, emptyList())
            .copy(showRecentFoldersDialog = true)
        _domainState.value = newState
    }
    
    /**
     * Dismisses the recent folders dialog.
     */
    fun dismissRecentFoldersDialog() {
        _domainState.value = _domainState.value.copy(showRecentFoldersDialog = false)
    }
    
    /**
     * Refreshes the file list for the current directory.
     */
    fun refreshFiles() {
        coroutineScope.launch {
            try {
                val currentDir = _domainState.value.currentDirectory
                val files = withContext(Dispatchers.IO) {
                    if (currentDir != null) {
                        fileSystem.listFiles(currentDir)
                    } else {
                        emptyList()
                    }
                }
                _domainState.value = _domainState.value.copy(files = files)
            } catch (e: Exception) {
                AppLogger.e("EditorStateHolder", "Failed to refresh files: ${e.message}", e)
                _domainState.value = _domainState.value.copy(files = emptyList())
            }
        }
    }
    
    /**
     * Opens a tab for the given file path.
     */
    fun openTab(path: Path) {
        coroutineScope.launch {
            try {
                _status.value = UiStatus.Loading
                val pathString = path.toString()
                
                // Check if already open
                val existingIndex = _domainState.value.documents.indexOfFirst { it.path == pathString }
                if (existingIndex >= 0) {
                    val newState = editorDomain.switchTab(_domainState.value, existingIndex)
                    _domainState.value = newState
                    _status.value = UiStatus.Idle
                    AppLogger.action("Editor", "TabSwitched", pathString)
                    return@launch
                }
                
                // Load file content
                val content = withContext(Dispatchers.IO) {
                    fileSystem.readFile(pathString) ?: ""
                }
                
                // Use domain to open tab
                val newState = editorDomain.openTab(_domainState.value, pathString, content)
                _domainState.value = newState
                _status.value = UiStatus.Idle
                
                AppLogger.action("Editor", "TabOpened", pathString)
            } catch (e: Exception) {
                _status.value = UiStatus.Error("Failed to open file: ${e.message}", recoverable = true)
                AppLogger.e("EditorStateHolder", "Failed to open tab: ${e.message}", e)
            }
        }
    }
    
    /**
     * Closes a tab at the given index.
     */
    fun closeTab(index: Int) {
        coroutineScope.launch {
            val doc = _domainState.value.documents.getOrNull(index) ?: return@launch
            
            // Auto-save before closing
            if (doc.isDirty && doc.path != null) {
                try {
                    withContext(Dispatchers.IO) {
                        fileSystem.writeFile(doc.path, doc.text)
                    }
                    AppLogger.action("Editor", "FileSaved", doc.path)
                } catch (e: Exception) {
                    AppLogger.e("EditorStateHolder", "Failed to save file before closing tab: ${e.message}", e)
                }
            }
            
            val path = doc.path ?: "untitled"
            AppLogger.action("Editor", "TabClosed", path)
            
            // Use domain to close tab
            val newState = editorDomain.closeTab(_domainState.value, index)
            _domainState.value = newState
            
            // Auto-index if callback is set
            doc.path?.let { filePath ->
                if (filePath.endsWith(".md", ignoreCase = true)) {
                    onFileSaved?.invoke(filePath)
                }
            }
        }
    }
    
    /**
     * Switches to a different tab.
     */
    fun switchTab(index: Int) {
        coroutineScope.launch {
            // Auto-save current document before switching
            val currentIndex = _domainState.value.activeTabIndex
            if (currentIndex >= 0 && currentIndex < _domainState.value.documents.size) {
                val currentDoc = _domainState.value.documents[currentIndex]
                if (currentDoc.isDirty && currentDoc.path != null) {
                    try {
                        withContext(Dispatchers.IO) {
                            fileSystem.writeFile(currentDoc.path, currentDoc.text)
                        }
                        AppLogger.action("Editor", "FileSaved", currentDoc.path)
                    } catch (e: Exception) {
                        AppLogger.e("EditorStateHolder", "Failed to save file before switching tab: ${e.message}", e)
                    }
                }
            }
            
            val newState = editorDomain.switchTab(_domainState.value, index)
            _domainState.value = newState
            
            _domainState.value.documents.getOrNull(index)?.path?.let { path ->
                AppLogger.action("Editor", "TabSwitched", path)
            }
        }
    }
    
    /**
     * Updates the content of the active tab.
     */
    fun updateTabContent(content: String, pushToHistory: Boolean = true) {
        val newState = editorDomain.updateTabContent(_domainState.value, content, pushToHistory)
        _domainState.value = newState
    }
    
    /**
     * Performs undo operation.
     */
    fun undo(): Boolean {
        val newState = editorDomain.undo(_domainState.value) ?: return false
        _domainState.value = newState
        return true
    }
    
    /**
     * Performs redo operation.
     */
    fun redo(): Boolean {
        val newState = editorDomain.redo(_domainState.value) ?: return false
        _domainState.value = newState
        return true
    }
    
    /**
     * Checks if undo is available.
     */
    fun canUndo(): Boolean = editorDomain.canUndo(_domainState.value)
    
    /**
     * Checks if redo is available.
     */
    fun canRedo(): Boolean = editorDomain.canRedo(_domainState.value)
    
    /**
     * Gets the active document.
     */
    fun getActiveTab(): org.krypton.krypton.core.domain.editor.MarkdownDocument? {
        return _domainState.value.activeDocument
    }
    
    /**
     * Saves the active tab.
     */
    fun saveActiveTab() {
        coroutineScope.launch {
            try {
                _status.value = UiStatus.Loading
                val activeDoc = _domainState.value.activeDocument ?: return@launch
                if (activeDoc.path != null) {
                    val success = withContext(Dispatchers.IO) {
                        fileSystem.writeFile(activeDoc.path, activeDoc.text)
                    }
                    if (success) {
                        AppLogger.action("Editor", "FileSaved", activeDoc.path)
                        
                        val newState = editorDomain.markActiveTabSaved(_domainState.value)
                        _domainState.value = newState
                        _status.value = UiStatus.Success
                        
                        // Auto-index if callback is set
                        if (activeDoc.path.endsWith(".md", ignoreCase = true)) {
                            onFileSaved?.invoke(activeDoc.path)
                        }
                    } else {
                        _status.value = UiStatus.Error("Failed to save file: write operation returned false", recoverable = true)
                        AppLogger.e("EditorStateHolder", "Failed to save file: write operation returned false", null)
                    }
                }
            } catch (e: Exception) {
                _status.value = UiStatus.Error("Failed to save file: ${e.message}", recoverable = true)
                AppLogger.e("EditorStateHolder", "Failed to save active tab: ${e.message}", e)
            }
        }
    }
    
    /**
     * Clears the error status.
     */
    fun clearError() {
        if (_status.value is UiStatus.Error) {
            _status.value = UiStatus.Idle
        }
    }
    
    /**
     * Toggles the view mode of the active tab.
     */
    fun toggleViewMode() {
        val newState = editorDomain.toggleViewMode(_domainState.value)
        _domainState.value = newState
    }
    
    // UI state management
    fun toggleLeftSidebar() {
        _leftSidebarVisible.value = !_leftSidebarVisible.value
        AppLogger.action("LeftSidebar", if (_leftSidebarVisible.value) "Opened" else "Closed")
    }
    
    fun toggleRightSidebar() {
        _rightSidebarVisible.value = !_rightSidebarVisible.value
        AppLogger.action("RightSidebar", if (_rightSidebarVisible.value) "Opened" else "Closed")
    }
    
    fun updateLeftSidebarWidth(width: Double, minWidth: Double = 200.0, maxWidth: Double = 400.0) {
        _leftSidebarWidth.value = width.coerceIn(minWidth, maxWidth)
    }
    
    fun updateRightSidebarWidth(width: Double, minWidth: Double = 200.0, maxWidth: Double = 400.0) {
        _rightSidebarWidth.value = width.coerceIn(minWidth, maxWidth)
    }
    
    fun updateActiveRibbonButton(button: RibbonButton) {
        _activeRibbonButton.value = button
        AppLogger.action("Ribbon", "ButtonClicked", button.name)
    }
    
    fun updateActiveRightPanel(type: RightPanelType) {
        _activeRightPanel.value = type
        AppLogger.action("RightPanel", "Switched", type.name)
        if (type == RightPanelType.Chat && !_rightSidebarVisible.value) {
            _rightSidebarVisible.value = true
        }
    }
    
    // Settings dialog
    fun openSettingsDialog() {
        _settingsDialogOpen.value = true
    }
    
    fun closeSettingsDialog() {
        _settingsDialogOpen.value = false
    }
    
    fun selectSettingsCategory(category: SettingsCategory) {
        _selectedSettingsCategory.value = category
    }
    
    fun openSettingsJson() {
        // This will be handled by the UI layer
    }
    
    // Search state (temporary - will move to SearchStateHolder)
    // Search functionality is now handled by SearchStateHolder
    // These methods are kept for backward compatibility but do nothing
    @Deprecated("Use SearchStateHolder instead")
    fun openSearchDialog(showReplace: Boolean = false) {
        // No-op - search is handled by SearchStateHolder
    }
    
    @Deprecated("Use SearchStateHolder instead")
    fun closeSearchDialog() {
        // No-op - search is handled by SearchStateHolder
    }
    
    // Search functionality is now handled by SearchStateHolder
    // These methods are kept for backward compatibility but do nothing
    @Deprecated("Use SearchStateHolder instead")
    fun updateSearchState(update: (Any) -> Any) {
        // No-op - search is handled by SearchStateHolder
    }
    
    @Deprecated("Use SearchStateHolder instead")
    fun findNext(): Boolean {
        // No-op - search is handled by SearchStateHolder
        return false
    }
    
    @Deprecated("Use SearchStateHolder instead")
    fun findPrevious(): Boolean {
        // No-op - search is handled by SearchStateHolder
        return false
    }
    
    // File tree operations
    fun startCreatingNewFile(parentPath: Path) {
        _domainState.value = _domainState.value.copy(
            editingMode = org.krypton.krypton.core.domain.editor.FileTreeEditMode.CreatingFile,
            editingParentPath = parentPath.toString(),
            editingItemPath = null
        )
    }
    
    fun startCreatingNewFolder(parentPath: Path) {
        _domainState.value = _domainState.value.copy(
            editingMode = org.krypton.krypton.core.domain.editor.FileTreeEditMode.CreatingFolder,
            editingParentPath = parentPath.toString(),
            editingItemPath = null
        )
    }
    
    fun startRenamingItem(path: Path) {
        _domainState.value = _domainState.value.copy(
            editingMode = org.krypton.krypton.core.domain.editor.FileTreeEditMode.Renaming,
            editingItemPath = path.toString(),
            editingParentPath = null
        )
    }
    
    fun cancelEditing() {
        _domainState.value = _domainState.value.copy(
            editingMode = null,
            editingItemPath = null,
            editingParentPath = null
        )
    }
    
    fun confirmCreateFile(name: String, parentPath: Path) {
        coroutineScope.launch {
            if (name.isNotBlank()) {
                val newFile = parentPath.resolve(name)
                val success = withContext(Dispatchers.IO) {
                    fileSystem.createFile(newFile.toString())
                }
                if (success) {
                    AppLogger.action("FileExplorer", "CreateFile", newFile.toString())
                    refreshFiles()
                    openTab(newFile)
                } else {
                    AppLogger.e("FileExplorer", "Failed to create file: $newFile", null)
                }
            }
            cancelEditing()
        }
    }
    
    fun confirmCreateFolder(name: String, parentPath: Path) {
        coroutineScope.launch {
            if (name.isNotBlank()) {
                val newFolder = parentPath.resolve(name)
                val success = withContext(Dispatchers.IO) {
                    fileSystem.createDirectory(newFolder.toString())
                }
                if (success) {
                    AppLogger.action("FileExplorer", "CreateFolder", newFolder.toString())
                    refreshFiles()
                } else {
                    AppLogger.e("FileExplorer", "Failed to create folder: $newFolder", null)
                }
            }
            cancelEditing()
        }
    }
    
    fun confirmRename(oldPath: Path, newName: String) {
        coroutineScope.launch {
            if (newName.isNotBlank() && newName != oldPath.fileName.toString()) {
                val parent = oldPath.parent
                if (parent != null) {
                    val newPath = parent.resolve(newName)
                    val success = withContext(Dispatchers.IO) {
                        fileSystem.renameFile(oldPath.toString(), newPath.toString())
                    }
                    if (success) {
                        AppLogger.action("FileExplorer", "Rename", "${oldPath.fileName} -> $newName")
                        
                        // Update any open tabs with the old path
                        val updatedDocs = _domainState.value.documents.map { doc ->
                            if (doc.path == oldPath.toString()) {
                                doc.copy(path = newPath.toString())
                            } else {
                                doc
                            }
                        }
                        _domainState.value = _domainState.value.copy(documents = updatedDocs)
                        
                        refreshFiles()
                    } else {
                        AppLogger.e("FileExplorer", "Failed to rename: $oldPath -> $newPath", null)
                    }
                }
            }
            cancelEditing()
        }
    }
    
    fun deleteItem(path: Path) {
        _domainState.value = _domainState.value.copy(deletingPath = path.toString())
    }
    
    fun confirmDelete() {
        coroutineScope.launch {
            val pathString = _domainState.value.deletingPath ?: return@launch
            val isDir = withContext(Dispatchers.IO) {
                fileSystem.isDirectory(pathString)
            }
            
            // Close any open tabs for this file or files within this directory
            if (isDir) {
                val updatedDocs = _domainState.value.documents.filter { doc ->
                    doc.path == null || !doc.path!!.startsWith(pathString)
                }
                val newActiveIndex = if (updatedDocs.isEmpty()) {
                    -1
                } else if (_domainState.value.activeTabIndex >= updatedDocs.size) {
                    updatedDocs.size - 1
                } else {
                    _domainState.value.activeTabIndex
                }
                _domainState.value = _domainState.value.copy(
                    documents = updatedDocs,
                    activeTabIndex = newActiveIndex
                )
            } else {
                val indexToClose = _domainState.value.documents.indexOfFirst { it.path == pathString }
                if (indexToClose >= 0) {
                    closeTab(indexToClose)
                }
            }
            
            val success = withContext(Dispatchers.IO) {
                fileSystem.deleteFile(pathString)
            }
            if (success) {
                AppLogger.action("FileExplorer", "Delete", pathString)
                refreshFiles()
            } else {
                AppLogger.e("FileExplorer", "Failed to delete: $pathString", null)
            }
            _domainState.value = _domainState.value.copy(deletingPath = null)
        }
    }
    
    fun cancelDelete() {
        _domainState.value = _domainState.value.copy(deletingPath = null)
    }
}

// Legacy enums and types (for compatibility)
enum class RibbonButton {
    Files, Search, Bookmarks, Settings
}

enum class RightPanelType {
    Outline, Chat
}

enum class SettingsCategory {
    General, Editor, Appearance, UI, Colors, Keybindings, RAG
}

