package org.krypton.data.chat

import org.krypton.chat.conversation.*

/**
 * Implementation of ConversationMemoryProvider that applies memory bounds.
 * 
 * Loads all messages, takes the last N messages, then trims by character count
 * from newest to oldest, ensuring we stay within both limits.
 */
class ConversationMemoryProviderImpl(
    private val repository: ConversationRepository,
    private val policy: ConversationMemoryPolicy,
) : ConversationMemoryProvider {

    override suspend fun buildContextMessages(
        conversationId: ConversationId,
    ): List<ChatMessage> {
        val all = repository.getConversationMessages(conversationId)
        
        // Take last N messages
        val tail = all.takeLast(policy.maxMessages)
        
        // Build result from newest to oldest, respecting char limit
        val result = mutableListOf<ChatMessage>()
        var charCount = 0
        
        for (msg in tail.asReversed()) {
            val len = msg.text.length
            if (charCount + len > policy.maxChars) {
                break
            }
            result += msg
            charCount += len
        }
        
        // Return in chronological order (oldest to newest)
        return result.asReversed()
    }
}

