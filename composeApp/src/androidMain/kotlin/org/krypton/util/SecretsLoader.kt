package org.krypton.util

import android.content.Context
import org.krypton.krypton.BuildConfig
import org.koin.core.context.GlobalContext
import org.koin.java.KoinJavaComponent.getKoin
import java.io.File
import java.util.Properties

/**
 * Android implementation of SecretsLoader.
 * Loads secrets from local.properties file.
 * Checks BuildConfig first (injected at build time), then assets folder, then app's internal files directory.
 */
object SecretsLoader {
    /**
     * Loads a secret value from local.properties file.
     * 
     * @param key The secret key to load
     * @return The secret value, or null if not found
     */
    fun loadSecret(key: String): String? {
        return try {
            // Strategy 1: Check BuildConfig first (values injected from local.properties at build time)
            try {
                val buildConfigKey = key.uppercase().replace(".", "_").replace("-", "_")
                val buildConfigClass = BuildConfig::class.java
                val field = buildConfigClass.getField(buildConfigKey)
                val value = field.get(null) as? String
                if (!value.isNullOrBlank()) {
                    AppLogger.d("SecretsLoader", "Found secret '$key' in BuildConfig")
                    return value
                }
            } catch (e: NoSuchFieldException) {
                AppLogger.d("SecretsLoader", "Secret '$key' not found in BuildConfig: ${e.message}")
            } catch (e: Exception) {
                AppLogger.d("SecretsLoader", "Failed to read BuildConfig for '$key': ${e.message}")
            }
            
            val context: Context = getKoin().get()
            
            // Strategy 2: Check assets folder (bundled with app)
            try {
                context.assets.open("local.properties").use { inputStream ->
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
            
            // Strategy 3: Check app's internal files directory
            val secretsFile = File(context.filesDir, "local.properties")
            if (secretsFile.exists()) {
                val props = Properties()
                props.load(secretsFile.inputStream())
                val value = props.getProperty(key)?.takeIf { it.isNotBlank() }
                if (value != null) {
                    AppLogger.d("SecretsLoader", "Found secret '$key' in files directory")
                    return value
                }
            }
            
            AppLogger.w("SecretsLoader", "Secret '$key' not found in BuildConfig, assets, or files directory")
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

