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
import org.krypton.util.AppLogger

/**
 * Android implementation of SettingsRepository.
 * 
 * Android uses read-only settings - loads from project root (vault root) settings.json if available,
 * otherwise uses hardcoded defaults. Settings modifications are disabled.
 */
class SettingsRepositoryImpl(
    private val persistence: SettingsPersistence
) : SettingsRepository {
    private val mutex = Mutex()
    private var currentVaultId: String? = null
    
    // Get project root settings path (vault root on Android)
    private fun getProjectRootSettingsPath(): String {
        // On Android, project root is the vault root
        return persistence.getSettingsFilePath(null)
    }
    
    private val _settingsFlow = MutableStateFlow<Settings>(loadInitialSettings())
    override val settingsFlow: StateFlow<Settings> = _settingsFlow.asStateFlow()

    private fun loadInitialSettings(): Settings {
        // Try to load from project root (vault root) settings.json
        val projectRootPath = getProjectRootSettingsPath()
        val loaded = persistence.loadSettingsFromFile(projectRootPath)
        
        return if (loaded != null) {
            val migrated = migrateSettings(loaded)
            
            // Android-specific validation: Only GEMINI and CHROMA_CLOUD are supported
            // If loaded settings have unsupported values, override them with Android defaults
            val androidCompliantSettings = if (migrated.llm.provider != org.krypton.LlmProvider.GEMINI ||
                migrated.rag.vectorBackend != org.krypton.VectorBackend.CHROMA_CLOUD) {
                AppLogger.w("SettingsRepositoryImpl", "Loaded settings have unsupported values, overriding with Android defaults")
                migrated.copy(
                    llm = migrated.llm.copy(provider = org.krypton.LlmProvider.GEMINI),
                    rag = migrated.rag.copy(vectorBackend = org.krypton.VectorBackend.CHROMA_CLOUD)
                )
            } else {
                migrated
            }
            
            val validated = validateSettings(androidCompliantSettings)
            if (validated.isValid) {
                AppLogger.d("SettingsRepositoryImpl", "Loaded settings from project root: $projectRootPath")
                androidCompliantSettings
            } else {
                // If validation fails on load, use Android defaults
                AppLogger.w("SettingsRepositoryImpl", "Settings validation failed, using Android defaults")
                createAndroidDefaults()
            }
        } else {
            // File doesn't exist, use Android defaults (never create files on Android)
            AppLogger.d("SettingsRepositoryImpl", "Project root settings.json not found, using Android defaults")
            createAndroidDefaults()
        }
    }
    
    private fun createAndroidDefaults(): Settings {
        // Android: MUST use ChromaCloud and Gemini ONLY (OLLAMA and local ChromaDB not supported)
        return Settings(
            rag = Settings().rag.copy(
                vectorBackend = org.krypton.VectorBackend.CHROMA_CLOUD
            ),
            llm = Settings().llm.copy(
                provider = org.krypton.LlmProvider.GEMINI
            )
        )
    }

    override suspend fun update(transform: (Settings) -> Settings) {
        // Android: Settings modifications are disabled
        throw UnsupportedOperationException("Settings modifications are not available on Android. The app uses default values only.")
    }

    override suspend fun reloadFromDisk() {
        mutex.withLock {
            // Reload from project root (vault root)
            val reloaded = loadInitialSettings()
            _settingsFlow.value = reloaded
        }
    }
    
    override fun setCurrentVaultId(vaultId: String?) {
        if (currentVaultId != vaultId) {
            currentVaultId = vaultId
            AppLogger.d("SettingsRepositoryImpl", "Current vault ID set to: $vaultId")
            // Reload settings (though Android doesn't support vault-specific settings)
            val reloaded = loadInitialSettings()
            _settingsFlow.value = reloaded
        }
    }
    
    override fun getCurrentVaultId(): String? {
        return currentVaultId
    }
}

