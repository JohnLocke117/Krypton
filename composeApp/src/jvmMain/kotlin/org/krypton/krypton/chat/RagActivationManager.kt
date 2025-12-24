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
    private val vaultMetadataService: VaultMetadataService
) {
    /**
     * Activates RAG mode for a vault.
     * 
     * This method:
     * 1. Checks ChromaDB health
     * 2. Checks vault sync status
     * 3. Shows ingestion prompt if needed
     * 4. Auto re-ingests modified files if changes detected
     * 5. Enables RAG
     * 
     * @param vaultPath Absolute path of the current vault (folder)
     * @param onIngestionNeeded Callback to show ingestion prompt. Returns true if user wants to continue.
     * @param onIngestionProgress Callback to show ingestion progress (filePath, progress)
     * @return RagActivationResult indicating the outcome
     */
    suspend fun activateRag(
        vaultPath: String?,
        onIngestionNeeded: suspend () -> Boolean,
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
            
            // Step 2: Check sync status
            val syncStatus = vaultSyncService.checkSyncStatus(vaultPath)
            
            when (syncStatus) {
                SyncStatus.UNAVAILABLE -> {
                    AppLogger.e("RagActivationManager", "ChromaDB is unavailable")
                    return@withContext RagActivationResult.ERROR
                }
                
                SyncStatus.NOT_INDEXED -> {
                    // Step 3: Show ingestion prompt
                    val shouldIngest = onIngestionNeeded()
                    if (!shouldIngest) {
                        return@withContext RagActivationResult.CANCELLED
                    }
                    
                    // Step 4: Perform full ingestion
                    performIngestion(vaultPath, onIngestionProgress)
                }
                
                SyncStatus.OUT_OF_SYNC -> {
                    // Changes detected, but don't auto re-ingest
                    // User can manually trigger re-ingestion via the re-ingest icon
                    AppLogger.d("RagActivationManager", "Vault has changes, but not auto re-ingesting. User can manually trigger re-ingestion.")
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
            
            // Set up callback to update metadata and report progress
            val originalCallback = indexer.onIndexingComplete
            var totalFiles = 0
            var processedFiles = 0
            
            indexer.onIndexingComplete = { path, files ->
                vaultMetadataService.updateVaultMetadata(path, files)
                originalCallback?.invoke(path, files)
            }
            
            // We need to get the file count first to report progress accurately
            // For now, we'll report progress as we go
            // Perform full reindex
            onProgress("Starting ingestion...", 0.0f)
            indexer.fullReindex(vaultPath)
            onProgress("Ingestion complete", 1.0f)
            
            // Restore original callback
            indexer.onIndexingComplete = originalCallback
            
            // Log ingestion pipeline complete
            AppLogger.i("IngestionPipeline", "═══════════════════════════════════════════════════════════")
            AppLogger.i("IngestionPipeline", "Ingestion Pipeline Complete")
            AppLogger.i("IngestionPipeline", "Vault Path: $vaultPath")
            AppLogger.i("IngestionPipeline", "═══════════════════════════════════════════════════════════")
        } catch (e: Exception) {
            AppLogger.e("IngestionPipeline", "Ingestion Pipeline Failed: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Performs incremental ingestion of modified files.
     */
    private suspend fun performIncrementalIngestion(
        vaultPath: String,
        filePaths: List<String>,
        onProgress: suspend (String, Float) -> Unit
    ) = withContext(Dispatchers.Default) {
        try {
            // Get existing metadata
            val existingMetadata = vaultMetadataService.getIndexedFiles(vaultPath).toMutableMap()
            
            // Set up callback to update metadata
            val originalCallback = indexer.onIndexingComplete
            indexer.onIndexingComplete = { path, files ->
                existingMetadata.putAll(files)
                vaultMetadataService.updateVaultMetadata(path, existingMetadata)
                originalCallback?.invoke(path, existingMetadata)
            }
            
            // Index modified files
            indexer.indexModifiedFiles(filePaths, vaultPath)
            
            // Restore original callback
            indexer.onIndexingComplete = originalCallback
            
            AppLogger.d("RagActivationManager", "Incremental ingestion completed for ${filePaths.size} files")
        } catch (e: Exception) {
            AppLogger.e("RagActivationManager", "Incremental ingestion failed: ${e.message}", e)
            throw e
        }
    }
}

