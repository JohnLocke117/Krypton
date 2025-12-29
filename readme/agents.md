# Agents & Agentic Architecture

Krypton includes an intelligent agent system that enables natural language interactions for specific tasks. Agents intercept user messages before normal chat processing, detect intents, and perform structured actions.

## Overview

The agent system provides a way to handle specific user intents with dedicated, purpose-built handlers. Instead of relying solely on the LLM to interpret and execute actions, agents provide:

- **Intent Detection**: Pattern-based recognition of user requests
- **Structured Actions**: Direct execution of specific operations
- **Better UX**: Clear, formatted responses with actionable results
- **Reliability**: Deterministic behavior for common tasks

## Architecture

### Agent Flow

```
User Message
    ↓
OllamaChatService.sendMessage()
    ↓
Check Agents (in order)
    ├─→ CreateNoteAgent.tryHandle()
    ├─→ SearchNoteAgent.tryHandle()
    ├─→ SummarizeNoteAgent.tryHandle()
    └─→ (if no agent matches)
        ↓
    Normal RAG/Chat Flow
```

### Agent Interface

All agents implement the `ChatAgent` interface:

```kotlin
interface ChatAgent {
    suspend fun tryHandle(
        message: String,
        chatHistory: List<ChatMessage>,
        context: AgentContext
    ): AgentResult?
}
```

**Key Points:**
- Agents return `AgentResult?` - `null` means the agent didn't handle the message
- First agent to return non-null result wins
- Agents receive full context (vault path, current note, settings)
- Agents are checked before normal chat flow

### Agent Context

Agents receive `AgentContext` containing:

- `currentVaultPath`: Path to the currently opened vault/folder
- `settings`: Current application settings (RAG config, models, etc.)
- `currentNotePath`: Path to the currently active/open note file

This context allows agents to:
- Validate prerequisites (e.g., vault must be open)
- Access current state
- Use appropriate models and settings

### Agent Results

Agents return structured results via `AgentResult` sealed class:

- `NoteCreated`: A note was successfully created
- `NotesFound`: Search results with matching notes
- `NoteSummarized`: A summary was generated

These results are converted to formatted chat responses by `OllamaChatService`.

## Available Agents

### 1. CreateNoteAgent

**Purpose**: Creates new markdown notes based on user requests.

**Intent Patterns:**
- "create a note on [topic]"
- "create a short note on [topic]"
- "make a note about [topic]"
- "write a note on [topic]"

**How It Works:**

1. **Intent Detection**: Detects note creation patterns in the message
2. **Topic Extraction**: Extracts the topic from the message
3. **Content Generation**: Uses LLM to generate markdown content about the topic
4. **File Creation**: Creates a new `.md` file in the current vault
5. **Collision Handling**: Automatically handles filename collisions with numeric suffixes

**Example:**
```
User: "create a note on machine learning basics"

Agent:
- Detects intent: "create a note on"
- Extracts topic: "machine learning basics"
- Generates content using LLM
- Creates file: "machine-learning-basics.md"
- Returns formatted response with preview
```

**Features:**
- Automatic filename generation (slugified from topic)
- Collision detection and resolution
- Title extraction from generated content
- Preview generation for user feedback

**Requirements:**
- Vault must be open
- LLM must be available

---

### 2. SearchNoteAgent

**Purpose**: Searches for notes matching a query using both semantic and keyword search.

**Intent Patterns:**
- "find notes about [query]"
- "search my notes for [query]"
- "show me notes on [query]"
- "which notes talk about [query]"

**How It Works:**

1. **Intent Detection**: Detects search patterns in the message
2. **Query Extraction**: Extracts the search query
3. **Dual Search**:
   - **Vector Search**: Uses RAG retriever for semantic similarity
   - **Keyword Search**: Performs text-based keyword matching
4. **Result Merging**: Combines and deduplicates results
5. **Scoring**: Assigns relevance scores (vector: 70%, keyword: 30%)
6. **Formatting**: Returns formatted list with titles, snippets, and scores

**Example:**
```
User: "find notes about AWS"

Agent:
- Detects intent: "find notes about"
- Extracts query: "AWS"
- Performs vector search (semantic)
- Performs keyword search (exact matches)
- Merges results with combined scores
- Returns formatted list of matching notes
```

**Features:**
- Hybrid search (semantic + keyword)
- Relevance scoring
- Snippet extraction showing why notes matched
- Title extraction from content or filename

