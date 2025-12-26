package org.krypton.data.chat.impl

import org.krypton.RagSettings
import org.krypton.chat.ChatService
import org.krypton.rag.RagComponents

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
     * @return ChatService instance
     * @throws UnsupportedOperationException Always throws - use DI instead
     */
    fun createChatService(
        ragComponents: RagComponents?,
        ragSettings: RagSettings
    ): ChatService {
        // This factory is deprecated - use DI (Koin) to get ChatService
        throw UnsupportedOperationException("ChatServiceFactory is deprecated. Use DI (Koin) to get ChatService instead: get<ChatService>()")
    }
}

