package org.krypton.ui.study

import org.krypton.core.domain.study.StudyGoal
import org.krypton.core.domain.study.StudyGoalId
import org.krypton.core.domain.study.StudySession
import org.krypton.core.domain.study.NoteSummary
import org.krypton.core.domain.flashcard.Flashcard

/**
 * State for a quiz in progress.
 */
data class QuizState(
    val sessionId: org.krypton.core.domain.study.StudySessionId,
    val flashcards: List<Flashcard>,
    val currentIndex: Int = 0,
    val answers: Map<Int, Boolean> = emptyMap(), // flashcard index -> isCorrect
)

/**
 * Toast notification message.
 */
data class ToastMessage(
    val text: String,
    val type: ToastType,
    val id: String = java.util.UUID.randomUUID().toString()
)

enum class ToastType {
    SUCCESS,
    ERROR
}

/**
 * UI state for study mode.
 * 
 * @param isLoading Whether data is currently loading
 * @param goals List of study goals for current vault
 * @param currentGoal Currently selected/viewed goal
 * @param currentSession Currently active session
 * @param sessions List of sessions for current goal
 * @param sessionSummaries Map of note path to summary for current session
 * @param sessionFlashcards Flashcards for current session
 * @param quizState Current quiz state if quiz is in progress
 * @param errorMessage Error message if any
 * @param showCreateGoalDialog Whether to show create goal dialog
 * @param showRoadmap Whether to show roadmap view
 * @param showSession Whether to show session view
 * @param planningInProgress Whether planning is currently in progress
 * @param preparingSession Whether session preparation is in progress
 * @param goalLoadingStates Map of goal ID to loading state (for per-goal loading indicators)
 * @param toastMessage Current toast message to display
 */
data class StudyUiState(
    val isLoading: Boolean = false,
    val goals: List<StudyGoal> = emptyList(),
    val currentGoal: StudyGoal? = null,
    val currentSession: StudySession? = null,
    val sessions: List<StudySession> = emptyList(),
    val sessionSummaries: Map<String, NoteSummary> = emptyMap(), // notePath -> summary
    val sessionFlashcards: List<Flashcard>? = null,
    val quizState: QuizState? = null,
    val errorMessage: String? = null,
    val showCreateGoalDialog: Boolean = false,
    val showRoadmap: Boolean = false,
    val showSession: Boolean = false,
    val planningInProgress: Boolean = false,
    val preparingSession: Boolean = false,
    val goalLoadingStates: Map<StudyGoalId, Boolean> = emptyMap(),
    val toastMessage: ToastMessage? = null,
)

