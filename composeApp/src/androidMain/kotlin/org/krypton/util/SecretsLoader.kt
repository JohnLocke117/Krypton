package org.krypton.util

import android.content.Context
import org.koin.core.context.GlobalContext
import org.koin.java.KoinJavaComponent.getKoin
import java.io.File
import java.util.Properties

/**
 * Android implementation of SecretsLoader.
 * Loads secrets from local.secrets.properties file.
 */
object SecretsLoader {
    /**
     * Loads a secret value from local.secrets.properties file.
     * 
     * @param key The secret key to load
     * @return The secret value, or null if not found
     */
    fun loadSecret(key: String): String? {
        return try {
            val context: Context = getKoin().get()
            val secretsFile = File(context.filesDir, "local.secrets.properties")
            
            if (secretsFile.exists()) {
                val props = Properties()
                props.load(secretsFile.inputStream())
                props.getProperty(key)?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.w("SecretsLoader", "Failed to load secret '$key': ${e.message}", e)
            null
        }
    }
    
    /**
     * Checks if a secret exists.
     */
    fun hasSecret(key: String): Boolean {
        return loadSecret(key) != null
    }
}

