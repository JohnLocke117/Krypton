package org.krypton.platform

/**
 * Platform-agnostic interface for managing settings configuration.
 * 
 * This abstracts the location and management of the settings file path,
 * allowing different implementations per platform:
 * - Desktop: Uses user home directory and config.json
 * - Android: Uses app's internal storage or SharedPreferences
 */
interface SettingsConfigProvider {
    /**
     * Gets the file path where settings should be stored.
     * Checks for vault-specific settings first, then falls back to app-wide settings.
     * 
     * @param vaultId Optional vault ID to check for vault-specific settings
     * @return Path as a String
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
     * Sets the settings file path.
     * 
     * @param path Path to the settings file
     * @return true if successful, false otherwise
     */
    fun setSettingsFilePath(path: String): Boolean
    
    /**
     * Gets the directory where configuration files should be stored.
     * 
     * @return Directory path as a String
     */
    fun getConfigDirectory(): String
}

