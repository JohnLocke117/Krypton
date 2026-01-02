package org.krypton.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.koin.core.context.GlobalContext
import org.krypton.ObsidianThemeValues
import org.krypton.data.repository.SettingsRepository
import org.krypton.ui.study.CreateGoalDialog
import org.krypton.ui.study.StudyModeState
import org.krypton.ui.study.StudyOverviewScreen
import org.krypton.ui.study.StudySessionScreen

/**
 * Android study tab with full-screen session mode.
 */
@Composable
fun StudyTab(
    settingsRepository: SettingsRepository,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier,
) {
    // Get study mode state from Koin
    val studyModeState = remember { GlobalContext.get().get<StudyModeState>() }
    
    // Get current vault from settings
    val settings by settingsRepository.settingsFlow.collectAsState()
    val vaultId = settings.app.vaultRootUri ?: ""
    
    // Load goals for current vault
    // Always call loadGoals, even with empty vaultId, since goals may be saved with empty vaultId
    LaunchedEffect(vaultId) {
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
            // Show full-screen session with back button
            StudySessionScreen(
                state = state,
                onRate = { studyModeState.rateCurrentItem(it) },
                onNext = { studyModeState.moveToNextItem() },
                onExit = { studyModeState.navigateBackToGoals() },
                onBack = { studyModeState.navigateBackToGoals() },
                isFullScreen = true
            )
        } else {
            // Show overview when no active session
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

