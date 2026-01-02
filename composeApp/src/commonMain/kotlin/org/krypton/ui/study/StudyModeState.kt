package org.krypton.ui.study

import kotlinx.coroutines.flow.StateFlow
import org.krypton.core.domain.study.StudyGoalId
import org.krypton.core.domain.study.StudySessionId

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
     * Creates a new study goal with topics.
     * 
     * @param vaultId ID of the vault
     * @param title Goal title
     * @param description Optional description
     * @param topics List of topics (each becomes a session)
     * @param targetDate Optional target date (ISO-8601 string)
     */
    fun createGoal(
        vaultId: String,
        title: String,
        description: String?,
        topics: List<String>,
        targetDate: String?,
    )
    
    /**
     * Plans a goal by creating sessions for each topic.
     * 
     * @param goalId ID of the goal to plan
     */
    fun planGoal(goalId: StudyGoalId)
    
    /**
     * Creates a study plan for a goal: generates roadmap using LLM, then creates sessions.
     * 
     * @param goalId ID of the goal to create study plan for
     */
    fun createStudyPlan(goalId: StudyGoalId)
    
    /**
     * Selects a goal and shows its roadmap.
     * 
     * @param goalId ID of goal to select
     */
    fun selectGoal(goalId: StudyGoalId)
    
    /**
     * Views the roadmap for a goal.
     * 
     * @param goalId ID of the goal
     */
    fun viewRoadmap(goalId: StudyGoalId)
    
    /**
     * Prepares a session by ensuring summaries and flashcards exist.
     * 
     * @param sessionId ID of the session
     */
    fun prepareSession(sessionId: StudySessionId)
    
    /**
     * Starts a quiz for a session.
     * 
     * @param sessionId ID of the session
     * @param flashcardCount Number of flashcards to include
     */
    fun startQuiz(sessionId: StudySessionId, flashcardCount: Int)
    
    /**
     * Submits an answer for the current quiz question.
     * 
     * @param sessionId ID of the session
     * @param flashcardIndex Index of the flashcard
     * @param isCorrect Whether the answer was correct
     */
    fun submitQuizAnswer(sessionId: StudySessionId, flashcardIndex: Int, isCorrect: Boolean)
    
    /**
     * Moves to the next question in the quiz.
     * 
     * @param sessionId ID of the session
     */
    fun moveToNextQuestion(sessionId: StudySessionId)
    
    /**
     * Completes the quiz and updates session status.
     * 
     * @param sessionId ID of the session
     */
    fun completeQuiz(sessionId: StudySessionId)
    
    /**
     * Deletes a study goal.
     * 
     * @param goalId ID of goal to delete
     */
    fun deleteGoal(goalId: StudyGoalId)
    
    /**
     * Navigates back to the goals list view.
     */
    fun navigateBackToGoals()
    
    /**
     * Shows the create goal dialog.
     */
    fun showCreateGoalDialog()
    
    /**
     * Dismisses the create goal dialog.
     */
    fun dismissCreateGoalDialog()
}

