package org.krypton.platform

/**
 * Represents an entry in a vault directory (either a folder or a file).
 * 
 * @param name Display name of the entry
 * @param uri Platform-specific identifier (URI string or file path)
 */
sealed class NoteEntry {
    abstract val name: String
    abstract val uri: String
    
    /**
     * Represents a folder/directory entry.
     */
    data class Folder(
        override val name: String,
        override val uri: String
    ) : NoteEntry()
    
    /**
     * Represents a file entry.
     */
    data class File(
        override val name: String,
        override val uri: String
    ) : NoteEntry()
}

