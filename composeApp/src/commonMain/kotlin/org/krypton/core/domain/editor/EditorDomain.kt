package org.krypton.core.domain.editor

/**
 * Pure domain logic for editor operations.
 * 
 * This class contains business logic for managing documents, tabs, and content
 * without any platform-specific dependencies or file I/O operations.
 * 
 * File operations should be handled by the data layer and passed to this domain.
 */
class EditorDomain {
    
    private val undoRedoManagers = mutableMapOf<Int, UndoRedoManager>()
    
    /**
     * Gets or creates an undo/redo manager for a document tab.
     */
    private fun getUndoRedoManager(tabIndex: Int): UndoRedoManager {
        return undoRedoManagers.getOrPut(tabIndex) { UndoRedoManager() }
    }
    
    /**
     * Opens a new tab or switches to an existing tab.
     * 
     * @param currentState Current editor state
     * @param filePath Path to the file to open
     * @param fileContent Content of the file (loaded by data layer)
     * @return New editor state with the tab opened
     */
    fun openTab(
        currentState: EditorDomainState,
        filePath: String,
        fileContent: String
    ): EditorDomainState {
        // Check if document already exists
        val existingIndex = currentState.documents.indexOfFirst { it.path == filePath }
        if (existingIndex >= 0) {
            // Switch to existing tab
            return currentState.copy(
                activeTabIndex = existingIndex,
                selectedFile = filePath
            )
        }
        
        // Create new document
        val newDocument = MarkdownDocument(
            path = filePath,
            text = fileContent,
            isDirty = false
        )
        
        val newDocuments = currentState.documents + newDocument
        val newIndex = newDocuments.size - 1
        
        // Initialize undo/redo manager for new tab
        getUndoRedoManager(newIndex).initialize(fileContent)
        
        return currentState.copy(
            documents = newDocuments,
            activeTabIndex = newIndex,
            selectedFile = filePath
        )
    }
    
    /**
     * Closes a tab at the specified index.
     * 
     * @param currentState Current editor state
     * @param index Index of the tab to close
     * @return New editor state with the tab closed, and the active tab adjusted
     */
    fun closeTab(
        currentState: EditorDomainState,
        index: Int
    ): EditorDomainState {
        if (index < 0 || index >= currentState.documents.size) {
            return currentState
        }
        
        // Remove undo/redo manager
        undoRedoManagers.remove(index)
        
        // Reindex remaining managers
        val managersToReindex = undoRedoManagers.filter { it.key > index }.toList()
        undoRedoManagers.clear()
        managersToReindex.forEach { (oldKey, manager) ->
            undoRedoManagers[oldKey - 1] = manager
        }
        
        val newDocuments = currentState.documents.toMutableList().apply {
            removeAt(index)
        }
        
        // Adjust active tab index
        val newActiveIndex = when {
            newDocuments.isEmpty() -> -1
            currentState.activeTabIndex >= newDocuments.size -> newDocuments.size - 1
            currentState.activeTabIndex > index -> currentState.activeTabIndex - 1
            else -> currentState.activeTabIndex
        }
        
        val newSelectedFile = if (newActiveIndex >= 0 && newActiveIndex < newDocuments.size) {
            newDocuments[newActiveIndex].path
        } else {
            null
        }
        
        return currentState.copy(
            documents = newDocuments,
            activeTabIndex = newActiveIndex,
            selectedFile = newSelectedFile
        )
    }
    
    /**
     * Switches to a different tab.
     * 
     * @param currentState Current editor state
     * @param index Index of the tab to switch to
     * @return New editor state with the active tab changed
     */
    fun switchTab(
        currentState: EditorDomainState,
        index: Int
    ): EditorDomainState {
        if (index < 0 || index >= currentState.documents.size) {
            return currentState
        }
        
        // Mark current document as saved (if it was dirty, caller should save it first)
        val updatedDocuments = if (currentState.activeTabIndex >= 0 && 
            currentState.activeTabIndex < currentState.documents.size) {
            val currentDoc = currentState.documents[currentState.activeTabIndex]
            if (currentDoc.isDirty) {
                currentState.documents.toMutableList().apply {
                    set(currentState.activeTabIndex, currentDoc.copy(isDirty = false))
                }
            } else {
                currentState.documents
            }
        } else {
            currentState.documents
        }
        
        return currentState.copy(
            documents = updatedDocuments,
            activeTabIndex = index,
            selectedFile = updatedDocuments[index].path
        )
    }
    
