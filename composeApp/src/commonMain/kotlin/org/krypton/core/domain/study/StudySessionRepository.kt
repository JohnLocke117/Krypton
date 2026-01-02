package org.krypton.core.domain.study

import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing study sessions.
 */
interface StudySessionRepository {
    /**
     * Observes all sessions for a goal.
     * 
     * @param goalId Goal ID
     * @return Flow of sessions list
     */
    fun observeSessionsForGoal(goalId: StudyGoalId): Flow<List<StudySession>>
    
    /**
     * Gets a specific session by ID.
     * 
     * @param id Session ID
     * @return Session if found, null otherwise
     */
    suspend fun getSession(id: StudySessionId): StudySession?
    
    /**
     * Creates or updates a session.
     * 
     * @param session Session to upsert
     */
    suspend fun upsertSession(session: StudySession)
    
    /**
     * Updates the status of a session.
     * 
     * @param id Session ID
     * @param status New status
     */
    suspend fun updateSessionStatus(id: StudySessionId, status: SessionStatus)
}

