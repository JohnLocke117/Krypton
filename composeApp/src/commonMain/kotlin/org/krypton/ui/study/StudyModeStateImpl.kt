package org.krypton.ui.study

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.krypton.chat.agent.AgentContext
import org.krypton.chat.agent.AgentResult
import org.krypton.chat.agent.SearchNoteAgent
import org.krypton.core.domain.study.*
import org.krypton.data.repository.SettingsRepository
import org.krypton.data.study.StudyData
import org.krypton.data.study.StudyPersistence
import org.krypton.util.AppLogger
import org.krypton.util.TimeProvider
import java.util.UUID

/**
 * Implementation of StudyModeState using StateFlow.
 */
class StudyModeStateImpl(
    private val studyGoalRepository: StudyGoalRepository,
    private val studySessionRepository: StudySessionRepository,
    private val studyCacheRepository: StudyCacheRepository,
    private val studyPlanner: StudyPlanner,
    private val studyRunner: StudyRunner,
    private val searchNoteAgent: SearchNoteAgent,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
    private val coroutineScope: CoroutineScope,
    private val persistence: StudyPersistence,
) : StudyModeState {
    
    private val _state = MutableStateFlow(StudyUiState())
    override val state: StateFlow<StudyUiState> = _state.asStateFlow()
    
    private var currentVaultId: String? = null
    private var goalsCollectionJob: kotlinx.coroutines.Job? = null
    private var sessionsCollectionJob: kotlinx.coroutines.Job? = null
    
    override fun loadGoals(vaultId: String) {
        if (currentVaultId == vaultId && goalsCollectionJob?.isActive == true) {
            AppLogger.d("StudyModeState", "Already loading goals for vault: $vaultId")
            return
        }
        currentVaultId = vaultId
        
        goalsCollectionJob?.cancel()
        
        goalsCollectionJob = coroutineScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)
                
                AppLogger.d("StudyModeState", "Starting to observe goals for vault: '$vaultId'")
                
                studyGoalRepository.observeGoals(vaultId).collect { goals ->
                    AppLogger.d("StudyModeState", "Flow emitted ${goals.size} goals for vault: '$vaultId'")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        goals = goals,
                        errorMessage = null
                    )
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
    
    override fun createGoal(
        vaultId: String,
        title: String,
        description: String?,
        topics: List<String>,
        targetDate: String?,
    ) {
        coroutineScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)
                
                if (vaultId.isBlank()) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "Vault path is empty. Please ensure a vault is open."
                    )
                    return@launch
                }
                
                // Fetch notes using SearchAgent
                val matchedNotes = mutableListOf<String>()
                try {
                    val settings = settingsRepository.settingsFlow.value
                    val agentContext = AgentContext(
                        currentVaultPath = vaultId,
                        settings = settings,
                        currentNotePath = null
                    )
                    
                    // Build search query from title and optional topics
                    val searchQuery = if (topics.isNotEmpty()) {
                        "search my notes for $title about ${topics.joinToString(", ")}"
                    } else {
                        "search my notes for $title"
                    }
                    
                    AppLogger.d("StudyModeState", "Searching notes for goal: $title")
                    val searchResult = searchNoteAgent.execute(searchQuery, emptyList(), agentContext)
                    
                    if (searchResult is AgentResult.NotesFound) {
                        matchedNotes.addAll(searchResult.results.map { it.filePath })
                        AppLogger.d("StudyModeState", "Found ${matchedNotes.size} notes for goal: $title")
                    } else {
                        AppLogger.w("StudyModeState", "SearchAgent did not return NotesFound result")
                    }
                } catch (e: Exception) {
                    AppLogger.w("StudyModeState", "Failed to fetch notes for goal: $title", e)
                    // Continue with goal creation even if note fetching fails
                }
                
                val now = timeProvider.currentTimeMillis()
                val goalId = StudyGoalId(UUID.randomUUID().toString())
                
                // Generate roadmap immediately after fetching notes
                val roadmap = try {
                    if (matchedNotes.isNotEmpty()) {
                        val tempGoal = StudyGoal(
                            id = goalId,
                            vaultId = vaultId,
                            title = title,
                            description = description,
                            topics = topics,
                            matchedNotes = matchedNotes,
                            status = GoalStatus.PENDING,
                            targetDate = targetDate,
                            createdAtMillis = now,
                            updatedAtMillis = now,
                        )
                        studyPlanner.generateRoadmap(tempGoal, matchedNotes)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    AppLogger.w("StudyModeState", "Failed to generate roadmap for goal: $title", e)
                    null
                }
                
                val goal = StudyGoal(
                    id = goalId,
                    vaultId = vaultId,
                    title = title,
                    description = description,
                    topics = topics,
                    matchedNotes = matchedNotes,
                    roadmap = roadmap,
                    status = GoalStatus.PENDING,
                    targetDate = targetDate,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                )
                
                // Save initial GoalData with roadmap (even if no sessions yet)
                val initialGoalData = org.krypton.data.study.GoalData(
                    goal = goal,
                    roadmap = roadmap,
                    sessions = emptyList(),
                    noteSummaries = emptyList(),
                    sessionFlashcards = emptyList(),
                    sessionResults = emptyList()
                )
                persistence.saveGoalData(vaultId, goalId.value, initialGoalData)
                
                // Ensure flow collection is active before creating goal
                if (goalsCollectionJob?.isActive != true || currentVaultId != vaultId) {
                    AppLogger.d("StudyModeState", "Flow collection not active for vault '$vaultId', starting it...")
                    loadGoals(vaultId)
                }
                
                studyGoalRepository.upsert(goal)
                
                _state.value = _state.value.copy(
                    isLoading = false,
                    currentGoal = goal,
                    errorMessage = null,
                    showCreateGoalDialog = false
                )
                
                AppLogger.d("StudyModeState", "Created goal: ${goal.title} with ${topics.size} topics and ${matchedNotes.size} matched notes")
            } catch (e: Exception) {
                AppLogger.e("StudyModeState", "Failed to create goal", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to create goal: ${e.message}"
                )
            }
        }
    }
    
    override fun planGoal(goalId: StudyGoalId) {
        coroutineScope.launch {
            try {
                val goal = studyGoalRepository.getGoal(goalId)
                    ?: run {
                        _state.value = _state.value.copy(
                            errorMessage = "Goal not found: $goalId"
                        )
                        return@launch
                    }
                
                if (goal.vaultId.isBlank()) {
                    _state.value = _state.value.copy(
                        errorMessage = "Vault path is empty. Please ensure a vault is open."
                    )
                    return@launch
                }
                
                _state.value = _state.value.copy(
                    planningInProgress = true,
                    errorMessage = null
                )
                
                AppLogger.i("StudyModeState", "Planning goal: ${goal.title}")
                
                studyPlanner.planForGoal(goal)
                
                // Refresh sessions for the goal
                loadSessionsForGoal(goalId)
                
                _state.value = _state.value.copy(
                    planningInProgress = false,
                    errorMessage = null
                )
                
                AppLogger.i("StudyModeState", "Successfully planned goal: ${goal.title}")
            } catch (e: Exception) {
                AppLogger.e("StudyModeState", "Failed to plan goal: $goalId", e)
                _state.value = _state.value.copy(
                    planningInProgress = false,
                    errorMessage = "Failed to plan goal: ${e.message}"
                )
            }
        }
    }
    
    override fun createStudyPlan(goalId: StudyGoalId) {
        coroutineScope.launch {
            try {
                val goal = studyGoalRepository.getGoal(goalId)
                    ?: run {
                        _state.value = _state.value.copy(
                            toastMessage = ToastMessage(
                                text = "Goal not found",
                                type = ToastType.ERROR
                            )
                        )
                        return@launch
                    }
                
                if (goal.vaultId.isBlank()) {
                    _state.value = _state.value.copy(
                        toastMessage = ToastMessage(
                            text = "Vault path is empty. Please ensure a vault is open.",
                            type = ToastType.ERROR
                        )
                    )
                    return@launch
                }
                
                // Set loading state for this goal
                _state.value = _state.value.copy(
                    goalLoadingStates = _state.value.goalLoadingStates + (goalId to true),
                    planningInProgress = true,
                    errorMessage = null
                )
                
                // Load roadmap from persistence if not in goal
                var updatedGoal = goal
                if (goal.roadmap == null) {
                    val roadmap = persistence.loadRoadmap(goal.vaultId, goal.id.value)
                    if (roadmap != null) {
                        updatedGoal = goal.copy(roadmap = roadmap)
                        studyGoalRepository.upsert(updatedGoal)
                    }
                }
                
                AppLogger.i("StudyModeState", "Creating sessions for goal: ${updatedGoal.title}")
                
                // Create sessions (roadmap should already exist from goal creation)
                studyPlanner.planForGoal(updatedGoal)
                
                // Retry loading sessions with increasing delays until we find them
                var sessions = emptyList<StudySession>()
                var retryCount = 0
                val maxRetries = 5
                
                while (sessions.isEmpty() && retryCount < maxRetries) {
                    kotlinx.coroutines.delay(300L * (retryCount + 1)) // 300ms, 600ms, 900ms, etc.
                    
                    // Directly load goal data from persistence (bypassing cache)
                    val goalData = persistence.loadGoalData(updatedGoal.vaultId, goalId.value)
                    sessions = goalData?.sessions?.sortedBy { it.order } ?: emptyList()
                    
                    AppLogger.d("StudyModeState", "Attempt ${retryCount + 1}: Loaded ${sessions.size} sessions from persistence for goal: $goalId")
                    retryCount++
                }
                
                if (sessions.isEmpty()) {
                    AppLogger.w("StudyModeState", "No sessions found after $maxRetries retries! Files might not be written yet.")
                } else {
                    AppLogger.i("StudyModeState", "Successfully loaded ${sessions.size} sessions from persistence for goal: $goalId")
                }
                
                // Update state with loaded sessions and goal immediately
                // Only update if this is still the current goal (prevent cross-contamination)
                if (_state.value.currentGoal?.id == goalId) {
                    _state.value = _state.value.copy(
                        sessions = sessions, // Only this goal's sessions
                        currentGoal = updatedGoal
                    )
                }
                
                // Clear loading state and show success toast
                _state.value = _state.value.copy(
                    goalLoadingStates = _state.value.goalLoadingStates - goalId,
                    planningInProgress = false,
                    toastMessage = ToastMessage(
                        text = "Sessions created successfully!",
                        type = ToastType.SUCCESS
                    )
                )
                
                // Auto-dismiss toast after 3 seconds
                kotlinx.coroutines.delay(3000)
                _state.value = _state.value.copy(
                    toastMessage = null
                )
                
                AppLogger.i("StudyModeState", "Successfully created sessions for goal: ${updatedGoal.title}")
            } catch (e: Exception) {
                AppLogger.e("StudyModeState", "Failed to create sessions: $goalId", e)
                _state.value = _state.value.copy(
                    goalLoadingStates = _state.value.goalLoadingStates - goalId,
                    planningInProgress = false,
                    toastMessage = ToastMessage(
                        text = "Failed to create sessions: ${e.message}",
                        type = ToastType.ERROR
                    )
                )
                
                // Auto-dismiss error toast after 5 seconds
                kotlinx.coroutines.delay(5000)
                _state.value = _state.value.copy(
                    toastMessage = null
                )
            }
        }
    }
    
    private suspend fun saveRoadmapToKrypton(goal: StudyGoal, roadmap: String) {
        try {
            val vaultPath = goal.vaultId
            val kryptonDir = if (vaultPath.endsWith("/")) {
                "$vaultPath.krypton"
            } else {
                "$vaultPath/.krypton"
            }
            
            val roadmapFileName = "roadmap-${goal.id.value}.md"
            val roadmapPath = "$kryptonDir/$roadmapFileName"
            
            // Ensure .krypton directory exists
            val kryptonDirFile = java.io.File(kryptonDir)
            if (!kryptonDirFile.exists()) {
                kryptonDirFile.mkdirs()
            }
            
            // Write roadmap file
            java.io.File(roadmapPath).writeText(roadmap)
            AppLogger.i("StudyModeState", "Saved roadmap to: $roadmapPath")
        } catch (e: Exception) {
            AppLogger.e("StudyModeState", "Failed to save roadmap", e)
            // Don't throw - roadmap generation succeeded even if saving failed
        }
    }
    
    override fun selectGoal(goalId: StudyGoalId) {
        coroutineScope.launch {
            try {
                var goal = studyGoalRepository.getGoal(goalId)
                if (goal != null) {
                    // Load roadmap from persistence if not already in goal
                    if (goal.roadmap == null) {
                        val roadmap = persistence.loadRoadmap(goal.vaultId, goal.id.value)
                        if (roadmap != null) {
                            goal = goal.copy(roadmap = roadmap)
                            // Update goal in repository
                            studyGoalRepository.upsert(goal)
                        }
                    }
                    
                    // Load goal data immediately (includes roadmap and sessions)
                    val goalData = persistence.loadGoalData(goal.vaultId, goal.id.value)
                    val sessions = goalData?.sessions?.sortedBy { it.order } ?: emptyList()
                    
                    // Update goal with roadmap from GoalData if available
                    val updatedGoal = if (goalData?.roadmap != null && goal.roadmap == null) {
                        goal.copy(roadmap = goalData.roadmap)
                    } else {
                        goal
                    }
                    
                    // Always show ONLY sessions for the current goal (no cross-contamination)
                    _state.value = _state.value.copy(
                        currentGoal = updatedGoal,
                        sessions = sessions, // Only this goal's sessions
                        errorMessage = null
                    )
                    
                    // Start flow collection to observe future changes
                    loadSessionsForGoal(goalId)
                } else {
                    _state.value = _state.value.copy(
                        errorMessage = "Goal not found: $goalId"
                    )
                }
            } catch (e: Exception) {
                AppLogger.e("StudyModeState", "Failed to select goal: $goalId", e)
                _state.value = _state.value.copy(
                    errorMessage = "Failed to select goal: ${e.message}"
                )
            }
        }
    }
    
    override fun viewRoadmap(goalId: StudyGoalId) {
        coroutineScope.launch {
            try {
                var goal = studyGoalRepository.getGoal(goalId)
                if (goal != null) {
                    // Load roadmap from persistence if not already in goal
                    if (goal.roadmap == null) {
                        val roadmap = persistence.loadRoadmap(goal.vaultId, goal.id.value)
                        if (roadmap != null) {
                            goal = goal.copy(roadmap = roadmap)
                            // Update goal in repository
                            studyGoalRepository.upsert(goal)
                        }
                    }
                    
                    _state.value = _state.value.copy(
                        currentGoal = goal,
                        showRoadmap = true,
                        showSession = false,
                        errorMessage = null
                    )
                    loadSessionsForGoal(goalId)
                } else {
                    _state.value = _state.value.copy(
                        errorMessage = "Goal not found: $goalId"
                    )
                }
            } catch (e: Exception) {
                AppLogger.e("StudyModeState", "Failed to view roadmap: $goalId", e)
                _state.value = _state.value.copy(
                    errorMessage = "Failed to view roadmap: ${e.message}"
                )
            }
        }
    }
    
    override fun prepareSession(sessionId: StudySessionId) {
        coroutineScope.launch {
            try {
                val session = studySessionRepository.getSession(sessionId)
                    ?: run {
                        _state.value = _state.value.copy(
                            errorMessage = "Session not found: $sessionId"
                        )
                        return@launch
                    }
                
                _state.value = _state.value.copy(
                    preparingSession = true,
                    currentSession = session,
                    errorMessage = null
                )
                
                AppLogger.i("StudyModeState", "Preparing session: ${session.topic}")
                
                studyRunner.prepareSession(sessionId)
                
                // Load summaries and flashcards
                loadSessionData(sessionId)
                
                _state.value = _state.value.copy(
                    preparingSession = false,
                    showSession = true,
                    showRoadmap = false
                )
                
                AppLogger.i("StudyModeState", "Session prepared: ${session.topic}")
            } catch (e: Exception) {
                AppLogger.e("StudyModeState", "Failed to prepare session: $sessionId", e)
                _state.value = _state.value.copy(
                    preparingSession = false,
                    errorMessage = "Failed to prepare session: ${e.message}"
                )
            }
        }
    }
    
    override fun startQuiz(sessionId: StudySessionId, flashcardCount: Int) {
        coroutineScope.launch {
            try {
                val session = studySessionRepository.getSession(sessionId)
                    ?: run {
                        _state.value = _state.value.copy(
                            errorMessage = "Session not found: $sessionId"
                        )
                        return@launch
                    }
                
                // Ensure session data is loaded
                loadSessionData(sessionId)
                
                val flashcards = _state.value.sessionFlashcards
                    ?: run {
                        _state.value = _state.value.copy(
                            errorMessage = "Flashcards not found for session. Please prepare session first."
                        )
                        return@launch
                    }
                
                // Get quiz flashcard count from settings (or use all if less than setting)
                val settings = settingsRepository.settingsFlow.value
                val quizCount = if (flashcardCount > 0) {
                    flashcardCount // Legacy support
                } else {
                    minOf(settings.study.quizFlashcardCount, flashcards.size)
                }
                
                // Select flashcards (take first quizCount, or all if less than quizCount)
                val selectedFlashcards = flashcards.take(quizCount)
                
                if (selectedFlashcards.isEmpty()) {
                    _state.value = _state.value.copy(
                        errorMessage = "No flashcards available for quiz"
                    )
                    return@launch
                }
                
                val quizState = QuizState(
                    sessionId = sessionId,
                    flashcards = selectedFlashcards,
                    currentIndex = 0,
                    answers = emptyMap()
                )
                
                _state.value = _state.value.copy(
                    quizState = quizState,
                    errorMessage = null
                )
                
                AppLogger.i("StudyModeState", "Started quiz for session: ${session.topic} (${selectedFlashcards.size} flashcards)")
            } catch (e: Exception) {
                AppLogger.e("StudyModeState", "Failed to start quiz: $sessionId", e)
                _state.value = _state.value.copy(
                    errorMessage = "Failed to start quiz: ${e.message}"
                )
            }
        }
    }
    
    override fun submitQuizAnswer(sessionId: StudySessionId, flashcardIndex: Int, isCorrect: Boolean) {
        val quizState = _state.value.quizState
            ?: run {
                AppLogger.w("StudyModeState", "No active quiz")
                return
            }
        
        if (quizState.sessionId != sessionId) {
            AppLogger.w("StudyModeState", "Quiz session ID mismatch")
            return
        }
        
        val updatedAnswers = quizState.answers + (flashcardIndex to isCorrect)
        val updatedQuizState = quizState.copy(answers = updatedAnswers)
        
        _state.value = _state.value.copy(quizState = updatedQuizState)
        
        AppLogger.d("StudyModeState", "Submitted answer for flashcard $flashcardIndex: $isCorrect")
    }
    
    override fun moveToNextQuestion(sessionId: StudySessionId) {
        val quizState = _state.value.quizState
            ?: return
        
        if (quizState.sessionId != sessionId) {
            return
        }
        
        val nextIndex = quizState.currentIndex + 1
        if (nextIndex < quizState.flashcards.size) {
            _state.value = _state.value.copy(
                quizState = quizState.copy(currentIndex = nextIndex)
            )
        }
    }
    
    override fun completeQuiz(sessionId: StudySessionId) {
        coroutineScope.launch {
            try {
                val quizState = _state.value.quizState
                    ?: run {
                        _state.value = _state.value.copy(
                            errorMessage = "No active quiz"
                        )
                        return@launch
                    }
                
                if (quizState.sessionId != sessionId) {
                    _state.value = _state.value.copy(
                        errorMessage = "Quiz session ID mismatch"
                    )
                    return@launch
                }
                
                // Calculate result
                val result = studyRunner.runQuiz(
                    sessionId = sessionId,
                    flashcardCount = quizState.flashcards.size,
                    answers = quizState.answers
                )
                
                // Complete session
                studyRunner.completeSession(sessionId, result)
                
                // Refresh sessions to get updated status
                val session = studySessionRepository.getSession(sessionId)
                if (session != null) {
                    loadSessionsForGoal(session.goalId)
                }
                
                _state.value = _state.value.copy(
                    quizState = null,
                    errorMessage = if (result.score >= 7) null else "Quiz score: ${result.score}/10 (need ≥7 to complete)"
                )
                
                AppLogger.i("StudyModeState", "Quiz completed: score ${result.score}/10")
            } catch (e: Exception) {
                AppLogger.e("StudyModeState", "Failed to complete quiz: $sessionId", e)
                _state.value = _state.value.copy(
                    errorMessage = "Failed to complete quiz: ${e.message}"
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
                if (_state.value.currentGoal?.id == goalId) {
                    _state.value = _state.value.copy(
                        currentGoal = null,
                        showRoadmap = false,
                        showSession = false
                    )
                }
            } catch (e: Exception) {
                AppLogger.e("StudyModeState", "Failed to delete goal: $goalId", e)
                _state.value = _state.value.copy(
                    errorMessage = "Failed to delete goal: ${e.message}"
                )
            }
        }
    }
    
    override fun navigateBackToGoals() {
        _state.value = _state.value.copy(
            showRoadmap = false,
            showSession = false,
            currentGoal = null,
            currentSession = null,
            quizState = null
        )
    }
    
    override fun showCreateGoalDialog() {
        _state.value = _state.value.copy(showCreateGoalDialog = true)
    }
    
    override fun dismissCreateGoalDialog() {
        _state.value = _state.value.copy(showCreateGoalDialog = false)
    }
    
    private fun loadSessionsForGoal(goalId: StudyGoalId) {
        // Always cancel previous job to force reload from persistence
        sessionsCollectionJob?.cancel()
        
        sessionsCollectionJob = coroutineScope.launch {
            try {
                studySessionRepository.observeSessionsForGoal(goalId).collect { sessions ->
                    // Don't overwrite state with empty sessions if we already have sessions FOR THIS GOAL
                    // This prevents the flow from clearing sessions that were just loaded
                    val existingSessionsForGoal = _state.value.sessions.filter { it.goalId == goalId }
                    if (sessions.isEmpty() && existingSessionsForGoal.isNotEmpty()) {
                        return@collect
                    }
                    
                    // Only update if this is the current goal (prevent cross-contamination)
                    // IMPORTANT: Replace ALL sessions, not merge, to prevent cross-contamination
                    if (_state.value.currentGoal?.id == goalId) {
                        _state.value = _state.value.copy(sessions = sessions) // Replace, don't merge
                    }
                    
                    AppLogger.d("StudyModeState", "Loaded ${sessions.size} sessions for goal: $goalId")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Don't log cancellation as error - it's expected when switching goals
                AppLogger.d("StudyModeState", "Session loading cancelled for goal: $goalId")
                throw e
            } catch (e: Exception) {
                AppLogger.e("StudyModeState", "Failed to load sessions for goal: $goalId", e)
            }
        }
    }
    
    private suspend fun loadSessionData(sessionId: StudySessionId) {
        try {
            val session = studySessionRepository.getSession(sessionId)
                ?: return
            
            // Load summaries for notes in session
            val summaries = mutableMapOf<String, NoteSummary>()
            
            for (notePath in session.notePaths) {
                val summary = studyCacheRepository.getNoteSummary(notePath)
                if (summary != null) {
                    summaries[notePath] = summary
                }
            }
            
            // Load flashcards
            val flashcards = studyCacheRepository.getSessionFlashcards(sessionId)
            
            _state.value = _state.value.copy(
                sessionSummaries = summaries,
                sessionFlashcards = flashcards?.flashcards
            )
        } catch (e: Exception) {
            AppLogger.e("StudyModeState", "Failed to load session data: $sessionId", e)
        }
    }
}
