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

