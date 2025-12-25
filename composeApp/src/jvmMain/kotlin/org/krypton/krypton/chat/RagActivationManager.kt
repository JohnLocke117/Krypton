package org.krypton.krypton.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.krypton.krypton.rag.*
import org.krypton.krypton.util.AppLogger

/**
 * Result of RAG activation attempt.
 */
enum class RagActivationResult {
    /**
     * RAG was successfully enabled.
     */
    ENABLED,
    
    /**
     * User cancelled the activation (e.g., cancelled ingestion prompt).
     */
    CANCELLED,
    
    /**
     * An error occurred during activation.
     */
    ERROR
}

/**
 * Manages RAG activation flow with health checks, sync status, and ingestion prompts.
 */
class RagActivationManager(
    private val healthService: ChromaDBHealthService,
    private val vaultSyncService: VaultSyncService,
    private val indexer: Indexer,
    private val vaultMetadataService: VaultMetadataService,
    private val vectorStore: VectorStore
) {
    /**
     * Activates RAG mode for a vault.
     * 
     * This method:
     * 1. Checks ChromaDB health
     * 2. Checks vault sync status
     * 3. Shows ingestion prompt if needed (NOT_INDEXED)
     * 4. Shows re-index prompt if OUT_OF_SYNC
     * 5. Performs ingestion/re-indexing if user confirms
     * 6. Enables RAG
     * 
     * @param vaultPath Absolute path of the current vault (folder)
     * @param onIngestionNeeded Callback to show ingestion prompt. Returns true if user wants to continue.
     * @param onReindexNeeded Callback to show re-index prompt. Returns true if user wants to continue.
     * @param onIngestionProgress Callback to show ingestion progress (filePath, progress)
     * @return RagActivationResult indicating the outcome
     */
    suspend fun activateRag(
        vaultPath: String?,
        onIngestionNeeded: suspend () -> Boolean,
        onReindexNeeded: suspend () -> Boolean,
        onIngestionProgress: suspend (String, Float) -> Unit
    ): RagActivationResult = withContext(Dispatchers.Default) {
        try {
            // Step 1: Health check
            val health = healthService.checkHealth()
            if (health == HealthStatus.UNHEALTHY) {
                AppLogger.e("RagActivationManager", "ChromaDB is unavailable")
                return@withContext RagActivationResult.ERROR
            }
            
            if (vaultPath == null) {
                AppLogger.w("RagActivationManager", "No vault path provided")
                return@withContext RagActivationResult.ERROR
            }
            
            // Step 2: Check if ChromaDB has existing data for this vault (cold start detection)
            AppLogger.i("RagActivationManager", "═══════════════════════════════════════════════════════════")
            AppLogger.i("RagActivationManager", "Checking for existing vault data")
            AppLogger.i("RagActivationManager", "Vault Path: $vaultPath")
            
            val hasExistingData = if (vectorStore is org.krypton.krypton.data.rag.impl.ChromaDBVectorStore) {
                val result = vectorStore.hasVaultData(vaultPath)
                AppLogger.i("RagActivationManager", "hasVaultData() returned: $result")
                result
            } else {
                // For non-ChromaDB stores, check metadata instead
                val metadata = vaultMetadataService.getVaultMetadata(vaultPath)
                val result = metadata != null && metadata.indexedFileHashes.isNotEmpty()
                AppLogger.i("RagActivationManager", "Metadata check returned: $result (metadata exists: ${metadata != null}, hashes: ${metadata?.indexedFileHashes?.size ?: 0})")
                result
            }
            
            AppLogger.i("RagActivationManager", "Has existing data: $hasExistingData")
            AppLogger.i("RagActivationManager", "═══════════════════════════════════════════════════════════")
            
            if (!hasExistingData) {
                // Cold start - no data in ChromaDB for this vault
                AppLogger.i("RagActivationManager", "❌ Cold start detected - no existing data for vault")
                val shouldIngest = onIngestionNeeded()
                if (!shouldIngest) {
                    return@withContext RagActivationResult.CANCELLED
                }
                
                // Perform full ingestion
                performIngestion(vaultPath, onIngestionProgress)
                return@withContext RagActivationResult.ENABLED
            }
            
            // Step 3: Existing data found - check sync status
            AppLogger.i("RagActivationManager", "✅ Existing data found - checking sync status")
            val syncStatus = vaultSyncService.checkSyncStatus(vaultPath)
            AppLogger.i("RagActivationManager", "Sync status: $syncStatus")
            
            when (syncStatus) {
                SyncStatus.UNAVAILABLE -> {
                    AppLogger.e("RagActivationManager", "ChromaDB is unavailable")
                    return@withContext RagActivationResult.ERROR
                }
                
                SyncStatus.NOT_INDEXED -> {
                    // Metadata missing but data exists - treat as OUT_OF_SYNC
                    AppLogger.w("RagActivationManager", "Data exists but metadata missing - treating as OUT_OF_SYNC")
                    val shouldReindex = onReindexNeeded()
                    if (!shouldReindex) {
                        return@withContext RagActivationResult.CANCELLED
                    }
                    performIncrementalIngestion(vaultPath, onIngestionProgress)
                }
                
                SyncStatus.OUT_OF_SYNC -> {
                    // Changes detected, show prompt for re-indexing
                    AppLogger.d("RagActivationManager", "Vault has changes, showing re-index prompt")
                    val shouldReindex = onReindexNeeded()
                    if (!shouldReindex) {
                        return@withContext RagActivationResult.CANCELLED
                    }
                    
                    // Perform incremental re-indexing
                    performIncrementalIngestion(vaultPath, onIngestionProgress)
                }
                
                SyncStatus.SYNCED -> {
                    // Already synced, nothing to do
                    AppLogger.d("RagActivationManager", "Vault is already synced")
                }
            }
            
            return@withContext RagActivationResult.ENABLED
        } catch (e: Exception) {
            AppLogger.e("RagActivationManager", "Failed to activate RAG: ${e.message}", e)
            return@withContext RagActivationResult.ERROR
        }
    }
    
    /**
     * Performs full ingestion of the vault.
     * This is a blocking operation - if tenant, database, or collection setup fails, the pipeline fails.
     */
    private suspend fun performIngestion(
        vaultPath: String,
        onProgress: suspend (String, Float) -> Unit
    ) = withContext(Dispatchers.Default) {
        try {
            // Log ingestion pipeline start
            AppLogger.i("IngestionPipeline", "═══════════════════════════════════════════════════════════")
            AppLogger.i("IngestionPipeline", "Starting Ingestion Pipeline")
            AppLogger.i("IngestionPipeline", "Vault Path: $vaultPath")
            AppLogger.i("IngestionPipeline", "═══════════════════════════════════════════════════════════")
            
            // Step 1: Ensure tenant and database exist (blocking)
            onProgress("Setting up ChromaDB tenant and database...", 0.0f)
            try {
                healthService.ensureTenantAndDatabase()
            } catch (e: Exception) {
                val errorMsg = "Failed to set up ChromaDB tenant/database: ${e.message}"
                AppLogger.e("IngestionPipeline", "✗ $errorMsg", e)
                // Log in red (error level)
                System.err.println("✗ Ingestion Pipeline Failed: $errorMsg")
                throw Exception(errorMsg, e)
            }
            
            // Step 2: Collection will be created automatically by ChromaDBVectorStore.ensureCollection()
            // when we start indexing, so we don't need to check it here
            
            // Set up callback to update metadata and report progress
            val originalCallback = indexer.onIndexingComplete
            var totalFiles = 0
            var processedFiles = 0
            
            indexer.onIndexingComplete = { path, files, hashes ->
                // Only use hashes (hash-only tracking, ignore files/timestamps)
                vaultMetadataService.updateVaultMetadata(path, hashes)
                originalCallback?.invoke(path, files, hashes)
            }
            
            // Step 3: Perform full reindex
            onProgress("Starting ingestion...", 0.1f)
            try {
                indexer.fullReindex(vaultPath)
            } catch (e: Exception) {
                val errorMsg = "Failed to index files: ${e.message}"
                AppLogger.e("IngestionPipeline", "✗ $errorMsg", e)
                // Log in red (error level)
                System.err.println("✗ Ingestion Pipeline Failed: $errorMsg")
                throw Exception(errorMsg, e)
            }
            
            // Restore original callback
            indexer.onIndexingComplete = originalCallback
            
            // Step 4: Wait for metadata to be persisted to ChromaDB
            // The onIndexingComplete callback updates metadata, but ChromaDB needs time to persist it
            onProgress("Finalizing metadata...", 0.95f)
            kotlinx.coroutines.delay(1000) // Wait 1 second for ChromaDB to persist metadata
            
            // Verify metadata was written by reading it back
            try {
                val writtenMetadata = vaultMetadataService.getVaultMetadata(vaultPath)
                if (writtenMetadata == null) {
                    AppLogger.w("IngestionPipeline", "Metadata not yet available after ingestion, but continuing...")
                } else {
                    AppLogger.d("IngestionPipeline", "Metadata verified: ${writtenMetadata.indexedFileHashes.size} files indexed")
                }
            } catch (e: Exception) {
                AppLogger.w("IngestionPipeline", "Could not verify metadata after ingestion: ${e.message}")
                // Don't fail the pipeline if verification fails - metadata might still be written
            }
            
            onProgress("Ingestion complete", 1.0f)
            
            // Log ingestion pipeline complete
            AppLogger.i("IngestionPipeline", "═══════════════════════════════════════════════════════════")
            AppLogger.i("IngestionPipeline", "✓ Ingestion Pipeline Complete")
            AppLogger.i("IngestionPipeline", "Vault Path: $vaultPath")
            AppLogger.i("IngestionPipeline", "═══════════════════════════════════════════════════════════")
        } catch (e: Exception) {
            val errorMsg = "Ingestion Pipeline Failed: ${e.message}"
            AppLogger.e("IngestionPipeline", "✗ $errorMsg", e)
            // Log in red (error level) to terminal
            System.err.println("✗ $errorMsg")
            throw e
        }
    }
    
    /**
     * Performs incremental ingestion of changed files (new, modified, and deleted).
     * This is a blocking operation - if tenant, database, or collection setup fails, the pipeline fails.
     */
    private suspend fun performIncrementalIngestion(
        vaultPath: String,
        onProgress: suspend (String, Float) -> Unit
    ) = withContext(Dispatchers.Default) {
        try {
            AppLogger.i("RagActivationManager", "Starting incremental ingestion for vault: $vaultPath")
            
            // Step 1: Ensure tenant and database exist (blocking)
            onProgress("Setting up ChromaDB tenant and database...", 0.0f)
            try {
                healthService.ensureTenantAndDatabase()
            } catch (e: Exception) {
                val errorMsg = "Failed to set up ChromaDB tenant/database: ${e.message}"
                AppLogger.e("IngestionPipeline", "✗ $errorMsg", e)
                // Log in red (error level)
                System.err.println("✗ Ingestion Pipeline Failed: $errorMsg")
                throw Exception(errorMsg, e)
            }
            
            // Detect changes
            val changes = vaultSyncService.detectChanges(vaultPath)
            AppLogger.d("RagActivationManager", "Detected changes: ${changes.newFiles.size} new, ${changes.modifiedFiles.size} modified, ${changes.deletedFiles.size} deleted")
            
            // Get existing metadata (hash-only)
            val existingMetadata = vaultMetadataService.getVaultMetadata(vaultPath)
            val existingHashes = existingMetadata?.indexedFileHashes?.toMutableMap() ?: mutableMapOf()
            
            // Delete vectors for deleted files
            if (changes.deletedFiles.isNotEmpty()) {
                onProgress("Deleting ${changes.deletedFiles.size} deleted files...", 0.0f)
                for (deletedFile in changes.deletedFiles) {
                    try {
                        // Use deleteByVaultAndFile if available, otherwise fall back to deleteByFilePath
                        if (vectorStore is org.krypton.krypton.data.rag.impl.ChromaDBVectorStore) {
                            vectorStore.deleteByVaultAndFile(vaultPath, deletedFile)
                        } else {
                            vectorStore.deleteByFilePath(deletedFile)
                        }
                        existingHashes.remove(deletedFile)
                    } catch (e: Exception) {
                        AppLogger.w("RagActivationManager", "Failed to delete chunks for deleted file $deletedFile: ${e.message}")
                    }
                }
            }
            
            // Index new and modified files
            val filesToIndex = (changes.newFiles + changes.modifiedFiles).distinct()
            if (filesToIndex.isNotEmpty()) {
                onProgress("Indexing ${filesToIndex.size} files...", 0.3f)
                
                // Set up callback to update metadata with hashes (hash-only, no timestamps)
                val originalCallback = indexer.onIndexingComplete
                indexer.onIndexingComplete = { path, files, hashes ->
                    // Only use hashes (hash-only tracking)
                    existingHashes.putAll(hashes)
                    vaultMetadataService.updateVaultMetadata(path, existingHashes)
                    originalCallback?.invoke(path, files, hashes)
                }
                
                // Index modified files
                indexer.indexModifiedFiles(filesToIndex, vaultPath)
                
                // Restore original callback
                indexer.onIndexingComplete = originalCallback
            }
            
            // Update metadata with final state (hash-only)
            vaultMetadataService.updateVaultMetadata(vaultPath, existingHashes)
            
            // Wait for metadata to be persisted to ChromaDB
            if (filesToIndex.isNotEmpty() || changes.deletedFiles.isNotEmpty()) {
                onProgress("Finalizing metadata...", 0.95f)
                kotlinx.coroutines.delay(1000) // Wait 1 second for ChromaDB to persist metadata
            }
            
            onProgress("Incremental ingestion complete", 1.0f)
            AppLogger.i("RagActivationManager", "Incremental ingestion completed: ${filesToIndex.size} files indexed, ${changes.deletedFiles.size} files deleted")
        } catch (e: Exception) {
            val errorMsg = "Incremental ingestion failed: ${e.message}"
            AppLogger.e("IngestionPipeline", "✗ $errorMsg", e)
            // Log in red (error level) to terminal
            System.err.println("✗ Ingestion Pipeline Failed: $errorMsg")
            throw e
        }
    }
    
    /**
     * Forces a complete re-indexing of the vault.
     * Clears all existing vectors and metadata, then performs full re-indexing.
     * 
     * @param vaultPath Absolute path of the vault
     * @param onProgress Callback to show re-indexing progress
     */
    suspend fun forceReindex(
        vaultPath: String,
        onProgress: suspend (String, Float) -> Unit
    ) = withContext(Dispatchers.Default) {
        try {
            AppLogger.i("RagActivationManager", "Starting force re-index for vault: $vaultPath")
            
            // Step 1: Ensure tenant and database exist (blocking)
            onProgress("Setting up ChromaDB tenant and database...", 0.0f)
            try {
                healthService.ensureTenantAndDatabase()
            } catch (e: Exception) {
                val errorMsg = "Failed to set up ChromaDB tenant/database: ${e.message}"
                AppLogger.e("IngestionPipeline", "✗ $errorMsg", e)
                // Log in red (error level)
                System.err.println("✗ Ingestion Pipeline Failed: $errorMsg")
                throw Exception(errorMsg, e)
            }
            
            onProgress("Clearing existing data...", 0.1f)
            
            // Clear all vectors for this vault
            if (vectorStore is org.krypton.krypton.data.rag.impl.ChromaDBVectorStore) {
                vectorStore.clearVault(vaultPath)
            } else {
                // Fallback: clear all if we can't clear by vault
                vectorStore.clear()
            }
            
            // Clear metadata
            vaultMetadataService.clearVaultMetadata(vaultPath)
            
            onProgress("Re-indexing all files...", 0.2f)
            
            // Perform full re-indexing (this will also ensure tenant/database, but we already did it)
            performIngestion(vaultPath, onProgress)
            
            AppLogger.i("RagActivationManager", "Force re-index completed for vault: $vaultPath")
        } catch (e: Exception) {
            val errorMsg = "Force re-index failed: ${e.message}"
            AppLogger.e("IngestionPipeline", "✗ $errorMsg", e)
            // Log in red (error level) to terminal
            System.err.println("✗ Ingestion Pipeline Failed: $errorMsg")
            throw e
        }
    }
}

