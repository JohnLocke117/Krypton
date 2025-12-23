package org.krypton.krypton.rag

import org.krypton.krypton.data.repository.SettingsPersistence
import java.nio.file.Paths

/**
 * Gets the path to the RAG vector database.
 * 
 * Database is stored in the same directory as settings.json.
 */
fun getRagDatabasePath(settingsPersistence: SettingsPersistence): String {
    val settingsPath = settingsPersistence.getSettingsFilePath()
    val dbDir = Paths.get(settingsPath).parent
    return dbDir.resolve("vector.db").toString()
}

