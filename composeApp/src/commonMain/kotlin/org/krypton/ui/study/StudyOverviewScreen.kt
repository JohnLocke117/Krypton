package org.krypton.ui.study

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.krypton.core.domain.study.StudyGoal
import org.krypton.core.domain.study.StudyGoalId

/**
 * Overview screen showing study goals and options to create/start sessions.
 * 
 * @param state Current UI state
 * @param onGoalSelected Callback when a goal is selected
 * @param onCreateGoalClick Callback when create goal button is clicked
 * @param onStartSessionClick Callback when start session button is clicked
 */
@Composable
fun StudyOverviewScreen(
    state: StudyUiState,
    onGoalSelected: (StudyGoalId) -> Unit,
    onCreateGoalClick: () -> Unit,
    onStartSessionClick: () -> Unit,
    onDeleteGoal: (StudyGoalId) -> Unit,
    hasItemsForSelectedGoal: Boolean = false, // Whether selected goal has study items
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = "Study Goals",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        // Error message
        state.errorMessage?.let { error ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        
        // Loading indicator
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        
        // Goals list
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // #region agent log
            item {
                kotlin.runCatching {
                    val goalsJson = state.goals.joinToString(",", "[", "]") { "\"${it.id.value}\"" }
                    val titlesJson = state.goals.joinToString(",", "[", "]") { "\"${it.title.replace("\"", "\\\"")}\"" }
                    val vaultIdsJson = state.goals.joinToString(",", "[", "]") { "\"${it.vaultId.replace("\"", "\\\"")}\"" }
                    val logLine = """{"sessionId":"debug-session","runId":"run1","hypothesisId":"A","location":"StudyOverviewScreen.kt:69","message":"Goals list render","data":{"goalsCount":${state.goals.size},"isLoading":${state.isLoading},"goalIds":$goalsJson,"goalTitles":$titlesJson,"goalVaultIds":$vaultIdsJson},"timestamp":${System.currentTimeMillis()}}"""
                    java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("$logLine\n")
                }
            }
            // #endregion
            
            if (state.goals.isEmpty() && !state.isLoading) {
                item {
                    Card {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No study goals yet. Create one to get started!",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(state.goals) { goal ->
                    GoalCard(
                        goal = goal,
                        isSelected = state.currentGoal?.id == goal.id,
                        onClick = { onGoalSelected(goal.id) },
                        onDelete = { onDeleteGoal(goal.id) }
                    )
                }
            }
        }
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onCreateGoalClick,
                modifier = Modifier.weight(1f)
            ) {
                Text("Create Goal")
            }
            
            Button(
                onClick = onStartSessionClick,
                enabled = state.currentGoal != null && !state.isLoading,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (hasItemsForSelectedGoal) "Continue Session" else "Start Session")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalCard(
    goal: StudyGoal,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = goal.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                
                goal.description?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                
                if (goal.targetDate != null) {
                    Text(
                        text = "Target: ${goal.targetDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            
            // Delete button
            IconButton(
                onClick = { onDelete() },
                modifier = Modifier.size(24.dp)
            ) {
                DeleteIcon(
                    contentDescription = "Delete goal",
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
expect fun DeleteIcon(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color? = null
)

