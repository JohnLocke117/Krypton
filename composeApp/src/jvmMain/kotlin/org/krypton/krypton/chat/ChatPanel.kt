package org.krypton.krypton.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import org.krypton.krypton.ObsidianThemeValues
import org.krypton.krypton.CatppuccinMochaColors
import org.krypton.krypton.VectorBackend
import org.krypton.krypton.ui.state.UiStatus
import org.krypton.krypton.rag.RagComponents
import org.krypton.krypton.rag.SyncStatus
import org.krypton.krypton.rag.createExtendedRagComponents
import org.krypton.krypton.rag.ExtendedRagComponents
import org.krypton.krypton.rag.RagConfig
import org.krypton.krypton.config.RagDefaults
import org.krypton.krypton.chat.RagActivationManager
import org.krypton.krypton.chat.RagActivationResult
import org.krypton.krypton.chat.IngestionPromptDialog
import org.krypton.krypton.ui.state.EditorStateHolder
import org.koin.core.context.GlobalContext
import org.krypton.krypton.util.AppLogger
import org.krypton.krypton.util.SecretsLoader
import io.ktor.client.engine.cio.CIO
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPanel(
    chatStateHolder: org.krypton.krypton.ui.state.ChatStateHolder,
    editorStateHolder: EditorStateHolder? = null,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    val messages by chatStateHolder.messages.collectAsState()
    val isLoading by chatStateHolder.isLoading.collectAsState()
    val error by chatStateHolder.error.collectAsState()
    val chatStatus by chatStateHolder.status.collectAsState()
    
    var inputText by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
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
    val settingsRepository: org.krypton.krypton.data.repository.SettingsRepository = remember { koin.get() }
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
    
    // Check if chat service supports RAG
    val ragChatService = remember(chatStateHolder) {
        val service = chatStateHolder.chatService
        if (service is org.krypton.krypton.chat.RagChatService) {
            service
        } else {
            null
        }
    }
    
    // Get RAG components and extended services
    val ragComponents: RagComponents? = remember {
        try {
            koin.getOrNull<RagComponents>()
        } catch (e: Exception) {
            null
        }
    }
    
    // Get extended RAG components with sync services
    val extendedRagComponents: ExtendedRagComponents? = remember(ragComponents) {
        ragComponents?.let {
            try {
                val settingsRepository: org.krypton.krypton.data.repository.SettingsRepository = koin.get()
                val ragSettings = settingsRepository.settingsFlow.value.rag
                val httpEngine = CIO.create()
                
                val config = RagConfig(
                    vectorBackend = ragSettings.vectorBackend,
                    llamaBaseUrl = ragSettings.llamaBaseUrl,
                    embeddingBaseUrl = ragSettings.embeddingBaseUrl,
                    chromaBaseUrl = ragSettings.chromaBaseUrl,
                    chromaCollectionName = ragSettings.chromaCollectionName,
                    chromaTenant = ragSettings.chromaTenant,
                    chromaDatabase = ragSettings.chromaDatabase,
                    llamaModel = RagDefaults.DEFAULT_LLAMA_MODEL,
                    embeddingModel = RagDefaults.DEFAULT_EMBEDDING_MODEL
                )
                
                createExtendedRagComponents(
                    config = config,
                    notesRoot = null,
                    httpClientEngine = httpEngine
                )
            } catch (e: Exception) {
                null
            }
        }
    }
    
    // Re-index prompt dialog state
    var showReindexPrompt by remember { mutableStateOf(false) }
    
    // RAG activation manager
    val ragActivationManager: RagActivationManager? = remember(extendedRagComponents) {
        extendedRagComponents?.let {
            RagActivationManager(
                healthService = it.healthService,
                vaultSyncService = it.vaultSyncService,
                indexer = it.base.indexer,
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
            ragComponents?.ragService?.setRerankingEnabled(false)
            multiQueryEnabled = false
            AppLogger.d("ChatPanel", "Multi-Query disabled (ChromaDB unavailable)")
            rebuildStatus = UiStatus.Error(
                "ChromaDB is unavailable. RAG mode has been disabled. Chat has reverted to normal mode.",
                recoverable = true
            )
            System.err.println("✗ ChromaDB unavailable - RAG mode disabled")
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
                System.err.println("✗ RAG error - RAG mode disabled: ${errorStatus.message}")
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

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(modifier = modifier) {
        // Rebuild status messages
        rebuildStatus?.let { status ->
            when (status) {
                is UiStatus.Success -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Vector database rebuilt successfully",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                is UiStatus.Error -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = status.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { rebuildStatus = null },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
                is UiStatus.Loading -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = theme.Accent,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Rebuilding vector database...",
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.TextPrimary
                            )
                        }
                    }
                }
                else -> {}
            }
        }
        
        // Tavily error message (non-blocking)
        tavilyError?.let { errorMsg ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { tavilyError = null },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        }
        
        // Error message
        error?.let { errorMsg ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { chatStateHolder.clearError() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        }

        // Messages list
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = theme.PanelPadding + 4.dp, vertical = theme.PanelPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            displayMessages.forEachIndexed { index, message ->
                ChatMessageItem(
                    message = message,
                    theme = theme,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Add divider and spacing after assistant messages
                if (message.role == ChatRole.ASSISTANT) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(
                        modifier = Modifier.fillMaxWidth(),
                        color = theme.BorderVariant,
                        thickness = 1.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            
            // Loading indicator
            if (isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = theme.Accent,
                        strokeWidth = 2.dp
                    )
                }
            }
        }

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
                        // RAG icon (if RAG service is available)
                        if (ragChatService != null) {
                            // 1. RAG toggle icon (first)
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable(enabled = syncStatus != SyncStatus.UNAVAILABLE) {
                                        if (ragEnabled) {
                                            // Disable RAG
                                            ragEnabled = false
                                            // Disable reranking and multi-query when RAG is disabled
                                            rerankingEnabled = false
                                            ragComponents?.ragService?.setRerankingEnabled(false)
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
                                                                            ragComponents?.ragService?.setRerankingEnabled(false)
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
                                                                            // Log to console in red
                                                                            System.err.println("✗ $errorMsg")
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
                                                    ragComponents?.ragService?.setRerankingEnabled(false)
                                                    multiQueryEnabled = false
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
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
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable(enabled = ragEnabled) {
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
                                    },
                                contentAlignment = Alignment.Center
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
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable(enabled = ragEnabled) {
                                        if (ragEnabled) {
                                            val newValue = !rerankingEnabled
                                            rerankingEnabled = newValue
                                            // Update RagService reranking state
                                            ragComponents?.ragService?.setRerankingEnabled(newValue)
                                        }
                                    },
                                contentAlignment = Alignment.Center
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
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable(
                                        enabled = currentVaultPath != null && 
                                                 extendedRagComponents != null &&
                                                 syncStatus != SyncStatus.UNAVAILABLE &&
                                                 rebuildStatus !is UiStatus.Loading &&
                                                 !isIngesting,
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
                                                        
                                                        extendedRagComponents.base.indexer.fullReindex(
                                                            vaultPath = currentVaultPath,
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
                                    ),
                                contentAlignment = Alignment.Center
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
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clickable(enabled = tavilyAvailable) {
                                    if (tavilyAvailable) {
                                        webEnabled = !webEnabled
                                        tavilyError = null
                                        AppLogger.d("ChatPanel", "Web search ${if (webEnabled) "enabled" else "disabled"}")
                                    } else {
                                        // Show non-blocking error
                                        tavilyError = "Tavily API key not found. Please add TAVILLY_API_KEY to local.secrets.properties"
                                        AppLogger.w("ChatPanel", "Attempted to enable Tavily without API key")
                                    }
                                },
                            contentAlignment = Alignment.Center
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
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(
                                enabled = inputText.isNotBlank() && !isLoading,
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
                                        
                                        // Send message with current retrieval mode
                                        chatStateHolder.sendMessage(messageText, retrievalMode)
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
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
                                ragComponents?.ragService?.setRerankingEnabled(false)
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
                        // Also log to console in red
                        System.err.println("✗ Ingestion Pipeline Failed: $errorMsg")
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
                                ragComponents?.ragService?.setRerankingEnabled(false)
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
                        // Also log to console in red
                        System.err.println("✗ Ingestion Pipeline Failed: $errorMsg")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: ChatMessage,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == ChatRole.USER
    
    if (isUser) {
        // User message: displayed in a colored box, full width
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    color = theme.Accent.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp)
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary
            )
        }
    } else {
        // Assistant message: displayed directly without box, full width
        Text(
            text = message.content,
            style = MaterialTheme.typography.bodyMedium,
            color = theme.TextPrimary,
            modifier = modifier.fillMaxWidth()
        )
    }
}

