package org.krypton.core.domain.search

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * JVM implementation of PatternMatcher using java.util.regex.
 */
class JvmPatternMatcher : PatternMatcher {
    override fun findMatches(
        text: String,
        pattern: String,
        matchCase: Boolean,
        wholeWords: Boolean,
        useRegex: Boolean
    ): List<IntRange> {
        if (pattern.isEmpty()) {
            return emptyList()
        }

        return try {
            val compiledPattern = when {
                useRegex -> {
                    try {
                        Pattern.compile(
                            pattern,
                            if (matchCase) 0 else Pattern.CASE_INSENSITIVE
                        )
                    } catch (e: PatternSyntaxException) {
                        // Invalid regex, return empty
                        return emptyList()
                    }
                }
                wholeWords -> {
                    // Escape special regex characters and add word boundaries
                    val escaped = Pattern.quote(pattern)
                    Pattern.compile(
                        "\\b$escaped\\b",
                        if (matchCase) 0 else Pattern.CASE_INSENSITIVE
                    )
                }
                else -> {
                    // Simple string search
                    val escaped = Pattern.quote(pattern)
                    Pattern.compile(
                        escaped,
                        if (matchCase) 0 else Pattern.CASE_INSENSITIVE
                    )
                }
            }

            val matcher = compiledPattern.matcher(text)
            val matches = mutableListOf<IntRange>()

            while (matcher.find()) {
                matches.add(IntRange(matcher.start(), matcher.end()))
            }

            matches
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun replaceAll(
        text: String,
        searchPattern: String,
        replacement: String,
        matchCase: Boolean,
        wholeWords: Boolean,
        useRegex: Boolean
    ): String {
        if (searchPattern.isEmpty()) {
            return text
        }

        return try {
            val compiledPattern = when {
                useRegex -> {
                    try {
                        Pattern.compile(
                            searchPattern,
                            if (matchCase) 0 else Pattern.CASE_INSENSITIVE
                        )
                    } catch (e: PatternSyntaxException) {
                        return text
                    }
                }
                wholeWords -> {
                    val escaped = Pattern.quote(searchPattern)
                    Pattern.compile(
                        "\\b$escaped\\b",
                        if (matchCase) 0 else Pattern.CASE_INSENSITIVE
                    )
                }
                else -> {
                    val escaped = Pattern.quote(searchPattern)
                    Pattern.compile(
                        escaped,
                        if (matchCase) 0 else Pattern.CASE_INSENSITIVE
                    )
                }
            }

            val matcher = compiledPattern.matcher(text)
            if (useRegex) {
                matcher.replaceAll(replacement)
            } else {
                // For non-regex, we need to escape $ and \ in replacement
                val escapedReplace = replacement.replace("\\", "\\\\").replace("$", "\\$")
                matcher.replaceAll(escapedReplace)
            }
        } catch (e: Exception) {
            text
        }
    }

    override fun replaceMatch(
        text: String,
        range: IntRange,
        replacement: String
    ): String {
        return text.substring(0, range.first) + replacement + text.substring(range.last)
    }
}

