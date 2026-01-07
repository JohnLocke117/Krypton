package org.krypton.config

/**
 * JVM implementation that returns hardcoded defaults.
 * 
 * Ollama configuration is now managed via settings.json, not local.properties.
 * local.properties should only contain API keys.
 */
internal actual fun loadOllamaEmbeddingModel(): String {
    // Default Ollama embedding model - can be overridden in settings.json
    return "mxbai-embed-large:335m"
}

/**
 * JVM implementation that returns default embedding max tokens.
 * 
 * Uses a conservative default that works for most embedding models.
 * Can be overridden in settings.json.
 */
internal actual fun loadDefaultEmbeddingMaxTokens(): Int {
    // Default: 409 tokens (80% of 512 token context length, which is common for embedding models)
    // This is a conservative default that works for most models
    return 409
}

