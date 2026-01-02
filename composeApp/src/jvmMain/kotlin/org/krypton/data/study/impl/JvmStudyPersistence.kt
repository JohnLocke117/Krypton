package org.krypton.data.study.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.krypton.data.study.GoalsData
import org.krypton.data.study.StudyData
import org.krypton.data.study.StudyPersistence
import org.krypton.data.study.SessionData
import org.krypton.util.AppLogger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * JVM implementation of StudyPersistence using java.nio.file.
 * Stores study data in `.krypton/study-data.json` relative to vault root.
 */
class JvmStudyPersistence : StudyPersistence {
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    override suspend fun loadStudyData(vaultId: String): StudyData? = withContext(Dispatchers.IO) {
        try {
            val filePath = getStudyDataPath(vaultId)
            if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                val content = Files.readString(filePath)
                if (content.isBlank()) {
                    null
                } else {
                    json.decodeFromString<StudyData>(content)
                }
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.e("JvmStudyPersistence", "Failed to load study data for vault: $vaultId", e)
            null
        }
    }
    
    override suspend fun saveStudyData(vaultId: String, data: StudyData): Boolean = withContext(Dispatchers.IO) {
        try {
            val filePath = getStudyDataPath(vaultId)
            
            // Ensure .krypton directory exists
            Files.createDirectories(filePath.parent)
            
            val content = json.encodeToString(StudyData.serializer(), data)
            Files.writeString(
                filePath,
                content,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            )
            true
        } catch (e: Exception) {
            AppLogger.e("JvmStudyPersistence", "Failed to save study data for vault: $vaultId", e)
            false
        }
    }
    
    private fun getStudyDataPath(vaultId: String): Path {
        val vaultPath = Paths.get(vaultId)
        return vaultPath.resolve(".krypton").resolve("study-data.json")
    }
    
    override suspend fun loadStudyPlan(vaultId: String, goalId: String): StudyData? = withContext(Dispatchers.IO) {
        try {
            val filePath = getStudyPlanPath(vaultId, goalId)
            if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                val content = Files.readString(filePath)
                if (content.isBlank()) {
                    null
                } else {
                    json.decodeFromString<StudyData>(content)
                }
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.e("JvmStudyPersistence", "Failed to load study plan for goal: $goalId", e)
            null
        }
    }
    
    override suspend fun saveStudyPlan(vaultId: String, goalId: String, data: StudyData): Boolean = withContext(Dispatchers.IO) {
        try {
            // Save each session to its own file
            val goalDir = getGoalDirectory(vaultId, goalId)
            Files.createDirectories(goalDir)
            
            for (session in data.sessions) {
                // Load existing session data to preserve summaries, flashcards, results
                val existingSessionData = loadSessionData(vaultId, goalId, session.id.value)
                val sessionData = org.krypton.data.study.SessionData(
                    session = session,
                    noteSummaries = existingSessionData?.noteSummaries ?: data.noteSummaries.filter { it.notePath in session.notePaths },
                    flashcards = existingSessionData?.flashcards ?: data.sessionFlashcards.firstOrNull { it.sessionId == session.id },
                    result = existingSessionData?.result ?: data.sessionResults.firstOrNull { it.sessionId == session.id }
                )
                
                if (!saveSessionData(vaultId, goalId, session.id.value, sessionData)) {
                    AppLogger.w("JvmStudyPersistence", "Failed to save session: ${session.id.value}")
                }
            }
            
            true
        } catch (e: Exception) {
            AppLogger.e("JvmStudyPersistence", "Failed to save study plan for goal: $goalId", e)
            false
        }
    }
    
    private fun getStudyPlanPath(vaultId: String, goalId: String): Path {
        val vaultPath = Paths.get(vaultId)
        return vaultPath.resolve(".krypton").resolve("study-plans").resolve("$goalId.json")
    }
    
    override suspend fun loadGoals(vaultId: String): GoalsData? = withContext(Dispatchers.IO) {
        try {
            val filePath = getGoalsPath(vaultId)
            if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                val content = Files.readString(filePath)
                if (content.isBlank()) {
                    null
                } else {
                    json.decodeFromString<GoalsData>(content)
                }
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.e("JvmStudyPersistence", "Failed to load goals for vault: $vaultId", e)
            null
        }
    }
    
