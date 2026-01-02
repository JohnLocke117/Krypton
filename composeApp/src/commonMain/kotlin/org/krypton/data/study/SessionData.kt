package org.krypton.data.study

import kotlinx.serialization.Serializable
import org.krypton.core.domain.study.StudySession
import org.krypton.core.domain.study.NoteSummary
import org.krypton.core.domain.study.SessionFlashcards
import org.krypton.core.domain.study.SessionResult

/**
 * Complete data for a single study session, stored in `.krypton/goals/{goalId}/{sessionId}.json`
 * 
 * @param session The session data
 * @param noteSummaries Summaries for notes in this session
 * @param flashcards Flashcards for this session (if generated)
 * @param result Quiz result for this session (if completed)
 */
@Serializable
data class SessionData(
    val session: StudySession,
    val noteSummaries: List<NoteSummary> = emptyList(),
    val flashcards: SessionFlashcards? = null,
    val result: SessionResult? = null,
)

