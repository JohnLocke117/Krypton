package org.krypton.config

import org.krypton.Settings
import org.krypton.LlmSettings
import org.krypton.RagSettings
import org.krypton.util.SecretsLoader
import org.krypton.util.AppLogger

/**
 * Loads default configuration values from local.secrets.properties.
 * 
 * This is used when creating settings.json for the first time.
 * If a property is not found in local.secrets.properties, returns null
 * to allow the code to use its own fallback defaults.
 */
object SecretsDefaults {
    /**
     * Gets the OLLAMA base URL from local.secrets.properties.
     */
    fun getOllamaBaseUrl(): String? {
        return SecretsLoader.loadSecret("OLLAMA_BASE_URL")
    }
    
    /**
     * Gets the OLLAMA generation model from local.secrets.properties.
     */
    fun getOllamaGenerationModel(): String? {
        return SecretsLoader.loadSecret("OLLAMA_GENERATION_MODEL")
    }
    
    /**
     * Gets the OLLAMA generation model context length from local.secrets.properties.
     */
    fun getOllamaGenerationModelContextLength(): Int? {
        return SecretsLoader.loadSecret("OLLAMA_GENERATION_MODEL_CONTEXT_LENGTH")?.toIntOrNull()
    }
    
    /**
     * Gets the OLLAMA embedding model from local.secrets.properties.
     */
    fun getOllamaEmbeddingModel(): String? {
        return SecretsLoader.loadSecret("OLLAMA_EMBEDDING_MODEL")
    }
    
    /**
     * Gets the OLLAMA embedding model context length from local.secrets.properties.
     */
    fun getOllamaEmbeddingModelContextLength(): Int? {
        return SecretsLoader.loadSecret("OLLAMA_EMBEDDING_MODEL_CONTEXT_LENGTH")?.toIntOrNull()
    }
    
    /**
     * Creates default Settings using values from local.secrets.properties.
     * If a value is not found in local.secrets.properties, uses sensible defaults.
     */
    fun createDefaultSettings(): Settings {
        val ollamaBaseUrl = getOllamaBaseUrl() ?: "http://localhost:11434"
        val ollamaModel = getOllamaGenerationModel()
        val embeddingModel = getOllamaEmbeddingModel()
        
        AppLogger.d("SecretsDefaults", "Creating default settings from local.secrets.properties")
        AppLogger.d("SecretsDefaults", "OLLAMA_BASE_URL: $ollamaBaseUrl")
        AppLogger.d("SecretsDefaults", "OLLAMA_GENERATION_MODEL: ${ollamaModel ?: "not found, using default"}")
        AppLogger.d("SecretsDefaults", "OLLAMA_EMBEDDING_MODEL: ${embeddingModel ?: "not found, using default"}")
        
        // Create default settings
        val defaultSettings = Settings()
        
        // Override with values from local.secrets.properties if available
        val llmSettings = if (ollamaModel != null) {
            defaultSettings.llm.copy(
                ollamaBaseUrl = ollamaBaseUrl,
                ollamaModel = ollamaModel
            )
        } else {
            defaultSettings.llm.copy(
                ollamaBaseUrl = ollamaBaseUrl
            )
        }
        
        // Compute embedding max tokens from context length if available
        val embeddingContextLength = getOllamaEmbeddingModelContextLength()
        val embeddingMaxTokens = if (embeddingContextLength != null && embeddingContextLength > 0) {
            // Use 80% of context length as conservative safety margin
            (embeddingContextLength * 0.8).toInt().coerceAtLeast(100)
        } else {
            // Use default from RagDefaults
            org.krypton.config.RagDefaults.Embedding.DEFAULT_EMBEDDING_MAX_TOKENS
        }
        
        val ragSettings = if (embeddingModel != null) {
            defaultSettings.rag.copy(
                embeddingBaseUrl = ollamaBaseUrl,
                embeddingModel = embeddingModel,
                embeddingMaxTokens = embeddingMaxTokens,
                embeddingMaxChars = org.krypton.config.RagDefaults.Embedding.DEFAULT_EMBEDDING_MAX_CHARS
            )
        } else {
            defaultSettings.rag.copy(
                embeddingBaseUrl = ollamaBaseUrl,
                embeddingMaxTokens = embeddingMaxTokens,
                embeddingMaxChars = org.krypton.config.RagDefaults.Embedding.DEFAULT_EMBEDDING_MAX_CHARS
            )
        }
        
        return defaultSettings.copy(
            llm = llmSettings,
            rag = ragSettings
        )
    }
}