    override suspend fun saveGoals(vaultId: String, data: GoalsData): Boolean = withContext(Dispatchers.IO) {
        try {
            val filePath = getGoalsPath(vaultId)
            
            // Ensure .krypton directory exists
            Files.createDirectories(filePath.parent)
            
            val content = json.encodeToString(GoalsData.serializer(), data)
            Files.writeString(
                filePath,
                content,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            )
            true
        } catch (e: Exception) {
            AppLogger.e("JvmStudyPersistence", "Failed to save goals for vault: $vaultId", e)
            false
        }
    }
    
    private fun getGoalsPath(vaultId: String): Path {
        val vaultPath = Paths.get(vaultId)
        return vaultPath.resolve(".krypton").resolve("goals.json")
    }
    
    private fun getGoalDirectory(vaultId: String, goalId: String): Path {
        val vaultPath = Paths.get(vaultId)
        return vaultPath.resolve(".krypton").resolve("goals").resolve(goalId)
    }
    
    override suspend fun loadSessionData(vaultId: String, goalId: String, sessionId: String): SessionData? = withContext(Dispatchers.IO) {
        try {
            val goalDir = getGoalDirectory(vaultId, goalId)
            val sessionFile = goalDir.resolve("$sessionId.json")
            if (Files.exists(sessionFile) && Files.isRegularFile(sessionFile)) {
                val content = Files.readString(sessionFile)
                if (content.isBlank()) {
                    null
                } else {
                    json.decodeFromString<SessionData>(content)
                }
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.e("JvmStudyPersistence", "Failed to load session data: $sessionId", e)
            null
        }
    }
    
    override suspend fun saveSessionData(vaultId: String, goalId: String, sessionId: String, data: SessionData): Boolean = withContext(Dispatchers.IO) {
        try {
            val goalDir = getGoalDirectory(vaultId, goalId)
            val sessionFile = goalDir.resolve("$sessionId.json")
            
            // Ensure goal directory exists
            Files.createDirectories(goalDir)
            
            val content = json.encodeToString(SessionData.serializer(), data)
            Files.writeString(
                sessionFile,
                content,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            )
            true
        } catch (e: Exception) {
            AppLogger.e("JvmStudyPersistence", "Failed to save session data: $sessionId", e)
            false
        }
    }
    
    override suspend fun loadAllSessionsForGoal(vaultId: String, goalId: String): Map<String, SessionData> = withContext(Dispatchers.IO) {
        try {
            val goalDir = getGoalDirectory(vaultId, goalId)
            if (!Files.exists(goalDir) || !Files.isDirectory(goalDir)) {
                return@withContext emptyMap()
            }
            
            val sessions = mutableMapOf<String, SessionData>()
            Files.list(goalDir).use { stream ->
                stream.filter { it.fileName.toString().endsWith(".json") && !it.fileName.toString().equals("roadmap.md") }
                    .forEach { sessionFile ->
                        try {
                            val content = Files.readString(sessionFile)
                            if (content.isNotBlank()) {
                                val sessionData = json.decodeFromString<SessionData>(content)
                                val sessionId = sessionFile.fileName.toString().removeSuffix(".json")
                                sessions[sessionId] = sessionData
                            }
                        } catch (e: Exception) {
                            AppLogger.w("JvmStudyPersistence", "Failed to load session file: ${sessionFile.fileName}", e)
                        }
                    }
            }
            sessions
        } catch (e: Exception) {
            AppLogger.e("JvmStudyPersistence", "Failed to load all sessions for goal: $goalId", e)
            emptyMap()
        }
    }
    
    override suspend fun saveRoadmap(vaultId: String, goalId: String, roadmapContent: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val goalDir = getGoalDirectory(vaultId, goalId)
            val roadmapFile = goalDir.resolve("roadmap.md")
            
            // Ensure goal directory exists
            Files.createDirectories(goalDir)
            
            Files.writeString(
                roadmapFile,
                roadmapContent,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            )
            true
        } catch (e: Exception) {
            AppLogger.e("JvmStudyPersistence", "Failed to save roadmap for goal: $goalId", e)
            false
        }
    }
    
    override suspend fun loadRoadmap(vaultId: String, goalId: String): String? = withContext(Dispatchers.IO) {
        try {
            val goalDir = getGoalDirectory(vaultId, goalId)
            val roadmapFile = goalDir.resolve("roadmap.md")
            if (Files.exists(roadmapFile) && Files.isRegularFile(roadmapFile)) {
                Files.readString(roadmapFile)
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.e("JvmStudyPersistence", "Failed to load roadmap for goal: $goalId", e)
            null
        }
    }
}

