package org.krypton.data.study

import org.krypton.core.domain.study.StudySessionId
import org.krypton.core.domain.study.StudyCacheRepository
import org.krypton.core.domain.study.NoteSummary
import org.krypton.core.domain.study.SessionFlashcards
import org.krypton.core.domain.study.SessionResult
import org.krypton.util.AppLogger

/**
 * Implementation of StudyCacheRepository using StudyPersistence.
 */
class StudyCacheRepositoryImpl(
    private val persistence: StudyPersistence,
    private val goalRepository: org.krypton.core.domain.study.StudyGoalRepository,
    private val sessionRepository: org.krypton.core.domain.study.StudySessionRepository,
) : StudyCacheRepository {
    
    // In-memory cache of study data per vault
    private val dataCache = kotlinx.coroutines.flow.MutableStateFlow<Map<String, StudyData>>(emptyMap())
    
    override suspend fun getNoteSummary(notePath: String): NoteSummary? {
        // Search through all cached study plans for this note
        return dataCache.value.values
            .flatMap { it.noteSummaries }
            .firstOrNull { it.notePath == notePath }
    }
    
    override suspend fun saveNoteSummary(summary: NoteSummary) {
        try {
            // Find which goal/session this note belongs to by searching through sessions
            // This is a limitation - we need to know which goal the note belongs to
            // For now, we'll search through all goals to find a matching session
            val matchingSession = dataCache.value.values
                .flatMap { it.sessions }
                .firstOrNull { session -> session.notePaths.contains(summary.notePath) }
            
            if (matchingSession == null) {
                AppLogger.w("StudyCacheRepository", "Could not find session for note: ${summary.notePath}")
                return
            }
            
            val goal = goalRepository.getGoal(matchingSession.goalId) ?: run {
                AppLogger.e("StudyCacheRepository", "Goal not found for session: ${matchingSession.id}")
                return
            }
            
            // Load existing session data
            val existingSessionData = persistence.loadSessionData(goal.vaultId, goal.id.value, matchingSession.id.value)
            val existingSummaries = existingSessionData?.noteSummaries?.filterNot { it.notePath == summary.notePath } ?: emptyList()
            val updatedSummaries = existingSummaries + summary
            
            val sessionData = (existingSessionData ?: org.krypton.data.study.SessionData(
                session = matchingSession,
                noteSummaries = emptyList(),
                flashcards = null,
                result = null
            )).copy(noteSummaries = updatedSummaries)
            
            if (persistence.saveSessionData(goal.vaultId, goal.id.value, matchingSession.id.value, sessionData)) {
                // Update cache
                val currentPlan = loadStudyPlan(goal.vaultId, goal.id.value)
                updateCacheForGoal(goal.vaultId, goal.id.value, currentPlan)
                AppLogger.d("StudyCacheRepository", "Saved note summary: ${summary.notePath}")
            } else {
                AppLogger.e("StudyCacheRepository", "Failed to save note summary: ${summary.notePath}")
            }
        } catch (e: Exception) {
            AppLogger.e("StudyCacheRepository", "Error saving note summary: ${summary.notePath}", e)
        }
    }
    
    override suspend fun getSessionFlashcards(sessionId: StudySessionId): SessionFlashcards? {
        val session = sessionRepository.getSession(sessionId) ?: return null
        val goal = goalRepository.getGoal(session.goalId) ?: return null
        val sessionData = persistence.loadSessionData(goal.vaultId, goal.id.value, sessionId.value)
        return sessionData?.flashcards
    }
    
    override suspend fun saveSessionFlashcards(flashcards: SessionFlashcards) {
        try {
            val session = sessionRepository.getSession(flashcards.sessionId) ?: run {
                AppLogger.e("StudyCacheRepository", "Session not found for flashcards: ${flashcards.sessionId}")
                return
            }
            
            val goal = goalRepository.getGoal(session.goalId) ?: run {
                AppLogger.e("StudyCacheRepository", "Goal not found for session: ${session.id}")
                return
            }
            
            // Load existing session data
            val existingSessionData = persistence.loadSessionData(goal.vaultId, goal.id.value, session.id.value)
            val sessionData = (existingSessionData ?: org.krypton.data.study.SessionData(
                session = session,
                noteSummaries = emptyList(),
                flashcards = null,
                result = null
            )).copy(flashcards = flashcards)
            
            if (persistence.saveSessionData(goal.vaultId, goal.id.value, session.id.value, sessionData)) {
                // Update cache
                val currentPlan = loadStudyPlan(goal.vaultId, goal.id.value)
                updateCacheForGoal(goal.vaultId, goal.id.value, currentPlan)
                AppLogger.d("StudyCacheRepository", "Saved session flashcards: ${flashcards.sessionId}")
            } else {
                AppLogger.e("StudyCacheRepository", "Failed to save session flashcards: ${flashcards.sessionId}")
            }
        } catch (e: Exception) {
            AppLogger.e("StudyCacheRepository", "Error saving session flashcards: ${flashcards.sessionId}", e)
        }
    }
    
    override suspend fun getSessionResult(sessionId: StudySessionId): SessionResult? {
        val session = sessionRepository.getSession(sessionId) ?: return null
        val goal = goalRepository.getGoal(session.goalId) ?: return null
        val sessionData = persistence.loadSessionData(goal.vaultId, goal.id.value, sessionId.value)
        return sessionData?.result
    }
    
    override suspend fun saveSessionResult(result: SessionResult) {
        try {
            val session = sessionRepository.getSession(result.sessionId) ?: run {
                AppLogger.e("StudyCacheRepository", "Session not found for result: ${result.sessionId}")
                return
            }
            
            val goal = goalRepository.getGoal(session.goalId) ?: run {
                AppLogger.e("StudyCacheRepository", "Goal not found for session: ${session.id}")
                return
            }
            
            // Load existing session data
            val existingSessionData = persistence.loadSessionData(goal.vaultId, goal.id.value, session.id.value)
            val sessionData = (existingSessionData ?: org.krypton.data.study.SessionData(
                session = session,
                noteSummaries = emptyList(),
                flashcards = null,
                result = null
            )).copy(result = result)
            
            if (persistence.saveSessionData(goal.vaultId, goal.id.value, session.id.value, sessionData)) {
                // Update cache
                val currentPlan = loadStudyPlan(goal.vaultId, goal.id.value)
                updateCacheForGoal(goal.vaultId, goal.id.value, currentPlan)
                AppLogger.d("StudyCacheRepository", "Saved session result: ${result.sessionId}")
            } else {
                AppLogger.e("StudyCacheRepository", "Failed to save session result: ${result.sessionId}")
            }
        } catch (e: Exception) {
            AppLogger.e("StudyCacheRepository", "Error saving session result: ${result.sessionId}", e)
        }
    }
    
    // Note: deleteCachedSessionData and deleteCachedGoalData are not in the interface
    // They can be added if needed, but for now we'll keep them as private helpers
    private suspend fun deleteCachedSessionData(sessionId: StudySessionId) {
        try {
            val session = sessionRepository.getSession(sessionId) ?: return
            val goal = goalRepository.getGoal(session.goalId) ?: return
            val currentPlan = loadStudyPlan(goal.vaultId, goal.id.value)
            
            // Filter out summaries for notes in this session
            val sessionNotePaths = session.notePaths.toSet()
            val updatedPlan = currentPlan.copy(
                noteSummaries = currentPlan.noteSummaries.filterNot { it.notePath in sessionNotePaths },
                sessionFlashcards = currentPlan.sessionFlashcards.filterNot { it.sessionId == sessionId },
                sessionResults = currentPlan.sessionResults.filterNot { it.sessionId == sessionId }
            )
            
            if (persistence.saveStudyPlan(goal.vaultId, goal.id.value, updatedPlan)) {
                updateCacheForGoal(goal.vaultId, goal.id.value, updatedPlan)
                AppLogger.d("StudyCacheRepository", "Deleted cached data for session: $sessionId")
            } else {
                AppLogger.e("StudyCacheRepository", "Failed to delete cached data for session: $sessionId")
            }
        } catch (e: Exception) {
            AppLogger.e("StudyCacheRepository", "Error deleting cached data for session: $sessionId", e)
        }
    }
    
    private suspend fun deleteCachedGoalData(goalId: org.krypton.core.domain.study.StudyGoalId) {
        try {
            val goal = goalRepository.getGoal(goalId) ?: return
            // Delete the study plan file
            val emptyPlan = StudyData()
            if (persistence.saveStudyPlan(goal.vaultId, goalId.value, emptyPlan)) {
                // Update cache - remove all data for this goal
                val currentCache = dataCache.value[goal.vaultId] ?: StudyData()
                val goalSessions = currentCache.sessions.filter { it.goalId == goalId }
                val goalNotePaths = goalSessions.flatMap { it.notePaths }.toSet()
                val updatedCache = currentCache.copy(
                    sessions = currentCache.sessions.filterNot { it.goalId == goalId },
                    noteSummaries = currentCache.noteSummaries.filterNot { it.notePath in goalNotePaths },
                    sessionFlashcards = currentCache.sessionFlashcards.filterNot { flashcard ->
                        goalSessions.any { it.id == flashcard.sessionId }
                    },
                    sessionResults = currentCache.sessionResults.filterNot { result ->
                        goalSessions.any { it.id == result.sessionId }
                    }
                )
                updateCache(goal.vaultId, updatedCache)
                AppLogger.d("StudyCacheRepository", "Deleted cached data for goal: $goalId")
            } else {
                AppLogger.e("StudyCacheRepository", "Failed to delete cached data for goal: $goalId")
            }
        } catch (e: Exception) {
            AppLogger.e("StudyCacheRepository", "Error deleting cached data for goal: $goalId", e)
        }
    }
    
    private suspend fun loadStudyPlan(vaultId: String, goalId: String): StudyData {
        // Check cache first
        val cached = dataCache.value[vaultId] ?: return StudyData()
        val cachedSessions = cached.sessions.filter { it.goalId.value == goalId }
        if (cachedSessions.isNotEmpty()) {
            // Get note paths for sessions in this goal
            val goalNotePaths = cachedSessions.flatMap { it.notePaths }.toSet()
            // Get session IDs for sessions in this goal
            val goalSessionIds = cachedSessions.map { it.id }.toSet()
            
            return StudyData(
                sessions = cachedSessions,
                noteSummaries = cached.noteSummaries.filter { it.notePath in goalNotePaths },
                sessionFlashcards = cached.sessionFlashcards.filter { it.sessionId in goalSessionIds },
                sessionResults = cached.sessionResults.filter { it.sessionId in goalSessionIds }
            )
        }
        
        // Load from persistence
        val plan = persistence.loadStudyPlan(vaultId, goalId) ?: StudyData()
        
        // Update cache
        val currentCache = dataCache.value[vaultId] ?: StudyData()
        val goalNotePaths = plan.sessions.flatMap { it.notePaths }.toSet()
        val goalSessionIds = plan.sessions.map { it.id }.toSet()
        val updatedCache = currentCache.copy(
            sessions = currentCache.sessions.filterNot { it.goalId.value == goalId } + plan.sessions,
            noteSummaries = currentCache.noteSummaries.filterNot { it.notePath in goalNotePaths } + plan.noteSummaries,
            sessionFlashcards = currentCache.sessionFlashcards.filterNot { it.sessionId in goalSessionIds } + plan.sessionFlashcards,
            sessionResults = currentCache.sessionResults.filterNot { it.sessionId in goalSessionIds } + plan.sessionResults
        )
        updateCache(vaultId, updatedCache)
        
        return plan
    }
    
    private fun updateCacheForGoal(vaultId: String, goalId: String, plan: StudyData) {
        val currentCache = dataCache.value[vaultId] ?: StudyData()
        val goalNotePaths = plan.sessions.flatMap { it.notePaths }.toSet()
        val goalSessionIds = plan.sessions.map { it.id }.toSet()
        val updatedCache = currentCache.copy(
            sessions = currentCache.sessions.filterNot { it.goalId.value == goalId } + plan.sessions,
            noteSummaries = currentCache.noteSummaries.filterNot { it.notePath in goalNotePaths } + plan.noteSummaries,
            sessionFlashcards = currentCache.sessionFlashcards.filterNot { it.sessionId in goalSessionIds } + plan.sessionFlashcards,
            sessionResults = currentCache.sessionResults.filterNot { it.sessionId in goalSessionIds } + plan.sessionResults
        )
        updateCache(vaultId, updatedCache)
    }
    
    private suspend fun loadData(vaultId: String): StudyData {
        // Check cache first
        dataCache.value[vaultId]?.let { return it }
        
        // Load from persistence (legacy format for backward compatibility)
        val data = persistence.loadStudyData(vaultId) ?: StudyData()
        updateCache(vaultId, data)
        return data
    }
    
    private fun updateCache(vaultId: String, data: StudyData) {
        dataCache.value = dataCache.value + (vaultId to data)
    }
}

