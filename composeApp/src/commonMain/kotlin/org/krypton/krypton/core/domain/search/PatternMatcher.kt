package org.krypton.krypton.core.domain.search

/**
 * Platform-agnostic interface for pattern matching.
 * 
 * This allows search functionality to work across platforms
 * with different regex implementations.
 */
interface PatternMatcher {
    /**
     * Finds all matches of the pattern in the given text.
     * 
     * @param text The text to search in
     * @param pattern The pattern to search for
     * @param matchCase Whether matching should be case-sensitive
     * @param wholeWords Whether to match whole words only
     * @param useRegex Whether the pattern is a regex
     * @return List of character ranges (start inclusive, end exclusive) for each match
     */
    fun findMatches(
        text: String,
        pattern: String,
        matchCase: Boolean,
        wholeWords: Boolean,
        useRegex: Boolean
    ): List<IntRange>
    
    /**
     * Replaces all matches of the pattern in the given text.
     * 
     * @param text The text to search and replace in
     * @param searchPattern The pattern to search for
     * @param replacement The replacement string
     * @param matchCase Whether matching should be case-sensitive
     * @param wholeWords Whether to match whole words only
     * @param useRegex Whether the pattern is a regex
     * @return The text with all matches replaced
     */
    fun replaceAll(
        text: String,
        searchPattern: String,
        replacement: String,
        matchCase: Boolean,
        wholeWords: Boolean,
        useRegex: Boolean
    ): String
    
    /**
     * Replaces a single match at the specified range.
     * 
     * @param text The text to modify
     * @param range The character range to replace
     * @param replacement The replacement string
     * @return The text with the match replaced
     */
    fun replaceMatch(
        text: String,
        range: IntRange,
        replacement: String
    ): String
}

