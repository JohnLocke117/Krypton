package org.krypton.krypton.ui.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.krypton.krypton.chat.ChatMessage
import org.krypton.krypton.chat.ChatService
import org.krypton.krypton.util.AppLogger

/**
 * State holder for chat functionality using StateFlow pattern.
 * 
 * Manages chat messages, loading states, and error handling.
 * All chat operations are performed asynchronously using the provided coroutine scope.
 * 
 * @param chatService The chat service implementation (e.g., OllamaChatService or RagChatService)
 * @param coroutineScope Coroutine scope for launching async operations
 */
class ChatStateHolder(
    val chatService: ChatService,
    private val coroutineScope: CoroutineScope
) {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // UI status for error handling (replaces simple error string)
    private val _status = MutableStateFlow<UiStatus>(UiStatus.Idle)
    val status: StateFlow<UiStatus> = _status.asStateFlow()
    
    // Keep error for backward compatibility
    @Deprecated("Use status instead", ReplaceWith("status"))
    val error: StateFlow<String?> = _status.map { 
        if (it is UiStatus.Error) it.message else null 
    }.stateIn(coroutineScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)
    
    /**
     * Sends a message to the chat service.
     */
    fun sendMessage(message: String) {
        if (message.isBlank()) return
        
        coroutineScope.launch {
            try {
                _isLoading.value = true
                _status.value = UiStatus.Loading
                
                val updatedMessages = chatService.sendMessage(_messages.value, message)
                _messages.value = updatedMessages
                _status.value = UiStatus.Success
                
                AppLogger.i("ChatStateHolder", "Message sent successfully")
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Failed to send message"
                _status.value = UiStatus.Error(errorMsg, recoverable = true)
                AppLogger.e("ChatStateHolder", "Failed to send message: $errorMsg", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Clears the chat history.
     */
    fun clearHistory() {
        _messages.value = emptyList()
        _status.value = UiStatus.Idle
    }
    
    /**
     * Clears the error state.
     */
    fun clearError() {
        if (_status.value is UiStatus.Error) {
            _status.value = UiStatus.Idle
        }
    }
}

