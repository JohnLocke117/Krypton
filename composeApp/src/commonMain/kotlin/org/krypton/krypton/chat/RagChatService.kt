package org.krypton.krypton.chat

import org.krypton.krypton.rag.RagService
import org.krypton.krypton.util.IdGenerator
import org.krypton.krypton.util.TimeProvider
import org.krypton.krypton.util.createIdGenerator
import org.krypton.krypton.util.createTimeProvider
import org.krypton.krypton.util.AppLogger

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
        
        // Defensive check: Never use RAG if disabled
        if (!ragEnabled || ragService == null) {
            val reason = if (!ragEnabled) "RAG disabled - using normal chat mode" else "RAG service unavailable - using normal chat mode"
            AppLogger.i("RagChatService", reason)
            // Use base chat service which doesn't include note context
            return baseChatService.sendMessage(history, userMessage)
        }
        
        // RAG is enabled and available - use it
        try {
            AppLogger.d("RagChatService", "RAG enabled - using RAG service with note context")
            val answer = ragService.ask(userMessage)
            
            val assistantMsg = ChatMessage(
                id = idGenerator.generateId(),
                role = ChatRole.ASSISTANT,
                content = answer,
                timestamp = timeProvider.currentTimeMillis()
            )
            
            return history + userMsg + assistantMsg
        } catch (e: Exception) {
            // If RAG fails, log error and fall back to base chat service
            AppLogger.w(
                "RagChatService",
                "RAG query failed, falling back to base chat service: ${e.message}",
                e
            )
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

