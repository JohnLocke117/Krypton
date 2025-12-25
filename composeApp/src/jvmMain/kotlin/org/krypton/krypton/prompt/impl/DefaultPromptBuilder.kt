package org.krypton.krypton.prompt.impl

import org.krypton.krypton.chat.RetrievalMode
import org.krypton.krypton.prompt.PromptBuilder
import org.krypton.krypton.prompt.PromptContext

/**
 * Default implementation of PromptBuilder.
 * 
 * Creates mode-specific prompts with appropriate system instructions
 * and formatted context from local chunks and/or web snippets.
 */
class DefaultPromptBuilder : PromptBuilder {
    
    override fun build(ctx: PromptContext): String {
        return when (ctx.retrievalMode) {
            RetrievalMode.NONE -> buildNonePrompt(ctx.query)
            RetrievalMode.RAG -> buildRagPrompt(ctx.query, ctx.localChunks)
            RetrievalMode.WEB -> buildWebPrompt(ctx.query, ctx.webSnippets)
            RetrievalMode.HYBRID -> buildHybridPrompt(ctx.query, ctx.localChunks, ctx.webSnippets)
        }
    }
    
    /**
     * Builds a prompt for NONE mode (plain chat).
     */
    private fun buildNonePrompt(query: String): String {
        return """You are a helpful assistant. Answer the user's question.

Question: $query"""
    }
    
    /**
     * Builds a prompt for RAG mode (local notes only).
     */
    private fun buildRagPrompt(query: String, chunks: List<org.krypton.krypton.rag.RetrievedChunk>): String {
        val systemPrompt = """You are a helpful assistant. Use ONLY the following note excerpts to answer. If the answer is not in the notes, say you don't know.

Rules:
- Only use information from the provided context
- If the answer is not in the context, explicitly say so
- When referencing sources, mention the note or section naturally (e.g., "According to my notes on X..." or "In the section about Y...")
- Do not mention "chunks" or "chunk numbers" in your response
- Answer naturally and conversationally, as if you're recalling information from memory
- Answer concisely and accurately"""
        
        val contextSection = if (chunks.isEmpty()) {
            "Notes: No relevant notes found.\n\n"
        } else {
            buildString {
                append("Notes:\n\n")
                chunks.forEachIndexed { index, chunk ->
                    val filePath = chunk.metadata["filePath"] ?: "unknown"
                    val sectionTitle = chunk.metadata["sectionTitle"]
                    val startLine = chunk.metadata["startLine"] ?: "?"
                    val endLine = chunk.metadata["endLine"] ?: "?"
                    
                    val sourceLabel = sectionTitle ?: filePath.substringAfterLast('/').substringBeforeLast('.')
                    
                    append("Note ${index + 1} ($sourceLabel, lines $startLine-$endLine):\n")
                    append(chunk.text)
                    append("\n\n")
                }
            }
        }
        
        return "$systemPrompt\n\n$contextSection\nQuestion: $query"
    }
    
    /**
     * Builds a prompt for WEB mode (Tavily search only).
     */
    private fun buildWebPrompt(query: String, snippets: List<org.krypton.krypton.web.WebSnippet>): String {
        val systemPrompt = """You are a helpful assistant. Use ONLY the following web search results (Tavily) to answer. Cite URLs when relevant.

Rules:
- Only use information from the provided web search results
- If the answer is not in the results, explicitly say so
- Cite URLs when referencing information from web sources
- Answer concisely and accurately"""
        
        val contextSection = if (snippets.isEmpty()) {
            "Web results (Tavily): No results found.\n\n"
        } else {
            buildString {
                append("Web results (Tavily):\n\n")
                snippets.forEachIndexed { index, snippet ->
                    append("Web ${index + 1}: ${snippet.title} — ${snippet.url}\n")
                    append(snippet.content)
                    append("\n\n")
                }
            }
        }
        
        return "$systemPrompt\n\n$contextSection\nQuestion: $query"
    }
    
    /**
     * Builds a prompt for HYBRID mode (RAG + web search).
     */
    private fun buildHybridPrompt(
        query: String,
        chunks: List<org.krypton.krypton.rag.RetrievedChunk>,
        snippets: List<org.krypton.krypton.web.WebSnippet>
    ): String {
        val systemPrompt = """You are a helpful assistant. Prefer the user's notes when they conflict with the web. Use both the note excerpts and web search results.

Rules:
- Prefer information from notes when there's a conflict with web results
- Use both sources to provide a comprehensive answer
- When referencing sources, mention whether information came from "notes" or "web"
- If the answer is not in either source, explicitly say so
- Cite URLs when referencing web sources
- Answer naturally and conversationally
- Answer concisely and accurately"""
        
        val notesSection = if (chunks.isEmpty()) {
            "Notes: No relevant notes found.\n\n"
        } else {
            buildString {
                append("Notes:\n\n")
                chunks.forEachIndexed { index, chunk ->
                    val filePath = chunk.metadata["filePath"] ?: "unknown"
                    val sectionTitle = chunk.metadata["sectionTitle"]
                    val startLine = chunk.metadata["startLine"] ?: "?"
                    val endLine = chunk.metadata["endLine"] ?: "?"
                    
                    val sourceLabel = sectionTitle ?: filePath.substringAfterLast('/').substringBeforeLast('.')
                    
                    append("Note ${index + 1} ($sourceLabel, lines $startLine-$endLine):\n")
                    append(chunk.text)
                    append("\n\n")
                }
            }
        }
        
        val webSection = if (snippets.isEmpty()) {
            "Web results (Tavily): No results found.\n\n"
        } else {
            buildString {
                append("Web results (Tavily):\n\n")
                snippets.forEachIndexed { index, snippet ->
                    append("Web ${index + 1}: ${snippet.title} — ${snippet.url}\n")
                    append(snippet.content)
                    append("\n\n")
                }
            }
        }
        
        return "$systemPrompt\n\n$notesSection\n$webSection\nQuestion: $query"
    }
}

