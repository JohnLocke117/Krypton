package org.krypton.krypton.rag

import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import org.krypton.krypton.RagSettings
import org.krypton.krypton.rag.getRagDatabasePath

/**
 * Helper class for creating and managing RAG components.
 * 
 * Extracts RAG initialization logic from App.kt to improve separation of concerns.
 */
object RagComponentProvider {
    /**
     * Creates RAG components based on the provided settings and notes root.
     * 
     * @param ragSettings RAG settings from app configuration
     * @param notesRoot Root directory containing markdown notes (null = current directory)
     * @return RagComponents if initialization succeeds, null otherwise
     */
    fun createRagComponents(
        ragSettings: RagSettings,
        notesRoot: String?
    ): RagComponents? {
        return try {
            val dbPath = getRagDatabasePath()
            val httpEngine = CIO.create()
            
            val config = RagConfig(
                vectorBackend = ragSettings.vectorBackend,
                llamaBaseUrl = ragSettings.llamaBaseUrl,
                embeddingBaseUrl = ragSettings.embeddingBaseUrl,
                llamaModel = RagConstants.DEFAULT_LLAMA_MODEL,
                embeddingModel = RagConstants.DEFAULT_EMBEDDING_MODEL
            )
            
            createRagComponents(
                config = config,
                dbPath = dbPath,
                notesRoot = notesRoot,
                httpClientEngine = httpEngine,
                sqlDriverFactory = ::createSqlDriver
            )
        } catch (e: Exception) {
            // If RAG initialization fails, return null
            // Chat will fall back to direct Ollama
            null
        }
    }
}

