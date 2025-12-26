package org.krypton.config.models

/**
 * HTTP client configuration for API requests.
 * 
 * Groups timeout and retry settings that typically travel together.
 */
data class HttpConfig(
    /** Connection timeout in milliseconds */
    val connectTimeoutMs: Long,
    /** Read timeout in milliseconds */
    val readTimeoutMs: Long,
    /** Write timeout in milliseconds */
    val writeTimeoutMs: Long,
    /** Maximum number of retry attempts */
    val maxRetries: Int,
    /** Delay between retries in milliseconds */
    val retryDelayMs: Long
)

