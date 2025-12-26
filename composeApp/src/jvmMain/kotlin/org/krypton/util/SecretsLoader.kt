package org.krypton.util

import java.io.File
import java.nio.file.Paths
import java.util.Properties
import org.krypton.util.AppLogger

/**
 * Utility for loading secrets from local.secrets.properties file.
 * 
 * The file should be located at the project root.
 */
object SecretsLoader {
    /**
     * Loads a secret value from local.secrets.properties.
     * 
     * @param key The property key (e.g., "TAVILLY_API_KEY")
     * @return The property value, or null if file/key doesn't exist
     */
    fun loadSecret(key: String): String? {
        return try {
            // Try multiple strategies to find the file:
            // 1. Check current working directory
            // 2. Check relative to user.dir
            // 3. Walk up from current directory looking for build.gradle.kts
            // 4. Check project root (if we can determine it)
            
            val searchPaths = mutableListOf<File>()
            
            // Strategy 1: Current working directory
            val userDir = System.getProperty("user.dir")
            if (userDir != null) {
                val candidate = File(userDir, "local.secrets.properties")
                if (!searchPaths.contains(candidate)) {
                    searchPaths.add(candidate)
                }
            }
            
            // Strategy 2: Walk up from user.dir looking for project root markers
            var currentDir = File(userDir ?: ".")
            var levels = 0
            while (levels < 10) {
                val buildFile = File(currentDir, "build.gradle.kts")
                val settingsFile = File(currentDir, "settings.gradle.kts")
                val gradleWrapper = File(currentDir, "gradlew")
                
                // If we find project markers, add the secrets file from this directory
                if (buildFile.exists() || settingsFile.exists() || gradleWrapper.exists()) {
                    val secretsFile = File(currentDir, "local.secrets.properties")
                    if (!searchPaths.contains(secretsFile)) {
                        searchPaths.add(0, secretsFile) // Add at beginning for priority
                    }
                }
                
                // Also add the file from current directory level
                val candidate = File(currentDir, "local.secrets.properties")
                if (!searchPaths.contains(candidate)) {
                    searchPaths.add(candidate)
                }
                
                val parent = currentDir.parentFile
                if (parent == null || parent == currentDir) {
                    break
                }
                currentDir = parent
                levels++
            }
            
            // Try each path
            for (secretsFile in searchPaths) {
                if (secretsFile.exists() && secretsFile.isFile) {
                    val properties = Properties()
                    secretsFile.inputStream().use { stream ->
                        properties.load(stream)
                    }
                    val value = properties.getProperty(key)?.takeIf { it.isNotBlank() }
                    if (value != null) {
                        AppLogger.i("SecretsLoader", "Found secret '$key' in ${secretsFile.absolutePath}")
                        return value
                    } else {
                        AppLogger.d("SecretsLoader", "File ${secretsFile.absolutePath} exists but key '$key' is missing or empty")
                    }
                }
            }
            
            AppLogger.w("SecretsLoader", "Secret '$key' not found. Searched ${searchPaths.size} paths:")
            searchPaths.forEach { path ->
                AppLogger.d("SecretsLoader", "  - ${path.absolutePath} (exists: ${path.exists()})")
            }
            null
        } catch (e: Exception) {
            AppLogger.w("SecretsLoader", "Failed to load secret '$key': ${e.message}", e)
            null
        }
    }
    
    /**
     * Checks if a secret key exists and has a non-empty value.
     * 
     * @param key The property key
     * @return true if the key exists and has a value, false otherwise
     */
    fun hasSecret(key: String): Boolean {
        return loadSecret(key) != null
    }
}

