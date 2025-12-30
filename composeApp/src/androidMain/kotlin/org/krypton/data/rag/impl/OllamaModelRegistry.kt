package org.krypton.data.rag.impl

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.krypton.util.AppLogger

/**
 * Interface for checking if Ollama models are available.
 */
interface OllamaModelRegistry {
    /**
     * Checks if a model with the given name is available locally.
     * 
     * @param modelName The model name to check (e.g., "qwen3-reranker-0.6b")
     * @return true if the model exists, false otherwise
     */
    suspend fun hasModel(modelName: String): Boolean
}

/**
 * HTTP-based implementation that checks Ollama model availability via /api/tags endpoint.
 */
class HttpOllamaModelRegistry(
    private val baseUrl: String,
    httpClientEngine: HttpClientEngine
) : OllamaModelRegistry {
    
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
    private data class ModelInfo(
        val name: String? = null,
        val model: String? = null
    )
    
    @Serializable
    private data class TagsResponse(
        val models: List<ModelInfo>? = null
    )
    
    override suspend fun hasModel(modelName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/api/tags"
            AppLogger.d("HttpOllamaModelRegistry", "Checking for model: $modelName at $url")
            
            val response = client.get(url) {
                contentType(ContentType.Application.Json)
            }
            
            if (response.status.value !in 200..299) {
                AppLogger.w("HttpOllamaModelRegistry", "Failed to check model availability: ${response.status}")
                return@withContext false
            }
            
            val tagsResponse: TagsResponse = response.body()
            val models = tagsResponse.models ?: emptyList()
            
            // Check if model name matches any model's name or model field
            val found = models.any { modelInfo ->
                modelInfo.name == modelName || modelInfo.model == modelName ||
                modelInfo.name?.startsWith("$modelName:") == true ||
                modelInfo.model?.startsWith("$modelName:") == true
            }
            
            AppLogger.d("HttpOllamaModelRegistry", "Model '$modelName' ${if (found) "found" else "not found"}")
            return@withContext found
        } catch (e: Exception) {
            AppLogger.w("HttpOllamaModelRegistry", "Error checking model availability for '$modelName': ${e.message}", e)
            return@withContext false
        }
    }
}

