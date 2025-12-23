package org.krypton.krypton.ui.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.krypton.krypton.core.domain.search.SearchDomain
import org.krypton.krypton.core.domain.search.SearchState as DomainSearchState

/**
 * State holder for search functionality using StateFlow pattern.
 * 
 * Manages search state, match finding, and replace operations.
 * Coordinates with SearchDomain for business logic and PatternMatcher for pattern matching.
 * 
 * @param searchDomain Domain logic for search operations
 */
class SearchStateHolder(
    private val searchDomain: SearchDomain
) {
    private val _state = MutableStateFlow<DomainSearchState?>(null)
    val state: StateFlow<DomainSearchState?> = _state.asStateFlow()
    
    // UI status for error handling
    private val _status = MutableStateFlow<UiStatus>(UiStatus.Idle)
    val status: StateFlow<UiStatus> = _status.asStateFlow()
    
    /**
     * Opens the search dialog.
     */
    fun openSearchDialog(showReplace: Boolean = false) {
        _state.value = DomainSearchState(showReplace = showReplace)
    }
    
    /**
     * Closes the search dialog.
     */
    fun closeSearchDialog() {
        _state.value = null
    }
    
    /**
     * Updates the search state and recalculates matches for the given text.
     */
    fun updateSearchState(text: String, update: (DomainSearchState) -> DomainSearchState) {
        try {
            _status.value = UiStatus.Loading
            val currentState = _state.value ?: return
            val updated = searchDomain.updateSearchState(text, currentState, update)
            _state.value = updated
            _status.value = UiStatus.Idle
        } catch (e: Exception) {
            _status.value = UiStatus.Error("Failed to update search: ${e.message}", recoverable = true)
            org.krypton.krypton.util.AppLogger.e("SearchStateHolder", "Failed to update search state: ${e.message}", e)
        }
    }
    
    /**
     * Finds matches in the given text.
     */
    fun findMatches(text: String) {
        try {
            _status.value = UiStatus.Loading
            val currentState = _state.value ?: return
            val updated = searchDomain.findMatches(text, currentState)
            _state.value = updated
            _status.value = UiStatus.Idle
        } catch (e: Exception) {
            _status.value = UiStatus.Error("Failed to find matches: ${e.message}", recoverable = true)
            org.krypton.krypton.util.AppLogger.e("SearchStateHolder", "Failed to find matches: ${e.message}", e)
        }
    }
    
    /**
     * Moves to the next match.
     */
    fun findNext() {
        val currentState = _state.value ?: return
        val updated = searchDomain.findNext(currentState)
        _state.value = updated
    }
    
    /**
     * Moves to the previous match.
     */
    fun findPrevious() {
        val currentState = _state.value ?: return
        val updated = searchDomain.findPrevious(currentState)
        _state.value = updated
    }
    
    /**
     * Replaces all matches in the given text.
     */
    fun replaceAll(text: String): String {
        val currentState = _state.value ?: return text
        return searchDomain.replaceAll(text, currentState)
    }
    
    /**
     * Replaces the current match in the given text.
     */
    fun replaceCurrent(text: String): String {
        try {
            val currentState = _state.value ?: return text
            val result = searchDomain.replaceCurrent(text, currentState)
            _status.value = UiStatus.Success
            return result
        } catch (e: Exception) {
            _status.value = UiStatus.Error("Failed to replace match: ${e.message}", recoverable = true)
            org.krypton.krypton.util.AppLogger.e("SearchStateHolder", "Failed to replace current match: ${e.message}", e)
            return text
        }
    }
    
    /**
     * Clears the error status.
     */
    fun clearError() {
        if (_status.value is UiStatus.Error) {
            _status.value = UiStatus.Idle
        }
    }
}

