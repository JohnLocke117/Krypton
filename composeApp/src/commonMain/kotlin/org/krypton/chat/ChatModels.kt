package org.krypton.chat

/**
 * Role of a participant in a chat conversation.
 */
enum class ChatRole {
    /** Message from the user */
    USER,
    /** Message from the assistant/AI */
    ASSISTANT,
    /** System message (e.g., instructions or context) */
    SYSTEM
}

/**
 * Represents a single message in a chat conversation.
 * 
 * @param id Unique identifier for this message
 * @param role The role of the message sender
 * @param content The text content of the message
 * @param timestamp Timestamp when the message was created (milliseconds since epoch)
 */
data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val content: String,
    val timestamp: Long
)

/**
 * Response from a chat service containing the assistant's message and metadata.
 * 
 * Used by [ChatService] to return structured responses with context about
 * how the response was generated (retrieval mode, sources, etc.).
 * 
 * @param message The assistant's response message
 * @param retrievalMode The retrieval mode used for this response
 * @param metadata Metadata about the response generation
 */
data class ChatResponse(
    val message: ChatMessage,
    val retrievalMode: RetrievalMode,
    val metadata: ChatResponseMetadata = ChatResponseMetadata()
)

/**
 * Metadata about a chat response.
 * 
 * Contains information about sources used and additional context
 * that may be useful for displaying or debugging the response.
 * 
 * @param sources Sources cited in the response (RAG chunks or web snippets)
 * @param additionalInfo Additional metadata as key-value pairs
 */
data class ChatResponseMetadata(
    val sources: List<ChatSource> = emptyList(),
    val additionalInfo: Map<String, String> = emptyMap()
)

/**
 * Represents a source cited in a chat response.
 * 
 * Used to track where information came from (local notes or web search).
 * 
 * @param type Type of source (RAG or WEB)
 * @param identifier Identifier or title of the source
 * @param location Optional URL or file path
 */
data class ChatSource(
    val type: SourceType,
    val identifier: String,
    val location: String? = null
)

/**
 * Type of source used in a chat response.
 */
enum class SourceType {
    /** Source from local notes via RAG */
    RAG,
    /** Source from web search */
    WEB
}

