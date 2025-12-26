package org.krypton.chat.rag

import org.krypton.rag.RagChunk

/**
 * Context retrieved from local notes via RAG (Retrieval-Augmented Generation).
 * 
 * Contains chunks retrieved from vector search over local notes.
 * Used by [org.krypton.prompt.PromptBuilder] to build prompts with local context.
 * 
 * @param chunks Retrieved chunks from local notes
 * @param query The query that was used for retrieval
 * @param retrievedAt Timestamp when retrieval occurred (milliseconds since epoch), or null if not available
 */
data class RagContext(
    val chunks: List<RagChunk>,
    val query: String,
    val retrievedAt: Long? = null
)

