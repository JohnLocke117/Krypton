package org.krypton.platform

import org.krypton.data.repository.impl.SettingsConfigManager
import org.krypton.util.AppLogger

/**
 * JVM implementation of SettingsConfigProvider that wraps SettingsConfigManager.
 */
class JvmSettingsConfigProvider : SettingsConfigProvider {
    
    override fun getSettingsFilePath(vaultId: String?): String {
        // Check for vault-specific settings first
        if (vaultId != null && vaultId.isNotBlank()) {
            val vaultPath = getVaultSettingsPath(vaultId)
            if (vaultPath != null) {
                val vaultSettingsFile = java.nio.file.Paths.get(vaultPath)
                if (java.nio.file.Files.exists(vaultSettingsFile)) {
                    return vaultPath
                }
            }
        }
        
        // Fall back to app-wide settings
        return SettingsConfigManager.getSettingsFilePath()
    }
    
    override fun getVaultSettingsPath(vaultId: String): String? {
        return try {
            val vaultPath = java.nio.file.Paths.get(vaultId)
            val kryptonDir = vaultPath.resolve(".krypton")
            val settingsFile = kryptonDir.resolve("settings.json")
            settingsFile.toString()
        } catch (e: Exception) {
            null
        }
    }
    
    override fun setSettingsFilePath(path: String): Boolean {
        return SettingsConfigManager.setSettingsFilePath(path)
    }
    
    override fun getConfigDirectory(): String {
        // Return the directory where config.json is stored
        val configPath = System.getProperty("user.home")?.let { home ->
            java.nio.file.Paths.get(home).resolve(".krypton")
        } ?: java.nio.file.Paths.get(".krypton")
        
        return configPath.toString()
    }
}

