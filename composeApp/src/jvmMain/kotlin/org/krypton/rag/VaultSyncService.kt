package org.krypton.rag

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.krypton.util.AppLogger
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Represents the current state of files in a vault.
 * Uses hash-only tracking (no timestamps).
 */
data class VaultState(
    val fileHashes: Map<String, String>     // filePath -> hash
)

/**
 * Represents detected changes in a vault.
 */
data class VaultChanges(
    val newFiles: List<String>,
    val modifiedFiles: List<String>,
    val deletedFiles: List<String>
)

/**
 * Service for detecting changes in vault files and determining sync status.
 */
class VaultSyncService(
    private val vaultMetadataService: VaultMetadataService,
    private val healthService: ChromaDBHealthService,
    private val vectorStore: VectorStore
) {
    /**
     * Checks the sync status of a vault using hash-based detection.
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
        
        // If metadata is null, check if collection has data
        // If collection has data but metadata is missing, we can't determine sync status accurately
        // Return SYNCED optimistically to avoid false positives (user can manually re-index if needed)
        if (metadata == null) {
            val hasData = vectorStore.hasVaultData(vaultPath)
            return@withContext if (hasData) {
                SyncStatus.SYNCED // Collection has data - assume synced (can't verify without metadata)
            } else {
                SyncStatus.NOT_INDEXED // No data at all
            }
        }
        
        // Get current vault state with hashes
        val currentState = getCurrentVaultState(vaultPath)
        
        // Detect changes using hash-based comparison
        val changes = detectChanges(
            currentHashes = currentState.fileHashes,
            indexedHashes = metadata.indexedFileHashes
        )
        
        return@withContext if (changes.newFiles.isEmpty() && changes.modifiedFiles.isEmpty() && changes.deletedFiles.isEmpty()) {
            SyncStatus.SYNCED
        } else {
            SyncStatus.OUT_OF_SYNC
        }
    }
    
    /**
     * Detects changes in a vault and returns structured change information.
     * 
     * @param vaultPath Absolute path of the vault
     * @return VaultChanges with lists of new, modified, and deleted files
     */
    suspend fun detectChanges(vaultPath: String): VaultChanges = withContext(Dispatchers.IO) {
        val metadata = vaultMetadataService.getVaultMetadata(vaultPath) ?: return@withContext VaultChanges(
            newFiles = emptyList(),
            modifiedFiles = emptyList(),
            deletedFiles = emptyList()
        )
        
        val currentState = getCurrentVaultState(vaultPath)
        
        return@withContext detectChanges(
            currentHashes = currentState.fileHashes,
            indexedHashes = metadata.indexedFileHashes
        )
    }
    
    /**
     * Gets the current state of files in the vault with their hashes.
     * 
     * Computes SHA-256 hash for all .md files in the vault.
     * Uses hash-only tracking (no timestamps).
     * 
     * @param vaultPath Absolute path of the vault
     * @return VaultState with file hashes
     */
    private suspend fun getCurrentVaultState(vaultPath: String): VaultState = withContext(Dispatchers.IO) {
        val vaultDir = File(vaultPath)
        if (!vaultDir.exists() || !vaultDir.isDirectory) {
            return@withContext VaultState(emptyMap())
        }
        
        val fileHashes = mutableMapOf<String, String>()
        val vaultPathObj = Paths.get(vaultPath)
        
        vaultDir.walkTopDown()
            .filter { it.isFile && it.extension == "md" }
            .forEach { file ->
                try {
                    val relativePath = vaultPathObj.relativize(file.toPath()).toString().replace('\\', '/')
                    // Always compute hash for all files (hash-only tracking)
                    val hash = FileHashUtil.computeFileHash(file)
                    if (hash.isNotEmpty()) {
                        fileHashes[relativePath] = hash
                    }
                } catch (e: Exception) {
                    AppLogger.w("VaultSyncService", "Failed to get file info for ${file.path}: ${e.message}")
                }
            }
        
        return@withContext VaultState(fileHashes)
    }
    
    /**
     * Detects changes between current state and indexed state using hash-based comparison.
     * 
     * Uses hash-only tracking (no timestamps).
     * 
     * @param currentHashes Current file hashes (all files in vault)
     * @param indexedHashes Indexed file hashes
     * @return VaultChanges with detected changes
     */
    private fun detectChanges(
        currentHashes: Map<String, String>,
        indexedHashes: Map<String, String>
    ): VaultChanges {
        val newFiles = mutableListOf<String>()
        val modifiedFiles = mutableListOf<String>()
        val deletedFiles = mutableListOf<String>()
        
        // Check for new files (in current but not in indexed)
        for (filePath in currentHashes.keys) {
            if (!indexedHashes.containsKey(filePath)) {
                newFiles.add(filePath)
            }
        }
        
        // Check for deleted files (in indexed but not in current)
        for (filePath in indexedHashes.keys) {
            if (!currentHashes.containsKey(filePath)) {
                deletedFiles.add(filePath)
            }
        }
        
        // Check for modified files using hash-based comparison (source of truth)
        // Hash comparison is reliable - if hash matches, content hasn't changed
        for ((filePath, currentHash) in currentHashes) {
            val indexedHash = indexedHashes[filePath]
            if (indexedHash != null && currentHash != indexedHash) {
                // File exists in both, but hash differs - file is modified
                modifiedFiles.add(filePath)
            }
            // If hash matches or file is new (handled above), no action needed
        }
        
        return VaultChanges(
            newFiles = newFiles.distinct(),
            modifiedFiles = modifiedFiles.distinct(),
            deletedFiles = deletedFiles.distinct()
        )
    }
    
    /**
     * Gets list of modified files in the vault (using hash-based detection).
     * 
     * @param vaultPath Absolute path of the vault
     * @return List of file paths (relative to vault) that have been modified
     */
    suspend fun getModifiedFiles(vaultPath: String): List<String> = withContext(Dispatchers.IO) {
        val changes = detectChanges(vaultPath)
        return@withContext changes.modifiedFiles
    }
    
    /**
     * Gets list of new files in the vault.
     * 
     * @param vaultPath Absolute path of the vault
     * @return List of file paths (relative to vault) that are new
     */
    suspend fun getNewFiles(vaultPath: String): List<String> = withContext(Dispatchers.IO) {
        val changes = detectChanges(vaultPath)
        return@withContext changes.newFiles
    }
    
    /**
     * Gets list of deleted files (files that were indexed but no longer exist).
     * 
     * @param vaultPath Absolute path of the vault
     * @return List of file paths (relative to vault) that have been deleted
     */
    suspend fun getDeletedFiles(vaultPath: String): List<String> = withContext(Dispatchers.IO) {
        val changes = detectChanges(vaultPath)
        return@withContext changes.deletedFiles
    }
    
    /**
     * Gets all files that need to be re-indexed (modified + new).
     * 
     * @param vaultPath Absolute path of the vault
     * @return List of file paths (relative to vault) that need indexing
     */
    suspend fun getFilesToReindex(vaultPath: String): List<String> = withContext(Dispatchers.IO) {
        val changes = detectChanges(vaultPath)
        return@withContext (changes.modifiedFiles + changes.newFiles).distinct()
    }
}

