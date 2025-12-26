package org.krypton.config.models

/**
 * Configuration for an LLM model endpoint.
 * 
 * Groups model settings that are typically used together.
 */
data class LlmModelConfig(
    /** Base URL for the LLM API */
    val baseUrl: String,
    /** Model name identifier */
    val modelName: String,
    /** Temperature for text generation (0.0 to 2.0) */
    val temperature: Double,
    /** Maximum tokens to generate (null = no limit) */
    val maxTokens: Int?,
    /** HTTP timeout in milliseconds */
    val timeoutMs: Long
)

