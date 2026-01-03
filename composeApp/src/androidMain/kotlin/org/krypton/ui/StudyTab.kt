package org.krypton.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import org.koin.core.context.GlobalContext
import org.krypton.data.repository.SettingsRepository
import org.krypton.ui.study.*

/**
 * Android study tab with full-screen session mode.
 */
@Composable
fun StudyTab(
    settingsRepository: SettingsRepository,
    theme: org.krypton.ObsidianThemeValues,
    modifier: Modifier = Modifier,
) {
    // Get study mode state from Koin
    val studyModeState = remember { GlobalContext.get().get<StudyModeState>() }
    
    // Get current vault from settings
    val settings by settingsRepository.settingsFlow.collectAsState()
    val vaultId = settings.app.vaultRootUri ?: ""
    
    // Load goals for current vault
    LaunchedEffect(vaultId) {
        studyModeState.loadGoals(vaultId)
    }
    
    val state by studyModeState.state.collectAsState()
    
    Box(modifier = modifier.fillMaxSize()) {
        // Toast notification overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1000f),
            contentAlignment = Alignment.TopCenter
        ) {
            ToastNotification(toastMessage = state.toastMessage)
        }
        
        when {
            // Show session view
            state.showSession -> {
                val currentSession = state.currentSession
                if (currentSession != null) {
                    SessionScreen(
                        session = currentSession,
                state = state,
                onBack = { studyModeState.navigateBackToGoals() },
                    onStartQuiz = { sessionId, count ->
                        studyModeState.startQuiz(sessionId, count)
                    },
                    onSubmitAnswer = { sessionId, index, isCorrect ->
                        studyModeState.submitQuizAnswer(sessionId, index, isCorrect)
                    },
                    onNextQuestion = { sessionId ->
                        studyModeState.moveToNextQuestion(sessionId)
                    },
                    onCompleteQuiz = { sessionId ->
                        studyModeState.completeQuiz(sessionId)
                    },
                isFullScreen = true
            )
        } else {
                    // No session available
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Text("No session available")
                    }
                }
            }
            
            // Show roadmap view
            state.showRoadmap -> {
                val currentGoal = state.currentGoal
                if (currentGoal != null) {
                    GoalRoadmapScreen(
                        goal = currentGoal,
                    sessions = state.sessions,
                    onBack = { studyModeState.navigateBackToGoals() },
                    onStartSession = { sessionId ->
                        studyModeState.prepareSession(sessionId)
                    },
                    onCreateStudyPlan = {
                        state.currentGoal?.id?.let { studyModeState.createStudyPlan(it) }
                    },
                    planningInProgress = state.planningInProgress
                    )
                } else {
                    // No goal available
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Text("No goal available")
                    }
        }
    }
    
            // Show create goal dialog
            state.showCreateGoalDialog -> {
                CreateGoalScreen(
                    onDismiss = { studyModeState.dismissCreateGoalDialog() },
                    onCreate = { title, description, topics, targetDate ->
                        studyModeState.createGoal(vaultId, title, description, topics, targetDate)
                        studyModeState.dismissCreateGoalDialog()
                    }
                )
            }
            
            // Show goal list
            else -> {
                StudyGoalListScreen(
                    state = state,
                    onGoalSelected = { goalId ->
                        studyModeState.selectGoal(goalId)
                        studyModeState.viewRoadmap(goalId)
                    },
                    onCreateGoalClick = { studyModeState.showCreateGoalDialog() },
                    onDeleteGoal = { goalId ->
                        studyModeState.deleteGoal(goalId)
    }
                )
            }
        }
    }
}
