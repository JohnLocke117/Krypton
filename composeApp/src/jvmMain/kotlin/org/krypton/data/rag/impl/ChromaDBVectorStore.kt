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
import org.krypton.rag.NoteChunk
import org.krypton.rag.RagChunk
import org.krypton.rag.SearchResult
import org.krypton.rag.VectorStore
import org.krypton.rag.models.Embedding
import org.krypton.util.AppLogger

/**
 * ChromaDB vector store implementation using HTTP API.
 * 
 * This implementation:
 * 1. Connects to ChromaDB running in Docker container
 * 2. Stores embeddings, documents, and metadata in ChromaDB collections
 * 3. Uses ChromaDB's built-in cosine similarity search
 * 
 * For persistence across container restarts, ChromaDB should be run with:
 *   docker run --name chromadb \
 *     -p 8000:8000 \
 *     -v $(pwd)/chroma_data:/chroma/chroma_data \
 *     -e IS_PERSISTENT=TRUE \
 *     chromadb/chroma
 * 
 * The collection ID is cached in memory but will be re-fetched by name on app restart
 * to handle persistence. Logs will indicate whether collections are found (persisted)
 * or created (new) to help verify persistence is working.
 * 
 * @param baseUrl Base URL of ChromaDB server (e.g., "http://localhost:8000")
 * @param collectionName Name of the ChromaDB collection to use
 * @param httpClientEngine HTTP client engine for making requests
 */
