package org.krypton.data.study

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.krypton.chat.agent.AgentContext
import org.krypton.chat.agent.AgentResult
import org.krypton.chat.agent.SearchNoteAgent
import org.krypton.core.domain.study.*
import org.krypton.data.files.FileSystem
import org.krypton.data.repository.SettingsRepository
import org.krypton.data.study.StudyPersistence
import org.krypton.rag.LlamaClient
import org.krypton.util.AppLogger
import org.krypton.util.TimeProvider
import java.util.UUID

/**
 * Implementation of StudyPlanner that creates sessions per topic using SearchNoteAgent.
 */
class StudyPlannerImpl(
    private val fileSystem: FileSystem,
    private val timeProvider: TimeProvider,
    private val searchNoteAgent: SearchNoteAgent,
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: StudySessionRepository,
    private val llamaClient: LlamaClient,
    private val persistence: StudyPersistence,
) : StudyPlanner {
    
    override suspend fun planForGoal(goal: StudyGoal) = withContext(Dispatchers.Default) {
        try {
            AppLogger.i("StudyPlanner", "Planning for goal: ${goal.title} (vault: ${goal.vaultId}, topics: ${goal.topics.size})")
            
            val vaultPath = goal.vaultId
            if (vaultPath.isBlank()) {
                AppLogger.w("StudyPlanner", "Vault path is empty for goal: ${goal.title}")
                return@withContext
            }
            
            val sessions = mutableListOf<StudySession>()
            val now = timeProvider.currentTimeMillis()
            
            // Use matchedNotes if available, otherwise search per topic
            val notesToUse = if (goal.matchedNotes.isNotEmpty()) {
                AppLogger.i("StudyPlanner", "Using ${goal.matchedNotes.size} matched notes from goal creation")
                goal.matchedNotes
            } else {
                emptyList()
            }
            
            if (goal.topics.isEmpty()) {
                // No topics: use LLM to divide notes into topics and create multiple sessions
                if (notesToUse.isNotEmpty()) {
                    try {
                        AppLogger.i("StudyPlanner", "No topics provided, using LLM to divide ${notesToUse.size} notes into topics")
                        
                        // Use LLM to divide notes into topics
                        val topicsWithNotes = divideNotesIntoTopics(goal, notesToUse, vaultPath)
                        
                        if (topicsWithNotes.isNotEmpty()) {
                            topicsWithNotes.entries.forEachIndexed { index, entry ->
                                val topic = entry.key
                                val notePaths = entry.value
                                val sessionId = StudySessionId("${goal.id.value}-session-${index + 1}")
                                val session = StudySession(
                                    id = sessionId,
                                    goalId = goal.id,
                                    topic = topic,
                                    notePaths = notePaths,
                                    status = SessionStatus.PENDING,
                                    order = index + 1,
                                    createdAtMillis = now,
                                    completedAtMillis = null
                                )
                                sessions.add(session)
                                sessionRepository.upsertSession(session)
                                AppLogger.i("StudyPlanner", "Created session ${index + 1}: $topic (${notePaths.size} notes)")
                            }
                            AppLogger.i("StudyPlanner", "Created ${topicsWithNotes.size} sessions for goal: ${goal.title}")
                        } else {
                            // Fallback: create single session if LLM division fails
                            AppLogger.w("StudyPlanner", "LLM topic division failed, creating single session")
                            val sessionId = StudySessionId("${goal.id.value}-session-1")
                            val session = StudySession(
                                id = sessionId,
                                goalId = goal.id,
                                topic = goal.title,
                                notePaths = notesToUse,
                                status = SessionStatus.PENDING,
                                order = 1,
                                createdAtMillis = now,
                                completedAtMillis = null
                            )
                            sessions.add(session)
                            sessionRepository.upsertSession(session)
                        }
                    } catch (e: Exception) {
                        AppLogger.e("StudyPlanner", "Failed to divide notes into topics using LLM", e)
                        // Fallback: create single session
                        val sessionId = StudySessionId("${goal.id.value}-session-1")
                        val session = StudySession(
                            id = sessionId,
                            goalId = goal.id,
                            topic = goal.title,
                            notePaths = notesToUse,
                            status = SessionStatus.PENDING,
                            order = 1,
                            createdAtMillis = now,
                            completedAtMillis = null
                        )
                        sessions.add(session)
                        sessionRepository.upsertSession(session)
                        AppLogger.i("StudyPlanner", "Created single session for goal: ${goal.title} (${notesToUse.size} notes)")
                    }
                } else {
                    AppLogger.w("StudyPlanner", "No topics and no matched notes for goal: ${goal.title}")
                }
            } else {
                // Create a session for each topic
            val settings = settingsRepository.settingsFlow.value
            val agentContext = AgentContext(
                currentVaultPath = vaultPath,
                settings = settings,
                currentNotePath = null
            )
            
                for ((index, topic) in goal.topics.withIndex()) {
                    try {
                        AppLogger.i("StudyPlanner", "Searching notes for topic: $topic")
                        
                        // Use matchedNotes if available, otherwise search
                        val notePaths: List<String> = if (notesToUse.isNotEmpty()) {
                            // Filter matched notes by topic (simple keyword matching)
                            val filtered = notesToUse.filter { notePath ->
                                val noteName = notePath.substringAfterLast("/").lowercase()
                                topic.lowercase() in noteName || noteName in topic.lowercase()
                            }
                            if (filtered.isNotEmpty()) {
                                filtered
                            } else {
                                notesToUse.take(5) // Fallback to first 5 if no match
                            }
                        } else {
                            // Search for notes related to this topic
                            val searchMessage = "search my notes for $topic"
            val searchResult = try {
                searchNoteAgent.execute(searchMessage, emptyList(), agentContext)
            } catch (e: Exception) {
                                AppLogger.w("StudyPlanner", "SearchNoteAgent execution failed for topic '$topic'", e)
                null
            }
            
                            if (searchResult is AgentResult.NotesFound) {
                                AppLogger.i("StudyPlanner", "Found ${searchResult.results.size} notes for topic '$topic'")
                                // Convert to full paths
                                searchResult.results.map { result ->
                                    if (result.filePath.startsWith(vaultPath)) {
                                        result.filePath
                                    } else {
                                        "$vaultPath/${result.filePath}"
                                    }
                                }
                            } else {
                                AppLogger.w("StudyPlanner", "No notes found for topic '$topic'")
                                emptyList<String>()
                            }
                        }
                        
                        if (notePaths.isNotEmpty()) {
                            val sessionId = StudySessionId("${goal.id.value}-session-${index + 1}")
                            val session = StudySession(
                                id = sessionId,
                                goalId = goal.id,
                                topic = topic,
                                notePaths = notePaths,
                                status = SessionStatus.PENDING,
                                order = index + 1,
                                createdAtMillis = now,
                                completedAtMillis = null
                            )
                            sessions.add(session)
                            sessionRepository.upsertSession(session)
                            AppLogger.i("StudyPlanner", "Created session ${index + 1}: $topic (${notePaths.size} notes)")
            } else {
                            AppLogger.w("StudyPlanner", "Skipping session for topic '$topic' - no notes found")
                        }
                    } catch (e: Exception) {
                        AppLogger.e("StudyPlanner", "Failed to create session for topic: $topic", e)
                    }
                }
            }
            
            // Save roadmap in GoalData (if goal has roadmap)
            // The roadmap is already in the goal object from goal creation
            // When sessions are saved via upsertSession, the roadmap will be included in GoalData
            
            AppLogger.i("StudyPlanner", "Created ${sessions.size} sessions for goal: ${goal.title}")
            
        } catch (e: Exception) {
            AppLogger.e("StudyPlanner", "Failed to plan for goal: ${goal.title}", e)
        }
    }
    
    override suspend fun generateRoadmap(goal: StudyGoal, notes: List<String>): String = withContext(Dispatchers.Default) {
        try {
            val prompt = buildString {
                appendLine("Generate a brief study roadmap (1-2 paragraphs) for the following goal:")
                appendLine("Title: ${goal.title}")
                if (goal.description != null) {
                    appendLine("Description: ${goal.description}")
                }
                if (goal.topics.isNotEmpty()) {
                    appendLine("Topics: ${goal.topics.joinToString(", ")}")
                }
                appendLine("Number of relevant notes found: ${notes.size}")
                appendLine()
                appendLine("The roadmap should describe what the user will learn and how the study plan is organized. Keep it concise (1-2 paragraphs).")
            }
            
            AppLogger.d("StudyPlanner", "Generating roadmap using LLM for goal: ${goal.title}")
            val roadmap = llamaClient.complete(prompt).trim()
            
            if (roadmap.isBlank()) {
                AppLogger.w("StudyPlanner", "LLM returned empty roadmap, using fallback")
                // Fallback roadmap
                buildString {
                    append("This study plan for \"${goal.title}\" ")
                    if (goal.topics.isNotEmpty()) {
                        append("covers ${goal.topics.size} topics: ${goal.topics.joinToString(", ")}. ")
                    }
                    append("You will study ${notes.size} relevant notes to achieve this goal. ")
                    append("The plan is organized into sessions that will help you systematically work through the material.")
                }
            } else {
                roadmap
            }
        } catch (e: Exception) {
            AppLogger.e("StudyPlanner", "Failed to generate roadmap using LLM", e)
            // Fallback roadmap
            buildString {
                append("This study plan for \"${goal.title}\" ")
                if (goal.topics.isNotEmpty()) {
                    append("covers ${goal.topics.size} topics: ${goal.topics.joinToString(", ")}. ")
                }
                append("You will study ${notes.size} relevant notes to achieve this goal.")
            }
        }
                    }
                    
    /**
     * Generates a roadmap markdown document for the goal.
     */
    private suspend fun generateRoadmapDocument(goal: StudyGoal, sessions: List<StudySession>) = withContext(Dispatchers.IO) {
        try {
            val vaultPath = goal.vaultId
            val roadmapContent = buildString {
                appendLine("# Study Roadmap: ${goal.title}")
                appendLine()
                
                if (goal.description != null) {
                    appendLine("## Description")
                    appendLine(goal.description)
                    appendLine()
                }
                
                // Add generated roadmap if available
                goal.roadmap?.let { roadmap ->
                    appendLine("## Overview")
                    appendLine(roadmap)
                    appendLine()
                }
                
                appendLine("## Sessions")
                appendLine("This roadmap outlines ${sessions.size} study sessions covering the following topics:")
                appendLine()
                
                sessions.forEach { session ->
                    appendLine("### Session ${session.order}: ${session.topic}")
                    session.notePaths.forEach { notePath ->
                        val relativePath = if (notePath.startsWith(vaultPath)) {
                            notePath.removePrefix(vaultPath).removePrefix("/")
                        } else {
                            notePath
                        }
                        val noteName = relativePath.substringAfterLast("/").removeSuffix(".md")
                        appendLine("- [$noteName]($relativePath)")
                    }
                    appendLine()
                }
            }
            
            // Save roadmap to .krypton/goals/{goalId}/roadmap.md
            if (persistence.saveRoadmap(goal.vaultId, goal.id.value, roadmapContent)) {
                AppLogger.i("StudyPlanner", "Saved roadmap to .krypton/goals/${goal.id.value}/roadmap.md")
            } else {
                AppLogger.w("StudyPlanner", "Failed to save roadmap via persistence, trying fileSystem fallback")
                // Fallback: save to vault root
                val roadmapFileName = "Study Roadmap - ${goal.title}.md"
                val roadmapPath = if (vaultPath.endsWith("/")) {
                    "$vaultPath$roadmapFileName"
                } else {
                    "$vaultPath/$roadmapFileName"
                }
                fileSystem.writeFile(roadmapPath, roadmapContent)
                AppLogger.i("StudyPlanner", "Generated roadmap document: $roadmapPath")
            }
        } catch (e: Exception) {
            AppLogger.e("StudyPlanner", "Failed to generate roadmap for goal: ${goal.title}", e)
        }
    }
    
    /**
     * Uses LLM to divide notes into topics and return a map of topic -> list of note paths.
     */
    private suspend fun divideNotesIntoTopics(
        goal: StudyGoal,
        notes: List<String>,
        vaultPath: String
    ): Map<String, List<String>> = withContext(Dispatchers.Default) {
        try {
            // Limit notes to maxNotes setting
            val settings = settingsRepository.settingsFlow.value
            val notesToProcess = notes.take(settings.study.maxNotes)
            
            // Build prompt with note names
            val noteNames = notesToProcess.map { it.substringAfterLast("/").removeSuffix(".md") }
            val prompt = buildString {
                appendLine("Given a study goal titled \"${goal.title}\" and the following list of notes:")
                appendLine()
                noteNames.forEachIndexed { index, name ->
                    appendLine("${index + 1}. $name")
                }
                appendLine()
                appendLine("Please divide these notes into logical topics/sessions (3-6 topics recommended).")
                appendLine("For each topic, provide:")
                appendLine("1. A short topic name (2-5 words)")
                appendLine("2. The note numbers that belong to that topic")
                appendLine()
                appendLine("Format your response as JSON with this structure:")
                appendLine("{")
                appendLine("  \"topics\": [")
                appendLine("    {")
                appendLine("      \"name\": \"Topic Name\",")
                appendLine("      \"noteIndices\": [1, 2, 3]")
                appendLine("    }")
                appendLine("  ]")
                appendLine("}")
                appendLine()
                appendLine("Note indices are 1-based (first note is 1, second is 2, etc.).")
                appendLine("Each note should appear in exactly one topic.")
            }
            
            AppLogger.d("StudyPlanner", "Asking LLM to divide ${notesToProcess.size} notes into topics")
            val response = llamaClient.complete(prompt).trim()
            
            if (response.isBlank()) {
                AppLogger.w("StudyPlanner", "LLM returned empty response for topic division")
                return@withContext emptyMap()
            }
            
            // Parse JSON response
            val json = kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
            
            // Try to extract JSON from response (might have markdown code blocks)
            val jsonText = if (response.contains("```json")) {
                response.substringAfter("```json").substringBefore("```").trim()
            } else if (response.contains("```")) {
                response.substringAfter("```").substringBefore("```").trim()
            } else {
                response
            }
            
            val parsed = json.decodeFromString<TopicDivisionResponse>(jsonText)
            
            // Convert note indices to note paths
            val result = mutableMapOf<String, List<String>>()
            parsed.topics.forEach { topic ->
                val topicNotes = topic.noteIndices
                    .mapNotNull { index ->
                        if (index >= 1 && index <= notesToProcess.size) {
                            notesToProcess[index - 1] // Convert 1-based to 0-based
                        } else {
                            null
                        }
                    }
                if (topicNotes.isNotEmpty()) {
                    result[topic.name] = topicNotes
                }
            }
            
            AppLogger.i("StudyPlanner", "LLM divided notes into ${result.size} topics")
            result
        } catch (e: Exception) {
            AppLogger.e("StudyPlanner", "Failed to divide notes into topics using LLM", e)
            emptyMap()
        }
    }
    
    @kotlinx.serialization.Serializable
    private data class TopicDivisionResponse(
        val topics: List<TopicWithNotes>
    )
    
    @kotlinx.serialization.Serializable
    private data class TopicWithNotes(
        val name: String,
        val noteIndices: List<Int>
    )
}

