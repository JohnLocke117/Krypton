package org.krypton.data.study.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.krypton.data.study.StudyData
import org.krypton.data.study.StudyPersistence
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
}

