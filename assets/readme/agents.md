# Agents & Agentic Architecture

Krypton includes an intelligent agent system that enables natural language interactions for specific tasks. The system uses a MasterAgent architecture with LLM-based intent classification to route messages to specialized concrete agents.

## Overview

The agent system provides a way to handle specific user intents with dedicated, purpose-built handlers. The architecture consists of:

- **MasterAgent**: Single entry point that uses LLM-based intent classification to route messages
- **IntentClassifier**: LLM-based classifier that determines user intent (CREATE_NOTE, SEARCH_NOTES, SUMMARIZE_NOTE, or NORMAL_CHAT)
- **Concrete Agents**: Specialized agents (CreateNoteAgent, SearchNoteAgent, SummarizeNoteAgent) that execute specific actions
- **Structured Actions**: Direct execution of specific operations with clear, formatted responses
- **Better UX**: Reliable, deterministic behavior for common tasks

## Architecture

### Agent Flow

```
User Message
    ↓
OllamaChatService.sendMessage()
    ↓
MasterAgent.tryHandle()
    ↓
IntentClassifier.classify() [LLM call]
    ↓
Route based on IntentType:
    ├─→ CREATE_NOTE → CreateNoteAgent.execute()
    ├─→ SEARCH_NOTES → SearchNoteAgent.execute()
    ├─→ SUMMARIZE_NOTE → SummarizeNoteAgent.execute()
    └─→ NORMAL_CHAT / UNKNOWN → null (fall back to normal RAG/Chat flow)
```

### MasterAgent

The `MasterAgent` is the single entry point for the agent system. It implements `ChatAgent` and is the only agent exposed to `ChatService`:

```kotlin
interface MasterAgent : ChatAgent {
    suspend fun tryHandle(
        message: String,
        chatHistory: List<ChatMessage>,
        context: AgentContext
    ): AgentResult?
}
```

**Key Points:**
- Single LLM call for intent classification (via `IntentClassifier`)
- Routes to appropriate concrete agent based on classified intent
- Returns `null` to fall back to normal chat flow if no agent matches
- Handles errors gracefully, falling back to normal chat on failures

### Intent Classification

The `IntentClassifier` uses an LLM to classify user messages into one of four intent types:

```kotlin
enum class IntentType {
    CREATE_NOTE,      // User wants to create a new note
    SEARCH_NOTES,     // User wants to search/find notes
    SUMMARIZE_NOTE,   // User wants a summary
    NORMAL_CHAT,      // General conversation (fall back to normal chat)
    UNKNOWN           // Classification failed (fall back to normal chat)
}
```

**LlmIntentClassifier**:
- Makes a single LLM call with a system prompt
- Uses recent conversation history (last 4 messages) for context
- Returns a single intent label
- Falls back to `UNKNOWN` on errors or invalid responses

### Concrete Agents

Concrete agents are specialized interfaces that execute specific actions. They assume intent has already been classified by MasterAgent:

```kotlin
interface CreateNoteAgent {
    suspend fun execute(
        message: String,
        history: List<ChatMessage>,
        context: AgentContext
    ): AgentResult
}

interface SearchNoteAgent {
    suspend fun execute(
        message: String,
        history: List<ChatMessage>,
        context: AgentContext
    ): AgentResult
}

interface SummarizeNoteAgent {
    suspend fun execute(
        message: String,
        history: List<ChatMessage>,
        context: AgentContext
    ): AgentResult
}
```

**Key Points:**
- Concrete agents are **not** `ChatAgent` implementations
- They focus purely on execution, not intent detection
- They throw exceptions on failures (MasterAgent handles errors)
- They return structured `AgentResult` objects

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

**Intent Classification**: Handled when `IntentClassifier` returns `CREATE_NOTE`

**How It Works:**

1. **Topic Extraction**: Extracts the topic from the message (assumes intent already classified)
2. **Content Generation**: Uses LLM to generate markdown content about the topic
3. **File Creation**: Creates a new `.md` file in the current vault
4. **Collision Handling**: Automatically handles filename collisions with numeric suffixes

