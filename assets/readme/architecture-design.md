# Krypton Architecture Overview

This document provides a comprehensive overview of Krypton's architecture, design choices, and implementation details. Krypton is an AI-Powered Markdown Workspace for Learning and Research, built as a "Second Brain" that combines intelligent note-taking with RAG-powered chat, semantic search, and goal-driven study tools.


----
## Introduction

Krypton is built using Kotlin Multiplatform, enabling code sharing between Desktop (JVM) and Android platforms while maintaining platform-specific implementations where needed. The application follows a layered, modular architecture with clear separation of concerns, making it maintainable, testable, and extensible.

### Core Philosophy
- **Platform Independence**: Shared core logic in `commonMain`, thin platform layers for UI and integrations
- **Modularity**: Clear module boundaries with well-defined responsibilities
- **Graceful Degradation**: Features degrade gracefully when dependencies are unavailable
- **Type Safety**: Leverages Kotlin's type system for compile-time safety
- **Reactive**: Uses StateFlow and coroutines for reactive, non-blocking operations


---
## Overall Architecture
Krypton follows a layered, modular architecture with clear separation of concerns. The application is built using Kotlin Multiplatform, allowing code sharing between platforms while maintaining platform-specific implementations where needed.

### Detailed Architecture Diagram

The following Mermaid diagram illustrates the low-level detailed architecture of Krypton, showing all major components and their relationships:

![[Pasted image 20260104225436.png]]


----
## Project Structure

```
composeApp/src/
├── commonMain/              # Shared code across platforms
│   ├── kotlin/org/krypton/
│   │   ├── chat/           # Chat interfaces, agents, retrieval modes
│   │   ├── core/           # Domain logic (editor, search, flashcard)
│   │   ├── data/           # Data layer interfaces
│   │   ├── markdown/       # Markdown parsing and rendering
│   │   ├── rag/            # RAG components (embedding, indexing, retrieval)
│   │   ├── retrieval/      # Retrieval orchestration
│   │   ├── web/            # Web search integration
│   │   ├── platform/       # Platform abstractions
│   │   ├── ui/             # Shared UI components and state holders
│   │   ├── prompt/         # Prompt building
│   │   ├── config/         # Configuration defaults
│   │   └── util/           # Utilities (logging, ID, time)
│   └── sqldelight/         # Database schemas (if used)
├── jvmMain/                # JVM/Desktop-specific code
│   ├── kotlin/             # Desktop UI, DI setup, MCP server, platform impls
│   └── composeResources/   # Resources (fonts, icons, SVG)
└── androidMain/            # Android-specific code
    ├── kotlin/             # Android UI, platform implementations, SAF
    └── AndroidManifest.xml # Android manifest
```


----
## Key Design Choices

### 1. Dependency Injection with Koin

**Why Koin?**
- Lightweight and simple
- No code generation required
- Works well with Kotlin Multiplatform
- Easy to test

**Structure:**
- Modules organized by feature (RAG, Chat, UI, Data)
- Singleton services for shared resources
- Factory functions for stateful components

**Example:**
```kotlin
val ragModule = module {
    single<LlamaClient> { /* ... */ }
    single<VectorStore> { /* ... */ }
    single<RagComponents?> { /* ... */ }
}
```

### 2. State Management Pattern

**State Holders:**
- UI state managed by dedicated state holder classes
- Observable state using `StateFlow` or `MutableStateFlow`
- Separation between UI state and domain state

**Domain State:**
- Business logic state in domain layer
- Immutable state objects
- State transitions through domain functions

**Example:**
```kotlin
class EditorStateHolder {
    val domainState: StateFlow<EditorDomainState> = ...
    val leftSidebarVisible: StateFlow<Boolean> = ...
}
```

### 3. Coroutines for Async Operations

**Why Coroutines?**
- Native Kotlin support
- Structured concurrency
- Easy cancellation
- Non-blocking I/O

**Usage:**
- All network operations use `suspend` functions
- File I/O operations are async
- UI updates on main dispatcher
- Background work on `Dispatchers.Default` or `Dispatchers.IO`

### 4. Platform Abstraction

**Common Code:**
- Business logic
- Domain models
- Interfaces and abstractions

**Platform-Specific:**
- UI components (JVM-specific Compose Desktop)
- File system operations
- Dependency injection setup
- Resource loading

### 5. Modular Architecture

**Benefits:**
- Clear separation of concerns
- Easy to test individual modules
- Can swap implementations (e.g., different vector stores)
- Parallel development

**Module Boundaries:**
- Each module has a clear responsibility
- Dependencies flow in one direction (UI → Domain → Data)
- Infrastructure modules are independent

## Markdown Parsing

Krypton uses a custom markdown parsing system built on top of JetBrains Markdown library. The system provides both parsing and rendering capabilities.

### Architecture

```
MarkdownEngine (interface)
    ↓
JetBrainsMarkdownEngine (implementation)
    ↓
AST Conversion
    ↓
BlockNode/InlineNode (custom AST)
    ↓
Compose Rendering
```

