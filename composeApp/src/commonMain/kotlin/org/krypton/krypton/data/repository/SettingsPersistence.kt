package org.krypton.krypton.data.repository

import org.krypton.krypton.Settings

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
     * 
     * @return Path as a string
     */
    fun getSettingsFilePath(): String
    
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

