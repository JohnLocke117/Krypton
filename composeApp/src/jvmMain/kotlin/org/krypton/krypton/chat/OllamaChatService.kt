package org.krypton.krypton.chat

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.krypton.krypton.chat.RetrievalMode
import org.krypton.krypton.config.ChatDefaults
import org.krypton.krypton.util.IdGenerator
import org.krypton.krypton.util.TimeProvider
import org.krypton.krypton.util.createIdGenerator
import org.krypton.krypton.util.createTimeProvider
import org.krypton.krypton.util.AppLogger

@Serializable
private data class OllamaGenerateRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = false
)

@Serializable
private data class OllamaGenerateResponse(
    val model: String? = null,
    val response: String = "",
    val done: Boolean = false,
    val error: String? = null
)

class OllamaChatService(
    private val baseUrl: String = ChatDefaults.DEFAULT_BASE_URL,
    private val model: String = ChatDefaults.DEFAULT_MODEL,
    private val idGenerator: IdGenerator = createIdGenerator(),
    private val timeProvider: TimeProvider = createTimeProvider()
) : ChatService {

    private val systemPrompt = ChatDefaults.DEFAULT_SYSTEM_PROMPT

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            })
        }
    }

    override suspend fun sendMessage(
        history: List<ChatMessage>,
        userMessage: String,
        retrievalMode: RetrievalMode
    ): List<ChatMessage> = withContext(Dispatchers.IO) {
        // OllamaChatService ignores retrievalMode - it's plain chat
        AppLogger.action("Chat", "MessageSent", "model=$model, length=${userMessage.length}")
        
        try {
            val userMsg = createUserMessage(userMessage)
            val prompt = buildPrompt(history + userMsg)
            val httpResponse = makeOllamaRequest(prompt)
            
            validateHttpResponse(httpResponse)
            val response = parseOllamaResponse(httpResponse)
            validateResponse(response)
            
            val assistantMsg = createAssistantMessage(response.response.trim())
            AppLogger.i("Chat", "ResponseReceived: length=${response.response.length}")
            history + userMsg + assistantMsg
        } catch (e: ChatServiceException) {
            AppLogger.e("Chat", "ChatError: ${e.message}", e)
            throw e
        } catch (e: Exception) {
            AppLogger.e("Chat", "ChatError: ${e.message}", e)
            throw ChatServiceException(
                "Could not reach Ollama. Please make sure `ollama serve` is running. Error: ${e.message}",
                e
            )
        }
    }
    
    private fun createUserMessage(content: String): ChatMessage {
        return ChatMessage(
            id = idGenerator.generateId(),
            role = ChatRole.USER,
            content = content,
            timestamp = timeProvider.currentTimeMillis()
        )
    }
    
    private fun createAssistantMessage(content: String): ChatMessage {
        if (content.isEmpty()) {
            throw ChatServiceException("Ollama returned an empty response", null)
        }
        return ChatMessage(
            id = idGenerator.generateId(),
            role = ChatRole.ASSISTANT,
            content = content,
            timestamp = timeProvider.currentTimeMillis()
        )
    }
    
    private suspend fun makeOllamaRequest(prompt: String): HttpResponse {
        val request = OllamaGenerateRequest(
            model = model,
            prompt = prompt,
            stream = false
        )
        
        return client.post("$baseUrl/api/generate") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }
    
    private fun validateHttpResponse(httpResponse: HttpResponse) {
        if (httpResponse.status.value !in 200..299) {
            throw ChatServiceException(
                "Ollama returned error: ${httpResponse.status}",
                null
            )
        }
    }
    
    private suspend fun parseOllamaResponse(httpResponse: HttpResponse): OllamaGenerateResponse {
        val json = Json { 
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
        
        return try {
            val rawResponse = httpResponse.bodyAsText()
            
            if (rawResponse.isBlank()) {
                throw ChatServiceException("Ollama returned empty response body", null)
            }
            
            val lines = rawResponse.trim().lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) {
                throw ChatServiceException("Ollama response contains no valid JSON lines", null)
            }
            
            parseNdjsonLines(json, lines, rawResponse)
        } catch (e: SerializationException) {
            val rawResponse = try {
                httpResponse.bodyAsText()
            } catch (e2: Exception) {
                "Could not read response body"
            }
            throw ChatServiceException(
                "Failed to parse Ollama response. Response preview: ${rawResponse.take(500)}. Error: ${e.message}",
                e
            )
        } catch (e: ChatServiceException) {
            throw e
        } catch (e: Exception) {
            throw ChatServiceException("Failed to get Ollama response: ${e.message}", e)
        }
    }
    
    private fun parseNdjsonLines(
        json: Json,
        lines: List<String>,
        rawResponse: String
    ): OllamaGenerateResponse {
        var accumulatedResponse = ""
        var isDone = false
        var hasError: String? = null
        
        for ((index, line) in lines.withIndex()) {
            try {
                val lineResponse = json.decodeFromString<OllamaGenerateResponse>(line)
                accumulatedResponse += lineResponse.response
                if (lineResponse.done) {
                    isDone = true
                }
                if (lineResponse.error != null) {
                    hasError = lineResponse.error
                }
            } catch (e: Exception) {
                throw ChatServiceException(
                    "Failed to parse line ${index + 1} of ${lines.size}: ${line.take(200)}. Error: ${e.message}",
                    e
                )
            }
        }
        
        if (hasError != null) {
            throw ChatServiceException("Ollama API error: $hasError", null)
        }
        
        return when {
            accumulatedResponse.isNotBlank() -> {
                OllamaGenerateResponse(
                    model = null,
                    response = accumulatedResponse,
                    done = isDone,
                    error = null
                )
            }
            isDone -> {
                throw ChatServiceException(
                    "Ollama returned done=true but with empty response. Raw response: ${rawResponse.take(500)}",
                    null
                )
            }
            else -> {
                val lastLineResponse = json.decodeFromString<OllamaGenerateResponse>(lines.last())
                if (lastLineResponse.response.isBlank() && !lastLineResponse.done) {
                    throw ChatServiceException(
                        "Ollama response is empty. Parsed ${lines.size} lines. Last line: ${lines.last().take(200)}",
                        null
                    )
                }
                lastLineResponse
            }
        }
    }
    
    private fun validateResponse(response: OllamaGenerateResponse) {
        if (response.error != null) {
            throw ChatServiceException("Ollama API error: ${response.error}", null)
        }
    }

    private fun buildPrompt(messages: List<ChatMessage>): String {
        val promptBuilder = StringBuilder()
        
        // Add system prompt
        promptBuilder.append("System: $systemPrompt\n\n")

        // Add conversation history (excluding system messages from history)
        val conversationMessages = messages.filter { it.role != ChatRole.SYSTEM }
        conversationMessages.forEach { message ->
            when (message.role) {
                ChatRole.USER -> {
                    promptBuilder.append("User: ${message.content}\n")
                }
                ChatRole.ASSISTANT -> {
                    promptBuilder.append("Assistant: ${message.content}\n\n")
                }
                ChatRole.SYSTEM -> {
                    // System messages are handled separately
                }
            }
        }

        // Add final prompt for assistant response
        promptBuilder.append("Assistant:\n")

        return promptBuilder.toString()
    }
}

class ChatServiceException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

