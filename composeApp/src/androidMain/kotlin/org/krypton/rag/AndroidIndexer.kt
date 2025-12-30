package org.krypton.rag

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.krypton.data.rag.impl.ChromaDBVectorStore
import org.krypton.util.AppLogger
import org.krypton.util.Logger
import org.krypton.util.createLogger
import java.security.MessageDigest

/**
 * Android-specific Indexer that implements VaultIndexService.
 * 
 * Provides indexing functionality for Android platform.
 */
class AndroidIndexer(
    private val fileSystem: NoteFileSystem,
    private val chunker: MarkdownChunker,
    private val embedder: Embedder,
    private val vectorStore: VectorStore,
    private val logger: Logger = createLogger("AndroidIndexer"),
    private val fileSystemFactory: ((String?) -> NoteFileSystem)? = null
) : VaultIndexService {
    
    var onIndexingComplete: (suspend (vaultPath: String, indexedFiles: Map<String, Long>, indexedFileHashes: Map<String, String>) -> Unit)? = null
    
    override suspend fun indexVault(
        rootPath: String,
        existingFileHashes: Map<String, String>?
    ) = withContext(Dispatchers.Default) {
        try {
            // Create NoteFileSystem for this vault
            val fileSystemToUse = if (rootPath.isNotEmpty() && fileSystemFactory != null) {
                fileSystemFactory?.invoke(rootPath) ?: fileSystem
            } else if (rootPath.isNotEmpty()) {
                NoteFileSystem(rootPath)
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
            val filesToIndex = if (existingFileHashes != null && rootPath.isNotEmpty()) {
                files.filter { filePath ->
                    val currentHash = try {
                        computeFileHash(filePath, rootPath, fileSystemToUse)
                    } catch (e: Exception) {
                        logger.warn("Failed to compute hash for $filePath, will index: ${e.message}")
                        ""
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
            
            val indexedFileHashes = mutableMapOf<String, String>()
            val indexedFiles = mutableMapOf<String, Long>()
            val semaphore = Semaphore(5) // Limit concurrent operations
            
            coroutineScope {
                filesToIndex.map { filePath ->
                    async {
                        semaphore.withPermit {
                            try {
                                val content = fileSystemToUse.readFile(filePath)
                                if (content == null) {
                                    logger.warn("Failed to read file: $filePath")
                                    return@withPermit
                                }
                                
                                val fileHash = computeFileHash(filePath, rootPath, fileSystemToUse)
                                val chunks = chunker.chunk(filePath, content)
                                
                                if (chunks.isNotEmpty()) {
                                    upsertChunksWithMetadata(chunks, rootPath, fileHash)
                                    indexedFileHashes[filePath] = fileHash
                                    indexedFiles[filePath] = System.currentTimeMillis()
                                    logger.debug("Indexed file: $filePath (${chunks.size} chunks)")
                                }
                            } catch (e: Exception) {
                                logger.warn("Failed to index file $filePath: ${e.message}", e)
                            }
                        }
                    }
                }.awaitAll()
            }
            
            logger.info("Indexing complete: ${indexedFiles.size} files indexed")
            
            // Call completion callback if set
            onIndexingComplete?.invoke(rootPath, indexedFiles, indexedFileHashes)
        } catch (e: Exception) {
            logger.error("Failed to index vault: ${e.message}", e)
            throw e
        }
    }
    
    private suspend fun computeFileHash(filePath: String, vaultPath: String?, fileSystem: NoteFileSystem): String = withContext(Dispatchers.IO) {
        try {
            val content = fileSystem.readFile(filePath)
            if (content != null) {
                val digest = MessageDigest.getInstance("SHA-256")
                val hashBytes = digest.digest(content.toByteArray())
                hashBytes.joinToString("") { "%02x".format(it) }
            } else {
                ""
            }
        } catch (e: Exception) {
            AppLogger.w("AndroidIndexer", "Failed to compute file hash for $filePath: ${e.message}", e)
            ""
        }
    }
    
    private suspend fun upsertChunksWithMetadata(chunks: List<RagChunk>, vaultPath: String?, fileHash: String) {
        if (vectorStore is ChromaDBVectorStore && vaultPath != null && fileHash.isNotEmpty()) {
            try {
                vectorStore.upsertWithMetadata(chunks, vaultPath, fileHash)
            } catch (e: Exception) {
                // Fallback to standard upsert if metadata upsert fails
                logger.warn("Failed to upsert with metadata, using standard upsert: ${e.message}")
                vectorStore.upsert(chunks)
            }
        } else {
            vectorStore.upsert(chunks)
        }
    }
    
    override suspend fun indexFile(path: String) {
        try {
            val content = fileSystem.readFile(path)
            if (content != null) {
                val chunks = chunker.chunk(path, content)
                if (chunks.isNotEmpty()) {
                    vectorStore.upsert(chunks)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to index file $path: ${e.message}", e)
        }
    }
    
    override suspend fun removeFile(path: String) {
        try {
            vectorStore.deleteByFilePath(path)
        } catch (e: Exception) {
            logger.error("Failed to remove file $path from index: ${e.message}", e)
        }
    }
}

