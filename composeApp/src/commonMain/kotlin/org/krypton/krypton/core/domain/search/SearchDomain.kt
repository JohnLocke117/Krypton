package org.krypton.krypton.core.domain.search

/**
 * Pure domain logic for search operations.
 * 
 * This class contains business logic for searching and replacing text
 * without platform-specific dependencies.
 */
class SearchDomain(
    private val patternMatcher: PatternMatcher
) {
    /**
     * Finds all matches in the text based on search criteria.
     * 
     * @param text The text to search in
     * @param state Current search state with query and options
     * @return Updated search state with matches found
     */
    fun findMatches(text: String, state: SearchState): SearchState {
        if (state.searchQuery.isEmpty()) {
            return state.copy(matches = emptyList(), currentMatchIndex = -1)
        }
        
        val matches = patternMatcher.findMatches(
            text = text,
            pattern = state.searchQuery,
            matchCase = state.matchCase,
            wholeWords = state.wholeWords,
            useRegex = state.useRegex
        )
        
        return state.copy(
            matches = matches,
            currentMatchIndex = if (matches.isNotEmpty()) 0 else -1
        )
    }
    
    /**
     * Updates the search state with new query or options.
     * 
     * @param text The text to search in
     * @param state Current search state
     * @param update Function to update the search state
     * @return Updated search state with recalculated matches
     */
    fun updateSearchState(
        text: String,
        state: SearchState,
        update: (SearchState) -> SearchState
    ): SearchState {
        val updated = update(state)
        
        // Recalculate matches if query changed
        return if (updated.searchQuery.isNotEmpty()) {
            findMatches(text, updated)
        } else {
            updated.copy(matches = emptyList(), currentMatchIndex = -1)
        }
    }
    
    /**
     * Moves to the next match.
     * 
     * @param state Current search state
     * @return Updated search state with next match selected, or same state if no matches
     */
    fun findNext(state: SearchState): SearchState {
        if (state.matches.isEmpty()) {
            return state
        }
        
        val nextIndex = if (state.currentMatchIndex < state.matches.size - 1) {
            state.currentMatchIndex + 1
        } else {
            0 // Wrap around
        }
        
        return state.copy(currentMatchIndex = nextIndex)
    }
    
    /**
     * Moves to the previous match.
     * 
     * @param state Current search state
     * @return Updated search state with previous match selected, or same state if no matches
     */
    fun findPrevious(state: SearchState): SearchState {
        if (state.matches.isEmpty()) {
            return state
        }
        
        val prevIndex = if (state.currentMatchIndex > 0) {
            state.currentMatchIndex - 1
        } else {
            state.matches.size - 1 // Wrap around
        }
        
        return state.copy(currentMatchIndex = prevIndex)
    }
    
    /**
     * Replaces all matches in the text.
     * 
     * @param text The text to modify
     * @param state Current search state with search and replace queries
     * @return The text with all matches replaced
     */
    fun replaceAll(text: String, state: SearchState): String {
        if (state.searchQuery.isEmpty()) {
            return text
        }
        
        return patternMatcher.replaceAll(
            text = text,
            searchPattern = state.searchQuery,
            replacement = state.replaceQuery,
            matchCase = state.matchCase,
            wholeWords = state.wholeWords,
            useRegex = state.useRegex
        )
    }
    
    /**
     * Replaces the current match in the text.
     * 
     * @param text The text to modify
     * @param state Current search state
     * @return The text with the current match replaced, or original text if no current match
     */
    fun replaceCurrent(text: String, state: SearchState): String {
        if (state.currentMatchIndex < 0 || state.currentMatchIndex >= state.matches.size) {
            return text
        }
        
        val range = state.matches[state.currentMatchIndex]
        return patternMatcher.replaceMatch(text, range, state.replaceQuery)
    }
}

