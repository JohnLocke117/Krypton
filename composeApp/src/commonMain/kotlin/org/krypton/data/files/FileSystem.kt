package org.krypton.data.files

import org.krypton.platform.VaultRoot
import org.krypton.platform.NoteFile

/**
 * Platform-agnostic interface for file system operations.
 * 
 * This interface abstracts file operations so they can be implemented
 * differently on each platform (JVM, Android, iOS, etc.).
 * 
 * All paths are represented as strings for platform independence.
 * 
 * This interface provides both simple methods (returning nullable/boolean)
 * and Result-based methods for structured error handling.
 */
interface FileSystem {
    /**
     * Lists all markdown note files (.md) in the given vault root.
     * 
     * @param root Vault root to search in
     * @return List of note files found in the vault
     */
    suspend fun listNotes(root: VaultRoot): List<NoteFile>
    
    /**
     * Reads the content of a note file.
     * 
     * @param root Vault root containing the note
     * @param noteFile Note file to read
     * @return File content, or null if file doesn't exist or on error
     */
    suspend fun readNote(root: VaultRoot, noteFile: NoteFile): String?
    
    /**
     * Writes content to a note file.
     * 
     * @param root Vault root containing the note
     * @param noteFile Note file to write
     * @param content Content to write
     * @return true if successful, false otherwise
     */
    suspend fun writeNote(root: VaultRoot, noteFile: NoteFile, content: String): Boolean
    
    /**
     * Creates a new note file.
     * 
     * @param root Vault root to create the note in
     * @param noteFile Note file to create
     * @return true if successful, false if file already exists or on error
     */
    suspend fun createNote(root: VaultRoot, noteFile: NoteFile): Boolean
    
    /**
     * Deletes a note file.
     * 
     * @param root Vault root containing the note
     * @param noteFile Note file to delete
     * @return true if successful, false if file doesn't exist or on error
     */
    suspend fun deleteNote(root: VaultRoot, noteFile: NoteFile): Boolean
    /**
     * Lists all files and directories in the given directory.
     * 
     * @param directoryPath Path to the directory
     * @return List of file/directory paths, or empty list if directory doesn't exist or on error
     */
    fun listFiles(directoryPath: String): List<String>
    
    /**
     * Lists all files and directories in the given directory with error handling.
     * 
     * @param directoryPath Path to the directory
     * @return FileResult containing the list of paths or an error
     */
    fun listFilesResult(directoryPath: String): FileResult<List<String>> {
        return try {
            val files = listFiles(directoryPath)
            FileResult.Success(files)
        } catch (e: Exception) {
            FileResult.Failure(mapExceptionToError(directoryPath, "listFiles", e))
        }
    }
    
    /**
     * Reads the content of a file as a string.
     * 
     * @param filePath Path to the file
     * @return File content, or null if file doesn't exist or on error
     */
    fun readFile(filePath: String): String?
    
    /**
     * Reads the content of a file as a string with error handling.
     * 
     * @param filePath Path to the file
     * @return FileResult containing the file content or an error
     */
    fun readFileResult(filePath: String): FileResult<String> {
        return try {
            val content = readFile(filePath)
            if (content != null) {
                FileResult.Success(content)
            } else {
                FileResult.Failure(FileError.NotFound(filePath))
            }
        } catch (e: Exception) {
            FileResult.Failure(mapExceptionToError(filePath, "readFile", e))
        }
    }
    
    /**
     * Writes content to a file.
     * 
     * @param filePath Path to the file
     * @param content Content to write
     * @return true if successful, false otherwise
     */
    fun writeFile(filePath: String, content: String): Boolean
    
    /**
     * Writes content to a file with error handling.
     * 
     * @param filePath Path to the file
     * @param content Content to write
     * @return FileResult indicating success or failure
     */
    fun writeFileResult(filePath: String, content: String): FileResult<Boolean> {
        return try {
            val success = writeFile(filePath, content)
            if (success) {
                FileResult.Success(true)
            } else {
                FileResult.Failure(FileError.IoFailure(filePath, "Write operation returned false"))
            }
        } catch (e: Exception) {
            FileResult.Failure(mapExceptionToError(filePath, "writeFile", e))
        }
    }
    
    /**
     * Creates a new empty file.
     * 
     * @param filePath Path to the file to create
     * @return true if successful, false if file already exists or on error
     */
    fun createFile(filePath: String): Boolean
    
    /**
     * Creates a new empty file with error handling.
     * 
     * @param filePath Path to the file to create
     * @return FileResult indicating success or failure
     */
    fun createFileResult(filePath: String): FileResult<Boolean> {
        return try {
            val success = createFile(filePath)
            if (success) {
                FileResult.Success(true)
            } else if (exists(filePath)) {
                FileResult.Failure(FileError.AlreadyExists(filePath))
            } else {
                FileResult.Failure(FileError.IoFailure(filePath, "Create operation returned false"))
            }
        } catch (e: Exception) {
            FileResult.Failure(mapExceptionToError(filePath, "createFile", e))
        }
    }
    
    /**
     * Creates a new directory.
     * 
     * @param directoryPath Path to the directory to create
     * @return true if successful, false if directory already exists or on error
     */
    fun createDirectory(directoryPath: String): Boolean
    
