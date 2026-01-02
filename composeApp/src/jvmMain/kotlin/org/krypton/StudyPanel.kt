package org.krypton

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.core.context.GlobalContext
import org.krypton.ui.study.CreateGoalDialog
import org.krypton.ui.study.StudyModeState
import org.krypton.ui.study.StudyOverviewScreen
import org.krypton.ui.study.StudySessionScreen
import org.krypton.data.repository.SettingsRepository

/**
 * Desktop study panel showing overview and session side-by-side.
 */
@Composable
fun StudyPanel(
    modifier: Modifier = Modifier
) {
    // Get dependencies from Koin
    val studyModeState = remember { GlobalContext.get().get<StudyModeState>() }
    val settingsRepository = remember { GlobalContext.get().get<SettingsRepository>() }
    
    // Get current vault from settings
    val settings by settingsRepository.settingsFlow.collectAsState()
    val vaultId = settings.app.vaultRootUri ?: ""
    val theme = rememberObsidianTheme(settings)
    
    // Load goals for current vault
    // Always call loadGoals, even with empty vaultId, since goals may be saved with empty vaultId
    LaunchedEffect(vaultId) {
        // #region agent log
        kotlin.runCatching {
            val logLine = """{"sessionId":"debug-session","runId":"run1","hypothesisId":"F","location":"StudyPanel.kt:31","message":"LaunchedEffect vaultId","data":{"vaultId":"${vaultId.replace("\"", "\\\"")}","vaultIdLength":${vaultId.length},"vaultIdIsEmpty":${vaultId.isEmpty()},"willCallLoadGoals":true},"timestamp":${System.currentTimeMillis()}}"""
            java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("$logLine\n")
        }
        // #endregion
        studyModeState.loadGoals(vaultId)
    }
    
    val state by studyModeState.state.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    
    // Check if selected goal has items (for "Continue Session" button)
    var hasItems by remember(state.selectedGoal?.id) { mutableStateOf(false) }
    
    // Observe items for selected goal
    LaunchedEffect(state.selectedGoal?.id) {
        state.selectedGoal?.id?.let { goalId ->
            val studyItemRepository = org.koin.core.context.GlobalContext.get().get<org.krypton.core.domain.study.StudyItemRepository>()
            studyItemRepository.observeItemsForGoal(goalId).collect { items ->
                hasItems = items.isNotEmpty()
            }
        } ?: run { hasItems = false }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        if (state.showSessionView && state.todaySession != null) {
            // Show session view with back button
            StudySessionScreen(
                state = state,
                onRate = { studyModeState.rateCurrentItem(it) },
                onNext = { studyModeState.moveToNextItem() },
                onExit = { studyModeState.navigateBackToGoals() },
                onBack = { studyModeState.navigateBackToGoals() },
                isFullScreen = false
            )
        } else {
            // Show goals overview
            StudyOverviewScreen(
                state = state,
                onGoalSelected = { studyModeState.selectGoal(it) },
                onCreateGoalClick = { showCreateDialog = true },
                onStartSessionClick = { studyModeState.startTodaySession() },
                onDeleteGoal = { studyModeState.deleteGoal(it) },
                hasItemsForSelectedGoal = hasItems
            )
        }
    }
    
    // Create goal dialog
    if (showCreateDialog) {
        CreateGoalDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title, description, targetDate ->
                studyModeState.createGoal(vaultId, title, description, targetDate)
            },
            theme = theme
        )
    }
}

