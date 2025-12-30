package org.krypton.platform

import android.content.Context
import java.io.File

/**
 * Android implementation of VaultPicker.
 * 
 * For v1, this uses an app-internal directory approach.
 * In a future implementation, this could be enhanced to use Storage Access Framework
 * (SAF) for user-selected external directories.
 */
class AndroidVaultPicker(
    private val context: Context
) : VaultPicker {
    
    /**
     * Returns the default vault directory (app-internal storage).
     * Creates the directory if it doesn't exist.
     * 
     * TODO: Future enhancement - implement Storage Access Framework (SAF) picker
     * to allow users to select external directories for their vault.
     */
    override suspend fun pickVault(): String? {
        val dir = File(context.filesDir, "vault")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir.absolutePath
    }
    
    /**
     * File picking is not yet implemented for Android.
     * 
     * TODO: Implement using Storage Access Framework or Document Picker
     * This would require Activity reference and proper result handling via Activity Result API.
     */
    override suspend fun pickFile(filter: FileFilter?): String? {
        // TODO: Implement using Storage Access Framework or Document Picker
        // For v1, return null (can be enhanced later)
        return null
    }
}

