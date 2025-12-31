package org.krypton.data.repository.impl

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.krypton.Settings
import org.krypton.migrateSettings
import org.krypton.validateSettings
import org.krypton.data.repository.SettingsRepository
import org.krypton.data.repository.SettingsPersistence

/**
 * JVM implementation of SettingsRepository.
 */
class SettingsRepositoryImpl(
    private val persistence: SettingsPersistence
) : SettingsRepository {
    private val mutex = Mutex()
    
    // Get settings path dynamically each time (can change via config)
    private fun getSettingsPath(): String = persistence.getSettingsFilePath()
    
    private val _settingsFlow = MutableStateFlow<Settings>(loadInitialSettings())
    override val settingsFlow: StateFlow<Settings> = _settingsFlow.asStateFlow()

    private fun loadInitialSettings(): Settings {
        val settingsPath = getSettingsPath()
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
            // File doesn't exist, create default settings with Android preferences
            // Android: Prefer ChromaCloud and Gemini
            val defaultSettings = Settings(
                rag = Settings().rag.copy(
                    vectorBackend = org.krypton.VectorBackend.CHROMA_CLOUD
                ),
                llm = Settings().llm.copy(
                    provider = org.krypton.LlmProvider.GEMINI
                )
            )
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
                // Save to current settings file path
                val settingsPath = getSettingsPath()
                persistence.saveSettingsToFile(settingsPath, migrated)
            } else {
                // Validation failed, don't update
                throw IllegalArgumentException("Settings validation failed: ${validation.errors.joinToString()}")
            }
        }
    }

    override suspend fun reloadFromDisk() {
        mutex.withLock {
            val settingsPath = getSettingsPath()
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

