package org.krypton.krypton.rag

import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.engine.*
import org.krypton.krypton.VectorBackend
import org.krypton.krypton.config.RagDefaults

/**
 * Configuration for RAG components.
 */
data class RagConfig(
    val vectorBackend: VectorBackend,
    val llamaBaseUrl: String,
    val embeddingBaseUrl: String,
    val llamaModel: String = RagDefaults.DEFAULT_LLAMA_MODEL,
    val embeddingModel: String = RagDefaults.DEFAULT_EMBEDDING_MODEL
)

/**
 * Container for all RAG components.
 */
data class RagComponents(
    val vectorStore: VectorStore,
    val embedder: Embedder,
    val llamaClient: LlamaClient,
    val indexer: Indexer,
    val ragService: RagService
)

/**
 * Factory for creating RAG components.
 * 
 * @param config RAG configuration
 * @param dbPath Path to the SQLite database file
 * @param notesRoot Root directory containing markdown notes (null = current directory)
 * @param httpClientEngine HTTP client engine (platform-specific)
 * @param sqlDriverFactory Factory for creating SQLDelight drivers (platform-specific)
 */
expect fun createRagComponents(
    config: RagConfig,
    dbPath: String,
    notesRoot: String?,
    httpClientEngine: HttpClientEngine,
    sqlDriverFactory: (String) -> SqlDriver
): RagComponents