**Example:**
```
User: "create a note on machine learning basics"

Flow:
1. MasterAgent receives message
2. IntentClassifier classifies as CREATE_NOTE
3. CreateNoteAgent.execute() called
4. Extracts topic: "machine learning basics"
5. Generates content using LLM
6. Creates file: "machine-learning-basics.md"
7. Returns AgentResult.NoteCreated with preview
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

**Intent Classification**: Handled when `IntentClassifier` returns `SEARCH_NOTES`

**How It Works:**

1. **Query Extraction**: Extracts the search query from the message (assumes intent already classified)
2. **Dual Search**:
   - **Vector Search**: Uses RAG retriever for semantic similarity
   - **Keyword Search**: Performs text-based keyword matching
3. **Result Merging**: Combines and deduplicates results
4. **Scoring**: Assigns relevance scores (vector: 70%, keyword: 30%)
5. **Formatting**: Returns formatted list with titles, snippets, and scores

**Example:**
```
User: "find notes about AWS"

Flow:
1. MasterAgent receives message
2. IntentClassifier classifies as SEARCH_NOTES
3. SearchNoteAgent.execute() called
4. Extracts query: "AWS"
5. Performs vector search (semantic)
6. Performs keyword search (exact matches)
7. Merges results with combined scores
8. Returns AgentResult.NotesFound with formatted list
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

**Intent Classification**: Handled when `IntentClassifier` returns `SUMMARIZE_NOTE`

**How It Works:**

The agent automatically determines the mode based on context:

#### Current Note Mode:
1. **Mode Detection**: Checks if `currentNotePath` is available in context
2. **Note Validation**: Verifies current note exists and is readable
3. **Content Retrieval**: Reads the current note content
4. **Summary Generation**: Uses LLM with simple, direct prompt
5. **Response**: Returns formatted summary

#### Topic Mode:
1. **Mode Detection**: No current note available, extracts topic from message
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

Flow:
1. MasterAgent receives message
2. IntentClassifier classifies as SUMMARIZE_NOTE
3. SummarizeNoteAgent.execute() called
4. Detects topic mode (no current note)
5. Extracts topic: "AWS"
6. Retrieves chunks using query "notes about AWS"
7. Filters chunks for relevance (must contain "AWS")
8. Truncates long chunks to 800 words
9. Generates summary with simple prompt
10. Returns AgentResult.NoteSummarized with formatted summary
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
// IntentClassifier (LLM-based)
single<IntentClassifier> {
    LlmIntentClassifier(llmClient = get())
}

// Concrete Agents (interfaces, not ChatAgent)
single<CreateNoteAgent> { CreateNoteAgentImpl(...) }
single<SearchNoteAgent> { SearchNoteAgentImpl(...) }
single<SummarizeNoteAgent> { SummarizeNoteAgentImpl(...) }

// MasterAgent (the only ChatAgent, routes to concrete agents)
single<MasterAgent> {
    MasterAgentImpl(
        classifier = get(),
        createNoteAgent = get(),
        searchNoteAgent = get(),
        summarizeNoteAgent = get()
    )
}

