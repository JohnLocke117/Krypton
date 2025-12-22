package org.krypton.krypton.rag

import org.krypton.krypton.SettingsPersistence
import java.nio.file.Paths

/**
 * Gets the path to the RAG vector database.
 * 
 * Database is stored in the same directory as settings.json.
 */
fun getRagDatabasePath(): String {
    val settingsPath = SettingsPersistence.getSettingsFilePath()
    val dbDir = settingsPath.parent
    return dbDir.resolve("vector.db").toString()
}

