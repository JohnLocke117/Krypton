package org.krypton.data.study.impl

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.krypton.data.study.StudyData
import org.krypton.data.study.StudyPersistence
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
}

