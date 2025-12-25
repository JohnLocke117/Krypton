package org.krypton.krypton.rag

import kotlinx.coroutines.flow.Flow

/**
 * Event type for file system events.
 */
enum class FileSystemEventType {
    CREATE,
    MODIFY,
    DELETE
}

/**
 * Represents a file system event.
 * 
 * @param type The type of event (CREATE, MODIFY, DELETE)
 * @param relativePath The relative path of the file within the vault
 */
data class FileSystemEvent(
    val type: FileSystemEventType,
    val relativePath: String
)

/**
 * Interface for watching vault directories for file system changes.
 * 
 * Implementations should watch for CREATE, MODIFY, and DELETE events
 * and emit them via a Flow.
 */
interface VaultWatcher {
    /**
     * Starts watching the specified vault directory for file system events.
     * 
     * @param vaultPath Absolute path of the vault directory to watch
     * @return Flow of file system events with relative paths
     */
    fun watch(vaultPath: String): Flow<FileSystemEvent>
    
    /**
     * Stops watching the vault directory.
     */
    suspend fun stop()
}

