package org.krypton.rag

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.krypton.data.files.FileSystem
import java.io.File

/**
 * Android implementation of NoteFileSystem using Android file APIs.
 */
actual class NoteFileSystem actual constructor(
    private val notesRoot: String?
) {
    
    private fun getRootPath(): File? {
        return notesRoot?.let { File(it) } ?: null
    }
    
    actual suspend fun listMarkdownFiles(): List<String> = withContext(Dispatchers.IO) {
        val root = getRootPath() ?: return@withContext emptyList()
        
        if (!root.exists() || !root.isDirectory) {
            return@withContext emptyList()
        }
        
        try {
            val rootPath = root.absolutePath
            root.walkTopDown()
                .filter { it.isFile }
                .filter { it.name.endsWith(".md", ignoreCase = true) }
                .map { file ->
                    // Return relative path from root, or absolute if root is null
                    if (notesRoot != null) {
                        val filePath = file.absolutePath
                        if (filePath.startsWith(rootPath)) {
                            filePath.substring(rootPath.length).trimStart('/').replace('\\', '/')
                        } else {
                            filePath
                        }
                    } else {
                        file.absolutePath
                    }
                }
                .toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    actual suspend fun readFile(path: String): String? = withContext(Dispatchers.IO) {
        val root = getRootPath() ?: return@withContext null
        
        try {
            val filePath = if (File(path).isAbsolute) {
                File(path)
            } else {
                File(root, path)
            }
            
            if (filePath.exists() && filePath.isFile) {
                filePath.readText()
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
            val filePath = if (File(path).isAbsolute) {
                File(path)
            } else {
                File(root, path)
            }
            
            if (filePath.exists() && filePath.isFile) {
                filePath.lastModified()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

