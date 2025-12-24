package org.krypton.krypton.rag

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * JVM implementation of NoteFileSystem using Java NIO Path APIs.
 * 
 * @param notesRoot The root directory containing markdown notes.
 *                  If null, uses the current working directory.
 */
actual class NoteFileSystem actual constructor(
    private val notesRoot: String?
) {
    
    private fun getRootPath(): Path? {
        return notesRoot?.let { Paths.get(it) } ?: Paths.get(".")
    }
    
    actual suspend fun listMarkdownFiles(): List<String> = withContext(Dispatchers.IO) {
        val root = getRootPath() ?: return@withContext emptyList()
        
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            return@withContext emptyList()
        }
        
        try {
            Files.walk(root)
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".md", ignoreCase = true) }
                .map { 
                    // Return relative path from root, or absolute if root is null
                    root.relativize(it).toString().replace('\\', '/')
                }
                .toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    actual suspend fun readFile(path: String): String? = withContext(Dispatchers.IO) {
        val root = getRootPath() ?: return@withContext null
        
        try {
            val filePath = if (Paths.get(path).isAbsolute) {
                Paths.get(path)
            } else {
                root.resolve(path)
            }
            
            if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                Files.readString(filePath)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    actual suspend fun getFileLastModified(path: String): Long? = withContext(Dispatchers.IO) {
        val root = getRootPath() ?: return@withContext null
        
        try {
            val filePath = if (Paths.get(path).isAbsolute) {
                Paths.get(path)
            } else {
                root.resolve(path)
            }
            
            if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                Files.getLastModifiedTime(filePath).toMillis()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

