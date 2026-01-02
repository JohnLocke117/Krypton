package org.krypton.core.domain.study

/**
 * Service for planning study sessions by scanning notes and generating study items.
 */
interface StudyPlanner {
    /**
     * Initialize or refresh a goal's items by scanning notes/flashcards in the vault.
     * Should be idempotent - can be called multiple times to update the study plan.
     * 
     * @param goal The study goal to plan for
     */
    suspend fun planForGoal(goal: StudyGoal)
    
    /**
     * Generates a brief roadmap (1-2 paragraphs) for a goal using LLM.
     * 
     * @param goal The study goal
     * @param notes List of matched note paths
     * @return Generated roadmap text
     */
    suspend fun generateRoadmap(goal: StudyGoal, notes: List<String>): String
}

