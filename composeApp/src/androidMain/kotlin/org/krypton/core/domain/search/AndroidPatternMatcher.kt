package org.krypton.core.domain.search

/**
 * Android implementation of PatternMatcher.
 * Uses simple string matching - can be enhanced with regex support later.
 */
class AndroidPatternMatcher : PatternMatcher {
    override fun findMatches(
        text: String,
        pattern: String,
        matchCase: Boolean,
        wholeWords: Boolean,
        useRegex: Boolean
    ): List<IntRange> {
        if (pattern.isEmpty()) return emptyList()
        
        val matches = mutableListOf<IntRange>()
        val searchText = if (matchCase) text else text.lowercase()
        val searchPattern = if (matchCase) pattern else pattern.lowercase()
        
        if (useRegex) {
            // Basic regex support - can be enhanced
            try {
                val regex = if (matchCase) {
                    Regex(searchPattern)
                } else {
                    Regex(searchPattern, RegexOption.IGNORE_CASE)
                }
                regex.findAll(text).forEach { matchResult ->
                    matches.add(matchResult.range)
                }
            } catch (e: Exception) {
                // Invalid regex, fall back to simple search
                return findMatches(text, pattern, matchCase, wholeWords, false)
            }
        } else {
            // Simple string search
            var startIndex = 0
            while (true) {
                val index = searchText.indexOf(searchPattern, startIndex)
                if (index == -1) break
                
                val endIndex = index + searchPattern.length
                if (wholeWords) {
                    val isWordBoundary = (index == 0 || !text[index - 1].isLetterOrDigit()) &&
                            (endIndex == text.length || !text[endIndex].isLetterOrDigit())
                    if (isWordBoundary) {
                        matches.add(index until endIndex)
                    }
                } else {
                    matches.add(index until endIndex)
                }
                startIndex = index + 1
            }
        }
        
        return matches
    }
    
    override fun replaceAll(
        text: String,
        searchPattern: String,
        replacement: String,
        matchCase: Boolean,
        wholeWords: Boolean,
        useRegex: Boolean
    ): String {
        if (useRegex) {
            try {
                val regex = if (matchCase) {
                    Regex(searchPattern)
                } else {
                    Regex(searchPattern, RegexOption.IGNORE_CASE)
                }
                return if (wholeWords) {
                    // For whole words with regex, use word boundaries
                    val wordBoundaryPattern = "\\b$searchPattern\\b"
                    val wordRegex = if (matchCase) {
                        Regex(wordBoundaryPattern)
                    } else {
                        Regex(wordBoundaryPattern, RegexOption.IGNORE_CASE)
                    }
                    text.replace(wordRegex, replacement)
                } else {
                    text.replace(regex, replacement)
                }
            } catch (e: Exception) {
                // Invalid regex, fall back to simple replace
                return replaceAll(text, searchPattern, replacement, matchCase, wholeWords, false)
            }
        } else {
            // Simple string replacement
            if (wholeWords) {
                // For whole words, we need to check boundaries
                val regex = if (matchCase) {
                    Regex("\\b${Regex.escape(searchPattern)}\\b")
                } else {
                    Regex("\\b${Regex.escape(searchPattern)}\\b", RegexOption.IGNORE_CASE)
                }
                return text.replace(regex, replacement)
            } else {
                return if (matchCase) {
                    text.replace(searchPattern, replacement)
                } else {
                    // Case-insensitive replacement
                    var result = text
                    var startIndex = 0
                    while (true) {
                        val index = result.lowercase().indexOf(searchPattern.lowercase(), startIndex)
                        if (index == -1) break
                        result = result.substring(0, index) + replacement + result.substring(index + searchPattern.length)
                        startIndex = index + replacement.length
                    }
                    result
                }
            }
        }
    }
    
    override fun replaceMatch(
        text: String,
        range: IntRange,
        replacement: String
    ): String {
        return text.substring(0, range.first) + replacement + text.substring(range.last + 1)
    }
}

