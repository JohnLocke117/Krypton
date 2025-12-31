package org.krypton

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.krypton.ui.state.EditorStateHolder

/**
 * Dialog for displaying and reviewing flashcards.
 * 
 * @param state The flashcards UI state
 * @param onNext Called when user clicks Next
 * @param onPrev Called when user clicks Previous
 * @param onToggleAnswer Called when user toggles answer visibility
 * @param onClose Called when user closes the dialog
 * @param theme Theme values
 */
@Composable
fun FlashcardsScreen(
    state: EditorStateHolder.FlashcardsUiState,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onToggleAnswer: () -> Unit,
    onClose: () -> Unit,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    if (!state.isVisible) return

    // Backdrop
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        // Dialog content
        Surface(
            modifier = modifier
                .widthIn(max = 700.dp, min = 500.dp)
                .heightIn(max = 600.dp)
                .padding(16.dp)
                .clickable(enabled = false, onClick = { /* Prevent click-through */ }),
            shape = RoundedCornerShape(12.dp),
            color = theme.BackgroundElevated
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with title and close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Flashcards",
                        style = MaterialTheme.typography.headlineSmall,
                        color = theme.TextPrimary
                    )
                    IconButton(onClick = onClose) {
                        Text(
                            text = "✕",
                            style = MaterialTheme.typography.titleMedium,
                            color = theme.TextSecondary
                        )
                    }
                }

                // Loading state
                if (state.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(color = theme.Accent)
                            Text(
                                text = "Generating flashcards...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.TextSecondary
                            )
                        }
                    }
                    return@Column
                }

                // Error state
                state.error?.let { error ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Error",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color(0xFFF44336)
                            )
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = onClose,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = theme.Accent,
                                    contentColor = theme.TextPrimary
                                )
                            ) {
                                Text("Close")
                            }
                        }
                    }
                    return@Column
                }

                // Empty state
                if (state.cards.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No flashcards generated.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = theme.TextSecondary
                        )
                    }
                    return@Column
                }

                // Card display
                val card = state.cards[state.currentIndex]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = theme.Surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Question
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Q:",
                                style = MaterialTheme.typography.labelLarge,
                                color = theme.Accent
                            )
                            Text(
                                text = card.question,
                                style = MaterialTheme.typography.bodyLarge,
                                color = theme.TextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Answer section
                        if (state.isAnswerVisible) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "A:",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = theme.Accent
                                )
                                Text(
                                    text = card.answer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = theme.TextSecondary
                                )
                            }
                        } else {
                            Button(
                                onClick = onToggleAnswer,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = theme.Accent,
                                    contentColor = theme.TextPrimary
                                )
                            ) {
                                Text("Show Answer")
                            }
                        }
                    }
                }

                // Navigation controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous button
                    Button(
                        onClick = onPrev,
                        enabled = state.currentIndex > 0,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.Surface,
                            contentColor = theme.TextPrimary,
                            disabledContainerColor = theme.Surface.copy(alpha = 0.5f)
                        )
                    ) {
                        Text("Previous")
                    }

                    // Card counter
                    Text(
                        text = "${state.currentIndex + 1} / ${state.cards.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.TextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    // Next button
                    Button(
                        onClick = onNext,
                        enabled = state.currentIndex < state.cards.size - 1,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.Accent,
                            contentColor = theme.TextPrimary,
                            disabledContainerColor = theme.Surface.copy(alpha = 0.5f)
                        )
                    ) {
                        Text("Next")
                    }
                }
            }
        }
    }
}

