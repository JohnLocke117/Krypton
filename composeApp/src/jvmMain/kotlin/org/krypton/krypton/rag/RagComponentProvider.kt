package org.krypton.krypton.rag

import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import org.krypton.krypton.RagSettings
import org.krypton.krypton.config.RagDefaults
import org.krypton.krypton.data.repository.SettingsPersistence
import org.krypton.krypton.rag.getRagDatabasePath

/**
 * Helper class for creating and managing RAG components.
 * 
 * Extracts RAG initialization logic from App.kt to improve separation of concerns.
 * 
 * Note: This is now primarily used for backward compatibility. 
 * New code should use Koin DI (RagModule) instead.
 */
object RagComponentProvider {
    /**
     * Creates RAG components based on the provided settings and notes root.
     * 
     * @param ragSettings RAG settings from app configuration
     * @param notesRoot Root directory containing markdown notes (null = current directory)
     * @param settingsPersistence Settings persistence for getting database path
     * @return RagComponents if initialization succeeds, null otherwise
     */
    fun createRagComponents(
        ragSettings: RagSettings,
        notesRoot: String?,
        settingsPersistence: SettingsPersistence
    ): RagComponents? {
        return try {
            val dbPath = getRagDatabasePath(settingsPersistence)
            val httpEngine = CIO.create()
            
            val config = RagConfig(
                vectorBackend = ragSettings.vectorBackend,
                llamaBaseUrl = ragSettings.llamaBaseUrl,
                embeddingBaseUrl = ragSettings.embeddingBaseUrl,
                llamaModel = RagDefaults.DEFAULT_LLAMA_MODEL,
                embeddingModel = RagDefaults.DEFAULT_EMBEDDING_MODEL
            )
            
            createRagComponents(
                config = config,
                dbPath = dbPath,
                notesRoot = notesRoot,
                httpClientEngine = httpEngine,
                sqlDriverFactory = ::createSqlDriver
            )
        } catch (e: Exception) {
            // Log the error with context
            org.krypton.krypton.util.AppLogger.e(
                "RagComponentProvider",
                "Failed to initialize RAG components: ${e.message}. Chat will fall back to direct Ollama.",
                e
            )
            // If RAG initialization fails, return null
            // Chat will fall back to direct Ollama
            null
        }
    }
}

