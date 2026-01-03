package org.krypton.data.study

import kotlinx.serialization.Serializable
import org.krypton.core.domain.study.StudyGoal
import org.krypton.core.domain.study.StudySession
import org.krypton.core.domain.study.NoteSummary
import org.krypton.core.domain.study.SessionFlashcards
import org.krypton.core.domain.study.SessionResult

/**
 * Complete data for a single study goal, stored in `.krypton/goals/{goalId}.json`
 * 
 * This contains the goal itself plus all its sessions and related data.
 * 
 * @param goal The study goal
 * @param roadmap Roadmap/summary for this goal (markdown content)
 * @param sessions All sessions for this goal
 * @param noteSummaries Cached summaries for notes used in any session
 * @param sessionFlashcards Cached flashcards for sessions (if generated)
 * @param sessionResults Quiz results for sessions (if completed)
 */
@Serializable
data class GoalData(
    val goal: StudyGoal,
    val roadmap: String? = null,
    val sessions: List<StudySession> = emptyList(),
    val noteSummaries: List<NoteSummary> = emptyList(),
    val sessionFlashcards: List<SessionFlashcards> = emptyList(),
    val sessionResults: List<SessionResult> = emptyList(),
)

