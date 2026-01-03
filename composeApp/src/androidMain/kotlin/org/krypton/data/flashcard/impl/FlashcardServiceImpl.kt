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

            // Log the raw response for debugging (truncated to first 500 chars)
            AppLogger.d("FlashcardServiceImpl", "Raw LLM response (first 500 chars): ${trimmed.take(500)}")

            // Try to extract JSON array from the response (may have extra text)
            // Use bracket matching to find the correct JSON array boundaries
            val jsonStr = extractJsonArray(trimmed) ?: run {
                AppLogger.w("FlashcardServiceImpl", "Could not extract JSON array from response")
                AppLogger.d("FlashcardServiceImpl", "Full response: $trimmed")
                return emptyList()
            }
            
            AppLogger.d("FlashcardServiceImpl", "Extracted JSON (first 300 chars): ${jsonStr.take(300)}")
            
            // Basic validation: check if JSON looks reasonable
            if (!jsonStr.trim().startsWith("[") || !jsonStr.trim().endsWith("]")) {
                AppLogger.w("FlashcardServiceImpl", "Extracted JSON doesn't look like an array: ${jsonStr.take(100)}")
            }
            
            // First, try to parse the JSON as-is (it might already be valid)
            var jsonArray: JsonArray? = null
            try {
                jsonArray = json.parseToJsonElement(jsonStr).jsonArray
                AppLogger.d("FlashcardServiceImpl", "JSON was already valid, no cleaning needed")
            } catch (e: Exception) {
                AppLogger.d("FlashcardServiceImpl", "JSON needs cleaning, attempting to fix...")
                // Clean up JSON: remove trailing commas, fix backticks, escape quotes, etc.
                val cleanedJson = cleanJsonString(jsonStr)
                
                AppLogger.d("FlashcardServiceImpl", "Cleaned JSON (first 300 chars): ${cleanedJson.take(300)}")
                
                // Try to parse the cleaned JSON
                try {
                    jsonArray = json.parseToJsonElement(cleanedJson).jsonArray
                } catch (e2: Exception) {
                    AppLogger.e("FlashcardServiceImpl", "Failed to parse cleaned JSON", e2)
                    AppLogger.d("FlashcardServiceImpl", "Cleaned JSON that failed: $cleanedJson")
                    throw e2
                }
            }
            
            if (jsonArray == null) {
                throw IllegalStateException("Failed to parse JSON array")
            }
            
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
            AppLogger.d("FlashcardServiceImpl", "Response that failed to parse: ${response.take(1000)}")
            return emptyList()
        }
    }

    /**
     * Extracts a JSON array from text, handling cases where JSON is embedded in surrounding text.
     * Uses bracket matching to find the correct boundaries.
     */
    private fun extractJsonArray(text: String): String? {
        // Find the first '[' that starts a JSON array
        var startIndex = -1
        for (i in text.indices) {
            if (text[i] == '[') {
                startIndex = i
                break
            }
        }
        
        if (startIndex == -1) {
            return null
        }
        
        // Use bracket matching to find the matching ']'
        var bracketCount = 0
        var inString = false
        var escapeNext = false
        
        for (i in startIndex until text.length) {
            val char = text[i]
            
            when {
                escapeNext -> {
                    escapeNext = false
                }
                char == '\\' -> {
                    escapeNext = true
                }
                char == '"' && !escapeNext -> {
                    inString = !inString
                }
                !inString -> {
                    when (char) {
                        '[' -> bracketCount++
                        ']' -> {
                            bracketCount--
                            if (bracketCount == 0) {
                                // Found the matching closing bracket
                                return text.substring(startIndex, i + 1)
                            }
                        }
                    }
                }
            }
        }
        
        // If we didn't find a matching bracket, try the simple approach as fallback
        val jsonEnd = text.lastIndexOf(']')
        if (jsonEnd > startIndex) {
            return text.substring(startIndex, jsonEnd + 1)
        }
        
        return null
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
        
        // Only escape quotes if we detect unescaped quotes inside string values
        // Check if there are patterns like: "key": "value with "quotes" inside"
        // This is more conservative - only fix if we detect the problem
        if (hasUnescapedQuotesInValues(cleaned)) {
            cleaned = escapeQuotesInStringValues(cleaned)
        }
        
        return cleaned
    }
    
    /**
     * Checks if the JSON string has unescaped quotes inside string values.
     * This is a conservative check to avoid breaking valid JSON.
     */
    private fun hasUnescapedQuotesInValues(jsonStr: String): Boolean {
        var inString = false
        var escapeNext = false
        var afterColon = false
        
        for (i in jsonStr.indices) {
            val char = jsonStr[i]
            
            when {
                escapeNext -> {
                    escapeNext = false
                }
                char == '\\' -> {
                    escapeNext = true
                }
                char == ':' -> {
                    afterColon = true
                }
                char == '"' && !escapeNext -> {
                    if (inString && afterColon) {
                        // We're in a value string. Check if this quote is followed by something
                        // that suggests it's an unescaped quote inside the value
                        var j = i + 1
                        while (j < jsonStr.length && jsonStr[j].isWhitespace()) {
                            j++
                        }
                        
                        // If it's not followed by comma, }, ], or :, it's likely an unescaped quote
                        if (j < jsonStr.length && 
                            jsonStr[j] != ',' && 
                            jsonStr[j] != '}' && 
                            jsonStr[j] != ']' && 
                            jsonStr[j] != ':') {
                            return true
                        }
                    }
                    inString = !inString
                    if (!inString) {
                        afterColon = false
                    }
                }
                else -> {
                    if (!char.isWhitespace() && char != ',' && char != '{' && char != '}') {
                        afterColon = false
                    }
                }
            }
        }
        
        return false
    }
    
    /**
     * Escapes unescaped quotes inside JSON string values.
     * Handles cases like: "answer": "You use the command: "git init""
     * Converts to: "answer": "You use the command: \"git init\""
     * 
     * This function only escapes quotes that are inside string values, not the quotes
     * that delimit the strings themselves.
     */
    private fun escapeQuotesInStringValues(jsonStr: String): String {
        val result = StringBuilder()
        var i = 0
        var inString = false
        var escapeNext = false
        var afterColon = false // Track if we're after a colon (in a value, not a key)
        
        while (i < jsonStr.length) {
            val char = jsonStr[i]
            
            when {
                escapeNext -> {
                    // Previous char was backslash, so this char is escaped
                    result.append(char)
                    escapeNext = false
                }
                char == '\\' -> {
                    // Escape character - next char will be escaped
                    result.append(char)
                    escapeNext = true
                }
                char == ':' -> {
                    result.append(char)
                    afterColon = true // Next string will be a value
                }
                char == '"' && !escapeNext -> {
                    if (inString) {
                        // We're inside a string. Check if this quote closes the string
                        // Look ahead past whitespace to see what comes next
                        var j = i + 1
                        while (j < jsonStr.length && jsonStr[j].isWhitespace()) {
                            j++
                        }
                        
                        val isClosingQuote = when {
                            j >= jsonStr.length -> true // End of input
                            jsonStr[j] == ',' -> true // Followed by comma
                            jsonStr[j] == '}' -> true // Followed by closing brace
                            jsonStr[j] == ']' -> true // Followed by closing bracket
                            jsonStr[j] == ':' && !afterColon -> true // Followed by colon (end of key)
                            else -> false // Likely an unescaped quote inside the string
                        }
                        
                        if (isClosingQuote) {
                            // This is the closing quote of the string
                            result.append(char)
                            inString = false
                            afterColon = false
                        } else {
                            // This is an unescaped quote inside the string - escape it
                            result.append("\\\"")
                        }
                    } else {
                        // Start of a string (could be key or value)
                        result.append(char)
                        inString = true
                    }
                }
                else -> {
                    result.append(char)
                    if (!char.isWhitespace() && char != ',' && char != '{' && char != '}') {
                        afterColon = false // Reset after non-structural chars
                    }
                }
            }
            
            i++
        }
        
        return result.toString()
    }
}

