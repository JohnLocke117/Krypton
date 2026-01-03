# Krypton

Krypton is a modern, AI-powered note-taking application built with Kotlin Multiplatform and Jetpack Compose. It combines a beautiful markdown editor with advanced Retrieval-Augmented Generation (RAG) capabilities, enabling intelligent chat interactions with your notes and web search integration.

## Overview

Krypton is designed to be your intelligent knowledge companion. It provides:

### Core Features

- **Rich Markdown Editor**: Live preview, syntax highlighting, auto-save, undo/redo, and a modern editing experience
- **AI Chat Interface**: Interact with your notes using AI, with support for RAG, web search, and hybrid modes
- **Intelligent Agents**: MasterAgent system with LLM-based intent classification that routes to specialized agents for note creation, search, and summarization
- **Vector Search**: Automatic indexing of your markdown notes for semantic search with ChromaDB
- **File Management**: Full file and folder CRUD operations with a clean, intuitive interface
- **Flashcard Generation**: AI-powered flashcard generation from your notes
- **MCP Server**: Expose agents as tools via Model Context Protocol for integration with external clients (Desktop only)
- **Cross-Platform**: Built with Kotlin Multiplatform, supporting Desktop (JVM) and Android

### Advanced Features

- **RAG Pipeline**: Automatic document chunking, embedding generation, and semantic retrieval
- **Reranking**: Dedicated reranker models or LLM-based fallback for improved retrieval quality
- **Query Processing**: Optional query rewriting and multi-query expansion for better search results
- **Hybrid Retrieval**: Combine local note search with web search for comprehensive answers
- **Vault Management**: Support for multiple vaults with automatic indexing and file watching
- **Theme Support**: Customizable themes with Catppuccin Mocha color scheme
- **Mobile-Optimized UI**: Android-specific navigation and touch-optimized interface

## High-Level Architecture

Krypton follows a clean, modular architecture:

### Technology Stack

- **UI Framework**: Jetpack Compose Multiplatform
- **Language**: Kotlin Multiplatform
- **Dependency Injection**: Koin
- **Vector Database**: ChromaDB (via Docker)
- **LLM Integration**: Ollama (local LLM support)
- **Embeddings**: HTTP-based embedding service (Ollama-compatible)
- **Web Search**: Tavily API integration
- **Markdown Parsing**: JetBrains Markdown library
- **MCP Protocol**: Model Context Protocol SDK for agent exposure
- **Logging**: Kermit

### Architecture Layers

1. **UI Layer** (`ui/`, `jvmMain/`, `androidMain/`)
   - Compose UI components (shared and platform-specific)
   - State management via state holders
   - Theme and styling
   - Mobile-optimized navigation (Android)

2. **Domain Layer** (`core/domain/`)
   - Editor domain logic (autosave, undo/redo)
   - Search domain logic
   - Flashcard generation service
   - Business rules and state management

3. **Data Layer** (`data/`)
   - File system operations (platform-abstracted)
   - Settings persistence (platform-specific)
   - Repository pattern implementations
   - Flashcard service implementations

4. **RAG Layer** (`rag/`, `retrieval/`)
   - Vector store integration (ChromaDB)
   - Embedding generation
   - Document chunking and indexing
   - Retrieval and reranking

5. **Chat Layer** (`chat/`)
   - Chat service interfaces and implementations
   - Retrieval mode handling (NONE, RAG, WEB, HYBRID)
   - Prompt building
   - MasterAgent system with LLM-based intent classification
   - Concrete agents (CreateNoteAgent, SearchNoteAgent, SummarizeNoteAgent)
   - Conversation management with bounded memory

6. **Markdown Layer** (`markdown/`)
   - Markdown parsing and AST generation
   - HTML rendering
   - Block and inline node structures

7. **Platform Layer** (`platform/`)
   - Platform abstraction interfaces
   - Vault picker implementations
   - Settings configuration providers

