package org.krypton.krypton.web.impl

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.krypton.krypton.web.WebSearchClient
import org.krypton.krypton.web.WebSnippet
import org.krypton.krypton.util.AppLogger

/**
 * Tavily web search client implementation.
 * 
 * @param apiKey Tavily API key
 * @param baseUrl Base URL for Tavily API (default: "https://api.tavily.com")
 * @param httpClientEngine HTTP client engine
 */
class TavilyClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.tavily.com",
    httpClientEngine: HttpClientEngine
) : WebSearchClient {
    
    private val client = HttpClient(httpClientEngine) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000 // 30 seconds
            connectTimeoutMillis = 10_000 // 10 seconds
            socketTimeoutMillis = 30_000 // 30 seconds
        }
    }
    
    @Serializable
    private data class TavilySearchRequest(
        val query: String,
        val search_depth: String = "basic",
        val max_results: Int = 5,
        val include_answer: Boolean = false,
        val include_raw_content: Boolean = false,
        val include_images: Boolean = false
    )
    
    @Serializable
    private data class TavilyResult(
        val title: String = "",
        val url: String = "",
        val content: String = ""
    )
    
    @Serializable
    private data class TavilySearchResponse(
        val results: List<TavilyResult> = emptyList()
    )
    
    override suspend fun search(
        query: String,
        maxResults: Int
    ): List<WebSnippet> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/search"
            AppLogger.d("TavilyClient", "Searching: query=\"$query\", maxResults=$maxResults")
            
            val request = TavilySearchRequest(
                query = query,
                search_depth = "basic",
                max_results = maxResults,
                include_answer = false,
                include_raw_content = false,
                include_images = false
            )
            
            val httpResponse = client.post(url) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(request)
            }
            
            if (httpResponse.status.value !in 200..299) {
                val errorMsg = "Tavily API returned error: ${httpResponse.status}"
                AppLogger.e("TavilyClient", errorMsg, null)
                throw TavilyException(errorMsg)
            }
            
            AppLogger.i("TavilyClient", "Search succeeded: status=${httpResponse.status.value}")
            
            // Parse JSON response
            val json = Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            }
            
            val rawResponse: String = httpResponse.body<String>()
            if (rawResponse.isBlank()) {
                AppLogger.w("TavilyClient", "Tavily API returned empty response")
                return@withContext emptyList()
            }
            
            // Parse the response
            val response = json.decodeFromString<TavilySearchResponse>(rawResponse)
            
            val snippets = response.results.map { result ->
                WebSnippet(
                    title = result.title,
                    url = result.url,
                    content = result.content
                )
            }
            
            AppLogger.d("TavilyClient", "Retrieved ${snippets.size} web snippets")
            return@withContext snippets
            
        } catch (e: TavilyException) {
            AppLogger.e("TavilyClient", "Tavily search failed: ${e.message}", e)
            throw e
        } catch (e: Exception) {
            val errorMsg = "Failed to perform Tavily search: ${e.message}"
            AppLogger.e("TavilyClient", errorMsg, e)
            throw TavilyException(errorMsg, e)
        }
    }
}

/**
 * Exception thrown when Tavily search fails.
 */
class TavilyException(message: String, cause: Throwable? = null) : Exception(message, cause)

