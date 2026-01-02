package org.krypton.chat.conversation

import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing persisted conversations per vault.
 * 
 * Conversations are stored in `.krypton/chat/` directory within each vault.
 * Each conversation is stored as a separate JSON file, with an index file
 * containing summaries of all conversations.
 */
interface ConversationRepository {
    /**
     * Observes the list of conversations for a given vault.
     * 
     * @param vaultId The vault path (used directly as identifier)
     * @return Flow of conversation summaries, sorted by most recent first
     */
    fun observeConversations(vaultId: String): Flow<List<ConversationSummary>>
    
    /**
     * Gets all messages for a specific conversation.
     * 
     * @param conversationId The conversation to load messages for
     * @return List of messages in chronological order
     */
    suspend fun getConversationMessages(conversationId: ConversationId): List<ChatMessage>
    
    /**
     * Creates a new conversation.
     * 
     * @param vaultId The vault this conversation belongs to
     * @param initialUserMessage The first user message (used for title generation)
     * @param retrievalMode The retrieval mode used for this conversation
     * @return The created conversation ID
     */
    suspend fun createConversation(
        vaultId: String,
        initialUserMessage: String,
        retrievalMode: String,
    ): ConversationId
    
    /**
     * Appends a message to an existing conversation.
     * 
     * @param message The message to append
     */
    suspend fun appendMessage(message: ChatMessage)
    
    /**
     * Updates the summary metadata for a conversation.
     * 
     * @param conversationId The conversation to update
     * @param title Optional new title (null to keep existing)
     * @param lastMessagePreview Optional preview of last message (null to keep existing)
     * @param updatedAt The new updated timestamp
     */
    suspend fun updateConversationSummary(
        conversationId: ConversationId,
        title: String? = null,
        lastMessagePreview: String? = null,
        updatedAt: Long, // Milliseconds since epoch
    )
    
    /**
     * Deletes a conversation and all its messages.
     * 
     * @param conversationId The conversation to delete
     */
    suspend fun deleteConversation(conversationId: ConversationId)
}