**Requirements:**
- Vault must be open
- RAG retriever available (for vector search, optional)
- File system access for keyword search

**Search Strategy:**
- **Vector Search**: Uses embedding similarity for semantic matching
- **Keyword Search**: Token-based matching with filename boost
- **Combined Score**: `(vectorScore * 0.7) + (keywordScore * 0.3)`

---

### 3. SummarizeNoteAgent

**Purpose**: Summarizes notes, either the current note or notes about a specific topic.

**Intent Patterns:**

**Current Note Mode:**
- "summarize this note"
- "summarize the current note"
- "give me a summary of this note"
- "what is this note about"

**Topic Mode:**
- "summarize my notes on [topic]"
- "summarize my notes about [topic]"
- "give me a summary of my notes on [topic]"
- "what do my notes say about [topic]"

**How It Works:**

#### Current Note Mode:
1. **Intent Detection**: Detects current note summarization patterns
2. **Note Validation**: Verifies current note exists and is readable
3. **Content Retrieval**: Reads the current note content
4. **Summary Generation**: Uses LLM with simple, direct prompt
5. **Response**: Returns formatted summary

#### Topic Mode:
1. **Intent Detection**: Detects topic summarization patterns
2. **Topic Extraction**: Extracts the topic from the message
3. **Retrieval Query**: Builds query "notes about [topic]"
4. **Chunk Retrieval**: Retrieves relevant chunks using RAG
5. **Relevance Filtering**: Filters chunks containing the topic keyword
6. **Chunk Truncation**: Limits chunks to 800 words each (for 8B model)
7. **Context Building**: Combines filtered, truncated chunks
8. **Summary Generation**: Uses LLM with simple prompt optimized for 8B models
9. **Response**: Returns formatted summary with source files

**Example:**
```
User: "summarize my notes on AWS"

Agent:
- Detects intent: "summarize my notes on"
- Extracts topic: "AWS"
- Retrieves chunks using query "notes about AWS"
- Filters chunks for relevance (must contain "AWS")
- Truncates long chunks to 800 words
- Generates summary with simple prompt
- Returns formatted summary with source file list
```

**Features:**
- Two modes: current note or topic-based
- Relevance filtering to ensure quality
- Chunk truncation for 8B model capacity
- Simple prompts optimized for smaller models
- Source file tracking

**Requirements:**
- **Current Note Mode**: Current note must be open
- **Topic Mode**: Vault must be open, RAG retriever available
- LLM must be available

**Optimizations for 8B Models:**
- Reduced context: Max 6 chunks (down from 10)
- Chunk truncation: 800 words per chunk
- Simple prompts: Direct instructions, no examples
- Relevance filtering: Only chunks containing topic keyword

**Prompt Design:**
The agent uses extremely simple prompts designed for 8B Llama models:
```
Summarize the following notes about [topic].

[content]

Summary:
```

This avoids:
- Complex instructions
- Negative instructions ("do not...")
- Examples that can confuse smaller models
- Verbose explanations

---

## Agent Registration

Agents are registered in the dependency injection system (`ChatModule.kt`):

```kotlin
single<ChatService> {
    val agents = buildList<ChatAgent> {
        add(get<CreateNoteAgent>())
        add(get<SearchNoteAgent>())
        add(get<SummarizeNoteAgent>())
    }
    
    OllamaChatService(
        agents = if (agents.isNotEmpty()) agents else null
    )
}
```

**Order Matters:**
Agents are checked in registration order. The first agent to return a non-null result handles the message.

**Graceful Degradation:**
- If an agent's dependencies are unavailable, it's simply not added to the list
- The chat service continues to work without that agent
- No errors are thrown for missing optional dependencies

## Agent vs. Normal Chat

### When Agents Are Used

Agents handle messages when:
- Intent patterns match
- Prerequisites are met (vault open, dependencies available)
- Agent successfully processes the request

### When Normal Chat Is Used

