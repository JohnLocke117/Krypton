package org.krypton.util

import android.content.Context
import org.koin.core.context.GlobalContext
import org.koin.java.KoinJavaComponent.getKoin
import java.io.File
import java.util.Properties

/**
 * Android implementation of SecretsLoader.
 * Loads secrets from local.secrets.properties file.
 * Checks assets folder first, then app's internal files directory.
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
            
            // Strategy 1: Check assets folder first (bundled with app)
            try {
                context.assets.open("local.secrets.properties").use { inputStream ->
                    val props = Properties()
                    props.load(inputStream)
                    val value = props.getProperty(key)?.takeIf { it.isNotBlank() }
                    if (value != null) {
                        AppLogger.d("SecretsLoader", "Found secret '$key' in assets")
                        return value
                    }
                }
            } catch (e: Exception) {
                AppLogger.d("SecretsLoader", "Secret '$key' not found in assets: ${e.message}")
            }
            
            // Strategy 2: Check app's internal files directory
            val secretsFile = File(context.filesDir, "local.secrets.properties")
            if (secretsFile.exists()) {
                val props = Properties()
                props.load(secretsFile.inputStream())
                val value = props.getProperty(key)?.takeIf { it.isNotBlank() }
                if (value != null) {
                    AppLogger.d("SecretsLoader", "Found secret '$key' in files directory")
                    return value
                }
            }
            
            AppLogger.w("SecretsLoader", "Secret '$key' not found in assets or files directory")
            null
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