    /**
     * Updates the content of the active tab.
     * 
     * @param currentState Current editor state
     * @param content New content for the active tab
     * @param pushToHistory Whether to push this change to undo history
     * @return New editor state with updated content
     */
    fun updateTabContent(
        currentState: EditorDomainState,
        content: String,
        pushToHistory: Boolean = true
    ): EditorDomainState {
        if (currentState.activeTabIndex < 0 || currentState.activeTabIndex >= currentState.documents.size) {
            return currentState
        }
        
        val doc = currentState.documents[currentState.activeTabIndex]
        
        // Push to undo history before updating (unless this is an undo/redo operation)
        if (pushToHistory) {
            getUndoRedoManager(currentState.activeTabIndex).pushState(doc.text)
        }
        
        val updatedDoc = doc.copy(text = content, isDirty = true)
        val updatedDocuments = currentState.documents.toMutableList().apply {
            set(currentState.activeTabIndex, updatedDoc)
        }
        
        return currentState.copy(documents = updatedDocuments)
    }
    
    /**
     * Performs an undo operation on the active tab.
     * 
     * @param currentState Current editor state
     * @return New editor state with undone content, or null if undo is not available
     */
    fun undo(currentState: EditorDomainState): EditorDomainState? {
        if (currentState.activeTabIndex < 0 || currentState.activeTabIndex >= currentState.documents.size) {
            return null
        }
        
        val doc = currentState.documents[currentState.activeTabIndex]
        val manager = getUndoRedoManager(currentState.activeTabIndex)
        val previousState = manager.undo(doc.text) ?: return null
        
        // Update content without pushing to history
        return updateTabContent(currentState, previousState, pushToHistory = false)
    }
    
    /**
     * Performs a redo operation on the active tab.
     * 
     * @param currentState Current editor state
     * @return New editor state with redone content, or null if redo is not available
     */
    fun redo(currentState: EditorDomainState): EditorDomainState? {
        if (currentState.activeTabIndex < 0 || currentState.activeTabIndex >= currentState.documents.size) {
            return null
        }
        
        val doc = currentState.documents[currentState.activeTabIndex]
        val manager = getUndoRedoManager(currentState.activeTabIndex)
        val nextState = manager.redo(doc.text) ?: return null
        
        // Update content without pushing to history
        return updateTabContent(currentState, nextState, pushToHistory = false)
    }
    
    /**
     * Checks if undo is available for the active tab.
     */
    fun canUndo(currentState: EditorDomainState): Boolean {
        if (currentState.activeTabIndex < 0 || currentState.activeTabIndex >= currentState.documents.size) {
            return false
        }
        return getUndoRedoManager(currentState.activeTabIndex).canUndo()
    }
    
    /**
     * Checks if redo is available for the active tab.
     */
    fun canRedo(currentState: EditorDomainState): Boolean {
        if (currentState.activeTabIndex < 0 || currentState.activeTabIndex >= currentState.documents.size) {
            return false
        }
        return getUndoRedoManager(currentState.activeTabIndex).canRedo()
    }
    
    /**
     * Marks the active tab as saved (not dirty).
     * 
     * @param currentState Current editor state
     * @return New editor state with the active tab marked as saved
     */
    fun markActiveTabSaved(currentState: EditorDomainState): EditorDomainState {
        if (currentState.activeTabIndex < 0 || currentState.activeTabIndex >= currentState.documents.size) {
            return currentState
        }
        
        val doc = currentState.documents[currentState.activeTabIndex]
        val updatedDoc = doc.copy(isDirty = false)
        val updatedDocuments = currentState.documents.toMutableList().apply {
            set(currentState.activeTabIndex, updatedDoc)
        }
        
        return currentState.copy(documents = updatedDocuments)
    }
    
    /**
     * Toggles the view mode of the active tab.
     * 
     * @param currentState Current editor state
     * @return New editor state with toggled view mode
     */
    fun toggleViewMode(currentState: EditorDomainState): EditorDomainState {
        if (currentState.activeTabIndex < 0 || currentState.activeTabIndex >= currentState.documents.size) {
            return currentState
        }
        
        val doc = currentState.documents[currentState.activeTabIndex]
        val newMode = when (doc.viewMode) {
            ViewMode.LivePreview -> ViewMode.Compiled
            ViewMode.Compiled -> ViewMode.LivePreview
        }
        
        val updatedDoc = doc.copy(viewMode = newMode)
        val updatedDocuments = currentState.documents.toMutableList().apply {
            set(currentState.activeTabIndex, updatedDoc)
        }
        
        return currentState.copy(documents = updatedDocuments)
    }
    
    /**
     * Updates the current directory and file list.
     * 
     * @param currentState Current editor state
     * @param directoryPath New directory path
     * @param files List of file paths in the directory
     * @return New editor state with updated directory
     */
    fun changeDirectory(
        currentState: EditorDomainState,
        directoryPath: String?,
        files: List<String>
    ): EditorDomainState {
        return currentState.copy(
            currentDirectory = directoryPath,
            files = files,
            selectedFile = null
        )
    }
}