8. **MCP Layer** (`mcp/`) - JVM only
   - MCP server implementation
   - Agent tool exposure
   - HTTP/SSE transport

### Key Design Choices

- **Dependency Injection**: Uses Koin for loose coupling and testability
- **State Management**: State holders pattern for UI state, domain state for business logic
- **Coroutines**: Extensive use of Kotlin coroutines for async operations
- **Modular Structure**: Clear separation of concerns with dedicated modules for RAG, chat, UI, etc.
- **Platform Abstraction**: Common code in `commonMain`, platform-specific in `jvmMain`/`androidMain`
- **Interface-Based Design**: Platform-agnostic interfaces in `commonMain`, implementations in platform-specific source sets
- **Graceful Degradation**: Features degrade gracefully when dependencies are unavailable

## Running the Project

### Prerequisites

**For Desktop (JVM):**
1. **Java Development Kit (JDK)**: JDK 17 or higher
2. **Gradle**: Included via Gradle Wrapper (`./gradlew`)
3. **Docker**: For running ChromaDB (required for RAG features)
4. **Ollama**: For local LLM support (optional but recommended)

**For Android:**
1. **Android Studio**: Hedgehog (2023.1.1) or later
2. **Android SDK**: API 24 (minimum) / API 35 (target)
3. **JDK**: JDK 17 or higher
4. **Docker**: For running ChromaDB (required for RAG features)
5. **Ollama**: For local LLM support (optional but recommended)

### Setup Steps

#### 1. Start ChromaDB

ChromaDB is required for vector storage and RAG functionality. Run it using Docker:

```bash
# Create a volume for persistence
docker volume create chroma-data

# Run ChromaDB container
docker run -d \
  --name chromadb \
  -p 8000:8000 \
  --mount type=volume,src=chroma-data,target=/data \
  -e IS_PERSISTENT=TRUE \
  -e PERSIST_DIRECTORY=/data \
  -e ANONYMIZED_TELEMETRY=FALSE \
  chromadb/chroma:latest
```

Verify ChromaDB is running:
```bash
curl http://localhost:8000/api/v1/heartbeat
```

#### 2. Configure Ollama (Optional)

If you want to use local LLMs, install and run Ollama:

```bash
# Install Ollama (macOS)
brew install ollama

# Or download from https://ollama.ai

# Start Ollama
ollama serve

# Pull a model (e.g., llama3.2)
ollama pull llama3.2
```

#### 3. Configure API Keys (Optional)

For web search functionality, add the API key to your `local.properties` file in the project root:

```properties
TAVILLY_API_KEY=your_tavily_api_key_here
```

#### 4. Build and Run

**Desktop (JVM):**

On macOS/Linux:
```bash
./gradlew :composeApp:run
```

On Windows:
```bash
.\gradlew.bat :composeApp:run
```

The application will start with a default window size of 1400x900 pixels.

**Android:**

1. Open the project in Android Studio
2. Select the `android` run configuration
3. Choose a device or emulator
4. Click Run

Or via command line:
```bash
./gradlew :composeApp:installDebug
```

**MCP Server (Desktop only):**

Run the MCP server to expose agents as tools:
```bash
./gradlew :composeApp:runMcpServer
```

Or with custom port:
```bash
MCP_PORT=9000 ./gradlew :composeApp:runMcpServer
```

### First Run

1. **Open a Folder**: Click the folder icon in the left sidebar to open a directory containing markdown files
2. **Index Your Notes**: The app will automatically index markdown files in the selected folder
3. **Start Chatting**: Use the chat panel to interact with your notes using AI

### Configuration

Settings are stored in a platform-specific location and can be accessed via the Settings dialog in the application. Key settings include:

- **RAG Settings**: ChromaDB URL, embedding model, LLM model, similarity thresholds, reranking options
- **Editor Settings**: Theme, font, tab size, line numbers, autosave interval
- **UI Settings**: Sidebar widths, panel sizes, colors, layout preferences
- **Chat Settings**: Default retrieval mode, conversation history management
- **App Settings**: Vault paths, recent folders, window state

