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
import org.krypton.config.RagDefaults
import org.krypton.rag.Embedder
import org.krypton.rag.EmbeddingTaskType
import org.krypton.rag.models.Embedding
import org.krypton.util.AppLogger
import kotlin.time.Duration.Companion.seconds

/**
 * HTTP-based embedder that works with Ollama, Llama Stack, or similar APIs.
 * 
 * @param baseUrl Base URL of the embedding service (e.g., "http://localhost:11434")
 * @param model Model name (e.g., "nomic-embed-text:v1.5")
 * @param apiPath API endpoint path (default: "/api/embed" for Ollama)
 * @param httpClientEngine HTTP client engine (platform-specific)
 */
class HttpEmbedder(
    private val baseUrl: String,
    private val model: String,
    private val apiPath: String = "/api/embed",
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
    
    /**
     * Validates and possibly truncates content to ensure it doesn't exceed the embedding limit.
     * 
     * This is a hard guard that ensures no content exceeds maxContentChars, even if
     * upstream chunking is buggy. If content exceeds the limit, it will be truncated
     * and a warning will be logged.
     * 
     * @param raw The raw text content
     * @param maxContentChars Maximum content characters allowed (excluding prefix)
     * @return The text, truncated if necessary
     */
    private fun validateAndPossiblyTrimContent(raw: String, maxContentChars: Int): String {
        if (raw.length <= maxContentChars) return raw

        // Log and truncate as a last line of defense
        AppLogger.w(
            "HttpEmbedder",
            "Embedding content exceeded MAX_CONTENT_CHARS=${maxContentChars}, length=${raw.length}. Truncating."
        )
        return raw.substring(0, maxContentChars)
    }
    
    @Serializable
    private data class EmbeddingRequest(
        val model: String,
        val input: List<String>
    )
    
    @Serializable
    private data class EmbeddingResponse(
        val model: String? = null,
        val embeddings: List<List<Float>>? = null,
        val error: String? = null
    )
    
    override suspend fun embedDocument(texts: List<String>): List<Embedding> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) {
            return@withContext emptyList()
        }
        
        // Use the existing embed method with SEARCH_DOCUMENT task type
        val floatArrays = embed(texts, EmbeddingTaskType.SEARCH_DOCUMENT)
        
        // Convert FloatArray to Embedding (List<Float>)
        return@withContext floatArrays.map { floatArray ->
            Embedding(vector = floatArray.toList())
        }
    }
    
    override suspend fun embedQuery(text: String): Embedding = withContext(Dispatchers.IO) {
        // Use the existing embed method with SEARCH_QUERY task type
        val floatArrays = embed(listOf(text), EmbeddingTaskType.SEARCH_QUERY)
        
        if (floatArrays.isEmpty()) {
            throw EmbeddingException("No embedding returned for query")
        }
        
        // Convert FloatArray to Embedding (List<Float>)
        return@withContext Embedding(vector = floatArrays[0].toList())
    }
    
    // Override deprecated method from interface for backward compatibility
    @Deprecated("Use embedDocument or embedQuery instead", ReplaceWith("if (taskType == EmbeddingTaskType.SEARCH_DOCUMENT) embedDocument(texts).map { it.vector.toFloatArray() } else texts.map { embedQuery(it).vector.toFloatArray() }"))
    override suspend fun embed(texts: List<String>, taskType: EmbeddingTaskType): List<FloatArray> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) {
            return@withContext emptyList()
        }
        
        // Prefix texts based on task type for Nomic embedding models
        val prefixLength = when (taskType) {
            EmbeddingTaskType.SEARCH_DOCUMENT -> RagDefaults.Embedding.DOCUMENT_PREFIX_LENGTH
            EmbeddingTaskType.SEARCH_QUERY -> RagDefaults.Embedding.QUERY_PREFIX_LENGTH
        }
        
        // Validate text lengths before sending to API
        // Use model-specific limit estimation for better accuracy
        val maxContentChars = org.krypton.rag.EmbeddingValidator.estimateMaxContentChars(model)
        val maxChars = maxContentChars + prefixLength // Total including prefix
        
        // Apply hard guard: truncate any texts that exceed maxContentChars before prefixing
        val safeTexts = texts.map { validateAndPossiblyTrimContent(it, maxContentChars) }
        
        // Prefix the safe texts
        val prefixedTexts = safeTexts.map { text ->
            when (taskType) {
                EmbeddingTaskType.SEARCH_DOCUMENT -> "search_document: $text"
                EmbeddingTaskType.SEARCH_QUERY -> "search_query: $text"
            }
        }
        
        AppLogger.d("HttpEmbedder", "Validating ${prefixedTexts.size} texts for embedding (model=$model, maxContentChars=$maxContentChars, maxTotalChars=$maxChars)")
        
        val invalidTexts = mutableListOf<Pair<Int, Int>>() // (index, length)
        val closeToLimitTexts = mutableListOf<Pair<Int, Int>>() // (index, length)
        
        prefixedTexts.forEachIndexed { index, prefixedText ->
            val textLength = prefixedText.length
            if (textLength > maxChars) {
                invalidTexts.add(index to textLength)
            } else if (textLength > maxChars * 0.9) {
                closeToLimitTexts.add(index to textLength)
            }
        }
        
        if (invalidTexts.isNotEmpty()) {
            val errorDetails = invalidTexts.joinToString(", ") { "text[$it.first]=${it.second}chars" }
            val errorMsg = "One or more texts exceed embedding context limit (max=$maxChars chars including prefix, model=$model, estimated max content=$maxContentChars). $errorDetails. " +
                    "This should not happen if chunks were properly validated before embedding. " +
                    "Please check chunking configuration and ensure chunks are validated before calling embed()."
            AppLogger.e("HttpEmbedder", errorMsg)
            throw EmbeddingException(errorMsg)
        }
        
        // Log if any texts are close to the limit
        if (closeToLimitTexts.isNotEmpty()) {
            val details = closeToLimitTexts.joinToString(", ") { "text[$it.first]=${it.second}chars" }
            AppLogger.w("HttpEmbedder", "Some texts are close to embedding limit (max=$maxChars, model=$model): $details")
        }
        
        val url = "$baseUrl$apiPath"
        AppLogger.d("HttpEmbedder", "Embedding request started: $url, model=$model, taskType=$taskType, count=${texts.size}")
        
        var lastException: Exception? = null
        var retryCount = 0
        
        while (retryCount <= maxRetries) {
            try {
                // Send batch request with all texts (prefixed)
                val request = EmbeddingRequest(
                    model = model,
                    input = prefixedTexts
                )
                
                // Make HTTP call and try to deserialize response
                val response: EmbeddingResponse = try {
                    client.post(url) {
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }.body<EmbeddingResponse>()
                } catch (e: kotlinx.serialization.SerializationException) {
                    // If deserialization fails, get raw response body for debugging
                    val rawResponseBody = try {
                        client.post(url) {
                            contentType(ContentType.Application.Json)
                            setBody(request)
                        }.body<String>()
                    } catch (e2: Exception) {
                        "Failed to read response body: ${e2.message}"
                    }
                    
                    AppLogger.e("HttpEmbedder", "Failed to deserialize embedding API response. Raw response: $rawResponseBody", e)
                    
                    // Check if it's an error response
                    val errorMsg = if (rawResponseBody.contains("\"error\"") || rawResponseBody.contains("\"error\"")) {
                        "Embedding API returned an error response. Raw response: $rawResponseBody"
                    } else if (rawResponseBody.contains("model") && (rawResponseBody.contains("not found") || rawResponseBody.contains("404"))) {
                        "Embedding model not found or unavailable: $model. Raw response: $rawResponseBody"
                    } else {
                        "Embedding API response format is invalid or missing 'embeddings' field. Model: $model. Raw response: $rawResponseBody"
                    }
                    
                    throw EmbeddingException(errorMsg, e)
                }
                
                if (response.error != null) {
                    val errorMsg = response.error.lowercase()
                    val finalError = if (errorMsg.contains("model") || errorMsg.contains("not found") || 
                        errorMsg.contains("invalid") || errorMsg.contains("404")) {
                        "Model error: ${response.error}. Please check model name: $model"
                    } else if (errorMsg.contains("context length") || errorMsg.contains("input length") || 
                               errorMsg.contains("exceeds")) {
                        // Context length error - this should have been caught by validation
                        val maxChars = maxContentChars + prefixLength
                        "Embedding API returned error: ${response.error}. " +
                        "This indicates a chunk exceeded the context limit (max=$maxChars chars including prefix, model=$model). " +
                        "Please check that chunking and validation are working correctly. " +
                        "Current validation limit: $maxContentChars chars content + $prefixLength chars prefix = $maxChars chars total."
                    } else {
                        "Embedding API returned error: ${response.error}"
                    }
                    AppLogger.e("HttpEmbedder", finalError, null)
                    throw EmbeddingException(finalError)
                }
                
                // Verify response has embeddings field
                if (response.embeddings == null) {
                    throw EmbeddingException("Embedding API response is missing 'embeddings' field. Model: $model. This may indicate the model is not available or the API format has changed.")
                }
                
                // Verify response has embeddings
                if (response.embeddings.isEmpty()) {
                    throw EmbeddingException("No embeddings found in API response. Model: $model")
                }
                
                // Verify embeddings count matches input texts count
                if (response.embeddings.size != texts.size) {
                    throw EmbeddingException(
                        "Embedding count mismatch: expected ${texts.size}, got ${response.embeddings.size}. Model: $model"
                    )
                }
                
                // Convert List<List<Float>> to List<FloatArray>
                val embeddings = response.embeddings.map { it.toFloatArray() }
                
                // Log embedding sizes
                embeddings.forEachIndexed { index, embedding ->
                    AppLogger.d("HttpEmbedder", "Embedding[$index] size: ${embedding.size} dimensions")
                }
                
                AppLogger.i("HttpEmbedder", "Embedding request succeeded: generated ${embeddings.size} embeddings, model: $model")
                return@withContext embeddings
            } catch (e: Exception) {
                lastException = e
                retryCount++
                
                if (retryCount <= maxRetries) {
                    val delayMs = initialRetryDelayMs * (1 shl (retryCount - 1)) // Exponential backoff
                    AppLogger.w("HttpEmbedder", "Embedding request failed (attempt $retryCount/$maxRetries), retrying in ${delayMs}ms: ${e.message}, model: $model", e)
                    delay(delayMs)
                } else {
                    // Final failure after all retries
                    val errorMsg = if (e is EmbeddingException) {
                        e.message ?: "Embedding failed"
                    } else {
                        "Failed to generate embeddings after $maxRetries retries: ${e.message}. Model: $model"
                    }
                    AppLogger.e("HttpEmbedder", errorMsg, e)
                    throw EmbeddingException(errorMsg, e)
                }
            }
        }
        
        // Should never reach here, but handle it just in case
        throw lastException ?: EmbeddingException("Unknown error in embedding generation")
    }
}

/**
 * Exception thrown when embedding generation fails.
 */
class EmbeddingException(message: String, cause: Throwable? = null) : Exception(message, cause)

