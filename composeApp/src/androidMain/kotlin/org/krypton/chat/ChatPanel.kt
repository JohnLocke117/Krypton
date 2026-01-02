package org.krypton.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.krypton.CatppuccinMochaColors
import org.krypton.ObsidianThemeValues
import org.krypton.Settings
import org.krypton.VectorBackend
import org.krypton.LlmProvider
import org.krypton.ui.state.UiStatus
import org.krypton.rag.RagComponents
import org.krypton.rag.SyncStatus
import org.krypton.ui.state.EditorStateHolder
import org.koin.core.context.GlobalContext
import org.krypton.util.AppLogger
import org.krypton.util.SecretsLoader
import org.krypton.chat.ui.ChatMessageList
import org.krypton.chat.ui.ChatStatusBar
import org.krypton.chat.ui.ChatHistoryState
import org.krypton.chat.ui.ChatHistoryStateImpl
import org.krypton.chat.ui.ConversationList
import org.krypton.chat.conversation.ConversationRepository
import org.krypton.data.repository.SettingsRepository
import org.krypton.chat.*

/**
 * Android implementation of ChatPanel with full RAG controls.
 * 
 * Provides feature parity with Desktop while maintaining Android-specific layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun ChatPanel(
    chatStateHolder: org.krypton.ui.state.ChatStateHolder,
    editorStateHolder: EditorStateHolder?,
    theme: ObsidianThemeValues,
    settings: Settings,
    modifier: Modifier
) {
    val messages by chatStateHolder.messages.collectAsState()
    val messageMetadata by chatStateHolder.messageMetadata.collectAsState()
    val isLoading by chatStateHolder.isLoading.collectAsState()
    val chatStatus by chatStateHolder.status.collectAsState()
    val agentMessage by chatStateHolder.agentMessage.collectAsState()
    val agentError by chatStateHolder.agentError.collectAsState()
    
    // Extract error message from status
    val error = (chatStatus as? UiStatus.Error)?.message
    
    var inputText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    
    // Optimistic user message (shown immediately when sent)
    var optimisticUserMessage by remember { mutableStateOf<ChatMessage?>(null) }
    
    // Combine actual messages with optimistic message and agent message for display
    val displayMessages = remember(messages, optimisticUserMessage, agentMessage) {
        val baseMessages = if (optimisticUserMessage != null && messages.isEmpty()) {
            listOf(optimisticUserMessage!!)
        } else if (optimisticUserMessage != null && messages.isNotEmpty()) {
            // Check if optimistic message is already in the real messages
            val optimisticId = optimisticUserMessage!!.id
            if (messages.any { it.id == optimisticId }) {
                messages // Use real messages if optimistic one is already included
            } else {
                messages + optimisticUserMessage!! // Append optimistic if not yet in real messages
            }
        } else {
            messages
        }
        
        // Add agent message if present (temporary message shown while agent is processing)
        if (agentMessage != null) {
            baseMessages + agentMessage!!
                        } else {
            baseMessages
        }
    }
    
    // Clear optimistic message when real messages update and include it
    LaunchedEffect(messages) {
        optimisticUserMessage?.let { optimistic ->
            if (messages.any { it.id == optimistic.id || (it.role == ChatRole.USER && it.content == optimistic.content) }) {
                optimisticUserMessage = null
            }
        }
    }
    
    // RAG toggle state (disabled by default)
    var ragEnabled by remember { mutableStateOf(false) }
    
    // Web search (Tavily) toggle state (disabled by default)
    var webEnabled by remember { mutableStateOf(false) }
    
    // Multi-query toggle state (disabled by default)
    val koin = remember { GlobalContext.get() }
    
    // Tavily availability check
    val tavilyAvailable = remember {
        SecretsLoader.hasSecret("TAVILLY_API_KEY")
    }
    
    // Compute RetrievalMode from toggles
    val retrievalMode = remember(ragEnabled, webEnabled) {
        when {
            ragEnabled && webEnabled -> RetrievalMode.HYBRID
            ragEnabled -> RetrievalMode.RAG
            webEnabled -> RetrievalMode.WEB
            else -> RetrievalMode.NONE
        }
    }
    val settingsRepository: SettingsRepository = remember { koin.get() }
    val currentSettings by settingsRepository.settingsFlow.collectAsState()
    
    var multiQueryEnabled by remember { 
        mutableStateOf(currentSettings.rag.multiQueryEnabled)
    }
    
    // Reranking toggle state - initialized from settings
    var rerankingEnabled by remember { mutableStateOf(currentSettings.rag.rerankingEnabled) }
    
    // History and New Chat state
    val currentVaultPath = editorStateHolder?.currentDirectory?.value?.toString()
    var showHistorySheet by remember { mutableStateOf(false) }
    val historyCoroutineScope = rememberCoroutineScope()
    
    // Get ChatHistoryState from DI - always available (works with default vault too)
    val historyState = remember {
        try {
            val koin = GlobalContext.get()
            val conversationRepository: ConversationRepository? = koin.getOrNull<ConversationRepository>()
            
            if (conversationRepository != null) {
                ChatHistoryStateImpl(
                    conversationRepository = conversationRepository,
                    chatStateHolder = chatStateHolder,
                    coroutineScope = historyCoroutineScope
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    // Load conversations when sheet opens - use current vault or default
    LaunchedEffect(showHistorySheet, currentVaultPath) {
        if (showHistorySheet && historyState != null) {
            val vaultId = currentVaultPath ?: "default"
            historyState.loadConversations(vaultId)
        }
    }
    
    // Sync toggles with settings changes
    LaunchedEffect(currentSettings.rag.multiQueryEnabled) {
        multiQueryEnabled = currentSettings.rag.multiQueryEnabled
    }
    
    LaunchedEffect(currentSettings.rag.rerankingEnabled) {
        rerankingEnabled = currentSettings.rag.rerankingEnabled
    }
    
    // Error state for Tavily toggle attempts without API key
    var tavilyError by remember { mutableStateOf<String?>(null) }
    
    // LLM provider selection state - sync with settings (Android: Gemini only)
    var selectedLlmProvider by remember { mutableStateOf(currentSettings.llm.provider) }
    var llmProviderDropdownExpanded by remember { mutableStateOf(false) }
    var llmProviderError by remember { mutableStateOf<String?>(null) }
    
    // Sync selectedLlmProvider with settings changes
    LaunchedEffect(currentSettings.llm.provider) {
        selectedLlmProvider = currentSettings.llm.provider
    }
    
    // RAG pipeline selection state - sync with settings (Android: ChromaCloud only)
    var selectedBackend by remember { mutableStateOf(currentSettings.rag.vectorBackend) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    
    // Sync selectedBackend with settings changes
    LaunchedEffect(currentSettings.rag.vectorBackend) {
        selectedBackend = currentSettings.rag.vectorBackend
    }
    
    // Rebuild status state
    var rebuildStatus by remember { mutableStateOf<UiStatus?>(null) }
    
    // Get RAG components from DI
    val ragComponents: RagComponents? = remember {
        try {
            koin.getOrNull<RagComponents>()
        } catch (e: Exception) {
            null
        }
    }
    
    // RAG is available if we have RAG components
    val ragAvailable = ragComponents != null
    
    // Sync status state (Android is query-only, so sync status is always null or SYNCED)
    var syncStatus by remember { mutableStateOf<SyncStatus?>(null) }
    
    // Get current note path
    val currentNotePath = editorStateHolder?.activeDocument?.value?.path
    
    // Advanced options menu state
    var showAdvancedMenu by remember { mutableStateOf(false) }
    
    // Monitor sync status and disable RAG if ChromaDB becomes unavailable
    LaunchedEffect(syncStatus, ragEnabled) {
        if (syncStatus == SyncStatus.UNAVAILABLE && ragEnabled) {
            AppLogger.w("ChatPanel", "ChromaDB unavailable, disabling RAG mode")
            ragEnabled = false
            rerankingEnabled = false
            multiQueryEnabled = false
            AppLogger.d("ChatPanel", "Multi-Query disabled (ChromaDB unavailable)")
            rebuildStatus = UiStatus.Error(
                "ChromaDB is unavailable. RAG mode has been disabled. Chat has reverted to normal mode.",
                recoverable = true
            )
            AppLogger.e("ChatPanel", "ChromaDB unavailable - RAG mode disabled")
        }
    }
    
    // Monitor chat errors and disable RAG if error occurs while RAG is enabled
    LaunchedEffect(chatStatus, ragEnabled) {
        val errorStatus = chatStatus as? UiStatus.Error
        if (errorStatus != null && ragEnabled) {
            val errorMessage = errorStatus.message.lowercase()
            // Check if error is RAG-related
            val isRagError = errorMessage.contains("chromadb", ignoreCase = true) ||
                            errorMessage.contains("vector", ignoreCase = true) ||
                            errorMessage.contains("embedding", ignoreCase = true) ||
                            errorMessage.contains("rag", ignoreCase = true) ||
                            errorMessage.contains("retrieval", ignoreCase = true) ||
                            errorMessage.contains("index", ignoreCase = true) ||
                            errorMessage.contains("collection", ignoreCase = true) ||
                            errorMessage.contains("database", ignoreCase = true) ||
                            errorMessage.contains("connection", ignoreCase = true) ||
                            errorMessage.contains("unavailable", ignoreCase = true) ||
                            errorMessage.contains("unreachable", ignoreCase = true)
            
            if (isRagError) {
                AppLogger.w("ChatPanel", "RAG error detected, disabling RAG mode: ${errorStatus.message}")
                ragEnabled = false
                syncStatus = SyncStatus.UNAVAILABLE
                rebuildStatus = UiStatus.Error(
                    "RAG error: ${errorStatus.message}. RAG mode has been disabled. Chat has reverted to normal mode.",
                    recoverable = true
                )
                AppLogger.e("ChatPanel", "RAG error - RAG mode disabled: ${errorStatus.message}")
            }
        }
    }
    
    // Check sync status on vault change (simplified for Android)
    LaunchedEffect(currentVaultPath, ragComponents, ragEnabled) {
        if (ragComponents != null && currentVaultPath != null && ragEnabled) {
            // Simplified sync status check for Android
            // Assume synced if RAG components are available
            syncStatus = SyncStatus.SYNCED
        } else {
            syncStatus = null
        }
    }
    
    // Auto-dismiss success message
    LaunchedEffect(rebuildStatus) {
        if (rebuildStatus is UiStatus.Success) {
            kotlinx.coroutines.delay(3000)
            rebuildStatus = null
        }
    }
    
    // Helper function to get display name for backend
    fun getBackendDisplayName(backend: VectorBackend): String {
        return when (backend) {
            VectorBackend.CHROMADB -> "ChromaDB"
            VectorBackend.CHROMA_CLOUD -> "ChromaDB Cloud"
        }
    }
    
    // Android: Filter available providers/backends (Gemini only, ChromaCloud only)
    val availableLlmProviders = remember { listOf(LlmProvider.GEMINI) }
    val availableBackends = remember { listOf(VectorBackend.CHROMA_CLOUD) }

    Column(modifier = modifier) {
        // Top toolbar row
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = theme.BackgroundElevated
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Retrieval mode selector (compact)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Web search toggle
                    RetrievalModeSelector(
                        ragEnabled = ragEnabled,
                        webEnabled = webEnabled,
                        tavilyAvailable = tavilyAvailable,
                        onRagToggle = { /* Handled by RagToggleButton */ },
                        onWebToggle = {
                            webEnabled = !webEnabled
                            tavilyError = null
                            AppLogger.d("ChatPanel", "Web search ${if (webEnabled) "enabled" else "disabled"}")
                        },
                        onTavilyError = { errorMsg ->
                            tavilyError = errorMsg
                            AppLogger.w("ChatPanel", "Attempted to enable Tavily without API key")
                        },
                        theme = theme,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                // LLM provider selector (Gemini only on Android)
                LlmProviderSelector(
                    selectedProvider = selectedLlmProvider,
                    availableProviders = availableLlmProviders,
                    expanded = llmProviderDropdownExpanded,
                    onExpandedChange = { llmProviderDropdownExpanded = it },
                    onProviderSelected = { provider ->
                        if (provider == LlmProvider.GEMINI) {
                            val apiKey = SecretsLoader.loadSecret("GEMINI_API_KEY")
                            if (apiKey.isNullOrBlank()) {
                                llmProviderError = "GEMINI_API_KEY not found in local.secrets.properties. Please add it to use Gemini API."
                                llmProviderDropdownExpanded = false
                                return@LlmProviderSelector
                            }
                        }
                        
                        coroutineScope.launch {
                            settingsRepository.update { current ->
                                current.copy(
                                    llm = current.llm.copy(provider = provider)
                                )
                            }
                        }
                        llmProviderDropdownExpanded = false
                        llmProviderError = null
                    },
                    onError = { errorMsg -> llmProviderError = errorMsg },
                    theme = theme,
                    modifier = Modifier.size(32.dp)
                )
                
                // RAG controls wrapped in bordered box (always visible)
                Surface(
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = theme.Border,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Transparent
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // RAG toggle icon (always visible, disabled if RAG not available)
                        RagToggleButton(
                            ragEnabled = ragEnabled && ragAvailable,
                            syncStatus = if (ragAvailable) syncStatus else SyncStatus.UNAVAILABLE,
                            onToggle = {
                                if (!ragAvailable) {
                                    return@RagToggleButton
                                }
                                if (ragEnabled) {
                                    ragEnabled = false
                                    rerankingEnabled = false
                                    multiQueryEnabled = false
                                    AppLogger.d("ChatPanel", "Multi-Query disabled (RAG disabled)")
                                } else {
                                    // Simply enable RAG - no collection checks or ingestion prompts on Android
                                    ragEnabled = true
                                    rerankingEnabled = false
                                    multiQueryEnabled = false
                                    syncStatus = SyncStatus.SYNCED // Assume synced for query-only mode
                                    AppLogger.d("ChatPanel", "RAG enabled (query-only mode)")
                                }
                            },
                            theme = theme
                        )
                        
                        // Sync status indicator
                        SyncStatusIndicator(
                            syncStatus = if (ragAvailable) syncStatus else SyncStatus.UNAVAILABLE,
                            ragEnabled = ragEnabled && ragAvailable
                        )
                        
                        // Multi-query toggle
                        MultiQueryToggle(
                            enabled = multiQueryEnabled,
                            ragEnabled = ragEnabled && ragAvailable,
                            onToggle = {
                                if (ragEnabled && ragAvailable) {
                                    val newValue = !multiQueryEnabled
                                    multiQueryEnabled = newValue
                                    AppLogger.d("ChatPanel", "Multi-Query ${if (newValue) "enabled" else "disabled"}")
                                    coroutineScope.launch {
                                        settingsRepository.update { currentSettings ->
                                            currentSettings.copy(
                                                rag = currentSettings.rag.copy(
                                                    multiQueryEnabled = newValue
                                                )
                                            )
                                        }
                                    }
                                }
                            },
                            theme = theme
                        )
                        
                        // Reranking toggle
                        RerankingToggle(
                            enabled = rerankingEnabled,
                            ragEnabled = ragEnabled && ragAvailable,
                            onToggle = {
                                if (ragEnabled && ragAvailable) {
                                    val newValue = !rerankingEnabled
                                    rerankingEnabled = newValue
                                    AppLogger.d("ChatPanel", "Reranking ${if (newValue) "enabled" else "disabled"}")
                                    coroutineScope.launch {
                                        settingsRepository.update { currentSettings ->
                                            currentSettings.copy(
                                                rag = currentSettings.rag.copy(
                                                    rerankingEnabled = newValue
                                                )
                                            )
                                        }
                                    }
                                }
                            },
                            theme = theme
                        )
                    }
                }
                
                // History and New Chat buttons
                // History button - always visible when historyState is available (works with default vault)
                if (historyState != null) {
                    IconButton(onClick = { showHistorySheet = true }) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "Chat History",
                            tint = theme.TextPrimary
                        )
                    }
                }
                
                // New chat button
                IconButton(onClick = { chatStateHolder.clearHistory() }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "New Chat",
                        tint = theme.TextPrimary
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Advanced options menu
                Box {
                    IconButton(onClick = { showAdvancedMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Advanced options")
                    }
                    
                    DropdownMenu(
                        expanded = showAdvancedMenu,
                        onDismissRequest = { showAdvancedMenu = false }
                    ) {
                        if (ragAvailable && ragEnabled) {
                            DropdownMenuItem(
                                text = { Text("Vector Store: ${getBackendDisplayName(selectedBackend)}") },
                                onClick = {
                                    dropdownExpanded = !dropdownExpanded
                                    showAdvancedMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Multi-Query: ${if (multiQueryEnabled) "On" else "Off"}") },
                                onClick = {
                                    val newValue = !multiQueryEnabled
                                    multiQueryEnabled = newValue
                                    AppLogger.d("ChatPanel", "Multi-Query ${if (newValue) "enabled" else "disabled"}")
                                    coroutineScope.launch {
                                        settingsRepository.update { currentSettings ->
                                            currentSettings.copy(
                                                rag = currentSettings.rag.copy(
                                                    multiQueryEnabled = newValue
                                                )
                                            )
                                        }
                                    }
                                    showAdvancedMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Reranking: ${if (rerankingEnabled) "On" else "Off"}") },
                                onClick = {
                                    val newValue = !rerankingEnabled
                                    rerankingEnabled = newValue
                                    AppLogger.d("ChatPanel", "Reranking ${if (newValue) "enabled" else "disabled"}")
                                    coroutineScope.launch {
                                        settingsRepository.update { currentSettings ->
                                            currentSettings.copy(
                                                rag = currentSettings.rag.copy(
                                                    rerankingEnabled = newValue
                                                )
                                            )
                                        }
                                    }
                                    showAdvancedMenu = false
                                }
                            )
                            Divider()
                        }
                        // "Rebuild Vector DB" removed - Android is query-only, indexing must be done on Desktop
                    }
                }
                
                // Vector store selector dropdown (shown when triggered from menu)
                if (dropdownExpanded) {
                    VectorStoreSelector(
                        selectedBackend = selectedBackend,
                        availableBackends = availableBackends,
                        ragEnabled = ragEnabled,
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = it },
                        onBackendSelected = { backend ->
                            if (backend == VectorBackend.CHROMA_CLOUD) {
                                val apiKey = SecretsLoader.loadSecret("CHROMA_API_KEY")
                                if (apiKey.isNullOrBlank()) {
                                    tavilyError = "CHROMA_API_KEY not found in local.secrets.properties. Please add it to use ChromaDB Cloud."
                                    dropdownExpanded = false
                                    return@VectorStoreSelector
                                }
                            }
                            
                            coroutineScope.launch {
                                settingsRepository.update { current ->
                                    current.copy(
                                        rag = current.rag.copy(vectorBackend = backend)
                                    )
                                }
                            }
                            dropdownExpanded = false
                        },
                        onError = { errorMsg -> tavilyError = errorMsg },
                        theme = theme
                    )
                }
            }
        }
        
        // Status row (compact)
        ChatStatusBar(
            rebuildStatus = rebuildStatus,
            tavilyError = tavilyError,
            llmProviderError = llmProviderError,
            chatError = error,
            agentError = agentError,
            onDismissRebuildStatus = { rebuildStatus = null },
            onDismissTavilyError = { tavilyError = null },
            onDismissLlmProviderError = { llmProviderError = null },
            onDismissChatError = { chatStateHolder.clearError() },
            onDismissAgentError = { chatStateHolder.clearAgentError() },
            theme = theme
        )

        // Messages list
        ChatMessageList(
            messages = displayMessages,
            isLoading = isLoading,
            theme = theme,
            settings = settings,
            messageMetadata = messageMetadata,
            modifier = Modifier.weight(1f)
        )

        // Input area
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CatppuccinMochaColors.Crust
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "Type your message...",
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.TextSecondary
                        )
                    },
                    textStyle = MaterialTheme.typography.bodySmall,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.TextPrimary,
                        unfocusedTextColor = theme.TextPrimary,
                        focusedBorderColor = theme.Accent,
                        unfocusedBorderColor = theme.Border,
                        disabledBorderColor = theme.Border,
                        disabledTextColor = theme.TextSecondary,
                        focusedContainerColor = CatppuccinMochaColors.Surface0,
                        unfocusedContainerColor = CatppuccinMochaColors.Surface0,
                        disabledContainerColor = CatppuccinMochaColors.Surface0
                    ),
                    maxLines = 5,
                    minLines = 1,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank() && !isLoading) {
                                val messageText = inputText.trim()
                                inputText = ""
                                
                                val optimisticMessage = ChatMessage(
                                    id = "optimistic_${System.currentTimeMillis()}",
                                    role = ChatRole.USER,
                                    content = messageText,
                                    timestamp = System.currentTimeMillis()
                                )
                                optimisticUserMessage = optimisticMessage
                                
                                // Check if RAG mode requires a vault
                                val requiresVault = retrievalMode == RetrievalMode.RAG || retrievalMode == RetrievalMode.HYBRID
                                
                                if (requiresVault && currentVaultPath == null) {
                                    // Show error - sendMessage will handle displaying the error via status
                                    chatStateHolder.sendMessage(
                                        message = messageText,
                                        retrievalMode = retrievalMode,
                                        vaultId = "",
                                        currentNotePath = currentNotePath
                                    )
                                } else {
                                    // Use default vault ID for non-RAG modes when no vault is open
                                    val effectiveVaultId = currentVaultPath ?: "default"
                                    
                                    chatStateHolder.sendMessage(
                                        message = messageText,
                                        retrievalMode = retrievalMode,
                                        vaultId = effectiveVaultId,
                                        currentNotePath = currentNotePath
                                    )
                                }
                            }
                        }
                    )
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank() && !isLoading) {
                                val messageText = inputText.trim()
                                inputText = ""
                                
                                val optimisticMessage = ChatMessage(
                                    id = "optimistic_${System.currentTimeMillis()}",
                                    role = ChatRole.USER,
                                    content = messageText,
                                    timestamp = System.currentTimeMillis()
                                )
                                optimisticUserMessage = optimisticMessage
                                
                                // Check if RAG mode requires a vault
                                val requiresVault = retrievalMode == RetrievalMode.RAG || retrievalMode == RetrievalMode.HYBRID
                                
                                if (requiresVault && currentVaultPath == null) {
                                    // Show error - sendMessage will handle displaying the error via status
                                    chatStateHolder.sendMessage(
                                        message = messageText,
                                        retrievalMode = retrievalMode,
                                        vaultId = "",
                                        currentNotePath = currentNotePath
                                    )
                                } else {
                                    // Use default vault ID for non-RAG modes when no vault is open
                                    val effectiveVaultId = currentVaultPath ?: "default"
                                    
                                    chatStateHolder.sendMessage(
                                        message = messageText,
                                        retrievalMode = retrievalMode,
                                        vaultId = effectiveVaultId,
                                        currentNotePath = currentNotePath
                                    )
                                }
                            }
                        },
                        enabled = inputText.isNotBlank() && !isLoading
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
    
    // History bottom sheet
    if (showHistorySheet && historyState != null) {
        val historyUiState by historyState.state.collectAsState()
        
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Chat History",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                ConversationList(
                    conversations = historyUiState.conversations,
                    selectedId = historyUiState.selectedConversationId,
                    onSelect = { conversationId ->
                        historyState.selectConversation(conversationId)
                        showHistorySheet = false
                    },
                    onDelete = { conversationId ->
                        historyState.deleteConversation(conversationId)
                    },
                    theme = theme,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
