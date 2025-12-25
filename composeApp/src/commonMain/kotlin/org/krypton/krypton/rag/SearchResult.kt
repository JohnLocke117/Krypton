package org.krypton.krypton.rag

/**
 * Result of a vector search with similarity score.
 * 
 * @param chunk The retrieved chunk
 * @param similarity Similarity score (0.0 to 1.0, higher is more similar)
 */
data class SearchResult(
    val chunk: NoteChunk,
    val similarity: Float
)

