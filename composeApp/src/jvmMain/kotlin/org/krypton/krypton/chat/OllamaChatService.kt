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
import java.util.UUID

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
    private val baseUrl: String = "http://localhost:11434",
    private val model: String = "llama3.2:1b"
) : ChatService {

    private val systemPrompt = "You are a helpful study assistant integrated into a personal markdown editor. Be concise and helpful."

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
        userMessage: String
    ): List<ChatMessage> = withContext(Dispatchers.IO) {
        try {
            // Create user message
            val userMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = ChatRole.USER,
                content = userMessage,
                timestamp = System.currentTimeMillis()
            )

            // Build prompt from conversation history
            val prompt = buildPrompt(history + userMsg)

            // Make request to Ollama
            val request = OllamaGenerateRequest(
                model = model,
                prompt = prompt,
                stream = false
            )

            val httpResponse = client.post("$baseUrl/api/generate") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            // Check for HTTP errors
            if (httpResponse.status.value !in 200..299) {
                throw ChatServiceException(
                    "Ollama returned error: ${httpResponse.status}",
                    null
                )
            }
            
            // Ollama returns application/x-ndjson, so we need to read as text and parse manually
            // NDJSON is newline-delimited JSON. With stream=false, we should get a single JSON object
            val json = Json { 
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            }
            
            val response: OllamaGenerateResponse = try {
                val rawResponse = httpResponse.bodyAsText()
                
                if (rawResponse.isBlank()) {
                    throw ChatServiceException(
                        "Ollama returned empty response body",
                        null
                    )
                }
                
                // NDJSON format: each line is a JSON object. With stream=false, we should get one line
                // But handle the case where there might be multiple lines or the response is a single JSON object
                val trimmedResponse = rawResponse.trim()
                val lines = trimmedResponse.lines().filter { it.isNotBlank() }
                
                if (lines.isEmpty()) {
                    throw ChatServiceException(
                        "Ollama response contains no valid JSON lines",
                        null
                    )
                }
                
                // Try to parse each line and accumulate the response
                // With stream=false, we should get one complete response, but handle multiple lines
                var accumulatedResponse = ""
                var isDone = false
                var hasError: String? = null
                
                for (line in lines) {
                    try {
                        val lineResponse = json.decodeFromString<OllamaGenerateResponse>(line)
                        // Accumulate the response text
                        accumulatedResponse += lineResponse.response
                        // Check if this is the final response
                        if (lineResponse.done) {
                            isDone = true
                        }
                        // Check for errors
                        if (lineResponse.error != null) {
                            hasError = lineResponse.error
                        }
                    } catch (e: Exception) {
                        // If a line fails to parse, throw with context
                        throw ChatServiceException(
                            "Failed to parse line ${lines.indexOf(line) + 1} of ${lines.size}: ${line.take(200)}. Error: ${e.message}",
                            e
                        )
                    }
                }
                
                // If we have an error, throw it
                if (hasError != null) {
                    throw ChatServiceException(
                        "Ollama API error: $hasError",
                        null
                    )
                }
                
                // If we accumulated a response, use it
                if (accumulatedResponse.isNotBlank()) {
                    OllamaGenerateResponse(
                        model = null,
                        response = accumulatedResponse,
                        done = isDone,
                        error = null
                    )
                } else if (isDone) {
                    // Response is done but empty - this shouldn't happen, but handle it
                    throw ChatServiceException(
                        "Ollama returned done=true but with empty response. Raw response: ${rawResponse.take(500)}",
                        null
                    )
                } else {
                    // Fallback: try to parse the last line as-is
                    val lastLineResponse = json.decodeFromString<OllamaGenerateResponse>(lines.last())
                    if (lastLineResponse.response.isBlank() && !lastLineResponse.done) {
                        throw ChatServiceException(
                            "Ollama response is empty. Parsed ${lines.size} lines. Last line: ${lines.last().take(200)}",
                            null
                        )
                    }
                    lastLineResponse
                }
            } catch (e: SerializationException) {
                // Try to get more info about what we received
                val rawResponse = try {
                    httpResponse.bodyAsText()
                } catch (e2: Exception) {
                    "Could not read response body"
                }
                throw ChatServiceException(
                    "Failed to parse Ollama response. Response preview: ${rawResponse.take(500)}. Error: ${e.message}",
                    e
                )
            } catch (e: Exception) {
                throw ChatServiceException(
                    "Failed to get Ollama response: ${e.message}",
                    e
                )
            }
            
            // Check for API-level errors
            if (response.error != null) {
                throw ChatServiceException(
                    "Ollama API error: ${response.error}",
                    null
                )
            }
            
            // Create assistant message
            val assistantResponse = response.response.trim()
            if (assistantResponse.isEmpty()) {
                throw ChatServiceException(
                    "Ollama returned an empty response",
                    null
                )
            }
            
            val assistantMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = ChatRole.ASSISTANT,
                content = assistantResponse,
                timestamp = System.currentTimeMillis()
            )

            // Return updated history
            history + userMsg + assistantMsg
        } catch (e: ChatServiceException) {
            // Re-throw ChatServiceException as-is
            throw e
        } catch (e: Exception) {
            // Wrap other exceptions
            throw ChatServiceException(
                "Could not reach Ollama. Please make sure `ollama serve` is running. Error: ${e.message}",
                e
            )
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

