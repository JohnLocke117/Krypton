package org.krypton.krypton.web

/**
 * Represents a web search result snippet.
 * 
 * @param title The title of the web page
 * @param url The URL of the web page
 * @param content The content snippet from the web page
 */
data class WebSnippet(
    val title: String,
    val url: String,
    val content: String
)

