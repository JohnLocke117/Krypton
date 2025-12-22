package org.krypton.krypton.rag

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
    }
    
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
        
        val embeddings = mutableListOf<FloatArray>()
        
        // Process texts one by one (some APIs may support batch, but we'll do one at a time for compatibility)
        for (text in texts) {
            try {
                val request = EmbeddingRequest(
                    model = model,
                    prompt = text
                )
                
                val response: EmbeddingResponse = client.post("$baseUrl$apiPath") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.body()
                
                if (response.error != null) {
                    throw EmbeddingException("Embedding API returned error: ${response.error}")
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
            } catch (e: Exception) {
                if (e is EmbeddingException) {
                    throw e
                }
                throw EmbeddingException("Failed to generate embedding: ${e.message}", e)
            }
        }
        
        return@withContext embeddings
    }
}

/**
 * Exception thrown when embedding generation fails.
 */
class EmbeddingException(message: String, cause: Throwable? = null) : Exception(message, cause)

