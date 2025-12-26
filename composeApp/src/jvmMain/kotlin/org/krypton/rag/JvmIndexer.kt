package org.krypton.rag

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.krypton.data.rag.impl.ChromaDBVectorStore
import org.krypton.util.Logger
import org.krypton.util.createLogger
import java.io.File
import java.nio.file.Paths

/**
 * JVM-specific Indexer that computes file hashes and includes vault metadata.
 */
class JvmIndexer(
    fileSystem: NoteFileSystem,
    chunker: MarkdownChunker,
    embedder: Embedder,
    vectorStore: VectorStore,
    logger: Logger = createLogger("JvmIndexer"),
    override val fileSystemFactory: ((String?) -> NoteFileSystem)? = null
) : Indexer(fileSystem, chunker, embedder, vectorStore, logger, fileSystemFactory) {
    
    override suspend fun computeFileHash(filePath: String, vaultPath: String?, fileSystem: NoteFileSystem): String = withContext(Dispatchers.IO) {
        try {
            // Resolve the actual file path using the same logic as NoteFileSystem
            val actualFile = if (vaultPath != null) {
                // If vaultPath is provided, resolve relative to vault (same as NoteFileSystem.readFile)
                val vaultPathObj = Paths.get(vaultPath)
                val filePathObj = if (Paths.get(filePath).isAbsolute) {
                    Paths.get(filePath)
                } else {
                    vaultPathObj.resolve(filePath)
                }
                filePathObj.toFile()
            } else {
                // Fallback: try to use filePath as-is (might be absolute)
                File(filePath)
            }
            
            if (actualFile.exists() && actualFile.isFile) {
                FileHashUtil.computeFileHash(actualFile)
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }
    
    override suspend fun upsertChunksWithMetadata(chunks: List<RagChunk>, vaultPath: String?, fileHash: String) {
        // If vectorStore is ChromaDBVectorStore, use the enhanced upsert with metadata
        if (vectorStore is ChromaDBVectorStore && vaultPath != null && fileHash.isNotEmpty()) {
            vectorStore.upsertWithMetadata(chunks, vaultPath, fileHash)
        } else {
            // Fallback to standard upsert
            vectorStore.upsert(chunks)
        }
    }
}