// ChatService (exposes only MasterAgent)
factory<ChatService> {
    val agents = listOf(get<MasterAgent>())
    OllamaChatService(agents = agents, ...)
}
```

**Key Points:**
- Only `MasterAgent` implements `ChatAgent` and is exposed to `ChatService`
- Concrete agents are registered separately and injected into `MasterAgent`
- `IntentClassifier` is registered as a singleton
- Graceful degradation: If `MasterAgent` fails, chat falls back to normal flow

## Agent vs. Normal Chat

### When Agents Are Used

Agents handle messages when:
- `IntentClassifier` classifies intent as `CREATE_NOTE`, `SEARCH_NOTES`, or `SUMMARIZE_NOTE`
- Prerequisites are met (vault open, dependencies available)
- Concrete agent successfully executes the action

### When Normal Chat Is Used

Normal chat flow (RAG/WEB/HYBRID) is used when:
- `IntentClassifier` classifies intent as `NORMAL_CHAT` or `UNKNOWN`
- `MasterAgent` returns `null` (no agent matched or error occurred)
- Agent prerequisites not met (e.g., no vault open)
- Concrete agent throws an exception (MasterAgent catches and returns `null`)

**Benefits of This Design:**
- **LLM-based Intent Classification**: More accurate than pattern matching, understands context
- **Single Classification Call**: One LLM call determines routing, more efficient than checking multiple agents
- **Structured Actions**: Concrete agents provide reliable, deterministic behavior
- **Flexible Fallback**: Normal chat provides flexible, conversational responses when agents don't match
- **Better UX**: Users get structured actions for common tasks and flexible chat for everything else

## Extending the Agent System

### Adding a New Agent

1. **Add Intent Type:**
```kotlin
enum class IntentType {
    CREATE_NOTE,
    SEARCH_NOTES,
    SUMMARIZE_NOTE,
    MY_NEW_INTENT,  // Add new intent type
    NORMAL_CHAT,
    UNKNOWN
}
```

2. **Update IntentClassifier:**
Update `LlmIntentClassifier` system prompt to include the new intent type.

3. **Create Concrete Agent Interface:**
```kotlin
interface MyNewAgent {
    suspend fun execute(
        message: String,
        history: List<ChatMessage>,
        context: AgentContext
    ): AgentResult
}
```

4. **Implement Concrete Agent:**
```kotlin
class MyNewAgentImpl(
    // Dependencies
) : MyNewAgent {
    override suspend fun execute(
        message: String,
        history: List<ChatMessage>,
        context: AgentContext
    ): AgentResult {
        // Extract parameters from message
        // Execute action
        // Return appropriate AgentResult
    }
}
```

5. **Update MasterAgent:**
```kotlin
class MasterAgentImpl(
    private val classifier: IntentClassifier,
    private val createNoteAgent: CreateNoteAgent,
    private val searchNoteAgent: SearchNoteAgent,
    private val summarizeNoteAgent: SummarizeNoteAgent,
    private val myNewAgent: MyNewAgent  // Add new agent
) : MasterAgent {
    override suspend fun tryHandle(...): AgentResult? {
        val intent = classifier.classify(...)
        return when (intent) {
            IntentType.MY_NEW_INTENT -> myNewAgent.execute(...)
            // ... other intents
        }
    }
}
```

6. **Register in DI:**
```kotlin
single<MyNewAgent> { MyNewAgentImpl(...) }

single<MasterAgent> {
    MasterAgentImpl(
        classifier = get(),
        createNoteAgent = get(),
        searchNoteAgent = get(),
        summarizeNoteAgent = get(),
        myNewAgent = get()  // Add new agent
    )
}
```

7. **Add AgentResult Type (if needed):**
```kotlin
sealed class AgentResult {
    data class NoteCreated(...) : AgentResult()
    data class NotesFound(...) : AgentResult()
    data class NoteSummarized(...) : AgentResult()
    data class MyNewResult(...) : AgentResult()  // Add new result type
}
```

### Best Practices

1. **Intent Classification**: Ensure `IntentClassifier` can reliably distinguish your intent from others
2. **Validate Prerequisites**: Check vault, dependencies before processing in concrete agents
3. **Logging**: Log intent classification, agent execution, and errors
4. **Error Handling**: Concrete agents should throw exceptions; MasterAgent catches and falls back to normal chat
5. **Simple Prompts**: For LLM interactions, keep prompts simple (especially for 8B models)
6. **Structured Results**: Use appropriate `AgentResult` types
7. **User Feedback**: Provide clear, formatted responses
8. **Single Responsibility**: Each concrete agent should handle one specific action

## MCP (Model Context Protocol) Server

Krypton's agents are exposed as MCP tools via an HTTP server, allowing external clients (like MCP Inspector, Claude Desktop, or custom applications) to interact with the agents programmatically.

### Overview

The MCP server exposes Krypton's three agents as standardized MCP tools:
- `create_note`: Create markdown notes in a vault
- `search_notes`: Search notes using semantic and keyword search
- `summarize_notes`: Summarize notes or topics

### Architecture

```
MCP Client (MCP Inspector, Claude Desktop, etc.)
    ↓ (HTTP/SSE)
