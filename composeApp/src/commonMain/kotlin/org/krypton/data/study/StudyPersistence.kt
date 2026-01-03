package org.krypton.data.study

/**
 * Platform-agnostic interface for persisting study data.
 * 
 * Study data is stored in `.krypton` directory:
 * - Goals: `.krypton/goals.json` (all goals in one file)
 * - Goal data: `.krypton/goals/{goalId}.json` (one JSON file per goal containing goal + all sessions + related data)
 */
interface StudyPersistence {
    /**
     * Loads study data for a vault (legacy method for backward compatibility).
     * 
     * @param vaultId Platform-specific vault identifier (file path or URI)
     * @return Study data if found, null if file doesn't exist or on error
     */
    suspend fun loadStudyData(vaultId: String): StudyData?
    
    /**
     * Saves study data for a vault (legacy method for backward compatibility).
     * 
     * @param vaultId Platform-specific vault identifier (file path or URI)
     * @param data Study data to save
     * @return true if successful, false on error
     */
    suspend fun saveStudyData(vaultId: String, data: StudyData): Boolean
    
    /**
     * Loads all goals for a vault.
     * 
     * @param vaultId Platform-specific vault identifier
     * @return GoalsData containing all goals, or null if not found
     */
    suspend fun loadGoals(vaultId: String): GoalsData?
    
    /**
     * Saves all goals for a vault.
     * 
     * @param vaultId Platform-specific vault identifier
     * @param data GoalsData containing all goals
     * @return true if successful, false on error
     */
    suspend fun saveGoals(vaultId: String, data: GoalsData): Boolean
    
    /**
     * Loads study plan data for a specific goal.
     * 
     * @param vaultId Platform-specific vault identifier
     * @param goalId ID of the goal
     * @return StudyData containing sessions, summaries, flashcards, and results for this goal, or null if not found
     */
    suspend fun loadStudyPlan(vaultId: String, goalId: String): StudyData?
    
    /**
     * Saves study plan data for a specific goal.
     * 
     * @param vaultId Platform-specific vault identifier
     * @param goalId ID of the goal
     * @param data StudyData containing sessions, summaries, flashcards, and results for this goal
     * @return true if successful, false on error
     */
    suspend fun saveStudyPlan(vaultId: String, goalId: String, data: StudyData): Boolean
    
    /**
     * Loads session data from `.krypton/goals/{goalId}/{sessionId}.json`
     * 
     * @param vaultId Platform-specific vault identifier
     * @param goalId ID of the goal
     * @param sessionId ID of the session
     * @return SessionData if found, null otherwise
     */
    suspend fun loadSessionData(vaultId: String, goalId: String, sessionId: String): SessionData?
    
    /**
     * Saves session data to `.krypton/goals/{goalId}/{sessionId}.json`
     * 
     * @param vaultId Platform-specific vault identifier
     * @param goalId ID of the goal
     * @param sessionId ID of the session
     * @param data SessionData to save
     * @return true if successful, false on error
     */
    suspend fun saveSessionData(vaultId: String, goalId: String, sessionId: String, data: SessionData): Boolean
    
    /**
     * Loads all session data files for a goal from `.krypton/goals/{goalId}/`
     * 
     * @param vaultId Platform-specific vault identifier
     * @param goalId ID of the goal
     * @return Map of sessionId to SessionData
     */
    suspend fun loadAllSessionsForGoal(vaultId: String, goalId: String): Map<String, SessionData>
    
    /**
     * Saves roadmap document to `.krypton/goals/{goalId}/roadmap.md`
     * 
     * @param vaultId Platform-specific vault identifier
     * @param goalId ID of the goal
     * @param roadmapContent Markdown content of the roadmap
     * @return true if successful, false on error
     */
    suspend fun saveRoadmap(vaultId: String, goalId: String, roadmapContent: String): Boolean
    
    /**
     * Loads roadmap document from `.krypton/goals/{goalId}/roadmap.md`
     * 
     * @param vaultId Platform-specific vault identifier
     * @param goalId ID of the goal
     * @return Roadmap content if found, null otherwise
     */
    suspend fun loadRoadmap(vaultId: String, goalId: String): String?
    
    /**
     * Loads complete goal data from `.krypton/goals/{goalId}.json`
     * This includes the goal, all its sessions, summaries, flashcards, and results.
     * 
     * @param vaultId Platform-specific vault identifier
     * @param goalId ID of the goal
     * @return GoalData if found, null otherwise
     */
    suspend fun loadGoalData(vaultId: String, goalId: String): GoalData?
    
    /**
     * Saves complete goal data to `.krypton/goals/{goalId}.json`
     * This includes the goal, all its sessions, summaries, flashcards, and results.
     * 
     * @param vaultId Platform-specific vault identifier
     * @param goalId ID of the goal
     * @param data GoalData to save
     * @return true if successful, false on error
     */
    suspend fun saveGoalData(vaultId: String, goalId: String, data: GoalData): Boolean
}

