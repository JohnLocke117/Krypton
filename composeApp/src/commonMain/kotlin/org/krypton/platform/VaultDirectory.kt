package org.krypton.platform

/**
 * Represents a directory within a vault (either root or a subfolder).
 * 
 * On Android: uri is a SAF tree URI string (e.g., "content://...")
 * On Desktop: uri is a file system path (e.g., "/Users/name/Documents/vault")
 * 
 * @param uri Platform-specific identifier (URI string or file path)
 * @param displayPath Human-readable path for display in UI (e.g., "Documents/Vault" or "Vault/Subfolder")
 */
data class VaultDirectory(
    val uri: String,
    val displayPath: String
)

