package org.krypton.chat.agent

import kotlinx.coroutines.flow.first
import org.krypton.chat.ChatMessage
import org.krypton.core.domain.study.*
import org.krypton.data.study.GoalData
import org.krypton.data.study.StudyPersistence
import org.krypton.util.AppLogger
import org.krypton.util.TimeProvider
import java.util.UUID

/**
 * Interface for managing study goals and sessions.
 * 
 * Assumes intent has already been classified as STUDY_GOAL by MasterAgent.
 * Handles multiple operations: create goal, plan goal, generate roadmap, prepare session.
 */
interface StudyAgent {
    /**
     * Executes study goal operation for the given message.
     * 
     * @param message The user's message (assumed to be a study goal operation)
     * @param history Conversation history
     * @param context Agent context
     * @return Appropriate AgentResult based on operation performed
     * @throws Exception if execution fails
     */
    suspend fun execute(
        message: String,
        history: List<ChatMessage>,
        context: AgentContext
    ): AgentResult
}

/**
 * Implementation of StudyAgent that manages study goals and sessions.
 */
class StudyAgentImpl(
    private val studyPlanner: org.krypton.core.domain.study.StudyPlanner,
    private val studyRunner: org.krypton.core.domain.study.StudyRunner,
    private val studyGoalRepository: StudyGoalRepository,
    private val sessionRepository: StudySessionRepository,
    private val cacheRepository: org.krypton.core.domain.study.StudyCacheRepository,
    private val searchNoteAgent: SearchNoteAgent,
    private val timeProvider: TimeProvider,
    private val persistence: StudyPersistence
) : StudyAgent {

    companion object {
        private val CREATE_PATTERNS = listOf(
            Regex("""create\s+(?:a\s+)?study\s+goal\s+(?:for|about|on)?\s*(.*)""", RegexOption.IGNORE_CASE),
            Regex("""create\s+(?:a\s+)?goal\s+(?:for|about|on)?\s*(.*)""", RegexOption.IGNORE_CASE)
        )
        private val PLAN_PATTERNS = listOf(
            Regex("""plan\s+(?:study\s+)?goal\s+["']?([^"'\s]+)["']?""", RegexOption.IGNORE_CASE),
            Regex("""plan\s+my\s+study\s+goal""", RegexOption.IGNORE_CASE),
            Regex("""plan\s+goal\s+["']?([^"'\s]+)["']?""", RegexOption.IGNORE_CASE)
        )
        private val ROADMAP_PATTERNS = listOf(
            Regex("""(?:generate|create|make)\s+(?:a\s+)?roadmap\s+(?:for|of)\s+(?:goal\s+)?["']?([^"'\s]+)["']?""", RegexOption.IGNORE_CASE),
            Regex("""roadmap\s+(?:for|of)\s+(?:goal\s+)?["']?([^"'\s]+)["']?""", RegexOption.IGNORE_CASE)
        )
        private val PREPARE_PATTERNS = listOf(
            Regex("""prepare\s+session\s+["']?([^"'\s]+)["']?""", RegexOption.IGNORE_CASE),
            Regex("""prepare\s+["']?([^"'\s]+)["']?""", RegexOption.IGNORE_CASE)
        )
    }

    override suspend fun execute(
        message: String,
        history: List<ChatMessage>,
        context: AgentContext
    ): AgentResult {
        AppLogger.i("StudyAgent", "Executing study operation - message: \"$message\"")
        
        // Check if vault is open
        val vaultPath = context.currentVaultPath
            ?: throw IllegalStateException("No vault open. Please open a vault to manage study goals.")
        
        // Determine operation type from message
        when {
            matchesPattern(message, CREATE_PATTERNS) -> {
                return createGoal(message, context, vaultPath)
            }
            matchesPattern(message, PLAN_PATTERNS) -> {
                return planGoal(message, context, vaultPath)
            }
            matchesPattern(message, ROADMAP_PATTERNS) -> {
                return generateRoadmap(message, context, vaultPath)
            }
            matchesPattern(message, PREPARE_PATTERNS) -> {
                return prepareSession(message, context, vaultPath)
            }
            else -> {
                throw IllegalArgumentException("Could not determine study operation from message: $message")
            }
        }
    }
    
    /**
     * Creates a new study goal.
     */
    private suspend fun createGoal(
        message: String,
        context: AgentContext,
        vaultPath: String
    ): AgentResult {
        // Extract goal details from message
        // For now, we'll use a simple extraction - in a real implementation, this could use LLM
        // For MCP, the parameters will be passed directly, so this is mainly for chat interface
        val title = extractTitle(message) ?: throw IllegalArgumentException("Could not extract goal title from message")
        val description = extractDescription(message)
        val topics = extractTopics(message)
        val targetDate = extractTargetDate(message)
        
        AppLogger.i("StudyAgent", "Creating study goal: $title with ${topics.size} topics")
        
        // Use SearchNoteAgent to find matching notes
        val matchedNotes = mutableListOf<String>()
        try {
            val searchQuery = if (topics.isNotEmpty()) {
                "search my notes for $title about ${topics.joinToString(", ")}"
            } else {
                "search my notes for $title"
            }
            
            val searchResult = searchNoteAgent.execute(searchQuery, emptyList(), context)
            if (searchResult is AgentResult.NotesFound) {
                matchedNotes.addAll(searchResult.results.map { it.filePath })
                AppLogger.d("StudyAgent", "Found ${matchedNotes.size} notes for goal: $title")
            }
        } catch (e: Exception) {
            AppLogger.w("StudyAgent", "Failed to fetch notes for goal: $title", e)
            // Continue with goal creation even if note fetching fails
        }
        
        // Create goal
        val now = timeProvider.currentTimeMillis()
        val goalId = StudyGoalId(UUID.randomUUID().toString())
        
        // Generate roadmap if we have notes
        val roadmap = if (matchedNotes.isNotEmpty()) {
            try {
                val tempGoal = StudyGoal(
                    id = goalId,
                    vaultId = vaultPath,
                    title = title,
                    description = description,
                    topics = topics,
                    matchedNotes = matchedNotes,
                    roadmap = null,
                    status = GoalStatus.PENDING,
                    targetDate = targetDate,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
                studyPlanner.generateRoadmap(tempGoal, matchedNotes)
            } catch (e: Exception) {
                AppLogger.w("StudyAgent", "Failed to generate roadmap for goal: $title", e)
                null
            }
        } else {
            null
        }
        
        val goal = StudyGoal(
            id = goalId,
            vaultId = vaultPath,
            title = title,
            description = description,
            topics = topics,
            matchedNotes = matchedNotes,
            roadmap = roadmap,
            status = GoalStatus.PENDING,
            targetDate = targetDate,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        
        // Save goal data
        val goalData = GoalData(
            goal = goal,
            roadmap = roadmap,
            sessions = emptyList(),
            noteSummaries = emptyList(),
            sessionFlashcards = emptyList(),
            sessionResults = emptyList()
        )
        persistence.saveGoalData(vaultPath, goalId.value, goalData)
        
        // Save to repository
        studyGoalRepository.upsert(goal)
        
        AppLogger.i("StudyAgent", "Created study goal: ${goal.title} with ${topics.size} topics and ${matchedNotes.size} matched notes")
        
        return AgentResult.StudyGoalCreated(
            goalId = goalId.value,
            title = title,
            topics = topics,
            matchedNotesCount = matchedNotes.size
        )
    }
    
    /**
     * Plans a study goal (creates sessions).
     */
    private suspend fun planGoal(
        message: String,
        context: AgentContext,
        vaultPath: String
    ): AgentResult {
        // Extract goal ID from message
        val goalIdStr = extractGoalId(message) ?: throw IllegalArgumentException("Could not extract goal ID from message")
        val goalId = StudyGoalId(goalIdStr)
        
        // Load goal
        val goal = studyGoalRepository.getGoal(goalId)
            ?: throw IllegalStateException("Goal not found: $goalId")
        
        if (goal.vaultId != vaultPath) {
            throw IllegalStateException("Goal belongs to a different vault")
        }
        
        AppLogger.i("StudyAgent", "Planning goal: ${goal.title}")
        
        // Plan the goal (creates sessions)
        studyPlanner.planForGoal(goal)
        
        // Get created sessions count from flow (take first value)
        val sessions = sessionRepository.observeSessionsForGoal(goalId).first()
        
        AppLogger.i("StudyAgent", "Planned goal: ${goal.title}, created ${sessions.size} sessions")
        
        return AgentResult.StudyGoalPlanned(
            goalId = goalId.value,
            sessionsCreated = sessions.size,
            topics = goal.topics
        )
    }
    
    /**
     * Generates a roadmap for a study goal.
     */
    private suspend fun generateRoadmap(
        message: String,
        context: AgentContext,
        vaultPath: String
    ): AgentResult {
        // Extract goal ID from message
        val goalIdStr = extractGoalId(message) ?: throw IllegalArgumentException("Could not extract goal ID from message")
        val goalId = StudyGoalId(goalIdStr)
        
        // Load goal
        val goal = studyGoalRepository.getGoal(goalId)
            ?: throw IllegalStateException("Goal not found: $goalId")
        
        if (goal.vaultId != vaultPath) {
            throw IllegalStateException("Goal belongs to a different vault")
        }
        
        AppLogger.i("StudyAgent", "Generating roadmap for goal: ${goal.title}")
        
        // Generate roadmap
        val roadmap = studyPlanner.generateRoadmap(goal, goal.matchedNotes)
        
        // Update goal with roadmap
        val updatedGoal = goal.copy(
            roadmap = roadmap,
            updatedAtMillis = timeProvider.currentTimeMillis()
        )
        studyGoalRepository.upsert(updatedGoal)
        
        AppLogger.i("StudyAgent", "Generated roadmap for goal: ${goal.title}")
        
        return AgentResult.RoadmapGenerated(
            goalId = goalId.value,
            roadmap = roadmap
        )
    }
    
    /**
     * Prepares a study session (generates summaries and flashcards).
     */
    private suspend fun prepareSession(
        message: String,
        context: AgentContext,
        vaultPath: String
    ): AgentResult {
        // Extract session ID from message
        val sessionIdStr = extractSessionId(message) ?: throw IllegalArgumentException("Could not extract session ID from message")
        val sessionId = StudySessionId(sessionIdStr)
        
        // Load session
        val session = sessionRepository.getSession(sessionId)
            ?: throw IllegalStateException("Session not found: $sessionId")
        
        // Load goal to verify vault
        val goal = studyGoalRepository.getGoal(session.goalId)
            ?: throw IllegalStateException("Goal not found: ${session.goalId}")
        
        if (goal.vaultId != vaultPath) {
            throw IllegalStateException("Session belongs to a different vault")
        }
        
        AppLogger.i("StudyAgent", "Preparing session: ${session.topic}")
        
        // Prepare session (generates summaries and flashcards)
        studyRunner.prepareSession(sessionId)
        
        // Get summary and flashcard counts from cache
        val summariesCount = session.notePaths.size // One summary per note
        val flashcardsCount = try {
            val cachedFlashcards = cacheRepository.getSessionFlashcards(sessionId)
            cachedFlashcards?.flashcards?.size ?: 0
        } catch (e: Exception) {
            AppLogger.w("StudyAgent", "Could not get flashcard count", e)
            0
        }
        
        AppLogger.i("StudyAgent", "Prepared session: ${session.topic}, summaries: $summariesCount, flashcards: $flashcardsCount")
        
        return AgentResult.SessionPrepared(
            sessionId = sessionId.value,
            topic = session.topic,
            summariesCount = summariesCount,
            flashcardsCount = flashcardsCount
        )
    }
    
    /**
     * Checks if message matches any of the patterns.
     */
    private fun matchesPattern(message: String, patterns: List<Regex>): Boolean {
        return patterns.any { it.containsMatchIn(message) }
    }
    
    /**
     * Extracts goal title from message (simple extraction for chat interface).
     * For MCP calls, title is extracted from "create study goal for [title]" pattern.
     */
    private fun extractTitle(message: String): String? {
        // Try pattern: "create study goal for [title]"
        val forPattern = Regex("""create\s+(?:study\s+)?goal\s+for\s+([^"'\s]+(?:\s+[^"'\s]+)*?)(?:\s+about|\s+description|$)""", RegexOption.IGNORE_CASE)
        val forMatch = forPattern.find(message)
        if (forMatch != null) {
            return forMatch.groupValues[1].trim().takeIf { it.isNotBlank() }
        }
        
        // Fallback: extract after "goal"
        val afterGoal = message.substringAfter("goal", "").trim()
        if (afterGoal.isNotBlank()) {
            // Take first part before "about", "description", or "target"
            val title = afterGoal.split(Regex("""\s+(?:about|description|target)""", RegexOption.IGNORE_CASE)).firstOrNull()?.trim()
            return title?.takeIf { it.isNotBlank() }
        }
        
        return null
    }
    
    /**
     * Extracts description from message.
     */
    private fun extractDescription(message: String): String? {
        val descMatch = Regex("""description[:\s]+["']?([^"']+)["']?""", RegexOption.IGNORE_CASE).find(message)
        return descMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }
    
    /**
     * Extracts topics from message.
     */
    private fun extractTopics(message: String): List<String> {
        // Try "about [topics]" pattern first
        val aboutMatch = Regex("""about\s+([^"']+)""", RegexOption.IGNORE_CASE).find(message)
        if (aboutMatch != null) {
            val topicsStr = aboutMatch.groupValues[1].trim()
            return topicsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }
        
        // Try "topics:" pattern
        val topicsMatch = Regex("""topics?[:\s]+["']?([^"']+)["']?""", RegexOption.IGNORE_CASE).find(message)
        val topicsStr = topicsMatch?.groupValues?.getOrNull(1)?.trim()
        return if (topicsStr != null) {
            topicsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            emptyList()
        }
    }
    
    /**
     * Extracts target date from message.
     */
    private fun extractTargetDate(message: String): String? {
        val dateMatch = Regex("""(?:target\s+)?date[:\s]+["']?(\d{4}-\d{2}-\d{2})["']?""", RegexOption.IGNORE_CASE).find(message)
        return dateMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }
    
    /**
     * Extracts goal ID from message.
     */
    private fun extractGoalId(message: String): String? {
        val match = PLAN_PATTERNS.firstOrNull { it.containsMatchIn(message) }?.find(message)
            ?: ROADMAP_PATTERNS.firstOrNull { it.containsMatchIn(message) }?.find(message)
        return match?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }
    
    /**
     * Extracts session ID from message.
     */
    private fun extractSessionId(message: String): String? {
        val match = PREPARE_PATTERNS.firstOrNull { it.containsMatchIn(message) }?.find(message)
        return match?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }
}

