package org.krypton.krypton.retrieval

import org.krypton.krypton.chat.RetrievalMode

/**
 * Service that retrieves context from various sources based on retrieval mode.
 * 
 * Orchestrates retrieval from local notes (RAG) and/or web search
 * depending on the specified retrieval mode.
 */
interface RetrievalService {
    /**
     * Retrieves context for the given query based on the retrieval mode.
     * 
     * @param query The user's query
     * @param mode The retrieval mode (NONE, RAG, WEB, or HYBRID)
     * @return RetrievalContext containing local chunks and/or web snippets
     */
    suspend fun retrieve(
        query: String,
        mode: RetrievalMode
    ): RetrievalContext
}

