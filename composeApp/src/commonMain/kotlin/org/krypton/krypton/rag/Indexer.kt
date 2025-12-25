package org.krypton.krypton.rag

import org.krypton.krypton.config.RagDefaults
import org.krypton.krypton.util.Logger
import org.krypton.krypton.util.createLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Service for indexing markdown notes into the vector store.
 * 
 * Coordinates file system access, chunking, embedding, and storage.
 */
open class Indexer(
    protected val fileSystem: NoteFileSystem,
    protected val chunker: MarkdownChunker,
    protected val embedder: Embedder,
    protected val vectorStore: VectorStore,
    protected val logger: Logger = createLogger("Indexer"),
    protected open val fileSystemFactory: ((String?) -> NoteFileSystem)? = null
) {
    /**
     * Optional callback to update vault metadata after indexing.
     * Set this to track indexed files.
     * This is a suspend function to allow async operations.
     */
    var onIndexingComplete: (suspend (vaultPath: String, indexedFiles: Map<String, Long>, indexedFileHashes: Map<String, String>) -> Unit)? = null
    /**
     * Performs a full reindex of all markdown files.
     * 
     * This will:
     * 1. List all .md files
     * 2. For each file: read, chunk, embed, and upsert into vector store
     * 3. Skip files with unchanged hashes if existing metadata is provided
     * 
     * Note: This does not clear the vector store first. Use vectorStore.clear() if needed.
     * 
     * @param vaultPath Optional vault path for metadata tracking. If provided, this path will be used as the root for file listing.
     * @param existingFileHashes Optional map of existing file hashes. Files with matching hashes will be skipped.
     */
    suspend fun fullReindex(
        vaultPath: String? = null,
        existingFileHashes: Map<String, String>? = null
    ) = withContext(Dispatchers.Default) {
        try {
            // If vaultPath is provided and we have a factory, create a new NoteFileSystem with that path
            // Otherwise, use the existing fileSystem
            val fileSystemToUse = if (vaultPath != null && fileSystemFactory != null) {
                fileSystemFactory?.invoke(vaultPath) ?: fileSystem
            } else {
                fileSystem
            }
            
            val files = fileSystemToUse.listMarkdownFiles()
            logger.info("Found ${files.size} markdown files to index")
            
            if (files.isEmpty()) {
                logger.warn("No markdown files found to index")
                return@withContext
            }
            
            // Filter out unchanged files if existing hashes are provided
            val filesToIndex = if (existingFileHashes != null && vaultPath != null) {
                files.filter { filePath ->
                    // Compute hash for file
                    val currentHash = try {
                        computeFileHash(filePath, vaultPath, fileSystemToUse)
                    } catch (e: Exception) {
                        logger.warn("Failed to compute hash for $filePath, will index: ${e.message}")
                        "" // Index if hash computation fails
                    }
                    
                    val existingHash = existingFileHashes[filePath]
                    val shouldIndex = existingHash == null || currentHash != existingHash
                    
                    if (!shouldIndex) {
                        logger.debug("Skipping unchanged file: $filePath")
                    }
                    
                    shouldIndex
                }
            } else {
                files
            }
            
            val filesSkipped = files.size - filesToIndex.size
            if (filesSkipped > 0) {
                logger.info("Skipping $filesSkipped unchanged files")
            }
            
            // Hash-only tracking (no timestamps)
            val indexedFileHashes = mutableMapOf<String, String>()
            
            // Process files in parallel with concurrency limit
            val maxConcurrentFiles = 4 // Limit concurrent file processing
            val semaphore = Semaphore(maxConcurrentFiles)
            
            coroutineScope {
                val results = filesToIndex.map { filePath ->
                    async {
                        semaphore.withPermit {
                            try {
                                logger.debug("Indexing file: $filePath")
                                val result = indexFileWithFileSystem(filePath, fileSystemToUse, vaultPath)
                                if (result != null && result.hash.isNotEmpty()) {
                                    indexedFileHashes[filePath] = result.hash
                                }
                                result
                            } catch (e: Exception) {
                                // Log error but continue with other files
                                logger.error("Failed to index file $filePath: ${e.message}", e)
                                null
                            }
                        }
                    }
                }.awaitAll()
            }
            
            // Also include skipped files in the final hash map (for metadata update)
            existingFileHashes?.forEach { (filePath, hash) ->
                if (!indexedFileHashes.containsKey(filePath)) {
                    indexedFileHashes[filePath] = hash
                }
            }
            
            logger.info("Indexed ${indexedFileHashes.size - filesSkipped} files successfully (skipped $filesSkipped unchanged)")
            
            // Update metadata if callback is set (pass empty map for indexedFiles - hash-only tracking)
            if (vaultPath != null && indexedFileHashes.isNotEmpty()) {
                onIndexingComplete?.invoke(vaultPath, emptyMap(), indexedFileHashes)
            }
        } catch (e: Exception) {
            logger.error("Failed to perform full reindex: ${e.message}", e)
            throw IndexingException("Failed to perform full reindex: ${e.message}", e)
        }
    }
    
    /**
     * Indexes only the specified files (for incremental updates).
     * 
     * @param filePaths List of file paths to index
     * @param vaultPath Optional vault path for metadata tracking. If provided, this path will be used as the root for file reading.
     */
    suspend fun indexModifiedFiles(filePaths: List<String>, vaultPath: String? = null) = withContext(Dispatchers.Default) {
        try {
            // If vaultPath is provided and we have a factory, create a new NoteFileSystem with that path
            // Otherwise, use the existing fileSystem
            val fileSystemToUse = if (vaultPath != null && fileSystemFactory != null) {
                fileSystemFactory?.invoke(vaultPath) ?: fileSystem
            } else {
                fileSystem
            }
            
            logger.info("Indexing ${filePaths.size} modified files")
            // Hash-only tracking (no timestamps)
            val indexedFileHashes = mutableMapOf<String, String>()
            
            for ((index, filePath) in filePaths.withIndex()) {
                try {
                    logger.debug("Indexing modified file ${index + 1}/${filePaths.size}: $filePath")
                    val result = indexFileWithFileSystem(filePath, fileSystemToUse, vaultPath)
                    if (result != null && result.hash.isNotEmpty()) {
                        indexedFileHashes[filePath] = result.hash
                    }
                } catch (e: Exception) {
                    logger.error("Failed to index file $filePath: ${e.message}", e)
                }
            }
            
            logger.info("Indexed ${indexedFileHashes.size} modified files successfully")
            
            // Update metadata if callback is set (pass empty map for indexedFiles - hash-only tracking)
            if (vaultPath != null && indexedFileHashes.isNotEmpty()) {
                onIndexingComplete?.invoke(vaultPath, emptyMap(), indexedFileHashes)
            }
        } catch (e: Exception) {
            logger.error("Failed to index modified files: ${e.message}", e)
            throw IndexingException("Failed to index modified files: ${e.message}", e)
        }
    }
    
    /**
     * Result of indexing a file.
     */
    data class IndexFileResult(
        val lastModified: Long,
        val hash: String
    )
    
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
     * @return IndexFileResult with lastModified and hash, or null if file couldn't be read
     */
    suspend fun indexFile(filePath: String, vaultPath: String? = null): IndexFileResult? = indexFileWithFileSystem(filePath, fileSystem, vaultPath)
    
    /**
     * Indexes a single markdown file using a specific file system.
     * 
     * @param filePath Path to the markdown file to index
     * @param fileSystemToUse The file system to use for reading the file
     * @param vaultPath Optional vault path for computing file hash
     * @return IndexFileResult with lastModified and hash, or null if file couldn't be read
     */
    private suspend fun indexFileWithFileSystem(filePath: String, fileSystemToUse: NoteFileSystem, vaultPath: String?): IndexFileResult? = withContext(Dispatchers.Default) {
        try {
            // Read file
            val content = fileSystemToUse.readFile(filePath)
                ?: throw IndexingException("Could not read file: $filePath")
            
            // Get file modification time
            val lastModified = fileSystemToUse.getFileLastModified(filePath)
                ?: throw IndexingException("Could not get last modified time for file: $filePath")
            
            // Compute file hash (platform-specific, will be implemented in JVM)
            val hash = computeFileHash(filePath, vaultPath, fileSystemToUse)
            
            // Chunk content
            val chunks = chunker.chunk(content, filePath)
            
            if (chunks.isEmpty()) {
                // File is empty or has no chunkable content
                logger.debug("File $filePath has no chunkable content")
                return@withContext IndexFileResult(lastModified, hash)
            }
            
            logger.debug("Chunked file $filePath into ${chunks.size} chunks")
            
            // Delete existing chunks for this file (to handle updates)
            // This is best-effort - if it fails, we'll just upsert which will overwrite
            try {
                vectorStore.deleteByFilePath(filePath)
            } catch (e: Exception) {
                logger.warn("Failed to delete existing chunks for $filePath (will upsert anyway): ${e.message}")
            }
            
            // Generate embeddings in batches
            logger.debug("Generating embeddings for ${chunks.size} chunks from file $filePath")
            val texts = chunks.map { it.text }
            val maxBatchSize = RagDefaults.DEFAULT_EMBEDDING_BATCH_SIZE
            
            // Batch chunks for embedding to reduce HTTP overhead
            val batches = texts.chunked(maxBatchSize)
            val allEmbeddings = mutableListOf<FloatArray>()
            
            for ((batchIndex, batch) in batches.withIndex()) {
                logger.debug("Embedding batch ${batchIndex + 1}/${batches.size} (${batch.size} chunks)")
                val batchEmbeddings = embedder.embed(batch, EmbeddingTaskType.SEARCH_DOCUMENT)
                allEmbeddings.addAll(batchEmbeddings)
            }
            
            val embeddings = allEmbeddings
            
            if (embeddings.size != chunks.size) {
                throw IndexingException(
                    "Embedding count mismatch: expected ${chunks.size}, got ${embeddings.size}"
                )
            }
            
            logger.debug("Generated ${embeddings.size} embeddings for file $filePath")
            
            // Attach embeddings to chunks
            val chunksWithEmbeddings = chunks.zip(embeddings).map { (chunk, embedding) ->
                chunk.copy(embedding = embedding)
            }
            
            // Upsert into vector store (with vaultPath and hash if available)
            upsertChunksWithMetadata(chunksWithEmbeddings, vaultPath, hash)
            logger.debug("Upserted ${chunksWithEmbeddings.size} chunks for file $filePath into vector store")
            
            return@withContext IndexFileResult(lastModified, hash)
        } catch (e: IndexingException) {
            throw e
        } catch (e: Exception) {
            throw IndexingException("Failed to index file $filePath: ${e.message}", e)
        }
    }
    
    /**
     * Computes file hash. Platform-specific implementation.
     * Default implementation returns empty string.
     */
    protected open suspend fun computeFileHash(filePath: String, vaultPath: String?, fileSystem: NoteFileSystem): String {
        // Default: no hash computation (platform-specific implementations will override)
        return ""
    }
    
    /**
     * Upserts chunks with metadata. Default implementation just calls vectorStore.upsert.
     * Platform-specific implementations can override to add vault_path and file_hash metadata.
     */
    protected open suspend fun upsertChunksWithMetadata(chunks: List<NoteChunk>, vaultPath: String?, fileHash: String) {
        vectorStore.upsert(chunks)
    }
    
}

/**
 * Exception thrown when indexing fails.
 */
class IndexingException(message: String, cause: Throwable? = null) : Exception(message, cause)

