package org.krypton.krypton.rag

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.krypton.krypton.util.AppLogger
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Service for detecting changes in vault files and determining sync status.
 */
class VaultSyncService(
    private val vaultMetadataService: VaultMetadataService,
    private val healthService: ChromaDBHealthService,
    private val vectorStore: VectorStore
) {
    /**
     * Checks the sync status of a vault.
     * 
     * @param vaultPath Absolute path of the vault
     * @return SyncStatus indicating the current state
     */
    suspend fun checkSyncStatus(vaultPath: String?): SyncStatus = withContext(Dispatchers.IO) {
        if (vaultPath == null) {
            return@withContext SyncStatus.NOT_INDEXED
        }
        
        // First check health
        val health = healthService.checkHealth()
        if (health == HealthStatus.UNHEALTHY) {
            return@withContext SyncStatus.UNAVAILABLE
        }
        
        // Check if vault is indexed
        val metadata = vaultMetadataService.getVaultMetadata(vaultPath)
        if (metadata == null) {
            return@withContext SyncStatus.NOT_INDEXED
        }
        
        // Check for changes
        val currentFiles = getCurrentVaultFiles(vaultPath)
        val indexedFiles = metadata.indexedFiles
        
        // Check for new or modified files
        val hasChanges = hasChanges(currentFiles, indexedFiles)
        
        return@withContext if (hasChanges) {
            SyncStatus.OUT_OF_SYNC
        } else {
            SyncStatus.SYNCED
        }
    }
    
    /**
     * Gets list of modified files in the vault.
     * 
     * @param vaultPath Absolute path of the vault
     * @return List of file paths (relative to vault) that have been modified
     */
    suspend fun getModifiedFiles(vaultPath: String): List<String> = withContext(Dispatchers.IO) {
        val metadata = vaultMetadataService.getVaultMetadata(vaultPath) ?: return@withContext emptyList()
        val indexedFiles = metadata.indexedFiles
        val currentFiles = getCurrentVaultFiles(vaultPath)
        
        val modified = mutableListOf<String>()
        
        for ((filePath, currentModified) in currentFiles) {
            val indexedModified = indexedFiles[filePath]
            if (indexedModified != null && currentModified > indexedModified) {
                modified.add(filePath)
            }
        }
        
        return@withContext modified
    }
    
    /**
     * Gets list of new files in the vault.
     * 
     * @param vaultPath Absolute path of the vault
     * @return List of file paths (relative to vault) that are new
     */
    suspend fun getNewFiles(vaultPath: String): List<String> = withContext(Dispatchers.IO) {
        val metadata = vaultMetadataService.getVaultMetadata(vaultPath) ?: return@withContext emptyList()
        val indexedFiles = metadata.indexedFiles
        val currentFiles = getCurrentVaultFiles(vaultPath)
        
        return@withContext currentFiles.keys.filter { !indexedFiles.containsKey(it) }
    }
    
    /**
     * Gets list of deleted files (files that were indexed but no longer exist).
     * 
     * @param vaultPath Absolute path of the vault
     * @return List of file paths (relative to vault) that have been deleted
     */
    suspend fun getDeletedFiles(vaultPath: String): List<String> = withContext(Dispatchers.IO) {
        val metadata = vaultMetadataService.getVaultMetadata(vaultPath) ?: return@withContext emptyList()
        val indexedFiles = metadata.indexedFiles
        val currentFiles = getCurrentVaultFiles(vaultPath)
        
        return@withContext indexedFiles.keys.filter { !currentFiles.containsKey(it) }
    }
    
    /**
     * Gets all files that need to be re-indexed (modified + new).
     * 
     * @param vaultPath Absolute path of the vault
     * @return List of file paths (relative to vault) that need indexing
     */
    suspend fun getFilesToReindex(vaultPath: String): List<String> = withContext(Dispatchers.IO) {
        val modified = getModifiedFiles(vaultPath)
        val new = getNewFiles(vaultPath)
        return@withContext (modified + new).distinct()
    }
    
    /**
     * Gets current vault files with their modification timestamps.
     * 
     * @param vaultPath Absolute path of the vault
     * @return Map of file paths (relative to vault) to their last modified timestamps
     */
    private suspend fun getCurrentVaultFiles(vaultPath: String): Map<String, Long> = withContext(Dispatchers.IO) {
        val vaultDir = File(vaultPath)
        if (!vaultDir.exists() || !vaultDir.isDirectory) {
            return@withContext emptyMap()
        }
        
        val files = mutableMapOf<String, Long>()
        val vaultPathObj = Paths.get(vaultPath)
        
        vaultDir.walkTopDown()
            .filter { it.isFile && it.extension == "md" }
            .forEach { file ->
                try {
                    val relativePath = vaultPathObj.relativize(file.toPath()).toString().replace('\\', '/')
                    val lastModified = Files.getLastModifiedTime(file.toPath()).toMillis()
                    files[relativePath] = lastModified
                } catch (e: Exception) {
                    AppLogger.w("VaultSyncService", "Failed to get file info for ${file.path}: ${e.message}")
                }
            }
        
        return@withContext files
    }
    
    /**
     * Checks if there are any changes between current files and indexed files.
     */
    private fun hasChanges(
        currentFiles: Map<String, Long>,
        indexedFiles: Map<String, Long>
    ): Boolean {
        // Check for new files
        if (currentFiles.keys.any { !indexedFiles.containsKey(it) }) {
            return true
        }
        
        // Check for deleted files
        if (indexedFiles.keys.any { !currentFiles.containsKey(it) }) {
            return true
        }
        
        // Check for modified files
        for ((filePath, currentModified) in currentFiles) {
            val indexedModified = indexedFiles[filePath]
            if (indexedModified != null && currentModified > indexedModified) {
                return true
            }
        }
        
        return false
    }
}

