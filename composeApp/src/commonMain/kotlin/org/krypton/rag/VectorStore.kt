package org.krypton.rag

import org.krypton.rag.models.Embedding

/**
 * Interface for storing and searching vector embeddings.
 * 
 * This interface is platform-independent and suitable for use across different platforms.
 */
interface VectorStore {
    /**
     * Upserts (inserts or updates) chunks into the vector store.
     * 
     * @param chunks List of RagChunk objects to store. Embeddings must be non-null.
     */
    suspend fun upsert(chunks: List<RagChunk>)
    
    /**
     * Searches for the top-k most similar chunks to the query embedding.
     * 
     * @param queryEmbedding The embedding vector to search for
     * @param topK Number of results to return
     * @param filters Optional metadata filters (e.g., filePath, sectionTitle)
     * @return List of SearchResult objects ordered by similarity (most similar first)
     */
    suspend fun search(
        queryEmbedding: Embedding,
        topK: Int,
        filters: Map<String, String> = emptyMap()
    ): List<SearchResult>
    
    /**
     * Clears all chunks from the vector store.
     */
    suspend fun clear()
    
    /**
     * Deletes all chunks for a specific file.
     * 
     * @param filePath Path to the file whose chunks should be deleted
     */
    suspend fun deleteByFilePath(filePath: String)
    
    /**
     * Checks if the vector store has any data for the specified vault.
     * 
     * @param vaultPath Absolute path of the vault
     * @return true if any documents exist for this vault, false otherwise
     */
    suspend fun hasVaultData(vaultPath: String): Boolean
    
    /**
     * Legacy method for backward compatibility.
     * 
     * @deprecated Use search(Embedding, Int, Map) instead
     */
    @Deprecated("Use search(Embedding, Int, Map) instead", ReplaceWith("search(Embedding(queryEmbedding.toList()), topK, emptyMap())"))
    suspend fun search(queryEmbedding: FloatArray, topK: Int): List<SearchResult> {
        return search(Embedding(queryEmbedding.toList()), topK, emptyMap())
    }
    
}