Normal chat flow (RAG/WEB/HYBRID) is used when:
- No agent matches the intent
- Agent prerequisites not met (e.g., no vault open)
- Agent returns `null` (couldn't handle the message)

**Benefits of This Design:**
- Agents provide structured, reliable actions for common tasks
- Normal chat provides flexible, conversational responses
- Users get the best of both worlds

## Extending the Agent System

### Adding a New Agent

1. **Create Agent Class:**
```kotlin
class MyAgent(
    // Dependencies
) : ChatAgent {
    override suspend fun tryHandle(
        message: String,
        chatHistory: List<ChatMessage>,
        context: AgentContext
    ): AgentResult? {
        // Intent detection
        // Action execution
        // Return result or null
    }
}
```

2. **Define Intent Patterns:**
```kotlin
companion object {
    private val INTENT_PATTERNS = listOf(
        "pattern 1",
        "pattern 2"
    )
}
```

3. **Extract Parameters:**
```kotlin
private fun extractParameter(message: String): String? {
    // Pattern matching and extraction
}
```

4. **Return Appropriate Result:**
```kotlin
return AgentResult.NoteCreated(...)
// or
return AgentResult.NotesFound(...)
// or
return AgentResult.NoteSummarized(...)
```

5. **Register in DI:**
```kotlin
single<MyAgent> { MyAgent(...) }

// In ChatService registration:
val agents = buildList<ChatAgent> {
    add(get<MyAgent>())
    // ... other agents
}
```

### Best Practices

1. **Clear Intent Patterns**: Use specific, unambiguous patterns
2. **Validate Prerequisites**: Check vault, dependencies before processing
3. **Logging**: Log intent detection, actions, and errors
4. **Error Handling**: Return `null` on errors, don't throw
5. **Simple Prompts**: For LLM interactions, keep prompts simple (especially for 8B models)
6. **Structured Results**: Use appropriate `AgentResult` types
7. **User Feedback**: Provide clear, formatted responses

## Configuration

Agents use settings from `AgentContext.settings`:

- **RAG Settings**: Model names, similarity thresholds, etc.
- **File System**: Vault paths, file operations
- **LLM Settings**: Model selection, base URLs

Agents adapt to current settings dynamically.

## Performance Considerations

### Agent Overhead

- **Intent Detection**: Pattern matching is fast (string operations)
- **Agent Execution**: Varies by agent (file I/O, RAG retrieval, LLM calls)
- **Early Exit**: Agents return `null` quickly if they don't match

### Optimization Strategies

1. **Pattern Order**: Most common patterns first
2. **Early Validation**: Check prerequisites before expensive operations
3. **Caching**: Cache file listings, settings where appropriate
4. **Concurrent Operations**: Use coroutines for parallel work
5. **Context Limiting**: Limit context size for LLM calls (especially 8B models)

## Future Enhancements

Potential improvements to the agent system:

- **Conversation Context**: Use chat history for better intent detection
- **Confidence Scores**: Agents could return confidence scores
- **Multi-Agent Collaboration**: Agents could call other agents
- **Learning**: Agents could learn from user corrections
- **Custom Agents**: User-defined agents via configuration
- **MCP Server**: Convert agents to MCP (Model Context Protocol) server
- **Streaming Responses**: Stream agent results for better UX
- **Agent Chaining**: Chain agents for complex workflows

## Troubleshooting

### Agent Not Triggering

**Check:**
1. Intent pattern matches (case-insensitive)
2. Prerequisites met (vault open, dependencies available)
3. Agent registered in DI
4. Agent not returning `null` early

**Debug:**
- Check logs for "Agent called: [AgentName]"
- Verify intent patterns match your message
- Ensure vault is open for vault-dependent agents

### Agent Errors

**Common Issues:**
- Missing dependencies (RAG retriever, file system)
- Invalid vault path
- LLM unavailable
- File permission errors

**Handling:**
- Agents should return `null` on errors (fallback to normal chat)
- Errors are logged with context
- User sees normal chat response if agent fails

### Performance Issues

**Symptoms:**
- Slow agent responses
- High memory usage
- Timeouts

**Solutions:**
- Reduce context size (chunk limits, truncation)
- Optimize pattern matching
- Add caching where appropriate
- Use simpler prompts for 8B models

## Conclusion

The agent system provides a powerful way to handle specific user intents with structured, reliable actions. By combining agents with normal chat flow, Krypton provides both deterministic actions for common tasks and flexible conversational AI for everything else.

Agents are designed to be:
- **Simple**: Clear intent patterns, straightforward logic
- **Reliable**: Deterministic behavior, proper error handling
- **Efficient**: Fast intent detection, optimized for 8B models
- **Extensible**: Easy to add new agents

For implementation details, see the agent source files in `composeApp/src/commonMain/kotlin/org/krypton/chat/agent/`.

