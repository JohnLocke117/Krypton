package org.krypton.platform

import android.content.Context
import android.content.SharedPreferences

/**
 * Android implementation of SettingsConfigProvider using SharedPreferences.
 */
class AndroidSettingsConfigProvider(
    private val context: Context
) : SettingsConfigProvider {
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("krypton_config", Context.MODE_PRIVATE)
    }
    
    private val settingsFileName = "settings.json"
    
    override fun getSettingsFilePath(vaultId: String?): String {
        // Check for vault-specific settings first
        if (vaultId != null && vaultId.isNotBlank()) {
            val vaultPath = getVaultSettingsPath(vaultId)
            if (vaultPath != null) {
                val vaultSettingsFile = java.io.File(vaultPath)
                if (vaultSettingsFile.exists()) {
                    return vaultPath
                }
            }
        }
        
        // Check if custom path is set
        val customPath = prefs.getString("settings_file_path", null)
        if (customPath != null) {
            return customPath
        }
        
        // Default to app's internal files directory
        return context.filesDir.resolve(settingsFileName).absolutePath
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

