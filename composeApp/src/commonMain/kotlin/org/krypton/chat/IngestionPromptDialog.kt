package org.krypton.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.krypton.ObsidianThemeValues

/**
 * Dialog prompting user to index the vault before enabling RAG.
 * 
 * @param onContinue Called when user clicks Continue
 * @param onCancel Called when user clicks Cancel
 * @param isIngesting Whether ingestion is currently in progress
 * @param errorMessage Error message to display, if any
 * @param success Whether the operation completed successfully
 * @param title Dialog title (defaults to "Index Vault")
 * @param message Custom message (optional, uses default if null)
 * @param theme Theme values
 */
@Composable
fun IngestionPromptDialog(
    onContinue: () -> Unit,
    onCancel: () -> Unit,
    isIngesting: Boolean = false,
    errorMessage: String? = null,
    success: Boolean = false,
    title: String = "Index Vault",
    message: String? = null,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    // Backdrop
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(enabled = !isIngesting, onClick = onCancel),
        contentAlignment = Alignment.Center
    ) {
        // Dialog content
        Surface(
            modifier = modifier
                .widthIn(max = 500.dp)
                .padding(16.dp)
                .clickable(enabled = false, onClick = { /* Prevent click-through */ }),
            shape = RoundedCornerShape(12.dp),
            color = theme.BackgroundElevated
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = theme.TextPrimary
                )
                
                // Message
                Text(
                    text = message ?: when {
                        success -> "Vault indexed successfully! RAG mode is now enabled."
                        errorMessage != null -> errorMessage
                        isIngesting -> "Indexing vault... This may take a few minutes."
                        else -> "This vault hasn't been indexed yet. Indexing will generate embeddings for all markdown files. This may take a few minutes."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        success -> Color(0xFF4CAF50) // Green for success
                        errorMessage != null -> Color(0xFFF44336) // Red for error
                        else -> theme.TextSecondary
                    }
                )
                
                // Progress indicator (if ingesting)
                if (isIngesting) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = theme.Accent
                    )
                }
                
                // Success indicator
                if (success) {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cancel/Close button
                    TextButton(
                        onClick = onCancel,
                        enabled = !isIngesting,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (success || errorMessage != null) "Close" else "Cancel",
                            color = theme.TextSecondary
                        )
                    }
                    
                    // Continue button (only show if not ingesting and not completed)
                    if (!isIngesting && !success && errorMessage == null) {
                        Button(
                            onClick = onContinue,
                            enabled = !isIngesting,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.Accent,
                                contentColor = theme.TextPrimary
                            )
                        ) {
                            Text("Continue")
                        }
                    }
                }
            }
        }
    }
}

