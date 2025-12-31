package org.krypton.platform

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.krypton.data.repository.SettingsRepository
import org.koin.core.context.GlobalContext
import java.io.File

/**
 * Android implementation of VaultPicker using Storage Access Framework (SAF).
 * 
 * Uses SAF to allow users to select external directories for their vault.
 * The selected tree URI is persisted in settings and restored on app launch.
 */
class AndroidVaultPicker(
    private val context: Context
) : VaultPicker {
    
    private val settingsRepository: SettingsRepository by lazy {
        GlobalContext.get().get()
    }
    
    /**
     * Returns the current vault root from settings, or null if not set.
     * 
     * This should be called after the user has selected a vault via SAF.
     * The vault root is persisted in settings.app.vaultRootUri.
     */
    override suspend fun pickVaultRoot(): VaultRoot? = withContext(Dispatchers.IO) {
        val settings = settingsRepository.settingsFlow.value
        val vaultRootUri = settings.app.vaultRootUri
        
        if (vaultRootUri != null) {
            try {
                val uri = Uri.parse(vaultRootUri)
                val documentFile = DocumentFile.fromTreeUri(context, uri)
                val displayName = documentFile?.name ?: "Vault"
                VaultRoot(
                    id = vaultRootUri,
                    displayName = displayName
                )
            } catch (e: Exception) {
                // If URI is invalid, return null
                null
            }
        } else {
            // Fallback to default vault directory (external Documents/Vault)
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val dir = File(documentsDir, "Vault")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            // Use absolute path: typically /storage/emulated/0/Documents/Vault
            VaultRoot(
                id = dir.absolutePath,
                displayName = "Default Vault"
            )
        }
    }
    
    /**
     * Sets the vault root from a SAF tree URI.
     * This should be called from the Activity Result callback.
     * 
     * @param treeUri The tree URI from OpenDocumentTree result
     * @return The created VaultRoot, or null if URI is invalid
     */
    suspend fun setVaultRootFromUri(treeUri: Uri?): VaultRoot? = withContext(Dispatchers.IO) {
        if (treeUri == null) return@withContext null
        
        try {
            // Grant persistent permission with proper flags
            // The flags should match what was requested in the Intent
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
            
            val documentFile = DocumentFile.fromTreeUri(context, treeUri)
            val displayName = documentFile?.name ?: "Vault"
            val vaultRoot = VaultRoot(
                id = treeUri.toString(),
                displayName = displayName
            )
            
            // Persist URI in settings
            settingsRepository.update { current ->
                current.copy(
                    app = current.app.copy(
                        vaultRootUri = treeUri.toString()
                    )
                )
            }
            
            vaultRoot
        } catch (e: Exception) {
            android.util.Log.e("AndroidVaultPicker", "Failed to set vault root from URI: ${e.message}", e)
            null
        }
    }
    
    /**
     * @deprecated Use pickVaultRoot() instead
     */
    @Deprecated("Use pickVaultRoot() instead", ReplaceWith("pickVaultRoot()?.id"))
    override suspend fun pickVault(): String? {
        return pickVaultRoot()?.id
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