Krypton MCP Server (KryptonMcpServer.kt)
    ↓
MCP Server (io.modelcontextprotocol SDK)
    ↓
Tool Handlers
    ↓
Agents (CreateNoteAgent, SearchNoteAgent, SummarizeNoteAgent)
    ↓
AgentContext (vault path, settings, note path)
```

### MCP Tools

#### 1. `create_note`

Creates a markdown note in the specified vault.

**Parameters:**
- `vault_path` (required, string): Path to the vault directory where the note should be created
- `topic` (required, string): Topic/title of the note to create

**Returns:**
- Success: Note creation details (filePath, title, preview)
- Error: Descriptive error message

**Example Request:**
```json
{
  "vault_path": "/path/to/vault",
  "topic": "machine learning basics"
}
```

#### 2. `search_notes`

Searches notes in the specified vault using semantic and keyword search.

**Parameters:**
- `vault_path` (required, string): Path to the vault directory to search in
- `query` (required, string): Search query text
- `limit` (optional, integer, default: 20): Maximum number of matches to return

**Returns:**
- Success: JSON array of search results with filePath, title, snippet, and score
- Error: Descriptive error message

**Example Request:**
```json
{
  "vault_path": "/path/to/vault",
  "query": "AWS",
  "limit": 10
}
```

**Example Response:**
```json
[
  {
    "filePath": "aws-notes.md",
    "title": "AWS Notes",
    "snippet": "Amazon Web Services provides cloud computing...",
    "score": 0.85
  }
]
```

#### 3. `summarize_notes`

Summarizes either a specific note or notes on a topic from the vault.

**Parameters:**
- `vault_path` (required, string): Path to the vault directory
- `mode` (required, string): Either `"current_note"` or `"topic"`
- `note_path` (optional, string): Path to the note file to summarize (required when `mode = "current_note"`)
- `topic` (optional, string): Topic to summarize (required when `mode = "topic"`)

**Returns:**
- Success: JSON object with title, summary, and sourceFiles
- Error: Descriptive error message

**Example Request (Current Note Mode):**
```json
{
  "vault_path": "/path/to/vault",
  "mode": "current_note",
  "note_path": "/path/to/vault/my-note.md"
}
```

**Example Request (Topic Mode):**
```json
{
  "vault_path": "/path/to/vault",
  "mode": "topic",
  "topic": "AWS"
}
```

**Example Response:**
```json
{
  "title": "AWS",
  "summary": "Your notes about AWS cover various services including EC2, S3, and Lambda...",
  "sourceFiles": ["aws-notes.md", "cloud-computing.md"]
}
```

### Running the MCP Server

#### Prerequisites

- Java 11 or higher
- Gradle (included via wrapper)
- All Krypton dependencies configured (see main README)

#### Start the Server

**Option 1: Using Gradle (default port 8080)**
```bash
./gradlew :composeApp:run --args="org.krypton.mcp.KryptonMcpServerKt"
```

**Option 2: Custom Port**
```bash
MCP_PORT=9000 ./gradlew :composeApp:run --args="org.krypton.mcp.KryptonMcpServerKt"
```

**Option 3: Build JAR and Run**
```bash
./gradlew :composeApp:jar
java -jar composeApp/build/libs/composeApp-jvm.jar org.krypton.mcp.KryptonMcpServerKt
```

The server will:
- Start on port 8080 (or the port specified by `MCP_PORT` environment variable)
- Listen on `0.0.0.0` (all network interfaces)
- Log startup messages and tool invocations
- Handle MCP protocol over HTTP/SSE

#### Server Logs

The server logs important events:
- Startup: `[MCP] Starting Krypton MCP Server...`
- Port binding: `[MCP] Starting HTTP server on port 8080`
- Tool invocations: `[MCP] Error in [tool_name] tool` (on errors)

### Testing the MCP Server

#### Using MCP Inspector

1. **Start the MCP Server** (see above)

2. **Install MCP Inspector** (if not already installed):
   ```bash
   npm install -g @modelcontextprotocol/inspector
   ```

3. **Connect to the Server**:
   - Open MCP Inspector
   - Connect to: `http://localhost:8080` (or your custom port)
   - The inspector will discover available tools

