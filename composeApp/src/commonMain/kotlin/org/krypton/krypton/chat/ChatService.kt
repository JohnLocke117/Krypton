package org.krypton.krypton.chat

/**
 * Interface for chat services that generate responses to user messages.
 * 
 * Implementations can use direct LLM calls (OllamaChatService) or
 * RAG-enhanced responses (RagChatService).
 * 
 * All operations are suspend functions to allow for async network calls.
 */
interface ChatService {
    /**
     * Sends a user message and generates an assistant response.
     * 
     * Given the current chat history and a new user message,
     * returns an updated list of messages including the assistant's reply.
     * 
     * @param history Current conversation history (list of previous messages)
     * @param userMessage The new user message to respond to
     * @return Updated message list including the new user message and assistant response
     * @throws Exception if the chat service fails (network error, API error, etc.)
     */
    suspend fun sendMessage(
        history: List<ChatMessage>,
        userMessage: String
    ): List<ChatMessage>
}

