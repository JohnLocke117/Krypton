package org.krypton.chat.conversation

/**
 * Provides bounded conversation history for LLM context.
 * 
 * Ensures that only the most recent messages within memory bounds
 * are included in the context, respecting both message count and
 * character limits.
 */
interface ConversationMemoryProvider {
    /**
     * Builds a bounded list of context messages for a conversation.
     * 
     * Returns messages in chronological order (oldest to newest),
     * limited by the memory policy (maxMessages and maxChars).
     * 
     * @param conversationId The conversation to build context for
     * @return List of messages to include in context, in chronological order
     */
    suspend fun buildContextMessages(
        conversationId: ConversationId,
    ): List<ChatMessage>
}

