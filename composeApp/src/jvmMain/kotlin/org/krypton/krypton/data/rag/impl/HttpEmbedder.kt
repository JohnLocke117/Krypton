package org.krypton.krypton.data.rag.impl

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.krypton.krypton.rag.Embedder
import org.krypton.krypton.util.AppLogger
import kotlin.time.Duration.Companion.seconds

/**
 * HTTP-based embedder that works with Ollama, Llama Stack, or similar APIs.
 * 
 * @param baseUrl Base URL of the embedding service (e.g., "http://localhost:11434")
 * @param model Model name (e.g., "nomic-embed-text:v1.5")
 * @param apiPath API endpoint path (default: "/api/embeddings" for Ollama)
 * @param httpClientEngine HTTP client engine (platform-specific)
 */
class HttpEmbedder(
    private val baseUrl: String,
    private val model: String,
    private val apiPath: String = "/api/embeddings",
    httpClientEngine: HttpClientEngine
) : Embedder {
    
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
    
    private val maxRetries = 3
    private val initialRetryDelayMs = 500L
    
    @Serializable
    private data class EmbeddingRequest(
        val model: String,
        val prompt: String
    )
    
    @Serializable
    private data class EmbeddingResponse(
        val embedding: List<Float>? = null,
        val embeddings: List<List<Float>>? = null, // Some APIs return multiple embeddings
        val error: String? = null
    )
    
    override suspend fun embed(texts: List<String>): List<FloatArray> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) {
            return@withContext emptyList()
        }
        
        val url = "$baseUrl$apiPath"
        AppLogger.d("HttpEmbedder", "Embedding request started: $url, model=$model, count=${texts.size}")
        
        val embeddings = mutableListOf<FloatArray>()
        
        // Process texts one by one (some APIs may support batch, but we'll do one at a time for compatibility)
        for ((index, text) in texts.withIndex()) {
            var lastException: Exception? = null
            var retryCount = 0
            
            while (retryCount <= maxRetries) {
                try {
                    val request = EmbeddingRequest(
                        model = model,
                        prompt = text
                    )
                    
                    val response: EmbeddingResponse = client.post(url) {
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }.body()
                    
                    if (response.error != null) {
                        val errorMsg = "Embedding API returned error: ${response.error}"
                        AppLogger.e("HttpEmbedder", errorMsg, null)
                        throw EmbeddingException(errorMsg)
                    }
                    
                    // Handle different response formats
                    val embedding = when {
                        response.embedding != null -> {
                            response.embedding.toFloatArray()
                        }
                        response.embeddings != null && response.embeddings.isNotEmpty() -> {
                            // If multiple embeddings returned, use the first one
                            response.embeddings[0].toFloatArray()
                        }
                        else -> {
                            throw EmbeddingException("No embedding found in API response")
                        }
                    }
                    
                    embeddings.add(embedding)
                    if (index == texts.size - 1) {
                        AppLogger.i("HttpEmbedder", "Embedding request succeeded: generated ${embeddings.size} embeddings")
                    }
                    break // Success, exit retry loop
                } catch (e: Exception) {
                    lastException = e
                    retryCount++
                    
                    if (retryCount <= maxRetries) {
                        val delayMs = initialRetryDelayMs * (1 shl (retryCount - 1)) // Exponential backoff
                        AppLogger.w("HttpEmbedder", "Embedding request failed (attempt $retryCount/$maxRetries), retrying in ${delayMs}ms: ${e.message}", e)
                        delay(delayMs)
                    } else {
                        // Final failure after all retries
                        val errorMsg = if (e is EmbeddingException) {
                            e.message ?: "Embedding failed"
                        } else {
                            "Failed to generate embedding after $maxRetries retries: ${e.message}"
                        }
                        AppLogger.e("HttpEmbedder", errorMsg, e)
                        throw EmbeddingException(errorMsg, e)
                    }
                }
            }
            
            // If we exhausted retries, throw the last exception
            if (lastException != null && retryCount > maxRetries) {
                throw lastException
            }
        }
        
        return@withContext embeddings
    }
}

/**
 * Exception thrown when embedding generation fails.
 */
class EmbeddingException(message: String, cause: Throwable? = null) : Exception(message, cause)

