package org.krypton.prompt

/**
 * Builds prompts for LLM generation based on retrieval context.
 * 
 * Creates appropriate system prompts and formats context based on the retrieval mode.
 * This interface is platform-independent and should be implemented in platform-specific code.
 */
interface PromptBuilder {
    /**
     * Builds a prompt string from the given context.
     * 
     * @param ctx The prompt context containing query, retrieval mode, and retrieved chunks/snippets
     * @return The formatted prompt string ready for LLM generation
     */
    fun buildPrompt(ctx: PromptContext): String
}

