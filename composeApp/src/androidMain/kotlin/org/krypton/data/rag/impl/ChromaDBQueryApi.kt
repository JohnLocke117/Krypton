package org.krypton.data.rag.impl

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * ChromaDB query API request/response models.
 * 
 * These DTOs are used for querying, adding, and deleting documents in collections.
 */

@Serializable
internal data class AddRequest(
    val ids: List<String>,
    val embeddings: List<List<Float>>,
    val documents: List<String>,
    @Contextual val metadatas: List<JsonObject>,
    val uris: List<String>? = null
)

@Serializable
internal data class QueryRequest(
    val query_embeddings: List<List<Float>>,
    val n_results: Int,
    @Contextual val where: JsonObject? = null,
    @Contextual val where_document: JsonObject? = null,
    val ids: List<String>? = null,
    val include: List<String> = listOf("documents", "metadatas", "distances")
)

@Serializable
internal data class QueryResponse(
    val ids: List<List<String>>? = null,
    val embeddings: List<List<List<Float>>>? = null,
    val documents: List<List<String>>? = null,
    @Contextual val metadatas: List<List<JsonObject>>? = null,
    val distances: List<List<Float>>? = null
)

@Serializable
internal data class DeleteRequest(
    val ids: List<String>? = null,
    @Contextual val where: JsonObject? = null,
    @Contextual val where_document: JsonObject? = null
)

