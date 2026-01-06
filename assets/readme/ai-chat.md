# (Another) AI-Powered Chat

Krypton’s AI chat is the “brain on top of our notes”: it can talk normally, reason over our markdown vault, pull in live web context, or combine everything in one conversation. Truly our Big-Brain Time.


---
## Chat at a Glance
Krypton’s chat supports four retrieval modes that we can flip between depending on what we need.

- **NONE** – Plain LLM chat with just our conversation history.
- **RAG** – Retrieval‑Augmented Generation over our local markdown notes.
- **WEB** – Tavily‑powered web search only.
- **HYBRID** – RAG + web, so the model can see both our notes and the internet at once.

Under the hood, the same chat pipeline powers all modes; the only difference is *which* sources are pulled in as context before the LLM answers.


---
## Core Pieces of the Chat System
The chat architecture is a small stack on top of the shared domain layer.

- **ChatService**
	- Main interface for “send a message, get a response”.
	- Hides away retrieval modes, agents, prompts, and external services from the UI.

- **OllamaChatService**
	- Concrete ChatService that talks to Ollama or Gemini, and internally decides how to handle NONE, RAG, WEB, and HYBRID.
	- Hosts the **MasterAgent** system, so every message passes through agents before falling back to “normal chat”.

- **RetrievalService**
	- Knows how to fetch context from ChromaDB (RAG), Tavily (WEB), or both (HYBRID).
	- Handles embeddings, vector search, reranking, and filtering.

- **PromptBuilder**
	- Takes system instructions, conversation history, and any retrieved context, and turns them into a single prompt the LLM can actually understand.

- **LlamaClient / LLM client**
	- Thin client that wraps the actual LLM API calls (Ollama locally, or Gemini over HTTP).

The UI just calls `ChatService` with a message and current mode; everything else happens below the surface.


---
## Agentic Layer: MasterAgent and Friends
Before a message becomes an LLM call, it goes through an **agent router** that can turn chats into actual actions over our notes.

### How the agent flow works
1. We send a message from the chat UI.
2. The **MasterAgent** receives the message plus a small slice of recent conversation history.
3. An LLM‑based **intent classifier** decides whether this looks like: “create a note”, “summarise this file”, “generate flashcards”, “manage a study goal”, “search notes”, or just “normal chat”.
4. If a concrete agent matches the intent, MasterAgent routes the request to that agent:
	- **CreateNoteAgent** – creates and saves new markdown notes.
	- **SearchNoteAgent** – runs semantic/keyword search over our notes.
	- **SummarizeNoteAgent** – produces summaries of notes or topics.
	- **FlashcardAgent** – generates flashcards from selected notes or queries.
	- **StudyAgent** – manages study goals and sessions.

5. The agent executes its workflow (often using RAG + domain services), produces a result, and optionally a human‑readable message.
6. If no agent is appropriate (NORMAL_CHAT or UNKNOWN), the request falls through to the usual chat path with the selected retrieval mode.

All of these agents are also exposed via MCP‑SSE as tools, so external clients can trigger them directly without going through the chat UI.


---
## Retrieval Modes Explained
Each retrieval mode changes how context is built before talking to the LLM, but the chat API stays the same.

### NONE – Plain Conversation
**What happens**
- our message is appended to conversation history.
- A bounded slice of that history is sent straight to the LLM (no RAG, no web).

**Good for**
- Quick questions, brainstorming, or topics unrelated to our notes.

### RAG – chat over our vault
RAG (Retrieval‑Augmented Generation) lets the model answer using our markdown vault as a private knowledge base.

**Da Flow**:
1. Message comes in; if RAG is enabled and no agent short‑circuits, the query is prepared for retrieval.
2. Optional **query rewriting** and/or **multi‑query** generate better search queries.
3. The final query (or queries) is embedded into a vector.
4. ChromaDB searches for similar document chunks.
5. Returned chunks are optionally **reranked** by a dedicated reranker model or by the main LLM.
6. Chunks below `similarityThreshold` are discarded; the top `displayK` remain.
7. PromptBuilder injects these chunks as “local context” above the user question.
8. The LLM answers, citing information only from what it can see in the prompt.

**Config knobs**
- `similarityThreshold` – how picky to be about relevance (default 0.25).
- `maxK` – how many chunks to grab before reranking (default 10).
- `displayK` – how many chunks actually make it into the prompt (default 5).
- `queryRewritingEnabled` / `multiQueryEnabled` – turn smarter retrieval on or off.
- `rerankerModel` – optional dedicated reranker name.

### WEB – internet only
WEB mode brings in live information from Tavily instead of our notes.

**The Flow**:
1. Query is sent to the `WebSearchClient` (Tavily implementation).
2. Tavily returns structured `WebSnippet`s, each with title, URL, and snippet text.
3. PromptBuilder adds these snippets into the context section.
4. The LLM answers using just web context plus conversation history.

