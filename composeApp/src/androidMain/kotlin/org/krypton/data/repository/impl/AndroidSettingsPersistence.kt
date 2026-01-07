package org.krypton.data.repository.impl

import android.content.Context
import kotlinx.serialization.json.Json
import org.krypton.Settings
import org.krypton.data.repository.SettingsPersistence
import org.krypton.platform.SettingsConfigProvider
import java.io.File

/**
 * Android implementation of SettingsPersistence using Android file APIs.
 */
class AndroidSettingsPersistence(
    private val context: Context,
    private val configProvider: SettingsConfigProvider
) : SettingsPersistence {
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun getSettingsFilePath(vaultId: String?): String {
        return configProvider.getSettingsFilePath(vaultId)
    }
    
    override fun getVaultSettingsPath(vaultId: String): String? {
        return configProvider.getVaultSettingsPath(vaultId)
    }

    override fun loadSettingsFromFile(path: String): Settings? {
        return try {
            val file = File(path)
            if (file.exists() && file.isFile) {
                val content = file.readText()
                if (content.isBlank()) {
                    null
                } else {
                    json.decodeFromString<Settings>(content)
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun parseSettingsFromJson(jsonString: String): Settings? {
        return try {
            if (jsonString.isBlank()) {
                null
            } else {
                json.decodeFromString<Settings>(jsonString)
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun saveSettingsToFile(path: String, settings: Settings): Boolean {
        // Android: Settings modifications are disabled (read-only)
        // This method should never be called on Android, but if it is, return false
        android.util.Log.w("AndroidSettingsPersistence", "saveSettingsToFile called on Android (read-only mode) - operation ignored")
        return false
    }
}

