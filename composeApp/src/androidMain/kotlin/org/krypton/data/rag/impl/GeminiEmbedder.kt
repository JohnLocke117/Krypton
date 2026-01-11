package org.krypton.data.rag.impl

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
import org.krypton.rag.Embedder
import org.krypton.rag.models.Embedding
import org.krypton.util.AppLogger

/**
 * Gemini embedding API client for Android.
 * 
 * Uses Gemini's embedding API to generate embeddings for queries only.
 * Document embedding is not supported on Android (indexing happens on Desktop).
 * 
 * @param apiKey Gemini API key from secrets (same as GeminiClient)
 * @param baseUrl Base URL for Gemini API (default: "https://generativelanguage.googleapis.com/v1beta")
 * @param model Embedding model name (default: "gemini-embedding-001")
 * @param outputDimension Output dimension for embeddings (default: 768 to match Desktop/collection dimension)
 * @param httpClientEngine HTTP client engine (platform-specific)
 */
class GeminiEmbedder(
    private val apiKey: String,
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
    private val model: String = "gemini-embedding-001",
    private val outputDimension: Int = 768,
    httpClientEngine: HttpClientEngine
) : Embedder {
    
    private val embeddingUrl = "$baseUrl/models/$model:embedContent"
    
    private val client = HttpClient(httpClientEngine) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
                encodeDefaults = true // Ensure all fields are encoded, including output_dimensionality
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
    private data class EmbeddingContentPart(
        val text: String
    )
    
    @Serializable
    private data class EmbeddingContent(
        val parts: List<EmbeddingContentPart>
    )
    
    @Serializable
    private data class EmbeddingRequest(
        val content: EmbeddingContent,
        val output_dimensionality: Int = 768
    )
    
    @Serializable
    private data class EmbeddingValue(
        val values: List<Float>
    )
    
    @Serializable
    private data class EmbeddingResponse(
        val embedding: EmbeddingValue? = null,
        val error: EmbeddingError? = null
    )
    
    @Serializable
    private data class EmbeddingError(
        val message: String? = null,
        val code: Int? = null
    )
    
    override suspend fun embedQuery(text: String): Embedding = withContext(Dispatchers.IO) {
        AppLogger.d("GeminiEmbedder", "Embedding query text (length=${text.length}, requested_dimension=$outputDimension)")
        
        var lastException: Exception? = null
        var retryCount = 0
        
        while (retryCount <= maxRetries) {
            try {
                val request = EmbeddingRequest(
                    content = EmbeddingContent(
                        parts = listOf(
                            EmbeddingContentPart(text = text)
                        )
                    ),
                    output_dimensionality = outputDimension
                )
                
                AppLogger.d("GeminiEmbedder", "Sending embedding request with output_dimensionality=$outputDimension")
                
                val httpResponse = client.post(embeddingUrl) {
                    contentType(ContentType.Application.Json)
                    header("x-goog-api-key", apiKey)
                    setBody(request)
                }
                
                if (httpResponse.status.value !in 200..299) {
                    val errorBody = try {
                        httpResponse.body<EmbeddingResponse>()
                    } catch (e: Exception) {
                        null
                    }
                    
                    val errorMsg = when {
                        httpResponse.status.value == 401 || httpResponse.status.value == 403 -> {
                            "Invalid or missing Gemini API key"
                        }
                        errorBody?.error != null -> {
                            errorBody.error.message ?: "Gemini embedding API error: ${httpResponse.status}"
                        }
                        else -> {
                            "Gemini embedding API returned error: ${httpResponse.status}"
                        }
                    }
                    
                    AppLogger.e("GeminiEmbedder", errorMsg, null)
                    throw EmbeddingException(errorMsg)
                }
                
                AppLogger.d("GeminiEmbedder", "Embedding request succeeded: status=${httpResponse.status.value}")
                
                val response: EmbeddingResponse = httpResponse.body()
                
                if (response.error != null) {
                    val errorMsg = response.error.message ?: "Gemini embedding API error"
                    AppLogger.e("GeminiEmbedder", errorMsg, null)
                    throw EmbeddingException(errorMsg)
                }
                
                val embedding = response.embedding
                if (embedding == null || embedding.values.isEmpty()) {
                    throw EmbeddingException("Gemini embedding API returned no embedding vector")
                }
                
                val actualDimension = embedding.values.size
                AppLogger.d("GeminiEmbedder", "Successfully generated embedding (requested=$outputDimension, actual=$actualDimension)")
                
                // Warn if dimension doesn't match (but still return the embedding)
                if (actualDimension != outputDimension) {
                    AppLogger.w("GeminiEmbedder", "Gemini returned dimension $actualDimension but requested $outputDimension. The API may not support the requested dimension.")
                }
                
                return@withContext Embedding(vector = embedding.values)
                
            } catch (e: EmbeddingException) {
                lastException = e
                retryCount++
                
                if (retryCount <= maxRetries) {
                    val delayMs = initialRetryDelayMs * (1 shl (retryCount - 1)) // Exponential backoff
                    AppLogger.w("GeminiEmbedder", "Embedding request failed (attempt $retryCount/$maxRetries), retrying in ${delayMs}ms: ${e.message}", e)
                    delay(delayMs)
                } else {
                    AppLogger.e("GeminiEmbedder", "Embedding request failed after $maxRetries retries: ${e.message}", e)
                    throw e
                }
            } catch (e: Exception) {
                lastException = e
                retryCount++
                
                if (retryCount <= maxRetries) {
                    val delayMs = initialRetryDelayMs * (1 shl (retryCount - 1)) // Exponential backoff
                    AppLogger.w("GeminiEmbedder", "Embedding request failed (attempt $retryCount/$maxRetries), retrying in ${delayMs}ms: ${e.message}", e)
                    delay(delayMs)
                } else {
                    val errorMsg = when {
                        e.message?.contains("401") == true || e.message?.contains("403") == true -> {
                            "Invalid or missing Gemini API key"
                        }
                        e.message?.contains("timeout") == true || e.message?.contains("Timeout") == true -> {
                            "Gemini embedding API not reachable (timeout)"
                        }
                        else -> {
                            "Failed to generate embedding after $maxRetries retries: ${e.message}"
                        }
                    }
                    AppLogger.e("GeminiEmbedder", errorMsg, e)
                    throw EmbeddingException(errorMsg, e)
                }
            }
        }
        
        // Should never reach here, but handle it just in case
        throw lastException ?: EmbeddingException("Unknown error in Gemini embedding")
    }
    
    override suspend fun embedDocument(texts: List<String>): List<Embedding> {
        // Android doesn't index documents, so this should not be called
        throw UnsupportedOperationException("Document embedding not supported on Android. Indexing must be done on Desktop.")
    }
    
    @Deprecated("Use embedDocument or embedQuery instead", ReplaceWith("if (taskType == EmbeddingTaskType.SEARCH_DOCUMENT) embedDocument(texts).map { it.vector.toFloatArray() } else texts.map { embedQuery(it).vector.toFloatArray() }"))
    override suspend fun embed(texts: List<String>, taskType: org.krypton.rag.EmbeddingTaskType): List<FloatArray> {
        return when (taskType) {
            org.krypton.rag.EmbeddingTaskType.SEARCH_DOCUMENT -> {
                throw UnsupportedOperationException("Document embedding not supported on Android")
            }
            org.krypton.rag.EmbeddingTaskType.SEARCH_QUERY -> {
                texts.map { embedQuery(it).vector.toFloatArray() }
            }
        }
    }
}


