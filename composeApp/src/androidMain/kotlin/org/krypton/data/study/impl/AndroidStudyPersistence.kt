package org.krypton.data.study.impl

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
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
            val uri = try {
                Uri.parse(vaultId)
            } catch (e: Exception) {
                null
            }
            
            if (uri != null && uri.scheme == "content") {
                // SAF-based access using DocumentFile
                val treeDocument = DocumentFile.fromTreeUri(context, uri) ?: return@withContext null
                val kryptonDir = treeDocument.findFile(".krypton")
                if (kryptonDir == null || !kryptonDir.isDirectory) {
                    return@withContext null
                }
                
                val goalsFile = kryptonDir.findFile("goals.json")
                if (goalsFile == null || !goalsFile.isFile) {
                    return@withContext null
                }
                
                context.contentResolver.openInputStream(goalsFile.uri)?.use { inputStream ->
                    val content = inputStream.bufferedReader().readText()
                    if (content.isBlank()) {
                        null
                    } else {
                        json.decodeFromString<GoalsData>(content)
                    }
                } ?: null
            } else {
                // File path-based access (fallback)
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
            }
        } catch (e: Exception) {
            AppLogger.e("AndroidStudyPersistence", "Failed to load goals for vault: $vaultId", e)
            null
        }
    }
    
    override suspend fun saveGoals(vaultId: String, data: GoalsData): Boolean = withContext(Dispatchers.IO) {
        try {
            val content = json.encodeToString(GoalsData.serializer(), data)
            
            val uri = try {
                Uri.parse(vaultId)
            } catch (e: Exception) {
                null
            }
            
            if (uri != null && uri.scheme == "content") {
                // SAF-based access using DocumentFile
                val treeDocument = DocumentFile.fromTreeUri(context, uri) ?: return@withContext false
                
                // Ensure .krypton directory exists
                val kryptonDir = findOrCreateDirectory(treeDocument, ".krypton")
                if (kryptonDir == null) {
                    AppLogger.e("AndroidStudyPersistence", "Failed to create .krypton directory")
                    return@withContext false
                }
                
                // Find or create the goals.json file
                var goalsFile = kryptonDir.findFile("goals.json")
                if (goalsFile == null) {
                    goalsFile = kryptonDir.createFile("application/json", "goals.json")
                }
                
                if (goalsFile != null && goalsFile.canWrite()) {
                    context.contentResolver.openOutputStream(goalsFile.uri)?.use { outputStream ->
                        outputStream.bufferedWriter().use { writer ->
                            writer.write(content)
                        }
                    }
                    true
                } else {
                    false
                }
            } else {
                // File path-based access (fallback)
                val file = getGoalsFile(vaultId)
                file.parentFile?.mkdirs()
                file.writeText(content)
                true
            }
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
            val uri = try {
                Uri.parse(vaultId)
            } catch (e: Exception) {
                null
            }
            
            if (uri != null && uri.scheme == "content") {
                // SAF-based access using DocumentFile
                val treeDocument = DocumentFile.fromTreeUri(context, uri) ?: return@withContext null
                val kryptonDir = treeDocument.findFile(".krypton")
                if (kryptonDir == null || !kryptonDir.isDirectory) {
                    return@withContext null
                }
                
                val goalsDir = kryptonDir.findFile("goals")
                if (goalsDir == null || !goalsDir.isDirectory) {
                    return@withContext null
                }
                
                val fileName = "$goalId.json"
                val goalFile = goalsDir.findFile(fileName)
                if (goalFile == null || !goalFile.isFile) {
                    return@withContext null
                }
                
                context.contentResolver.openInputStream(goalFile.uri)?.use { inputStream ->
                    val content = inputStream.bufferedReader().readText()
                    if (content.isBlank()) {
                        null
                    } else {
                        json.decodeFromString<GoalData>(content)
                    }
                } ?: null
            } else {
                // File path-based access (fallback)
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
            }
        } catch (e: Exception) {
            AppLogger.e("AndroidStudyPersistence", "Failed to load goal data for goal: $goalId", e)
            null
        }
    }
    
    override suspend fun saveGoalData(vaultId: String, goalId: String, data: GoalData): Boolean = withContext(Dispatchers.IO) {
        try {
            val content = json.encodeToString(GoalData.serializer(), data)
            
            val uri = try {
                Uri.parse(vaultId)
            } catch (e: Exception) {
                null
            }
            
            if (uri != null && uri.scheme == "content") {
                // SAF-based access using DocumentFile
                val treeDocument = DocumentFile.fromTreeUri(context, uri) ?: return@withContext false
                
                // Ensure .krypton/goals directory exists
                val goalsDir = findOrCreateDirectory(treeDocument, ".krypton/goals")
                if (goalsDir == null) {
                    AppLogger.e("AndroidStudyPersistence", "Failed to create .krypton/goals directory")
                    return@withContext false
                }
                
                // Find or create the goal data file
                val fileName = "$goalId.json"
                var goalFile = goalsDir.findFile(fileName)
                if (goalFile == null) {
                    goalFile = goalsDir.createFile("application/json", fileName)
                }
                
                if (goalFile != null && goalFile.canWrite()) {
                    context.contentResolver.openOutputStream(goalFile.uri)?.use { outputStream ->
                        outputStream.bufferedWriter().use { writer ->
                            writer.write(content)
                        }
                    }
                    true
                } else {
                    false
                }
            } else {
                // File path-based access (fallback)
                val file = getGoalDataFile(vaultId, goalId)
                file.parentFile?.mkdirs()
                file.writeText(content)
                true
            }
        } catch (e: Exception) {
            AppLogger.e("AndroidStudyPersistence", "Failed to save goal data for goal: $goalId", e)
            false
        }
    }
    
    private fun getGoalDataFile(vaultId: String, goalId: String): File {
        val vaultFile = File(vaultId)
        return File(vaultFile, ".krypton/goals/$goalId.json")
    }
    
    /**
     * Helper function to find or create a directory by relative path using DocumentFile.
     */
    private fun findOrCreateDirectory(root: DocumentFile?, path: String): DocumentFile? {
        if (root == null) return null
        if (path.isEmpty()) return root
        
        val parts = path.split("/").filter { it.isNotEmpty() }
        var current = root
        
        for (part in parts) {
            var child = current?.findFile(part)
            if (child == null) {
                child = current?.createDirectory(part)
            }
            current = child
            if (current == null) break
        }
        
        return current
    }
    
    /**
     * Ensures the .krypton directory exists in the vault.
     * This should be called when a vault is initialized.
     */
    suspend fun ensureKryptonDirectory(vaultId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = try {
                Uri.parse(vaultId)
            } catch (e: Exception) {
                null
            }
            
            if (uri != null && uri.scheme == "content") {
                // SAF-based access using DocumentFile
                val treeDocument = DocumentFile.fromTreeUri(context, uri) ?: return@withContext false
                val kryptonDir = findOrCreateDirectory(treeDocument, ".krypton")
                kryptonDir != null
            } else {
                // File path-based access (fallback)
                val vaultFile = File(vaultId)
                val kryptonDir = File(vaultFile, ".krypton")
                if (!kryptonDir.exists()) {
                    kryptonDir.mkdirs()
                }
                kryptonDir.exists() && kryptonDir.isDirectory
            }
        } catch (e: Exception) {
            AppLogger.e("AndroidStudyPersistence", "Failed to ensure .krypton directory for vault: $vaultId", e)
            false
        }
    }
}

