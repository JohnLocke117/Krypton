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
    // For now, use app-wide settings (vault-specific can be added later if needed)
    private fun getSettingsPath(vaultId: String? = null): String = persistence.getSettingsFilePath(vaultId)
    
    private val _settingsFlow = MutableStateFlow<Settings>(loadInitialSettings())
    override val settingsFlow: StateFlow<Settings> = _settingsFlow.asStateFlow()

    private fun loadInitialSettings(): Settings {
        val settingsPath = getSettingsPath()
        val loaded = persistence.loadSettingsFromFile(settingsPath)
        return if (loaded != null) {
            val migrated = migrateSettings(loaded)
            
            // Android-specific validation: Only GEMINI and CHROMA_CLOUD are supported
            // If loaded settings have unsupported values, override them with Android defaults
            val androidCompliantSettings = if (migrated.llm.provider != org.krypton.LlmProvider.GEMINI ||
                migrated.rag.vectorBackend != org.krypton.VectorBackend.CHROMA_CLOUD) {
                migrated.copy(
                    llm = migrated.llm.copy(provider = org.krypton.LlmProvider.GEMINI),
                    rag = migrated.rag.copy(vectorBackend = org.krypton.VectorBackend.CHROMA_CLOUD)
                )
            } else {
                migrated
            }
            
            val validated = validateSettings(androidCompliantSettings)
            if (validated.isValid) {
                androidCompliantSettings
            } else {
                // If validation fails on load, use Android defaults
                Settings(
                    rag = Settings().rag.copy(
                        vectorBackend = org.krypton.VectorBackend.CHROMA_CLOUD
                    ),
                    llm = Settings().llm.copy(
                        provider = org.krypton.LlmProvider.GEMINI
                    )
                )
            }
        } else {
            // File doesn't exist, create default settings with Android preferences
            // Android: MUST use ChromaCloud and Gemini ONLY (OLLAMA and local ChromaDB not supported)
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
            
            // Android-specific validation: Only GEMINI and CHROMA_CLOUD are supported
            val androidErrors = mutableListOf<String>()
            if (migrated.llm.provider != org.krypton.LlmProvider.GEMINI) {
                androidErrors.add("Android only supports GEMINI provider. OLLAMA is not supported on Android.")
            }
            if (migrated.rag.vectorBackend != org.krypton.VectorBackend.CHROMA_CLOUD) {
                androidErrors.add("Android only supports CHROMA_CLOUD vector backend. Local ChromaDB is not supported on Android.")
            }
            
            if (androidErrors.isNotEmpty()) {
                throw IllegalArgumentException("Android platform restrictions: ${androidErrors.joinToString()}")
            }
            
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

