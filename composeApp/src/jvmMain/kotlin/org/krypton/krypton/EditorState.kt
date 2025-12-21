package org.krypton.krypton

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import java.nio.file.Path

enum class RibbonButton {
    Files, Search, Bookmarks, Settings
}

enum class SettingsCategory {
    General, Editor, Appearance, Keybindings
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
fun rememberEditorState(): EditorState {
    return remember {
        EditorState()
    }
}

class EditorState {
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

    fun changeDirectory(path: Path?) {
        currentDirectory = path
        selectedFile = null
        fileContent = ""
        isCreatingNewFile = false
        newFileName = ""
        refreshFiles()
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
            return
        }

        // Load file content
        val content = FileManager.readFile(path) ?: ""
        val newDocument = MarkdownDocument(path = path, text = content, isDirty = false)
        
        documents = documents + newDocument
        activeTabIndex = documents.size - 1
        selectedFile = path
        fileContent = content
    }

    fun closeTab(index: Int) {
        if (index < 0 || index >= documents.size) return

        // Auto-save before closing
        val doc = documents[index]
        if (doc.isDirty && doc.path != null) {
            FileManager.writeFile(doc.path, doc.text)
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
    }

    fun updateTabContent(content: String) {
        if (activeTabIndex >= 0 && activeTabIndex < documents.size) {
            val doc = documents[activeTabIndex]
            documents = documents.toMutableList().apply {
                set(activeTabIndex, doc.copy(text = content, isDirty = true))
            }
            fileContent = content
        }
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

    var leftSidebarWidth by mutableStateOf(ObsidianTheme.SidebarDefaultWidth)
        private set

    var rightSidebarWidth by mutableStateOf(ObsidianTheme.SidebarDefaultWidth)
        private set

    var activeRibbonButton by mutableStateOf(RibbonButton.Files)
        private set

    fun toggleLeftSidebar() {
        leftSidebarVisible = !leftSidebarVisible
    }

    fun toggleRightSidebar() {
        rightSidebarVisible = !rightSidebarVisible
    }

    fun updateLeftSidebarWidth(width: androidx.compose.ui.unit.Dp) {
        val minWidth = ObsidianTheme.SidebarMinWidth
        val maxWidth = ObsidianTheme.SidebarMaxWidth
        leftSidebarWidth = if (width < minWidth) minWidth else if (width > maxWidth) maxWidth else width
    }

    fun updateRightSidebarWidth(width: androidx.compose.ui.unit.Dp) {
        val minWidth = ObsidianTheme.SidebarMinWidth
        val maxWidth = ObsidianTheme.SidebarMaxWidth
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
}

