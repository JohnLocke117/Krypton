package org.krypton.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.krypton.ObsidianThemeValues
import org.krypton.chat.ChatMessage
import org.krypton.chat.ChatRole

/**
 * Displays the list of chat messages with scrolling.
 */
@Composable
fun ChatMessageList(
    messages: List<ChatMessage>,
    isLoading: Boolean,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = theme.PanelPadding + 4.dp, vertical = theme.PanelPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        messages.forEachIndexed { index, message ->
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
}

/**
 * Displays a single chat message item.
 */
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

