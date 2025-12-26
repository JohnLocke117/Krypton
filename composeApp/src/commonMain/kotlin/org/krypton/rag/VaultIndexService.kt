package org.krypton.rag

/**
 * Interface for indexing vaults (collections of markdown files).
 * 
 * Handles file system access, chunking, embedding, and storage.
 * This interface is platform-independent and suitable for use across different platforms.
 */
interface VaultIndexService {
    /**
     * Performs a full reindex of all markdown files in a vault.
     * 
     * @param rootPath Root path of the vault (logical identifier)
     * @param existingFileHashes Optional map of existing file hashes for incremental indexing
     */
    suspend fun indexVault(
        rootPath: String,
        existingFileHashes: Map<String, String>? = null
    )
    
    /**
     * Indexes a single file.
     * 
     * @param path File path (relative to vault root or absolute)
     */
    suspend fun indexFile(path: String)
    
    /**
     * Removes a file from the index.
     * 
     * @param path File path to remove
     */
    suspend fun removeFile(path: String)
}

