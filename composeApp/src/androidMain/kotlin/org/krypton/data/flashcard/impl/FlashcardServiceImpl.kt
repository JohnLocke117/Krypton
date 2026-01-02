package org.krypton.data.flashcard.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.krypton.core.domain.flashcard.Flashcard
import org.krypton.core.domain.flashcard.FlashcardService
import org.krypton.data.files.FileSystem
import org.krypton.rag.LlamaClient
import org.krypton.util.AppLogger

/**
 * Implementation of FlashcardService that generates flashcards from notes using LLM.
 */
class FlashcardServiceImpl(
    private val fileSystem: FileSystem,
    private val llamaClient: LlamaClient
) : FlashcardService {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    override suspend fun generateFromNote(
        notePath: String,
        maxCards: Int
    ): List<Flashcard> = withContext(Dispatchers.IO) {
        try {
            // Validate file exists
            if (!fileSystem.isFile(notePath)) {
                AppLogger.w("FlashcardServiceImpl", "Note path is not a file: $notePath")
                return@withContext emptyList()
            }

            // Read note content
            val content = fileSystem.readFile(notePath)
            if (content.isNullOrBlank()) {
                AppLogger.w("FlashcardServiceImpl", "Note content is empty: $notePath")
                return@withContext emptyList()
            }

            // Build prompt
            val prompt = buildPrompt(content, maxCards)
            
            AppLogger.d("FlashcardServiceImpl", "Generating flashcards for note: $notePath, maxCards: $maxCards")
            
            // Generate flashcards using LLM
            val response = llamaClient.complete(prompt)
            
            if (response.isBlank()) {
                AppLogger.w("FlashcardServiceImpl", "LLM returned empty response")
                return@withContext emptyList()
            }

            // Parse JSON response
            val flashcards = parseFlashcards(response, notePath)
            
            AppLogger.i("FlashcardServiceImpl", "Generated ${flashcards.size} flashcards from note: $notePath")
            
            return@withContext flashcards
        } catch (e: Exception) {
            AppLogger.e("FlashcardServiceImpl", "Error generating flashcards for note: $notePath", e)
            throw e
        }
    }

    /**
     * Builds the prompt for flashcard generation.
     */
    private fun buildPrompt(content: String, maxCards: Int): String {
        return """You are an AI that creates high-quality flashcards for spaced repetition.

You are given the full content of a single markdown note.
Your task: generate at most $maxCards flashcards that help a learner remember the key facts and concepts.

Rules:
- Use only information from the note.
- Focus on important concepts, definitions, and relationships.
- Each flashcard must have:
  - "question": a clear, concise prompt.
  - "answer": a clear, concise answer.
- Avoid overly broad or vague questions.
- Do not include explanations outside the "answer" field.
- Do not mention that you are an AI, the note file name, or any meta instructions.

Return the result as pure JSON in the following format:

[
  { "question": "...", "answer": "..." },
  { "question": "...", "answer": "..." }
]

Note content:

$content"""
    }

    /**
     * Parses the LLM response to extract flashcards.
     * Handles cases where JSON may be embedded in surrounding text.
     */
    private fun parseFlashcards(response: String, sourceFile: String): List<Flashcard> {
        try {
            val trimmed = response.trim()
            if (trimmed.isEmpty()) {
                AppLogger.w("FlashcardServiceImpl", "Empty response from LLM")
                return emptyList()
            }

            // Try to extract JSON array from the response (may have extra text)
            val jsonStart = trimmed.indexOf('[')
            val jsonEnd = trimmed.lastIndexOf(']')
            
            if (jsonStart == -1 || jsonEnd == -1 || jsonEnd <= jsonStart) {
                AppLogger.w("FlashcardServiceImpl", "No JSON array found in response")
                return emptyList()
            }
            
            var jsonStr = trimmed.substring(jsonStart, jsonEnd + 1)
            // Clean up JSON: remove trailing commas, fix backticks, etc.
            jsonStr = cleanJsonString(jsonStr)
            
            val jsonArray = json.parseToJsonElement(jsonStr).jsonArray
            
            val flashcards = mutableListOf<Flashcard>()
            for (element in jsonArray) {
                if (element !is JsonObject) {
                    AppLogger.w("FlashcardServiceImpl", "Invalid flashcard element: expected object")
                    continue
                }
                
                val question = element["question"]?.jsonPrimitive?.content
                val answer = element["answer"]?.jsonPrimitive?.content
                
                if (question.isNullOrBlank() || answer.isNullOrBlank()) {
                    AppLogger.w("FlashcardServiceImpl", "Flashcard missing question or answer")
                    continue
                }
                
                flashcards.add(
                    Flashcard(
                        question = question.trim(),
                        answer = answer.trim(),
                        sourceFile = sourceFile
                    )
                )
            }
            
            AppLogger.d("FlashcardServiceImpl", "Parsed ${flashcards.size} flashcards from JSON")
            return flashcards
        } catch (e: Exception) {
            AppLogger.e("FlashcardServiceImpl", "Failed to parse flashcards from response", e)
            return emptyList()
        }
    }

    /**
     * Cleans up JSON string to make it valid JSON.
     * Handles trailing commas, backticks in strings, and other common issues.
     */
    private fun cleanJsonString(jsonStr: String): String {
        var cleaned = jsonStr
            // Remove trailing commas
            .replace(Regex(",\\s*\\}"), "}")
            .replace(Regex(",\\s*\\]"), "]")
        
        // Fix backticks in JSON strings (replace backticks with escaped quotes or remove them)
        // Pattern: find strings with backticks like `"answer": `"text"` and fix them
        cleaned = cleaned.replace(Regex("`([^`]+)`"), "\"$1\"")
        
        // Fix cases where backticks are used instead of quotes at the start/end of string values
        // Pattern: `"key": `value` should become `"key": "value"`
        cleaned = cleaned.replace(Regex(":\\s*`([^`]+)`"), ": \"$1\"")
        
        // Fix escaped backticks in strings (backslash-backtick becomes quote)
        cleaned = cleaned.replace("\\`", "\"")
        
        return cleaned
    }
}

