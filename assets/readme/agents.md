# Agentic Architecture
Krypton’s agent system is the “automation layer” on top of chat: it turns natural language into concrete actions like creating notes, searching your vault, or managing study goals, while still falling back to normal chat when needed.


----
## How the Agent System Works
Krypton uses a **MasterAgent** at the center of its agent architecture, backed by an LLM‑powered **IntentClassifier** and a set of focused concrete agents.

- **MasterAgent**  
  - Single entry point for all agent logic.  
  - Decides *whether* a message should trigger an agent or just go through normal chat.  

- **IntentClassifier**  
  - LLM‑based classifier that looks at the latest chat turns and labels the user’s intent as one of:  
    `CREATE_NOTE`, `SEARCH_NOTES`, `SUMMARIZE_NOTE`, `GENERATE_FLASHCARDS`, `STUDY_GOAL`, `NORMAL_CHAT`, or `UNKNOWN`.

- **Concrete agents**  
  - Purpose‑built executors:  
    - `CreateNoteAgent` – create markdown notes.  
    - `SearchNoteAgent` – run vault search.  
    - `SummarizeNoteAgent` – summarise notes/topics.  
    - `FlashcardAgent` – generate flashcards.  
    - `StudyAgent` – manage study goals, sessions, and roadmaps.

- **Structured actions & UX**  
  - Each agent returns a structured `AgentResult`, which is then rendered into a friendly chat reply, giving deterministic behaviour for common workflows while keeping chat flexible for everything else.


---
## Message Flow: From User to Agent (or Chat)
The high‑level flow for every chat message looks like this:

![agents](assets/images/agents.png)

- **Single classification step**: Only one LLM call is made per message to decide routing.
- **Graceful fallback**: If classification fails, prerequisites are missing (for example, no vault open), or an agent throws, MasterAgent returns `null` and the message is handled by the normal chat pipeline.


----
## MasterAgent, Intent, and Context
### MasterAgent contract
The MasterAgent itself implements `ChatAgent` but exposes a richer `tryHandle` entry point:


```kotlin
interface MasterAgent : ChatAgent {
    suspend fun tryHandle(
        message: String,
        chatHistory: List<ChatMessage>,
        context: AgentContext
    ): AgentResult?
}
```

Key behaviours:

- Calls `IntentClassifier` with the message and a **short window of recent history** (typically the last 4 messages) for context.  
- Routes to a single concrete agent based on the intent label.  
- Returns `null` if:  
  - Intent is `NORMAL_CHAT` or `UNKNOWN`.  
  - Agent preconditions fail (no vault, no note, missing services).  
  - The concrete agent throws an error.  

### IntentClassifier details

The `IntentClassifier` is intentionally simple but context‑aware:

- Uses an LLM with a fixed, compact system prompt that lists all supported intents and their descriptions.  
- Looks at the latest few messages rather than the full conversation to minimise token usage and keep classification snappy.  
- Returns a single `IntentType` enum, defaulting to `UNKNOWN` on invalid or malformed responses.  

This keeps the routing logic robust while still letting the classifier understand soft phrasing like “hey, can you make a note about…” vs “explain…” vs “quiz me on…”.


----
## Concrete Agents and Agent Results

Concrete agents never worry about *what* the user meant; they assume the intent has already been decided and focus only on execution.

### Shared patterns
All agents follow the same basic pattern:
- Input: `message`, `history`, and `AgentContext` (vault path, settings, current note, etc.).  
- Validate: check prerequisites (vault, file system, RAG, LLM).  
- Execute: run their specific workflow (create file, search, generate summary, plan sessions, etc.).  
- Output: a typed `AgentResult` value (`NoteCreated`, `NotesFound`, `NoteSummarized`, `FlashcardsGenerated`, `StudyGoalCreated`, `StudyGoalPlanned`, `RoadmapGenerated`, `SessionPrepared`, …).  


`AgentResult` stays intentionally structured so the chat layer can render it to:
- Nicely formatted messages (bullet lists, previews, paths).  
- Machine‑friendly JSON when exposed via MCP tools.  

