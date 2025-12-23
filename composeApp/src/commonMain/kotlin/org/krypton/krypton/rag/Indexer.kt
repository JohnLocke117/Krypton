package org.krypton.krypton.rag

import org.krypton.krypton.util.Logger
import org.krypton.krypton.util.createLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service for indexing markdown notes into the vector store.
 * 
 * Coordinates file system access, chunking, embedding, and storage.
 */
class Indexer(
    private val fileSystem: NoteFileSystem,
    private val chunker: MarkdownChunker,
    private val embedder: Embedder,
    private val vectorStore: VectorStore,
    private val logger: Logger = createLogger("Indexer")
) {
    /**
     * Performs a full reindex of all markdown files.
     * 
     * This will:
     * 1. List all .md files
     * 2. For each file: read, chunk, embed, and upsert into vector store
     * 
     * Note: This does not clear the vector store first. Use vectorStore.clear() if needed.
     */
    suspend fun fullReindex() = withContext(Dispatchers.Default) {
        try {
            val files = fileSystem.listMarkdownFiles()
            
            for (filePath in files) {
                try {
                    indexFile(filePath)
                } catch (e: Exception) {
                    // Log error but continue with other files
                    logger.error("Failed to index file $filePath: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            throw IndexingException("Failed to perform full reindex: ${e.message}", e)
        }
    }
    
    /**
     * Indexes a single markdown file.
     * 
     * This will:
     * 1. Read the file content
     * 2. Chunk the content
     * 3. Generate embeddings for chunks
     * 4. Upsert chunks into vector store
     * 
     * @param filePath Path to the markdown file to index
     */
    suspend fun indexFile(filePath: String) = withContext(Dispatchers.Default) {
        try {
            // Read file
            val content = fileSystem.readFile(filePath)
                ?: throw IndexingException("Could not read file: $filePath")
            
            // Chunk content
            val chunks = chunker.chunk(content, filePath)
            
            if (chunks.isEmpty()) {
                // File is empty or has no chunkable content
                return@withContext
            }
            
            // Delete existing chunks for this file (to handle updates)
            vectorStore.deleteByFilePath(filePath)
            
            // Generate embeddings
            val texts = chunks.map { it.text }
            val embeddings = embedder.embed(texts)
            
            if (embeddings.size != chunks.size) {
                throw IndexingException(
                    "Embedding count mismatch: expected ${chunks.size}, got ${embeddings.size}"
                )
            }
            
            // Attach embeddings to chunks
            val chunksWithEmbeddings = chunks.zip(embeddings).map { (chunk, embedding) ->
                chunk.copy(embedding = embedding)
            }
            
            // Upsert into vector store
            vectorStore.upsert(chunksWithEmbeddings)
        } catch (e: IndexingException) {
            throw e
        } catch (e: Exception) {
            throw IndexingException("Failed to index file $filePath: ${e.message}", e)
        }
    }
}

/**
 * Exception thrown when indexing fails.
 */
class IndexingException(message: String, cause: Throwable? = null) : Exception(message, cause)

