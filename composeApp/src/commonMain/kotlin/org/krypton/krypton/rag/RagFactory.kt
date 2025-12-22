package org.krypton.krypton.rag

import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.engine.*
import org.krypton.krypton.VectorBackend

/**
 * Configuration for RAG components.
 */
data class RagConfig(
    val vectorBackend: VectorBackend,
    val llamaBaseUrl: String,
    val embeddingBaseUrl: String,
    val llamaModel: String = "llama3.2:1b",
    val embeddingModel: String = "nomic-embed-text:v1.5"
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

