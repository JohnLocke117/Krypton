package org.krypton.krypton.core.domain.editor

/**
 * File tree editing mode.
 */
enum class FileTreeEditMode {
    CreatingFile, CreatingFolder, Renaming
}

/**
 * Immutable state representation of the editor domain.
 * 
 * This represents the core editor state without UI-specific concerns
 * like sidebar visibility or widths.
 */
data class EditorDomainState(
    /**
     * List of open documents.
     */
    val documents: List<MarkdownDocument> = emptyList(),
    
    /**
     * Index of the currently active tab (-1 if no tab is active).
     */
    val activeTabIndex: Int = -1,
    
    /**
     * Current directory path (as string for platform independence).
     */
    val currentDirectory: String? = null,
    
    /**
     * List of file paths in the current directory.
     */
    val files: List<String> = emptyList(),
    
    /**
     * Currently selected file path.
     */
    val selectedFile: String? = null,
    
    /**
     * File tree editing mode (creating file, creating folder, or renaming).
     */
    val editingMode: FileTreeEditMode? = null,
    
    /**
     * Path of the item being edited (for renaming).
     */
    val editingItemPath: String? = null,
    
    /**
     * Path of the parent directory where a new item is being created.
     */
    val editingParentPath: String? = null,
    
    /**
     * Path of the item being deleted (for confirmation dialog).
     */
    val deletingPath: String? = null,
    
    /**
     * Whether to show the recent folders dialog.
     */
    val showRecentFoldersDialog: Boolean = false
) {
    /**
     * Gets the active document, if any.
     */
    val activeDocument: MarkdownDocument?
        get() = if (activeTabIndex >= 0 && activeTabIndex < documents.size) {
            documents[activeTabIndex]
        } else {
            null
        }
    
    /**
     * Checks if there are any open documents.
     */
    val hasDocuments: Boolean
        get() = documents.isNotEmpty()
    
    /**
     * Gets the number of open documents.
     */
    val documentCount: Int
        get() = documents.size
}

