package org.krypton.config

/**
 * JVM implementation that loads from local.properties.
 */
internal actual fun loadOllamaBaseUrl(): String {
    return SecretsDefaults.getOllamaBaseUrl() ?: "http://localhost:11434"
}

internal actual fun loadOllamaGenerationModel(): String {
    return SecretsDefaults.getOllamaGenerationModel() ?: ""
}

