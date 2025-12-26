package org.krypton.rag

import kotlinx.serialization.Serializable

/**
 * Metadata about an indexed vault.
 * 
 * Uses hash-only tracking (no timestamps) for reliable change detection.
 * 
 * @param vaultPath Absolute path of the indexed vault
 * @param lastIndexedTime Timestamp of last full index (milliseconds since epoch)
 * @param indexedFileHashes Map of file paths (relative to vault) to their content hashes (SHA-256)
 */
@Serializable
data class VaultMetadata(
    val vaultPath: String,
    val lastIndexedTime: Long,
    val indexedFileHashes: Map<String, String> = emptyMap() // filePath -> "sha256:<hex>"
)

