package org.krypton.data.study

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.krypton.core.domain.study.StudySession
import org.krypton.core.domain.study.StudySessionId
import org.krypton.core.domain.study.StudyGoalId
import org.krypton.core.domain.study.StudySessionRepository
import org.krypton.core.domain.study.SessionStatus
import org.krypton.util.AppLogger

/**
 * Implementation of StudySessionRepository using StudyPersistence.
 */
class StudySessionRepositoryImpl(
    private val persistence: StudyPersistence,
    private val goalRepository: org.krypton.core.domain.study.StudyGoalRepository,
) : StudySessionRepository {
    
    // In-memory cache of study data per vault
    private val dataCache = MutableStateFlow<Map<String, StudyData>>(emptyMap())
    
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSessionsForGoal(goalId: StudyGoalId): Flow<List<StudySession>> {
        // Load sessions from study plan when flow is collected, then observe cache changes
        return kotlinx.coroutines.flow.flow {
            try {
                val goal = goalRepository.getGoal(goalId) ?: run {
                    emit(emptyList())
                    return@flow
                }
                
                // Load study plan for this goal
                val plan = persistence.loadStudyPlan(goal.vaultId, goalId.value) ?: StudyData()
                
                // Update cache
                val currentCache = dataCache.value[goal.vaultId] ?: StudyData()
                val updatedCache = currentCache.copy(
                    sessions = currentCache.sessions.filterNot { it.goalId == goalId } + plan.sessions
                )
                dataCache.value = dataCache.value + (goal.vaultId to updatedCache)
                
                emit(plan.sessions.sortedBy { it.order })
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Re-throw cancellation to properly handle it
                throw e
            } catch (e: Exception) {
                AppLogger.e("StudySessionRepository", "Error loading sessions for goal: $goalId", e)
                emit(emptyList())
            }
        }.flatMapLatest { initialSessions ->
            // Then observe cache changes, starting with initial sessions
            dataCache.map { cache ->
                cache.values
                    .flatMap { it.sessions }
                    .filter { it.goalId == goalId }
                    .sortedBy { it.order }
            }.onStart { emit(initialSessions) }
        }.catch { e ->
            if (e is kotlinx.coroutines.CancellationException) {
                throw e
            }
            AppLogger.e("StudySessionRepository", "Error in observeSessionsForGoal flow", e)
            emit(emptyList())
        }
    }
    
    override suspend fun getSession(id: StudySessionId): StudySession? {
        // Try to find in cache first
        val cached = dataCache.value.values
            .flatMap { it.sessions }
            .firstOrNull { it.id == id }
        if (cached != null) return cached
        
        // If not in cache, load from persistence
        // Session IDs are in format: {goalId}-session-{index}
        val goalIdStr = id.value.substringBefore("-session-")
        val goalId = StudyGoalId(goalIdStr)
        val goal = getGoalForSession(goalId) ?: return null
        
        // Load session data from new structure
        val sessionData = persistence.loadSessionData(goal.vaultId, goal.id.value, id.value)
        return sessionData?.session
    }
    
    override suspend fun upsertSession(session: StudySession) {
        try {
            // Get goal to find vault
            val goal = getGoalForSession(session.goalId) ?: run {
                AppLogger.e("StudySessionRepository", "Goal not found for session: ${session.id}")
                return
            }
            
            // Load existing session data or create new
            val existingSessionData = persistence.loadSessionData(goal.vaultId, goal.id.value, session.id.value)
            val sessionData = if (existingSessionData != null) {
                existingSessionData.copy(session = session)
            } else {
                org.krypton.data.study.SessionData(
                    session = session,
                    noteSummaries = emptyList(),
                    flashcards = null,
                    result = null
                )
            }
            
            if (persistence.saveSessionData(goal.vaultId, goal.id.value, session.id.value, sessionData)) {
                // Update cache
                val currentPlan = loadStudyPlan(goal.vaultId, goal.id.value)
                updateCacheForGoal(goal.vaultId, goal.id.value, currentPlan)
                AppLogger.d("StudySessionRepository", "Saved session: ${session.id}")
            } else {
                AppLogger.e("StudySessionRepository", "Failed to save session: ${session.id}")
            }
        } catch (e: Exception) {
            AppLogger.e("StudySessionRepository", "Error upserting session: ${session.id}", e)
        }
    }
    
    override suspend fun updateSessionStatus(id: StudySessionId, status: SessionStatus) {
        try {
            val session = getSession(id) ?: run {
                AppLogger.e("StudySessionRepository", "Session not found: $id")
                return
            }
            
            val goal = getGoalForSession(session.goalId) ?: run {
                AppLogger.e("StudySessionRepository", "Goal not found for session: $id")
                return
            }
            
            val updatedSession = session.copy(
                status = status,
                completedAtMillis = if (status == SessionStatus.COMPLETED) {
                    System.currentTimeMillis()
                } else {
                    null
                }
            )
            
            // Load existing session data and update
            val existingSessionData = persistence.loadSessionData(goal.vaultId, goal.id.value, id.value)
            val sessionData = if (existingSessionData != null) {
                existingSessionData.copy(session = updatedSession)
            } else {
                org.krypton.data.study.SessionData(
                    session = updatedSession,
                    noteSummaries = emptyList(),
                    flashcards = null,
                    result = null
                )
            }
            
            if (persistence.saveSessionData(goal.vaultId, goal.id.value, id.value, sessionData)) {
                // Update cache
                val currentPlan = loadStudyPlan(goal.vaultId, goal.id.value)
                updateCacheForGoal(goal.vaultId, goal.id.value, currentPlan)
                AppLogger.d("StudySessionRepository", "Updated session status: $id -> $status")
            } else {
                AppLogger.e("StudySessionRepository", "Failed to update session status: $id")
            }
        } catch (e: Exception) {
            AppLogger.e("StudySessionRepository", "Error updating session status: $id", e)
        }
    }
    
    private suspend fun getGoalForSession(goalId: StudyGoalId): org.krypton.core.domain.study.StudyGoal? {
        return goalRepository.getGoal(goalId)
    }
    
    private suspend fun loadStudyPlan(vaultId: String, goalId: String): StudyData {
        // Check cache first (look for goal-specific data)
        val cached = dataCache.value[vaultId] ?: return StudyData()
        val goalIdObj = StudyGoalId(goalId)
        val cachedSessions = cached.sessions.filter { it.goalId == goalIdObj }
        if (cachedSessions.isNotEmpty()) {
            // Get note paths and session IDs for this goal
            val goalNotePaths = cachedSessions.flatMap { it.notePaths }.toSet()
            val goalSessionIds = cachedSessions.map { it.id }.toSet()
            
            // Return cached data for this goal
            return StudyData(
                sessions = cachedSessions,
                noteSummaries = cached.noteSummaries.filter { it.notePath in goalNotePaths },
                sessionFlashcards = cached.sessionFlashcards.filter { it.sessionId in goalSessionIds },
                sessionResults = cached.sessionResults.filter { it.sessionId in goalSessionIds }
            )
        }
        
        // Load from new storage structure: load all session files for this goal
        val allSessionsData = persistence.loadAllSessionsForGoal(vaultId, goalId)
        
        // Aggregate into StudyData
        val sessions = allSessionsData.values.map { it.session }
        val noteSummaries = allSessionsData.values.flatMap { it.noteSummaries }
        val sessionFlashcards = allSessionsData.values.mapNotNull { it.flashcards }
        val sessionResults = allSessionsData.values.mapNotNull { it.result }
        
        val plan = StudyData(
            sessions = sessions,
            noteSummaries = noteSummaries,
            sessionFlashcards = sessionFlashcards,
            sessionResults = sessionResults
        )
        
        // Update cache
        val currentCache = dataCache.value[vaultId] ?: StudyData()
        val goalNotePaths = plan.sessions.flatMap { it.notePaths }.toSet()
        val goalSessionIds = plan.sessions.map { it.id }.toSet()
        val updatedCache = currentCache.copy(
            sessions = currentCache.sessions.filterNot { it.goalId == goalIdObj } + plan.sessions,
            noteSummaries = currentCache.noteSummaries.filterNot { it.notePath in goalNotePaths } + plan.noteSummaries,
            sessionFlashcards = currentCache.sessionFlashcards.filterNot { it.sessionId in goalSessionIds } + plan.sessionFlashcards,
            sessionResults = currentCache.sessionResults.filterNot { it.sessionId in goalSessionIds } + plan.sessionResults
        )
        updateCache(vaultId, updatedCache)
        
        return plan
    }
    
    private suspend fun loadData(vaultId: String): StudyData {
        // Check cache first
        dataCache.value[vaultId]?.let { return it }
        
        // Load from persistence (legacy format for backward compatibility)
        val data = persistence.loadStudyData(vaultId) ?: StudyData()
        updateCache(vaultId, data)
        return data
    }
    
    private fun updateCacheForGoal(vaultId: String, goalId: String, plan: StudyData) {
        val currentCache = dataCache.value[vaultId] ?: StudyData()
        val goalIdObj = StudyGoalId(goalId)
        val goalNotePaths = plan.sessions.flatMap { it.notePaths }.toSet()
        val goalSessionIds = plan.sessions.map { it.id }.toSet()
        val updatedCache = currentCache.copy(
            sessions = currentCache.sessions.filterNot { it.goalId == goalIdObj } + plan.sessions,
            noteSummaries = currentCache.noteSummaries.filterNot { it.notePath in goalNotePaths } + plan.noteSummaries,
            sessionFlashcards = currentCache.sessionFlashcards.filterNot { it.sessionId in goalSessionIds } + plan.sessionFlashcards,
            sessionResults = currentCache.sessionResults.filterNot { it.sessionId in goalSessionIds } + plan.sessionResults
        )
        updateCache(vaultId, updatedCache)
    }
    
    private fun updateCache(vaultId: String, data: StudyData) {
        dataCache.value = dataCache.value + (vaultId to data)
    }
}

