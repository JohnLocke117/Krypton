package org.krypton

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import org.koin.core.context.GlobalContext
import org.krypton.ui.study.*
import org.krypton.data.repository.SettingsRepository
import org.krypton.ui.state.EditorStateHolder

/**
 * Desktop study panel showing goal list, roadmap, and session views.
 */
@Composable
fun StudyPanel(
    editorStateHolder: EditorStateHolder? = null,
    modifier: Modifier = Modifier
) {
    // Get dependencies from Koin
    val studyModeState = remember { GlobalContext.get().get<StudyModeState>() }
    val settingsRepository = remember { GlobalContext.get().get<SettingsRepository>() }
    
    // Get current vault from EditorStateHolder if available, otherwise fall back to settings
    val settings by settingsRepository.settingsFlow.collectAsState()
    val currentDirectory by editorStateHolder?.currentDirectory?.collectAsState() ?: remember { mutableStateOf<String?>(null) }
    val vaultId = currentDirectory ?: settings.app.vaultRootUri ?: ""
    
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
                    isFullScreen = false
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
