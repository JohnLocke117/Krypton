package org.krypton.ui.state

/**
 * Represents the status of a UI operation or component.
 * 
 * Used across state holders to communicate loading, success, and error states
 * to the UI layer.
 */
sealed class UiStatus {
    /**
     * Idle state - no operation in progress.
     */
    object Idle : UiStatus()
    
    /**
     * Loading state - an operation is in progress.
     */
    object Loading : UiStatus()
    
    /**
     * Error state - an operation failed.
     * 
     * @param message Human-readable error message
     * @param recoverable Whether the error is recoverable (e.g., network error vs. fatal error)
     */
    data class Error(
        val message: String,
        val recoverable: Boolean = true
    ) : UiStatus()
    
    /**
     * Success state - an operation completed successfully.
     */
    object Success : UiStatus()
    
    /**
     * Checks if the status represents an error.
     */
    val isError: Boolean
        get() = this is Error
    
    /**
     * Checks if the status represents a loading state.
     */
    val isLoading: Boolean
        get() = this is Loading
    
    /**
     * Checks if the status represents an idle state.
     */
    val isIdle: Boolean
        get() = this is Idle
    
    /**
     * Checks if the status represents a success state.
     */
    val isSuccess: Boolean
        get() = this is Success
}

