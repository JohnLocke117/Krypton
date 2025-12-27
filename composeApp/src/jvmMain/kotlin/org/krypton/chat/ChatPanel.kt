package org.krypton.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import krypton.composeapp.generated.resources.Res
import krypton.composeapp.generated.resources.arrow_split
import krypton.composeapp.generated.resources.close
import krypton.composeapp.generated.resources.database_search
import krypton.composeapp.generated.resources.globe
import krypton.composeapp.generated.resources.leaderboard
import krypton.composeapp.generated.resources.rag
import krypton.composeapp.generated.resources.send
import org.krypton.ObsidianThemeValues
import org.krypton.CatppuccinMochaColors
import org.krypton.VectorBackend
import org.krypton.ui.state.UiStatus
import org.krypton.rag.RagComponents
import org.krypton.rag.SyncStatus
import org.krypton.rag.ExtendedRagComponents
import org.krypton.rag.RagConfig
import org.krypton.chat.RagActivationManager
import org.krypton.chat.RagActivationResult
import org.krypton.chat.IngestionPromptDialog
import org.krypton.ui.state.EditorStateHolder
import org.koin.core.context.GlobalContext
import org.krypton.util.AppLogger
import org.krypton.util.SecretsLoader
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import org.krypton.ui.AppIconWithTooltip
import org.krypton.ui.TooltipPosition
import org.krypton.chat.ui.ChatMessageList
import org.krypton.chat.ui.ChatStatusBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPanel(
    chatStateHolder: org.krypton.ui.state.ChatStateHolder,
    editorStateHolder: EditorStateHolder? = null,
    theme: ObsidianThemeValues,
    settings: org.krypton.Settings,
    modifier: Modifier = Modifier
) {
    val messages by chatStateHolder.messages.collectAsState()
    val isLoading by chatStateHolder.isLoading.collectAsState()
    val chatStatus by chatStateHolder.status.collectAsState()
    
    // Extract error message from status
    val error = (chatStatus as? UiStatus.Error)?.message
    
    var inputText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    // Separate scope for long-running operations (not tied to Compose lifecycle)
    val ingestionScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    
    // Optimistic user message (shown immediately when sent)
    var optimisticUserMessage by remember { mutableStateOf<ChatMessage?>(null) }
    
    // Combine actual messages with optimistic message for display
    val displayMessages = remember(messages, optimisticUserMessage) {
        if (optimisticUserMessage != null && messages.isEmpty()) {
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
    
    // Tavily availability check - use SecretsLoader to check for API key
    // The improved SecretsLoader now searches multiple paths to find the file
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
    val settingsRepository: org.krypton.data.repository.SettingsRepository = remember { koin.get() }
    var multiQueryEnabled by remember { 
        mutableStateOf(false) 
    }
    
    // Reranking toggle state (disabled by default)
    var rerankingEnabled by remember { mutableStateOf(false) }
    
    // Error state for Tavily toggle attempts without API key
    var tavilyError by remember { mutableStateOf<String?>(null) }
    
    // RAG pipeline selection state
    var selectedBackend by remember { mutableStateOf(VectorBackend.CHROMADB) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    
    // Rebuild status state
    var rebuildStatus by remember { mutableStateOf<UiStatus?>(null) }
    
    // Get RAG components and extended services
    val ragComponents: RagComponents? = remember {
        try {
            koin.getOrNull<RagComponents>()
        } catch (e: Exception) {
            null
        }
    }
    
    // Get extended RAG components with sync services from DI
    val extendedRagComponents: ExtendedRagComponents? = remember {
        try {
            koin.getOrNull<ExtendedRagComponents>()
        } catch (e: Exception) {
            null
        }
    }
    
    // RAG is available if we have extended RAG components (which includes base components)
    val ragAvailable = extendedRagComponents != null || ragComponents != null
    
    // Re-index prompt dialog state
    var showReindexPrompt by remember { mutableStateOf(false) }
    
    // RAG activation manager
    val ragActivationManager: RagActivationManager? = remember(extendedRagComponents) {
        extendedRagComponents?.let {
            RagActivationManager(
                healthService = it.healthService,
                vaultSyncService = it.vaultSyncService,
                indexer = it.base.indexer as org.krypton.rag.Indexer,
                vaultMetadataService = it.vaultMetadataService,
                vectorStore = it.base.vectorStore
            )
        }
    }
    
    // Sync status state
    var syncStatus by remember { mutableStateOf<SyncStatus?>(null) }
    
    // Track when ingestion last completed to prevent immediate re-check
    var lastIngestionCompleteTime by remember { mutableStateOf<Long?>(null) }
    
    // Ingestion prompt dialog state
    var showIngestionPrompt by remember { mutableStateOf(false) }
    var isIngesting by remember { mutableStateOf(false) }
    
    // Get current vault path
    val currentVaultPath = editorStateHolder?.currentDirectory?.value?.toString()
    
    // Monitor sync status and disable RAG if ChromaDB becomes unavailable
    LaunchedEffect(syncStatus, ragEnabled) {
        if (syncStatus == SyncStatus.UNAVAILABLE && ragEnabled) {
            // ChromaDB became unavailable while RAG is enabled - disable RAG and revert to normal chat
            AppLogger.w("ChatPanel", "ChromaDB unavailable, disabling RAG mode")
            ragEnabled = false
            // Disable reranking and multi-query when RAG is disabled
            rerankingEnabled = false
            // Reranking is controlled via RagQueryOptions, not a service method
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
            // Check if error is RAG-related (ChromaDB, vector store, embedding, RAG, etc.)
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
                // RAG-related error occurred - disable RAG and revert to normal chat
                AppLogger.w("ChatPanel", "RAG error detected, disabling RAG mode: ${errorStatus.message}")
                ragEnabled = false
                syncStatus = SyncStatus.UNAVAILABLE // Set status to red
                rebuildStatus = UiStatus.Error(
                    "RAG error: ${errorStatus.message}. RAG mode has been disabled. Chat has reverted to normal mode.",
                    recoverable = true
                )
                AppLogger.e("ChatPanel", "RAG error - RAG mode disabled: ${errorStatus.message}")
            }
        }
    }
    
    // Check sync status on vault change and watch for file system events
    LaunchedEffect(currentVaultPath, extendedRagComponents, ragEnabled) {
        if (extendedRagComponents != null && currentVaultPath != null && ragEnabled) {
            // Initial sync status check
            try {
                val status = extendedRagComponents.vaultSyncService.checkSyncStatus(currentVaultPath)
                syncStatus = status
            } catch (e: Exception) {
                syncStatus = SyncStatus.UNAVAILABLE
            }
            
            // Watch for file system events
            val watcher = extendedRagComponents.vaultWatcher
            val events = watcher.watch(currentVaultPath)
            
            events.collect { event ->
                try {
                    // On file system event, check sync status
                    val status = extendedRagComponents.vaultSyncService.checkSyncStatus(currentVaultPath)
                    syncStatus = status
                    AppLogger.d("ChatPanel", "File system event: ${event.type} for ${event.relativePath}, sync status: $status")
                } catch (e: Exception) {
                    AppLogger.w("ChatPanel", "Error checking sync status after file event: ${e.message}")
                    syncStatus = SyncStatus.UNAVAILABLE
                }
            }
        } else {
            syncStatus = null
            // Stop watcher if RAG is disabled or vault changes
            extendedRagComponents?.vaultWatcher?.stop()
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
        }
    }


    Column(modifier = modifier) {
        // Status bar with error messages and rebuild status
        ChatStatusBar(
            rebuildStatus = rebuildStatus,
            tavilyError = tavilyError,
            chatError = error,
            onDismissRebuildStatus = { rebuildStatus = null },
            onDismissTavilyError = { tavilyError = null },
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
                // Part 1: Text area
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
                    shape = RoundedCornerShape(8.dp)
                )
                
                // Part 2: Icon bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left side: RAG controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // RAG icon (if RAG components are available)
                        if (ragAvailable) {
                            // 1. RAG toggle icon (first)
                            AppIconWithTooltip(
                                tooltip = "RAG",
                                modifier = Modifier.size(24.dp),
                                enabled = syncStatus != SyncStatus.UNAVAILABLE,
                                position = TooltipPosition.ABOVE,
                                onClick = {
                                    if (ragEnabled) {
                                        // Disable RAG
                                        ragEnabled = false
                                        // Disable reranking and multi-query when RAG is disabled
                                        rerankingEnabled = false
                                        // Reranking is controlled via RagQueryOptions, not a service method
                                        multiQueryEnabled = false
                                        AppLogger.d("ChatPanel", "Multi-Query disabled (RAG disabled)")
                                    } else {
                                        // Enable RAG - use activation manager
                                        coroutineScope.launch {
                                            if (ragActivationManager != null && currentVaultPath != null) {
                                                // First check if collection exists and has data
                                                val hasVaultData = extendedRagComponents?.base?.vectorStore?.hasVaultData(currentVaultPath) ?: false
                                                
                                                if (!hasVaultData) {
                                                    // Collection doesn't exist or has no data - show prompt
                                                    showIngestionPrompt = true
                                                } else {
                                                    // Collection exists and has data - check sync status
                                                    val status = extendedRagComponents?.vaultSyncService?.checkSyncStatus(currentVaultPath)
                                                    if (status == SyncStatus.OUT_OF_SYNC) {
                                                        // Show re-index prompt
                                                        showReindexPrompt = true
                                                    } else {
                                                        // No prompt needed, activate directly
                                                        // Use ingestionScope for long-running operations
                                                        ingestionScope.launch {
                                                            val result = ragActivationManager.activateRag(
                                                                vaultPath = currentVaultPath,
                                                                onIngestionNeeded = { false }, // Already checked
                                                                onReindexNeeded = { false }, // Not needed
                                                                onIngestionProgress = { filePath, progress ->
                                                                    withContext(Dispatchers.Main) {
                                                                        isIngesting = true
                                                                    }
                                                                }
                                                            )
                                                        
                                                            // Handle result
                                                            when (result) {
                                                                RagActivationResult.ENABLED -> {
                                                                    // Update UI state on main thread
                                                                    withContext(Dispatchers.Main) {
                                                                        ragEnabled = true
                                                                        // Keep reranking and multi-query disabled by default
                                                                        rerankingEnabled = false
                                                                        // Reranking is controlled via RagQueryOptions, not a service method
                                                                        multiQueryEnabled = false
                                                                        isIngesting = false
                                                                    }
                                                                    // Check sync status on IO thread, then update UI
                                                                    val newSyncStatus = extendedRagComponents?.vaultSyncService?.checkSyncStatus(currentVaultPath)
                                                                    withContext(Dispatchers.Main) {
                                                                        syncStatus = newSyncStatus
                                                                    }
                                                                }
                                                                RagActivationResult.CANCELLED -> {
                                                                    withContext(Dispatchers.Main) {
                                                                        isIngesting = false
                                                                    }
                                                                }
                                                                RagActivationResult.ERROR -> {
                                                                    withContext(Dispatchers.Main) {
                                                                        val errorMsg = "Failed to activate RAG. Please check ChromaDB connection."
                                                                        rebuildStatus = UiStatus.Error(
                                                                            errorMsg,
                                                                            recoverable = true
                                                                        )
                                                                        isIngesting = false
                                                                        AppLogger.e("ChatPanel", errorMsg)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
                                                // Fallback: just enable RAG
                                                ragEnabled = true
                                                // Keep reranking and multi-query disabled by default
                                                rerankingEnabled = false
                                                // Reranking is controlled via RagQueryOptions, not a service method
                                                multiQueryEnabled = false
                                            }
                                        }
                                    }
                                }
                            ) {
                                Image(
                                    painter = painterResource(Res.drawable.rag),
                                    contentDescription = "RAG",
                                    modifier = Modifier.size(20.dp),
                                    colorFilter = ColorFilter.tint(
                                        when {
                                            syncStatus == SyncStatus.UNAVAILABLE -> theme.TextTertiary
                                            ragEnabled -> theme.Accent
                                            else -> theme.TextSecondary
                                        }
                                    )
                                )
                            }
                            
                            // 2. Circular sync indicator (second) - outlined red circle when RAG off
                            syncStatus?.let { status ->
                                if (ragEnabled) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when (status) {
                                                    SyncStatus.SYNCED -> Color(0xFF4CAF50) // Green
                                                    SyncStatus.OUT_OF_SYNC, SyncStatus.NOT_INDEXED -> Color(0xFFFFC107) // Yellow
                                                    SyncStatus.UNAVAILABLE -> Color(0xFFF44336) // Red
                                                }
                                            )
                                    )
                                }
                            }
                            if (!ragEnabled) {
                                // Outlined red circle when RAG is off
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = 1.5.dp,
                                            color = Color(0xFFF44336), // Red
                                            shape = CircleShape
                                        )
                                )
                            }
                            
                            // 3. DB dropdown (third) - reduced opacity when RAG off
                            ExposedDropdownMenuBox(
                                expanded = dropdownExpanded && ragEnabled,
                                onExpandedChange = { if (ragEnabled) dropdownExpanded = it }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .height(24.dp)
                                        .menuAnchor()
                                        .clickable(enabled = ragEnabled) {
                                            if (ragEnabled) {
                                                dropdownExpanded = !dropdownExpanded
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = getBackendDisplayName(selectedBackend),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (ragEnabled) theme.TextPrimary else theme.TextSecondary.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                                ExposedDropdownMenu(
                                    expanded = dropdownExpanded && ragEnabled,
                                    onDismissRequest = { dropdownExpanded = false },
                                    modifier = Modifier
                                        .background(theme.BackgroundElevated)
                                        .widthIn(max = 150.dp)
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "ChromaDB",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = theme.TextPrimary
                                            )
                                        },
                                        onClick = {
                                            selectedBackend = VectorBackend.CHROMADB
                                            dropdownExpanded = false
                                        },
                                        colors = MenuDefaults.itemColors(
                                            textColor = theme.TextPrimary
                                        )
                                    )
                                }
                            }
                            
                            // 4. Multi-query option (fourth) - visible but disabled when RAG off
                            AppIconWithTooltip(
                                tooltip = "Multi-Query",
                                modifier = Modifier.size(24.dp),
                                enabled = ragEnabled,
                                position = TooltipPosition.ABOVE,
                                onClick = {
                                    if (ragEnabled) {
                                        val newValue = !multiQueryEnabled
                                        multiQueryEnabled = newValue
                                        // Log the state change
                                        AppLogger.d("ChatPanel", "Multi-Query ${if (newValue) "enabled" else "disabled"}")
                                        // Update settings
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
                                }
                            ) {
                                Image(
                                    painter = painterResource(Res.drawable.arrow_split),
                                    contentDescription = "Multi-Query",
                                    modifier = Modifier.size(20.dp),
                                    colorFilter = ColorFilter.tint(
                                        if (ragEnabled && multiQueryEnabled) {
                                            theme.Accent
                                        } else {
                                            theme.TextSecondary.copy(alpha = if (ragEnabled) 1f else 0.5f)
                                        }
                                    )
                                )
                            }
                            
                            // 5. Reranking toggle (fifth) - visible but disabled when RAG off
                            AppIconWithTooltip(
                                tooltip = "Reranking",
                                modifier = Modifier.size(24.dp),
                                enabled = ragEnabled,
                                position = TooltipPosition.ABOVE,
                                onClick = {
                                    if (ragEnabled) {
                                        val newValue = !rerankingEnabled
                                        rerankingEnabled = newValue
                                        // Update RagService reranking state
                                        // Reranking is controlled via RagQueryOptions, not a service method
                                    }
                                }
                            ) {
                                Image(
                                    painter = painterResource(Res.drawable.leaderboard),
                                    contentDescription = "Reranking",
                                    modifier = Modifier.size(20.dp),
                                    colorFilter = ColorFilter.tint(
                                        if (ragEnabled && rerankingEnabled) {
                                            theme.Accent
                                        } else {
                                            theme.TextSecondary.copy(alpha = if (ragEnabled) 1f else 0.5f)
                                        }
                                    )
                                )
                            }
                            
                            // 6. Manual re-index (sixth) - always available
                            AppIconWithTooltip(
                                tooltip = "Rebuild Vector Database",
                                modifier = Modifier.size(24.dp),
                                enabled = currentVaultPath != null && 
                                         extendedRagComponents != null &&
                                         syncStatus != SyncStatus.UNAVAILABLE &&
                                         rebuildStatus !is UiStatus.Loading &&
                                         !isIngesting,
                                position = TooltipPosition.ABOVE,
                                onClick = {
                                    if (currentVaultPath != null && extendedRagComponents != null) {
                                        // Use ingestionScope for long-running operation
                                        ingestionScope.launch {
                                            try {
                                                withContext(Dispatchers.Main) {
                                                    rebuildStatus = UiStatus.Loading
                                                    isIngesting = true
                                                }
                                                
                                                // Get existing metadata to enable incremental indexing
                                                val existingMetadata = extendedRagComponents.vaultMetadataService.getVaultMetadata(currentVaultPath)
                                                val existingHashes = existingMetadata?.indexedFileHashes ?: emptyMap()
                                                
                                                extendedRagComponents.base.indexer.indexVault(
                                                    rootPath = currentVaultPath,
                                                    existingFileHashes = existingHashes
                                                )
                                                
                                                // Update sync status on IO thread, then update UI
                                                val newSyncStatus = extendedRagComponents.vaultSyncService.checkSyncStatus(currentVaultPath)
                                                withContext(Dispatchers.Main) {
                                                    syncStatus = newSyncStatus
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
                                }
                            ) {
                                Image(
                                    painter = painterResource(Res.drawable.database_search),
                                    contentDescription = "Rebuild Vector Database",
                                    modifier = Modifier.size(20.dp),
                                    colorFilter = ColorFilter.tint(
                                        when {
                                            syncStatus == SyncStatus.UNAVAILABLE ||
                                            rebuildStatus is UiStatus.Loading ||
                                            isIngesting -> theme.TextSecondary
                                            else -> theme.TextPrimary
                                        }
                                    )
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.size(24.dp))
                        }
                        
                        // Globe icon (Tavily web search) - after RAG controls
                        AppIconWithTooltip(
                            tooltip = "Web Search",
                            modifier = Modifier.size(24.dp),
                            enabled = tavilyAvailable,
                            position = TooltipPosition.ABOVE,
                            onClick = {
                                if (tavilyAvailable) {
                                    webEnabled = !webEnabled
                                    tavilyError = null
                                    AppLogger.d("ChatPanel", "Web search ${if (webEnabled) "enabled" else "disabled"}")
                                } else {
                                    // Show non-blocking error
                                    tavilyError = "Tavily API key not found. Please add TAVILLY_API_KEY to local.secrets.properties"
                                    AppLogger.w("ChatPanel", "Attempted to enable Tavily without API key")
                                }
                            }
                        ) {
                            Image(
                                painter = painterResource(Res.drawable.globe),
                                contentDescription = "Web Search (Tavily)",
                                modifier = Modifier.size(20.dp),
                                colorFilter = ColorFilter.tint(
                                    when {
                                        !tavilyAvailable -> theme.TextTertiary
                                        webEnabled -> theme.Accent
                                        else -> theme.TextSecondary
                                    }
                                )
                            )
                        }
                    }
                    
                    // Right side: Send icon
                    AppIconWithTooltip(
                        tooltip = "Send",
                        modifier = Modifier.size(24.dp),
                        enabled = inputText.isNotBlank() && !isLoading,
                        position = TooltipPosition.ABOVE,
                        onClick = {
                            if (inputText.isNotBlank() && !isLoading) {
                                val messageText = inputText.trim()
                                inputText = ""
                                
                                // Create optimistic user message immediately
                                val optimisticMessage = ChatMessage(
                                    id = "optimistic_${System.currentTimeMillis()}",
                                    role = ChatRole.USER,
                                    content = messageText,
                                    timestamp = System.currentTimeMillis()
                                )
                                optimisticUserMessage = optimisticMessage
                                
                                // Send message with current retrieval mode and vault path
                                chatStateHolder.sendMessage(messageText, retrievalMode, currentVaultPath)
                            }
                        }
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.send),
                            contentDescription = "Send",
                            modifier = Modifier.size(20.dp),
                            colorFilter = ColorFilter.tint(
                                if (inputText.isNotBlank() && !isLoading) {
                                    theme.Accent
                                } else {
                                    theme.TextSecondary
                                }
                            )
                        )
                    }
                }
            }
        }
    }
    
    // Ingestion prompt dialog state - keep dialog open during ingestion
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
    
    // Handle ingestion after user confirms
    LaunchedEffect(userWantsToIngest) {
        if (userWantsToIngest && ragActivationManager != null && currentVaultPath != null) {
            userWantsToIngest = false
            isIngesting = true
            ingestionError = null
            ingestionSuccess = false
            
            // Use ingestionScope for long-running operation
            ingestionScope.launch {
                try {
                    val result = ragActivationManager.activateRag(
                        vaultPath = currentVaultPath,
                        onIngestionNeeded = { true }, // User already confirmed
                        onReindexNeeded = { true }, // Not used in this flow
                        onIngestionProgress = { filePath, progress ->
                            // Update UI with progress
                            withContext(Dispatchers.Main) {
                                isIngesting = true
                                // Could show progress message here if needed
                            }
                        }
                    )
                    
                    // Update UI state on main thread
                    withContext(Dispatchers.Main) {
                        isIngesting = false
                        when (result) {
                            RagActivationResult.ENABLED -> {
                                ragEnabled = true
                                // Keep reranking and multi-query disabled by default
                                rerankingEnabled = false
                                // Reranking is controlled via RagQueryOptions, not a service method
                                multiQueryEnabled = false
                                ingestionSuccess = true
                                // Set sync status to SYNCED since ingestion just completed successfully
                                // We trust that ingestion completed successfully, so vault is synced
                                syncStatus = SyncStatus.SYNCED
                                lastIngestionCompleteTime = System.currentTimeMillis()
                                // Auto-close after 2 seconds
                                kotlinx.coroutines.delay(2000)
                                showIngestionPrompt = false
                                ingestionSuccess = false
                                // Re-check sync status after a longer delay to ensure metadata is fully persisted
                                // Use a longer delay (2 seconds) to allow ChromaDB to fully persist metadata
                                kotlinx.coroutines.delay(2000)
                                val newSyncStatus = extendedRagComponents?.vaultSyncService?.checkSyncStatus(currentVaultPath)
                                // Only update if the check confirms SYNCED, otherwise keep SYNCED (we just completed ingestion)
                                if (newSyncStatus == SyncStatus.SYNCED) {
                                    syncStatus = SyncStatus.SYNCED
                                } else if (newSyncStatus == SyncStatus.UNAVAILABLE) {
                                    // Only update to UNAVAILABLE if ChromaDB is actually unavailable
                                    syncStatus = SyncStatus.UNAVAILABLE
                                }
                                // Otherwise, keep SYNCED since we just completed ingestion successfully
                            }
                            RagActivationResult.CANCELLED -> {
                                ingestionError = "Ingestion was cancelled"
                            }
                            RagActivationResult.ERROR -> {
                                ingestionError = "Failed to index vault. Please check ChromaDB connection."
                            }
                        }
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
    
    // Handle re-indexing after user confirms
    LaunchedEffect(userWantsToReindex) {
        if (userWantsToReindex && ragActivationManager != null && currentVaultPath != null) {
            userWantsToReindex = false
            isIngesting = true
            reindexError = null
            reindexSuccess = false
            
            // Use ingestionScope for long-running operation
            ingestionScope.launch {
                try {
                    val result = ragActivationManager.activateRag(
                        vaultPath = currentVaultPath,
                        onIngestionNeeded = { false }, // Not needed
                        onReindexNeeded = { true }, // User already confirmed
                        onIngestionProgress = { filePath, progress ->
                            // Update UI with progress
                            withContext(Dispatchers.Main) {
                                isIngesting = true
                            }
                        }
                    )
                    
                    // Update UI state on main thread
                    withContext(Dispatchers.Main) {
                        isIngesting = false
                        when (result) {
                            RagActivationResult.ENABLED -> {
                                ragEnabled = true
                                // Keep reranking and multi-query disabled by default
                                rerankingEnabled = false
                                // Reranking is controlled via RagQueryOptions, not a service method
                                multiQueryEnabled = false
                                reindexSuccess = true
                                // Set sync status to SYNCED since re-indexing just completed successfully
                                // We trust that re-indexing completed successfully, so vault is synced
                                syncStatus = SyncStatus.SYNCED
                                lastIngestionCompleteTime = System.currentTimeMillis()
                                // Auto-close after 2 seconds
                                kotlinx.coroutines.delay(2000)
                                showReindexPrompt = false
                                reindexSuccess = false
                                // Re-check sync status after a longer delay to ensure metadata is fully persisted
                                // Use a longer delay (2 seconds) to allow ChromaDB to fully persist metadata
                                kotlinx.coroutines.delay(2000)
                                val newSyncStatus = extendedRagComponents?.vaultSyncService?.checkSyncStatus(currentVaultPath)
                                // Only update if the check confirms SYNCED, otherwise keep SYNCED (we just completed re-indexing)
                                if (newSyncStatus == SyncStatus.SYNCED) {
                                    syncStatus = SyncStatus.SYNCED
                                } else if (newSyncStatus == SyncStatus.UNAVAILABLE) {
                                    // Only update to UNAVAILABLE if ChromaDB is actually unavailable
                                    syncStatus = SyncStatus.UNAVAILABLE
                                }
                                // Otherwise, keep SYNCED since we just completed re-indexing successfully
                            }
                            RagActivationResult.CANCELLED -> {
                                reindexError = "Re-indexing was cancelled"
                            }
                            RagActivationResult.ERROR -> {
                                reindexError = "Failed to re-index vault. Please check ChromaDB connection."
                            }
                        }
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


