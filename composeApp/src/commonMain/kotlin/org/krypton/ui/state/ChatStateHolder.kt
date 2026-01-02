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
import org.krypton.chat.conversation.ConversationId
import org.krypton.chat.conversation.ConversationRepository
import org.krypton.chat.conversation.MessageAuthor
import org.krypton.util.AppLogger
import org.krypton.ui.state.UiStatus

/**
 * Platform-specific check: Does RAG mode require a vault to be open?
 * 
 * - Android: RAG is query-only with ChromaDB Cloud, so no vault required
 * - Desktop (JVM): RAG requires a vault for indexing local notes
 */
internal expect fun ragRequiresVault(): Boolean

/**
 * State holder for chat functionality using StateFlow pattern.
 * 
 * Manages chat messages, loading states, and error handling.
 * All chat operations are performed asynchronously using the provided coroutine scope.
 * Now supports conversation persistence and history management.
 * 
 * @param chatService The chat service implementation (e.g., OllamaChatService or GeminiChatService)
 * @param conversationRepository Repository for loading conversation messages
 * @param coroutineScope Coroutine scope for launching async operations
 */
class ChatStateHolder(
    val chatService: ChatService,
    private val conversationRepository: ConversationRepository?,
    private val coroutineScope: CoroutineScope
) {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    // Current conversation ID
    private val _currentConversationId = MutableStateFlow<ConversationId?>(null)
    val currentConversationId: StateFlow<ConversationId?> = _currentConversationId.asStateFlow()
    
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
    
    // Agent error message (shown when agent fails and chat reverts to normal)
    private val _agentError = MutableStateFlow<String?>(null)
    val agentError: StateFlow<String?> = _agentError.asStateFlow()
    
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
     * @param vaultId The vault this conversation belongs to (required)
     * @param conversationId Optional conversation ID. If null, a new conversation is created.
     * @param currentNotePath Optional path to the currently active/open note file for agent context
     */
    fun sendMessage(
        message: String, 
        retrievalMode: RetrievalMode = RetrievalMode.NONE,
        vaultId: String,
        conversationId: ConversationId? = _currentConversationId.value,
        currentNotePath: String? = null
    ) {
        if (message.isBlank()) {
            return
        }
        
        // Check if RAG mode requires a vault but vaultId is empty
        // On Android, RAG is query-only with ChromaDB Cloud, so no vault is required
        val requiresVault = (retrievalMode == RetrievalMode.RAG || retrievalMode == RetrievalMode.HYBRID) && ragRequiresVault()
        if (requiresVault && vaultId.isBlank()) {
            _status.value = UiStatus.Error(
                "RAG mode requires a vault to be open. Please open a vault first or disable RAG mode.",
                recoverable = true
            )
            return
        }
        
        coroutineScope.launch {
            try {
                _isLoading.value = true
                _status.value = UiStatus.Loading
                
                // Create user message and add to history (optimistic update)
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
                    content = "Processing...",
                    timestamp = org.krypton.util.createTimeProvider().currentTimeMillis()
                )
                _agentMessage.value = tempAgentMessage
                
                // Call chat service with new signature
                val result = chatService.sendMessage(
                    vaultId = vaultId,
                    conversationId = conversationId,
                    userMessage = message,
                    retrievalMode = retrievalMode,
                    currentNotePath = currentNotePath
                )
                
                // Update current conversation ID
                _currentConversationId.value = result.conversationId
                
                // Convert conversation message to old format for display
                val assistantMessage = ChatMessage(
                    id = result.assistantMessage.id.value,
                    role = when (result.assistantMessage.author) {
                        MessageAuthor.USER -> org.krypton.chat.ChatRole.USER
                        MessageAuthor.ASSISTANT -> org.krypton.chat.ChatRole.ASSISTANT
                        MessageAuthor.SYSTEM -> org.krypton.chat.ChatRole.SYSTEM
                    },
                    content = result.assistantMessage.text,
                    timestamp = result.assistantMessage.createdAt
                )
                
                // Clear temporary agent message and add actual response
                _agentMessage.value = null
                val updatedMessages = updatedMessagesWithUser + assistantMessage
                _messages.value = updatedMessages
                
                // Reload messages from repository to ensure consistency
                loadConversationMessages(result.conversationId)
                
                // Set agent error if present (will be displayed in UI)
                _agentError.value = result.agentError
                
                _status.value = UiStatus.Success
                
                AppLogger.i("ChatStateHolder", "Message sent successfully with mode: $retrievalMode, conversation: ${result.conversationId.value}")
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
     * Loads messages from a conversation.
     * 
     * @param conversationId The conversation to load
     */
    fun loadConversation(conversationId: ConversationId) {
        _currentConversationId.value = conversationId
        loadConversationMessages(conversationId)
    }
    
    /**
     * Creates a new conversation and clears current messages.
     */
    fun createNewConversation() {
        _currentConversationId.value = null
        _messages.value = emptyList()
        _messageMetadata.value = emptyMap()
        _status.value = UiStatus.Idle
    }
    
    /**
     * Loads messages from the repository for the current conversation.
     */
    private fun loadConversationMessages(conversationId: ConversationId) {
        if (conversationRepository == null) return
        
        coroutineScope.launch {
            try {
                val conversationMessages = conversationRepository.getConversationMessages(conversationId)
                val oldFormatMessages = conversationMessages.map { msg ->
                    ChatMessage(
                        id = msg.id.value,
                        role = when (msg.author) {
                            MessageAuthor.USER -> org.krypton.chat.ChatRole.USER
                            MessageAuthor.ASSISTANT -> org.krypton.chat.ChatRole.ASSISTANT
                            MessageAuthor.SYSTEM -> org.krypton.chat.ChatRole.SYSTEM
                        },
                        content = msg.text,
                        timestamp = msg.createdAt
                    )
                }
                _messages.value = oldFormatMessages
            } catch (e: Exception) {
                AppLogger.e("ChatStateHolder", "Failed to load conversation messages: ${conversationId.value}", e)
            }
        }
    }
    
    /**
     * Clears the chat history.
     */
    fun clearHistory() {
        createNewConversation()
    }
    
    /**
     * Clears the agent error state.
     */
    fun clearAgentError() {
        _agentError.value = null
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

