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
import org.koin.core.context.GlobalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPanel(
    chatStateHolder: org.krypton.krypton.ui.state.ChatStateHolder,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    val messages by chatStateHolder.messages.collectAsState()
    val isLoading by chatStateHolder.isLoading.collectAsState()
    val error by chatStateHolder.error.collectAsState()
    
    var inputText by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    
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
    var selectedBackend by remember { mutableStateOf(VectorBackend.SQLITE_BRUTE_FORCE) }
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
    
    // Get RAG components for rebuild functionality
    val koin = remember { GlobalContext.get() }
    val ragComponents: RagComponents? = remember {
        try {
            koin.getOrNull<RagComponents>()
        } catch (e: Exception) {
            null
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
            VectorBackend.SQLITE_BRUTE_FORCE -> "SQLite"
            VectorBackend.SQLITE_VECTOR_EXTENSION -> "ChromaDB"
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
                                    .clickable {
                                        ragEnabled = !ragEnabled
                                        ragChatService.setRagEnabled(ragEnabled)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(Res.drawable.rag),
                                    contentDescription = "RAG",
                                    modifier = Modifier.size(20.dp),
                                    colorFilter = ColorFilter.tint(
                                        if (ragEnabled) theme.Accent else theme.TextSecondary
                                    )
                                )
                            }
                            
                            // Database search icon
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable(
                                        enabled = ragEnabled && rebuildStatus !is UiStatus.Loading,
                                        onClick = {
                                            if (ragEnabled && rebuildStatus !is UiStatus.Loading) {
                                                coroutineScope.launch {
                                                    try {
                                                        rebuildStatus = UiStatus.Loading
                                                        ragComponents?.indexer?.fullReindex()
                                                        rebuildStatus = UiStatus.Success
                                                    } catch (e: Exception) {
                                                        rebuildStatus = UiStatus.Error(
                                                            e.message ?: "Failed to rebuild vector database",
                                                            recoverable = true
                                                        )
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
                                        if (ragEnabled && rebuildStatus !is UiStatus.Loading) {
                                            theme.Accent
                                        } else {
                                            theme.TextSecondary
                                        }
                                    )
                                )
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
                                                text = "SQLite",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = theme.TextPrimary
                                            )
                                        },
                                        onClick = {
                                            selectedBackend = VectorBackend.SQLITE_BRUTE_FORCE
                                            dropdownExpanded = false
                                        },
                                        colors = MenuDefaults.itemColors(
                                            textColor = theme.TextPrimary
                                        )
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "ChromaDB",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = theme.TextPrimary
                                            )
                                        },
                                        onClick = {
                                            selectedBackend = VectorBackend.SQLITE_VECTOR_EXTENSION
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

