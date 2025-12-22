package org.krypton.krypton.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import krypton.composeapp.generated.resources.Res
import krypton.composeapp.generated.resources.close
import org.krypton.krypton.ObsidianThemeValues

@Composable
fun ChatPanel(
    chatService: ChatService,
    theme: ObsidianThemeValues,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var chatState by remember { mutableStateOf(ChatState()) }
    var inputText by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    
    // RAG toggle state (enabled by default)
    var ragEnabled by remember { mutableStateOf(true) }
    
    // Update RAG state if chatService is RagChatService
    val ragChatService = chatService as? org.krypton.krypton.chat.RagChatService
    LaunchedEffect(ragChatService) {
        ragChatService?.let {
            ragEnabled = it.isRagEnabled()
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(chatState.messages.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(modifier = modifier) {
        // Header bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = theme.SurfaceContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chat",
                        style = MaterialTheme.typography.titleSmall,
                        color = theme.TextPrimary
                    )
                    if (ragChatService != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "RAG",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (ragEnabled) theme.Accent else theme.TextSecondary
                            )
                            Switch(
                                checked = ragEnabled,
                                onCheckedChange = {
                                    ragEnabled = it
                                    ragChatService.setRagEnabled(it)
                                },
                                modifier = Modifier.size(32.dp, 18.dp),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = theme.Accent,
                                    checkedTrackColor = theme.Accent.copy(alpha = 0.5f),
                                    uncheckedThumbColor = theme.TextSecondary,
                                    uncheckedTrackColor = theme.SurfaceVariant
                                )
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(Res.drawable.close),
                        contentDescription = "Close",
                        modifier = Modifier.size(16.dp),
                        colorFilter = ColorFilter.tint(theme.TextSecondary)
                    )
                }
            }
        }

        // Error message
        chatState.error?.let { error ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Messages list
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(theme.PanelPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (chatState.messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Start a conversation with your AI assistant",
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.TextSecondary
                    )
                }
            } else {
                chatState.messages.forEach { message ->
                    ChatMessageItem(
                        message = message,
                        theme = theme,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // Loading indicator
                if (chatState.isLoading) {
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
        }

        // Input area
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = theme.SurfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                text = "Type your message...",
                                color = theme.TextSecondary
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = theme.TextPrimary,
                            unfocusedTextColor = theme.TextPrimary,
                            focusedBorderColor = theme.Accent,
                            unfocusedBorderColor = theme.Border,
                            disabledBorderColor = theme.Border,
                            disabledTextColor = theme.TextSecondary
                        ),
                        maxLines = 5,
                        enabled = !chatState.isLoading,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Button(
                        onClick = {
                            if (inputText.isNotBlank() && !chatState.isLoading) {
                                val message = inputText.trim()
                                inputText = ""
                                chatState = chatState.copy(
                                    isLoading = true,
                                    error = null
                                )
                                coroutineScope.launch {
                                    try {
                                        val updatedMessages = chatService.sendMessage(
                                            history = chatState.messages,
                                            userMessage = message
                                        )
                                        chatState = chatState.copy(
                                            messages = updatedMessages,
                                            isLoading = false,
                                            error = null
                                        )
                                    } catch (e: Exception) {
                                        chatState = chatState.copy(
                                            isLoading = false,
                                            error = if (e is ChatServiceException) {
                                                e.message
                                            } else {
                                                "Could not reach Ollama. Please make sure `ollama serve` is running."
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        enabled = inputText.isNotBlank() && !chatState.isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.Accent,
                            contentColor = theme.TextPrimary,
                            disabledContainerColor = theme.SurfaceVariant,
                            disabledContentColor = theme.TextSecondary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Send")
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
    
    Row(
        modifier = modifier,
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    color = if (isUser) {
                        theme.Accent.copy(alpha = 0.2f)
                    } else {
                        theme.SurfaceContainer
                    },
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
    }
}

