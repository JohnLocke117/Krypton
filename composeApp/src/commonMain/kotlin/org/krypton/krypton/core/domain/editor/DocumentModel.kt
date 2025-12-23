package org.krypton.krypton.core.domain.editor

/**
 * Represents a view mode for the editor.
 */
enum class ViewMode {
    LivePreview,
    Compiled
}

/**
 * Represents a markdown document in the editor.
 * 
 * This is a domain model that represents the state of a document
 * without platform-specific dependencies.
 */
data class MarkdownDocument(
    /**
     * The file path of the document, or null if unsaved.
     */
    val path: String?,
    
    /**
     * The raw markdown source text.
     */
    val text: String,
    
    /**
     * Whether the document has unsaved changes.
     */
    val isDirty: Boolean = false,
    
    /**
     * The current view mode for the document.
     */
    val viewMode: ViewMode = ViewMode.LivePreview
)

