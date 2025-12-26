package org.krypton.prompt

import org.krypton.chat.RetrievalMode
import org.krypton.rag.RagChunk
import org.krypton.web.WebSnippet

/**
 * Context for building prompts with retrieval results.
 * 
 * @param query The user's query
 * @param retrievalMode The retrieval mode used
 * @param localChunks Chunks from local notes (RAG)
 * @param webSnippets Snippets from web search
 */
data class PromptContext(
    val query: String,
    val retrievalMode: RetrievalMode,
    val localChunks: List<RagChunk>,
    val webSnippets: List<WebSnippet>
)

