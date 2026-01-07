package org.krypton.config

/**
 * JVM implementation that returns hardcoded defaults.
 * 
 * Ollama configuration is now managed via settings.json, not local.properties.
 * local.properties should only contain API keys.
 */
internal actual fun loadOllamaBaseUrl(): String {
    // Default Ollama base URL - can be overridden in settings.json
    return "http://localhost:11434"
}

internal actual fun loadOllamaGenerationModel(): String {
    // Default Ollama generation model - can be overridden in settings.json
    return "llama3.1:8b"
}

