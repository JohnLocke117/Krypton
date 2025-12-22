package org.krypton.krypton.rag

/**
 * Interface for generating text completions using a language model.
 */
interface LlamaClient {
    /**
     * Generates a text completion from a prompt.
     * 
     * @param prompt The input prompt
     * @return The generated text response
     */
    suspend fun complete(prompt: String): String
}

