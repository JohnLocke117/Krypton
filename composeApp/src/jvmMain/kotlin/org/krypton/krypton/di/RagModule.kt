package org.krypton.krypton.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.ktor.client.engine.*
import org.krypton.krypton.VectorBackend
import org.krypton.krypton.config.RagDefaults
import org.krypton.krypton.data.rag.impl.*
import org.krypton.krypton.rag.*
import org.krypton.krypton.data.repository.SettingsPersistence
import org.krypton.krypton.rag.getRagDatabasePath
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File

/**
 * RAG (Retrieval-Augmented Generation) dependency injection module.
 * 
 * Provides RAG components including vector stores, embedders, LLM clients, and indexers.
 */
val ragModule = module {
    // SQLDelight driver factory
    factory<(String) -> SqlDriver> {
        { dbPath: String ->
            val dbFile = File(dbPath)
            dbFile.parentFile?.mkdirs()
            val driver = JdbcSqliteDriver("jdbc:sqlite:$dbPath")
            NoteChunkDatabase.Schema.create(driver)
            driver
        }
    }
    
    // RAG components factory (optional - may be null if initialization fails)
    single<RagComponents?>(createdAtStart = false) {
        try {
            val settingsRepository: org.krypton.krypton.data.repository.SettingsRepository = get()
            val settingsPersistence: SettingsPersistence = get()
            val ragSettings = settingsRepository.settingsFlow.value.rag
            val dbPath = getRagDatabasePath(settingsPersistence)
            val httpEngine: HttpClientEngine = get()
            val sqlDriverFactory: (String) -> SqlDriver = get()
            
            val config = RagConfig(
                vectorBackend = ragSettings.vectorBackend,
                llamaBaseUrl = ragSettings.llamaBaseUrl,
                embeddingBaseUrl = ragSettings.embeddingBaseUrl,
                llamaModel = RagDefaults.DEFAULT_LLAMA_MODEL,
                embeddingModel = RagDefaults.DEFAULT_EMBEDDING_MODEL
            )
            
            // Get notes root from settings (if available)
            val notesRoot = null // TODO: Get from settings or current directory
            
            createRagComponents(
                config = config,
                dbPath = dbPath,
                notesRoot = notesRoot,
                httpClientEngine = httpEngine,
                sqlDriverFactory = sqlDriverFactory
            )
        } catch (e: Exception) {
            // If RAG initialization fails, return null
            // Chat will fall back to direct Ollama
            null
        }
    }
}

