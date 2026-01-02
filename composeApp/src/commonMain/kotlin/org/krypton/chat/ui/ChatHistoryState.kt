package org.krypton.chat.ui

import kotlinx.coroutines.flow.StateFlow
import org.krypton.chat.conversation.ConversationId
import org.krypton.chat.conversation.ConversationSummary

/**
 * UI state for chat history management.
 */
data class ChatHistoryUiState(
    val conversations: List<ConversationSummary> = emptyList(),
    val selectedConversationId: ConversationId? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * State holder for chat history UI.
 * 
 * Manages conversation list, selection, and deletion.
 */
interface ChatHistoryState {
    /**
     * Current UI state.
     */
    val state: StateFlow<ChatHistoryUiState>
    
    /**
     * Loads conversations for a vault.
     * 
     * @param vaultId The vault to load conversations for
     */
    fun loadConversations(vaultId: String)
    
    /**
     * Selects a conversation.
     * 
     * @param conversationId The conversation to select
     */
    fun selectConversation(conversationId: ConversationId)
    
    /**
     * Deletes a conversation.
     * 
     * @param conversationId The conversation to delete
     */
    fun deleteConversation(conversationId: ConversationId)
}

