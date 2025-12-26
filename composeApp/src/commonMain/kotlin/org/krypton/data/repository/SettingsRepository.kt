package org.krypton.data.repository

import kotlinx.coroutines.flow.StateFlow
import org.krypton.Settings

/**
 * Repository interface for managing application settings.
 * 
 * Provides reactive access to settings via StateFlow and methods
 * to update and reload settings.
 */
interface SettingsRepository {
    /**
     * Flow of current settings that emits whenever settings change.
     */
    val settingsFlow: StateFlow<Settings>
    
    /**
     * Updates settings by applying a transform function.
     * 
     * @param transform Function that takes current settings and returns updated settings
     * @throws IllegalArgumentException if the updated settings fail validation
     */
    suspend fun update(transform: (Settings) -> Settings)
    
    /**
     * Reloads settings from disk.
     * 
     * This is useful when the settings file might have been modified externally.
     */
    suspend fun reloadFromDisk()
}

