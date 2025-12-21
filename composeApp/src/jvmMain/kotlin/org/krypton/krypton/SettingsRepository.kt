package org.krypton.krypton

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface SettingsRepository {
    val settingsFlow: StateFlow<Settings>
    suspend fun update(transform: (Settings) -> Settings)
    suspend fun reloadFromDisk()
}

class SettingsRepositoryImpl : SettingsRepository {
    private val mutex = Mutex()
    private val settingsPath = SettingsPersistence.getSettingsFilePath()
    
    private val _settingsFlow = MutableStateFlow<Settings>(loadInitialSettings())
    override val settingsFlow: StateFlow<Settings> = _settingsFlow.asStateFlow()

    private fun loadInitialSettings(): Settings {
        val loaded = SettingsPersistence.loadSettingsFromFile(settingsPath)
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
            SettingsPersistence.saveSettingsToFile(settingsPath, defaultSettings)
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
                SettingsPersistence.saveSettingsToFile(settingsPath, migrated)
            } else {
                // Validation failed, don't update
                throw IllegalArgumentException("Settings validation failed: ${validation.errors.joinToString()}")
            }
        }
    }

    override suspend fun reloadFromDisk() {
        mutex.withLock {
            val loaded = SettingsPersistence.loadSettingsFromFile(settingsPath)
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

