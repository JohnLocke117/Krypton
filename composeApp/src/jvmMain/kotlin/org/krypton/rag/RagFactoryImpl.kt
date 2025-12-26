package org.krypton.rag

import io.ktor.client.engine.*
import org.krypton.data.rag.impl.ChromaDBVectorStore
import org.krypton.data.rag.impl.HttpEmbedder
import org.krypton.data.rag.impl.HttpLlamaClient
import org.krypton.data.rag.impl.RagServiceImpl

/**
 * JVM implementation of HTTP client engine factory.
 * Wraps Ktor's HttpClientEngine.
 */
actual class HttpClientEngineFactory(
    val engine: HttpClientEngine
)

/**
 * JVM implementation of RAG component factory.
 */
actual fun createRagComponents(
    config: RagConfig,
    notesRoot: String?,
    httpClientEngineFactory: HttpClientEngineFactory,
    llamaClient: LlamaClient?,
    reranker: Reranker?
): RagComponents {
    val httpClientEngine = httpClientEngineFactory.engine
    
    // Create ChromaDB vector store
    val vectorStore = ChromaDBVectorStore(
        baseUrl = config.chromaBaseUrl,
        collectionName = config.chromaCollectionName,
        httpClientEngine = httpClientEngine,
        tenant = config.chromaTenant,
        database = config.chromaDatabase
    )
    
    // Create embedder
    val embedder = HttpEmbedder(
        baseUrl = config.embeddingBaseUrl,
        model = config.embeddingModel,
        apiPath = "/api/embed",
        httpClientEngine = httpClientEngine
    )
    
    // Use provided LlamaClient or create a new one
    val llamaClientToUse = llamaClient ?: HttpLlamaClient(
        baseUrl = config.llamaBaseUrl,
        model = config.llamaModel,
        apiPath = "/api/generate",
        httpClientEngine = httpClientEngine
    )
    
    // Create file system
    val fileSystem = NoteFileSystem(notesRoot)
    
    // Create chunker
    val chunker = MarkdownChunkerImpl()
    
    // Create JVM-specific indexer with factory function for creating NoteFileSystem instances
    val indexer = JvmIndexer(
        fileSystem = fileSystem,
        chunker = chunker,
        embedder = embedder,
        vectorStore = vectorStore,
        fileSystemFactory = { root -> NoteFileSystem(root) }
    )
    
    // Create query preprocessor (optional, only if rewriting or multi-query is enabled)
    val queryPreprocessor = if (config.queryRewritingEnabled || config.multiQueryEnabled) {
        QueryPreprocessor(llamaClientToUse)
    } else {
        null
    }
    
    // Use provided Reranker or create NoopReranker
    val rerankerToUse = reranker ?: NoopReranker()
    
    // Create RAG service
    val ragService = RagServiceImpl(
        embedder = embedder,
        vectorStore = vectorStore,
        llamaClient = llamaClientToUse,
        similarityThreshold = config.similarityThreshold,
        maxK = config.maxK,
        displayK = config.displayK,
        queryPreprocessor = queryPreprocessor,
        queryRewritingEnabled = config.queryRewritingEnabled,
        multiQueryEnabled = config.multiQueryEnabled,
        reranker = rerankerToUse
    )
    
    return RagComponents(
        vectorStore = vectorStore,
        embedder = embedder,
        llamaClient = llamaClientToUse,
        indexer = indexer,
        ragService = ragService
    )
}

