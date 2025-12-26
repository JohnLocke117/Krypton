package org.krypton.retrieval

import org.krypton.rag.RagChunk
import org.krypton.web.WebSnippet

/**
 * Context retrieved from various sources for LLM generation.
 * 
 * @param localChunks Chunks retrieved from local notes (RAG)
 * @param webSnippets Snippets retrieved from web search
 */
data class RetrievalContext(
    val localChunks: List<RagChunk> = emptyList(),
    val webSnippets: List<WebSnippet> = emptyList()
)

