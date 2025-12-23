package org.krypton.krypton.chat

import org.krypton.krypton.rag.RagService
import org.krypton.krypton.util.IdGenerator
import org.krypton.krypton.util.TimeProvider
import org.krypton.krypton.util.createIdGenerator
import org.krypton.krypton.util.createTimeProvider

/**
 * Chat service that conditionally uses RAG or direct LLM.
 * 
 * When RAG is enabled, uses RagService to answer questions with context from notes.
 * When RAG is disabled, delegates to the underlying ChatService.
 */
class RagChatService(
    private val baseChatService: ChatService,
    private val ragService: RagService?,
    private var ragEnabled: Boolean = true,
    private val idGenerator: IdGenerator = createIdGenerator(),
    private val timeProvider: TimeProvider = createTimeProvider()
) : ChatService {
    
    override suspend fun sendMessage(
        history: List<ChatMessage>,
        userMessage: String
    ): List<ChatMessage> {
        // Create user message
        val userMsg = ChatMessage(
            id = idGenerator.generateId(),
            role = ChatRole.USER,
            content = userMessage,
            timestamp = timeProvider.currentTimeMillis()
        )
        
        // If RAG is enabled and available, use it
        if (ragEnabled && ragService != null) {
            try {
                val answer = ragService.ask(userMessage)
                
                val assistantMsg = ChatMessage(
                    id = idGenerator.generateId(),
                    role = ChatRole.ASSISTANT,
                    content = answer,
                    timestamp = timeProvider.currentTimeMillis()
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

