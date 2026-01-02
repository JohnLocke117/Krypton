package org.krypton.core.domain.study

/**
 * Service for running study sessions, preparing summaries/flashcards, and managing quizzes.
 */
interface StudyRunner {
    /**
     * Prepares a session by ensuring summaries and flashcards exist.
     * Generates them if they don't exist in cache.
     * 
     * @param sessionId ID of the session to prepare
     */
    suspend fun prepareSession(sessionId: StudySessionId)
    
    /**
     * Runs a quiz for a session and returns the result.
     * 
     * @param sessionId ID of the session
     * @param flashcardCount Number of flashcards to include in quiz
     * @param answers Map of flashcard index to whether answer was correct
     * @return SessionResult with score and completion status
     */
    suspend fun runQuiz(
        sessionId: StudySessionId,
        flashcardCount: Int,
        answers: Map<Int, Boolean>
    ): SessionResult
    
    /**
     * Completes a session based on quiz result.
     * Updates session status and goal status if all sessions are completed.
     * 
     * @param sessionId ID of the session
     * @param result Quiz result
     */
    suspend fun completeSession(sessionId: StudySessionId, result: SessionResult)
}

