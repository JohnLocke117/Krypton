# AI Chat Features

Krypton's AI chat system provides intelligent interactions with your notes through multiple retrieval modes. This document details how the chat system works, including the RAG pipeline, normal chat, and Tavily-based web search.

## Overview

The chat system supports four retrieval modes:

- **NONE**: Plain chat without any context retrieval
- **RAG**: Retrieval-Augmented Generation using local notes only
- **WEB**: Tavily web search only
- **HYBRID**: Combines both RAG (local notes) and web search

## Architecture

### Components

The chat system consists of several key components:

1. **ChatService**: Base interface for chat operations
2. **OllamaChatService**: Direct LLM chat without retrieval (includes agent system)
3. **ChatAgent**: Interface for specialized agents that handle specific intents
4. **RetrievalService**: Orchestrates retrieval from different sources
5. **PromptBuilder**: Constructs prompts with context
6. **LlamaClient**: Interface for LLM interactions

### Agent System

Krypton includes an intelligent agent system that intercepts user messages before normal chat processing. The system uses a MasterAgent architecture with LLM-based intent classification to route messages to specialized concrete agents.

**Agent Flow:**
1. User sends message
2. MasterAgent receives message
3. IntentClassifier (LLM-based) classifies intent
4. MasterAgent routes to appropriate concrete agent (CreateNoteAgent, SearchNoteAgent, SummarizeNoteAgent)
5. If no agent matches (NORMAL_CHAT or UNKNOWN), normal RAG/chat flow proceeds

See **[Agents & Agentic Architecture](./agents.md)** for detailed documentation on the MasterAgent system and all available agents.

### Service Hierarchy

```
ChatService (interface)
└── OllamaChatService (handles retrieval internally, includes agent system)
    └── RagChatService (simple delegate wrapper, may be removed in future)
```

`OllamaChatService` now handles retrieval internally based on the retrieval mode. It includes the MasterAgent system for intent-based routing. `RagChatService` is currently a simple delegate wrapper and may be removed in the future.

## Retrieval Modes

### NONE Mode

Plain chat without any context retrieval. The user's message is sent directly to the LLM with conversation history.

**Flow:**
1. User sends message
2. Message added to history
3. Full conversation history sent to LLM
4. LLM generates response
5. Response added to history

**Use Cases:**
- General conversation
- Questions not related to your notes
- When RAG/web search is not needed

### RAG Mode

Retrieval-Augmented Generation using your local markdown notes. The system retrieves relevant chunks from your indexed notes and includes them as context in the prompt.

**Flow:**
1. User sends message
2. Query is embedded (with optional query rewriting)
3. Vector store is searched for relevant chunks
4. Chunks are reranked (if reranker is available)
5. Top chunks are filtered by similarity threshold
6. Prompt is built with retrieved context
7. LLM generates response using context
8. Response returned to user

**Key Components:**

#### Query Processing

- **Query Rewriting** (optional): Rewrites the user query to be more effective for retrieval
- **Multi-Query** (optional): Generates multiple alternative queries to improve recall

#### Retrieval Pipeline

1. **Embedding**: User query is converted to a vector embedding
2. **Vector Search**: ChromaDB searches for similar document chunks
3. **Reranking**: Retrieved chunks are reranked using a dedicated reranker model or the generator LLM
4. **Filtering**: Chunks below the similarity threshold are filtered out
5. **Selection**: Top `displayK` chunks are selected for context

#### Reranking

The system supports two reranking strategies:

- **Dedicated Reranker**: Uses a specialized reranker model (if available)
- **LLM-based Fallback**: Uses the generator LLM for reranking if no dedicated model is available

Reranking improves the relevance of retrieved chunks by scoring them based on their semantic relevance to the query.

