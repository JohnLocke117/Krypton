package org.krypton.chat.web

import org.krypton.web.WebSnippet

/**
 * Context retrieved from web search.
 * 
 * Contains snippets retrieved from web search services.
 * Used by [org.krypton.prompt.PromptBuilder] to build prompts with web context.
 * 
 * @param snippets Retrieved web snippets
 * @param query The query that was used for search
 * @param retrievedAt Timestamp when search occurred (milliseconds since epoch), or null if not available
 */
data class WebContext(
    val snippets: List<WebSnippet>,
    val query: String,
    val retrievedAt: Long? = null
)

