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
import org.krypton.data.study.GoalData

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
                
                // Load goal data (includes all sessions)
                val goalData = persistence.loadGoalData(goal.vaultId, goalId.value)
                val plan = if (goalData != null) {
                    StudyData(
                        sessions = goalData.sessions,
                        noteSummaries = goalData.noteSummaries,
                        sessionFlashcards = goalData.sessionFlashcards,
                        sessionResults = goalData.sessionResults
                    )
                } else {
                    StudyData()
                }
                
                // Update cache
                val currentCache = dataCache.value[goal.vaultId] ?: StudyData()
                val updatedCache = currentCache.copy(
                    sessions = currentCache.sessions.filterNot { it.goalId == goalId } + plan.sessions
                )
                dataCache.value = dataCache.value + (goal.vaultId to updatedCache)
                
                val sortedSessions = plan.sessions.sortedBy { it.order }
                emit(sortedSessions)
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
        
        // Load goal data and find session
        val goalData = persistence.loadGoalData(goal.vaultId, goal.id.value)
        return goalData?.sessions?.firstOrNull { it.id == id }
    }
    
    override suspend fun upsertSession(session: StudySession) {
        try {
            // Get goal to find vault
            val goal = getGoalForSession(session.goalId) ?: run {
                AppLogger.e("StudySessionRepository", "Goal not found for session: ${session.id}")
                return
            }
            
            // Load existing goal data or create new
            val existingGoalData = persistence.loadGoalData(goal.vaultId, goal.id.value)
            val updatedSessions = if (existingGoalData != null) {
                existingGoalData.sessions.filterNot { it.id == session.id } + session
            } else {
                listOf(session)
            }
            
            // Create or update GoalData (preserve roadmap from existing data or goal)
            val roadmapToUse = existingGoalData?.roadmap ?: goal.roadmap
            val goalData = GoalData(
                goal = goal,
                roadmap = roadmapToUse,
                sessions = updatedSessions,
                noteSummaries = existingGoalData?.noteSummaries ?: emptyList(),
                sessionFlashcards = existingGoalData?.sessionFlashcards ?: emptyList(),
                sessionResults = existingGoalData?.sessionResults ?: emptyList()
            )
            
            if (persistence.saveGoalData(goal.vaultId, goal.id.value, goalData)) {
                // Update cache
                val currentPlan = StudyData(
                    sessions = goalData.sessions,
                    noteSummaries = goalData.noteSummaries,
                    sessionFlashcards = goalData.sessionFlashcards,
                    sessionResults = goalData.sessionResults
                )
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
            
            // Load existing goal data and update session
            val existingGoalData = persistence.loadGoalData(goal.vaultId, goal.id.value)
            val updatedSessions = if (existingGoalData != null) {
                existingGoalData.sessions.map { if (it.id == id) updatedSession else it }
            } else {
                listOf(updatedSession)
            }
            
            // Create or update GoalData (preserve roadmap)
            val roadmapToUse = existingGoalData?.roadmap ?: goal.roadmap
            val goalData = GoalData(
                goal = goal,
                roadmap = roadmapToUse,
                sessions = updatedSessions,
                noteSummaries = existingGoalData?.noteSummaries ?: emptyList(),
                sessionFlashcards = existingGoalData?.sessionFlashcards ?: emptyList(),
                sessionResults = existingGoalData?.sessionResults ?: emptyList()
            )
            
            if (persistence.saveGoalData(goal.vaultId, goal.id.value, goalData)) {
                // Update cache
                val currentPlan = StudyData(
                    sessions = goalData.sessions,
                    noteSummaries = goalData.noteSummaries,
                    sessionFlashcards = goalData.sessionFlashcards,
                    sessionResults = goalData.sessionResults
                )
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
        
        // Load from new storage structure: load goal data file
        val goalData = persistence.loadGoalData(vaultId, goalId)
        
        val plan = if (goalData != null) {
            StudyData(
                sessions = goalData.sessions,
                noteSummaries = goalData.noteSummaries,
                sessionFlashcards = goalData.sessionFlashcards,
                sessionResults = goalData.sessionResults
            )
        } else {
            StudyData()
        }
        
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

