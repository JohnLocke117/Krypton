package org.krypton.platform

/**
 * Represents a vault root directory selected by the user.
 * 
 * On Desktop: id is a file system path (e.g., "/Users/name/Documents/vault")
 * On Android: id is a tree URI string (e.g., "content://com.android.externalstorage.documents/tree/primary%3ADocuments")
 * 
 * @param id Platform-specific identifier (path or URI string)
 * @param displayName Human-readable name for display in UI
 */
data class VaultRoot(
    val id: String,
    val displayName: String
)

