package org.krypton.config

/**
 * Android implementation.
 * Android doesn't support loading from local.secrets.properties in the same way,
 * so returns empty string (Android uses GEMINI by default anyway).
 */
internal actual fun loadOllamaEmbeddingModel(): String {
    return ""
}

/**
 * Android implementation.
 * Android doesn't use Ollama embeddings (uses Gemini), so returns default constant.
 */
internal actual fun loadDefaultEmbeddingMaxTokens(): Int {
    return RagDefaults.Embedding.MAX_EMBEDDING_CONTEXT_TOKENS
}

