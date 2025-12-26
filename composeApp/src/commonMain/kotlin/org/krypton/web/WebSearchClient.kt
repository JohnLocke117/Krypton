package org.krypton.web

/**
 * Interface for web search clients.
 * 
 * Provides a unified interface for performing web searches
 * that can be used to retrieve context for LLM responses.
 */
interface WebSearchClient {
    /**
     * Performs a web search for the given query.
     * 
     * @param query The search query
     * @param maxResults Maximum number of results to return (default: 5)
     * @return List of web snippets with title, URL, and content
     */
    suspend fun search(
        query: String,
        maxResults: Int = 5
    ): List<WebSnippet>
}

