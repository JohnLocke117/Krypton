package org.krypton.krypton

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

object SearchEngine {
    /**
     * Find all matches in the text based on search criteria.
     * Returns a list of character ranges (start inclusive, end exclusive).
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
            val pattern = when {
                useRegex -> {
                    try {
                        Pattern.compile(
                            query,
                            if (matchCase) 0 else Pattern.CASE_INSENSITIVE
                        )
                    } catch (e: PatternSyntaxException) {
                        // Invalid regex, return empty
                        return emptyList()
                    }
                }
                wholeWords -> {
                    // Escape special regex characters and add word boundaries
                    val escaped = Pattern.quote(query)
                    Pattern.compile(
                        "\\b$escaped\\b",
                        if (matchCase) 0 else Pattern.CASE_INSENSITIVE
                    )
                }
                else -> {
                    // Simple string search
                    val escaped = Pattern.quote(query)
                    Pattern.compile(
                        escaped,
                        if (matchCase) 0 else Pattern.CASE_INSENSITIVE
                    )
                }
            }

            val matcher = pattern.matcher(text)
            val matches = mutableListOf<IntRange>()

            while (matcher.find()) {
                matches.add(IntRange(matcher.start(), matcher.end()))
            }

            matches
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Replace all matches in the text.
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
            val pattern = when {
                useRegex -> {
                    try {
                        Pattern.compile(
                            searchQuery,
                            if (matchCase) 0 else Pattern.CASE_INSENSITIVE
                        )
                    } catch (e: PatternSyntaxException) {
                        return text
                    }
                }
                wholeWords -> {
                    val escaped = Pattern.quote(searchQuery)
                    Pattern.compile(
                        "\\b$escaped\\b",
                        if (matchCase) 0 else Pattern.CASE_INSENSITIVE
                    )
                }
                else -> {
                    val escaped = Pattern.quote(searchQuery)
                    Pattern.compile(
                        escaped,
                        if (matchCase) 0 else Pattern.CASE_INSENSITIVE
                    )
                }
            }

            val matcher = pattern.matcher(text)
            if (useRegex) {
                matcher.replaceAll(replaceQuery)
            } else {
                // For non-regex, we need to escape $ and \ in replacement
                val escapedReplace = replaceQuery.replace("\\", "\\\\").replace("$", "\\$")
                matcher.replaceAll(escapedReplace)
            }
        } catch (e: Exception) {
            text
        }
    }

    /**
     * Replace a single match at the specified range.
     */
    fun replaceMatch(
        text: String,
        range: IntRange,
        replaceQuery: String
    ): String {
        return text.substring(0, range.first) + replaceQuery + text.substring(range.last)
    }
}