class ChromaDBVectorStore(
    private val baseUrl: String,
    private val collectionName: String,
    private val httpClientEngine: HttpClientEngine,
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
    
    // Cache collection ID to avoid repeated lookups
    private var collectionId: String? = null
    
    // Cache embedding dimension to avoid repeated queries
    // nomic-embed-text:v1.5 uses 768 dimensions, but we'll detect it dynamically
    private var embeddingDimension: Int? = null
    
    /**
     * Gets collection ID from collection name.
     */
    private suspend fun getCollectionIdFromName(collectionName: String): String? = withContext(Dispatchers.IO) {
        try {
            val response = client.get("$collectionsBasePath/$collectionName")
            if (response.status == HttpStatusCode.OK) {
                val collectionResponse: CollectionResponse = response.body()
                AppLogger.d("ChromaDBVectorStore", "Retrieved collection ID for '$collectionName': ${collectionResponse.id}")
                return@withContext collectionResponse.id
            } else if (response.status == HttpStatusCode.NotFound) {
                AppLogger.d("ChromaDBVectorStore", "Collection '$collectionName' not found (404)")
                return@withContext null
            } else {
                AppLogger.w("ChromaDBVectorStore", "Unexpected status when getting collection '$collectionName': ${response.status}")
                return@withContext null
            }
        } catch (e: Exception) {
            AppLogger.w("ChromaDBVectorStore", "Failed to get collection ID for '$collectionName': ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * Ensures the collection exists, creating it if necessary, and caches the collection ID.
     * Always attempts to fetch collection ID by name first to handle persistence across restarts.
     */
    private suspend fun ensureCollection() = withContext(Dispatchers.IO) {
        try {
            // Always try to get the collection ID from name first (handles persistence after restart)
            val id = getCollectionIdFromName(collectionName)
            if (id != null) {
                collectionId = id
                AppLogger.d("ChromaDBVectorStore", "Found existing collection '$collectionName' with ID: $id")
                return@withContext
            }
            
            // Collection doesn't exist, create it
            AppLogger.d("ChromaDBVectorStore", "Collection '$collectionName' not found, creating new collection")
            createCollection()
        } catch (e: Exception) {
            // Collection lookup failed, clear cache and try to create
            AppLogger.w("ChromaDBVectorStore", "Failed to lookup collection '$collectionName': ${e.message}. Clearing cache and attempting to create.")
            if (collectionId != null) {
                AppLogger.d("ChromaDBVectorStore", "Clearing collection ID cache due to lookup failure")
            }
            collectionId = null // Clear cache on lookup failure
            try {
                createCollection()
            } catch (createException: Exception) {
                AppLogger.e("ChromaDBVectorStore", "Failed to ensure collection exists: ${createException.message}", createException)
                throw ChromaDBException("Failed to connect to ChromaDB at $baseUrl: ${createException.message}", createException)
            }
        }
    }
    
    /**
     * Creates a new collection in ChromaDB and caches the collection ID.
     * 
     * @param forceCreate If true, uses get_or_create=false to force creation of a new collection.
     *                    If false, uses get_or_create=true to get existing or create new.
     */
    private suspend fun createCollection(forceCreate: Boolean = false) = withContext(Dispatchers.IO) {
        // #region agent log
        try {
            java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("""{"sessionId":"debug-session","runId":"run1","hypothesisId":"B","location":"ChromaDBVectorStore.kt:createCollection","message":"createCollection entry","data":{"collectionName":"$collectionName","forceCreate":$forceCreate,"get_or_create":${!forceCreate}},"timestamp":${System.currentTimeMillis()}}
""")
        } catch (e: Exception) {}
        // #endregion
        
        val request = CreateCollectionRequest(
            name = collectionName,
            get_or_create = !forceCreate,
            metadata = null,
            configuration = null,
            schema = null
        )
        
        val response = client.post(collectionsBasePath) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        
        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
            val errorBody = response.body<String>()
            // #region agent log
            try {
                java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("""{"sessionId":"debug-session","runId":"run1","hypothesisId":"B","location":"ChromaDBVectorStore.kt:createCollection","message":"Create failed","data":{"status":"${response.status}","error":"$errorBody"},"timestamp":${System.currentTimeMillis()}}
""")
            } catch (e: Exception) {}
            // #endregion
            throw ChromaDBException("Failed to create collection: ${response.status} - $errorBody")
        }
        
        // Parse collection ID from response
        val collectionResponse: CollectionResponse = response.body()
        val newCollectionId = collectionResponse.id
        
        // #region agent log
        try {
            java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("""{"sessionId":"debug-session","runId":"run1","hypothesisId":"B,C","location":"ChromaDBVectorStore.kt:createCollection","message":"Collection created/retrieved","data":{"newCollectionId":"$newCollectionId","oldCollectionId":"$collectionId","forceCreate":$forceCreate},"timestamp":${System.currentTimeMillis()}}
""")
        } catch (e: Exception) {}
        // #endregion
        
        collectionId = newCollectionId
        
        AppLogger.i("ChromaDBVectorStore", "Collection Created/Retrieved Successfully")
        AppLogger.i("ChromaDBVectorStore", "  Collection Name: $collectionName")
        AppLogger.i("ChromaDBVectorStore", "  Collection ID: $newCollectionId")
        AppLogger.d("ChromaDBVectorStore", "Collection ID cached for future operations")
    }
    
    override suspend fun upsert(chunks: List<RagChunk>) = withContext(Dispatchers.IO) {
        if (chunks.isEmpty()) return@withContext
        
        try {
            // Ensure collection exists
            ensureCollection()
            
            // Prepare data for ChromaDB
            val ids = chunks.map { it.id }
            val embeddings = chunks.mapNotNull { chunk ->
                chunk.embedding // RagChunk.embedding is already List<Float>?
            }
            val documents = chunks.map { it.text }
            val metadatas = chunks.map { chunk ->
                buildJsonObject {
                    // Copy all metadata from RagChunk
                    chunk.metadata.forEach { (key, value) ->
                        put(key, JsonPrimitive(value))
                    }
                }
            }
            
            // Validate that all chunks have embeddings
            if (embeddings.size != chunks.size) {
                throw ChromaDBException("Cannot upsert chunks without embeddings")
            }
            
            // Ensure we have collection ID
            if (collectionId == null) {
                ensureCollection()
            }
            
            val collectionIdToUse = collectionId
                ?: throw ChromaDBException("Collection ID not available for upsert")
            
            val request = AddRequest(
                ids = ids,
                embeddings = embeddings,
                documents = documents,
                metadatas = metadatas,
                uris = null
            )
            
            val response = client.post("$collectionsBasePath/$collectionIdToUse/add") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            // ChromaDB v2 API returns 201 Created for successful add operations
            if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
                val errorBody = response.body<String>()
                
                // Check for dimension mismatch error
                if (response.status == HttpStatusCode.BadRequest && 
                    errorBody.contains("dimension", ignoreCase = true)) {
                    AppLogger.w("ChromaDBVectorStore", "Dimension mismatch detected: $errorBody")
                    AppLogger.i("ChromaDBVectorStore", "Attempting to fix by recreating collection with correct dimension")
                    
                    // Get actual embedding dimension from chunks
                    val actualDimension = embeddings.firstOrNull()?.size
                    if (actualDimension != null) {
                        AppLogger.i("ChromaDBVectorStore", "Detected actual embedding dimension: $actualDimension")
                        
                        // Delete and recreate collection, then retry
                        try {
                            deleteCollection()
                            collectionId = null
                            embeddingDimension = null
                            
                            // Force create a new collection (don't use get_or_create which might find old one)
                            createCollection(forceCreate = true)
                            
                            // Get the new collection ID after recreation
                            val newCollectionId = collectionId
                                ?: throw ChromaDBException("Collection ID not available after recreation")
                            
                            // Retry the upsert
                            val retryRequest = AddRequest(
                                ids = ids,
                                embeddings = embeddings,
                                documents = documents,
                                metadatas = metadatas,
                                uris = null
                            )
                            
                            val retryResponse = client.post("$collectionsBasePath/$newCollectionId/add") {
                                contentType(ContentType.Application.Json)
                                setBody(retryRequest)
                            }
                            
                            if (retryResponse.status == HttpStatusCode.OK || retryResponse.status == HttpStatusCode.Created) {
                                AppLogger.i("ChromaDBVectorStore", "Successfully upserted after recreating collection with dimension $actualDimension")
                                AppLogger.d("ChromaDBVectorStore", "Upserted ${chunks.size} chunks into collection '$collectionName'")
                                // Update cached dimension
                                embeddingDimension = actualDimension
                                return@withContext
                            } else {
                                val retryErrorBody = retryResponse.body<String>()
                                AppLogger.e("ChromaDBVectorStore", "Retry failed after recreating collection: ${retryResponse.status} - $retryErrorBody")
                                throw ChromaDBException("Failed to upsert chunks after recreating collection: ${retryResponse.status} - $retryErrorBody")
                            }
                        } catch (recreateException: Exception) {
                            AppLogger.e("ChromaDBVectorStore", "Failed to recreate collection: ${recreateException.message}", recreateException)
                            throw ChromaDBException("Failed to upsert chunks: dimension mismatch and recreation failed - ${recreateException.message}", recreateException)
                        }
                    } else {
                        AppLogger.e("ChromaDBVectorStore", "Cannot determine embedding dimension from chunks")
                        throw ChromaDBException("Failed to upsert chunks: dimension mismatch - ${response.status} - $errorBody")
                    }
                } else {
                    throw ChromaDBException("Failed to upsert chunks: ${response.status} - $errorBody")
                }
            }
            
            AppLogger.d("ChromaDBVectorStore", "Upserted ${chunks.size} chunks into collection '$collectionName'")
        } catch (e: Exception) {
            AppLogger.e("ChromaDBVectorStore", "Failed to upsert ${chunks.size} chunks", e)
            throw ChromaDBException("Failed to upsert chunks: ${e.message}", e)
        }
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
            AppLogger.d("ChromaDBVectorStore", "Running Query on Collection")
            AppLogger.d("ChromaDBVectorStore", "  Collection Name: $collectionName")
            AppLogger.d("ChromaDBVectorStore", "  Collection ID: $collectionIdToUse")
            AppLogger.d("ChromaDBVectorStore", "  Top K: $topK")
            AppLogger.d("ChromaDBVectorStore", "  Query Embedding Dimension: ${queryEmbedding.vector.size}")
            
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
                setBody(request)
            }
            
            if (response.status != HttpStatusCode.OK) {
                val errorBody = response.body<String>()
                
                // Check for dimension mismatch error
                if (response.status == HttpStatusCode.BadRequest && 
                    errorBody.contains("dimension", ignoreCase = true)) {
                    AppLogger.w("ChromaDBVectorStore", "Dimension mismatch detected in search: $errorBody")
                    AppLogger.i("ChromaDBVectorStore", "Query embedding dimension: ${queryEmbedding.vector.size}, attempting to fix by recreating collection")
                    
                    // Get actual embedding dimension from query
                    val actualDimension = queryEmbedding.vector.size
                    if (actualDimension > 0) {
                        AppLogger.i("ChromaDBVectorStore", "Detected actual embedding dimension: $actualDimension")
                        
                        // Delete and recreate collection, then retry
                        try {
                            // #region agent log
                            try {
                                java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("""{"sessionId":"debug-session","runId":"run1","hypothesisId":"A,B,C,D","location":"ChromaDBVectorStore.kt:search","message":"Starting collection recreation","data":{"actualDimension":$actualDimension,"currentCollectionId":"$collectionId","collectionName":"$collectionName"},"timestamp":${System.currentTimeMillis()}}
""")
                            } catch (e: Exception) {}
                            // #endregion
                            
                            deleteCollection()
                            collectionId = null
                            embeddingDimension = null
                            
                            // #region agent log
                            try {
                                java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("""{"sessionId":"debug-session","runId":"run1","hypothesisId":"C","location":"ChromaDBVectorStore.kt:search","message":"Cache cleared, checking if collection still exists","data":{"collectionName":"$collectionName"},"timestamp":${System.currentTimeMillis()}}
""")
                            } catch (e: Exception) {}
                            // #endregion
                            
                            // Check if collection still exists by name (should not exist after deletion)
                            val stillExists = getCollectionIdFromName(collectionName)
                            
                            // #region agent log
                            try {
                                java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("""{"sessionId":"debug-session","runId":"run1","hypothesisId":"B,C","location":"ChromaDBVectorStore.kt:search","message":"Collection existence check","data":{"stillExists":${stillExists != null},"foundId":"$stillExists"},"timestamp":${System.currentTimeMillis()}}
""")
                            } catch (e: Exception) {}
                            // #endregion
                            
                            // Force create a new collection (don't use get_or_create which might find old one)
                            createCollection(forceCreate = true)
                            
                            // Get the new collection ID after recreation
                            val newCollectionId = collectionId
                                ?: throw ChromaDBException("Collection ID not available after recreation")
                            
                            // #region agent log
                            try {
                                java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("""{"sessionId":"debug-session","runId":"run1","hypothesisId":"B","location":"ChromaDBVectorStore.kt:search","message":"Collection recreated","data":{"newCollectionId":"$newCollectionId"},"timestamp":${System.currentTimeMillis()}}
""")
                            } catch (e: Exception) {}
                            // #endregion
                            
                            // Initialize collection with correct dimension by adding a dummy document
                            // This ensures ChromaDB knows the expected dimension before we search
                            AppLogger.d("ChromaDBVectorStore", "Initializing collection with dimension $actualDimension using dummy document")
                            val dummyEmbedding = List(actualDimension) { 0f }
                            val initRequest = AddRequest(
                                ids = listOf("__dimension_init__"),
                                embeddings = listOf(dummyEmbedding),
                                documents = listOf("__dimension_init__"),
                                metadatas = listOf(buildJsonObject {
                                    put("__init__", JsonPrimitive("true"))
                                }),
                                uris = null
                            )
                            
                            val initResponse = client.post("$collectionsBasePath/$newCollectionId/add") {
                                contentType(ContentType.Application.Json)
                                setBody(initRequest)
                            }
                            
                            if (initResponse.status != HttpStatusCode.OK && initResponse.status != HttpStatusCode.Created) {
                                val initErrorBody = initResponse.body<String>()
                                // #region agent log
                                try {
                                    java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("""{"sessionId":"debug-session","runId":"run1","hypothesisId":"E","location":"ChromaDBVectorStore.kt:search","message":"Dummy init failed","data":{"status":"${initResponse.status}","error":"$initErrorBody","actualDimension":$actualDimension},"timestamp":${System.currentTimeMillis()}}
""")
                                } catch (e: Exception) {}
                                // #endregion
                                AppLogger.w("ChromaDBVectorStore", "Failed to initialize collection dimension: ${initResponse.status} - $initErrorBody. Continuing with search anyway.")
                            } else {
                                // #region agent log
                                try {
                                    java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("""{"sessionId":"debug-session","runId":"run1","hypothesisId":"E","location":"ChromaDBVectorStore.kt:search","message":"Dummy init succeeded","data":{"actualDimension":$actualDimension},"timestamp":${System.currentTimeMillis()}}
""")
                                } catch (e: Exception) {}
                                // #endregion
                                AppLogger.d("ChromaDBVectorStore", "Collection initialized with dimension $actualDimension")
                                // Update cached dimension
                                embeddingDimension = actualDimension
                            }
                            
                            // Retry the search
                            val retryRequest = QueryRequest(
                                query_embeddings = listOf(queryEmbedding.vector),
                                n_results = topK,
                                where = whereClause,
                                where_document = null,
                                ids = null,
                                include = listOf("documents", "metadatas", "distances")
                            )
                            
                            val retryResponse = client.post("$collectionsBasePath/$newCollectionId/query") {
                                contentType(ContentType.Application.Json)
                                setBody(retryRequest)
                            }
                            
                            // #region agent log
                            try {
                                java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("""{"sessionId":"debug-session","runId":"run1","hypothesisId":"E","location":"ChromaDBVectorStore.kt:search","message":"Retry search response","data":{"status":"${retryResponse.status}","actualDimension":$actualDimension},"timestamp":${System.currentTimeMillis()}}
""")
                            } catch (e: Exception) {}
                            // #endregion
                            
                            if (retryResponse.status == HttpStatusCode.OK) {
                                AppLogger.i("ChromaDBVectorStore", "Successfully searched after recreating collection with dimension $actualDimension")
                                // Continue with processing retryResponse below
                                val queryResponse: QueryResponse = retryResponse.body()
                                
                                // Convert ChromaDB response to SearchResult list with similarity scores
                                val results = mutableListOf<SearchResult>()
                                
                                if (queryResponse.ids != null && queryResponse.ids.isNotEmpty()) {
                                    val ids = queryResponse.ids[0]
                                    val documents = queryResponse.documents?.get(0) ?: emptyList()
                                    val metadatas = queryResponse.metadatas?.get(0) ?: emptyList()
                                    val distances = queryResponse.distances?.get(0) ?: emptyList()
                                    
                                    for (i in ids.indices) {
                                        val id = ids[i]
                                        val document = documents.getOrNull(i) ?: ""
                                        val metadataJson = metadatas.getOrNull(i) ?: buildJsonObject { }
                                        val distance = distances.getOrNull(i) ?: 1.0f
                                        
                                        val similarity = (1.0 - distance.toDouble()).coerceIn(0.0, 1.0)
                                        
                                        val metadata = buildMap<String, String> {
                                            metadataJson.forEach { (key, value) ->
                                                if (value is JsonPrimitive && value.isString) {
                                                    put(key, value.content)
                                                }
                                            }
                                        }
                                        
                                        val chunk = RagChunk(
                                            id = id,
                                            text = document,
                                            metadata = metadata,
                                            embedding = null
                                        )
                                        results.add(SearchResult(chunk = chunk, similarity = similarity))
                                    }
                                }
                                
                                AppLogger.d("ChromaDBVectorStore", "Query Completed Successfully after dimension fix")
                                AppLogger.d("ChromaDBVectorStore", "  Results Returned: ${results.size} chunks")
                                return@withContext results
                            } else {
                                val retryErrorBody = retryResponse.body<String>()
                                AppLogger.e("ChromaDBVectorStore", "Retry search failed after recreating collection: ${retryResponse.status} - $retryErrorBody")
                                throw ChromaDBException("Failed to search after recreating collection: ${retryResponse.status} - $retryErrorBody")
                            }
                        } catch (recreateException: Exception) {
                            AppLogger.e("ChromaDBVectorStore", "Failed to recreate collection for search: ${recreateException.message}", recreateException)
                            throw ChromaDBException("Failed to search: dimension mismatch and recreation failed - ${recreateException.message}", recreateException)
                        }
                    } else {
                        AppLogger.e("ChromaDBVectorStore", "Cannot determine embedding dimension from query")
                        throw ChromaDBException("Failed to search: dimension mismatch - ${response.status} - $errorBody")
                    }
                } else {
                    throw ChromaDBException("Failed to search: ${response.status} - $errorBody")
                }
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
            
            AppLogger.d("ChromaDBVectorStore", "Query Completed Successfully")
            AppLogger.d("ChromaDBVectorStore", "  Results Returned: ${results.size} chunks")
            return@withContext results
        } catch (e: Exception) {
            AppLogger.e("ChromaDBVectorStore", "Failed to search (query size: ${queryEmbedding.vector.size}, topK: $topK)", e)
            // Return empty list on error rather than crashing
            return@withContext emptyList()
        }
    }
    
    override suspend fun clear() = withContext(Dispatchers.IO) {
        try {
            ensureCollection()
            
            val collectionIdToUse = collectionId
                ?: throw ChromaDBException("Collection ID not available for clear")
            
            // Delete collection with body containing new collection info
            val deleteRequest = DeleteCollectionRequest(
                new_name = collectionName,
                new_metadata = null,
                new_configuration = null
            )
            
            val deleteResponse = client.delete("$collectionsBasePath/$collectionIdToUse") {
                contentType(ContentType.Application.Json)
                setBody(deleteRequest)
            }
            
            if (deleteResponse.status == HttpStatusCode.OK || deleteResponse.status == HttpStatusCode.NoContent) {
                // Clear cached ID and embedding dimension, then recreate the collection
                AppLogger.d("ChromaDBVectorStore", "Clearing collection ID and embedding dimension cache before recreating collection")
                collectionId = null
                embeddingDimension = null
                createCollection()
                AppLogger.d("ChromaDBVectorStore", "Cleared collection '$collectionName'")
            } else {
                throw ChromaDBException("Failed to clear collection: ${deleteResponse.status}")
            }
        } catch (e: Exception) {
            AppLogger.e("ChromaDBVectorStore", "Failed to clear vector store", e)
            throw ChromaDBException("Failed to clear collection: ${e.message}", e)
        }
    }
    
    /**
     * Deletes the collection (without recreating it).
     * Used when we need to recreate the collection with a different embedding dimension.
     * 
     * If the collection doesn't exist (404), treats it as already deleted and returns successfully.
     */
    private suspend fun deleteCollection() = withContext(Dispatchers.IO) {
        // #region agent log
        try {
            java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("""{"sessionId":"debug-session","runId":"run1","hypothesisId":"A,B,C","location":"ChromaDBVectorStore.kt:deleteCollection","message":"deleteCollection entry","data":{"collectionName":"$collectionName","cachedCollectionId":"$collectionId"},"timestamp":${System.currentTimeMillis()}}
""")
        } catch (e: Exception) {}
        // #endregion
        
        try {
            val collectionIdToUse = collectionId
                ?: getCollectionIdFromName(collectionName)
                ?: run {
                    // #region agent log
                    try {
                        java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("""{"sessionId":"debug-session","runId":"run1","hypothesisId":"A","location":"ChromaDBVectorStore.kt:deleteCollection","message":"No collection ID found","data":{"collectionName":"$collectionName"},"timestamp":${System.currentTimeMillis()}}
""")
                    } catch (e: Exception) {}
                    // #endregion
                    AppLogger.w("ChromaDBVectorStore", "Cannot delete collection: ID not available, assuming already deleted")
                    return@withContext // Collection doesn't exist, treat as success
                }
            
            // #region agent log
            try {
                java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("""{"sessionId":"debug-session","runId":"run1","hypothesisId":"A","location":"ChromaDBVectorStore.kt:deleteCollection","message":"Attempting deletion","data":{"collectionIdToUse":"$collectionIdToUse","collectionName":"$collectionName"},"timestamp":${System.currentTimeMillis()}}
""")
            } catch (e: Exception) {}
            // #endregion
            
            val deleteRequest = DeleteCollectionRequest(
                new_name = null,
                new_metadata = null,
                new_configuration = null
            )
            
            val deleteResponse = client.delete("$collectionsBasePath/$collectionIdToUse") {
                contentType(ContentType.Application.Json)
                setBody(deleteRequest)
            }
            
            // #region agent log
            try {
                java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("""{"sessionId":"debug-session","runId":"run1","hypothesisId":"A,D","location":"ChromaDBVectorStore.kt:deleteCollection","message":"Delete response","data":{"status":"${deleteResponse.status}","collectionIdToUse":"$collectionIdToUse"},"timestamp":${System.currentTimeMillis()}}
""")
            } catch (e: Exception) {}
            // #endregion
            
            if (deleteResponse.status == HttpStatusCode.OK || deleteResponse.status == HttpStatusCode.NoContent) {
                AppLogger.i("ChromaDBVectorStore", "Deleted collection '$collectionName' for dimension mismatch fix")
            } else if (deleteResponse.status == HttpStatusCode.NotFound) {
                // Collection doesn't exist by ID, try deleting by name
                // #region agent log
                try {
                    java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("""{"sessionId":"debug-session","runId":"run1","hypothesisId":"A","location":"ChromaDBVectorStore.kt:deleteCollection","message":"Delete by ID returned 404, trying by name","data":{"collectionIdToUse":"$collectionIdToUse","collectionName":"$collectionName"},"timestamp":${System.currentTimeMillis()}}
""")
                } catch (e: Exception) {}
                // #endregion
                
                // Try deleting by name instead
                val deleteByNameResponse = client.delete("$collectionsBasePath/$collectionName") {
                    contentType(ContentType.Application.Json)
                    setBody(deleteRequest)
                }
                
                // #region agent log
                try {
                    java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("""{"sessionId":"debug-session","runId":"run1","hypothesisId":"A","location":"ChromaDBVectorStore.kt:deleteCollection","message":"Delete by name response","data":{"status":"${deleteByNameResponse.status}","collectionName":"$collectionName"},"timestamp":${System.currentTimeMillis()}}
""")
                } catch (e: Exception) {}
                // #endregion
                
                if (deleteByNameResponse.status == HttpStatusCode.OK || deleteByNameResponse.status == HttpStatusCode.NoContent) {
                    AppLogger.i("ChromaDBVectorStore", "Deleted collection '$collectionName' by name for dimension mismatch fix")
                } else if (deleteByNameResponse.status == HttpStatusCode.NotFound) {
                    AppLogger.d("ChromaDBVectorStore", "Collection '$collectionName' does not exist (404), treating as already deleted")
                } else {
                    val errorBody = deleteByNameResponse.body<String>()
                    AppLogger.w("ChromaDBVectorStore", "Failed to delete collection by name: ${deleteByNameResponse.status} - $errorBody")
                    // Don't throw - treat as already deleted
                }
            } else {
                val errorBody = deleteResponse.body<String>()
                AppLogger.w("ChromaDBVectorStore", "Failed to delete collection: ${deleteResponse.status} - $errorBody")
                throw ChromaDBException("Failed to delete collection: ${deleteResponse.status} - $errorBody")
            }
        } catch (e: ChromaDBException) {
            // Re-throw ChromaDBException as-is
            throw e
        } catch (e: Exception) {
            // For other exceptions (e.g., network errors), check if it's a 404
            val errorMsg = e.message?.lowercase() ?: ""
            if (errorMsg.contains("404") || errorMsg.contains("not found")) {
                AppLogger.d("ChromaDBVectorStore", "Collection deletion returned 404, treating as already deleted")
                return@withContext // Treat as success
            }
            AppLogger.e("ChromaDBVectorStore", "Error deleting collection: ${e.message}", e)
            throw ChromaDBException("Failed to delete collection: ${e.message}", e)
        }
    }
    
    /**
     * Upserts chunks with vault metadata (vault_path and file_hash).
     * 
     * @param chunks List of RagChunk objects to store
     * @param vaultPath Absolute path of the vault
     * @param fileHash SHA-256 hash of the file content
     */
    suspend fun upsertWithMetadata(chunks: List<RagChunk>, vaultPath: String, fileHash: String) = withContext(Dispatchers.IO) {
        if (chunks.isEmpty()) return@withContext
        
        try {
            // Ensure collection exists
            ensureCollection()
            
            // Prepare data for ChromaDB
            val ids = chunks.map { it.id }
            val embeddings = chunks.mapNotNull { chunk ->
                chunk.embedding // RagChunk.embedding is already List<Float>?
            }
            val documents = chunks.map { it.text }
            val metadatas = chunks.map { chunk ->
                buildJsonObject {
                    // Add vault metadata
                    put("vault_path", JsonPrimitive(vaultPath))
                    put("file_hash", JsonPrimitive(fileHash))
                    // Copy all metadata from RagChunk (includes filePath, startLine, endLine, etc.)
                    chunk.metadata.forEach { (key, value) ->
                        put(key, JsonPrimitive(value))
                    }
                }
            }
            
            // Validate that all chunks have embeddings
            if (embeddings.size != chunks.size) {
                throw ChromaDBException("Cannot upsert chunks without embeddings")
            }
            
            // Ensure we have collection ID
            if (collectionId == null) {
                ensureCollection()
            }
            
            val collectionIdToUse = collectionId
                ?: throw ChromaDBException("Collection ID not available for upsert")
            
            val request = AddRequest(
                ids = ids,
                embeddings = embeddings,
                documents = documents,
                metadatas = metadatas,
                uris = null
            )
            
            val addUrl = "$collectionsBasePath/$collectionIdToUse/add"
            AppLogger.i("ChromaDBVectorStore", "POST $addUrl")
            AppLogger.i("ChromaDBVectorStore", "Upserting ${chunks.size} chunks with metadata")
            AppLogger.i("ChromaDBVectorStore", "  vault_path: $vaultPath")
            AppLogger.i("ChromaDBVectorStore", "  file_hash: $fileHash")
            
            val response = client.post(addUrl) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            AppLogger.i("ChromaDBVectorStore", "Response status: ${response.status}")
            
            // ChromaDB v2 API returns 201 Created for successful add operations
            if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
                val errorBody = response.body<String>()
                
                // Check for dimension mismatch error
                if (response.status == HttpStatusCode.BadRequest && 
                    errorBody.contains("dimension", ignoreCase = true)) {
                    AppLogger.w("ChromaDBVectorStore", "Dimension mismatch detected: $errorBody")
                    AppLogger.i("ChromaDBVectorStore", "Attempting to fix by recreating collection with correct dimension")
                    
                    // Get actual embedding dimension from chunks
                    val actualDimension = embeddings.firstOrNull()?.size
                    if (actualDimension != null) {
                        AppLogger.i("ChromaDBVectorStore", "Detected actual embedding dimension: $actualDimension")
                        
                        // Delete and recreate collection, then retry
                        try {
                            deleteCollection()
                            collectionId = null
                            embeddingDimension = null
                            ensureCollection()
                            
                            // Get the new collection ID after recreation
                            val newCollectionId = collectionId
                                ?: throw ChromaDBException("Collection ID not available after recreation")
                            
                            // Retry the upsert
                            val retryRequest = AddRequest(
                                ids = ids,
                                embeddings = embeddings,
                                documents = documents,
                                metadatas = metadatas,
                                uris = null
                            )
                            
                            val retryAddUrl = "$collectionsBasePath/$newCollectionId/add"
                            val retryResponse = client.post(retryAddUrl) {
                                contentType(ContentType.Application.Json)
                                setBody(retryRequest)
                            }
                            
                            if (retryResponse.status == HttpStatusCode.OK || retryResponse.status == HttpStatusCode.Created) {
                                AppLogger.i("ChromaDBVectorStore", "Successfully upserted after recreating collection with dimension $actualDimension")
                                return@withContext
                            } else {
                                val retryErrorBody = retryResponse.body<String>()
                                AppLogger.e("ChromaDBVectorStore", "Retry failed after recreating collection: ${retryResponse.status} - $retryErrorBody")
                                throw ChromaDBException("Failed to upsert chunks after recreating collection: ${retryResponse.status} - $retryErrorBody")
                            }
                        } catch (recreateException: Exception) {
                            AppLogger.e("ChromaDBVectorStore", "Failed to recreate collection: ${recreateException.message}", recreateException)
                            throw ChromaDBException("Failed to upsert chunks: dimension mismatch and recreation failed - ${recreateException.message}", recreateException)
                        }
                    } else {
                        AppLogger.e("ChromaDBVectorStore", "Cannot determine embedding dimension from chunks")
                        throw ChromaDBException("Failed to upsert chunks: dimension mismatch - ${response.status} - $errorBody")
                    }
                } else {
                    AppLogger.e("ChromaDBVectorStore", "Failed to upsert chunks with metadata: ${response.status} - $errorBody")
                    throw ChromaDBException("Failed to upsert chunks: ${response.status} - $errorBody")
                }
            }
            
            AppLogger.i("ChromaDBVectorStore", "Upserted ${chunks.size} chunks with metadata into collection '$collectionName'")
        } catch (e: Exception) {
            AppLogger.e("ChromaDBVectorStore", "Failed to upsert ${chunks.size} chunks with metadata", e)
            throw ChromaDBException("Failed to upsert chunks with metadata: ${e.message}", e)
        }
    }
    
    override suspend fun deleteByFilePath(filePath: String) = withContext(Dispatchers.IO) {
        try {
            ensureCollection()
            
            // ChromaDB v2 requires collection ID (UUID) for delete endpoint
            // If we don't have the ID yet, try to get it
            var idToUse = collectionId
            if (idToUse == null) {
                try {
                    val getResponse = client.get("$collectionsBasePath/$collectionName")
                    if (getResponse.status == HttpStatusCode.OK) {
                        val collectionResponse: CollectionResponse = getResponse.body()
                        idToUse = collectionResponse.id
                        collectionId = idToUse
                    }
                } catch (e: Exception) {
                    AppLogger.w("ChromaDBVectorStore", "Could not get collection ID for delete: ${e.message}")
                }
            }
            
            // If we still don't have an ID, skip deletion (best effort)
            if (idToUse == null) {
                AppLogger.w("ChromaDBVectorStore", "Skipping delete for $filePath - collection ID not available")
                return@withContext
            }
            
            // Create JsonObject for where clause
            val whereJson = buildJsonObject {
                put("filePath", JsonPrimitive(filePath))
            }
            
            val request = DeleteRequest(
                ids = null,
                where = whereJson,
                where_document = null
            )
            
            val response = client.post("$collectionsBasePath/$idToUse/delete") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (response.status != HttpStatusCode.OK) {
                val errorBody = response.body<String>()
                // Log warning but don't throw - deletion is best effort for cleanup
                AppLogger.w("ChromaDBVectorStore", "Failed to delete chunks for file $filePath: ${response.status} - $errorBody")
                return@withContext
            }
            
            AppLogger.d("ChromaDBVectorStore", "Deleted chunks for file: $filePath")
        } catch (e: Exception) {
            // Log warning but don't throw - deletion is best effort for cleanup
            AppLogger.w("ChromaDBVectorStore", "Failed to delete chunks for file: $filePath - ${e.message}")
            // Don't throw exception - this is just cleanup before re-indexing
        }
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
                    val getResponse = client.get("$collectionsBasePath/$collectionName")
                    if (getResponse.status == HttpStatusCode.OK) {
                        val collectionResponse: CollectionResponse = getResponse.body()
                        idToUse = collectionResponse.id
                        collectionId = idToUse
                    }
                } catch (e: Exception) {
                    AppLogger.w("ChromaDBVectorStore", "Could not get collection ID for delete: ${e.message}")
                }
            }
            
            if (idToUse == null) {
                AppLogger.w("ChromaDBVectorStore", "Skipping delete for $filePath in vault $vaultPath - collection ID not available")
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
                setBody(request)
            }
            
            if (response.status != HttpStatusCode.OK) {
                val errorBody = response.body<String>()
                AppLogger.w("ChromaDBVectorStore", "Failed to delete chunks for file $filePath in vault $vaultPath: ${response.status} - $errorBody")
                return@withContext
            }
            
            AppLogger.d("ChromaDBVectorStore", "Deleted chunks for file: $filePath in vault: $vaultPath")
        } catch (e: Exception) {
            AppLogger.w("ChromaDBVectorStore", "Failed to delete chunks for file $filePath in vault $vaultPath: ${e.message}")
        }
    }
    
    /**
     * Gets the embedding dimension for the collection.
     * Tries to detect from existing documents, tries common dimensions (768, 1024) if detection fails.
     */
    private suspend fun getEmbeddingDimension(collectionIdToUse: String): Int = withContext(Dispatchers.IO) {
        // #region agent log
        try {
            java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("""{"sessionId":"debug-session","runId":"run1","hypothesisId":"F","location":"ChromaDBVectorStore.kt:getEmbeddingDimension","message":"getEmbeddingDimension entry","data":{"cachedDimension":"$embeddingDimension","collectionIdToUse":"$collectionIdToUse"},"timestamp":${System.currentTimeMillis()}}
""")
        } catch (e: Exception) {}
        // #endregion
        
        // Return cached dimension if available
        if (embeddingDimension != null) {
            // #region agent log
            try {
                java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("""{"sessionId":"debug-session","runId":"run1","hypothesisId":"F","location":"ChromaDBVectorStore.kt:getEmbeddingDimension","message":"Using cached dimension","data":{"dimension":$embeddingDimension},"timestamp":${System.currentTimeMillis()}}
""")
            } catch (e: Exception) {}
            // #endregion
            embeddingDimension?.let { return@withContext it }
        }
        
        // Try common dimensions: 768 (nomic-embed-text) and 1024 (mxbai-embed-large)
        val dimensionsToTry = listOf(768, 1024)
        var detectedDimension: Int? = null
        
        // First, try to get dimension from an existing document in the collection
        for (dim in dimensionsToTry) {
            try {
                // #region agent log
                try {
                    java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("""{"sessionId":"debug-session","runId":"run1","hypothesisId":"F","location":"ChromaDBVectorStore.kt:getEmbeddingDimension","message":"Trying to detect dimension","data":{"tryingDimension":$dim},"timestamp":${System.currentTimeMillis()}}
""")
                } catch (e: Exception) {}
                // #endregion
                
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
                    setBody(request)
                }
                
                if (response.status == HttpStatusCode.OK) {
                    val queryResponse: QueryResponse = response.body()
                    // Check if we got embeddings back
                    val embeddings = queryResponse.embeddings?.get(0)
                    if (embeddings != null && embeddings.isNotEmpty() && embeddings[0].isNotEmpty()) {
                        detectedDimension = embeddings[0].size
                        // #region agent log
                        try {
                            java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("""{"sessionId":"debug-session","runId":"run1","hypothesisId":"F","location":"ChromaDBVectorStore.kt:getEmbeddingDimension","message":"Detected dimension from embeddings","data":{"detectedDimension":$detectedDimension},"timestamp":${System.currentTimeMillis()}}
""")
                        } catch (e: Exception) {}
                        // #endregion
                        AppLogger.d("ChromaDBVectorStore", "Detected embedding dimension from collection: $detectedDimension")
                        break
                    } else {
                        // Query succeeded but no embeddings returned - collection might be empty
                        // Try the dimension we used for the query as it worked
                        detectedDimension = dim
                        // #region agent log
                        try {
                            java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("""{"sessionId":"debug-session","runId":"run1","hypothesisId":"F","location":"ChromaDBVectorStore.kt:getEmbeddingDimension","message":"Query succeeded with dimension, using it","data":{"dimension":$dim},"timestamp":${System.currentTimeMillis()}}
""")
                        } catch (e: Exception) {}
                        // #endregion
                        AppLogger.d("ChromaDBVectorStore", "Query succeeded with dimension $dim, collection may be empty. Using dimension $dim.")
                        break
                    }
                } else if (response.status == HttpStatusCode.UnprocessableEntity || response.status == HttpStatusCode.BadRequest) {
                    // Dimension mismatch - try next dimension
                    val errorBody = response.body<String>()
                    // #region agent log
                    try {
                        java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("""{"sessionId":"debug-session","runId":"run1","hypothesisId":"F","location":"ChromaDBVectorStore.kt:getEmbeddingDimension","message":"Dimension mismatch, trying next","data":{"triedDimension":$dim,"status":"${response.status}","error":"$errorBody"},"timestamp":${System.currentTimeMillis()}}
""")
                    } catch (e: Exception) {}
                    // #endregion
                    continue // Try next dimension
                }
            } catch (e: Exception) {
                // #region agent log
                try {
                    java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("""{"sessionId":"debug-session","runId":"run1","hypothesisId":"F","location":"ChromaDBVectorStore.kt:getEmbeddingDimension","message":"Exception trying dimension","data":{"triedDimension":$dim,"error":"${e.message}"},"timestamp":${System.currentTimeMillis()}}
""")
                } catch (ex: Exception) {}
                // #endregion
                continue // Try next dimension
            }
        }
        
        // If we couldn't detect, default to 1024 (mxbai-embed-large is now the default)
        val finalDimension = detectedDimension ?: 1024
        
        // #region agent log
        try {
            java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log").appendText("""{"sessionId":"debug-session","runId":"run1","hypothesisId":"F","location":"ChromaDBVectorStore.kt:getEmbeddingDimension","message":"Final dimension determined","data":{"finalDimension":$finalDimension,"detectedDimension":"$detectedDimension"},"timestamp":${System.currentTimeMillis()}}
""")
        } catch (e: Exception) {}
        // #endregion
        
        if (detectedDimension == null) {
            AppLogger.w("ChromaDBVectorStore", "Could not detect embedding dimension, defaulting to 1024 (mxbai-embed-large)")
        }
        
        // Cache and return the dimension
        embeddingDimension = finalDimension
        return@withContext finalDimension
    }
    
    /**
     * Checks if ChromaDB has any data for the specified vault.
     * 
     * @param vaultPath Absolute path of the vault
     * @return true if any documents exist for this vault, false otherwise
     */
    override suspend fun hasVaultData(vaultPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            AppLogger.i("ChromaDBVectorStore", "═══════════════════════════════════════════════════════════")
            AppLogger.i("ChromaDBVectorStore", "hasVaultData() called for vault: $vaultPath")
            AppLogger.i("ChromaDBVectorStore", "Endpoint: POST $collectionsBasePath/{collectionId}/query")
            
            ensureCollection()
            
            var idToUse = collectionId
            if (idToUse == null) {
                AppLogger.w("ChromaDBVectorStore", "Collection ID is null after ensureCollection(), attempting direct lookup")
                AppLogger.i("ChromaDBVectorStore", "Endpoint: GET $collectionsBasePath/$collectionName")
                try {
                    val getResponse = client.get("$collectionsBasePath/$collectionName")
                    AppLogger.i("ChromaDBVectorStore", "GET response status: ${getResponse.status}")
                    if (getResponse.status == HttpStatusCode.OK) {
                        val collectionResponse: CollectionResponse = getResponse.body()
                        idToUse = collectionResponse.id
                        collectionId = idToUse
                        AppLogger.i("ChromaDBVectorStore", "Retrieved collection ID via direct lookup: $idToUse")
                    } else {
                        AppLogger.w("ChromaDBVectorStore", "Collection '$collectionName' not found (status: ${getResponse.status})")
                    }
                } catch (e: Exception) {
                    AppLogger.w("ChromaDBVectorStore", "Could not get collection ID for hasVaultData: ${e.message}", e)
                    AppLogger.i("ChromaDBVectorStore", "Result: false (collection ID lookup failed)")
                    AppLogger.i("ChromaDBVectorStore", "═══════════════════════════════════════════════════════════")
                    return@withContext false
                }
            }
            
            if (idToUse == null) {
                AppLogger.i("ChromaDBVectorStore", "Result: false (collection ID is null)")
                AppLogger.i("ChromaDBVectorStore", "═══════════════════════════════════════════════════════════")
                return@withContext false
            }
            
            AppLogger.i("ChromaDBVectorStore", "Collection ID: $idToUse")
            
            // Get the embedding dimension (will detect or use default)
            val dimension = getEmbeddingDimension(idToUse)
            AppLogger.i("ChromaDBVectorStore", "Using embedding dimension: $dimension")
            
            // Query with where clause to check if any documents exist for this vault
            // Use a dummy embedding (zero vector) with limit=1 to minimize data transfer
            val dummyEmbedding = FloatArray(dimension) { 0f }
            
            val whereJson = buildJsonObject {
                put("vault_path", JsonPrimitive(vaultPath))
            }
            
            AppLogger.i("ChromaDBVectorStore", "Query where clause: vault_path = '$vaultPath'")
            
            val request = QueryRequest(
                query_embeddings = listOf(dummyEmbedding.toList()),
                n_results = 1, // Only need to know if at least one exists
                where = whereJson,
                where_document = null,
                ids = null,
                include = listOf("metadatas") // Use metadatas (ids are always returned by default)
            )
            
            val queryUrl = "$collectionsBasePath/$idToUse/query"
            AppLogger.i("ChromaDBVectorStore", "POST $queryUrl")
            AppLogger.i("ChromaDBVectorStore", "Request: n_results=1, include=[metadatas], where={vault_path: '$vaultPath'}")
            
            val response = client.post(queryUrl) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            AppLogger.i("ChromaDBVectorStore", "Response status: ${response.status}")
            
            if (response.status != HttpStatusCode.OK) {
                val errorBody = try { response.body<String>() } catch (e: Exception) { "Unable to read error body" }
                AppLogger.e("ChromaDBVectorStore", "Query failed with status ${response.status}")
                AppLogger.e("ChromaDBVectorStore", "Error body: $errorBody")
                // If it's a 422 error, it might be due to dimension mismatch - clear cache and retry with detected dimension
                if (response.status == HttpStatusCode.UnprocessableEntity && embeddingDimension == 768) {
                    AppLogger.w("ChromaDBVectorStore", "422 error detected, clearing embedding dimension cache to force re-detection")
                    embeddingDimension = null
                }
                AppLogger.i("ChromaDBVectorStore", "Result: false (query failed)")
                AppLogger.i("ChromaDBVectorStore", "═══════════════════════════════════════════════════════════")
                return@withContext false
            }
            
            val queryResponse: QueryResponse = response.body()
            // If we got any IDs back, the vault has data
            val idsCount = queryResponse.ids?.get(0)?.size ?: 0
            val hasData = queryResponse.ids != null && 
                         queryResponse.ids.isNotEmpty() && 
                         queryResponse.ids[0].isNotEmpty()
            
            AppLogger.i("ChromaDBVectorStore", "Query returned ${idsCount} document IDs")
            AppLogger.i("ChromaDBVectorStore", "Result: $hasData")
            AppLogger.i("ChromaDBVectorStore", "═══════════════════════════════════════════════════════════")
            return@withContext hasData
        } catch (e: Exception) {
            AppLogger.e("ChromaDBVectorStore", "Exception in hasVaultData: ${e.message}", e)
            AppLogger.i("ChromaDBVectorStore", "Result: false (exception)")
            AppLogger.i("ChromaDBVectorStore", "═══════════════════════════════════════════════════════════")
            return@withContext false
        }
    }
    
    /**
     * Deletes all chunks for a specific file within a vault.
     * 
     * @param vaultPath Absolute path of the vault
     * @param filePath Relative file path within the vault
     */
    suspend fun deleteByVaultAndFile(vaultPath: String, filePath: String) = withContext(Dispatchers.IO) {
        // Use the existing deleteByFilePath method that takes both parameters
        deleteByFilePath(vaultPath, filePath)
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
                    val getResponse = client.get("$collectionsBasePath/$collectionName")
                    if (getResponse.status == HttpStatusCode.OK) {
                        val collectionResponse: CollectionResponse = getResponse.body()
                        idToUse = collectionResponse.id
                        collectionId = idToUse
                    }
                } catch (e: Exception) {
                    AppLogger.w("ChromaDBVectorStore", "Could not get collection ID for clearVault: ${e.message}")
                }
            }
            
            if (idToUse == null) {
                AppLogger.w("ChromaDBVectorStore", "Skipping clearVault for $vaultPath - collection ID not available")
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
                setBody(request)
            }
            
            if (response.status != HttpStatusCode.OK) {
                val errorBody = response.body<String>()
                AppLogger.w("ChromaDBVectorStore", "Failed to clear vault $vaultPath: ${response.status} - $errorBody")
                return@withContext
            }
            
            AppLogger.d("ChromaDBVectorStore", "Cleared all vectors for vault: $vaultPath")
        } catch (e: Exception) {
            AppLogger.e("ChromaDBVectorStore", "Failed to clear vault $vaultPath", e)
        }
    }
    
    /**
     * Checks the health of the ChromaDB connection and collection.
     * 
     * @return true if healthy, false otherwise
     */
    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check healthcheck endpoint
            val healthcheckResponse = client.get("$baseUrl/api/v2/healthcheck")
            if (healthcheckResponse.status != HttpStatusCode.OK) {
                return@withContext false
            }
            
            // Check if collection exists
            val collectionResponse = client.get("$collectionsBasePath/$collectionName")
            return@withContext collectionResponse.status == HttpStatusCode.OK || 
                    collectionResponse.status == HttpStatusCode.NotFound // NotFound is OK, will be created
        } catch (e: Exception) {
            AppLogger.e("ChromaDBVectorStore", "Health check failed: ${e.message}", e)
            return@withContext false
        }
    }
    
    /**
     * Gets all file paths that are indexed in the collection.
     * 
     * @return List of unique file paths
     */
    suspend fun getFilesInCollection(): List<String> = withContext(Dispatchers.IO) {
        try {
            ensureCollection()
            
            val collectionIdToUse = collectionId
                ?: throw ChromaDBException("Collection ID not available")
            
            // Get the embedding dimension (will detect or use default)
            val dimension = getEmbeddingDimension(collectionIdToUse)
            
            // Query with a dummy embedding to get all documents
            // We'll use a zero vector to get all results
            val zeroVector = FloatArray(dimension) { 0f }
            
            val request = QueryRequest(
                query_embeddings = listOf(zeroVector.toList()),
                n_results = 10000, // Large number to get all files
                where = null,
                where_document = null,
                ids = null,
                include = listOf("metadatas")
            )
            
            val response = client.post("$collectionsBasePath/$collectionIdToUse/query") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (response.status != HttpStatusCode.OK) {
                return@withContext emptyList()
            }
            
            val queryResponse: QueryResponse = response.body()
            val filePaths = mutableSetOf<String>()
            
            queryResponse.metadatas?.get(0)?.forEach { metadata ->
                val filePath = (metadata["filePath"] as? JsonPrimitive)?.content
                if (filePath != null) {
                    filePaths.add(filePath)
                }
            }
            
            return@withContext filePaths.toList()
        } catch (e: Exception) {
            AppLogger.e("ChromaDBVectorStore", "Failed to get files in collection: ${e.message}", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Closes the HTTP client and releases resources.
     */
    fun close() {
        client.close()
    }
}

/**
 * Exception thrown when ChromaDB operations fail.
 */
class ChromaDBException(message: String, cause: Throwable? = null) : Exception(message, cause)

