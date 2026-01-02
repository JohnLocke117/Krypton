package org.krypton.chat.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.krypton.chat.conversation.ConversationId
import org.krypton.chat.conversation.ConversationRepository
import org.krypton.ui.state.ChatStateHolder
import org.krypton.util.AppLogger

/**
 * Implementation of ChatHistoryState.
 */
class ChatHistoryStateImpl(
    private val conversationRepository: ConversationRepository,
    private val chatStateHolder: ChatStateHolder,
    private val coroutineScope: CoroutineScope
) : ChatHistoryState {
    
    private val _state = MutableStateFlow(ChatHistoryUiState())
    override val state: StateFlow<ChatHistoryUiState> = _state.asStateFlow()
    
    override fun loadConversations(vaultId: String) {
        coroutineScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)
                
                conversationRepository.observeConversations(vaultId).collect { conversations ->
                    _state.value = _state.value.copy(
                        conversations = conversations,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                AppLogger.e("ChatHistoryStateImpl", "Failed to load conversations for vault: $vaultId", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load conversations"
                )
            }
        }
    }
    
    override fun selectConversation(conversationId: ConversationId) {
        _state.value = _state.value.copy(selectedConversationId = conversationId)
        chatStateHolder.loadConversation(conversationId)
    }
    
    override fun deleteConversation(conversationId: ConversationId) {
        coroutineScope.launch {
            try {
                conversationRepository.deleteConversation(conversationId)
                
                // If this was the selected conversation, clear selection
                if (_state.value.selectedConversationId == conversationId) {
                    _state.value = _state.value.copy(selectedConversationId = null)
                    chatStateHolder.createNewConversation()
                }
            } catch (e: Exception) {
                AppLogger.e("ChatHistoryStateImpl", "Failed to delete conversation: ${conversationId.value}", e)
                _state.value = _state.value.copy(
                    errorMessage = e.message ?: "Failed to delete conversation"
                )
            }
        }
    }
}

