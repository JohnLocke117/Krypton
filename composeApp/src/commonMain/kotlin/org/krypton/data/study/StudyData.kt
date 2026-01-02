package org.krypton.data.study

import kotlinx.serialization.Serializable
import org.krypton.core.domain.study.StudyGoal
import org.krypton.core.domain.study.StudySession
import org.krypton.core.domain.study.NoteSummary
import org.krypton.core.domain.study.SessionFlashcards
import org.krypton.core.domain.study.SessionResult

/**
 * Container for all study data in a vault.
 * Serialized to/from JSON for persistence.
 * 
 * @param goals List of all study goals for this vault
 * @param sessions List of all study sessions across all goals
 * @param noteSummaries Cached summaries for notes
 * @param sessionFlashcards Cached flashcards for sessions
 * @param sessionResults Quiz results for sessions
 */
@Serializable
data class StudyData(
    val goals: List<StudyGoal> = emptyList(),
    val sessions: List<StudySession> = emptyList(),
    val noteSummaries: List<NoteSummary> = emptyList(),
    val sessionFlashcards: List<SessionFlashcards> = emptyList(),
    val sessionResults: List<SessionResult> = emptyList(),
)

