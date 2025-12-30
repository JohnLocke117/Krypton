package org.krypton.platform

import org.krypton.data.repository.impl.SettingsConfigManager
import org.krypton.util.AppLogger

/**
 * JVM implementation of SettingsConfigProvider that wraps SettingsConfigManager.
 */
class JvmSettingsConfigProvider : SettingsConfigProvider {
    
    override fun getSettingsFilePath(): String {
        return SettingsConfigManager.getSettingsFilePath()
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

