package org.krypton.krypton.rag

import io.ktor.client.engine.*
import org.krypton.krypton.data.rag.impl.ChromaDBVectorStore
import org.krypton.krypton.data.rag.impl.HttpEmbedder
import org.krypton.krypton.data.rag.impl.HttpLlamaClient

/**
 * Extended RAG components with JVM-specific services.
 */
data class ExtendedRagComponents(
    val base: RagComponents,
    val healthService: ChromaDBHealthService,
    val vaultMetadataService: VaultMetadataService,
    val vaultSyncService: VaultSyncService
)

/**
 * JVM implementation of RAG component factory.
 */
actual fun createRagComponents(
    config: RagConfig,
    notesRoot: String?,
    httpClientEngine: HttpClientEngine
): RagComponents {
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
    
    // Create indexer with factory function for creating NoteFileSystem instances
    val indexer = Indexer(
        fileSystem = fileSystem,
        chunker = chunker,
        embedder = embedder,
        vectorStore = vectorStore,
        fileSystemFactory = { root -> NoteFileSystem(root) }
    )
    
    // Create RAG service
    val ragService = RagService(
        embedder = embedder,
        vectorStore = vectorStore,
        llamaClient = llamaClient
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
    
    // Set up indexer callback to update metadata
    indexer.onIndexingComplete = { vaultPath, indexedFiles ->
        vaultMetadataService.updateVaultMetadata(vaultPath, indexedFiles)
    }
    
    return RagComponents(
        vectorStore = vectorStore,
        embedder = embedder,
        llamaClient = llamaClient,
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
    httpClientEngine: HttpClientEngine
): ExtendedRagComponents {
    val base = createRagComponents(config, notesRoot, httpClientEngine)
    
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
    
    // Set up indexer callback to update metadata
    base.indexer.onIndexingComplete = { vaultPath, indexedFiles ->
        vaultMetadataService.updateVaultMetadata(vaultPath, indexedFiles)
    }
    
    return ExtendedRagComponents(
        base = base,
        healthService = healthService,
        vaultMetadataService = vaultMetadataService,
        vaultSyncService = vaultSyncService
    )
}