**Configuration:**
- `similarityThreshold`: Minimum similarity score (default: 0.25)
- `maxK`: Maximum chunks to retrieve before reranking (default: 10)
- `displayK`: Number of chunks to include in prompt (default: 5)
- `queryRewritingEnabled`: Enable query rewriting
- `multiQueryEnabled`: Enable multi-query expansion
- `rerankerModel`: Optional dedicated reranker model name

**Use Cases:**
- Questions about your notes
- Finding information across multiple documents
- Semantic search over your knowledge base

### WEB Mode

Uses Tavily web search to retrieve information from the internet. The search results are included as context in the prompt.

**Flow:**
1. User sends message
2. Query is sent to Tavily API
3. Web snippets are retrieved
4. Prompt is built with web search results
5. LLM generates response using web context
6. Response returned to user

**Tavily Integration:**

The `WebSearchClient` interface provides web search functionality. The implementation:
- Sends queries to Tavily API
- Retrieves snippets with title, URL, and content
- Returns structured `WebSnippet` objects

**Configuration:**
- Requires `TAVILLY_API_KEY` in `local.secrets.properties`
- `maxResults`: Maximum number of search results (default: 5)

**Use Cases:**
- Questions requiring current information
- Topics not covered in your notes
- Fact-checking and verification

### HYBRID Mode

Combines both RAG and web search. Retrieves context from both local notes and the web, providing comprehensive answers.

**Flow:**
1. User sends message
2. Parallel retrieval:
   - RAG: Query embedded, vector search performed, chunks retrieved
   - WEB: Query sent to Tavily, web snippets retrieved
3. Prompt is built with both local chunks and web snippets
4. LLM generates response using combined context
5. Response returned to user

**Advantages:**
- Combines personal knowledge (notes) with general knowledge (web)
- Provides more comprehensive answers
- Can verify information from notes against web sources

**Use Cases:**
- Questions that benefit from both personal and general knowledge
- When you want to cross-reference your notes with current information
- Comprehensive research queries

## RAG Pipeline Details

### Indexing Process

Before RAG can work, your notes must be indexed:

1. **File Discovery**: All `.md` files in the selected folder are discovered
2. **Chunking**: Each file is split into semantic chunks using `MarkdownChunker`
3. **Embedding**: Each chunk is converted to a vector embedding
4. **Storage**: Chunks with embeddings are stored in ChromaDB

**Chunking Strategy:**
- Chunks are created based on markdown structure (headings, paragraphs)
- Each chunk includes metadata: file path, start line, end line
- Chunks are sized to balance context and granularity

**Incremental Updates:**
- Files are tracked by hash
- Only modified files are re-indexed
- Deleted files' chunks are removed from the vector store

### Retrieval Process

When a query is made in RAG mode:

1. **Query Preprocessing** (if enabled):
   - Query rewriting: Improves query for better retrieval
   - Multi-query: Generates alternative queries for better recall

2. **Embedding Generation**:
   - Query is embedded using the same model as documents
   - Embedding type: `SEARCH_QUERY` (may differ from document embeddings)

3. **Vector Search**:
   - ChromaDB performs cosine similarity search
   - Returns top `maxK` results with similarity scores

4. **Reranking** (if enabled):
   - Retrieved chunks are reranked by semantic relevance
   - Uses dedicated reranker model or generator LLM

5. **Filtering**:
   - Chunks below `similarityThreshold` are removed
   - Remaining chunks are sorted by similarity

6. **Selection**:
   - Top `displayK` chunks are selected for context

### Prompt Construction

The `PromptBuilder` constructs prompts with retrieved context:

**Structure:**
```
System instructions
[Optional: Local context from RAG]
[Optional: Web search results]
User query
```

**Context Format:**
- Local chunks include file path, line numbers, and content
- Web snippets include title, URL, and snippet text
- Context is clearly marked and separated

## Conversation Management

Krypton includes a conversation management system that persists chat history and manages memory bounds for efficient LLM interactions.

### Architecture

```
ConversationRepository
    ↓
ConversationMemoryProvider
    ↓
Bounded Context Messages
    ↓
ChatService
```

