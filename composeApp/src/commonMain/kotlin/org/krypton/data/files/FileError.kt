package org.krypton.data.files

/**
 * Sealed hierarchy representing file operation errors.
 * 
 * This allows structured error handling for file operations
 * across all platforms.
 */
sealed class FileError {
    /**
     * The file or directory was not found.
     */
    data class NotFound(val path: String) : FileError()
    
    /**
     * Permission was denied for the operation.
     */
    data class PermissionDenied(val path: String) : FileError()
    
    /**
     * An I/O failure occurred (e.g., disk full, network error).
     */
    data class IoFailure(val path: String, val cause: String?) : FileError()
    
    /**
     * The path is invalid or malformed.
     */
    data class InvalidPath(val path: String, val reason: String?) : FileError()
    
    /**
     * The file or directory already exists.
     */
    data class AlreadyExists(val path: String) : FileError()
    
    /**
     * The operation is not supported (e.g., on this platform).
     */
    data class NotSupported(val operation: String, val path: String) : FileError()
    
    /**
     * An unknown or unexpected error occurred.
     */
    data class Unknown(val path: String, val message: String?, val cause: String?) : FileError()
}

/**
 * Result type for file operations that can fail.
 * 
 * @param T The success type (e.g., String for readFile, Boolean for writeFile)
 */
sealed class FileResult<out T> {
    /**
     * Successful operation result.
     */
    data class Success<T>(val value: T) : FileResult<T>()
    
    /**
     * Failed operation with error details.
     */
    data class Failure(val error: FileError) : FileResult<Nothing>()
    
    /**
     * Checks if the result is successful.
     */
    val isSuccess: Boolean
        get() = this is Success
    
    /**
     * Checks if the result is a failure.
     */
    val isFailure: Boolean
        get() = this is Failure
    
    /**
     * Gets the value if successful, or null otherwise.
     */
    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }
    
    /**
     * Gets the error if failed, or null otherwise.
     */
    fun errorOrNull(): FileError? = when (this) {
        is Success -> null
        is Failure -> error
    }
}

