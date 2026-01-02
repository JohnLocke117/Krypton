package org.krypton.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.krypton.chat.conversation.ConversationId
import org.krypton.chat.conversation.ConversationSummary
import org.krypton.ObsidianThemeValues
import org.krypton.chat.ui.DeleteIcon

/**
 * Composable for displaying a list of conversations.
 * 
 * @param conversations List of conversation summaries to display
 * @param selectedId Currently selected conversation ID
 * @param onSelect Callback when a conversation is selected
 * @param onDelete Callback when a conversation is deleted
 * @param theme Theme values for styling
 */
@Composable
fun ConversationList(
    conversations: List<ConversationSummary>,
    selectedId: ConversationId?,
    onSelect: (ConversationId) -> Unit,
    onDelete: (ConversationId) -> Unit,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    if (conversations.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No conversations yet",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextSecondary
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(conversations, key = { it.id.value }) { conversation ->
                ConversationItem(
                    conversation = conversation,
                    isSelected = conversation.id == selectedId,
                    onSelect = { onSelect(conversation.id) },
                    onDelete = { onDelete(conversation.id) },
                    theme = theme
                )
            }
        }
    }
}

@Composable
private fun ConversationItem(
    conversation: ConversationSummary,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    theme: ObsidianThemeValues
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) theme.Accent.copy(alpha = 0.2f) else theme.BackgroundElevated
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = conversation.lastMessagePreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            IconButton(onClick = onDelete) {
                DeleteIcon(tint = theme.TextSecondary)
            }
        }
    }
}

