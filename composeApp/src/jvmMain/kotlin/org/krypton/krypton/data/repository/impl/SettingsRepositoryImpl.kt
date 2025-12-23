package org.krypton.krypton.data.repository.impl

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.krypton.krypton.Settings
import org.krypton.krypton.migrateSettings
import org.krypton.krypton.validateSettings
import org.krypton.krypton.data.repository.SettingsRepository
import org.krypton.krypton.data.repository.SettingsPersistence

/**
 * JVM implementation of SettingsRepository.
 */
class SettingsRepositoryImpl(
    private val persistence: SettingsPersistence
) : SettingsRepository {
    private val mutex = Mutex()
    private val settingsPath = persistence.getSettingsFilePath()
    
    private val _settingsFlow = MutableStateFlow<Settings>(loadInitialSettings())
    override val settingsFlow: StateFlow<Settings> = _settingsFlow.asStateFlow()

    private fun loadInitialSettings(): Settings {
        val loaded = persistence.loadSettingsFromFile(settingsPath)
        return if (loaded != null) {
            val migrated = migrateSettings(loaded)
            val validated = validateSettings(migrated)
            if (validated.isValid) {
                migrated
            } else {
                // If validation fails on load, use defaults
                Settings()
            }
        } else {
            // File doesn't exist, create default settings
            val defaultSettings = Settings()
            persistence.saveSettingsToFile(settingsPath, defaultSettings)
            defaultSettings
        }
    }

    override suspend fun update(transform: (Settings) -> Settings) {
        mutex.withLock {
            val current = _settingsFlow.value
            val updated = transform(current)
            val migrated = migrateSettings(updated)
            val validation = validateSettings(migrated)
            
            if (validation.isValid) {
                _settingsFlow.value = migrated
                persistence.saveSettingsToFile(settingsPath, migrated)
            } else {
                // Validation failed, don't update
                throw IllegalArgumentException("Settings validation failed: ${validation.errors.joinToString()}")
            }
        }
    }

    override suspend fun reloadFromDisk() {
        mutex.withLock {
            val loaded = persistence.loadSettingsFromFile(settingsPath)
            if (loaded != null) {
                val migrated = migrateSettings(loaded)
                val validation = validateSettings(migrated)
                if (validation.isValid) {
                    _settingsFlow.value = migrated
                }
            }
        }
    }
}

