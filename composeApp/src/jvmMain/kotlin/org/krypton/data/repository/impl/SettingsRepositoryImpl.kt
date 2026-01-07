package org.krypton.data.repository.impl

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.krypton.Settings
import org.krypton.mergeSettings
import org.krypton.migrateSettings
import org.krypton.validateSettings
import org.krypton.data.repository.SettingsRepository
import org.krypton.data.repository.SettingsPersistence
import org.krypton.config.SecretsDefaults
import org.krypton.util.AppLogger

/**
 * JVM implementation of SettingsRepository.
 * 
 * Loads settings from project root settings.json by default.
 * If a vault is open, merges vault-specific .krypton/settings.json over project root settings.
 */
class SettingsRepositoryImpl(
    private val persistence: SettingsPersistence
) : SettingsRepository {
    private val mutex = Mutex()
    private var currentVaultId: String? = null
    
    // Get project root settings path (no vault-specific override)
    private fun getProjectRootSettingsPath(): String {
        // Use null vaultId to get project root settings
        return persistence.getSettingsFilePath(null)
    }
    
    // Get vault-specific settings path if vault is open
    private fun getVaultSettingsPath(vaultId: String?): String? {
        return if (vaultId != null && vaultId.isNotBlank()) {
            persistence.getVaultSettingsPath(vaultId)
        } else {
            null
        }
    }
    
    private val _settingsFlow = MutableStateFlow<Settings>(loadInitialSettings())
    override val settingsFlow: StateFlow<Settings> = _settingsFlow.asStateFlow()

    private fun loadInitialSettings(): Settings {
        // Step 1: Load project root settings.json (automatically detected, no config needed)
        val projectRootPath = getProjectRootSettingsPath()
        val projectRootSettings = persistence.loadSettingsFromFile(projectRootPath)
        
        val baseSettings = if (projectRootSettings != null) {
            val migrated = migrateSettings(projectRootSettings)
            val validated = validateSettings(migrated)
            if (validated.isValid) {
                migrated
            } else {
                // If validation fails on load, use defaults from local.properties
                AppLogger.w("SettingsRepositoryImpl", "Project root settings validation failed, using defaults")
                SecretsDefaults.createDefaultSettings()
            }
        } else {
            // File doesn't exist, create default settings at project root
            AppLogger.i("SettingsRepositoryImpl", "Project root settings.json not found, creating at: $projectRootPath")
            val defaultSettings = SecretsDefaults.createDefaultSettings()
            persistence.saveSettingsToFile(projectRootPath, defaultSettings)
            defaultSettings
        }
        
        // Step 2: If vault is open, load vault-specific settings and merge
        val vaultId = currentVaultId
        if (vaultId != null && vaultId.isNotBlank()) {
            val vaultSettingsPath = getVaultSettingsPath(vaultId)
            if (vaultSettingsPath != null) {
                val vaultSettings = persistence.loadSettingsFromFile(vaultSettingsPath)
                if (vaultSettings != null) {
                    val migratedVault = migrateSettings(vaultSettings)
                    val validatedVault = validateSettings(migratedVault)
                    if (validatedVault.isValid) {
                        AppLogger.d("SettingsRepositoryImpl", "Merging vault-specific settings from: $vaultSettingsPath")
                        // Merge vault settings over project root settings
                        return mergeSettings(baseSettings, migratedVault)
                    } else {
                        AppLogger.w("SettingsRepositoryImpl", "Vault settings validation failed, using project root settings only")
                    }
                }
            }
        }
        
        return baseSettings
    }

    override suspend fun update(transform: (Settings) -> Settings) {
        mutex.withLock {
            val current = _settingsFlow.value
            val updated = transform(current)
            val migrated = migrateSettings(updated)
            val validation = validateSettings(migrated)
            
            if (validation.isValid) {
                _settingsFlow.value = migrated
                // Save to project root settings.json by default
                // (User can optionally save to vault-specific settings if needed)
                val projectRootPath = getProjectRootSettingsPath()
                persistence.saveSettingsToFile(projectRootPath, migrated)
                AppLogger.d("SettingsRepositoryImpl", "Settings saved to project root: $projectRootPath")
            } else {
                // Validation failed, don't update
                throw IllegalArgumentException("Settings validation failed: ${validation.errors.joinToString()}")
            }
        }
    }

    override suspend fun reloadFromDisk() {
        mutex.withLock {
            // Reload with merging logic
            val reloaded = loadInitialSettings()
            _settingsFlow.value = reloaded
        }
    }
    
    override fun setCurrentVaultId(vaultId: String?) {
        if (currentVaultId != vaultId) {
            currentVaultId = vaultId
            AppLogger.d("SettingsRepositoryImpl", "Current vault ID set to: $vaultId")
            // Reload settings to apply vault-specific overrides
            // Use a coroutine scope if available, or just update synchronously
            val reloaded = loadInitialSettings()
            _settingsFlow.value = reloaded
        }
    }
    
    override fun getCurrentVaultId(): String? {
        return currentVaultId
    }
}

