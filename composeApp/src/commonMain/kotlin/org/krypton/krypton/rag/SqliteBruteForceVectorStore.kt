package org.krypton.krypton.rag

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.krypton.krypton.rag.NoteChunkDatabase

/**
 * SQLite-based vector store using brute-force cosine similarity.
 * 
 * This implementation:
 * 1. Stores embeddings as BLOB in SQLite
 * 2. Loads all chunks into memory
 * 3. Computes cosine similarity in Kotlin
 * 4. Returns top-k results
 * 
 * @param driver SQLDelight driver for database access
 */
class SqliteBruteForceVectorStore(
    private val driver: SqlDriver
) : VectorStore {
    
    private val database = NoteChunkDatabase(driver)
    
    // Tables are created automatically by SQLDelight when the database is initialized
    
    override suspend fun upsert(chunks: List<NoteChunk>) = withContext(Dispatchers.IO) {
        if (chunks.isEmpty()) return@withContext
        
        database.transaction {
            for (chunk in chunks) {
                require(chunk.embedding != null) { 
                    "Cannot upsert chunk without embedding: ${chunk.id}" 
                }
                
                val embeddingBytes = chunk.embedding.toByteArray()
                
                database.noteChunkEntityQueries.insertOrReplace(
                    id = chunk.id,
                    filePath = chunk.filePath,
                    startLine = chunk.startLine.toLong(),
                    endLine = chunk.endLine.toLong(),
                    text = chunk.text,
                    embedding = embeddingBytes
                )
            }
        }
    }
    
    override suspend fun search(queryEmbedding: FloatArray, topK: Int): List<NoteChunk> = 
        withContext(Dispatchers.Default) {
            // Load all chunks from database
            val allEntities = database.noteChunkEntityQueries.selectAll().executeAsList()
            
            if (allEntities.isEmpty()) {
                return@withContext emptyList()
            }
            
            // Convert entities to chunks and compute similarities
            val chunksWithSimilarity = allEntities.mapNotNull { entity ->
                try {
                    val embedding = entity.embedding.toFloatArray()
                    val similarity = cosineSimilarity(queryEmbedding, embedding)
                    
                    NoteChunk(
                        id = entity.id,
                        filePath = entity.filePath,
                        startLine = entity.startLine.toInt(),
                        endLine = entity.endLine.toInt(),
                        text = entity.text,
                        embedding = embedding
                    ) to similarity
                } catch (e: Exception) {
                    // Skip chunks with invalid embeddings
                    null
                }
            }
            
            // Sort by similarity (descending) and take top-k
            chunksWithSimilarity
                .sortedByDescending { it.second }
                .take(topK)
                .map { it.first }
        }
    
    override suspend fun clear() = withContext(Dispatchers.IO) {
        database.noteChunkEntityQueries.deleteAll()
    }
    
    override suspend fun deleteByFilePath(filePath: String) = withContext(Dispatchers.IO) {
        database.noteChunkEntityQueries.deleteByFilePath(filePath)
    }
    
    /**
     * Computes cosine similarity between two vectors.
     * 
     * @return Similarity score between -1 and 1 (1 = identical, 0 = orthogonal, -1 = opposite)
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { 
            "Vectors must have the same size: ${a.size} != ${b.size}" 
        }
        
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        val denominator = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        return if (denominator == 0f) 0f else dotProduct / denominator
    }
}

