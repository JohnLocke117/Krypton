package org.krypton.krypton.chat

/**
 * Retrieval mode for chat responses.
 * 
 * Determines which retrieval sources are used when answering user questions.
 */
enum class RetrievalMode {
    /**
     * No retrieval - plain chat without any context.
     */
    NONE,
    
    /**
     * RAG only - uses local notes via vector search.
     */
    RAG,
    
    /**
     * Web search only - uses Tavily web search.
     */
    WEB,
    
    /**
     * Hybrid - uses both RAG (local notes) and web search.
     */
    HYBRID
}

