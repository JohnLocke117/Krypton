package org.krypton.config

import org.krypton.config.models.LlmModelConfig

/**
 * Default configuration values for chat system.
 * 
 * These defaults are used when user settings are missing or invalid.
 */
object ChatDefaults {
    /**
     * Default LLM model configuration for chat.
     * 
     * Groups all LLM-related settings that are typically used together.
     */
    val DEFAULT_LLM = LlmModelConfig(
        baseUrl = LlmDefaults.DEFAULT_BASE_URL,
        modelName = "llama3.2:1b",
        temperature = LlmDefaults.DEFAULT_TEMPERATURE,
        maxTokens = 2048,
        timeoutMs = LlmDefaults.DEFAULT_TIMEOUT_MS
    )
    
    /**
     * Default system prompt for chat assistant.
     */
    const val DEFAULT_SYSTEM_PROMPT = "You are a helpful study assistant integrated into a personal markdown editor. Be concise and helpful."
    
}

