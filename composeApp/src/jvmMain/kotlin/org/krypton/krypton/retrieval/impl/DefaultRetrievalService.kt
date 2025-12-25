package org.krypton.krypton.retrieval.impl

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Deferred
import org.krypton.krypton.chat.RetrievalMode
import org.krypton.krypton.rag.RetrievedChunk
import org.krypton.krypton.retrieval.RagRetriever
import org.krypton.krypton.retrieval.RetrievalContext
import org.krypton.krypton.retrieval.RetrievalService
import org.krypton.krypton.web.WebSearchClient
import org.krypton.krypton.web.WebSnippet
import org.krypton.krypton.util.AppLogger

/**
 * Default implementation of RetrievalService.
 * 
 * Orchestrates retrieval from local notes (RAG) and/or web search
 * based on the specified retrieval mode.
 */
class DefaultRetrievalService(
    private val ragRetriever: RagRetriever?,
    private val webSearchClient: WebSearchClient?
) : RetrievalService {
    
    override suspend fun retrieve(
        query: String,
        mode: RetrievalMode
    ): RetrievalContext = coroutineScope {
        when (mode) {
            RetrievalMode.NONE -> {
                AppLogger.d("DefaultRetrievalService", "Mode: NONE - no retrieval")
                RetrievalContext()
            }
            
            RetrievalMode.RAG -> {
                AppLogger.d("DefaultRetrievalService", "Mode: RAG - local notes only")
                if (ragRetriever == null) {
                    AppLogger.w("DefaultRetrievalService", "RAG requested but RagRetriever is not available")
                    RetrievalContext()
                } else {
                    val localChunks = ragRetriever.retrieveChunks(query)
                    RetrievalContext(localChunks = localChunks)
                }
            }
            
            RetrievalMode.WEB -> {
                AppLogger.d("DefaultRetrievalService", "Mode: WEB - Tavily search only")
                if (webSearchClient == null) {
                    AppLogger.w("DefaultRetrievalService", "Web search requested but WebSearchClient is not available")
                    RetrievalContext()
                } else {
                    try {
                        val webSnippets = webSearchClient.search(query)
                        RetrievalContext(webSnippets = webSnippets)
                    } catch (e: Exception) {
                        AppLogger.e("DefaultRetrievalService", "Web search failed: ${e.message}", e)
                        RetrievalContext() // Return empty context on failure
                    }
                }
            }
            
            RetrievalMode.HYBRID -> {
                AppLogger.d("DefaultRetrievalService", "Mode: HYBRID - RAG + web search")
                // Run both retrievals in parallel for performance
                val localChunksDeferred: Deferred<List<RetrievedChunk>>? = if (ragRetriever != null) {
                    async { ragRetriever.retrieveChunks(query) }
                } else {
                    null
                }
                
                val webSnippetsDeferred: Deferred<List<WebSnippet>>? = if (webSearchClient != null) {
                    async {
                        try {
                            webSearchClient.search(query)
                        } catch (e: Exception) {
                            AppLogger.e("DefaultRetrievalService", "Web search failed in hybrid mode: ${e.message}", e)
                            emptyList()
                        }
                    }
                } else {
                    null
                }
                
                val localChunks = localChunksDeferred?.await() ?: emptyList()
                val webSnippets = webSnippetsDeferred?.await() ?: emptyList()
                
                RetrievalContext(
                    localChunks = localChunks,
                    webSnippets = webSnippets
                )
            }
        }
    }
}

