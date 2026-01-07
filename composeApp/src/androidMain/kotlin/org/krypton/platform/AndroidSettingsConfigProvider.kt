package org.krypton.platform

import android.content.Context
import android.content.SharedPreferences
import org.krypton.util.AppLogger

/**
 * Android implementation of SettingsConfigProvider.
 * 
 * On Android, the vault root is used as the project root.
 * Settings are read-only and loaded from vault root settings.json if available.
 */
class AndroidSettingsConfigProvider(
    private val context: Context
) : SettingsConfigProvider {
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("krypton_config", Context.MODE_PRIVATE)
    }
    
    private val settingsFileName = "settings.json"
    
    override fun getSettingsFilePath(vaultId: String?): String {
        // On Android, we don't support vault-specific settings in .krypton folder
        // Project root = vault root (where settings.json should be)
        
        // Check if custom path is set (for browsed settings.json files)
        val customPath = prefs.getString("settings_file_path", null)
        if (customPath != null) {
            AppLogger.d("AndroidSettingsConfigProvider", "Using custom settings path: $customPath")
            return customPath
        }
        
        // On Android, project root is the vault root
        // If vaultId is provided (vault root), use it as project root
        if (vaultId != null && vaultId.isNotBlank()) {
            // Try to convert vault root (could be SAF URI or file path) to file path
            // For SAF URIs, we can't easily access them here, so we'll return a path
            // that the caller can handle
            try {
                val vaultRootFile = java.io.File(vaultId)
                if (vaultRootFile.exists() && vaultRootFile.isDirectory) {
                    val projectRootSettings = java.io.File(vaultRootFile, settingsFileName)
                    AppLogger.d("AndroidSettingsConfigProvider", "Using vault root as project root: ${projectRootSettings.absolutePath}")
                    return projectRootSettings.absolutePath
                }
            } catch (e: Exception) {
                // vaultId might be a SAF URI, not a file path
                // In that case, we can't access it directly here
                AppLogger.d("AndroidSettingsConfigProvider", "Vault root is not a file path (might be SAF URI): $vaultId")
            }
            
            // If vaultId is a SAF URI or invalid path, construct a path string
            // The actual file access will need to use DocumentFile API elsewhere
            val constructedPath = if (vaultId.contains("://")) {
                // It's a URI, we can't use it directly with File API
                // Return a placeholder that indicates we need DocumentFile API
                vaultId + "/" + settingsFileName
            } else {
                // Try as file path
                java.io.File(vaultId, settingsFileName).absolutePath
            }
            return constructedPath
        }
        
        // No vault root available yet (first run)
        // Return a fallback path (settings won't be found, will use defaults)
        val fallbackPath = context.filesDir.resolve(settingsFileName).absolutePath
        AppLogger.d("AndroidSettingsConfigProvider", "No vault root available, using fallback: $fallbackPath")
        return fallbackPath
    }
    
    override fun getVaultSettingsPath(vaultId: String): String? {
        return try {
            val vaultFile = java.io.File(vaultId)
            val kryptonDir = java.io.File(vaultFile, ".krypton")
            val settingsFile = java.io.File(kryptonDir, "settings.json")
            settingsFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }
    
    override fun setSettingsFilePath(path: String): Boolean {
        return try {
            prefs.edit().putString("settings_file_path", path).apply()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override fun getConfigDirectory(): String {
        return context.filesDir.absolutePath
    }
}

