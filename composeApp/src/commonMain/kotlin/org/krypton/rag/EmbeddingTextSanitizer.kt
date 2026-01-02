package org.krypton.rag

import org.krypton.util.AppLogger

/**
 * Simple guard to ensure we never send text that would exceed
 * the embedding model's context window.
 * 
 * This sanitizer applies both character-based and token-based limits
 * to prevent "input length exceeds the context length" errors from Ollama.
 * 
 * @param maxTokens Maximum tokens allowed (conservative limit)
 * @param maxChars Hard character limit fallback
 */
class EmbeddingTextSanitizer(
    private val maxTokens: Int,
    private val maxChars: Int,
) {

    /**
     * Sanitizes a single text string to ensure it doesn't exceed limits.
     * 
     * @param text The text to sanitize
     * @return Sanitized text (truncated if necessary)
     */
    fun sanitize(text: String): String {
        if (text.isEmpty()) return text

        // First guard: char cap
        var safe = if (text.length > maxChars) {
            AppLogger.d(
                "EmbeddingTextSanitizer",
                "Trimming embedding text from ${text.length} to $maxChars chars (char limit)"
            )
            text.take(maxChars)
        } else {
            text
        }

        // Second guard: approximate token limit (4 chars ≈ 1 token)
        val approxTokens = safe.length / 4
        if (approxTokens > maxTokens) {
            val targetChars = maxTokens * 4
            val originalLength = safe.length
            safe = safe.take(targetChars)
            AppLogger.d(
                "EmbeddingTextSanitizer",
                "Trimming embedding text from approx $approxTokens tokens (${originalLength} chars) to $maxTokens tokens (${targetChars} chars)"
            )
        }

        return safe
    }

    /**
     * Sanitizes a list of text strings.
     * 
     * @param texts List of texts to sanitize
     * @return List of sanitized texts
     */
    fun sanitizeAll(texts: List<String>): List<String> =
        texts.map { sanitize(it) }
}

