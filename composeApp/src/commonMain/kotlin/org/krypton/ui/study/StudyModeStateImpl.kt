package org.krypton.ui.study

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.krypton.core.domain.study.*
import org.krypton.util.AppLogger
import org.krypton.util.TimeProvider
import java.util.UUID

/**
 * Implementation of StudyModeState using StateFlow.
 */
class StudyModeStateImpl(
    private val studyGoalRepository: StudyGoalRepository,
    private val studyPlanner: StudyPlanner,
    private val studyScheduler: StudyScheduler,
    private val studyItemRepository: StudyItemRepository,
    private val timeProvider: TimeProvider,
    private val coroutineScope: CoroutineScope,
) : StudyModeState {
    
    private val _state = MutableStateFlow(StudyUiState())
    override val state: StateFlow<StudyUiState> = _state.asStateFlow()
    
    private var currentVaultId: String? = null
    
    private var goalsCollectionJob: kotlinx.coroutines.Job? = null
    
    override fun loadGoals(vaultId: String) {
        // #region agent log
        kotlin.runCatching {
            val logLine = """{"sessionId":"debug-session","runId":"run1","hypothesisId":"F","location":"StudyModeStateImpl.kt:32","message":"loadGoals called","data":{"vaultId":"${vaultId.replace("\"", "\\\"")}","vaultIdLength":${vaultId.length},"vaultIdIsEmpty":${vaultId.isEmpty()},"currentVaultId":"${currentVaultId?.replace("\"", "\\\"") ?: "null"}","jobActive":${goalsCollectionJob?.isActive ?: false},"jobIsNull":${goalsCollectionJob == null}},"timestamp":${System.currentTimeMillis()}}"""
            java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("$logLine\n")
        }
        // #endregion
        
        if (currentVaultId == vaultId && goalsCollectionJob?.isActive == true) {
            // Already loading for this vault
            AppLogger.d("StudyModeState", "Already loading goals for vault: $vaultId")
            // #region agent log
            kotlin.runCatching {
                val logLine = """{"sessionId":"debug-session","runId":"run1","hypothesisId":"F","location":"StudyModeStateImpl.kt:36","message":"loadGoals EARLY RETURN","data":{"vaultId":"${vaultId.replace("\"", "\\\"")}","reason":"already_loading"},"timestamp":${System.currentTimeMillis()}}"""
                java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("$logLine\n")
            }
            // #endregion
            return
        }
        currentVaultId = vaultId
        
        // Cancel previous collection if any
        goalsCollectionJob?.cancel()
        
        // #region agent log
        kotlin.runCatching {
            val logLine = """{"sessionId":"debug-session","runId":"run1","hypothesisId":"F","location":"StudyModeStateImpl.kt:44","message":"loadGoals LAUNCHING JOB","data":{"vaultId":"${vaultId.replace("\"", "\\\"")}","previousJobCancelled":true},"timestamp":${System.currentTimeMillis()}}"""
            java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("$logLine\n")
        }
        // #endregion
        
        goalsCollectionJob = coroutineScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)
                
                AppLogger.d("StudyModeState", "Starting to observe goals for vault: '$vaultId'")
                // #region agent log
                kotlin.runCatching {
                    val logLine = """{"sessionId":"debug-session","runId":"run1","hypothesisId":"D","location":"StudyModeStateImpl.kt:47","message":"Starting flow collection","data":{"vaultId":"${vaultId.replace("\"", "\\\"")}","vaultIdLength":${vaultId.length},"jobActive":${goalsCollectionJob?.isActive ?: false}},"timestamp":${System.currentTimeMillis()}}"""
                    java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("$logLine\n")
                }
                // #endregion
                
                // Start observing changes - the flow will emit whenever the cache updates
                // Use collect instead of collectLatest to ensure we get all updates
                studyGoalRepository.observeGoals(vaultId).collect { goals ->
                    // #region agent log
                    kotlin.runCatching {
                        val goalIds = goals.joinToString(",", "[", "]") { "\"${it.id.value}\"" }
                        val logLine = """{"sessionId":"debug-session","runId":"run1","hypothesisId":"D","location":"StudyModeStateImpl.kt:52","message":"Flow collect received","data":{"vaultId":"${vaultId.replace("\"", "\\\"")}","goalsCount":${goals.size},"goalIds":$goalIds,"currentStateGoalsCount":${_state.value.goals.size}},"timestamp":${System.currentTimeMillis()}}"""
                        java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("$logLine\n")
                    }
                    // #endregion
                    AppLogger.d("StudyModeState", "Flow emitted ${goals.size} goals for vault: '$vaultId'")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        goals = goals,
                        errorMessage = null
                    )
                    // #region agent log
                    kotlin.runCatching {
                        val logLine = """{"sessionId":"debug-session","runId":"run1","hypothesisId":"D","location":"StudyModeStateImpl.kt:60","message":"State updated after flow","data":{"vaultId":"${vaultId.replace("\"", "\\\"")}","newStateGoalsCount":${_state.value.goals.size}},"timestamp":${System.currentTimeMillis()}}"""
                        java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("$logLine\n")
                    }
                    // #endregion
                }
            } catch (e: Exception) {
                AppLogger.e("StudyModeState", "Failed to load goals for vault: $vaultId", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load goals: ${e.message}"
                )
            }
        }
    }
    
    override fun selectGoal(goalId: StudyGoalId) {
        coroutineScope.launch {
            try {
                val goal = studyGoalRepository.getGoal(goalId)
                _state.value = _state.value.copy(
                    selectedGoal = goal,
                    todaySession = null,
                    currentItemIndex = 0,
                    errorMessage = null
                )
            } catch (e: Exception) {
                AppLogger.e("StudyModeState", "Failed to select goal: $goalId", e)
                _state.value = _state.value.copy(
                    errorMessage = "Failed to select goal: ${e.message}"
                )
            }
        }
    }
    
    override fun startTodaySession() {
        val selectedGoal = _state.value.selectedGoal
        if (selectedGoal == null) {
            _state.value = _state.value.copy(errorMessage = "No goal selected")
            return
        }
        
        coroutineScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)
                
                // First, ensure the goal has a study plan
                studyPlanner.planForGoal(selectedGoal)
                
                // Get today's session
                val now = timeProvider.currentTimeMillis()
                val session = studyScheduler.getTodaySession(selectedGoal.id, now)
                
                _state.value = _state.value.copy(
                    isLoading = false,
                    todaySession = session,
                    currentItemIndex = 0,
                    showSessionView = true, // Show session view instead of goals
                    errorMessage = if (session.itemsDue.isEmpty()) "No items due for review" else null
                )
            } catch (e: Exception) {
                AppLogger.e("StudyModeState", "Failed to start session", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to start session: ${e.message}"
                )
            }
        }
    }
    
    override fun rateCurrentItem(rating: ReviewRating) {
        val session = _state.value.todaySession ?: return
        val currentIndex = _state.value.currentItemIndex
        
        if (currentIndex >= session.itemsDue.size) return
        
        val currentItem = session.itemsDue[currentIndex]
        
        coroutineScope.launch {
            try {
                val now = timeProvider.currentTimeMillis()
                studyScheduler.registerReviewResult(currentItem.id, now, rating)
                
                // Move to next item
                moveToNextItem()
            } catch (e: Exception) {
                AppLogger.e("StudyModeState", "Failed to rate item", e)
                _state.value = _state.value.copy(
                    errorMessage = "Failed to rate item: ${e.message}"
                )
            }
        }
    }
    
    override fun moveToNextItem() {
        val session = _state.value.todaySession ?: return
        val nextIndex = _state.value.currentItemIndex + 1
        
        if (nextIndex >= session.itemsDue.size) {
            // Session complete
            endSession()
        } else {
            _state.value = _state.value.copy(
                currentItemIndex = nextIndex,
                errorMessage = null
            )
        }
    }
    
    override fun endSession() {
        _state.value = _state.value.copy(
            todaySession = null,
            currentItemIndex = 0,
            showSessionView = false, // Go back to goals view
            errorMessage = null
        )
    }
    
    override fun createGoal(
        vaultId: String,
        title: String,
        description: String?,
        targetDate: String?,
    ) {
        coroutineScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)
                
                val now = timeProvider.currentTimeMillis()
                val goal = StudyGoal(
                    id = StudyGoalId(UUID.randomUUID().toString()),
                    vaultId = vaultId,
                    title = title,
                    description = description,
                    targetDate = targetDate,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                )
                
                // #region agent log
                kotlin.runCatching {
                    val logLine = """{"sessionId":"debug-session","runId":"run1","hypothesisId":"E","location":"StudyModeStateImpl.kt:177","message":"createGoal BEFORE upsert","data":{"vaultId":"${vaultId.replace("\"", "\\\"")}","goalId":"${goal.id.value}","goalTitle":"${goal.title.replace("\"", "\\\"")}","currentStateGoalsCount":${_state.value.goals.size},"collectionJobActive":${goalsCollectionJob?.isActive ?: false}},"timestamp":${System.currentTimeMillis()}}"""
                    java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("$logLine\n")
                }
                // #endregion
                
                // Ensure flow collection is active before creating goal
                if (goalsCollectionJob?.isActive != true || currentVaultId != vaultId) {
                    AppLogger.d("StudyModeState", "Flow collection not active for vault '$vaultId', starting it...")
                    loadGoals(vaultId)
                }
                
                studyGoalRepository.upsert(goal)
                
                // #region agent log
                kotlin.runCatching {
                    val logLine = """{"sessionId":"debug-session","runId":"run1","hypothesisId":"E","location":"StudyModeStateImpl.kt:190","message":"createGoal AFTER upsert","data":{"vaultId":"${vaultId.replace("\"", "\\\"")}","goalId":"${goal.id.value}","currentStateGoalsCount":${_state.value.goals.size},"collectionJobActive":${goalsCollectionJob?.isActive ?: false}},"timestamp":${System.currentTimeMillis()}}"""
                    java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("$logLine\n")
                }
                // #endregion
                
                // The flow should automatically update, but ensure we're not blocking
                _state.value = _state.value.copy(
                    isLoading = false,
                    selectedGoal = goal,
                    errorMessage = null
                )
                
                AppLogger.d("StudyModeState", "Created goal: ${goal.title}, waiting for flow update...")
            } catch (e: Exception) {
                AppLogger.e("StudyModeState", "Failed to create goal", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to create goal: ${e.message}"
                )
            }
        }
    }
    
    override fun deleteGoal(goalId: StudyGoalId) {
        coroutineScope.launch {
            try {
                studyGoalRepository.deleteGoal(goalId)
                AppLogger.d("StudyModeState", "Deleted goal: $goalId")
                // Clear selection if the deleted goal was selected
                if (_state.value.selectedGoal?.id == goalId) {
                    _state.value = _state.value.copy(selectedGoal = null)
                }
            } catch (e: Exception) {
                AppLogger.e("StudyModeState", "Failed to delete goal: $goalId", e)
                _state.value = _state.value.copy(
                    errorMessage = "Failed to delete goal: ${e.message}"
                )
            }
        }
    }
    
    override fun showCreateGoalDialog() {
        _state.value = _state.value.copy(showCreateGoalDialog = true)
    }
    
    override fun dismissCreateGoalDialog() {
        _state.value = _state.value.copy(showCreateGoalDialog = false)
    }
    
    override fun navigateBackToGoals() {
        _state.value = _state.value.copy(showSessionView = false)
    }
}

