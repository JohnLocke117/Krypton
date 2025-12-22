package org.krypton.krypton.rag

import app.cash.sqldelight.db.SqlDriver

/**
 * SQLite vector store using the sqlite-vec extension for efficient vector similarity search.
 * 
 * This is a skeleton implementation for future use with sqlite-vec.
 * Currently, it delegates to brute-force implementation.
 * 
 * TODO: When sqlite-vec is available:
 * 1. Load the sqlite-vec extension in the database connection
 * 2. Use vec0_* functions for vector operations
 * 3. Create vector columns using vec0_fvec32() or similar
 * 4. Use ORDER BY vec_cosine_distance(embedding, ?) LIMIT ? for efficient search
 * 5. Use vec0_insert() or similar for efficient upserts
 * 
 * Example query (when implemented):
 * ```sql
 * SELECT *, vec_cosine_distance(embedding, ?) as distance
 * FROM NoteChunkEntity
 * ORDER BY distance
 * LIMIT ?
 * ```
 * 
 * @param driver SQLDelight driver for database access
 */
class SqliteVectorExtensionStore(
    private val driver: SqlDriver
) : VectorStore {
    
    // For now, delegate to brute-force implementation
    // TODO: Replace with sqlite-vec implementation
    private val delegate = SqliteBruteForceVectorStore(driver)
    
    override suspend fun upsert(chunks: List<NoteChunk>) {
        // TODO: Use vec0_insert() or similar when sqlite-vec is available
        delegate.upsert(chunks)
    }
    
    override suspend fun search(queryEmbedding: FloatArray, topK: Int): List<NoteChunk> {
        // TODO: Use vec_cosine_distance() in SQL query when sqlite-vec is available
        return delegate.search(queryEmbedding, topK)
    }
    
    override suspend fun clear() {
        delegate.clear()
    }
    
    override suspend fun deleteByFilePath(filePath: String) {
        delegate.deleteByFilePath(filePath)
    }
}

