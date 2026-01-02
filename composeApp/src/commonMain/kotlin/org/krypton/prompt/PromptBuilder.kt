package org.krypton.prompt

import org.krypton.chat.ChatMessage

/**
 * Builds prompts for LLM generation based on retrieval context and conversation history.
 * 
 * Creates appropriate system prompts and formats context based on the retrieval mode.
 * This interface is platform-independent and should be implemented in platform-specific code.
 */
interface PromptBuilder {
    /**
     * Builds a prompt string from the given context and conversation history.
     * 
     * @param systemInstructions System instructions for the LLM
     * @param conversationHistory List of previous messages in the conversation (old ChatMessage format)
     * @param localChunks Chunks from local notes (RAG)
     * @param webSnippets Snippets from web search
     * @param userMessage The current user message
     * @return The formatted prompt string ready for LLM generation
     */
    fun buildPrompt(
        systemInstructions: String,
        conversationHistory: List<ChatMessage>,
        localChunks: List<org.krypton.rag.RagChunk>,
        webSnippets: List<org.krypton.web.WebSnippet>,
        userMessage: String,
    ): String
    
    /**
     * Legacy method for backward compatibility.
     * 
     * @param ctx The prompt context containing query, retrieval mode, and retrieved chunks/snippets
     * @return The formatted prompt string ready for LLM generation
     * @deprecated Use buildPrompt with conversation history instead
     */
    @Deprecated("Use buildPrompt with conversation history", ReplaceWith("buildPrompt(systemInstructions, emptyList(), ctx.localChunks, ctx.webSnippets, ctx.query)"))
    fun buildPrompt(ctx: PromptContext): String {
        // Default implementation for backward compatibility
        return buildPrompt(
            systemInstructions = "",
            conversationHistory = emptyList(),
            localChunks = ctx.localChunks,
            webSnippets = ctx.webSnippets,
            userMessage = ctx.query
        )
    }
}

