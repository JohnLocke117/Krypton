package org.krypton.chat

import org.krypton.chat.conversation.ConversationId

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
     * @param vaultId The vault this conversation belongs to (required for conversation persistence)
     * @param conversationId Optional conversation ID. If null, a new conversation is created.
     * @param userMessage The user's message
     * @param retrievalMode The retrieval mode to use (NONE, RAG, WEB, or HYBRID)
     * @param currentNotePath Optional path to the currently active/open note file for agent context
     * @return ChatResult containing the conversation ID and assistant's message
     * @throws ChatException if the chat operation fails (e.g., LLM error, retrieval failure, network error)
     */
    suspend fun sendMessage(
        vaultId: String,
        conversationId: ConversationId?,
        userMessage: String,
        retrievalMode: RetrievalMode,
        currentNotePath: String? = null
    ): ChatResult
}

/**
 * Result of a chat operation containing the conversation and assistant response.
 * 
 * @param conversationId The conversation ID (newly created or existing)
 * @param assistantMessage The assistant's response message (in new conversation ChatMessage format)
 * @param agentError Optional error message if an agent failed and chat reverted to normal flow
 */
data class ChatResult(
    val conversationId: ConversationId,
    val assistantMessage: org.krypton.chat.conversation.ChatMessage,
    val agentError: String? = null
)

