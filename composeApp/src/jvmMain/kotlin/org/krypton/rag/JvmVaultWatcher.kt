package org.krypton.rag

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.krypton.util.AppLogger
import java.io.File
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.*

/**
 * JVM implementation of VaultWatcher using Java WatchService.
 */
class JvmVaultWatcher : VaultWatcher {
    private var watchService: WatchService? = null
    private var watchKey: WatchKey? = null
    private var vaultPath: Path? = null
    @Volatile
    private var isStopped: Boolean = false
    
    override fun watch(vaultPath: String): Flow<FileSystemEvent> = callbackFlow {
        val vaultPathObj = Paths.get(vaultPath)
        this@JvmVaultWatcher.vaultPath = vaultPathObj
        
        if (!Files.exists(vaultPathObj) || !Files.isDirectory(vaultPathObj)) {
            AppLogger.w("JvmVaultWatcher", "Vault path does not exist or is not a directory: $vaultPath")
            close()
            return@callbackFlow
        }
        
        val watchServiceToUse = try {
            FileSystems.getDefault().newWatchService()
        } catch (e: Exception) {
            AppLogger.e("JvmVaultWatcher", "Failed to create watch service: ${e.message}", e)
            close()
            return@callbackFlow
        }
        
        watchService = watchServiceToUse
        
        val watchKeyToUse = try {
            vaultPathObj.register(
                watchServiceToUse,
                ENTRY_CREATE,
                ENTRY_MODIFY,
                ENTRY_DELETE
            )
        } catch (e: Exception) {
            AppLogger.e("JvmVaultWatcher", "Failed to register watch key: ${e.message}", e)
            watchServiceToUse.close()
            close()
            return@callbackFlow
        }
        
        watchKey = watchKeyToUse
        isStopped = false // Reset flag when starting to watch
        AppLogger.d("JvmVaultWatcher", "Started watching vault: $vaultPath")
        
        // Process events in IO dispatcher using coroutineScope
        coroutineScope {
            launch(Dispatchers.IO) {
                try {
                    while (!isStopped) {
                        val key = try {
                            watchServiceToUse.take()
                        } catch (e: ClosedWatchServiceException) {
                            // Watch service was closed, exit gracefully
                            AppLogger.d("JvmVaultWatcher", "Watch service closed, stopping watch loop")
                            break
                        } ?: break
                        
                        if (key != watchKeyToUse) {
                            key.reset()
                            continue
                        }
                        
                        for (event in key.pollEvents()) {
                            val kind = event.kind()
                            val context = event.context()
                            
                            if (context is Path) {
                                try {
                                    val fullPath = vaultPathObj.resolve(context)
                                    val relativePath = vaultPathObj.relativize(fullPath).toString().replace('\\', '/')
                                    
                                    // Only watch .md files (ignore directory events for now)
                                    if (relativePath.endsWith(".md") && Files.isRegularFile(fullPath)) {
                                        val eventType = when (kind) {
                                            ENTRY_CREATE -> FileSystemEventType.CREATE
                                            ENTRY_MODIFY -> FileSystemEventType.MODIFY
                                            ENTRY_DELETE -> FileSystemEventType.DELETE
                                            else -> null
                                        }
                                        
                                        if (eventType != null) {
                                            trySend(FileSystemEvent(eventType, relativePath))
                                        }
                                    }
                                } catch (e: Exception) {
                                    AppLogger.w("JvmVaultWatcher", "Error processing file event: ${e.message}")
                                }
                            }
                        }
                        
                        if (!key.reset()) {
                            AppLogger.d("JvmVaultWatcher", "Watch key no longer valid")
                            break
                        }
                    }
                } catch (e: ClosedWatchServiceException) {
                    // Watch service was closed externally, this is expected
                    AppLogger.d("JvmVaultWatcher", "Watch service closed during operation")
                } catch (e: Exception) {
                    AppLogger.e("JvmVaultWatcher", "Error in watch loop: ${e.message}", e)
                } finally {
                    close()
                }
            }
        }
        
        awaitClose {
            try {
                watchKeyToUse.cancel()
                watchServiceToUse.close()
                watchKey = null
                watchService = null
                AppLogger.d("JvmVaultWatcher", "Stopped watching vault")
            } catch (e: Exception) {
                AppLogger.w("JvmVaultWatcher", "Error stopping watcher: ${e.message}")
            }
        }
    }
    
    override suspend fun stop() = withContext(Dispatchers.IO) {
        try {
            // Set flag first to signal the watch loop to stop
            isStopped = true
            watchKey?.cancel()
            watchKey = null
            watchService?.close()
            watchService = null
            AppLogger.d("JvmVaultWatcher", "Stopped watching vault")
        } catch (e: Exception) {
            AppLogger.w("JvmVaultWatcher", "Error stopping watcher: ${e.message}")
        }
    }
}

