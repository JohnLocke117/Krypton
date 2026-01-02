package org.krypton.core.domain.study

/**
 * Repository for caching study-related data (summaries, flashcards, results).
 */
interface StudyCacheRepository {
    /**
     * Gets a cached note summary.
     * 
     * @param notePath Path to the note
     * @return Summary if found, null otherwise
     */
    suspend fun getNoteSummary(notePath: String): NoteSummary?
    
    /**
     * Saves a note summary to cache.
     * 
     * @param summary Summary to save
     */
    suspend fun saveNoteSummary(summary: NoteSummary)
    
    /**
     * Gets cached flashcards for a session.
     * 
     * @param sessionId Session ID
     * @return Flashcards if found, null otherwise
     */
    suspend fun getSessionFlashcards(sessionId: StudySessionId): SessionFlashcards?
    
    /**
     * Saves flashcards for a session to cache.
     * 
     * @param flashcards Flashcards to save
     */
    suspend fun saveSessionFlashcards(flashcards: SessionFlashcards)
    
    /**
     * Gets quiz result for a session.
     * 
     * @param sessionId Session ID
     * @return Result if found, null otherwise
     */
    suspend fun getSessionResult(sessionId: StudySessionId): SessionResult?
    
    /**
     * Saves quiz result for a session.
     * 
     * @param result Result to save
     */
    suspend fun saveSessionResult(result: SessionResult)
}

