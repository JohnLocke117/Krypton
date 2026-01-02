package org.krypton.chat.conversation

/**
 * Policy defining memory bounds for conversation history.
 * 
 * Different platforms may use different policies based on their
 * LLM context window limitations.
 * 
 * @param maxMessages Maximum number of messages to include in context
 * @param maxChars Maximum number of characters to include in context
 */
data class ConversationMemoryPolicy(
    val maxMessages: Int,
    val maxChars: Int,
)

