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
    chatStateHolder: org.krypton.krypton.ui.state.ChatStateHolder,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    val messages by chatStateHolder.messages.collectAsState()
    val isLoading by chatStateHolder.isLoading.collectAsState()
    val error by chatStateHolder.error.collectAsState()
    
    var inputText by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    
    // RAG toggle state (enabled by default)
    var ragEnabled by remember { mutableStateOf(true) }
    
    // Check if chat service supports RAG
    val ragChatService = remember(chatStateHolder) {
        val service = chatStateHolder.chatService
        if (service is org.krypton.krypton.chat.RagChatService) {
            service
        } else {
            null
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(modifier = modifier) {
        // RAG toggle (moved from header to content area)
        if (ragChatService != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = org.krypton.krypton.CatppuccinMochaColors.Crust
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                .padding(theme.PanelPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty()) {
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
                messages.forEach { message ->
                    ChatMessageItem(
                        message = message,
                        theme = theme,
                        modifier = Modifier.fillMaxWidth()
                    )
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
        }

        // Input area
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = org.krypton.krypton.CatppuccinMochaColors.Crust
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
                        enabled = !isLoading,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Button(
                        onClick = {
                            if (inputText.isNotBlank() && !isLoading) {
                                val message = inputText.trim()
                                inputText = ""
                                chatStateHolder.sendMessage(message)
                            }
                        },
                        enabled = inputText.isNotBlank() && !isLoading,
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
                        org.krypton.krypton.CatppuccinMochaColors.Crust
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

