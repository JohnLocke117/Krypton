package org.krypton.chat

/**
 * Interface for chat services that generate responses to user messages.
 * 
 * This is the main entry point for chat functionality used by the UI layer.
 * Implementations handle message sending, retrieval (if enabled), and LLM generation.
 * 
 * All operations are suspend functions to allow for async network calls.
 * Implementations should be provided via dependency injection in platform-specific code.
 */
interface ChatService {
    /**
     * Sends a user message and generates an assistant response.
     * 
     * @param message The user's message
     * @param mode The retrieval mode to use (NONE, RAG, WEB, or HYBRID)
     * @param threadId Optional thread/conversation ID for multi-threaded conversations
     * @param vaultPath Optional path to the currently opened vault/folder for agent context
     * @param currentNotePath Optional path to the currently active/open note file for agent context
     * @return ChatResponse containing the assistant's message and metadata
     * @throws ChatException if the chat operation fails (e.g., LLM error, retrieval failure, network error)
     */
    suspend fun sendMessage(
        message: String,
        mode: RetrievalMode,
        threadId: String? = null,
        vaultPath: String? = null,
        currentNotePath: String? = null
    ): ChatResponse
}

