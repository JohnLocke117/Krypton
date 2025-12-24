package org.krypton.krypton.rag

/**
 * Represents the synchronization status of a vault with the vector database.
 */
enum class SyncStatus {
    /**
     * Green - All files are synced, no changes detected.
     */
    SYNCED,
    
    /**
     * Yellow - Changes detected but database is accessible.
     * RAG can still work but may have outdated information.
     */
    OUT_OF_SYNC,
    
    /**
     * Yellow - Vault has never been indexed.
     */
    NOT_INDEXED,
    
    /**
     * Red - Vector database is unavailable (health check failed).
     */
    UNAVAILABLE
}

