package org.krypton.rag

import io.ktor.client.engine.*
import org.krypton.data.rag.impl.ChromaDBVectorStore
import org.krypton.data.rag.impl.HttpEmbedder
import org.krypton.data.rag.impl.HttpLlamaClient
import org.krypton.data.rag.impl.RagServiceImpl
import org.krypton.rag.EmbeddingTextSanitizer

/**
 * Extended RAG components with JVM-specific services.
 */
data class ExtendedRagComponents(
    val base: RagComponents,
    val healthService: ChromaDBHealthService,
    val vaultMetadataService: VaultMetadataService,
    val vaultSyncService: VaultSyncService,
    val vaultWatcher: VaultWatcher
)

/**
 * Legacy JVM implementation - kept for backward compatibility.
 * 
 * @deprecated Use createRagComponents with HttpClientEngineFactory instead
 */
@Deprecated("Use createRagComponents with HttpClientEngineFactory", ReplaceWith("createRagComponents(config, notesRoot, HttpClientEngineFactory(httpClientEngine), llamaClient, reranker)"))
fun createRagComponentsLegacy(
    config: RagConfig,
    notesRoot: String?,
    httpClientEngine: HttpClientEngine,
    llamaClient: LlamaClient?,
    reranker: Reranker?
): RagComponents {
    // Create ChromaDB vector store
    val vectorStore = ChromaDBVectorStore(
        baseUrl = config.chromaBaseUrl,
        collectionName = config.chromaCollectionName,
        httpClientEngine = httpClientEngine,
        tenant = config.chromaTenant,
        database = config.chromaDatabase
    )
    
    // Create embedder with sanitizer (using defaults for fallback)
    val sanitizer = EmbeddingTextSanitizer(
        maxTokens = org.krypton.config.RagDefaults.Embedding.DEFAULT_EMBEDDING_MAX_TOKENS,
        maxChars = org.krypton.config.RagDefaults.Embedding.DEFAULT_EMBEDDING_MAX_CHARS
    )
    val embedder = HttpEmbedder(
        baseUrl = config.embeddingBaseUrl,
        model = config.embeddingModel,
        apiPath = "/api/embed",
        sanitizer = sanitizer,
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
    
    // Create JVM-specific services
    val healthService = ChromaDBHealthService(
        baseUrl = config.chromaBaseUrl,
        collectionName = config.chromaCollectionName,
        httpClientEngine = httpClientEngine,
        tenant = config.chromaTenant,
        database = config.chromaDatabase
    )
    
    val vaultMetadataService = VaultMetadataService(
        baseUrl = config.chromaBaseUrl,
        metadataCollectionName = "vault_metadata",
        httpClientEngine = httpClientEngine,
        tenant = config.chromaTenant,
        database = config.chromaDatabase
    )
    
    val vaultSyncService = VaultSyncService(
        vaultMetadataService = vaultMetadataService,
        healthService = healthService,
        vectorStore = vectorStore
    )
    
    // Set up indexer callback to update metadata (will be updated in createExtendedRagComponents)
    
    return RagComponents(
        vectorStore = vectorStore,
        embedder = embedder,
        llamaClient = llamaClientToUse,
        indexer = indexer,
        ragService = ragService
    )
}

/**
 * Creates extended RAG components with JVM-specific services.
 */
fun createExtendedRagComponents(
    config: RagConfig,
    notesRoot: String?,
    httpClientEngine: HttpClientEngine,
    vectorStore: VectorStore? = null
): ExtendedRagComponents {
    val base = createRagComponents(
        config = config,
        notesRoot = notesRoot,
        httpClientEngineFactory = HttpClientEngineFactory(httpClientEngine),
        vectorStore = vectorStore
    )
    
    val healthService = ChromaDBHealthService(
        baseUrl = config.chromaBaseUrl,
        collectionName = config.chromaCollectionName,
        httpClientEngine = httpClientEngine,
        tenant = config.chromaTenant,
        database = config.chromaDatabase
    )
    
    val vaultMetadataService = VaultMetadataService(
        baseUrl = config.chromaBaseUrl,
        metadataCollectionName = "vault_metadata",
        httpClientEngine = httpClientEngine,
        tenant = config.chromaTenant,
        database = config.chromaDatabase
    )
    
    val vaultSyncService = VaultSyncService(
        vaultMetadataService = vaultMetadataService,
        healthService = healthService,
        vectorStore = base.vectorStore
    )
    
    val vaultWatcher = JvmVaultWatcher()
    
    // Set up indexer callback to update metadata with hashes (hash-only, no timestamps)
    // Cast to Indexer to access onIndexingComplete callback
    (base.indexer as? Indexer)?.onIndexingComplete = { vaultPath, indexedFiles, indexedFileHashes ->
        // Only use indexedFileHashes (hash-only tracking)
        vaultMetadataService.updateVaultMetadata(vaultPath, indexedFileHashes)
    }
    
    return ExtendedRagComponents(
        base = base,
        healthService = healthService,
        vaultMetadataService = vaultMetadataService,
        vaultSyncService = vaultSyncService,
        vaultWatcher = vaultWatcher
    )
}

