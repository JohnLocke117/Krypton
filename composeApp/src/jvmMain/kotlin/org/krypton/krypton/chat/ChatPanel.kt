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
import krypton.composeapp.generated.resources.close
import krypton.composeapp.generated.resources.database_search
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
import io.ktor.client.engine.cio.CIO
import androidx.compose.foundation.shape.CircleShape
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
    val koin = remember { GlobalContext.get() }
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
    
    // RAG activation manager
    val ragActivationManager: RagActivationManager? = remember(extendedRagComponents) {
        extendedRagComponents?.let {
            RagActivationManager(
                healthService = it.healthService,
                vaultSyncService = it.vaultSyncService,
                indexer = it.base.indexer,
                vaultMetadataService = it.vaultMetadataService
            )
        }
    }
    
    // Sync status state
    var syncStatus by remember { mutableStateOf<SyncStatus?>(null) }
    
    // Ingestion prompt dialog state
    var showIngestionPrompt by remember { mutableStateOf(false) }
    var isIngesting by remember { mutableStateOf(false) }
    
    // Get current vault path
    val currentVaultPath = editorStateHolder?.currentDirectory?.value?.toString()
    
    // Check sync status periodically and on vault change
    LaunchedEffect(currentVaultPath, extendedRagComponents) {
        if (extendedRagComponents != null && currentVaultPath != null) {
            try {
                val status = extendedRagComponents.vaultSyncService.checkSyncStatus(currentVaultPath)
                syncStatus = status
            } catch (e: Exception) {
                syncStatus = SyncStatus.UNAVAILABLE
            }
        } else {
            syncStatus = null
        }
        
        // Check periodically (every 30 seconds)
        while (true) {
            kotlinx.coroutines.delay(30_000)
            if (extendedRagComponents != null && currentVaultPath != null) {
                try {
                    val status = extendedRagComponents.vaultSyncService.checkSyncStatus(currentVaultPath)
                    syncStatus = status
                } catch (e: Exception) {
                    syncStatus = SyncStatus.UNAVAILABLE
                }
            }
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
                            // RAG icon
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable(enabled = syncStatus != SyncStatus.UNAVAILABLE) {
                                        if (ragEnabled) {
                                            // Disable RAG
                                            ragEnabled = false
                                            ragChatService.setRagEnabled(false)
                                        } else {
                                            // Enable RAG - use activation manager
                                            coroutineScope.launch {
                                                if (ragActivationManager != null && currentVaultPath != null) {
                                                    // Check if ingestion is needed first
                                                    val status = extendedRagComponents?.vaultSyncService?.checkSyncStatus(currentVaultPath)
                                                    if (status == SyncStatus.NOT_INDEXED) {
                                                        // Show prompt
                                                        showIngestionPrompt = true
                                                    } else {
                                                        // No prompt needed, activate directly
                                                        // Use ingestionScope for long-running operations
                                                        ingestionScope.launch {
                                                            val result = ragActivationManager.activateRag(
                                                                vaultPath = currentVaultPath,
                                                                onIngestionNeeded = { false }, // Already checked
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
                                                                        ragChatService.setRagEnabled(true)
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
                                                                        rebuildStatus = UiStatus.Error(
                                                                            "Failed to activate RAG. Please check ChromaDB connection.",
                                                                            recoverable = true
                                                                        )
                                                                        isIngesting = false
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    // Fallback: just enable RAG
                                                    ragEnabled = true
                                                    ragChatService.setRagEnabled(true)
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
                            
                            // Status indicator (colored circle)
                            syncStatus?.let { status ->
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
                            
                            // Database search icon (re-ingest)
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable(
                                        enabled = ragEnabled && 
                                                 syncStatus != SyncStatus.SYNCED && 
                                                 syncStatus != SyncStatus.UNAVAILABLE &&
                                                 rebuildStatus !is UiStatus.Loading &&
                                                 !isIngesting,
                                        onClick = {
                                            if (ragEnabled && currentVaultPath != null && extendedRagComponents != null) {
                                                // Use ingestionScope for long-running operation
                                                ingestionScope.launch {
                                                    try {
                                                        withContext(Dispatchers.Main) {
                                                        rebuildStatus = UiStatus.Loading
                                                            isIngesting = true
                                                        }
                                                        
                                                        val filesToReindex = extendedRagComponents.vaultSyncService.getFilesToReindex(currentVaultPath)
                                                        if (filesToReindex.isNotEmpty()) {
                                                            extendedRagComponents.base.indexer.indexModifiedFiles(filesToReindex, currentVaultPath)
                                                        } else {
                                                            extendedRagComponents.base.indexer.fullReindex(currentVaultPath)
                                                        }
                                                        
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
                                            !ragEnabled || 
                                            syncStatus == SyncStatus.SYNCED || 
                                            syncStatus == SyncStatus.UNAVAILABLE ||
                                            rebuildStatus is UiStatus.Loading ||
                                            isIngesting -> theme.TextSecondary
                                            else -> Color.White // White when enabled
                                        }
                                    )
                                )
                            }
                            
                            // Status indicator before dropdown (if not already shown)
                            if (syncStatus == null) {
                                Spacer(modifier = Modifier.size(8.dp))
                            }
                            
                            // RAG Pipeline Dropdown
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
                                        color = if (ragEnabled) theme.TextPrimary else theme.TextSecondary,
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
                        } else {
                            Spacer(modifier = Modifier.size(24.dp))
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
                                        
                                        // Send message (will update messages when response arrives)
                                        chatStateHolder.sendMessage(messageText)
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
    
    // Show dialog if prompt is requested OR if ingestion is in progress/complete
    val showDialog = showIngestionPrompt || isIngesting || ingestionSuccess || ingestionError != null
    
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
                                ragChatService?.setRagEnabled(true)
                                ingestionSuccess = true
                                // Set sync status to SYNCED since ingestion just completed successfully
                                syncStatus = SyncStatus.SYNCED
                                // Auto-close after 2 seconds
                                kotlinx.coroutines.delay(2000)
                                showIngestionPrompt = false
                                ingestionSuccess = false
                                // Re-check sync status after a brief delay to ensure it's accurate
                                kotlinx.coroutines.delay(500)
                                val newSyncStatus = extendedRagComponents?.vaultSyncService?.checkSyncStatus(currentVaultPath)
                                syncStatus = newSyncStatus ?: SyncStatus.SYNCED
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
                        ingestionError = "Ingestion failed: ${e.message}"
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

