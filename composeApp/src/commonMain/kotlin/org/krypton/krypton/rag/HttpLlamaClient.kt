package org.krypton.krypton.rag

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
import org.krypton.krypton.util.AppLogger

/**
 * HTTP-based Llama client that works with Ollama, llama.cpp, or similar APIs.
 * 
 * @param baseUrl Base URL of the LLM service (e.g., "http://localhost:11434")
 * @param model Model name (e.g., "llama3.2:1b")
 * @param apiPath API endpoint path (default: "/api/generate" for Ollama)
 * @param httpClientEngine HTTP client engine (platform-specific)
 */
class HttpLlamaClient(
    private val baseUrl: String,
    private val model: String,
    private val apiPath: String = "/api/generate",
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
    }
    
    @Serializable
    private data class GenerateRequest(
        val model: String,
        val prompt: String,
        val stream: Boolean = false
    )
    
    @Serializable
    private data class GenerateResponse(
        val model: String? = null,
        val response: String = "",
        val done: Boolean = false,
        val error: String? = null
    )
    
    override suspend fun complete(prompt: String): String = withContext(Dispatchers.IO) {
        val url = "$baseUrl$apiPath"
        AppLogger.d("HttpLlamaClient", "Request started: $url, model=$model")
        
        try {
            val request = GenerateRequest(
                model = model,
                prompt = prompt,
                stream = false
            )
            
            val httpResponse = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (httpResponse.status.value !in 200..299) {
                AppLogger.e("HttpLlamaClient", "Request failed: status=${httpResponse.status.value}", null)
                throw LlamaClientException("LLM API returned error: ${httpResponse.status}")
            }
            
            AppLogger.i("HttpLlamaClient", "Request succeeded: status=${httpResponse.status.value}")
            
            // Ollama returns application/x-ndjson (newline-delimited JSON)
            val json = Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            }
            
            val rawResponse: String = httpResponse.body()
            if (rawResponse.isBlank()) {
                throw LlamaClientException("LLM API returned empty response")
            }
            
            // Parse NDJSON format
            val lines = rawResponse.trim().lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) {
                throw LlamaClientException("LLM API response contains no valid JSON lines")
            }
            
            // Accumulate response from all lines
            var accumulatedResponse = ""
            var hasError: String? = null
            
            for (line in lines) {
                val lineResponse = json.decodeFromString<GenerateResponse>(line)
                accumulatedResponse += lineResponse.response
                
                if (lineResponse.error != null) {
                    hasError = lineResponse.error
                }
            }
            
            if (hasError != null) {
                throw LlamaClientException("LLM API error: $hasError")
            }
            
            val trimmedResponse = accumulatedResponse.trim()
            if (trimmedResponse.isEmpty()) {
                throw LlamaClientException("LLM API returned empty response text")
            }
            
            trimmedResponse
        } catch (e: LlamaClientException) {
            AppLogger.e("HttpLlamaClient", "Request failed: ${e.message}", e)
            throw e
        } catch (e: Exception) {
            AppLogger.e("HttpLlamaClient", "Request failed: ${e.message}", e)
            throw LlamaClientException("Failed to generate completion: ${e.message}", e)
        }
    }
}

/**
 * Exception thrown when LLM completion fails.
 */
class LlamaClientException(message: String, cause: Throwable? = null) : Exception(message, cause)

