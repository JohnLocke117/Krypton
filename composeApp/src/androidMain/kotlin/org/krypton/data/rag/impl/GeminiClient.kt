package org.krypton.data.rag.impl

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.krypton.rag.LlamaClient
import org.krypton.util.AppLogger

/**
 * HTTP-based Gemini API client that implements LlamaClient interface.
 * 
 * @param apiKey Gemini API key from secrets
 * @param baseUrl Base URL from secrets (contains full URL with model)
 * @param model Model name (e.g., "gemini-2.5-flash")
 * @param httpClientEngine HTTP client engine (platform-specific)
 */
class GeminiClient(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String,
    httpClientEngine: HttpClientEngine
) : LlamaClient {
    
    private val client = HttpClient(httpClientEngine) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000 // 60 seconds
            connectTimeoutMillis = 10_000 // 10 seconds
            socketTimeoutMillis = 60_000 // 60 seconds
        }
    }
    
    private val maxRetries = 3
    private val initialRetryDelayMs = 500L
    
    @Serializable
    private data class GeminiContentPart(
        val text: String
    )
    
    @Serializable
    private data class GeminiContent(
        val parts: List<GeminiContentPart>
    )
    
    @Serializable
    private data class GeminiRequest(
        val contents: List<GeminiContent>
    )
    
    @Serializable
    private data class GeminiCandidate(
        val content: GeminiContent? = null
    )
    
    @Serializable
    private data class GeminiResponse(
        val candidates: List<GeminiCandidate>? = null,
        val error: GeminiError? = null
    )
    
    @Serializable
    private data class GeminiError(
        val message: String? = null,
        val code: Int? = null
    )
    
    /**
     * Builds the API URL, replacing model name if different from base URL.
     */
    private fun buildUrl(): String {
        // If baseUrl already contains the full URL with :generateContent, use it as-is
        // or replace the model if it's different
        return if (baseUrl.contains(":generateContent")) {
            // Full URL already provided, check if we need to replace the model
            if (baseUrl.contains("/models/$model:")) {
                // Model matches, use as-is
                baseUrl
            } else {
                // Replace model in the URL
                val basePath = baseUrl.substringBeforeLast("/")
                val pathBeforeModel = basePath.substringBeforeLast("/")
                "$pathBeforeModel/$model:generateContent"
            }
        } else if (baseUrl.contains("/models/")) {
            // Base URL with models path but no :generateContent
            val basePath = baseUrl.trimEnd('/')
            "$basePath/$model:generateContent"
        } else {
            // Fallback: construct from base URL
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        }
    }
    
    override suspend fun complete(prompt: String): String = withContext(Dispatchers.IO) {
        val url = buildUrl()
        AppLogger.d("GeminiClient", "Request started: $url, model=$model")
        
        var lastException: Exception? = null
        var retryCount = 0
        
        while (retryCount <= maxRetries) {
            try {
                val request = GeminiRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = listOf(
                                GeminiContentPart(text = prompt)
                            )
                        )
                    )
                )
                
                val httpResponse = client.post(url) {
                    contentType(ContentType.Application.Json)
                    header("x-goog-api-key", apiKey)
                    setBody(request)
                }
                
                if (httpResponse.status.value !in 200..299) {
                    val errorBody = try {
                        httpResponse.body<GeminiResponse>()
                    } catch (e: Exception) {
                        null
                    }
                    
                    val errorMsg = when {
                        httpResponse.status.value == 401 || httpResponse.status.value == 403 -> {
                            "Invalid or missing Gemini API key"
                        }
                        errorBody?.error != null -> {
                            errorBody.error.message ?: "Gemini API error: ${httpResponse.status}"
                        }
                        else -> {
                            "Gemini API returned error: ${httpResponse.status}"
                        }
                    }
                    
                    AppLogger.e("GeminiClient", errorMsg, null)
                    throw LlamaClientException(errorMsg)
                }
                
                AppLogger.i("GeminiClient", "Request succeeded: status=${httpResponse.status.value}")
                
                val response: GeminiResponse = httpResponse.body()
                
                if (response.error != null) {
                    val errorMsg = response.error.message ?: "Gemini API error"
                    AppLogger.e("GeminiClient", errorMsg, null)
                    throw LlamaClientException(errorMsg)
                }
                
                val candidates = response.candidates
                if (candidates.isNullOrEmpty()) {
                    throw LlamaClientException("Gemini API returned no candidates")
                }
                
                val firstCandidate = candidates.first()
                val content = firstCandidate.content
                if (content == null || content.parts.isEmpty()) {
                    throw LlamaClientException("Gemini API returned empty content")
                }
                
                val responseText = content.parts.first().text.trim()
                if (responseText.isEmpty()) {
                    throw LlamaClientException("Gemini API returned empty response text")
                }
                
                return@withContext responseText
            } catch (e: LlamaClientException) {
                lastException = e
                retryCount++
                
                if (retryCount <= maxRetries) {
                    val delayMs = initialRetryDelayMs * (1 shl (retryCount - 1)) // Exponential backoff
                    AppLogger.w("GeminiClient", "Request failed (attempt $retryCount/$maxRetries), retrying in ${delayMs}ms: ${e.message}", e)
                    delay(delayMs)
                } else {
                    AppLogger.e("GeminiClient", "Request failed after $maxRetries retries: ${e.message}", e)
                    throw e
                }
            } catch (e: Exception) {
                lastException = e
                retryCount++
                
                if (retryCount <= maxRetries) {
                    val delayMs = initialRetryDelayMs * (1 shl (retryCount - 1)) // Exponential backoff
                    AppLogger.w("GeminiClient", "Request failed (attempt $retryCount/$maxRetries), retrying in ${delayMs}ms: ${e.message}", e)
                    delay(delayMs)
                } else {
                    val errorMsg = when {
                        e.message?.contains("401") == true || e.message?.contains("403") == true -> {
                            "Invalid or missing Gemini API key"
                        }
                        e.message?.contains("timeout") == true || e.message?.contains("Timeout") == true -> {
                            "Gemini API not reachable (timeout)"
                        }
                        else -> {
                            "Failed to generate completion after $maxRetries retries: ${e.message}"
                        }
                    }
                    AppLogger.e("GeminiClient", errorMsg, e)
                    throw LlamaClientException(errorMsg, e)
                }
            }
        }
        
        // Should never reach here, but handle it just in case
        throw lastException ?: LlamaClientException("Unknown error in Gemini completion")
    }
    
    override suspend fun complete(model: String, prompt: String, temperature: Double): String {
        // Gemini API doesn't support per-request model override in the same way
        // For now, use the instance model and ignore the parameter
        // Temperature is also not supported in the basic API call
        return complete(prompt)
    }
}

