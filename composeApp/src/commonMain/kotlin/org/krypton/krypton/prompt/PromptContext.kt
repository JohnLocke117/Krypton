package org.krypton.krypton.prompt

import org.krypton.krypton.chat.RetrievalMode
import org.krypton.krypton.rag.RetrievedChunk
import org.krypton.krypton.web.WebSnippet

/**
 * Context for building prompts with retrieval results.
 * 
 * @param query The user's query
 * @param retrievalMode The retrieval mode used
 * @param localChunks Chunks from local notes (RAG)
 * @param webSnippets Snippets from web search (Tavily)
 */
data class PromptContext(
    val query: String,
    val retrievalMode: RetrievalMode,
    val localChunks: List<RetrievedChunk>,
    val webSnippets: List<WebSnippet>
)

