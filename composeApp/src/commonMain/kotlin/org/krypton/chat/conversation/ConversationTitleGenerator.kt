package org.krypton.chat.conversation

/**
 * Utility for generating conversation titles.
 * 
 * For now, uses a simple approach: truncate the first user message.
 * In the future, this could use an LLM to generate more descriptive titles.
 */
object ConversationTitleGenerator {
    /**
     * Generates a title from the first user message.
     * 
     * @param message The first user message
     * @return A title (truncated to ~50 characters)
     */
    fun generateFromMessage(message: String): String {
        val trimmed = message.trim()
        return if (trimmed.length <= 50) {
            trimmed
        } else {
            trimmed.take(47) + "..."
        }
    }
    
    /**
     * Generates a title from multiple messages (uses the first user message).
     * 
     * @param messages List of messages in the conversation
     * @return A title derived from the first user message
     */
    fun generateFromMessages(messages: List<ChatMessage>): String {
        val firstUserMessage = messages.firstOrNull { it.author == MessageAuthor.USER }
        return if (firstUserMessage != null) {
            generateFromMessage(firstUserMessage.text)
        } else {
            "New Conversation"
        }
    }
}

