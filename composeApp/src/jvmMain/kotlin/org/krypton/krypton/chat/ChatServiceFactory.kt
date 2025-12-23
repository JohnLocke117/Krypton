package org.krypton.krypton.chat

import org.krypton.krypton.RagSettings
import org.krypton.krypton.rag.RagComponents

/**
 * Factory for creating chat services.
 * 
 * Extracts chat service creation logic from App.kt to improve separation of concerns.
 */
object ChatServiceFactory {
    /**
     * Creates a chat service, optionally with RAG support.
     * 
     * @param ragComponents RAG components if available, null otherwise
     * @param ragSettings RAG settings to determine if RAG should be enabled
     * @return ChatService instance (RagChatService if RAG is enabled and available, otherwise OllamaChatService)
     */
    fun createChatService(
        ragComponents: RagComponents?,
        ragSettings: RagSettings
    ): ChatService {
        val baseChatService = OllamaChatService()
        
        return if (ragComponents != null && ragSettings.ragEnabled) {
            RagChatService(
                baseChatService = baseChatService,
                ragService = ragComponents.ragService,
                ragEnabled = true
            )
        } else {
            baseChatService
        }
    }
}

