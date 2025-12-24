package org.krypton.krypton.rag

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.krypton.krypton.util.AppLogger
import java.nio.file.Paths

/**
 * Service for managing vault metadata in ChromaDB.
 * 
 * Stores metadata about indexed vaults in a separate collection.
 */
class VaultMetadataService(
    private val baseUrl: String,
    private val metadataCollectionName: String = "vault_metadata",
    private val httpClientEngine: HttpClientEngine,
    private val tenant: String = "default",
    private val database: String = "defaultDB"
) {
    
    private val collectionsBasePath = "$baseUrl/api/v2/tenants/$tenant/databases/$database/collections"
    private val client = HttpClient(httpClientEngine) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
            })
        }
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    // Cache collection ID to avoid repeated lookups
    private var collectionId: String? = null
    
    /**
     * Gets collection ID from collection name.
     */
    private suspend fun getCollectionIdFromName(collectionName: String): String? = withContext(Dispatchers.IO) {
        try {
            val response = client.get("$collectionsBasePath/$collectionName")
            if (response.status == HttpStatusCode.OK) {
                val collectionResponse: CollectionResponse = response.body()
                return@withContext collectionResponse.id
            }
            return@withContext null
        } catch (e: Exception) {
            return@withContext null
        }
    }
    
    /**
     * Ensures the metadata collection exists and caches the collection ID.
     */
    private suspend fun ensureMetadataCollection() = withContext(Dispatchers.IO) {
        // If we already have the collection ID cached, use it
        if (collectionId != null) {
            return@withContext
        }
        
        try {
            // Try to get the collection ID from name
            val id = getCollectionIdFromName(metadataCollectionName)
            if (id != null) {
                collectionId = id
                return@withContext
            }
            
            // Collection doesn't exist, create it
            createMetadataCollection()
        } catch (e: Exception) {
            try {
                createMetadataCollection()
            } catch (createException: Exception) {
                AppLogger.w("VaultMetadataService", "Failed to ensure metadata collection: ${createException.message}")
            }
        }
    }
    
    /**
     * Creates the metadata collection and caches the collection ID.
     */
    private suspend fun createMetadataCollection() = withContext(Dispatchers.IO) {
        val request = CreateCollectionRequest(
            name = metadataCollectionName,
            get_or_create = true,
            metadata = null,
            configuration = null,
            schema = null
        )
        
        val response = client.post(collectionsBasePath) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        
        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
            AppLogger.w("VaultMetadataService", "Failed to create metadata collection: ${response.status}")
        } else {
            // Parse and cache collection ID from response
            try {
                val collectionResponse: CollectionResponse = response.body()
                collectionId = collectionResponse.id
            } catch (e: Exception) {
                AppLogger.w("VaultMetadataService", "Failed to parse collection ID from response: ${e.message}")
            }
        }
    }
    
    /**
     * Gets metadata for a vault.
     * 
     * @param vaultPath Absolute path of the vault
     * @return VaultMetadata if found, null otherwise
     */
    suspend fun getVaultMetadata(vaultPath: String): VaultMetadata? = withContext(Dispatchers.IO) {
        ensureMetadataCollection()
        try {
            val collectionIdToUse = collectionId
                ?: return@withContext null
            
            val normalizedPath = normalizePath(vaultPath)
            val response = client.get("$collectionsBasePath/$collectionIdToUse/get") {
                contentType(ContentType.Application.Json)
                parameter("ids", listOf(normalizedPath))
                parameter("include", listOf("documents", "metadatas"))
            }
            
            if (response.status != HttpStatusCode.OK) {
                return@withContext null
            }
            
            val getResponse: GetResponse = response.body()
            if (getResponse.ids?.isNotEmpty() == true && getResponse.documents?.isNotEmpty() == true) {
                val document = getResponse.documents[0][0]
                return@withContext json.decodeFromString<VaultMetadata>(document)
            }
            
            return@withContext null
        } catch (e: Exception) {
            AppLogger.e("VaultMetadataService", "Failed to get vault metadata for $vaultPath: ${e.message}", e)
            return@withContext null
        }
    }
    
    /**
     * Updates or creates metadata for a vault.
     * 
     * @param vaultPath Absolute path of the vault
     * @param indexedFiles Map of file paths to their last modified timestamps
     */
    suspend fun updateVaultMetadata(vaultPath: String, indexedFiles: Map<String, Long>) = withContext(Dispatchers.IO) {
        ensureMetadataCollection()
        try {
            val normalizedPath = normalizePath(vaultPath)
            val metadata = VaultMetadata(
                vaultPath = vaultPath,
                lastIndexedTime = System.currentTimeMillis(),
                indexedFiles = indexedFiles
            )
            
            val metadataJson = json.encodeToString(VaultMetadata.serializer(), metadata)
            
            val collectionIdToUse = collectionId
                ?: throw Exception("Collection ID not available for update")
            
            // ChromaDB v2 requires embeddings to have at least 1 dimension
            // Use a minimal dummy embedding for metadata-only entries
            val request = AddRequest(
                ids = listOf(normalizedPath),
                embeddings = listOf(listOf(0.0f)), // Minimal valid embedding (1 dimension)
                documents = listOf(metadataJson),
                metadatas = listOf(mapOf("vaultPath" to vaultPath)),
                uris = null
            )
            
            val response = client.post("$collectionsBasePath/$collectionIdToUse/add") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            // ChromaDB v2 API returns 201 Created for successful add operations
            if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
                val errorBody = response.body<String>()
                throw Exception("Failed to update vault metadata: ${response.status} - $errorBody")
            }
            
            AppLogger.d("VaultMetadataService", "Updated metadata for vault: $vaultPath")
        } catch (e: Exception) {
            AppLogger.e("VaultMetadataService", "Failed to update vault metadata for $vaultPath: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Gets the list of indexed files for a vault.
     * 
     * @param vaultPath Absolute path of the vault
     * @return Map of file paths to their last modified timestamps
     */
    suspend fun getIndexedFiles(vaultPath: String): Map<String, Long> = withContext(Dispatchers.IO) {
        val metadata = getVaultMetadata(vaultPath)
        return@withContext metadata?.indexedFiles ?: emptyMap()
    }
    
    /**
     * Clears metadata for a vault.
     * 
     * @param vaultPath Absolute path of the vault
     */
    suspend fun clearVaultMetadata(vaultPath: String) = withContext(Dispatchers.IO) {
        try {
            ensureMetadataCollection()
            val collectionIdToUse = collectionId
                ?: return@withContext
            
            val normalizedPath = normalizePath(vaultPath)
            val request = DeleteRequest(
                ids = listOf(normalizedPath),
                where = null,
                where_document = null
            )
            
            val response = client.post("$collectionsBasePath/$collectionIdToUse/delete") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (response.status != HttpStatusCode.OK) {
                AppLogger.w("VaultMetadataService", "Failed to clear vault metadata: ${response.status}")
            } else {
                AppLogger.d("VaultMetadataService", "Cleared metadata for vault: $vaultPath")
            }
        } catch (e: Exception) {
            AppLogger.e("VaultMetadataService", "Failed to clear vault metadata for $vaultPath: ${e.message}", e)
        }
    }
    
    /**
     * Normalizes a path for use as an ID in ChromaDB.
     */
    private fun normalizePath(path: String): String {
        return try {
            Paths.get(path).normalize().toString().replace('\\', '/').replace(":", "_")
        } catch (e: Exception) {
            path.replace('\\', '/').replace(":", "_")
        }
    }
    
    /**
     * Closes the HTTP client.
     */
    fun close() {
        client.close()
    }
}

// ChromaDB API models for metadata collection

@Serializable
private data class CreateCollectionRequest(
    val name: String,
    val get_or_create: Boolean = false,
    val metadata: Map<String, String>? = null,
    val configuration: Map<String, String>? = null,
    val schema: Map<String, String>? = null
)

@Serializable
private data class CollectionResponse(
    val id: String,
    val name: String,
    val metadata: Map<String, String>? = null
)

@Serializable
private data class AddRequest(
    val ids: List<String>,
    val embeddings: List<List<Float>>,
    val documents: List<String>,
    val metadatas: List<Map<String, String>>,
    val uris: List<String>? = null
)

@Serializable
private data class GetResponse(
    val ids: List<List<String>>? = null,
    val documents: List<List<String>>? = null,
    val metadatas: List<List<Map<String, String>>>? = null
)

@Serializable
private data class DeleteRequest(
    val ids: List<String>? = null,
    @Contextual val where: JsonObject? = null,
    @Contextual val where_document: JsonObject? = null
)

