package org.krypton.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper functions for safe external API calls with error handling.
 * 
 * These functions wrap external service calls (ChromaDB, Ollama, Tavily, Gemini)
 * and return Result types for graceful error handling.
 */

/**
 * Safely executes a ChromaDB operation and returns a Result.
 * 
 * @param operation Description of the operation (for logging)
 * @param block The operation to execute
 * @return Result.success with the value, or Result.failure with the exception
 */
suspend fun <T> safeChromaDBCall(
    operation: String,
    block: suspend () -> T
): Result<T> = withContext(Dispatchers.IO) {
    try {
        Result.success(block())
    } catch (e: Exception) {
        AppLogger.e("SafeCallHelpers", "ChromaDB operation failed: $operation - ${e.message}", e)
        Result.failure(e)
    }
}

/**
 * Safely executes an Ollama operation and returns a Result.
 * 
 * @param operation Description of the operation (for logging)
 * @param block The operation to execute
 * @return Result.success with the value, or Result.failure with the exception
 */
suspend fun <T> safeOllamaCall(
    operation: String,
    block: suspend () -> T
): Result<T> = withContext(Dispatchers.IO) {
    try {
        Result.success(block())
    } catch (e: Exception) {
        AppLogger.e("SafeCallHelpers", "Ollama operation failed: $operation - ${e.message}", e)
        Result.failure(e)
    }
}

/**
 * Safely executes a Tavily web search operation and returns a Result.
 * 
 * @param operation Description of the operation (for logging)
 * @param block The operation to execute
 * @return Result.success with the value, or Result.failure with the exception
 */
suspend fun <T> safeTavilyCall(
    operation: String,
    block: suspend () -> T
): Result<T> = withContext(Dispatchers.IO) {
    try {
        Result.success(block())
    } catch (e: Exception) {
        AppLogger.e("SafeCallHelpers", "Tavily operation failed: $operation - ${e.message}", e)
        Result.failure(e)
    }
}

/**
 * Safely executes a Gemini API operation and returns a Result.
 * 
 * @param operation Description of the operation (for logging)
 * @param block The operation to execute
 * @return Result.success with the value, or Result.failure with the exception
 */
suspend fun <T> safeGeminiCall(
    operation: String,
    block: suspend () -> T
): Result<T> = withContext(Dispatchers.IO) {
    try {
        Result.success(block())
    } catch (e: Exception) {
        AppLogger.e("SafeCallHelpers", "Gemini operation failed: $operation - ${e.message}", e)
        Result.failure(e)
    }
}

