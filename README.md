# Krypton

Krypton is a modern, AI-powered note-taking application built with Kotlin Multiplatform and Jetpack Compose. It combines a beautiful markdown editor with advanced Retrieval-Augmented Generation (RAG) capabilities, enabling intelligent chat interactions with your notes and web search integration.

## Overview

Krypton is designed to be your intelligent knowledge companion. It provides:

- **Rich Markdown Editor**: Live preview, syntax highlighting, and a modern editing experience
- **AI Chat Interface**: Interact with your notes using AI, with support for RAG, web search, and hybrid modes
- **Intelligent Agents**: Specialized agents for note creation, search, and summarization that understand natural language intents
- **Vector Search**: Automatic indexing of your markdown notes for semantic search
- **File Management**: Full file and folder CRUD operations with a clean, intuitive interface
- **Cross-Platform**: Built with Kotlin Multiplatform, targeting Desktop (JVM) with future Android support

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
- **Logging**: Kermit

### Architecture Layers

1. **UI Layer** (`ui/`, `jvmMain/kotlin/org/krypton/krypton/`)
   - Compose UI components
   - State management via state holders
   - Theme and styling

2. **Domain Layer** (`core/domain/`)
   - Editor domain logic (autosave, undo/redo)
   - Search domain logic
   - Business rules and state management

3. **Data Layer** (`data/`)
   - File system operations
   - Settings persistence
   - Repository pattern implementations

4. **RAG Layer** (`rag/`, `retrieval/`)
   - Vector store integration (ChromaDB)
   - Embedding generation
   - Document chunking and indexing
   - Retrieval and reranking

5. **Chat Layer** (`chat/`)
   - Chat service interfaces and implementations
   - Retrieval mode handling (NONE, RAG, WEB, HYBRID)
   - Prompt building
   - Agent system for intent-based actions

6. **Markdown Layer** (`markdown/`)
   - Markdown parsing and AST generation
   - HTML rendering
   - Block and inline node structures

### Key Design Choices

- **Dependency Injection**: Uses Koin for loose coupling and testability
- **State Management**: State holders pattern for UI state, domain state for business logic
- **Coroutines**: Extensive use of Kotlin coroutines for async operations
- **Modular Structure**: Clear separation of concerns with dedicated modules for RAG, chat, UI, etc.
- **Platform Abstraction**: Common code in `commonMain`, platform-specific in `jvmMain`

## Running the Project

### Prerequisites

1. **Java Development Kit (JDK)**: JDK 11 or higher
2. **Gradle**: Included via Gradle Wrapper (`./gradlew`)
3. **Docker**: For running ChromaDB (required for RAG features)
4. **Ollama**: For local LLM support (optional but recommended)

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

For web search functionality, create a `local.secrets.properties` file in the project root:

```properties
TAVILLY_API_KEY=your_tavily_api_key_here
```

#### 4. Build and Run

**On macOS/Linux:**
```bash
./gradlew :composeApp:run
```

**On Windows:**
```bash
.\gradlew.bat :composeApp:run
```

The application will start with a default window size of 1400x900 pixels.

### First Run

1. **Open a Folder**: Click the folder icon in the left sidebar to open a directory containing markdown files
2. **Index Your Notes**: The app will automatically index markdown files in the selected folder
3. **Start Chatting**: Use the chat panel to interact with your notes using AI

### Configuration

Settings are stored in a JSON file and can be accessed via the Settings dialog in the application. Key settings include:

- **RAG Settings**: ChromaDB URL, embedding model, LLM model, similarity thresholds
- **Editor Settings**: Theme, font, tab size, line numbers
- **UI Settings**: Sidebar widths, panel sizes, colors

## Project Structure

```
Krypton/
├── composeApp/
│   ├── src/
│   │   ├── commonMain/          # Shared code across platforms
│   │   │   ├── kotlin/org/krypton/krypton/
│   │   │   │   ├── chat/        # Chat service interfaces
│   │   │   │   ├── core/        # Domain logic
│   │   │   │   ├── data/        # Data layer
│   │   │   │   ├── markdown/    # Markdown parsing
│   │   │   │   ├── rag/         # RAG components
│   │   │   │   ├── retrieval/   # Retrieval services
│   │   │   │   └── web/         # Web search
│   │   │   └── sqldelight/      # Database schemas
│   │   └── jvmMain/             # JVM-specific code
│   │       ├── kotlin/          # UI components, DI setup
│   │       └── composeResources/ # Resources (fonts, icons)
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml       # Dependency versions
├── build.gradle.kts
└── README.md
```

## Documentation

For more detailed information, see:

- **[AI Chat Features](./readme/ai-chat.md)**: Detailed documentation on RAG pipeline, chat modes, and web search
- **[Architecture Guide](./readme/architecture.md)**: Deep dive into architecture, design choices, and markdown parsing
- **[Agents & Agentic Architecture](./readme/agents.md)**: Comprehensive guide to the agent system, including all available agents and how they work

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

## License

[Add your license here]

## Contributing

[Add contribution guidelines here]
