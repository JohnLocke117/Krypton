package org.krypton.ui.study

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.krypton.CatppuccinMochaColors
import org.krypton.core.domain.study.StudyGoal
import org.krypton.core.domain.study.StudyGoalId
import org.krypton.core.domain.study.GoalStatus

/**
 * Screen showing list of study goals.
 * 
 * @param state Current UI state
 * @param onGoalSelected Callback when a goal is selected
 * @param onCreateGoalClick Callback when create goal button is clicked
 * @param onDeleteGoal Callback when delete goal is clicked
 */
@Composable
fun StudyGoalListScreen(
    state: StudyUiState,
    onGoalSelected: (StudyGoalId) -> Unit,
    onCreateGoalClick: () -> Unit,
    onDeleteGoal: (StudyGoalId) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Show toast notification if present
    state.toastMessage?.let { toast ->
        LaunchedEffect(toast.id) {
            // Toast auto-dismisses in state management, but we can add visual feedback here
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Create Goal button (header removed, now in top bar)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(onClick = onCreateGoalClick) {
                Text("Create Goal")
            }
        }
        
        // Error message
        state.errorMessage?.let { error ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = CatppuccinMochaColors.Surface0
                )
            ) {
                Text(
                    text = error,
                    color = CatppuccinMochaColors.Red,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        
        // Loading indicator
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = CatppuccinMochaColors.Blue
                )
            }
        }
        
        // Goals list
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.goals.isEmpty() && !state.isLoading) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = CatppuccinMochaColors.Surface0
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No study goals yet. Create one to get started!",
                                style = MaterialTheme.typography.bodySmall,
                                color = CatppuccinMochaColors.Subtext0
                            )
                        }
                    }
                }
            } else {
                items(state.goals) { goal ->
                    GoalCard(
                        goal = goal,
                        isSelected = state.currentGoal?.id == goal.id,
                        isLoading = state.goalLoadingStates[goal.id] == true,
                        onClick = { onGoalSelected(goal.id) },
                        onDelete = { onDeleteGoal(goal.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalCard(
    goal: StudyGoal,
    isSelected: Boolean,
    isLoading: Boolean = false,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                CatppuccinMochaColors.Surface1
            } else {
                CatppuccinMochaColors.Surface0
            }
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
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = goal.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CatppuccinMochaColors.Text
                )
                
                goal.description?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = CatppuccinMochaColors.Subtext1
                    )
                }
                
                // Topics
                if (goal.topics.isNotEmpty()) {
                    Text(
                        text = "Topics: ${goal.topics.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CatppuccinMochaColors.Subtext0
                    )
                }
                
                // Status
                Text(
                    text = "Status: ${goal.status.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = when (goal.status) {
                        GoalStatus.COMPLETED -> CatppuccinMochaColors.Green
                        GoalStatus.IN_PROGRESS -> CatppuccinMochaColors.Blue
                        GoalStatus.PENDING -> CatppuccinMochaColors.Subtext0
                    }
                )
                
                if (goal.targetDate != null) {
                    Text(
                        text = "Target: ${goal.targetDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CatppuccinMochaColors.Subtext0
                    )
                }
            }
            
            // Loading indicator or Delete button
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = CatppuccinMochaColors.Blue
                )
            } else {
                IconButton(
                    onClick = { onDelete() },
                    modifier = Modifier.size(20.dp)
                ) {
                    DeleteIcon(
                        contentDescription = "Delete goal",
                        tint = CatppuccinMochaColors.Red
                    )
                }
            }
        }
    }
}

// DeleteIcon is defined in StudyOverviewScreen.kt

