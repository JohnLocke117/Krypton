package org.krypton.chat

/**
 * Retrieval mode for chat responses.
 * 
 * Determines which retrieval sources are used when answering user questions.
 * Used by [ChatService] to specify how context should be retrieved for generating responses.
 * 
 * The mode affects what information is available to the LLM when generating a response:
 * - NONE: No additional context, just the conversation history
 * - RAG: Context from local notes via vector search
 * - WEB: Context from web search services
 * - HYBRID: Context from both local notes and web search
 */
enum class RetrievalMode {
    /**
     * No retrieval - plain chat without any context.
     * 
     * The LLM will only use the conversation history to generate responses.
     * No external sources (local notes or web) are consulted.
     */
    NONE,
    
    /**
     * RAG only - uses local notes via vector search.
     * 
     * Retrieves relevant chunks from local notes using vector similarity search.
     * The retrieved chunks are provided as context to the LLM.
     */
    RAG,
    
    /**
     * Web search only - uses web search services.
     * 
     * Performs a web search query and uses the results as context for the LLM.
     * Useful for questions requiring current or external information.
     */
    WEB,
    
    /**
     * Hybrid - uses both RAG (local notes) and web search.
     * 
     * Combines context from both local notes and web search to provide
     * comprehensive information to the LLM.
     */
    HYBRID
}