### Components

1. **ConversationRepository**: Persists conversations per vault
   - Stores conversations in `.krypton/chat/` directory within each vault
   - Each conversation stored as separate JSON file
   - Index file contains summaries of all conversations
   - Platform-specific implementations (JVM and Android)

2. **ConversationMemoryProvider**: Applies memory bounds to conversations
   - Loads all messages for a conversation
   - Applies `ConversationMemoryPolicy` limits:
     - **Desktop (JVM)**: 50 messages max, 16,000 characters max (generous for Llama 128k)
     - **Android**: 15 messages max, 6,000 characters max (conservative for Gemini 2.5 Flash)
   - Builds context from newest to oldest, respecting both message and character limits
   - Returns bounded context messages in chronological order

3. **ConversationMemoryPolicy**: Configurable memory limits
   - `maxMessages`: Maximum number of messages to include
   - `maxChars`: Maximum character count for context

### Flow

1. **Message Sent**: User sends a message
2. **Conversation Creation/Retrieval**: 
   - If `conversationId` provided, loads existing conversation
   - Otherwise, creates new conversation with initial message
3. **Memory Loading**: `ConversationMemoryProvider` loads bounded history
4. **Context Building**: Bounded history converted to format for agents and prompt builder
5. **Agent Processing**: MasterAgent uses recent history (last 4 messages) for intent classification
6. **Response Generation**: Full bounded history used for LLM prompt
7. **Persistence**: Both user and assistant messages saved to conversation

### Benefits

- **Bounded Memory**: Prevents context window overflow with long conversations
- **Efficient**: Only loads necessary messages, respects character limits
- **Persistent**: Conversations saved per vault, survive app restarts
- **Platform-Optimized**: Different limits for desktop vs mobile based on model capabilities

## Configuration

### Settings

RAG and chat settings are configured in the application settings:

```kotlin
data class RagSettings(
    val vectorBackend: VectorBackend = CHROMADB,
    val llamaBaseUrl: String = "http://localhost:11434",
    val embeddingBaseUrl: String = "http://localhost:11434",
    val chromaBaseUrl: String = "http://localhost:8000",
    val chromaCollectionName: String = "krypton_notes",
    val similarityThreshold: Float = 0.25f,
    val maxK: Int = 10,
    val displayK: Int = 5,
    val queryRewritingEnabled: Boolean = false,
    val multiQueryEnabled: Boolean = false,
    val rerankerModel: String? = null
)
```

### Environment Variables

- `TAVILLY_API_KEY`: Required for web search functionality (stored in `local.secrets.properties`)

## Error Handling

The chat system includes robust error handling:

- **Retrieval Failures**: Falls back to base chat service if retrieval fails
- **LLM Failures**: Errors are logged and user is notified
- **Missing Components**: Gracefully degrades if RAG components are unavailable
- **Network Errors**: Handles timeouts and connection issues

## Performance Considerations

### Optimization Strategies

1. **Batch Embedding**: Multiple chunks are embedded in batches to reduce HTTP overhead
2. **Concurrent Processing**: File indexing uses parallel processing with concurrency limits
3. **Caching**: Vector store caches collection IDs and metadata
4. **Incremental Updates**: Only modified files are re-indexed

### Tuning Parameters

- **maxK**: Increase for better recall, decrease for faster retrieval
- **displayK**: Increase for more context, decrease for focused answers
- **similarityThreshold**: Increase for higher precision, decrease for higher recall
- **Multi-query**: Improves recall but increases latency

## Future Enhancements

Potential improvements to the chat system:

- Support for multiple vector backends (currently ChromaDB)
- Streaming responses for better UX (currently full response)
- Custom prompt templates (currently fixed templates)
- Citation tracking for retrieved chunks (currently context only)
- Query result caching (currently no caching)
- Adaptive memory bounds based on model context window size
- Conversation summarization for very long conversations
- Conversation search and filtering

