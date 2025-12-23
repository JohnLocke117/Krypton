package org.krypton.krypton

import org.krypton.krypton.settings.SettingsPersistence as ISettingsPersistence
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

object SettingsPersistence : ISettingsPersistence {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun getSettingsFilePath(): Path {
        val homeDir = System.getProperty("user.home")
        val settingsDir = Paths.get(homeDir, "Varun", "Code", "Test")
        return settingsDir.resolve("settings.json")
    }

    override fun loadSettingsFromFile(path: Path): Settings? {
        return try {
            if (Files.exists(path) && Files.isRegularFile(path)) {
                val content = Files.readString(path)
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

    override fun saveSettingsToFile(path: Path, settings: Settings): Boolean {
        return try {
            // Ensure directory exists
            val parentDir = path.parent
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir)
            }

            val content = json.encodeToString(Settings.serializer(), settings)
            Files.writeString(
                path,
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

