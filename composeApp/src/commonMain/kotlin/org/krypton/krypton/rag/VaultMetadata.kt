package org.krypton.krypton.rag

import kotlinx.serialization.Serializable

/**
 * Metadata about an indexed vault.
 * 
 * @param vaultPath Absolute path of the indexed vault
 * @param lastIndexedTime Timestamp of last full index (milliseconds since epoch)
 * @param indexedFiles Map of file paths (relative to vault) to their last modified timestamps
 */
@Serializable
data class VaultMetadata(
    val vaultPath: String,
    val lastIndexedTime: Long,
    val indexedFiles: Map<String, Long> // filePath -> lastModified timestamp
)

