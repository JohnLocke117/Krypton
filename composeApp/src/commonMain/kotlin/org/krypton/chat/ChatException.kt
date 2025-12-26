package org.krypton.chat

/**
 * Domain-specific exception for chat operations.
 * 
 * Thrown when chat operations fail due to domain-level issues such as:
 * - LLM errors (network failures, API errors, empty responses)
 * - Retrieval failures (RAG errors, vector store errors)
 * - Invalid mode or configuration
 * - Other chat-specific failures
 * 
 * This exception should be used instead of generic Exception to provide
 * better error handling and type safety in chat-related code.
 */
class ChatException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

