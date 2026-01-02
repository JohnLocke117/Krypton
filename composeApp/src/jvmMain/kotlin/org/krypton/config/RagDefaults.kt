package org.krypton.config

/**
 * JVM implementation that loads from local.secrets.properties.
 */
internal actual fun loadOllamaEmbeddingModel(): String {
    return SecretsDefaults.getOllamaEmbeddingModel() ?: ""
}

