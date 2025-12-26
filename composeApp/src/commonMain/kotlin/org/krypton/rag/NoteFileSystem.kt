package org.krypton.rag

/**
 * Platform-agnostic class for accessing markdown note files.
 * 
 * This allows the RAG system to work with files regardless of platform
 * (Desktop JVM, Android, etc.).
 */
expect class NoteFileSystem(notesRoot: String?) {
    /**
     * Lists all markdown files (.md) in the notes directory.
     * 
     * @return List of file paths (as strings) relative to the notes root,
     *         or absolute paths depending on implementation
     */
    suspend fun listMarkdownFiles(): List<String>
    
    /**
     * Reads the content of a markdown file.
     * 
     * @param path File path (relative to notes root or absolute, depending on implementation)
     * @return File content as string, or null if file doesn't exist or can't be read
     */
    suspend fun readFile(path: String): String?
    
    /**
     * Gets the last modified time of a file.
     * 
     * @param path File path (relative to notes root or absolute, depending on implementation)
     * @return Last modified timestamp in milliseconds since epoch, or null if file doesn't exist
     */
    suspend fun getFileLastModified(path: String): Long?
}

