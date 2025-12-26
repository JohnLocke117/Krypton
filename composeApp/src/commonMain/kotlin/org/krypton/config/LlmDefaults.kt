package org.krypton.config

/**
 * Shared default configuration for LLM (Language Model) services.
 * 
 * Consolidates common LLM settings used across chat and RAG systems.
 * This is the single source of truth for Ollama/LLM base configuration.
 */
object LlmDefaults {
    /**
     * Default base URL for Ollama/LLM API.
     */
    const val DEFAULT_BASE_URL = "http://localhost:11434"
    
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

