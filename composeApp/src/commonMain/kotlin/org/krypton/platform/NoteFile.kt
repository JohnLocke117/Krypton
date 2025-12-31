package org.krypton.platform

/**
 * Represents a markdown note file within a vault.
 * 
 * @param path Relative path from vault root (e.g., "folder/note.md")
 * @param name Display name (filename without extension, e.g., "note")
 * @param fullPath Full path for display (e.g., "folder/note.md")
 */
data class NoteFile(
    val path: String,
    val name: String,
    val fullPath: String
)

