package org.krypton.ui.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.krypton.chat.ChatMessage
import org.krypton.chat.ChatResponseMetadata
import org.krypton.chat.ChatService
import org.krypton.chat.RetrievalMode
import org.krypton.util.AppLogger
import org.krypton.ui.state.UiStatus

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
    
    // Store metadata (sources) for each message by message ID
    private val _messageMetadata = MutableStateFlow<Map<String, ChatResponseMetadata>>(emptyMap())
    val messageMetadata: StateFlow<Map<String, ChatResponseMetadata>> = _messageMetadata.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // UI status for error handling (replaces simple error string)
    private val _status = MutableStateFlow<UiStatus>(UiStatus.Idle)
    val status: StateFlow<UiStatus> = _status.asStateFlow()
    
    // Temporary agent message (shown while agent is processing)
    private val _agentMessage = MutableStateFlow<ChatMessage?>(null)
    val agentMessage: StateFlow<ChatMessage?> = _agentMessage.asStateFlow()
    
    // Keep error for backward compatibility
    @Deprecated("Use status instead", ReplaceWith("status"))
    val error: StateFlow<String?> = _status.map { 
        if (it is UiStatus.Error) it.message else null 
    }.stateIn(coroutineScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)
    
    /**
     * Sends a message to the chat service.
     * 
     * @param message The user's message
     * @param retrievalMode The retrieval mode to use
     * @param vaultPath Optional path to the currently opened vault/folder for agent context
     * @param currentNotePath Optional path to the currently active/open note file for agent context
     */
    fun sendMessage(
        message: String, 
        retrievalMode: RetrievalMode = RetrievalMode.NONE,
        vaultPath: String? = null,
        currentNotePath: String? = null
    ) {
        if (message.isBlank()) return
        
        coroutineScope.launch {
            try {
                _isLoading.value = true
                _status.value = UiStatus.Loading
                
                // Create user message and add to history
                val userMessage = ChatMessage(
                    id = org.krypton.util.createIdGenerator().generateId(),
                    role = org.krypton.chat.ChatRole.USER,
                    content = message,
                    timestamp = org.krypton.util.createTimeProvider().currentTimeMillis()
                )
                
                // Add user message immediately
                val updatedMessagesWithUser = _messages.value + userMessage
                _messages.value = updatedMessagesWithUser
                
                // Show temporary agent message immediately (will be updated/replaced when response arrives)
                val tempAgentId = org.krypton.util.createIdGenerator().generateId()
                val tempAgentMessage = ChatMessage(
                    id = tempAgentId,
                    role = org.krypton.chat.ChatRole.ASSISTANT,
                    content = "Agent called: Processing...",
                    timestamp = org.krypton.util.createTimeProvider().currentTimeMillis()
                )
                _agentMessage.value = tempAgentMessage
                
                // Call chat service with new signature
                val response = chatService.sendMessage(
                    message = message,
                    mode = retrievalMode,
                    threadId = null, // TODO: Support threadId if needed
                    vaultPath = vaultPath,
                    currentNotePath = currentNotePath
                )
                
                // Check if an agent was used (indicated in metadata)
                val agentName = response.metadata.additionalInfo["agent"]
                if (agentName != null) {
                    // Update temporary message with agent name
                    val updatedTempMessage = tempAgentMessage.copy(
                        content = "Agent called: $agentName"
                    )
                    _agentMessage.value = updatedTempMessage
                    
                    // Small delay to show the agent name, then replace with actual response
                    kotlinx.coroutines.delay(300)
                }
                
                // Clear temporary agent message and add actual response
                _agentMessage.value = null
                val updatedMessages = updatedMessagesWithUser + response.message
                _messages.value = updatedMessages
                
                // Store metadata (sources) for this message
                val updatedMetadata = _messageMetadata.value.toMutableMap()
                updatedMetadata[response.message.id] = response.metadata
                _messageMetadata.value = updatedMetadata
                
                _status.value = UiStatus.Success
                
                AppLogger.i("ChatStateHolder", "Message sent successfully with mode: $retrievalMode, sources: ${response.metadata.sources.size}")
            } catch (e: Exception) {
                // Clear temporary agent message on error
                _agentMessage.value = null
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
        _messageMetadata.value = emptyMap()
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

