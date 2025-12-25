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
    
    /**
     * Generates a text completion from a prompt with specified model and temperature.
     * 
     * @param model The model name to use
     * @param prompt The input prompt
     * @param temperature Temperature for sampling (0.0 = deterministic, higher = more random)
     * @return The generated text response
     */
    suspend fun complete(model: String, prompt: String, temperature: Double = 0.0): String
}

