package org.krypton.chat.conversation

import kotlinx.serialization.Serializable

/**
 * Unique identifier for a conversation.
 */
@JvmInline
@Serializable
value class ConversationId(val value: String)

/**
 * Unique identifier for a message within a conversation.
 */
@JvmInline
@Serializable
value class MessageId(val value: String)

/**
 * Author of a message in a conversation.
 */
enum class MessageAuthor {
    USER,
    ASSISTANT,
    SYSTEM
}

/**
 * Represents a single message in a persisted conversation.
 * 
 * This is separate from the existing [org.krypton.chat.ChatMessage] used in the UI layer.
 * This model is used for persistence and conversation management.
 * 
 * @param id Unique identifier for this message
 * @param conversationId The conversation this message belongs to
 * @param author The author of the message
 * @param text The text content of the message
 * @param createdAt When the message was created
 * @param metadata Additional metadata as key-value pairs
 */
@Serializable
data class ChatMessage(
    val id: MessageId,
    val conversationId: ConversationId,
    val author: MessageAuthor,
    val text: String,
    val createdAt: Long, // Milliseconds since epoch
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * Summary of a conversation for display in conversation lists.
 * 
 * @param id Unique identifier for this conversation
 * @param title The title of the conversation (usually derived from first message)
 * @param createdAt When the conversation was created
 * @param updatedAt When the conversation was last updated
 * @param lastMessagePreview Preview of the last message (truncated)
 * @param retrievalMode The retrieval mode used in this conversation
 */
@Serializable
data class ConversationSummary(
    val id: ConversationId,
    val title: String,
    val createdAt: Long, // Milliseconds since epoch
    val updatedAt: Long, // Milliseconds since epoch
    val lastMessagePreview: String,
    val retrievalMode: String,
)

/**
 * Index file structure for storing conversation summaries.
 */
@Serializable
data class ConversationIndex(
    val conversations: List<ConversationSummary> = emptyList()
)

/**
 * Structure for storing a single conversation's messages.
 */
@Serializable
data class ConversationData(
    val id: ConversationId,
    val vaultId: String,
    val title: String,
    val createdAt: Long, // Milliseconds since epoch
    val updatedAt: Long, // Milliseconds since epoch
    val retrievalMode: String,
    val messages: List<ChatMessage> = emptyList()
)

