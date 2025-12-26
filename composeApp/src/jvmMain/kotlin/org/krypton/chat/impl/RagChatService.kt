package org.krypton.chat.impl

import org.krypton.chat.ChatService
import org.krypton.chat.ChatResponse
import org.krypton.chat.RetrievalMode

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
        message: String,
        mode: RetrievalMode,
        threadId: String?
    ): ChatResponse {
        return delegate.sendMessage(message, mode, threadId)
    }
}

