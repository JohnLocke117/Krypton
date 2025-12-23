package org.krypton.krypton.settings

import org.krypton.krypton.Settings
import java.nio.file.Path

/**
 * Interface for settings persistence to improve testability.
 * 
 * This interface abstracts settings file operations, allowing for easier testing
 * and potential platform-specific implementations.
 */
interface SettingsPersistence {
    fun getSettingsFilePath(): Path
    fun loadSettingsFromFile(path: Path): Settings?
    fun parseSettingsFromJson(jsonString: String): Settings?
    fun saveSettingsToFile(path: Path, settings: Settings): Boolean
}

