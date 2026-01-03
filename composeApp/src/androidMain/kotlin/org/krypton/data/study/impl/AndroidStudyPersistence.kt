package org.krypton.data.study.impl

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.krypton.data.study.GoalsData
import org.krypton.data.study.StudyData
import org.krypton.data.study.StudyPersistence
import org.krypton.data.study.SessionData
import org.krypton.data.study.GoalData
import org.krypton.util.AppLogger
import java.io.File

/**
 * Android implementation of StudyPersistence using standard File APIs.
 * Stores study data in `.krypton/study-data.json` relative to vault root.
 */
class AndroidStudyPersistence(
    private val context: Context
) : StudyPersistence {
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    override suspend fun loadStudyData(vaultId: String): StudyData? = withContext(Dispatchers.IO) {
        try {
            val file = getStudyDataFile(vaultId)
            if (file.exists() && file.isFile) {
                val content = file.readText()
                if (content.isBlank()) {
                    null
                } else {
                    json.decodeFromString<StudyData>(content)
                }
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.e("AndroidStudyPersistence", "Failed to load study data for vault: $vaultId", e)
            null
        }
    }
    
    override suspend fun saveStudyData(vaultId: String, data: StudyData): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = getStudyDataFile(vaultId)
            
            // Ensure .krypton directory exists
            file.parentFile?.mkdirs()
            
            val content = json.encodeToString(StudyData.serializer(), data)
            file.writeText(content)
            true
        } catch (e: Exception) {
            AppLogger.e("AndroidStudyPersistence", "Failed to save study data for vault: $vaultId", e)
            false
        }
    }
    
    private fun getStudyDataFile(vaultId: String): File {
        val vaultFile = File(vaultId)
        return File(vaultFile, ".krypton/study-data.json")
    }
    
    override suspend fun loadStudyPlan(vaultId: String, goalId: String): StudyData? = withContext(Dispatchers.IO) {
        try {
            val file = getStudyPlanFile(vaultId, goalId)
            if (file.exists() && file.isFile) {
                val content = file.readText()
                if (content.isBlank()) {
                    null
                } else {
                    json.decodeFromString<StudyData>(content)
                }
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.e("AndroidStudyPersistence", "Failed to load study plan for goal: $goalId", e)
            null
        }
    }
    
    override suspend fun saveStudyPlan(vaultId: String, goalId: String, data: StudyData): Boolean = withContext(Dispatchers.IO) {
        try {
            // Save each session to its own file
            val goalDir = getGoalDirectory(vaultId, goalId)
            goalDir.mkdirs()
            
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
                    AppLogger.w("AndroidStudyPersistence", "Failed to save session: ${session.id.value}")
                }
            }
            
            true
        } catch (e: Exception) {
            AppLogger.e("AndroidStudyPersistence", "Failed to save study plan for goal: $goalId", e)
            false
        }
    }
    
    private fun getStudyPlanFile(vaultId: String, goalId: String): File {
        val vaultFile = File(vaultId)
        return File(vaultFile, ".krypton/study-plans/$goalId.json")
    }
    
    override suspend fun loadGoals(vaultId: String): GoalsData? = withContext(Dispatchers.IO) {
        try {
            val file = getGoalsFile(vaultId)
            if (file.exists() && file.isFile) {
                val content = file.readText()
                if (content.isBlank()) {
                    null
                } else {
                    json.decodeFromString<GoalsData>(content)
                }
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.e("AndroidStudyPersistence", "Failed to load goals for vault: $vaultId", e)
            null
        }
    }
    
    override suspend fun saveGoals(vaultId: String, data: GoalsData): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = getGoalsFile(vaultId)
            
            // Ensure .krypton directory exists
            file.parentFile?.mkdirs()
            
            val content = json.encodeToString(GoalsData.serializer(), data)
            file.writeText(content)
            true
        } catch (e: Exception) {
            AppLogger.e("AndroidStudyPersistence", "Failed to save goals for vault: $vaultId", e)
            false
        }
    }
    
    private fun getGoalsFile(vaultId: String): File {
        val vaultFile = File(vaultId)
        return File(vaultFile, ".krypton/goals.json")
    }
    
    private fun getGoalDirectory(vaultId: String, goalId: String): File {
        val vaultFile = File(vaultId)
        return File(vaultFile, ".krypton/goals/$goalId")
    }
    
    override suspend fun loadSessionData(vaultId: String, goalId: String, sessionId: String): SessionData? = withContext(Dispatchers.IO) {
        try {
            val goalDir = getGoalDirectory(vaultId, goalId)
            val sessionFile = File(goalDir, "$sessionId.json")
            if (sessionFile.exists() && sessionFile.isFile) {
                val content = sessionFile.readText()
                if (content.isBlank()) {
                    null
                } else {
                    json.decodeFromString<SessionData>(content)
                }
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.e("AndroidStudyPersistence", "Failed to load session data: $sessionId", e)
            null
        }
    }
    
    override suspend fun saveSessionData(vaultId: String, goalId: String, sessionId: String, data: SessionData): Boolean = withContext(Dispatchers.IO) {
        try {
            val goalDir = getGoalDirectory(vaultId, goalId)
            val sessionFile = File(goalDir, "$sessionId.json")
            
            // Ensure goal directory exists
            goalDir.mkdirs()
            
            val content = json.encodeToString(SessionData.serializer(), data)
            sessionFile.writeText(content)
            true
        } catch (e: Exception) {
            AppLogger.e("AndroidStudyPersistence", "Failed to save session data: $sessionId", e)
            false
        }
    }
    
    override suspend fun loadAllSessionsForGoal(vaultId: String, goalId: String): Map<String, SessionData> = withContext(Dispatchers.IO) {
        try {
            val goalDir = getGoalDirectory(vaultId, goalId)
            if (!goalDir.exists() || !goalDir.isDirectory) {
                return@withContext emptyMap()
            }
            
            val sessions = mutableMapOf<String, SessionData>()
            goalDir.listFiles()?.forEach { sessionFile ->
                if (sessionFile.isFile && sessionFile.name.endsWith(".json") && sessionFile.name != "roadmap.md") {
                    try {
                        val content = sessionFile.readText()
                        if (content.isNotBlank()) {
                            val sessionData = json.decodeFromString<SessionData>(content)
                            val sessionId = sessionFile.name.removeSuffix(".json")
                            sessions[sessionId] = sessionData
                        }
                    } catch (e: Exception) {
                        AppLogger.w("AndroidStudyPersistence", "Failed to load session file: ${sessionFile.name}", e)
                    }
                }
            }
            sessions
        } catch (e: Exception) {
            AppLogger.e("AndroidStudyPersistence", "Failed to load all sessions for goal: $goalId", e)
            emptyMap()
        }
    }
    
    override suspend fun saveRoadmap(vaultId: String, goalId: String, roadmapContent: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val goalDir = getGoalDirectory(vaultId, goalId)
            val roadmapFile = File(goalDir, "roadmap.md")
            
            // Ensure goal directory exists
            goalDir.mkdirs()
            
            roadmapFile.writeText(roadmapContent)
            true
        } catch (e: Exception) {
            AppLogger.e("AndroidStudyPersistence", "Failed to save roadmap for goal: $goalId", e)
            false
        }
    }
    
    override suspend fun loadRoadmap(vaultId: String, goalId: String): String? = withContext(Dispatchers.IO) {
        try {
            val goalDir = getGoalDirectory(vaultId, goalId)
            val roadmapFile = File(goalDir, "roadmap.md")
            if (roadmapFile.exists() && roadmapFile.isFile) {
                roadmapFile.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.e("AndroidStudyPersistence", "Failed to load roadmap for goal: $goalId", e)
            null
        }
    }
    
    override suspend fun loadGoalData(vaultId: String, goalId: String): GoalData? = withContext(Dispatchers.IO) {
        try {
            val file = getGoalDataFile(vaultId, goalId)
            if (file.exists() && file.isFile) {
                val content = file.readText()
                if (content.isBlank()) {
                    null
                } else {
                    json.decodeFromString<GoalData>(content)
                }
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.e("AndroidStudyPersistence", "Failed to load goal data for goal: $goalId", e)
            null
        }
    }
    
    override suspend fun saveGoalData(vaultId: String, goalId: String, data: GoalData): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = getGoalDataFile(vaultId, goalId)
            
            // Ensure .krypton/goals directory exists
            file.parentFile?.mkdirs()
            
            val content = json.encodeToString(GoalData.serializer(), data)
            file.writeText(content)
            true
        } catch (e: Exception) {
            AppLogger.e("AndroidStudyPersistence", "Failed to save goal data for goal: $goalId", e)
            false
        }
    }
    
    private fun getGoalDataFile(vaultId: String, goalId: String): File {
        val vaultFile = File(vaultId)
        return File(vaultFile, ".krypton/goals/$goalId.json")
    }
}

