package org.krypton.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
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
import org.krypton.chat.IngestionPromptDialog
import org.krypton.ui.state.EditorStateHolder
import org.koin.core.context.GlobalContext
import org.krypton.util.AppLogger
import org.krypton.util.SecretsLoader
import org.krypton.chat.ui.ChatMessageList
import org.krypton.chat.ui.ChatStatusBar
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
    val isLoading by chatStateHolder.isLoading.collectAsState()
    val chatStatus by chatStateHolder.status.collectAsState()
    val agentMessage by chatStateHolder.agentMessage.collectAsState()
    
    // Extract error message from status
    val error = (chatStatus as? UiStatus.Error)?.message
    
    var inputText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    // Separate scope for long-running operations (not tied to Compose lifecycle)
    val ingestionScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    
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
    
    // Re-index prompt dialog state
    var showReindexPrompt by remember { mutableStateOf(false) }
    
    // Sync status state
    var syncStatus by remember { mutableStateOf<SyncStatus?>(null) }
    
    // Track when ingestion last completed to prevent immediate re-check
    var lastIngestionCompleteTime by remember { mutableStateOf<Long?>(null) }
    
    // Ingestion prompt dialog state
    var showIngestionPrompt by remember { mutableStateOf(false) }
    var isIngesting by remember { mutableStateOf(false) }
    
    // Get current vault path
    val currentVaultPath = editorStateHolder?.currentDirectory?.value?.toString()
    
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
                
                // RAG controls (if available) - compact display in toolbar
                if (ragAvailable) {
                    RagToggleButton(
                        ragEnabled = ragEnabled,
                        syncStatus = syncStatus,
                        onToggle = {
                            if (ragEnabled) {
                                ragEnabled = false
                                rerankingEnabled = false
                                multiQueryEnabled = false
                                AppLogger.d("ChatPanel", "Multi-Query disabled (RAG disabled)")
                            } else {
                                // Simplified RAG activation for Android
                                coroutineScope.launch {
                                    if (ragComponents != null && currentVaultPath != null) {
                                        // Check if vector store has vault data
                                        val vectorStore = ragComponents.vectorStore
                                        val hasVaultData = if (vectorStore is org.krypton.data.rag.impl.ChromaCloudVectorStore) {
                                            try {
                                                vectorStore.hasVaultData(currentVaultPath)
                                            } catch (e: Exception) {
                                                false
                                            }
                                        } else {
                                            false
                                        }
                                        
                                        if (!hasVaultData) {
                                            showIngestionPrompt = true
                                        } else {
                                            // Assume synced if data exists
                                            syncStatus = SyncStatus.SYNCED
                                            ragEnabled = true
                                            rerankingEnabled = false
                                            multiQueryEnabled = false
                                        }
                                    } else {
                                        ragEnabled = true
                                        rerankingEnabled = false
                                        multiQueryEnabled = false
                                    }
                                }
                            }
                        },
                        theme = theme,
                        modifier = Modifier.size(32.dp)
                    )
                    
                    SyncStatusIndicator(
                        syncStatus = syncStatus,
                        ragEnabled = ragEnabled,
                        modifier = Modifier.size(8.dp)
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
                        DropdownMenuItem(
                            text = { Text("Rebuild Vector DB") },
                            onClick = {
                                if (currentVaultPath != null && ragComponents != null) {
                                    ingestionScope.launch {
                                        try {
                                            withContext(Dispatchers.Main) {
                                                rebuildStatus = UiStatus.Loading
                                                isIngesting = true
                                            }
                                            
                                            // Simplified rebuild for Android - use indexer directly
                                            val indexer = ragComponents.indexer
                                            if (indexer is org.krypton.rag.VaultIndexService) {
                                                indexer.indexVault(
                                                    rootPath = currentVaultPath,
                                                    existingFileHashes = emptyMap()
                                                )
                                            }
                                            
                                            withContext(Dispatchers.Main) {
                                                syncStatus = SyncStatus.SYNCED
                                                rebuildStatus = UiStatus.Success
                                                isIngesting = false
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                rebuildStatus = UiStatus.Error(
                                                    e.message ?: "Failed to rebuild vector database",
                                                    recoverable = true
                                                )
                                                isIngesting = false
                                            }
                                        }
                                    }
                                }
                                showAdvancedMenu = false
                            },
                            enabled = currentVaultPath != null && 
                                     ragComponents != null &&
                                     syncStatus != SyncStatus.UNAVAILABLE &&
                                     rebuildStatus !is UiStatus.Loading &&
                                     !isIngesting
                        )
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
            onDismissRebuildStatus = { rebuildStatus = null },
            onDismissTavilyError = { tavilyError = null },
            onDismissLlmProviderError = { llmProviderError = null },
            onDismissChatError = { chatStateHolder.clearError() },
            theme = theme
        )

        // Messages list
        ChatMessageList(
            messages = displayMessages,
            isLoading = isLoading,
            theme = theme,
            settings = settings,
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
                                
                                chatStateHolder.sendMessage(messageText, retrievalMode, currentVaultPath, currentNotePath)
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
                                
                                chatStateHolder.sendMessage(messageText, retrievalMode, currentVaultPath, currentNotePath)
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

    // Ingestion prompt dialog state
    var userWantsToIngest by remember { mutableStateOf(false) }
    var ingestionError by remember { mutableStateOf<String?>(null) }
    var ingestionSuccess by remember { mutableStateOf(false) }
    
    // Re-index prompt dialog state
    var userWantsToReindex by remember { mutableStateOf(false) }
    var reindexError by remember { mutableStateOf<String?>(null) }
    var reindexSuccess by remember { mutableStateOf(false) }
    
    // Show dialog if prompt is requested OR if ingestion is in progress/complete
    val showDialog = showIngestionPrompt || isIngesting || ingestionSuccess || ingestionError != null
    
    // Show re-index dialog if prompt is requested OR if re-indexing is in progress/complete
    val showReindexDialog = showReindexPrompt || isIngesting || reindexSuccess || reindexError != null
    
    if (showDialog) {
        IngestionPromptDialog(
            onContinue = {
                if (!isIngesting && !ingestionSuccess && ingestionError == null) {
                    userWantsToIngest = true
                    showIngestionPrompt = false
                    ingestionError = null
                    ingestionSuccess = false
                }
            },
            onCancel = {
                if (!isIngesting) {
                    userWantsToIngest = false
                    showIngestionPrompt = false
                    isIngesting = false
                    ingestionError = null
                    ingestionSuccess = false
                }
            },
            isIngesting = isIngesting,
            errorMessage = ingestionError,
            success = ingestionSuccess,
            theme = theme
        )
    }
    
    // Handle ingestion after user confirms (simplified for Android)
    LaunchedEffect(userWantsToIngest) {
        if (userWantsToIngest && ragComponents != null && currentVaultPath != null) {
            userWantsToIngest = false
            isIngesting = true
            ingestionError = null
            ingestionSuccess = false
            
            ingestionScope.launch {
                try {
                    // Simplified ingestion for Android - use indexer directly
                    val indexer = ragComponents.indexer
                    if (indexer is org.krypton.rag.VaultIndexService) {
                        indexer.indexVault(
                            rootPath = currentVaultPath,
                            existingFileHashes = emptyMap()
                        )
                    }
                    
                    withContext(Dispatchers.Main) {
                        isIngesting = false
                        ragEnabled = true
                        rerankingEnabled = false
                        multiQueryEnabled = false
                        ingestionSuccess = true
                        syncStatus = SyncStatus.SYNCED
                        lastIngestionCompleteTime = System.currentTimeMillis()
                        kotlinx.coroutines.delay(2000)
                        showIngestionPrompt = false
                        ingestionSuccess = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isIngesting = false
                        val errorMsg = e.message ?: "Unknown error occurred"
                        ingestionError = "Ingestion Pipeline Failed: $errorMsg"
                        AppLogger.e("ChatPanel", "Ingestion Pipeline Failed: $errorMsg", e)
                    }
                }
            }
        }
    }
    
    // Re-index prompt dialog
    if (showReindexDialog) {
        IngestionPromptDialog(
            onContinue = {
                if (!isIngesting && !reindexSuccess && reindexError == null) {
                    userWantsToReindex = true
                    showReindexPrompt = false
                    reindexError = null
                    reindexSuccess = false
                }
            },
            onCancel = {
                if (!isIngesting) {
                    userWantsToReindex = false
                    showReindexPrompt = false
                    isIngesting = false
                    reindexError = null
                    reindexSuccess = false
                }
            },
            isIngesting = isIngesting,
            errorMessage = reindexError,
            success = reindexSuccess,
            title = "Re-index Vault",
            message = when {
                reindexSuccess -> "Vault re-indexed successfully! All files are now up to date."
                reindexError != null -> reindexError
                isIngesting -> "Re-indexing vault... This may take a few minutes."
                else -> "Some files in this vault have changed. Re-indexing will update embeddings for changed files only."
            },
            theme = theme
        )
    }
    
    // Handle re-indexing after user confirms (simplified for Android)
    LaunchedEffect(userWantsToReindex) {
        if (userWantsToReindex && ragComponents != null && currentVaultPath != null) {
            userWantsToReindex = false
            isIngesting = true
            reindexError = null
            reindexSuccess = false
            
            ingestionScope.launch {
                try {
                    // Simplified re-indexing for Android - use indexer directly
                    val indexer = ragComponents.indexer
                    if (indexer is org.krypton.rag.VaultIndexService) {
                        indexer.indexVault(
                            rootPath = currentVaultPath,
                            existingFileHashes = emptyMap()
                        )
                    }
                    
                    withContext(Dispatchers.Main) {
                        isIngesting = false
                        ragEnabled = true
                        rerankingEnabled = false
                        multiQueryEnabled = false
                        reindexSuccess = true
                        syncStatus = SyncStatus.SYNCED
                        lastIngestionCompleteTime = System.currentTimeMillis()
                        kotlinx.coroutines.delay(2000)
                        showReindexPrompt = false
                        reindexSuccess = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isIngesting = false
                        val errorMsg = e.message ?: "Unknown error occurred"
                        reindexError = "Ingestion Pipeline Failed: $errorMsg"
                        AppLogger.e("ChatPanel", "Ingestion Pipeline Failed: $errorMsg", e)
                    }
                }
            }
        }
    }
}