    /**
     * Creates a new directory with error handling.
     * 
     * @param directoryPath Path to the directory to create
     * @return FileResult indicating success or failure
     */
    fun createDirectoryResult(directoryPath: String): FileResult<Boolean> {
        return try {
            val success = createDirectory(directoryPath)
            if (success) {
                FileResult.Success(true)
            } else if (exists(directoryPath)) {
                FileResult.Failure(FileError.AlreadyExists(directoryPath))
            } else {
                FileResult.Failure(FileError.IoFailure(directoryPath, "Create directory operation returned false"))
            }
        } catch (e: Exception) {
            FileResult.Failure(mapExceptionToError(directoryPath, "createDirectory", e))
        }
    }
    
    /**
     * Checks if a path exists and is a directory.
     * 
     * @param path Path to check
     * @return true if path exists and is a directory, false otherwise
     */
    fun isDirectory(path: String): Boolean
    
    /**
     * Checks if a path exists and is a regular file.
     * 
     * @param path Path to check
     * @return true if path exists and is a file, false otherwise
     */
    fun isFile(path: String): Boolean
    
    /**
     * Renames or moves a file or directory.
     * 
     * @param oldPath Current path
     * @param newPath New path
     * @return true if successful, false if old path doesn't exist, new path exists, or on error
     */
    fun renameFile(oldPath: String, newPath: String): Boolean
    
    /**
     * Renames or moves a file or directory with error handling.
     * 
     * @param oldPath Current path
     * @param newPath New path
     * @return FileResult indicating success or failure
     */
    fun renameFileResult(oldPath: String, newPath: String): FileResult<Boolean> {
        return try {
            val success = renameFile(oldPath, newPath)
            if (success) {
                FileResult.Success(true)
            } else if (!exists(oldPath)) {
                FileResult.Failure(FileError.NotFound(oldPath))
            } else if (exists(newPath)) {
                FileResult.Failure(FileError.AlreadyExists(newPath))
            } else {
                FileResult.Failure(FileError.IoFailure(oldPath, "Rename operation returned false"))
            }
        } catch (e: Exception) {
            FileResult.Failure(mapExceptionToError(oldPath, "renameFile", e))
        }
    }
    
    /**
     * Deletes a file or directory (recursively for directories).
     * 
     * @param path Path to the file or directory to delete
     * @return true if successful, false if path doesn't exist or on error
     */
    fun deleteFile(path: String): Boolean
    
    /**
     * Deletes a file or directory with error handling.
     * 
     * @param path Path to the file or directory to delete
     * @return FileResult indicating success or failure
     */
    fun deleteFileResult(path: String): FileResult<Boolean> {
        return try {
            val success = deleteFile(path)
            if (success) {
                FileResult.Success(true)
            } else if (!exists(path)) {
                FileResult.Failure(FileError.NotFound(path))
            } else {
                FileResult.Failure(FileError.IoFailure(path, "Delete operation returned false"))
            }
        } catch (e: Exception) {
            FileResult.Failure(mapExceptionToError(path, "deleteFile", e))
        }
    }
    
    /**
     * Checks if a path exists.
     * 
     * @param path Path to check
     * @return true if path exists, false otherwise
     */
    fun exists(path: String): Boolean
    
    /**
     * Moves a file or directory to the system recycle bin/trash.
     * 
     * @param path Path to the file or directory to move to trash
     * @return true if successful, false if path doesn't exist or on error
     */
    fun moveToTrash(path: String): Boolean
    
    /**
     * Moves a file or directory to the system recycle bin/trash with error handling.
     * 
     * @param path Path to the file or directory to move to trash
     * @return FileResult indicating success or failure
     */
    fun moveToTrashResult(path: String): FileResult<Boolean> {
        return try {
            val success = moveToTrash(path)
            if (success) {
                FileResult.Success(true)
            } else if (!exists(path)) {
                FileResult.Failure(FileError.NotFound(path))
            } else {
                FileResult.Failure(FileError.IoFailure(path, "Move to trash operation returned false"))
            }
        } catch (e: Exception) {
            FileResult.Failure(mapExceptionToError(path, "moveToTrash", e))
        }
    }
    
    /**
     * Maps an exception to a FileError.
     * 
     * This is a default implementation that can be overridden by implementations
     * to provide more specific error mapping based on platform-specific exception types.
     */
    fun mapExceptionToError(path: String, operation: String, exception: Throwable): FileError {
        val message = exception.message
        val exceptionName = exception::class.simpleName ?: "Unknown"
        
        // Generic error mapping - implementations should override for platform-specific types
        return when {
            message?.contains("not found", ignoreCase = true) == true ||
            message?.contains("does not exist", ignoreCase = true) == true ||
            exceptionName.contains("NoSuchFile", ignoreCase = true) ||
            exceptionName.contains("FileNotFound", ignoreCase = true) -> FileError.NotFound(path)
            message?.contains("permission", ignoreCase = true) == true ||
            message?.contains("access denied", ignoreCase = true) == true ||
            exceptionName.contains("AccessDenied", ignoreCase = true) -> FileError.PermissionDenied(path)
            message?.contains("already exists", ignoreCase = true) == true ||
            exceptionName.contains("FileAlreadyExists", ignoreCase = true) -> FileError.AlreadyExists(path)
            message?.contains("invalid", ignoreCase = true) == true ||
            exceptionName.contains("InvalidPath", ignoreCase = true) -> FileError.InvalidPath(path, message)
            else -> FileError.Unknown(path, message, exceptionName)
        }
    }
}

