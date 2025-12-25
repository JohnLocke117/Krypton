package org.krypton.krypton.rag.reranker

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.krypton.krypton.util.AppLogger

/**
 * Parses reranker responses to extract relevance scores.
 */
object RerankResponseParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    
    /**
     * Removes trailing commas from JSON string to make it valid JSON.
     * Handles trailing commas in objects and arrays.
     */
    private fun removeTrailingCommas(jsonStr: String): String {
        // Remove trailing commas before closing braces and brackets
        // Pattern: match comma followed by optional whitespace and closing brace/bracket
        return jsonStr
            .replace(Regex(",\\s*}"), "}")
            .replace(Regex(",\\s*]"), "]")
    }
    
    /**
     * Parses a reranker response to extract ID-to-score mappings.
     * 
     * @param response The raw response text from the reranker
     * @return Map of chunk ID to relevance score, or empty map if parsing fails
     */
    fun parseRerankResponse(response: String): Map<String, Double> {
        try {
            val trimmed = response.trim()
            if (trimmed.isEmpty()) {
                AppLogger.w("RerankResponseParser", "Empty response from reranker")
                return emptyMap()
            }
            
            // Try to extract JSON from the response (may have extra text)
            val jsonStart = trimmed.indexOf('{')
            val jsonEnd = trimmed.lastIndexOf('}')
            
            if (jsonStart == -1 || jsonEnd == -1 || jsonEnd <= jsonStart) {
                AppLogger.w("RerankResponseParser", "No JSON object found in response: $trimmed")
                return emptyMap()
            }
            
            var jsonStr = trimmed.substring(jsonStart, jsonEnd + 1)
            // Remove trailing commas to make it valid JSON
            jsonStr = removeTrailingCommas(jsonStr)
            val jsonObject = json.parseToJsonElement(jsonStr).jsonObject
            
            val scores = mutableMapOf<String, Double>()
            for ((key, value) in jsonObject) {
                val score = try {
                    when (value) {
                        is JsonPrimitive -> {
                            // Try to parse as double from content (works for both string and number)
                            value.content.toDoubleOrNull()
                        }
                        else -> null
                    }
                } catch (e: Exception) {
                    null
                }
                
                if (score == null) {
                    AppLogger.w("RerankResponseParser", "Invalid score value for ID '$key': $value")
                    continue
                }
                
                scores[key] = score.coerceIn(0.0, 1.0)
            }
            
            AppLogger.d("RerankResponseParser", "Parsed ${scores.size} scores from reranker response")
            return scores
        } catch (e: Exception) {
            AppLogger.w("RerankResponseParser", "Failed to parse reranker response: ${e.message}", e)
            return emptyMap()
        }
    }
}