**Config**
- Requires `TAVILLY_API_KEY` in `local.properties`.
- `maxResults` controls how many snippets to include (default 5).

### HYBRID – best of both worlds
HYBRID mode acts like “RAG + WEB in parallel” and then feeds everything to the LLM in one go.[1]

**Flow**
1. For a single user message, RAG retrieval and web search run in parallel.
2. RAG returns note chunks; Tavily returns web snippets.
3. PromptBuilder combines them into clearly separated context sections (e.g., “Local notes” vs “Web results”).
4. The LLM answers with awareness of both our notes and the wider internet.
  
**Why use it**
- Great for research‑y questions where we want our notes plus fresh external information.
- Useful for cross‑checking our notes against current facts.


----
## RAG Indexing and Retrieval Under the Hood
Before RAG can answer anything, Krypton needs to **index** our notes into ChromaDB.  

### Indexing notes  
1. **File discovery** – all `.md` files in the selected vault are scanned.
2. **Chunking** – files are split into semantic chunks by `MarkdownChunker`, typically around headings and paragraphs.
3. **Embedding** – each chunk is sent to the embedding model to get a vector representation.
4. **Storage** – ChromaDB stores vectors plus metadata like file path and line ranges.

To keep things efficient:
- Files are tracked by hash; only changed files get re‑indexed.
- Deleted files lead to their chunks being removed from the vector store.

### Retrieval cycle (RAG query)
When we ask a question in RAG or HYBRID mode:
1. Optional query rewriting / multi‑query improves search recall.
2. The query embedding is generated.
3. ChromaDB performs a similarity search (cosine, by default).
4. Results are reranked if configured.
5. Low‑score results are filtered; the top ones are selected.
6. PromptBuilder formats context for the LLM.


----
## Conversation Memory and Persistence
Krypton stores chat history **per vault** and keeps only a bounded slice of it in the LLM context so we do not blow the model’s context window.

### Storage  
- **ConversationRepository** persists conversations under a `.krypton/chat/` folder inside each vault.
- Each conversation lives in a JSON file; an index file stores short summaries for quick listing.
- Implementations differ per platform (JVM vs Android), but the interface is shared.

### Memory policy
- **ConversationMemoryProvider** loads messages and applies `ConversationMemoryPolicy` to decide how much history to keep for the next prompt.
- Policies differ by platform:
	- Desktop: up to ~50 messages and 16,000 characters (aligned with big context models like Llama 3.1 8B 128k).
	- Android: up to ~15 messages and 6,000 characters (more conservative for Gemini 2.5 Flash).
  

Typical flow when we send a message:
1. Conversation is created or loaded for the current vault.
2. Memory provider builds a bounded history slice, newest to oldest, capped by messages and characters.
3. MasterAgent gets a small, very recent subset for intent classification (for example, last 4 messages).
4. The full bounded history is used as the conversation context in the final prompt.
5. Both our message and the assistant’s reply are appended and saved.


----
## Configuration, Errors, and Performance
### How chat and RAG are configured
Core RAG/chat settings live in a `RagSettings` data class and are persisted via the app’s settings system.

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


Environment/keys needed:
- `TAVILLY_API_KEY` for web search.
- ChromaDB (local or cloud) configuration.
- Ollama or Gemini API endpoints and keys.

### Failure behaviour
The chat system is designed to degrade gracefully instead of breaking user flows.
- If RAG fails (Chroma down, embeddings error), chat falls back to normal LLM conversation.
- If Tavily fails, WEB and HYBRID drop the web part but still return a response when possible.
- LLM/network errors are logged, and the user is shown an error rather than a silent failure.
- If core RAG components are missing at startup, those modes are simply unavailable in the UI.
  
### Performance choices
Several optimisations keep RAG responsive even with large vaults.
- **Batch embeddings** – multiple chunks are embedded in a single call to reduce overhead.
- **Concurrent indexing** – indexing uses limited parallelism for faster initial builds.
- **Incremental updates** – only changed files are re‑embedded.
- **Tuning knobs** – `maxK`, `displayK`, `similarityThreshold`, and multi‑query allow trading off speed vs recall.


---
## Where This Can Go Next
The current system is intentionally modular, so there is a lot of room for upgrades without rewiring everything.

Some natural next steps:
- Plug in more vector backends besides ChromaDB.
- Support streaming responses from the LLM for a more “live” chat feel.
- Add citation markers that map answers back to specific note chunks or web snippets.
- Summarise and compress old conversations automatically once they grow too long.
- Add conversation search and filters so we can quickly find past chats inside a vault.

For a deep dive into the MasterAgent and each individual agent, see the **Agents & Agentic Architecture** README that sits next to this one.
