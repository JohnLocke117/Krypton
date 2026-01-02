package org.krypton.ui.study

import org.krypton.core.domain.study.StudyGoal
import org.krypton.core.domain.study.StudySession

/**
 * UI state for study mode.
 * 
 * @param isLoading Whether data is currently loading
 * @param goals List of study goals for current vault
 * @param selectedGoal Currently selected goal
 * @param todaySession Current study session, null if no session active
 * @param currentItemIndex Index of current item in session
 * @param errorMessage Error message if any
 */
data class StudyUiState(
    val isLoading: Boolean = false,
    val goals: List<StudyGoal> = emptyList(),
    val selectedGoal: StudyGoal? = null,
    val todaySession: StudySession? = null,
    val currentItemIndex: Int = 0,
    val errorMessage: String? = null,
    val showCreateGoalDialog: Boolean = false,
    val showSessionView: Boolean = false, // Whether to show session view instead of goals list
)

