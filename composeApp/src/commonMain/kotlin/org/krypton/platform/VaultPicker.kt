package org.krypton.platform

/**
 * Platform-agnostic interface for picking vaults/folders and files.
 * 
 * This allows the UI to request file/folder selection without knowing
 * the platform-specific implementation (JFileChooser on Desktop, 
 * Document Picker on Android, etc.).
 */
interface VaultPicker {
    /**
     * Opens a dialog to pick a vault/folder.
     * 
     * @return Selected vault root, or null if cancelled
     */
    suspend fun pickVaultRoot(): VaultRoot?
    
    /**
     * Opens a dialog to pick a file.
     * 
     * @param filter Optional file filter (e.g., JSON files only)
     * @return Selected file path as String, or null if cancelled
     */
    suspend fun pickFile(filter: FileFilter? = null): String?
    
    /**
     * @deprecated Use pickVaultRoot() instead
     */
    @Deprecated("Use pickVaultRoot() instead", ReplaceWith("pickVaultRoot()?.id"))
    suspend fun pickVault(): String? {
        return pickVaultRoot()?.id
    }
}

/**
 * File filter for file picker dialogs.
 */
data class FileFilter(
    val description: String,
    val extensions: List<String>
)

