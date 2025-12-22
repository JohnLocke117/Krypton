package org.krypton.krypton.chat

interface ChatService {
    /**
     * Given the current chat history and a new user message,
     * returns an updated list of messages including the assistant's reply.
     */
    suspend fun sendMessage(
        history: List<ChatMessage>,
        userMessage: String
    ): List<ChatMessage>
}

