package org.krypton.data.repository.impl

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.krypton.util.AppLogger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * Manages the configuration file that stores the path to the settings.json file.
 * This avoids a circular dependency (can't store settings path in settings.json itself).
 */
object SettingsConfigManager {
    private val configFilePath: Path = Paths.get(System.getProperty("user.home"))
        .resolve(".krypton")
        .resolve("config.json")
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    @Serializable
    private data class Config(
        val settingsFilePath: String? = null
    )
    
    /**
     * Gets the settings file path from config, or returns default.
     */
    fun getSettingsFilePath(): String {
        // Check environment variable first
        val envPath = System.getenv("KRYPTON_SETTINGS_PATH")
        if (envPath != null && envPath.isNotBlank()) {
            val path = Paths.get(envPath).toAbsolutePath().normalize().toString()
            AppLogger.d("SettingsConfigManager", "Using settings path from environment: $path")
            return path
        }
        
        // Try to load from config file
        val config = loadConfig()
        if (config?.settingsFilePath != null && config.settingsFilePath.isNotBlank()) {
            val path = Paths.get(config.settingsFilePath).toAbsolutePath().normalize().toString()
            AppLogger.d("SettingsConfigManager", "Using settings path from config: $path")
            return path
        }
        
        // Default to settings.json in current working directory or project root
        val defaultPath = findDefaultSettingsPath()
        AppLogger.d("SettingsConfigManager", "Using default settings path: $defaultPath")
        return defaultPath
    }
    
    /**
     * Sets the settings file path in the config.
     */
    fun setSettingsFilePath(path: String): Boolean {
        return try {
            val normalizedPath = Paths.get(path).toAbsolutePath().normalize().toString()
            val config = Config(settingsFilePath = normalizedPath)
            
            // Ensure config directory exists
            configFilePath.parent?.let { parent ->
                if (!Files.exists(parent)) {
                    Files.createDirectories(parent)
                }
            }
            
            val content = json.encodeToString(Config.serializer(), config)
            Files.writeString(
                configFilePath,
                content,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )
            AppLogger.i("SettingsConfigManager", "Settings file path updated to: $normalizedPath")
            true
        } catch (e: Exception) {
            AppLogger.e("SettingsConfigManager", "Failed to save settings file path: $path", e)
            false
        }
    }
    
    private fun loadConfig(): Config? {
        return try {
            if (Files.exists(configFilePath) && Files.isRegularFile(configFilePath)) {
                val content = Files.readString(configFilePath)
                if (content.isNotBlank()) {
                    json.decodeFromString<Config>(content)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Finds the default settings.json path by looking for it in:
     * 1. Current working directory
     * 2. Project root (where build.gradle.kts or settings.gradle.kts exists)
     */
    private fun findDefaultSettingsPath(): String {
        // Try current working directory first
        val userDir = System.getProperty("user.dir")
        if (userDir != null) {
            val cwdSettings = Paths.get(userDir, "settings.json")
            if (Files.exists(cwdSettings)) {
                return cwdSettings.toAbsolutePath().normalize().toString()
            }
        }
        
        // Walk up from current directory looking for project root
        var currentDir = if (userDir != null) Paths.get(userDir) else Paths.get(".")
        var levels = 0
        while (levels < 10) {
            val buildFile = currentDir.resolve("build.gradle.kts")
            val settingsFile = currentDir.resolve("settings.gradle.kts")
            val gradleWrapper = currentDir.resolve("gradlew")
            
            // If we find project markers, check for settings.json here
            if (Files.exists(buildFile) || Files.exists(settingsFile) || Files.exists(gradleWrapper)) {
                val projectSettings = currentDir.resolve("settings.json")
                if (Files.exists(projectSettings)) {
                    return projectSettings.toAbsolutePath().normalize().toString()
                }
                // Even if settings.json doesn't exist, use this as the default location
                return projectSettings.toAbsolutePath().normalize().toString()
            }
            
            val parent = currentDir.parent
            if (parent == null || parent == currentDir) {
                break
            }
            currentDir = parent
            levels++
        }
        
        // Fallback to current working directory
        return if (userDir != null) {
            Paths.get(userDir, "settings.json").toAbsolutePath().normalize().toString()
        } else {
            Paths.get("settings.json").toAbsolutePath().normalize().toString()
        }
    }
}