4. **Test Tools**:
   - Select a tool (e.g., `create_note`)
   - Fill in required parameters
   - Click "Call Tool"
   - View the response

#### Using HTTP Client (curl/Postman)

**List Available Tools:**
```bash
curl http://localhost:8080/mcp/tools
```

**Call a Tool (create_note):**
```bash
curl -X POST http://localhost:8080/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "tool": "create_note",
    "arguments": {
      "vault_path": "/path/to/vault",
      "topic": "test note"
    }
  }'
```

**Call a Tool (search_notes):**
```bash
curl -X POST http://localhost:8080/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "tool": "search_notes",
    "arguments": {
      "vault_path": "/path/to/vault",
      "query": "test",
      "limit": 5
    }
  }'
```

**Call a Tool (summarize_notes):**
```bash
curl -X POST http://localhost:8080/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "tool": "summarize_notes",
    "arguments": {
      "vault_path": "/path/to/vault",
      "mode": "topic",
      "topic": "AWS"
    }
  }'
```

#### Using Claude Desktop

1. **Configure Claude Desktop** to use the MCP server:
   ```json
   {
     "mcpServers": {
       "krypton": {
         "command": "java",
         "args": [
           "-jar",
           "/path/to/krypton/composeApp/build/libs/composeApp-jvm.jar",
           "org.krypton.mcp.KryptonMcpServerKt"
         ],
         "env": {
           "MCP_PORT": "8080"
         }
       }
     }
   }
   ```

2. **Restart Claude Desktop** to load the configuration

3. **Use in Claude**: Claude can now call the tools directly:
   - "Create a note about machine learning"
   - "Search my notes for AWS"
   - "Summarize my notes on cloud computing"

### Implementation Details

**File Location:**
- `composeApp/src/jvmMain/kotlin/org/krypton/mcp/KryptonMcpServer.kt`

**Dependencies:**
- `io.modelcontextprotocol:kotlin-sdk:0.8.1` - MCP SDK
- `io.ktor:ktor-server-*:3.2.3` - Ktor server (required by MCP SDK)

**Key Components:**
- `main()`: Server entry point, initializes DI and starts HTTP server
- `registerCreateNoteTool()`: Registers create_note tool
- `registerSearchNotesTool()`: Registers search_notes tool
- `registerSummarizeNotesTool()`: Registers summarize_notes tool
- `buildAgentContext()`: Builds AgentContext for tool handlers
- `textContent()`: Helper for creating TextContent responses

**Transport:**
- Uses Ktor's HTTP server with SSE (Server-Sent Events) support
- MCP protocol handled by `mcp { }` extension function from SDK
- Supports streaming responses for better performance

### Troubleshooting

#### Server Won't Start

**Check:**
- Port is not already in use: `lsof -i :8080`
- All dependencies are resolved: `./gradlew :composeApp:dependencies`
- Java version is 11+: `java -version`

**Common Issues:**
- Port conflict: Change port via `MCP_PORT` environment variable
- Missing dependencies: Run `./gradlew build` to download dependencies
- Class not found: Ensure you're using the correct main class path

#### Tools Not Available

**Check:**
- Server started successfully (check logs)
- MCP client connected to correct URL
- Tool names match exactly: `create_note`, `search_notes`, `summarize_notes`

#### Tool Errors

**Common Errors:**
- `Missing required parameter: vault_path` - Provide valid vault path
- `Agent did not create a note` - Check vault path exists and is writable
- `Agent did not return search results` - Ensure vault contains markdown files
- `Agent did not return a summary` - Check note path exists or topic has matching notes

