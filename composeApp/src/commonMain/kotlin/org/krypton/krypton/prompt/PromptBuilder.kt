package org.krypton.krypton.prompt

/**
 * Builds prompts for LLM generation based on retrieval context.
 * 
 * Creates appropriate system prompts and formats context
 * based on the retrieval mode (NONE, RAG, WEB, HYBRID).
 */
interface PromptBuilder {
    /**
     * Builds a prompt string from the given context.
     * 
     * @param ctx The prompt context with query, mode, and retrieved content
     * @return The formatted prompt string ready for LLM generation
     */
    fun build(ctx: PromptContext): String
}

