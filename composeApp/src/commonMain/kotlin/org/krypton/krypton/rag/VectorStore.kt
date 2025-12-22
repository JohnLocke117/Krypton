package org.krypton.krypton.rag

/**
 * Interface for storing and searching vector embeddings.
 */
interface VectorStore {
    /**
     * Upserts (inserts or updates) chunks into the vector store.
     * 
     * @param chunks List of NoteChunk objects to store. Embeddings must be non-null.
     */
    suspend fun upsert(chunks: List<NoteChunk>)
    
    /**
     * Searches for the top-k most similar chunks to the query embedding.
     * 
     * @param queryEmbedding The embedding vector to search for
     * @param topK Number of results to return
     * @return List of NoteChunk objects, ordered by similarity (most similar first)
     */
    suspend fun search(queryEmbedding: FloatArray, topK: Int): List<NoteChunk>
    
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
}

