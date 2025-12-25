package org.krypton.krypton.data.chat.impl

import org.krypton.krypton.RagSettings
import org.krypton.krypton.chat.ChatService
import org.krypton.krypton.chat.OllamaChatService
import org.krypton.krypton.rag.RagComponents

/**
 * Factory for creating chat services.
 * 
 * NOTE: This factory is deprecated. Chat services are now created via DI (Koin).
 * This is kept for backward compatibility but may be removed in the future.
 * 
 * @deprecated Use DI (Koin) to get ChatService instead
 */
@Deprecated("Use DI (Koin) to get ChatService instead", ReplaceWith("get<ChatService>()"))
object ChatServiceFactory {
    /**
     * Creates a chat service, optionally with RAG support.
     * 
     * @param ragComponents RAG components if available, null otherwise
     * @param ragSettings RAG settings to determine if RAG should be enabled
     * @return ChatService instance (currently always returns OllamaChatService - use DI for full functionality)
     */
    fun createChatService(
        ragComponents: RagComponents?,
        ragSettings: RagSettings
    ): ChatService {
        // Return base chat service - full RAG/web search support is now via DI
        return OllamaChatService()
    }
}

