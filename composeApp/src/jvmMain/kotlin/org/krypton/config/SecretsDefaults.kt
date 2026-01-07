package org.krypton.config

import org.krypton.Settings
import org.krypton.LlmSettings
import org.krypton.RagSettings
import org.krypton.util.SecretsLoader
import org.krypton.util.AppLogger

/**
 * Creates default Settings for first-time setup.
 * 
 * Ollama configuration is now managed via settings.json, not local.properties.
 * local.properties should only contain API keys (TAVILLY_API_KEY, CHROMA_API_KEY, GEMINI_API_KEY, etc.).
 * 
 * This function creates settings with hardcoded defaults that match the defaults
 * defined in Settings.kt. Users can then modify settings.json directly or via the UI.
 */
object SecretsDefaults {
    /**
     * Creates default Settings with hardcoded defaults.
     * 
     * All Ollama configuration (base URL, models, etc.) comes from the defaults
     * defined in Settings.kt, which in turn use RagDefaults and LlmDefaults.
     * These defaults can be overridden in settings.json.
     */
    fun createDefaultSettings(): Settings {
        AppLogger.d("SecretsDefaults", "Creating default settings with hardcoded defaults")
        AppLogger.d("SecretsDefaults", "Ollama configuration should be managed via settings.json")
        
        // Simply return Settings() with all defaults
        // The defaults in Settings.kt will use RagDefaults and LlmDefaults,
        // which now return hardcoded values instead of reading from local.properties
        return Settings()
    }
}

