package org.krypton.krypton.retrieval

import org.krypton.krypton.rag.RetrievedChunk
import org.krypton.krypton.web.WebSnippet

/**
 * Context retrieved from various sources for LLM generation.
 * 
 * @param localChunks Chunks retrieved from local notes (RAG)
 * @param webSnippets Snippets retrieved from web search (Tavily)
 */
data class RetrievalContext(
    val localChunks: List<RetrievedChunk> = emptyList(),
    val webSnippets: List<WebSnippet> = emptyList()
)