### MarkdownEngine Interface

The `MarkdownEngine` interface abstracts markdown parsing:

```kotlin
interface MarkdownEngine {
    fun parseToAst(markdown: String): MarkdownAst
    fun renderToHtml(markdown: String): String
    fun renderToBlocks(markdown: String): List<BlockNode>
}
```

### AST Structure

The custom AST uses sealed classes for type safety:

**Block Nodes:**
- `Heading(level, text, inlineNodes)`
- `Paragraph(inlineNodes)`
- `CodeBlock(code, language)`
- `Blockquote(blocks)`
- `UnorderedList(items)`
- `OrderedList(items, startNumber)`
- `HorizontalRule`

**Inline Nodes:**
- `Text(content)`
- `Strong(inlineNodes)`
- `Emphasis(inlineNodes)`
- `Code(code)`
- `Link(text, url)`
- `Image(alt, url)`

### Parsing Process

1. **JetBrains Parser**: Raw markdown is parsed using JetBrains Markdown parser
2. **AST Conversion**: JetBrains AST is converted to custom `BlockNode`/`InlineNode` structure
3. **Rendering**: Custom AST is rendered to Compose UI

### Implementation Details

#### JetBrainsMarkdownEngine
The implementation converts JetBrains AST nodes to custom nodes:

```kotlin
class JetBrainsMarkdownEngine : MarkdownEngine {
    private val flavour = CommonMarkFlavourDescriptor()
    private val parser = MarkdownParser(flavour)
    
    override fun parseToAst(markdown: String): MarkdownAst {
        val parsedTree = parser.buildMarkdownTreeFromString(markdown)
        val blocks = convertToBlocks(parsedTree.children, markdown)
        return MarkdownAst(blocks)
    }
}
```

#### Node Conversion

**Block Conversion:**
- ATX headings (`#`, `##`, etc.) → `BlockNode.Heading`
- Setext headings (`===`, `---`) → `BlockNode.Heading`
- Code blocks (fenced and indented) → `BlockNode.CodeBlock`
- Lists → `BlockNode.UnorderedList` or `BlockNode.OrderedList`
- Blockquotes → `BlockNode.Blockquote`

**Inline Conversion:**
- Text nodes → `InlineNode.Text`
- Emphasis (`*text*`, `_text_`) → `InlineNode.Emphasis`
- Strong (`**text**`, `__text__`) → `InlineNode.Strong`
- Code spans (`` `code` ``) → `InlineNode.Code`
- Links → `InlineNode.Link`
- Images → `InlineNode.Image`

#### Special Handling
**Code Blocks:**
- Fenced code blocks (```` ```language ````) extract language
- Indented code blocks preserve all content
- Language is stored for syntax highlighting (future feature)

**Lists:**
- Ordered lists extract start number
- Nested lists are supported through recursive conversion
- List items can contain multiple blocks

**Links and Images:**
- Link text and URL are extracted separately
- Image alt text and URL are extracted
- Relative URLs are preserved

### Rendering to Compose
The custom AST is rendered directly to Compose UI:

```kotlin
@Composable
fun MarkdownCompiledView(markdown: String, ...) {
    val engine = remember { JetBrainsMarkdownEngine() }
    val blocks = remember(markdown) {
        engine.renderToBlocks(markdown)
    }
    
    Column {
        blocks.forEach { block ->
            RenderBlock(block, ...)
        }
    }
}
```

**Block Rendering:**
- Headings use Material typography with appropriate sizes
- Paragraphs render inline nodes
- Code blocks use monospace font with background
- Lists render with proper indentation
- Blockquotes have distinct styling

**Inline Rendering:**
- Text uses default styling
- Strong uses bold font weight
- Emphasis uses italic font style
- Code spans use monospace with background
- Links are clickable with distinct color

### HTML Rendering

The engine also supports HTML rendering for export or web preview:

```kotlin
override fun renderToHtml(markdown: String): String {
    val parsedTree = parser.buildMarkdownTreeFromString(markdown)
    return HtmlGenerator(markdown, parsedTree, flavour).generateHtml()
}
```


---
## File System Abstraction

### NoteFileSystem

Abstracts file system operations:

```kotlin
class NoteFileSystem(private val rootPath: String?) {
    fun listMarkdownFiles(): List<String>
    fun readFile(path: String): String?
    fun getFileLastModified(path: String): Long?
}
```

**Benefits:**
- Easy to swap implementations
- Testable with mock file systems
- Platform-specific optimizations possible


---
## Settings Management

### Settings Structure

Settings are stored as a serializable data class:

```kotlin
data class Settings(
    val version: Int,
    val editor: EditorSettings,
    val ui: UISettings,
    val colors: ColorSettings,
    val app: AppSettings,
    val rag: RagSettings
)
```

### Persistence

- Settings are persisted to JSON file
- `SettingsRepository` manages loading and saving
- Reactive updates via `StateFlow`
- Validation on load
