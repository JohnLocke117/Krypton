package org.krypton.ui.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.krypton.data.files.FileSystem
import org.krypton.platform.NoteEntry
import org.krypton.platform.VaultDirectory
import org.krypton.util.AppLogger

/**
 * Android-specific state holder for Notes screen navigation.
 * 
 * Manages the directory stack for navigating through vault folders
 * and listing entries (folders and files) in the current directory.
 */
class AndroidNotesStateHolder(
    private val fileSystem: FileSystem,
    private val coroutineScope: CoroutineScope
) {
    private val _directoryStack = MutableStateFlow<List<VaultDirectory>>(emptyList())
    val directoryStack: StateFlow<List<VaultDirectory>> = _directoryStack.asStateFlow()
    
    private val _entries = MutableStateFlow<List<NoteEntry>>(emptyList())
    val entries: StateFlow<List<NoteEntry>> = _entries.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    /**
     * Gets the current directory (top of stack).
     */
    val currentDirectory: VaultDirectory?
        get() = _directoryStack.value.lastOrNull()
    
    /**
     * Checks if we can navigate up (not at root).
     */
    val canNavigateUp: Boolean
        get() = _directoryStack.value.size > 1
    
    /**
     * Initializes with the vault root directory.
     */
    fun initializeWithRoot(root: VaultDirectory) {
        coroutineScope.launch {
            _directoryStack.value = listOf(root)
            loadEntries(root)
        }
    }
    
    /**
     * Navigates into a folder by pushing it onto the stack.
     */
    fun navigateIntoFolder(folder: NoteEntry.Folder) {
        coroutineScope.launch {
            val newDirectory = VaultDirectory(
                uri = folder.uri,
                displayPath = buildDisplayPath(folder.name)
            )
            _directoryStack.value = _directoryStack.value + newDirectory
            loadEntries(newDirectory)
        }
    }
    
    /**
     * Navigates up one level by popping the top directory from the stack.
     */
    fun navigateUp() {
        if (!canNavigateUp) return
        
        coroutineScope.launch {
            val newStack = _directoryStack.value.dropLast(1)
            _directoryStack.value = newStack
            val currentDir = newStack.lastOrNull()
            if (currentDir != null) {
                loadEntries(currentDir)
            }
        }
    }
    
    /**
     * Refreshes the entries in the current directory.
     */
    fun refresh() {
        val currentDir = currentDirectory
        if (currentDir != null) {
            coroutineScope.launch {
                loadEntries(currentDir)
            }
        }
    }
    
    /**
     * Loads entries for the given directory.
     */
    private suspend fun loadEntries(directory: VaultDirectory) {
        _isLoading.value = true
        try {
            val androidFileSystem = fileSystem as? org.krypton.data.files.impl.AndroidFileSystem
            val entriesList = if (androidFileSystem != null) {
                androidFileSystem.listEntries(directory)
            } else {
                emptyList()
            }
            _entries.value = entriesList
        } catch (e: Exception) {
            AppLogger.e("AndroidNotesStateHolder", "Failed to load entries: ${e.message}", e)
            _entries.value = emptyList()
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Builds a display path from the current stack.
     */
    private fun buildDisplayPath(folderName: String): String {
        val stack = _directoryStack.value
        if (stack.isEmpty()) {
            return folderName
        }
        val parentPath = stack.last().displayPath
        return if (parentPath.isEmpty() || parentPath == "Vault") {
            folderName
        } else {
            "$parentPath/$folderName"
        }
    }
}