**Settings Storage:**
- **Desktop (JVM)**: JSON file in user's application data directory
- **Android**: SharedPreferences with JSON serialization

## Project Structure

```
Krypton/
├── composeApp/
│   ├── src/
│   │   ├── commonMain/          # Shared code across platforms
│   │   │   ├── kotlin/org/krypton/
│   │   │   │   ├── chat/        # Chat service, agents, retrieval modes
│   │   │   │   ├── core/        # Domain logic (editor, search, flashcard)
│   │   │   │   ├── data/        # Data layer interfaces (file system, settings)
│   │   │   │   ├── markdown/    # Markdown parsing and rendering
│   │   │   │   ├── rag/         # RAG components (embedding, indexing, retrieval)
│   │   │   │   ├── retrieval/   # Retrieval orchestration services
│   │   │   │   ├── web/         # Web search integration (Tavily)
│   │   │   │   ├── platform/   # Platform abstractions (vault picker, settings)
│   │   │   │   ├── ui/          # Shared UI components and state holders
│   │   │   │   ├── prompt/     # Prompt building utilities
│   │   │   │   ├── config/     # Configuration defaults and models
│   │   │   │   └── util/       # Utilities (logging, ID generation, time)
│   │   │   └── sqldelight/      # Database schemas (if used)
│   │   ├── jvmMain/             # JVM/Desktop-specific code
│   │   │   ├── kotlin/          # Desktop UI, DI setup, MCP server, platform impls
│   │   │   └── composeResources/ # Resources (fonts, icons, SVG)
│   │   └── androidMain/         # Android-specific code
│   │       ├── kotlin/          # Android UI, platform implementations, SAF
│   │       └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml       # Dependency versions
├── build.gradle.kts
├── readme/                      # Detailed documentation
│   ├── architecture.md
│   ├── ai-chat.md
│   ├── agents.md
│   ├── android.md
│   └── coding-guidelines.md
└── README.md
```

## Documentation

For more detailed information, see:

- **[AI Chat Features](./readme/ai-chat.md)**: Detailed documentation on RAG pipeline, chat modes, and web search
- **[Architecture Guide](./readme/architecture.md)**: Deep dive into architecture, design choices, markdown parsing, and platform support
- **[Agents & Agentic Architecture](./readme/agents.md)**: Comprehensive guide to the agent system, including all available agents, MCP server, and how they work
- **[Android Support](./readme/android.md)**: Complete guide to Android support, including setup, features, and platform-specific implementations
- **[Coding Guidelines](./readme/coding-guidelines.md)**: Development guidelines and best practices

## Development

### Building

```bash
./gradlew build
```

### Running Tests

```bash
./gradlew test
```

### Hot Reload

The project uses Compose Hot Reload for faster development iteration. Changes to Compose UI code will hot-reload automatically.

### Platform-Specific Development

**Desktop Development:**
- Use `jvmMain` source set for desktop-specific code
- MCP server is JVM-only
- File system uses standard Java File API

**Android Development:**
- Use `androidMain` source set for Android-specific code
- Follow platform abstraction patterns
- Use Storage Access Framework (SAF) for file access
- See [Android Support](./readme/android.md) for detailed guidelines

## Key Features Summary

### Desktop (JVM)
- Full-featured markdown editor with live preview
- Multi-panel layout with docked sidebars
- AI chat with RAG, web search, and hybrid modes
- MasterAgent system with LLM-based intent classification
- Intelligent agents (create note, search, summarize)
- Vector search with ChromaDB
- Flashcard generation
- MCP server for external tool integration
- File management with full CRUD operations
- Customizable themes and settings

### Android
- Mobile-optimized navigation with bottom bar
- All core features from desktop version
- Slide-in drawers for sidebars
- Storage Access Framework (SAF) integration
- Touch-optimized UI components
- Shared business logic with desktop
- MCP server not available (JVM-only)
