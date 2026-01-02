package org.krypton.core.domain.study

import kotlinx.serialization.Serializable
import org.krypton.core.domain.flashcard.Flashcard

/**
 * Cached flashcards for a session.
 * 
 * @param sessionId ID of the session these flashcards belong to
 * @param flashcards List of flashcards generated for this session
 * @param generatedAtMillis Timestamp when flashcards were generated
 */
@Serializable
data class SessionFlashcards(
    val sessionId: StudySessionId,
    val flashcards: List<Flashcard>,
    val generatedAtMillis: Long,
)

