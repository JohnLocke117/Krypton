package org.krypton.data.study

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.krypton.core.domain.study.StudyGoal
import org.krypton.core.domain.study.StudyGoalId
import org.krypton.core.domain.study.StudyGoalRepository
import org.krypton.util.AppLogger

/**
 * Implementation of StudyGoalRepository using StudyPersistence.
 */
class StudyGoalRepositoryImpl(
    private val persistence: StudyPersistence,
) : StudyGoalRepository {
    
    // In-memory cache of study data per vault
    private val dataCache = MutableStateFlow<Map<String, StudyData>>(emptyMap())
    
    // Coroutine scope for async operations
    private val scope = CoroutineScope(Dispatchers.Default)
    
    override fun observeGoals(vaultId: String): Flow<List<StudyGoal>> {
        AppLogger.d("StudyGoalRepository", "observeGoals called for vault: '$vaultId'")
        // #region agent log
        kotlin.runCatching {
            val cacheKeys = dataCache.value.keys.joinToString(",", "[", "]") { "\"${it.replace("\"", "\\\"")}\"" }
            val currentData = dataCache.value[vaultId]
            val currentGoals = currentData?.goals?.filter { it.vaultId == vaultId } ?: emptyList()
            val logLine = """{"sessionId":"debug-session","runId":"run1","hypothesisId":"B","location":"StudyGoalRepositoryImpl.kt:26","message":"observeGoals called","data":{"vaultId":"${vaultId.replace("\"", "\\\"")}","vaultIdLength":${vaultId.length},"cacheSize":${dataCache.value.size},"cacheKeys":$cacheKeys,"currentGoalsCount":${currentGoals.size},"hasCachedData":${currentData != null}},"timestamp":${System.currentTimeMillis()}}"""
            java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("$logLine\n")
        }
        // #endregion
        
        // Load initial data from persistence if cache is empty for this vault
        // Note: This is done asynchronously, so the flow will emit empty list first, then update when data loads
        if (dataCache.value[vaultId] == null) {
            scope.launch {
                try {
                    // Try loading from new goals.json, fallback to legacy study-data.json
                    val goalsData = persistence.loadGoals(vaultId)
                    val goals = goalsData?.goals ?: emptyList()
                    val data = StudyData(goals = goals)
                    updateCache(vaultId, data)
                    AppLogger.d("StudyGoalRepository", "Loaded initial data from persistence for vault '$vaultId': ${goals.size} goals")
                } catch (e: Exception) {
                    AppLogger.e("StudyGoalRepository", "Failed to load initial data for vault '$vaultId'", e)
                }
            }
        }
        
        // StateFlow.map emits the current value immediately when collected, then emits on changes
        return dataCache.map { cache ->
            val cachedData = cache[vaultId]
            val allGoals = cachedData?.goals ?: emptyList()
            val goals = allGoals.filter { it.vaultId == vaultId }
            
            // #region agent log
            kotlin.runCatching {
                val goalIds = goals.joinToString(",", "[", "]") { "\"${it.id.value}\"" }
                val cacheKeys = cache.keys.joinToString(",", "[", "]") { "\"${it.replace("\"", "\\\"")}\"" }
                val cachedGoalIds = allGoals.joinToString(",", "[", "]") { "\"${it.id.value}\"" }
                val logLine = """{"sessionId":"debug-session","runId":"run1","hypothesisId":"B","location":"StudyGoalRepositoryImpl.kt:50","message":"Flow map emitting","data":{"vaultId":"${vaultId.replace("\"", "\\\"")}","goalsCount":${goals.size},"allGoalsCount":${allGoals.size},"goalIds":$goalIds,"cachedGoalIds":$cachedGoalIds,"cacheSize":${cache.size},"cacheKeys":$cacheKeys,"cacheHasVaultId":${cache.containsKey(vaultId)},"cachedDataIsNull":${cachedData == null}},"timestamp":${System.currentTimeMillis()}}"""
                java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("$logLine\n")
            }
            // #endregion
            AppLogger.d("StudyGoalRepository", "Flow emitting ${goals.size} goals for vault '$vaultId' (cache has ${cache.size} vaults: ${cache.keys})")
            goals
        }.distinctUntilChanged()
    }
    
    override suspend fun getGoal(id: StudyGoalId): StudyGoal? {
        return dataCache.value.values
            .flatMap { it.goals }
            .firstOrNull { it.id == id }
    }
    
    override suspend fun upsert(goal: StudyGoal) {
        try {
            AppLogger.d("StudyGoalRepository", "Upserting goal: ${goal.title} for vault: ${goal.vaultId}")
            val currentGoals = loadGoals(goal.vaultId)
            val updatedGoals = currentGoals.goals.filterNot { it.id == goal.id } + goal
            val goalsData = GoalsData(goals = updatedGoals)
            
            AppLogger.d("StudyGoalRepository", "Saving ${updatedGoals.size} goals to persistence")
            if (persistence.saveGoals(goal.vaultId, goalsData)) {
                // Update cache - this will trigger the flow to emit
                val currentData = loadData(goal.vaultId)
                val updatedData = currentData.copy(goals = updatedGoals)
                updateCache(goal.vaultId, updatedData)
                AppLogger.d("StudyGoalRepository", "Cache updated, flow should emit")
            } else {
                AppLogger.e("StudyGoalRepository", "Failed to save goal: ${goal.id}")
            }
        } catch (e: Exception) {
            AppLogger.e("StudyGoalRepository", "Error upserting goal: ${goal.id}", e)
        }
    }
    
    override suspend fun deleteGoal(id: StudyGoalId) {
        try {
            val goal = getGoal(id) ?: return
            val currentGoals = loadGoals(goal.vaultId)
            val updatedGoals = currentGoals.goals.filterNot { it.id == id }
            val goalsData = GoalsData(goals = updatedGoals)
            
            if (persistence.saveGoals(goal.vaultId, goalsData)) {
                // Update cache
                val currentData = loadData(goal.vaultId)
                val updatedData = currentData.copy(goals = updatedGoals)
                updateCache(goal.vaultId, updatedData)
            } else {
                AppLogger.e("StudyGoalRepository", "Failed to delete goal: $id")
            }
        } catch (e: Exception) {
            AppLogger.e("StudyGoalRepository", "Error deleting goal: $id", e)
        }
    }
    
    private suspend fun loadGoals(vaultId: String): GoalsData {
        // Try loading from new goals.json, fallback to legacy study-data.json
        val goalsData = persistence.loadGoals(vaultId)
        if (goalsData != null) {
            return goalsData
        }
        // Fallback to legacy format
        val legacyData = persistence.loadStudyData(vaultId)
        return GoalsData(goals = legacyData?.goals ?: emptyList())
    }
    
    private suspend fun loadData(vaultId: String): StudyData {
        // Check cache first
        dataCache.value[vaultId]?.let { return it }
        
        // Load from persistence (try new format first, then legacy)
        val goalsData = persistence.loadGoals(vaultId)
        val goals = goalsData?.goals ?: emptyList()
        val data = StudyData(goals = goals)
        updateCache(vaultId, data)
        return data
    }
    
    private fun updateCache(vaultId: String, data: StudyData) {
        // #region agent log
        kotlin.runCatching {
            val goalIds = data.goals.joinToString(",", "[", "]") { "\"${it.id.value}\"" }
            val cacheKeys = dataCache.value.keys.joinToString(",", "[", "]") { "\"${it.replace("\"", "\\\"")}\"" }
            val logLine = """{"sessionId":"debug-session","runId":"run1","hypothesisId":"C","location":"StudyGoalRepositoryImpl.kt:85","message":"updateCache BEFORE","data":{"vaultId":"${vaultId.replace("\"", "\\\"")}","vaultIdLength":${vaultId.length},"goalsCount":${data.goals.size},"goalIds":$goalIds,"cacheSizeBefore":${dataCache.value.size},"cacheKeysBefore":$cacheKeys},"timestamp":${System.currentTimeMillis()}}"""
            java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("$logLine\n")
        }
        // #endregion
        
        val currentCache = dataCache.value.toMutableMap()
        currentCache[vaultId] = data
        dataCache.value = currentCache
        
        // #region agent log
        kotlin.runCatching {
            val cacheKeysAfter = dataCache.value.keys.joinToString(",", "[", "]") { "\"${it.replace("\"", "\\\"")}\"" }
            val logLine = """{"sessionId":"debug-session","runId":"run1","hypothesisId":"C","location":"StudyGoalRepositoryImpl.kt:95","message":"updateCache AFTER","data":{"vaultId":"${vaultId.replace("\"", "\\\"")}","goalsCount":${data.goals.size},"cacheSizeAfter":${dataCache.value.size},"cacheKeysAfter":$cacheKeysAfter},"timestamp":${System.currentTimeMillis()}}"""
            java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("$logLine\n")
        }
        // #endregion
        
        AppLogger.d("StudyGoalRepository", "Updated cache for vault $vaultId with ${data.goals.size} goals")
    }
}

