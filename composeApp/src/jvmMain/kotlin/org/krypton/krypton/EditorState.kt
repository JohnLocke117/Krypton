package org.krypton.krypton

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import java.nio.file.Path

enum class RibbonButton {
    Files, Search, Bookmarks, Settings
}

data class Tab(
    val path: Path,
    var content: String,
    var isModified: Boolean = false
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

    // Tab management
    var tabs by mutableStateOf<List<Tab>>(emptyList())
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

    // Tab management functions
    fun openTab(path: Path) {
        // Check if tab already exists
        val existingIndex = tabs.indexOfFirst { it.path == path }
        if (existingIndex >= 0) {
            activeTabIndex = existingIndex
            selectedFile = path
            fileContent = tabs[existingIndex].content
            return
        }

        // Load file content
        val content = FileManager.readFile(path) ?: ""
        val newTab = Tab(path = path, content = content, isModified = false)
        
        tabs = tabs + newTab
        activeTabIndex = tabs.size - 1
        selectedFile = path
        fileContent = content
    }

    fun closeTab(index: Int) {
        if (index < 0 || index >= tabs.size) return

        // Auto-save before closing
        val tab = tabs[index]
        if (tab.isModified) {
            FileManager.writeFile(tab.path, tab.content)
        }

        tabs = tabs.toMutableList().apply { removeAt(index) }

        // Adjust active tab index
        when {
            tabs.isEmpty() -> {
                activeTabIndex = -1
                selectedFile = null
                fileContent = ""
            }
            activeTabIndex >= tabs.size -> {
                activeTabIndex = tabs.size - 1
                selectedFile = tabs[activeTabIndex].path
                fileContent = tabs[activeTabIndex].content
            }
            activeTabIndex > index -> {
                activeTabIndex--
                selectedFile = tabs[activeTabIndex].path
                fileContent = tabs[activeTabIndex].content
            }
            else -> {
                selectedFile = tabs[activeTabIndex].path
                fileContent = tabs[activeTabIndex].content
            }
        }
    }

    fun switchTab(index: Int) {
        if (index < 0 || index >= tabs.size) return

        // Auto-save current tab before switching
        if (activeTabIndex >= 0 && activeTabIndex < tabs.size) {
            val currentTab = tabs[activeTabIndex]
            if (currentTab.isModified) {
                FileManager.writeFile(currentTab.path, currentTab.content)
                currentTab.isModified = false
            }
        }

        activeTabIndex = index
        selectedFile = tabs[index].path
        fileContent = tabs[index].content
    }

    fun updateTabContent(content: String) {
        if (activeTabIndex >= 0 && activeTabIndex < tabs.size) {
            val tab = tabs[activeTabIndex]
            tab.content = content
            tab.isModified = true
            fileContent = content
        }
    }

    fun getActiveTab(): Tab? {
        return if (activeTabIndex >= 0 && activeTabIndex < tabs.size) {
            tabs[activeTabIndex]
        } else {
            null
        }
    }

    fun saveActiveTab() {
        if (activeTabIndex >= 0 && activeTabIndex < tabs.size) {
            val tab = tabs[activeTabIndex]
            FileManager.writeFile(tab.path, tab.content)
            tab.isModified = false
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
}

