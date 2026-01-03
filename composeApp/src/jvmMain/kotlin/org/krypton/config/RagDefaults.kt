package org.krypton.config

/**
 * JVM implementation that loads from local.properties.
 */
internal actual fun loadOllamaEmbeddingModel(): String {
    return SecretsDefaults.getOllamaEmbeddingModel() ?: ""
}

/**
 * JVM implementation that loads default embedding max tokens from secrets.
 * Uses 80% of OLLAMA_EMBEDDING_MODEL_CONTEXT_LENGTH if available, otherwise defaults to 500.
 */
internal actual fun loadDefaultEmbeddingMaxTokens(): Int {
    val contextLength = SecretsDefaults.getOllamaEmbeddingModelContextLength()
    return if (contextLength != null && contextLength > 0) {
        // Use 80% of context length as conservative safety margin
        (contextLength * 0.8).toInt().coerceAtLeast(100)
    } else {
        // Fallback to default constant
        RagDefaults.Embedding.MAX_EMBEDDING_CONTEXT_TOKENS
    }
}

