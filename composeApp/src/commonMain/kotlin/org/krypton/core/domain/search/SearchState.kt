package org.krypton.core.domain.search

/**
 * Immutable state for search and replace functionality.
 */
data class SearchState(
    val searchQuery: String = "",
    val replaceQuery: String = "",
    val matchCase: Boolean = false,
    val wholeWords: Boolean = false,
    val useRegex: Boolean = false,
    val currentMatchIndex: Int = -1,
    val matches: List<IntRange> = emptyList(),
    val showReplace: Boolean = false
) {
    /**
     * Whether there are any matches found.
     */
    val hasMatches: Boolean get() = matches.isNotEmpty()
    
    /**
     * Total number of matches.
     */
    val matchCount: Int get() = matches.size
    
    /**
     * Human-readable string for current match position.
     */
    val currentMatchText: String get() = if (currentMatchIndex >= 0 && currentMatchIndex < matches.size) {
        "${currentMatchIndex + 1} of ${matches.size}"
    } else {
        "0 of ${matches.size}"
    }
}

