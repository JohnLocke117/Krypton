package org.krypton.krypton.config

/**
 * Default configuration values for chat system.
 * 
 * These defaults are used when user settings are missing or invalid.
 */
object ChatDefaults {
    /**
     * Default base URL for Ollama chat API.
     */
    const val DEFAULT_BASE_URL = "http://localhost:11434"
    
    /**
     * Default model name for chat.
     */
    const val DEFAULT_MODEL = "llama3.2:1b"
    
    /**
     * Default system prompt for chat assistant.
     */
    const val DEFAULT_SYSTEM_PROMPT = "You are a helpful study assistant integrated into a personal markdown editor. Be concise and helpful."
    
    /**
     * Default temperature for text generation (0.0 to 2.0).
     * Higher values make output more random.
     */
    const val DEFAULT_TEMPERATURE = 0.7f
    
    /**
     * Default maximum tokens to generate.
     */
    const val DEFAULT_MAX_TOKENS = 2048
    
    /**
     * Default timeout for chat requests (milliseconds).
     */
    const val DEFAULT_TIMEOUT_MS = 120_000L // 2 minutes
    
    /**
     * Default number of retry attempts for failed requests.
     */
    const val DEFAULT_MAX_RETRIES = 3
    
    /**
     * Default delay between retries (milliseconds).
     */
    const val DEFAULT_RETRY_DELAY_MS = 1_000L
}

