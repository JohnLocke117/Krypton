package org.krypton.core.domain.search

import kotlin.text.Regex
import org.krypton.util.AppLogger

/**
 * Platform-agnostic search and replace engine for text operations.
 * 
 * Provides text search and replacement functionality with support for:
 * - Case-sensitive and case-insensitive matching
 * - Whole word matching
 * - Regular expression patterns
 * 
 * All operations are pure functions with no side effects.
 */
object SearchEngine {
    /**
     * Finds all matches in the text based on search criteria.
     * 
     * Supports three search modes:
     * - **Regex mode**: Uses the query as a regular expression pattern
     * - **Whole words mode**: Matches complete words only (adds word boundaries)
     * - **Simple mode**: Plain text search with optional case sensitivity
     * 
     * @param text The text to search in
     * @param query The search query string
     * @param matchCase If true, search is case-sensitive; if false, case-insensitive
     * @param wholeWords If true, only matches complete words (ignored if useRegex is true)
     * @param useRegex If true, treats query as a regular expression pattern
     * @return List of character ranges (start inclusive, end exclusive) where matches were found.
     *         Returns empty list if query is empty, invalid regex, or no matches found.
     */
    fun findMatches(
        text: String,
        query: String,
        matchCase: Boolean,
        wholeWords: Boolean,
        useRegex: Boolean
    ): List<IntRange> {
        if (query.isEmpty()) {
            return emptyList()
        }

        return try {
            val regex = when {
                useRegex -> {
                    try {
                        Regex(
                            query,
                            if (matchCase) setOf() else setOf(RegexOption.IGNORE_CASE)
                        )
                    } catch (e: Exception) {
                        AppLogger.d("SearchEngine", "Invalid regex pattern: $query")
                        return emptyList()
                    }
                }
                wholeWords -> {
                    // Escape special regex characters and add word boundaries
                    val escaped = Regex.escape(query)
                    Regex(
                        "\\b$escaped\\b",
                        if (matchCase) setOf() else setOf(RegexOption.IGNORE_CASE)
                    )
                }
                else -> {
                    // Simple string search
                    val escaped = Regex.escape(query)
                    Regex(
                        escaped,
                        if (matchCase) setOf() else setOf(RegexOption.IGNORE_CASE)
                    )
                }
            }

            val matches = mutableListOf<IntRange>()
            regex.findAll(text).forEach { matchResult ->
                matches.add(IntRange(matchResult.range.first, matchResult.range.last + 1))
            }

            matches
        } catch (e: Exception) {
            AppLogger.e("SearchEngine", "Error finding matches in text", e)
            emptyList()
        }
    }

    /**
     * Replaces all matches in the text with the replacement string.
     * 
     * Supports the same search modes as [findMatches]. For regex mode, the replacement
     * string can contain regex replacement patterns (e.g., `$1`, `$2` for groups).
     * For non-regex mode, special characters in the replacement are escaped.
     * 
     * @param text The text to perform replacement on
     * @param searchQuery The search pattern to find
     * @param replaceQuery The replacement string
     * @param matchCase If true, search is case-sensitive
     * @param wholeWords If true, only matches complete words
     * @param useRegex If true, treats searchQuery as a regular expression
     * @return The text with all matches replaced. Returns original text if searchQuery is empty,
     *         regex is invalid, or an error occurs.
     */
    fun replaceAll(
        text: String,
        searchQuery: String,
        replaceQuery: String,
        matchCase: Boolean,
        wholeWords: Boolean,
        useRegex: Boolean
    ): String {
        if (searchQuery.isEmpty()) {
            return text
        }

        return try {
            val regex = when {
                useRegex -> {
                    try {
                        Regex(
                            searchQuery,
                            if (matchCase) setOf() else setOf(RegexOption.IGNORE_CASE)
                        )
                    } catch (e: Exception) {
                        AppLogger.d("SearchEngine", "Invalid regex pattern for replace: $searchQuery")
                        return text
                    }
                }
                wholeWords -> {
                    val escaped = Regex.escape(searchQuery)
                    Regex(
                        "\\b$escaped\\b",
                        if (matchCase) setOf() else setOf(RegexOption.IGNORE_CASE)
                    )
                }
                else -> {
                    val escaped = Regex.escape(searchQuery)
                    Regex(
                        escaped,
                        if (matchCase) setOf() else setOf(RegexOption.IGNORE_CASE)
                    )
                }
            }

            if (useRegex) {
                regex.replace(text, replaceQuery)
            } else {
                // For non-regex, we need to escape $ and \ in replacement
                val escapedReplace = replaceQuery.replace("\\", "\\\\").replace("$", "\\$")
                regex.replace(text, escapedReplace)
            }
        } catch (e: Exception) {
            AppLogger.e("SearchEngine", "Error replacing matches in text", e)
            text
        }
    }

    /**
     * Replaces a single match at the specified character range.
     * 
     * This is a simple string replacement that doesn't perform any search.
     * The range should be obtained from [findMatches] to ensure accuracy.
     * 
     * @param text The original text
     * @param range The character range to replace (start inclusive, end exclusive)
     * @param replaceQuery The replacement string
     * @return The text with the specified range replaced
     */
    fun replaceMatch(
        text: String,
        range: IntRange,
        replaceQuery: String
    ): String {
        return text.substring(0, range.first) + replaceQuery + text.substring(range.last)
    }
}
