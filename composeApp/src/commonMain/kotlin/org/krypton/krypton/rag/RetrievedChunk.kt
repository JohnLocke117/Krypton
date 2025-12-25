package org.krypton.krypton.rag

/**
 * Represents a retrieved chunk with metadata for reranking.
 * 
 * @param id Unique identifier for this chunk
 * @param text The chunk text content
 * @param metadata Additional metadata (e.g., filePath, sectionTitle)
 * @param similarity Similarity score from vector search (0.0 to 1.0, higher is more similar)
 */
data class RetrievedChunk(
    val id: String,
    val text: String,
    val metadata: Map<String, String>,
    val similarity: Double
)

/**
 * Converts a SearchResult to a RetrievedChunk for reranking.
 */
fun SearchResult.toRetrievedChunk(): RetrievedChunk {
    val metadata = buildMap<String, String> {
        put("filePath", chunk.filePath)
        if (chunk.sectionTitle != null) {
            put("sectionTitle", chunk.sectionTitle)
        }
        put("startLine", chunk.startLine.toString())
        put("endLine", chunk.endLine.toString())
    }
    
    return RetrievedChunk(
        id = chunk.id,
        text = chunk.text,
        metadata = metadata,
        similarity = similarity.toDouble()
    )
}

/**
 * Converts a list of SearchResults to RetrievedChunks.
 */
fun List<SearchResult>.toRetrievedChunks(): List<RetrievedChunk> {
    return map { it.toRetrievedChunk() }
}

