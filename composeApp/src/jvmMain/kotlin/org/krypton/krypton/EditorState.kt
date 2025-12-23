package org.krypton.krypton

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.nio.file.Path

enum class RibbonButton {
    Files, Search, Bookmarks, Settings
}

enum class RightPanelType {
    Outline, Chat
}

enum class SettingsCategory {
    General, Editor, Appearance, UI, Colors, Keybindings, RAG
}

enum class ViewMode {
    LivePreview, Compiled
}

data class MarkdownDocument(
    val path: Path?,
    val text: String,  // Raw Markdown source
    val isDirty: Boolean = false,
    val viewMode: ViewMode = ViewMode.LivePreview
)

@Composable
fun rememberEditorState(coroutineScope: CoroutineScope = rememberCoroutineScope()): EditorState {
    return remember(coroutineScope) {
        EditorState(coroutineScope)
    }
}

class EditorState(
    private val coroutineScope: CoroutineScope
) {
    // Optional callback for auto-indexing on save
    var onFileSaved: ((String) -> Unit)? = null
    
    var currentDirectory by mutableStateOf<Path?>(null)
        private set

    var files by mutableStateOf<List<Path>>(emptyList())
        private set

    var selectedFile by mutableStateOf<Path?>(null)
        private set

    var fileContent by mutableStateOf("")
        private set

    var isCreatingNewFile by mutableStateOf(false)
        private set

    var newFileName by mutableStateOf("")
        private set

    // Document management
    var documents by mutableStateOf<List<MarkdownDocument>>(emptyList())
    private set

    var activeTabIndex by mutableStateOf(-1)
        private set

    // Undo/Redo management per document
    private val undoRedoManagers = mutableMapOf<Int, UndoRedoManager>()

    private fun getUndoRedoManager(tabIndex: Int): UndoRedoManager {
        return undoRedoManagers.getOrPut(tabIndex) { UndoRedoManager() }
    }

    fun changeDirectory(path: Path?) {
        currentDirectory = path
        selectedFile = null
        fileContent = ""
        isCreatingNewFile = false
        newFileName = ""
        refreshFiles()
    }
    
    fun changeDirectoryWithHistory(path: Path?, settingsRepository: SettingsRepository?) {
        changeDirectory(path)
        
        // Save to recent folders history (async)
        if (path != null && settingsRepository != null) {
            val pathString = path.toString()
            val recent = settingsRepository.settingsFlow.value.app.recentFolders
            val updated = (listOf(pathString) + recent)
                .distinct()
                .take(5)
            // Use the provided coroutine scope to call suspend function
            coroutineScope.launch {
                settingsRepository.update { settings ->
                    settings.copy(
                        app = settings.app.copy(recentFolders = updated)
                    )
                }
            }
        }
    }
    
    var showRecentFoldersDialog by mutableStateOf(false)
        private set
    
    fun closeFolder() {
        currentDirectory = null
        selectedFile = null
        fileContent = ""
        isCreatingNewFile = false
        newFileName = ""
        files = emptyList()
        showRecentFoldersDialog = true
    }
    
    fun dismissRecentFoldersDialog() {
        showRecentFoldersDialog = false
    }

    fun refreshFiles() {
        currentDirectory?.let { dir ->
            files = FileManager.listFiles(dir)
        } ?: run {
            files = emptyList()
        }
    }

    fun loadFileContent(path: Path) {
        fileContent = FileManager.readFile(path) ?: ""
    }

    fun updateFileContent(content: String) {
        fileContent = content
    }

    fun saveCurrentFile() {
        selectedFile?.let { file ->
            FileManager.writeFile(file, fileContent)
        }
    }

    fun startCreatingNewFile() {
        isCreatingNewFile = true
        newFileName = ""
        selectedFile = null
        fileContent = ""
    }

    fun cancelCreatingNewFile() {
        isCreatingNewFile = false
        newFileName = ""
    }

    fun updateNewFileName(name: String) {
        newFileName = name
    }

    fun createNewFile() {
        currentDirectory?.let { dir ->
            val newFile = dir.resolve(newFileName)
            FileManager.createFile(newFile)
            refreshFiles()
            openTab(newFile)
            isCreatingNewFile = false
            newFileName = ""
        }
    }

    // Document management functions
    fun openTab(path: Path) {
        // Check if document already exists
        val existingIndex = documents.indexOfFirst { it.path == path }
        if (existingIndex >= 0) {
            activeTabIndex = existingIndex
            selectedFile = path
            fileContent = documents[existingIndex].text
            // Ensure undo/redo manager exists
            getUndoRedoManager(existingIndex)
            return
        }

        // Load file content
        val content = FileManager.readFile(path) ?: ""
        val newDocument = MarkdownDocument(path = path, text = content, isDirty = false)
        
        documents = documents + newDocument
        val newIndex = documents.size - 1
        activeTabIndex = newIndex
        selectedFile = path
        fileContent = content
        
        // Initialize undo/redo manager for new tab
        getUndoRedoManager(newIndex).initialize(content)
    }

    fun closeTab(index: Int) {
        if (index < 0 || index >= documents.size) return

        // Auto-save before closing
        val doc = documents[index]
        if (doc.isDirty && doc.path != null) {
            FileManager.writeFile(doc.path, doc.text)
        }

        // Remove undo/redo manager
        undoRedoManagers.remove(index)
        // Reindex remaining managers
        val managersToReindex = undoRedoManagers.filter { it.key > index }.toList()
        undoRedoManagers.clear()
        managersToReindex.forEach { (oldKey, manager) ->
            undoRedoManagers[oldKey - 1] = manager
        }

        documents = documents.toMutableList().apply { removeAt(index) }

        // Adjust active tab index
        when {
            documents.isEmpty() -> {
                activeTabIndex = -1
                selectedFile = null
                fileContent = ""
            }
            activeTabIndex >= documents.size -> {
                activeTabIndex = documents.size - 1
                selectedFile = documents[activeTabIndex].path
                fileContent = documents[activeTabIndex].text
            }
            activeTabIndex > index -> {
                activeTabIndex--
                selectedFile = documents[activeTabIndex].path
                fileContent = documents[activeTabIndex].text
            }
            else -> {
                selectedFile = documents[activeTabIndex].path
                fileContent = documents[activeTabIndex].text
            }
        }
    }

    fun switchTab(index: Int) {
        if (index < 0 || index >= documents.size) return

        // Auto-save current document before switching
        if (activeTabIndex >= 0 && activeTabIndex < documents.size) {
            val currentDoc = documents[activeTabIndex]
            if (currentDoc.isDirty && currentDoc.path != null) {
                FileManager.writeFile(currentDoc.path, currentDoc.text)
                documents = documents.toMutableList().apply {
                    set(activeTabIndex, currentDoc.copy(isDirty = false))
                }
            }
        }

        activeTabIndex = index
        selectedFile = documents[index].path
        fileContent = documents[index].text
        
        // Update search matches for new document
        searchState?.let { currentState ->
            if (currentState.searchQuery.isNotEmpty()) {
                val matches = SearchEngine.findMatches(
                    text = documents[index].text,
                    query = currentState.searchQuery,
                    matchCase = currentState.matchCase,
                    wholeWords = currentState.wholeWords,
                    useRegex = currentState.useRegex
                )
                searchState = currentState.copy(
                    matches = matches,
                    currentMatchIndex = if (matches.isNotEmpty()) 0 else -1
                )
            }
        }
    }

    fun updateTabContent(content: String, pushToHistory: Boolean = true) {
        if (activeTabIndex >= 0 && activeTabIndex < documents.size) {
            val doc = documents[activeTabIndex]
            
            // Push to undo history before updating (unless this is an undo/redo operation)
            if (pushToHistory) {
                getUndoRedoManager(activeTabIndex).pushState(doc.text)
            }
            
            documents = documents.toMutableList().apply {
                set(activeTabIndex, doc.copy(text = content, isDirty = true))
            }
            fileContent = content
            
            // Update search matches if search is active
            searchState?.let { currentState ->
                if (currentState.searchQuery.isNotEmpty()) {
                    val matches = SearchEngine.findMatches(
                        text = content,
                        query = currentState.searchQuery,
                        matchCase = currentState.matchCase,
                        wholeWords = currentState.wholeWords,
                        useRegex = currentState.useRegex
                    )
                    searchState = currentState.copy(
                        matches = matches,
                        currentMatchIndex = if (matches.isNotEmpty() && currentState.currentMatchIndex < matches.size) {
                            currentState.currentMatchIndex
                        } else if (matches.isNotEmpty()) {
                            0
                        } else {
                            -1
                        }
                    )
                }
            }
        }
    }

    fun undo(): Boolean {
        if (activeTabIndex < 0 || activeTabIndex >= documents.size) return false
        
        val doc = documents[activeTabIndex]
        val manager = getUndoRedoManager(activeTabIndex)
        val previousState = manager.undo(doc.text) ?: return false
        
        // Update content without pushing to history
        updateTabContent(previousState, pushToHistory = false)
        return true
    }

    fun redo(): Boolean {
        if (activeTabIndex < 0 || activeTabIndex >= documents.size) return false
        
        val doc = documents[activeTabIndex]
        val manager = getUndoRedoManager(activeTabIndex)
        val nextState = manager.redo(doc.text) ?: return false
        
        // Update content without pushing to history
        updateTabContent(nextState, pushToHistory = false)
        return true
    }

    fun canUndo(): Boolean {
        if (activeTabIndex < 0 || activeTabIndex >= documents.size) return false
        return getUndoRedoManager(activeTabIndex).canUndo()
    }

    fun canRedo(): Boolean {
        if (activeTabIndex < 0 || activeTabIndex >= documents.size) return false
        return getUndoRedoManager(activeTabIndex).canRedo()
    }

    fun getActiveTab(): MarkdownDocument? {
        return if (activeTabIndex >= 0 && activeTabIndex < documents.size) {
            documents[activeTabIndex]
        } else {
            null
        }
    }

    fun saveActiveTab() {
        if (activeTabIndex >= 0 && activeTabIndex < documents.size) {
            val doc = documents[activeTabIndex]
            if (doc.path != null) {
                FileManager.writeFile(doc.path, doc.text)
                documents = documents.toMutableList().apply {
                    set(activeTabIndex, doc.copy(isDirty = false))
                }
                
                // Auto-index if callback is set and file is markdown
                val filePath = doc.path.toString()
                if (filePath.endsWith(".md", ignoreCase = true)) {
                    onFileSaved?.invoke(filePath)
                }
            }
        }
    }

    fun toggleViewMode() {
        if (activeTabIndex >= 0 && activeTabIndex < documents.size) {
            val doc = documents[activeTabIndex]
            val newMode = when (doc.viewMode) {
                ViewMode.LivePreview -> ViewMode.Compiled
                ViewMode.Compiled -> ViewMode.LivePreview
            }
            documents = documents.toMutableList().apply {
                set(activeTabIndex, doc.copy(viewMode = newMode))
            }
        }
    }

    // Legacy functions for backward compatibility
    fun selectFile(path: Path) {
        openTab(path)
    }

    // Sidebar state
    var leftSidebarVisible by mutableStateOf(true)
        private set

    var rightSidebarVisible by mutableStateOf(true)
        private set

    var leftSidebarWidth by mutableStateOf(280.dp)
        private set

    var rightSidebarWidth by mutableStateOf(280.dp)
        private set

    var activeRibbonButton by mutableStateOf(RibbonButton.Files)
        private set

    var activeRightPanel by mutableStateOf(RightPanelType.Outline)
        private set

    fun toggleLeftSidebar() {
        leftSidebarVisible = !leftSidebarVisible
    }

    fun toggleRightSidebar() {
        rightSidebarVisible = !rightSidebarVisible
    }

    fun updateActiveRightPanel(type: RightPanelType) {
        activeRightPanel = type
        // Open sidebar if closed when switching to chat
        if (type == RightPanelType.Chat && !rightSidebarVisible) {
            rightSidebarVisible = true
        }
    }

    fun updateLeftSidebarWidth(width: androidx.compose.ui.unit.Dp, minWidth: androidx.compose.ui.unit.Dp = 200.dp, maxWidth: androidx.compose.ui.unit.Dp = 400.dp) {
        leftSidebarWidth = if (width < minWidth) minWidth else if (width > maxWidth) maxWidth else width
    }

    fun updateRightSidebarWidth(width: androidx.compose.ui.unit.Dp, minWidth: androidx.compose.ui.unit.Dp = 200.dp, maxWidth: androidx.compose.ui.unit.Dp = 400.dp) {
        rightSidebarWidth = if (width < minWidth) minWidth else if (width > maxWidth) maxWidth else width
    }

    fun updateActiveRibbonButton(button: RibbonButton) {
        activeRibbonButton = button
    }

    // Settings dialog state
    var settingsDialogOpen by mutableStateOf(false)
        private set

    var selectedSettingsCategory by mutableStateOf(SettingsCategory.General)
        private set

    fun openSettingsDialog() {
        settingsDialogOpen = true
    }

    fun closeSettingsDialog() {
        settingsDialogOpen = false
    }

    fun selectSettingsCategory(category: SettingsCategory) {
        selectedSettingsCategory = category
    }

    fun openSettingsJson() {
        val settingsPath = SettingsPersistence.getSettingsFilePath()
        openTab(settingsPath)
    }

    // Search state
    var searchState by mutableStateOf<SearchState?>(null)
        private set

    fun openSearchDialog(showReplace: Boolean = false) {
        searchState = SearchState(showReplace = showReplace)
    }

    fun closeSearchDialog() {
        searchState = null
    }

    fun updateSearchState(update: (SearchState) -> SearchState) {
        searchState = searchState?.let { currentState ->
            val updated = update(currentState)
            // Recalculate matches if document text changed
            val activeDoc = getActiveTab()
            if (activeDoc != null && updated.searchQuery.isNotEmpty()) {
                val matches = SearchEngine.findMatches(
                    text = activeDoc.text,
                    query = updated.searchQuery,
                    matchCase = updated.matchCase,
                    wholeWords = updated.wholeWords,
                    useRegex = updated.useRegex
                )
                updated.copy(
                    matches = matches,
                    currentMatchIndex = if (matches.isNotEmpty() && updated.currentMatchIndex < matches.size) {
                        updated.currentMatchIndex
                    } else if (matches.isNotEmpty()) {
                        0
                    } else {
                        -1
                    }
                )
            } else {
                updated
            }
        }
    }

    fun findNext(): Boolean {
        val state = searchState ?: return false
        if (state.matches.isEmpty()) return false
        
        val nextIndex = if (state.currentMatchIndex < state.matches.size - 1) {
            state.currentMatchIndex + 1
        } else {
            0 // Wrap around
        }
        searchState = state.copy(currentMatchIndex = nextIndex)
        return true
    }

    fun findPrevious(): Boolean {
        val state = searchState ?: return false
        if (state.matches.isEmpty()) return false
        
        val prevIndex = if (state.currentMatchIndex > 0) {
            state.currentMatchIndex - 1
        } else {
            state.matches.size - 1 // Wrap around
        }
        searchState = state.copy(currentMatchIndex = prevIndex)
        return true
    }
}

