package org.krypton.platform

import android.content.Context
import android.content.SharedPreferences

/**
 * Android implementation of SettingsConfigProvider using SharedPreferences.
 */
class AndroidSettingsConfigProvider(
    private val context: Context
) : SettingsConfigProvider {
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("krypton_config", Context.MODE_PRIVATE)
    }
    
    private val settingsFileName = "settings.json"
    
    override fun getSettingsFilePath(): String {
        // Check if custom path is set
        val customPath = prefs.getString("settings_file_path", null)
        if (customPath != null) {
            return customPath
        }
        
        // Default to app's internal files directory
        return context.filesDir.resolve(settingsFileName).absolutePath
    }
    
    override fun setSettingsFilePath(path: String): Boolean {
        return try {
            prefs.edit().putString("settings_file_path", path).apply()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override fun getConfigDirectory(): String {
        return context.filesDir.absolutePath
    }
}

