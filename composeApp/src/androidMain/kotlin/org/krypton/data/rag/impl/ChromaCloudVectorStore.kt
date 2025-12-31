package org.krypton.data.rag.impl

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.krypton.rag.RagChunk
import org.krypton.rag.SearchResult
import org.krypton.rag.VectorStore
import org.krypton.rag.models.Embedding
import org.krypton.util.AppLogger

/**
 * ChromaDB Cloud vector store implementation using HTTP API.
 * 
 * This implementation:
 * 1. Connects to ChromaDB Cloud (api.trychroma.com)
 * 2. Uses API key authentication via X-Chroma-Token header
 * 3. Stores embeddings, documents, and metadata in ChromaDB collections
 * 4. Uses ChromaDB's built-in cosine similarity search
 * 
 * @param baseUrl Base URL of ChromaDB Cloud server (e.g., "https://api.trychroma.com")
 * @param collectionName Name of the ChromaDB collection to use
 * @param httpClientEngine HTTP client engine for making requests
 * @param apiKey ChromaDB Cloud API key for authentication
 * @param tenant Tenant name (optional, defaults to "default")
 * @param database Database name (optional, defaults to "defaultDB")
 */
class ChromaCloudVectorStore(
    private val baseUrl: String,
    private val collectionName: String,
    private val httpClientEngine: HttpClientEngine,
    private val apiKey: String,
    private val tenant: String = "default",
    private val database: String = "defaultDB"
) : VectorStore {
    
    private val collectionsBasePath = "$baseUrl/api/v2/tenants/$tenant/databases/$database/collections"
    
    private val client = HttpClient(httpClientEngine) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
            })
        }
    }
    
    // Helper to add auth header to requests
    private fun HttpRequestBuilder.addAuthHeader() {
        header("X-Chroma-Token", apiKey)
    }
    
    // Cache collection ID to avoid repeated lookups
    private var collectionId: String? = null
    
    // Cache embedding dimension to avoid repeated queries
    private var embeddingDimension: Int? = null
    
    /**
     * Gets collection ID from collection name.
     */
    private suspend fun getCollectionIdFromName(collectionName: String): String? = withContext(Dispatchers.IO) {
        try {
            val response = client.get("$collectionsBasePath/$collectionName") {
                addAuthHeader()
            }
            if (response.status == HttpStatusCode.OK) {
                val collectionResponse: CollectionResponse = response.body()
                AppLogger.d("ChromaCloudVectorStore", "Retrieved collection ID for '$collectionName': ${collectionResponse.id}")
                return@withContext collectionResponse.id
            } else if (response.status == HttpStatusCode.NotFound) {
                AppLogger.d("ChromaCloudVectorStore", "Collection '$collectionName' not found (404)")
                return@withContext null
            } else {
                AppLogger.w("ChromaCloudVectorStore", "Unexpected status when getting collection '$collectionName': ${response.status}")
                return@withContext null
            }
        } catch (e: Exception) {
            AppLogger.w("ChromaCloudVectorStore", "Failed to get collection ID for '$collectionName': ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * Ensures the collection exists (query-only mode on Android).
     * Only checks if collection exists, does not create it.
     * Collections must be created on Desktop first.
     */
    private suspend fun ensureCollection() = withContext(Dispatchers.IO) {
        try {
            // Only check if collection exists, don't create
            if (collectionId == null) {
                val id = getCollectionIdFromName(collectionName)
                if (id != null) {
                    collectionId = id
                    AppLogger.d("ChromaCloudVectorStore", "Found existing collection '$collectionName' with ID: $id")
                } else {
                    // Collection doesn't exist - Android cannot create it
                    throw ChromaDBException("Collection '$collectionName' does not exist. Please create it on Desktop first.")
                }
            }
        } catch (e: ChromaDBException) {
            // Re-throw ChromaDBException as-is
            throw e
        } catch (e: Exception) {
            // Collection lookup failed
            AppLogger.e("ChromaCloudVectorStore", "Failed to lookup collection '$collectionName': ${e.message}", e)
            throw ChromaDBException("Failed to connect to ChromaDB Cloud at $baseUrl: ${e.message}. Collection '$collectionName' may not exist.", e)
        }
    }
    
    /**
     * Creates a new collection in ChromaDB Cloud and caches the collection ID.
     * 
     * @param forceCreate If true, uses get_or_create=false to force creation of a new collection.
     *                    If false, uses get_or_create=true to get existing or create new.
     */
    private suspend fun createCollection(forceCreate: Boolean = false) = withContext(Dispatchers.IO) {
        val request = CreateCollectionRequest(
            name = collectionName,
            get_or_create = !forceCreate,
            metadata = null,
            configuration = null,
            schema = null
        )
        
        val response = client.post(collectionsBasePath) {
            contentType(ContentType.Application.Json)
            addAuthHeader()
            setBody(request)
        }
        
        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
            val errorBody = response.body<String>()
            throw ChromaDBException("Failed to create collection: ${response.status} - $errorBody")
        }
        
        // Parse collection ID from response
        val collectionResponse: CollectionResponse = response.body()
        val newCollectionId = collectionResponse.id
        
        collectionId = newCollectionId
        
        AppLogger.i("ChromaCloudVectorStore", "Collection Created/Retrieved Successfully")
        AppLogger.i("ChromaCloudVectorStore", "  Collection Name: $collectionName")
        AppLogger.i("ChromaCloudVectorStore", "  Collection ID: $newCollectionId")
        AppLogger.d("ChromaCloudVectorStore", "Collection ID cached for future operations")
    }
    
    override suspend fun upsert(chunks: List<RagChunk>) = withContext(Dispatchers.IO) {
        // Android is query-only - indexing must be done on Desktop
        AppLogger.w("ChromaCloudVectorStore", "Upsert not supported on Android (query-only mode). Collection must be indexed on Desktop. Attempted to upsert ${chunks.size} chunks.")
        // No-op: Android doesn't index
    }
    
    override suspend fun search(
        queryEmbedding: Embedding,
        topK: Int,
        filters: Map<String, String>
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            ensureCollection()
            
            val collectionIdToUse = collectionId
                ?: throw ChromaDBException("Collection ID not available for search")
            
            // Log query execution
            AppLogger.d("ChromaCloudVectorStore", "Running Query on Collection")
            AppLogger.d("ChromaCloudVectorStore", "  Collection Name: $collectionName")
            AppLogger.d("ChromaCloudVectorStore", "  Collection ID: $collectionIdToUse")
            AppLogger.d("ChromaCloudVectorStore", "  Top K: $topK")
            AppLogger.d("ChromaCloudVectorStore", "  Query Embedding Dimension: ${queryEmbedding.vector.size}")
            
            // Build where clause from filters
            val whereClause = if (filters.isNotEmpty()) {
                buildJsonObject {
                    filters.forEach { (key, value) ->
                        put(key, JsonPrimitive(value))
                    }
                }
            } else {
                null
            }
            
            val request = QueryRequest(
                query_embeddings = listOf(queryEmbedding.vector),
                n_results = topK,
                where = whereClause,
                where_document = null,
                ids = null,
                include = listOf("documents", "metadatas", "distances")
            )
            
            val response = client.post("$collectionsBasePath/$collectionIdToUse/query") {
                contentType(ContentType.Application.Json)
                addAuthHeader()
                setBody(request)
            }
            
            if (response.status != HttpStatusCode.OK) {
                val errorBody = response.body<String>()
                val errorMsg = "Failed to search: ${response.status} - $errorBody"
                
                // Check for dimension mismatch error and provide helpful message
                if (errorBody.contains("dimension") && errorBody.contains("expecting")) {
                    AppLogger.e("ChromaCloudVectorStore", "Dimension mismatch detected. Collection expects a different embedding dimension than what Gemini returned.", null)
                    AppLogger.e("ChromaCloudVectorStore", "Solution: Recreate the collection on Desktop using Gemini embeddings (3072 dimensions) or use a different embedding model that matches the collection dimension.", null)
                }
                
                throw ChromaDBException(errorMsg)
            }
            
            val queryResponse: QueryResponse = response.body()
            
            // Convert ChromaDB response to SearchResult list with similarity scores
            val results = mutableListOf<SearchResult>()
            
            if (queryResponse.ids != null && queryResponse.ids.isNotEmpty()) {
                val ids = queryResponse.ids[0] // First query embedding results
                val documents = queryResponse.documents?.get(0) ?: emptyList()
                val metadatas = queryResponse.metadatas?.get(0) ?: emptyList()
                val distances = queryResponse.distances?.get(0) ?: emptyList()
                
                for (i in ids.indices) {
                    val id = ids[i]
                    val document = documents.getOrNull(i) ?: ""
                    val metadataJson = metadatas.getOrNull(i) ?: buildJsonObject { }
                    val distance = distances.getOrNull(i) ?: 1.0f
                    
                    // Convert ChromaDB cosine distance to similarity (1.0 - distance)
                    // ChromaDB returns cosine distance (0 = identical, 2 = opposite)
                    // For cosine similarity: similarity = 1.0 - distance
                    val similarity = (1.0 - distance.toDouble()).coerceIn(0.0, 1.0)
                    
                    // Convert metadata JsonObject to Map<String, String>
                    val metadata = buildMap<String, String> {
                        metadataJson.forEach { (key, value) ->
                            if (value is JsonPrimitive && value.isString) {
                                put(key, value.content)
                            }
                        }
                    }
                    
                    // Create RagChunk from ChromaDB response
                    // Note: ChromaDB doesn't return embeddings in query results by default
                    val chunk = RagChunk(
                        id = id,
                        text = document,
                        metadata = metadata,
                        embedding = null // ChromaDB doesn't return embeddings in query results
                    )
                    results.add(SearchResult(chunk = chunk, similarity = similarity))
                }
            }
            
            AppLogger.d("ChromaCloudVectorStore", "Query Completed Successfully")
            AppLogger.d("ChromaCloudVectorStore", "  Results Returned: ${results.size} chunks")
            return@withContext results
        } catch (e: Exception) {
            AppLogger.e("ChromaCloudVectorStore", "Failed to search (query size: ${queryEmbedding.vector.size}, topK: $topK)", e)
            // Return empty list on error rather than crashing
            return@withContext emptyList()
        }
    }
    
    override suspend fun clear() = withContext(Dispatchers.IO) {
        // Android is query-only - collection management must be done on Desktop
        AppLogger.w("ChromaCloudVectorStore", "Clear not supported on Android (query-only mode). Collection management must be done on Desktop.")
        // No-op: Android doesn't manage collections
    }
    
    /**
     * Deletes the collection (without recreating it).
     * Used when we need to recreate the collection with a different embedding dimension.
     * 
     * If the collection doesn't exist (404), treats it as already deleted and returns successfully.
     */
    private suspend fun deleteCollection() = withContext(Dispatchers.IO) {
        try {
            val collectionIdToUse = collectionId
                ?: getCollectionIdFromName(collectionName)
                ?: run {
                    AppLogger.w("ChromaCloudVectorStore", "Cannot delete collection: ID not available, assuming already deleted")
                    return@withContext // Collection doesn't exist, treat as success
                }
            
            val deleteRequest = DeleteCollectionRequest(
                new_name = null,
                new_metadata = null,
                new_configuration = null
            )
            
            val deleteResponse = client.delete("$collectionsBasePath/$collectionIdToUse") {
                contentType(ContentType.Application.Json)
                addAuthHeader()
                setBody(deleteRequest)
            }
            
            if (deleteResponse.status == HttpStatusCode.OK || deleteResponse.status == HttpStatusCode.NoContent) {
                AppLogger.i("ChromaCloudVectorStore", "Deleted collection '$collectionName'")
            } else if (deleteResponse.status == HttpStatusCode.NotFound) {
                AppLogger.d("ChromaCloudVectorStore", "Collection '$collectionName' does not exist (404), treating as already deleted")
            } else {
                val errorBody = deleteResponse.body<String>()
                AppLogger.w("ChromaCloudVectorStore", "Failed to delete collection: ${deleteResponse.status} - $errorBody")
                // Don't throw - treat as already deleted
            }
        } catch (e: ChromaDBException) {
            // Re-throw ChromaDBException as-is
            throw e
        } catch (e: Exception) {
            // For other exceptions (e.g., network errors), check if it's a 404
            val errorMsg = e.message?.lowercase() ?: ""
            if (errorMsg.contains("404") || errorMsg.contains("not found")) {
                AppLogger.d("ChromaCloudVectorStore", "Collection deletion returned 404, treating as already deleted")
                return@withContext // Treat as success
            }
            AppLogger.e("ChromaCloudVectorStore", "Error deleting collection: ${e.message}", e)
            throw ChromaDBException("Failed to delete collection: ${e.message}", e)
        }
    }
    
    override suspend fun deleteByFilePath(filePath: String) = withContext(Dispatchers.IO) {
        // Android is query-only - collection management must be done on Desktop
        AppLogger.w("ChromaCloudVectorStore", "DeleteByFilePath not supported on Android (query-only mode). Collection management must be done on Desktop. Attempted to delete: $filePath")
        // No-op: Android doesn't manage collections
    }
    
    /**
     * Deletes all chunks for a specific file path within a vault.
     * 
     * @param vaultPath Absolute path of the vault
     * @param filePath Relative file path within the vault
     */
    suspend fun deleteByFilePath(vaultPath: String, filePath: String) = withContext(Dispatchers.IO) {
        try {
            ensureCollection()
            
            var idToUse = collectionId
            if (idToUse == null) {
                try {
                    val getResponse = client.get("$collectionsBasePath/$collectionName") {
                        addAuthHeader()
                    }
                    if (getResponse.status == HttpStatusCode.OK) {
                        val collectionResponse: CollectionResponse = getResponse.body()
                        idToUse = collectionResponse.id
                        collectionId = idToUse
                    }
                } catch (e: Exception) {
                    AppLogger.w("ChromaCloudVectorStore", "Could not get collection ID for delete: ${e.message}")
                }
            }
            
            if (idToUse == null) {
                AppLogger.w("ChromaCloudVectorStore", "Skipping delete for $filePath in vault $vaultPath - collection ID not available")
                return@withContext
            }
            
            // Create JsonObject for where clause with both vault_path and file_path
            val whereJson = buildJsonObject {
                put("vault_path", JsonPrimitive(vaultPath))
                put("file_path", JsonPrimitive(filePath))
            }
            
            val request = DeleteRequest(
                ids = null,
                where = whereJson,
                where_document = null
            )
            
            val response = client.post("$collectionsBasePath/$idToUse/delete") {
                contentType(ContentType.Application.Json)
                addAuthHeader()
                setBody(request)
            }
            
            if (response.status != HttpStatusCode.OK) {
                val errorBody = response.body<String>()
                AppLogger.w("ChromaCloudVectorStore", "Failed to delete chunks for file $filePath in vault $vaultPath: ${response.status} - $errorBody")
                return@withContext
            }
            
            AppLogger.d("ChromaCloudVectorStore", "Deleted chunks for file: $filePath in vault: $vaultPath")
        } catch (e: Exception) {
            AppLogger.w("ChromaCloudVectorStore", "Failed to delete chunks for file $filePath in vault $vaultPath: ${e.message}")
        }
    }
    
    /**
     * Gets the embedding dimension for the collection.
     * Tries to detect from existing documents, tries common dimensions (768, 1024) if detection fails.
     */
    private suspend fun getEmbeddingDimension(collectionIdToUse: String): Int = withContext(Dispatchers.IO) {
        // Return cached dimension if available
        if (embeddingDimension != null) {
            return@withContext embeddingDimension!!
        }
        
        // Try common dimensions: 768 (nomic-embed-text) and 1024 (mxbai-embed-large)
        val dimensionsToTry = listOf(768, 1024)
        var detectedDimension: Int? = null
        
        // First, try to get dimension from an existing document in the collection
        for (dim in dimensionsToTry) {
            try {
                val dummyEmbedding = FloatArray(dim) { 0f }
                val request = QueryRequest(
                    query_embeddings = listOf(dummyEmbedding.toList()),
                    n_results = 1,
                    where = null,
                    where_document = null,
                    ids = null,
                    include = listOf("embeddings")
                )
                
                val response = client.post("$collectionsBasePath/$collectionIdToUse/query") {
                    contentType(ContentType.Application.Json)
                    addAuthHeader()
                    setBody(request)
                }
                
                if (response.status == HttpStatusCode.OK) {
                    val queryResponse: QueryResponse = response.body()
                    // Check if we got embeddings back
                    val embeddings = queryResponse.embeddings?.get(0)
                    if (embeddings != null && embeddings.isNotEmpty() && embeddings[0].isNotEmpty()) {
                        detectedDimension = embeddings[0].size
                        AppLogger.d("ChromaCloudVectorStore", "Detected embedding dimension from collection: $detectedDimension")
                        break
                    } else {
                        // Query succeeded but no embeddings returned - collection might be empty
                        // Try the dimension we used for the query as it worked
                        detectedDimension = dim
                        AppLogger.d("ChromaCloudVectorStore", "Query succeeded with dimension $dim, collection may be empty. Using dimension $dim.")
                        break
                    }
                } else if (response.status == HttpStatusCode.UnprocessableEntity || response.status == HttpStatusCode.BadRequest) {
                    // Dimension mismatch - try next dimension
                    continue // Try next dimension
                }
            } catch (e: Exception) {
                continue // Try next dimension
            }
        }
        
        // If we couldn't detect, default to 1024 (mxbai-embed-large is now the default)
        val finalDimension = detectedDimension ?: 1024
        
        if (detectedDimension == null) {
            AppLogger.w("ChromaCloudVectorStore", "Could not detect embedding dimension, defaulting to 1024 (mxbai-embed-large)")
        }
        
        // Cache and return the dimension
        embeddingDimension = finalDimension
        return@withContext finalDimension
    }
    
    override suspend fun hasVaultData(vaultPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            AppLogger.i("ChromaCloudVectorStore", "hasVaultData() called for vault: $vaultPath")
            
            ensureCollection()
            
            var idToUse = collectionId
            if (idToUse == null) {
                AppLogger.w("ChromaCloudVectorStore", "Collection ID is null after ensureCollection(), attempting direct lookup")
                try {
                    val getResponse = client.get("$collectionsBasePath/$collectionName") {
                        addAuthHeader()
                    }
                    AppLogger.i("ChromaCloudVectorStore", "GET response status: ${getResponse.status}")
                    if (getResponse.status == HttpStatusCode.OK) {
                        val collectionResponse: CollectionResponse = getResponse.body()
                        idToUse = collectionResponse.id
                        collectionId = idToUse
                        AppLogger.i("ChromaCloudVectorStore", "Retrieved collection ID via direct lookup: $idToUse")
                    } else {
                        AppLogger.w("ChromaCloudVectorStore", "Collection '$collectionName' not found (status: ${getResponse.status})")
                    }
                } catch (e: Exception) {
                    AppLogger.w("ChromaCloudVectorStore", "Could not get collection ID for hasVaultData: ${e.message}", e)
                    AppLogger.i("ChromaCloudVectorStore", "Result: false (collection ID lookup failed)")
                    return@withContext false
                }
            }
            
            if (idToUse == null) {
                AppLogger.i("ChromaCloudVectorStore", "Result: false (collection ID is null)")
                return@withContext false
            }
            
            AppLogger.i("ChromaCloudVectorStore", "Collection ID: $idToUse")
            
            // Get the embedding dimension (will detect or use default)
            val dimension = getEmbeddingDimension(idToUse)
            AppLogger.i("ChromaCloudVectorStore", "Using embedding dimension: $dimension")
            
            // Query with where clause to check if any documents exist for this vault
            // Use a dummy embedding (zero vector) with limit=1 to minimize data transfer
            val dummyEmbedding = FloatArray(dimension) { 0f }
            
            val whereJson = buildJsonObject {
                put("vault_path", JsonPrimitive(vaultPath))
            }
            
            AppLogger.i("ChromaCloudVectorStore", "Query where clause: vault_path = '$vaultPath'")
            
            val request = QueryRequest(
                query_embeddings = listOf(dummyEmbedding.toList()),
                n_results = 1, // Only need to know if at least one exists
                where = whereJson,
                where_document = null,
                ids = null,
                include = listOf("metadatas") // Use metadatas (ids are always returned by default)
            )
            
            val queryUrl = "$collectionsBasePath/$idToUse/query"
            AppLogger.i("ChromaCloudVectorStore", "POST $queryUrl")
            
            val response = client.post(queryUrl) {
                contentType(ContentType.Application.Json)
                addAuthHeader()
                setBody(request)
            }
            
            AppLogger.i("ChromaCloudVectorStore", "Response status: ${response.status}")
            
            if (response.status != HttpStatusCode.OK) {
                val errorBody = try { response.body<String>() } catch (e: Exception) { "Unable to read error body" }
                AppLogger.e("ChromaCloudVectorStore", "Query failed with status ${response.status}")
                AppLogger.e("ChromaCloudVectorStore", "Error body: $errorBody")
                AppLogger.i("ChromaCloudVectorStore", "Result: false (query failed)")
                return@withContext false
            }
            
            val queryResponse: QueryResponse = response.body()
            // If we got any IDs back, the vault has data
            val idsCount = queryResponse.ids?.get(0)?.size ?: 0
            val hasData = queryResponse.ids != null && 
                         queryResponse.ids.isNotEmpty() && 
                         queryResponse.ids[0].isNotEmpty()
            
            AppLogger.i("ChromaCloudVectorStore", "Query returned ${idsCount} document IDs")
            AppLogger.i("ChromaCloudVectorStore", "Result: $hasData")
            return@withContext hasData
        } catch (e: Exception) {
            AppLogger.e("ChromaCloudVectorStore", "Exception in hasVaultData: ${e.message}", e)
            AppLogger.i("ChromaCloudVectorStore", "Result: false (exception)")
            return@withContext false
        }
    }
    
    /**
     * Clears all vectors for a specific vault.
     * 
     * @param vaultPath Absolute path of the vault
     */
    suspend fun clearVault(vaultPath: String) = withContext(Dispatchers.IO) {
        try {
            ensureCollection()
            
            var idToUse = collectionId
            if (idToUse == null) {
                try {
                    val getResponse = client.get("$collectionsBasePath/$collectionName") {
                        addAuthHeader()
                    }
                    if (getResponse.status == HttpStatusCode.OK) {
                        val collectionResponse: CollectionResponse = getResponse.body()
                        idToUse = collectionResponse.id
                        collectionId = idToUse
                    }
                } catch (e: Exception) {
                    AppLogger.w("ChromaCloudVectorStore", "Could not get collection ID for clearVault: ${e.message}")
                }
            }
            
            if (idToUse == null) {
                AppLogger.w("ChromaCloudVectorStore", "Skipping clearVault for $vaultPath - collection ID not available")
                return@withContext
            }
            
            // Create JsonObject for where clause with vault_path
            val whereJson = buildJsonObject {
                put("vault_path", JsonPrimitive(vaultPath))
            }
            
            val request = DeleteRequest(
                ids = null,
                where = whereJson,
                where_document = null
            )
            
            val response = client.post("$collectionsBasePath/$idToUse/delete") {
                contentType(ContentType.Application.Json)
                addAuthHeader()
                setBody(request)
            }
            
            if (response.status != HttpStatusCode.OK) {
                val errorBody = response.body<String>()
                AppLogger.w("ChromaCloudVectorStore", "Failed to clear vault $vaultPath: ${response.status} - $errorBody")
                return@withContext
            }
            
            AppLogger.d("ChromaCloudVectorStore", "Cleared all vectors for vault: $vaultPath")
        } catch (e: Exception) {
            AppLogger.e("ChromaCloudVectorStore", "Failed to clear vault $vaultPath", e)
        }
    }
    
    /**
     * Closes the HTTP client and releases resources.
     */
    fun close() {
        client.close()
    }
}

