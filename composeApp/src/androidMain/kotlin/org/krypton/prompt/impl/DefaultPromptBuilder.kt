package org.krypton.prompt.impl

import org.krypton.chat.ChatMessage
import org.krypton.chat.ChatRole
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
    
    override fun buildPrompt(
        systemInstructions: String,
        conversationHistory: List<ChatMessage>,
        localChunks: List<org.krypton.rag.RagChunk>,
        webSnippets: List<org.krypton.web.WebSnippet>,
        userMessage: String,
    ): String {
        // Determine retrieval mode from provided chunks/snippets
        val retrievalMode = when {
            localChunks.isNotEmpty() && webSnippets.isNotEmpty() -> RetrievalMode.HYBRID
            localChunks.isNotEmpty() -> RetrievalMode.RAG
            webSnippets.isNotEmpty() -> RetrievalMode.WEB
            else -> RetrievalMode.NONE
        }
        
        // Format conversation history as chat transcript
        val historyTranscript = formatConversationHistory(conversationHistory)
        
        // Build base prompt based on retrieval mode
        val basePrompt = when (retrievalMode) {
            RetrievalMode.NONE -> buildNonePrompt(userMessage, historyTranscript, systemInstructions)
            RetrievalMode.RAG -> buildRagPrompt(userMessage, localChunks, historyTranscript, systemInstructions)
            RetrievalMode.WEB -> buildWebPrompt(userMessage, webSnippets, historyTranscript, systemInstructions)
            RetrievalMode.HYBRID -> buildHybridPrompt(userMessage, localChunks, webSnippets, historyTranscript, systemInstructions)
        }
        
        return basePrompt
    }
    
    override fun buildPrompt(ctx: PromptContext): String {
        return buildPrompt(
            systemInstructions = "",
            conversationHistory = emptyList(),
            localChunks = ctx.localChunks,
            webSnippets = ctx.webSnippets,
            userMessage = ctx.query
        )
    }
    
    /**
     * Formats conversation history as a chat transcript.
     */
    private fun formatConversationHistory(history: List<ChatMessage>): String {
        if (history.isEmpty()) return ""
        
        return buildString {
            history.forEach { msg ->
                when (msg.role) {
                    ChatRole.USER -> append("User: ${msg.content}\n")
                    ChatRole.ASSISTANT -> append("Assistant: ${msg.content}\n")
                    ChatRole.SYSTEM -> append("System: ${msg.content}\n")
                }
            }
        }
    }
    
    /**
     * Builds a prompt for NONE mode (plain chat).
     */
    private fun buildNonePrompt(
        query: String,
        historyTranscript: String,
        systemInstructions: String
    ): String {
        val systemPrompt = systemInstructions.ifBlank {
            """You are a helpful assistant. Answer the user's question.

RESPONSE FORMAT (STRICT):
1. First, provide a clear, well-structured answer in Markdown. Use headings, bullet points, and code blocks when helpful.
2. After ALL your content, add a divider line: `---`
3. After the divider, add a top-level heading exactly named:

## Sources

4. Under "## Sources", since no context was provided, write:

## Sources
- None. No relevant context provided.

IMPORTANT:
- The Sources section must appear ONLY ONCE, at the very end of your response
- It must come after ALL content and after the divider line
- Do not include sources anywhere else in your response
- Do not output JSON"""
        }
        
        return if (historyTranscript.isBlank()) {
            "$systemPrompt\n\nQuestion: $query"
        } else {
            "$systemPrompt\n\nConversation History:\n$historyTranscript\n\nQuestion: $query"
        }
    }
    
    /**
     * Builds a prompt for RAG mode (local notes only).
     */
    private fun buildRagPrompt(
        query: String,
        chunks: List<org.krypton.rag.RagChunk>,
        historyTranscript: String,
        systemInstructions: String
    ): String {
        val systemPrompt = systemInstructions.ifBlank {
            """You are a helpful assistant for question answering.
Answer the question using your knowledge and the information in the NOTES.
The NOTES are supplementary context to enhance your answer, not a restriction.

INSTRUCTIONS:
- Use your own knowledge to answer the question.
- Use the NOTES as additional context when relevant.
- If the NOTES contain relevant information, incorporate it naturally into your answer.
- Answer concisely in Markdown (headings and bullet points are fine).
- Do not mention chunks, embeddings, or retrieval.
- Do NOT list the notes or sources in your answer body.

RESPONSE FORMAT (MANDATORY - YOU MUST FOLLOW THIS):
1. First, provide a clear, well-structured answer in Markdown. Use headings, bullet points, and code blocks when helpful.
2. After ALL your content, add a divider line: `---`
3. After the divider, you MUST add a top-level heading exactly named:

## Sources

4. Under "## Sources", you MUST include one of the following:
   - If you used notes: List each source in this format:
     `- Note: "[short label]" — ID: <source_id>`
     Include the file path when available. Only list sources that actually influenced the answer.
   - If you did NOT use any notes: Write exactly:
     `- None. No relevant context provided.`

CRITICAL REQUIREMENTS:
- The Sources section is MANDATORY and MUST appear at the very end of your response
- It MUST come after ALL content and after the divider line `---`
- You MUST include the Sources section even if you didn't use any notes
- Do NOT invent IDs or paths
- Do not include sources anywhere else in your response
- Do not output JSON"""
        }
        
        val contextSection = if (chunks.isEmpty()) {
            "NOTES:\nNo relevant notes found.\n\n"
        } else {
            buildString {
                append("NOTES:\n")
                chunks.forEachIndexed { index, chunk ->
                    val filePath = chunk.metadata["filePath"] ?: "unknown"
                    val sectionTitle = chunk.metadata["sectionTitle"]
                    val startLine = chunk.metadata["startLine"] ?: "?"
                    val endLine = chunk.metadata["endLine"] ?: "?"
                    
                    val sourceLabel = sectionTitle ?: filePath.substringAfterLast('/').substringBeforeLast('.')
                    val chunkId = chunk.id
                    val sourceId = "$filePath:$startLine:$endLine"
                    
                    append("[Source ID: $sourceId | Note: $sourceLabel]\n")
                    append(chunk.text)
                    append("\n\n")
                }
            }
        }
        
        val historySection = if (historyTranscript.isBlank()) {
            ""
        } else {
            "\n\nConversation History:\n$historyTranscript"
        }
        
        return "$systemPrompt$historySection\n\nQUESTION:\n$query\n\n$contextSection"
    }
    
    /**
     * Builds a prompt for WEB mode (Tavily search only).
     */
    private fun buildWebPrompt(
        query: String,
        snippets: List<org.krypton.web.WebSnippet>,
        historyTranscript: String,
        systemInstructions: String
    ): String {
        val systemPrompt = systemInstructions.ifBlank {
            """You are a helpful assistant. Use ONLY the following web search results (Tavily) to answer. Cite URLs when relevant.

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
- None. No relevant context provided.

IMPORTANT:
- The Sources section must appear ONLY ONCE, at the very end of your response
- It must come after ALL content and after the divider line
- Do not include sources anywhere else in your response
- Do not output JSON"""
        }
        
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
        
        val historySection = if (historyTranscript.isBlank()) {
            ""
        } else {
            "\n\nConversation History:\n$historyTranscript"
        }
        
        return "$systemPrompt$historySection\n\n$contextSection\nQuestion: $query"
    }
    
    /**
     * Builds a prompt for HYBRID mode (RAG + web search).
     */
    private fun buildHybridPrompt(
        query: String,
        chunks: List<org.krypton.rag.RagChunk>,
        snippets: List<org.krypton.web.WebSnippet>,
        historyTranscript: String,
        systemInstructions: String
    ): String {
        val systemPrompt = systemInstructions.ifBlank {
            """You are a helpful assistant. Prefer the user's notes when they conflict with the web. Use both the note excerpts and web search results.

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
- None. No relevant context provided.

IMPORTANT:
- The Sources section must appear ONLY ONCE, at the very end of your response
- It must come after ALL content and after the divider line
- Do not include sources anywhere else in your response
- Do not output JSON"""
        }
        
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
        
        val historySection = if (historyTranscript.isBlank()) {
            ""
        } else {
            "\n\nConversation History:\n$historyTranscript"
        }
        
        return "$systemPrompt$historySection\n\n$notesSection\n$webSection\nQuestion: $query"
    }
}
