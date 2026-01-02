package org.krypton.config

/**
 * Platform-specific implementation to load OLLAMA base URL from secrets.
 */
internal expect fun loadOllamaBaseUrl(): String

/**
 * Platform-specific implementation to load OLLAMA generation model from secrets.
 */
internal expect fun loadOllamaGenerationModel(): String

/**
 * Shared default configuration for LLM (Language Model) services.
 * 
 * Consolidates common LLM settings used across chat and RAG systems.
 * Default values are loaded from local.secrets.properties when available.
 */
object LlmDefaults {
    /**
     * Default base URL for Ollama/LLM API.
     * Loads from local.secrets.properties (OLLAMA_BASE_URL) if available.
     */
    val DEFAULT_BASE_URL: String
        get() = loadOllamaBaseUrl()
    
    /**
     * Default temperature for text generation (0.0 to 2.0).
     * Lower values make output more deterministic.
     */
    const val DEFAULT_TEMPERATURE = 0.7
    
    /**
     * Default timeout for LLM HTTP requests (milliseconds).
     */
    const val DEFAULT_TIMEOUT_MS = 120_000L // 2 minutes
    
    /**
     * Default number of retry attempts for failed HTTP requests.
     */
    const val DEFAULT_MAX_RETRIES = 3
    
    /**
     * Default delay between retries (milliseconds).
     */
    const val DEFAULT_RETRY_DELAY_MS = 1_000L
}

