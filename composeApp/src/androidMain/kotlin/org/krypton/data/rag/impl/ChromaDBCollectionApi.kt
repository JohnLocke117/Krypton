package org.krypton.data.rag.impl

import kotlinx.serialization.Serializable

/**
 * ChromaDB collection API request/response models.
 * 
 * These DTOs are used for collection creation, retrieval, and deletion operations.
 */

@Serializable
internal data class CreateCollectionRequest(
    val name: String,
    val get_or_create: Boolean = false,
    val metadata: Map<String, String>? = null,
    val configuration: Map<String, String>? = null,
    val schema: Map<String, String>? = null
)

@Serializable
internal data class CollectionResponse(
    val id: String,
    val name: String,
    val metadata: Map<String, String>? = null
)

@Serializable
internal data class DeleteCollectionRequest(
    val new_name: String? = null,
    val new_metadata: Map<String, String>? = null,
    val new_configuration: Map<String, String>? = null
)