### Examples in practice
- **CreateNoteAgent**  
  - Generates markdown content for a topic, creates a slugified filename, resolves collisions, and returns a preview along with the file path.

- **SearchNoteAgent**  
  - Runs semantic vector search plus keyword search, merges and scores results, and returns a ranked list with snippets showing why each note matched.

- **SummarizeNoteAgent**  
  - Either summarises the current note or summarises notes on a topic by pulling RAG chunks, filtering, truncating, and feeding them to a small‑model‑friendly prompt.

- **FlashcardAgent**  
  - Generates question–answer pairs from a given note (or the current one), respecting a maximum card count and returning a structured flashcard list.

- **StudyAgent**  
  - Creates goals, uses note search to attach relevant files, plans sessions, builds roadmaps, and prepares sessions with summaries and flashcards.


---
## Layered Agents: Simple Building Blocks to Complex Flows
Krypton’s agents are deliberately built in layers.

- **Foundational agents**
  - `CreateNoteAgent`, `SearchNoteAgent`, `SummarizeNoteAgent`, and `FlashcardAgent` act as basic building blocks: they know how to create notes, search the vault, summarise content, and generate flashcards in a focused, reusable way.

- **Composite agents**
  - Higher‑level agents like `StudyAgent` are built *on top of* these foundational agents rather than duplicating logic.  
  - For example, when creating or planning a study goal, `StudyAgent` can call `SearchNoteAgent` to find relevant notes, `SummarizeNoteAgent` to prepare summaries, and `FlashcardAgent` to generate flashcards for each session.


---
## Integration with Chat and MCP
Agents are wired into Krypton’s dependency injection and exposed both inside the app and externally.

### Inside Krypton chat
In DI (for example, `ChatModule`):

- `IntentClassifier` is registered as a singleton.  
- Each concrete agent (`CreateNoteAgentImpl`, `SearchNoteAgentImpl`, etc.) is registered separately.  
- `MasterAgentImpl` depends on the classifier plus all concrete agents.  
- `ChatService` (for example, `OllamaChatService`) receives only the **MasterAgent** as its `ChatAgent` and never knows about individual concrete agents.  

This keeps the public chat surface small while allowing the internals to grow.

### Exposed via MCP tools
The same agents are also used behind Krypton’s **MCP (Model Context Protocol) server**, which exposes them as HTTP/SSE tools for external clients like Claude Desktop or MCP Inspector.

- Note‑related tools: `create_note`, `search_notes`, `summarize_notes`, `generate_flashcards`.  
- Study tools: `create_study_goal`, `plan_study_goal`, `generate_roadmap`, `prepare_session`.  

Each tool:
- Builds an `AgentContext` (vault path, settings, note path).  
- Calls the corresponding agent.  
- Serialises the `AgentResult` to JSON for the MCP client.  

This means Krypton’s “agent brain” is reusable, whether the request comes from the in‑app chat or an external tool.


----
## Extending and Operating the Agent System

The design is meant to be **easy to extend** while staying predictable in production.

### Adding a new agent
In short:

1. Add a new `IntentType` and teach `LlmIntentClassifier` about it via the system prompt.  
2. Define a new agent interface `MyNewAgent` with an `execute(...)` function.  
3. Implement it (`MyNewAgentImpl`) using whatever services it needs.  
4. Extend `MasterAgentImpl`’s `when(intent)` to call `MyNewAgent`.  
5. Register the agent in DI and add a matching `AgentResult` type if necessary.  

The rest of the system (chat UI, MCP tools) can then be wired to display results without touching the core routing logic.

### Performance and troubleshooting
- **Performance**  
  - Only one classification call per message.  
  - Agents keep prompts small and focused (especially for 8B models).  
  - Conversation history for intent is trimmed to a tiny window.

- **When agents are not used**  
  - If the classifier says `NORMAL_CHAT`/`UNKNOWN`, or an agent fails, the message is handled by the normal chat pipeline using the active retrieval mode (NONE/RAG/WEB/HYBRID).

- **Debugging**  
  - Logs usually include the classified intent and whether an agent handled the message or chat fell back to the default flow.  
  - Common issues: closed/missing vaults, unavailable RAG/LLM services, or misconfigured settings.
