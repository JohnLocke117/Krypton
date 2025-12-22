package org.krypton.krypton.rag

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import org.krypton.krypton.VectorBackend
import org.krypton.krypton.rag.NoteChunkDatabase
import java.io.File

/**
 * JVM implementation of RAG component factory.
 */
actual fun createRagComponents(
    config: RagConfig,
    dbPath: String,
    notesRoot: String?,
    httpClientEngine: HttpClientEngine,
    sqlDriverFactory: (String) -> SqlDriver
): RagComponents {
    // Create database driver
    val driver = sqlDriverFactory(dbPath)
    
    // Create vector store based on backend
    val vectorStore = when (config.vectorBackend) {
        VectorBackend.SQLITE_BRUTE_FORCE -> SqliteBruteForceVectorStore(driver)
        VectorBackend.SQLITE_VECTOR_EXTENSION -> SqliteVectorExtensionStore(driver)
    }
    
    // Create embedder
    val embedder = HttpEmbedder(
        baseUrl = config.embeddingBaseUrl,
        model = config.embeddingModel,
        apiPath = "/api/embeddings",
        httpClientEngine = httpClientEngine
    )
    
    // Create Llama client
    val llamaClient = HttpLlamaClient(
        baseUrl = config.llamaBaseUrl,
        model = config.llamaModel,
        apiPath = "/api/generate",
        httpClientEngine = httpClientEngine
    )
    
    // Create file system
    val fileSystem = NoteFileSystem(notesRoot)
    
    // Create chunker
    val chunker = MarkdownChunker()
    
    // Create indexer
    val indexer = Indexer(
        fileSystem = fileSystem,
        chunker = chunker,
        embedder = embedder,
        vectorStore = vectorStore
    )
    
    // Create RAG service
    val ragService = RagService(
        embedder = embedder,
        vectorStore = vectorStore,
        llamaClient = llamaClient
    )
    
    return RagComponents(
        vectorStore = vectorStore,
        embedder = embedder,
        llamaClient = llamaClient,
        indexer = indexer,
        ragService = ragService
    )
}

/**
 * Creates a SQLDelight driver for JVM.
 */
fun createSqlDriver(dbPath: String): SqlDriver {
    val dbFile = File(dbPath)
    // Ensure parent directory exists
    dbFile.parentFile?.mkdirs()
    
    val driver = JdbcSqliteDriver("jdbc:sqlite:$dbPath")
    
    // Create tables (SQLDelight creates them automatically, but we can ensure schema is up to date)
    NoteChunkDatabase.Schema.create(driver)
    
    return driver
}

