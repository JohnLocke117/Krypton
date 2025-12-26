package org.krypton.prompt.impl

import org.krypton.chat.RetrievalMode
import org.krypton.prompt.PromptBuilder
import org.krypton.prompt.PromptContext

/**
 * Default implementation of PromptBuilder.
 * 
 * Creates mode-specific prompts with appropriate system instructions
 * and formatted context from local chunks and/or web snippets.
 */
class DefaultPromptBuilder : PromptBuilder {
    
    override fun buildPrompt(ctx: PromptContext): String {
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

RESPONSE FORMAT (STRICT):
1. First, provide a clear, well-structured answer in Markdown. Use headings, bullet points, and code blocks when helpful.
2. After ALL your content, add a divider line: `---`
3. After the divider, add a top-level heading exactly named:

## Sources

4. Under "## Sources", since no context was provided, write:

## Sources
- None (no relevant context provided)

IMPORTANT:
- The Sources section must appear ONLY ONCE, at the very end of your response
- It must come after ALL content and after the divider line
- Do not include sources anywhere else in your response
- Do not output JSON

Question: $query"""
    }
    
    /**
     * Builds a prompt for RAG mode (local notes only).
     */
    private fun buildRagPrompt(query: String, chunks: List<org.krypton.rag.RagChunk>): String {
        val systemPrompt = """You are a helpful assistant. Use ONLY the following note excerpts to answer. If the answer is not in the notes, say you don't know.

Rules:
- Only use information from the provided context
- If the answer is not in the context, explicitly say so
- When referencing sources, mention the note or section naturally (e.g., "According to my notes on X..." or "In the section about Y...")
- Do not mention "chunks" or "chunk numbers" in your response
- Answer naturally and conversationally, as if you're recalling information from memory
- Answer concisely and accurately

RESPONSE FORMAT (STRICT):
1. First, provide a clear, well-structured answer in Markdown. Use headings, bullet points, and code blocks when helpful.
2. After ALL your content, add a divider line: `---`
3. After the divider, add a top-level heading exactly named:

## Sources

4. Under "## Sources", list each source you actually used, in this format:
   - `- Note: "[short label]" — ID: <source_id>`
   - Include the file path when available.
   - Only list sources that actually influenced the answer.
   - Do NOT invent IDs or paths.
   - Use each source at most once in the list.

Examples of source lines:
- `- Note: "RAG Pipeline Overview" — ID: file.md:10:20`
- `- Note: "Kotlin RAG Architecture" — ID: /notes/kotlin/rag.md:5:15`

If NO useful context is available, write:

## Sources
- None (no relevant context provided)

IMPORTANT:
- The Sources section must appear ONLY ONCE, at the very end of your response
- It must come after ALL content and after the divider line
- Do not include sources anywhere else in your response
- Do not output JSON"""
        
        val contextSection = if (chunks.isEmpty()) {
            "Notes: No relevant notes found.\n\n"
        } else {
            buildString {
                append("Notes:\n\n")
                chunks.forEach { chunk ->
                    val filePath = chunk.metadata["filePath"] ?: "unknown"
                    val sectionTitle = chunk.metadata["sectionTitle"]
                    val startLine = chunk.metadata["startLine"] ?: "?"
                    val endLine = chunk.metadata["endLine"] ?: "?"
                    
                    val sourceLabel = sectionTitle ?: filePath.substringAfterLast('/').substringBeforeLast('.')
                    val chunkId = chunk.id
                    
                    append("Note ID: $chunkId — $sourceLabel (lines $startLine-$endLine):\n")
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
    private fun buildWebPrompt(query: String, snippets: List<org.krypton.web.WebSnippet>): String {
        val systemPrompt = """You are a helpful assistant. Use ONLY the following web search results (Tavily) to answer. Cite URLs when relevant.

Rules:
- Only use information from the provided web search results
- If the answer is not in the results, explicitly say so
- Cite URLs when referencing information from web sources
- Answer concisely and accurately

RESPONSE FORMAT (STRICT):
1. First, provide a clear, well-structured answer in Markdown. Use headings, bullet points, and code blocks when helpful.
2. After ALL your content, add a divider line: `---`
3. After the divider, add a top-level heading exactly named:

## Sources

4. Under "## Sources", list each source you actually used, in this format:
   - `- Web: "[short label]" — ID: <source_id>`
   - Include the URL in Markdown link form when available.
   - Only list sources that actually influenced the answer.
   - Do NOT invent IDs or URLs.
   - Use each source at most once in the list.

Examples of source lines:
- `- Web: [Tavily docs](https://example.com/tavily) — ID: web_1`
- `- Web: [Kotlin Guide](https://kotlinlang.org/docs/) — ID: web_2`

If NO useful context is available, write:

## Sources
- None (no relevant context provided)

IMPORTANT:
- The Sources section must appear ONLY ONCE, at the very end of your response
- It must come after ALL content and after the divider line
- Do not include sources anywhere else in your response
- Do not output JSON"""
        
        val contextSection = if (snippets.isEmpty()) {
            "Web results (Tavily): No results found.\n\n"
        } else {
            buildString {
                append("Web results (Tavily):\n\n")
                snippets.forEachIndexed { index, snippet ->
                    val webId = "web_${index + 1}"
                    append("Web ID: $webId — ${snippet.title} — ${snippet.url}\n")
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
        chunks: List<org.krypton.rag.RagChunk>,
        snippets: List<org.krypton.web.WebSnippet>
    ): String {
        val systemPrompt = """You are a helpful assistant. Prefer the user's notes when they conflict with the web. Use both the note excerpts and web search results.

Rules:
- Prefer information from notes when there's a conflict with web results
- Use both sources to provide a comprehensive answer
- When referencing sources, mention whether information came from "notes" or "web"
- If the answer is not in either source, explicitly say so
- Cite URLs when referencing web sources
- Answer naturally and conversationally
- Answer concisely and accurately

RESPONSE FORMAT (STRICT):
1. First, provide a clear, well-structured answer in Markdown. Use headings, bullet points, and code blocks when helpful.
2. After ALL your content, add a divider line: `---`
3. After the divider, add a top-level heading exactly named:

## Sources

4. Under "## Sources", list each source you actually used, in this format:
   - For notes: `- Note: "[short label]" — ID: <source_id>`
   - For web: `- Web: "[short label]" — ID: <source_id>`
   - Include URLs for web sources in Markdown link form when available.
   - Include file paths for notes when available.
   - Only list sources that actually influenced the answer.
   - Do NOT invent IDs, URLs, or paths.
   - Use each source at most once in the list.

Examples of source lines:
- `- Note: "RAG Pipeline Overview" — ID: file.md:10:20`
- `- Web: [Tavily docs](https://example.com/tavily) — ID: web_1`
- `- File: /notes/kotlin/rag.md — ID: note_7`

If NO useful context is available, write:

## Sources
- None (no relevant context provided)

IMPORTANT:
- The Sources section must appear ONLY ONCE, at the very end of your response
- It must come after ALL content and after the divider line
- Do not include sources anywhere else in your response
- Do not output JSON"""
        
        val notesSection = if (chunks.isEmpty()) {
            "Notes: No relevant notes found.\n\n"
        } else {
            buildString {
                append("Notes:\n\n")
                chunks.forEach { chunk ->
                    val filePath = chunk.metadata["filePath"] ?: "unknown"
                    val sectionTitle = chunk.metadata["sectionTitle"]
                    val startLine = chunk.metadata["startLine"] ?: "?"
                    val endLine = chunk.metadata["endLine"] ?: "?"
                    
                    val sourceLabel = sectionTitle ?: filePath.substringAfterLast('/').substringBeforeLast('.')
                    val chunkId = chunk.id
                    
                    append("Note ID: $chunkId — $sourceLabel (lines $startLine-$endLine):\n")
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
                    val webId = "web_${index + 1}"
                    append("Web ID: $webId — ${snippet.title} — ${snippet.url}\n")
                    append(snippet.content)
                    append("\n\n")
                }
            }
        }
        
        return "$systemPrompt\n\n$notesSection\n$webSection\nQuestion: $query"
    }
}

