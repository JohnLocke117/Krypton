package org.krypton.ui.study

import kotlinx.coroutines.flow.StateFlow
import org.krypton.core.domain.study.ReviewRating
import org.krypton.core.domain.study.StudyGoalId

/**
 * State holder interface for study mode.
 */
interface StudyModeState {
    /**
     * Observable UI state.
     */
    val state: StateFlow<StudyUiState>
    
    /**
     * Loads goals for a vault.
     * 
     * @param vaultId ID of the vault
     */
    fun loadGoals(vaultId: String)
    
    /**
     * Selects a goal.
     * 
     * @param goalId ID of goal to select
     */
    fun selectGoal(goalId: StudyGoalId)
    
    /**
     * Starts today's study session for the selected goal.
     */
    fun startTodaySession()
    
    /**
     * Navigates back to the goals list view.
     */
    fun navigateBackToGoals()
    
    /**
     * Rates the current item in the session.
     * 
     * @param rating User's rating
     */
    fun rateCurrentItem(rating: ReviewRating)
    
    /**
     * Moves to the next item in the session.
     */
    fun moveToNextItem()
    
    /**
     * Ends the current study session.
     */
    fun endSession()
    
    /**
     * Creates a new study goal.
     * 
     * @param vaultId ID of the vault
     * @param title Goal title
     * @param description Optional description
     * @param targetDate Optional target date (ISO-8601 string)
     */
    fun createGoal(
        vaultId: String,
        title: String,
        description: String?,
        targetDate: String?,
    )
    
    /**
     * Deletes a study goal.
     * 
     * @param goalId ID of goal to delete
     */
    fun deleteGoal(goalId: StudyGoalId)
    
    /**
     * Shows the create goal dialog.
     */
    fun showCreateGoalDialog()
    
    /**
     * Dismisses the create goal dialog.
     */
    fun dismissCreateGoalDialog()
}

