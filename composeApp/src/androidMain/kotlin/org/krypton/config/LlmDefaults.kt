package org.krypton.config

/**
 * Android implementation.
 * Android doesn't support loading from local.properties in the same way,
 * so returns empty string (Android uses GEMINI by default anyway).
 */
internal actual fun loadOllamaBaseUrl(): String {
    return ""
}

internal actual fun loadOllamaGenerationModel(): String {
    return ""
}

