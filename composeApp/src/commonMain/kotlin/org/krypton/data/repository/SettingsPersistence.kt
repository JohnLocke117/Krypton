package org.krypton.data.repository

import org.krypton.Settings

/**
 * Platform-agnostic interface for settings persistence.
 * 
 * This interface abstracts settings file operations, allowing for easier testing
 * and platform-specific implementations.
 * 
 * All paths are represented as strings for platform independence.
 */
interface SettingsPersistence {
    /**
     * Gets the file path where settings should be stored.
     * Checks for vault-specific settings first, then falls back to app-wide settings.
     * 
     * @param vaultId Optional vault ID to check for vault-specific settings
     * @return Path as a string
     */
    fun getSettingsFilePath(vaultId: String? = null): String
    
    /**
     * Gets the vault-specific settings file path.
     * 
     * @param vaultId Vault ID
     * @return Path to `.krypton/settings.json` in vault root, or null if vaultId is invalid
     */
    fun getVaultSettingsPath(vaultId: String): String?
    
    /**
     * Loads settings from a file.
     * 
     * @param path Path to the settings file
     * @return Settings object if successful, null if file doesn't exist or on error
     */
    fun loadSettingsFromFile(path: String): Settings?
    
    /**
     * Parses settings from a JSON string.
     * 
     * @param jsonString JSON string to parse
     * @return Settings object if successful, null on parse error
     */
    fun parseSettingsFromJson(jsonString: String): Settings?
    
    /**
     * Saves settings to a file.
     * 
     * @param path Path to the settings file
     * @param settings Settings to save
     * @return true if successful, false otherwise
     */
    fun saveSettingsToFile(path: String, settings: Settings): Boolean
}