**Debug:**
- Check server logs for detailed error messages
- Verify vault path is absolute and exists
- Ensure agents have required dependencies (LLM, RAG retriever for some operations)

### Security Considerations

**Current Implementation:**
- Server listens on all interfaces (`0.0.0.0`) - consider restricting in production
- No authentication/authorization - add if exposing to network
- File system access - ensure vault paths are validated
- No rate limiting - consider adding for production use

**Production Recommendations:**
- Add authentication (API keys, OAuth, etc.)
- Restrict server to localhost or specific network
- Add rate limiting
- Validate and sanitize all inputs
- Use HTTPS in production
- Add logging and monitoring

## Configuration

Agents use settings from `AgentContext.settings`:

- **RAG Settings**: Model names, similarity thresholds, etc.
- **File System**: Vault paths, file operations
- **LLM Settings**: Model selection, base URLs

Agents adapt to current settings dynamically.

## Performance Considerations

### Agent Overhead

- **Intent Classification**: Single LLM call via `IntentClassifier` (typically fast, ~100-500ms)
- **Agent Execution**: Varies by agent (file I/O, RAG retrieval, LLM calls)
- **Early Exit**: MasterAgent returns `null` quickly if intent is `NORMAL_CHAT` or `UNKNOWN`

### Optimization Strategies

1. **Intent Classification**: Single LLM call determines routing (more efficient than checking multiple agents)
2. **Early Validation**: Concrete agents check prerequisites before expensive operations
3. **Caching**: Cache file listings, settings where appropriate
4. **Concurrent Operations**: Use coroutines for parallel work
5. **Context Limiting**: Limit context size for LLM calls (especially 8B models)
6. **Conversation History**: IntentClassifier uses only last 4 messages for context (reduces token usage)

## Future Enhancements

Potential improvements to the agent system:

- **Conversation Context**: ✅ Implemented - IntentClassifier uses last 4 messages for context
- **Confidence Scores**: IntentClassifier could return confidence scores for classification
- **Confidence Scores**: Agents could return confidence scores
- **Multi-Agent Collaboration**: Agents could call other agents
- **Learning**: Agents could learn from user corrections
- **Custom Agents**: User-defined agents via configuration
- **MCP Server**: ✅ Implemented - Agents are exposed via MCP (Model Context Protocol) server
- **Streaming Responses**: Stream agent results for better UX
- **Agent Chaining**: Chain agents for complex workflows

## Troubleshooting

### Agent Not Triggering

**Check:**
1. IntentClassifier correctly classifies intent (check logs for "Classified intent: ...")
2. Prerequisites met (vault open, dependencies available)
3. MasterAgent registered in DI
4. Concrete agent successfully executes (doesn't throw exception)

**Debug:**
- Check logs for "MasterAgent" and "Classified intent: ..."
- Check logs for "Agent ${agent::class.simpleName} handled the message successfully"
- Verify IntentClassifier is working (LLM available and responding)
- Ensure vault is open for vault-dependent agents
- Check if intent was classified as `NORMAL_CHAT` or `UNKNOWN` (will fall back to normal chat)

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
- Optimize IntentClassifier prompt (keep it simple and direct)
- Add caching where appropriate (intent classification results, file listings)
- Use simpler prompts for 8B models
- Reduce conversation history used by IntentClassifier (currently last 4 messages)

## Conclusion

The agent system provides a powerful way to handle specific user intents with structured, reliable actions. By combining agents with normal chat flow, Krypton provides both deterministic actions for common tasks and flexible conversational AI for everything else.

Agents are designed to be:
- **Intelligent**: LLM-based intent classification understands context and natural language
- **Efficient**: Single LLM call for intent classification, then direct routing to concrete agents
- **Reliable**: Deterministic behavior for concrete agents, proper error handling with fallback
- **Extensible**: Easy to add new intent types and concrete agents

For implementation details, see the agent source files in `composeApp/src/commonMain/kotlin/org/krypton/chat/agent/`.

