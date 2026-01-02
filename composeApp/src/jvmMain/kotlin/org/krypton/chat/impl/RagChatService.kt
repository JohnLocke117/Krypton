package org.krypton.chat.impl

import org.krypton.chat.ChatService
import org.krypton.chat.ChatResult
import org.krypton.chat.RetrievalMode
import org.krypton.chat.conversation.ConversationId

/**
 * Chat service wrapper that delegates to a base ChatService.
 * 
 * Since the base ChatService (OllamaChatService) now handles retrieval internally,
 * this wrapper simply delegates all calls. It may be removed in the future if
 * no additional functionality is needed.
 */
class RagChatService(
    private val delegate: ChatService
) : ChatService {
    
    override suspend fun sendMessage(
        vaultId: String,
        conversationId: ConversationId?,
        userMessage: String,
        retrievalMode: RetrievalMode,
        currentNotePath: String?
    ): ChatResult {
        return delegate.sendMessage(vaultId, conversationId, userMessage, retrievalMode, currentNotePath)
    }
}

