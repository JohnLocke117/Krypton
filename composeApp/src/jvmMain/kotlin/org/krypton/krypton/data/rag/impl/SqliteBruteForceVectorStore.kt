package org.krypton.krypton.data.rag.impl

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.krypton.krypton.rag.NoteChunk
import org.krypton.krypton.rag.NoteChunkDatabase
import org.krypton.krypton.rag.VectorStore
import org.krypton.krypton.rag.toByteArray
import org.krypton.krypton.rag.toFloatArray
import org.krypton.krypton.util.AppLogger

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
        
        try {
            database.transaction {
                for (chunk in chunks) {
                    try {
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
                    } catch (e: Exception) {
                        AppLogger.e("SqliteBruteForceVectorStore", "Failed to upsert chunk ${chunk.id} from ${chunk.filePath}", e)
                        // Continue with other chunks
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("SqliteBruteForceVectorStore", "Failed to upsert ${chunks.size} chunks", e)
            throw e
        }
    }
    
    override suspend fun search(queryEmbedding: FloatArray, topK: Int): List<NoteChunk> = 
        withContext(Dispatchers.Default) {
            try {
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
                        AppLogger.w("SqliteBruteForceVectorStore", "Skipping chunk ${entity.id} with invalid embedding", e)
                        null
                    }
                }
                
                // Sort by similarity (descending) and take top-k
                chunksWithSimilarity
                    .sortedByDescending { it.second }
                    .take(topK)
                    .map { it.first }
            } catch (e: Exception) {
                AppLogger.e("SqliteBruteForceVectorStore", "Failed to search vector store (query size: ${queryEmbedding.size}, topK: $topK)", e)
                // Return empty list on error rather than crashing
                emptyList()
            }
        }
    
    override suspend fun clear() = withContext(Dispatchers.IO) {
        try {
            database.noteChunkEntityQueries.deleteAll()
        } catch (e: Exception) {
            AppLogger.e("SqliteBruteForceVectorStore", "Failed to clear vector store", e)
            throw e
        }
    }
    
    override suspend fun deleteByFilePath(filePath: String) = withContext(Dispatchers.IO) {
        try {
            database.noteChunkEntityQueries.deleteByFilePath(filePath)
        } catch (e: Exception) {
            AppLogger.e("SqliteBruteForceVectorStore", "Failed to delete chunks for file: $filePath", e)
            throw e
        }
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

