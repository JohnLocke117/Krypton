package org.krypton.krypton.rag

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
    val chromaBaseUrl: String = RagDefaults.DEFAULT_CHROMA_BASE_URL,
    val chromaCollectionName: String = RagDefaults.DEFAULT_CHROMA_COLLECTION_NAME,
    val chromaTenant: String = RagDefaults.DEFAULT_CHROMA_TENANT,
    val chromaDatabase: String = RagDefaults.DEFAULT_CHROMA_DATABASE,
    val llamaModel: String = RagDefaults.DEFAULT_LLAMA_MODEL,
    val embeddingModel: String = RagDefaults.DEFAULT_EMBEDDING_MODEL,
    val similarityThreshold: Float = RagDefaults.DEFAULT_SIMILARITY_THRESHOLD,
    val maxK: Int = RagDefaults.DEFAULT_MAX_K,
    val displayK: Int = RagDefaults.DEFAULT_DISPLAY_K,
    val queryRewritingEnabled: Boolean = false,
    val multiQueryEnabled: Boolean = false
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
 * @param notesRoot Root directory containing markdown notes (null = current directory)
 * @param httpClientEngine HTTP client engine (platform-specific)
 */
expect fun createRagComponents(
    config: RagConfig,
    notesRoot: String?,
    httpClientEngine: HttpClientEngine
): RagComponents

