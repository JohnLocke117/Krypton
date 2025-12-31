package org.krypton.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.krypton.CatppuccinMochaColors
import org.krypton.ObsidianThemeValues
import org.krypton.Settings
import org.krypton.chat.ChatRole
import org.krypton.ui.state.ChatStateHolder
import org.krypton.ui.state.EditorStateHolder

/**
 * Android implementation of ChatPanel.
 * 
 * Simplified version for Android that provides basic chat functionality
 * without the advanced RAG features available on desktop.
 */
@Composable
actual fun ChatPanel(
    chatStateHolder: ChatStateHolder,
    editorStateHolder: EditorStateHolder?,
    theme: ObsidianThemeValues,
    settings: Settings,
    modifier: Modifier
) {
    val messages by chatStateHolder.messages.collectAsState()
    val isLoading by chatStateHolder.isLoading.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CatppuccinMochaColors.Base)
    ) {
        // Messages list
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { message ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (message.role == ChatRole.USER) {
                            theme.Accent.copy(alpha = 0.2f)
                        } else {
                            theme.Surface
                        }
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.TextPrimary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            
            if (isLoading) {
                item {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        color = theme.Accent
                    )
                }
            }
        }
        
        Divider(color = theme.Border)
        
        // Input area
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = theme.BackgroundElevated
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...", color = theme.TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.TextPrimary,
                        unfocusedTextColor = theme.TextPrimary,
                        focusedBorderColor = theme.Accent,
                        unfocusedBorderColor = theme.Border
                    ),
                    singleLine = false,
                    maxLines = 4
                )
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isLoading) {
                            coroutineScope.launch {
                                chatStateHolder.sendMessage(inputText.trim())
                                inputText = ""
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

