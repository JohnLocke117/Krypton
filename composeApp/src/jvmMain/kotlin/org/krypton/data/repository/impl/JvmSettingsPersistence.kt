package org.krypton.data.repository.impl

import kotlinx.serialization.json.Json
import org.krypton.Settings
import org.krypton.data.repository.SettingsPersistence
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * JVM implementation of SettingsPersistence using java.nio.file.
 */
object JvmSettingsPersistence : SettingsPersistence {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun getSettingsFilePath(): String {
        // Check for environment variable override first
        val envPath = System.getenv("KRYPTON_SETTINGS_PATH")
        if (envPath != null && envPath.isNotBlank()) {
            return Paths.get(envPath).toAbsolutePath().normalize().toString()
        }
        
        // Default to ./settings.json in project root
        return Paths.get(".").resolve("settings.json").toAbsolutePath().normalize().toString()
    }

    override fun loadSettingsFromFile(path: String): Settings? {
        return try {
            val filePath = Paths.get(path)
            if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                val content = Files.readString(filePath)
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
        return try {
            val filePath = Paths.get(path)
            // Ensure directory exists
            val parentDir = filePath.parent
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir)
            }

            val content = json.encodeToString(Settings.serializer(), settings)
            Files.writeString(
                filePath,
                content,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )
            true
        } catch (e: Exception) {
            false
        }
    }
}

