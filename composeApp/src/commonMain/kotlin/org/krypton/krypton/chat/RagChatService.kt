package org.krypton.krypton.chat

import org.krypton.krypton.rag.RagService
import java.util.UUID

/**
 * Chat service that conditionally uses RAG or direct LLM.
 * 
 * When RAG is enabled, uses RagService to answer questions with context from notes.
 * When RAG is disabled, delegates to the underlying ChatService.
 */
class RagChatService(
    private val baseChatService: ChatService,
    private val ragService: RagService?,
    private var ragEnabled: Boolean = true
) : ChatService {
    
    override suspend fun sendMessage(
        history: List<ChatMessage>,
        userMessage: String
    ): List<ChatMessage> {
        // Create user message
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatRole.USER,
            content = userMessage,
            timestamp = System.currentTimeMillis()
        )
        
        // If RAG is enabled and available, use it
        if (ragEnabled && ragService != null) {
            try {
                val answer = ragService.ask(userMessage)
                
                val assistantMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = ChatRole.ASSISTANT,
                    content = answer,
                    timestamp = System.currentTimeMillis()
                )
                
                return history + userMsg + assistantMsg
            } catch (e: Exception) {
                // If RAG fails, fall back to base chat service
                // TODO: Log error
                return baseChatService.sendMessage(history, userMessage)
            }
        } else {
            // Use base chat service
            return baseChatService.sendMessage(history, userMessage)
        }
    }
    
    /**
     * Sets whether RAG is enabled for this session.
     */
    fun setRagEnabled(enabled: Boolean) {
        ragEnabled = enabled
    }
    
    /**
     * Gets whether RAG is currently enabled.
     */
    fun isRagEnabled(): Boolean = ragEnabled
}

