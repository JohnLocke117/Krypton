# Krypton on Android
Krypton’s Android app shares the same **core brain** as Desktop but swaps in a touch‑friendly UI, Android storage, and mobile‑safe limits for chat and RAG.


---
## High‑Level Differences
On both platforms, all core logic (notes, RAG, chat, agents, study system) lives in `commonMain`; only the “shell” changes.

- **Desktop (JVM)**  
  - Compose Desktop UI with multi‑panel layout and docked sidebars.  
  - Direct file system access to any folder on disk.  
  - Can use **Ollama or Gemini**, plus **local or cloud ChromaDB**, and runs the **MCP server**.

- **Android**  
  - Compose Android UI with bottom navigation and single‑screen flows.  
  - *Uses Android storage (app‑internal + SAF) for vaults.*
  - *Limited to **Gemini API** for LLM and **ChromaDB Cloud** for vectors; no Ollama, no local Chroma, no MCP server.*


----
## Code Structure: Shared vs Android‑Only
Both targets plug into the same interfaces from `commonMain`, but Android provides its own implementations.

```text
commonMain/
  platform/
    VaultPicker
    SettingsConfigProvider
  data/
    FileSystem
  chat/
    ConversationPersistence
  study/
    StudyPersistence
```

```text
jvmMain/
  platform/ DesktopVaultPicker, DesktopSettingsConfigProvider
  data/    DesktopFileSystem
  chat/    DesktopConversationPersistence
  study/   DesktopStudyPersistence
  mcp/     KryptonMcpServer (desktop only)
```

```text
androidMain/
  platform/ AndroidVaultPicker, AndroidSettingsConfigProvider
  data/     AndroidFileSystem
  chat/     AndroidConversationPersistence
  study/    AndroidStudyPersistence
```

The same DI graph is used on both platforms, with a `desktopPlatformModule` or `androidPlatformModule` providing the correct implementations.


---
## UI Architecture: Desktop vs Android
The UI on each platform is tailored to how people actually use it, but both talk to the same state holders from `commonMain`.

| Aspect        | Desktop UI                                         | Android UI                                                |
|--------------|-----------------------------------------------------|-----------------------------------------------------------|
| Shell        | Compose Desktop window                              | `MainActivity` with Compose Android                      |
| Layout       | Multi‑panel editor, docked sidebars, resizable panes| Single‑screen views with **bottom navigation**           |
| Screens      | Vault tree + editor + chat in one workspace         | Separate Notes, Editor, Chat, Settings screens           |
| Entry point  | Desktop `main()` + DI bootstrap                     | `MainActivity.onCreate()` + `androidPlatformModule`      |

Both sides use the same **state holders** (`EditorStateHolder`, `ChatStateHolder`, etc.), so behaviour is aligned; only presentation differs.


----
## Platform‑Specific Behaviour and Limits
Android imposes a few extra rules so things stay fast and battery‑friendly.

- **LLM & vectors**  
  - Desktop: Ollama *or* Gemini; local or cloud ChromaDB.  
  - Android: Gemini only; **ChromaDB Cloud** only.  

- **Conversation memory**  
  - Desktop: up to ~50 messages and 16,000 characters per prompt.  
  - Android: trimmed to ~15 messages and 6,000 characters to fit mobile models and hardware.  

- **Servers and tooling**  
  - MCP server and desktop‑style integrations only exist in `jvmMain`; Android doesn’t host an MCP server at all.  

All of this is enforced through Android‑specific settings and defaults so users cannot accidentally select unsupported combinations.


---
## Files, Settings, and Vaults on Android
Android replaces plain file paths and config files with OS‑friendly equivalents while still honouring the same contracts.

- **File system**  
  - Desktop: direct paths, system trash, full FS access.  
  - Android: `AndroidFileSystem` wraps the app’s storage and SAF; “move to trash” effectively becomes delete because Android has no shared trash.  

- **Settings**  
  - Desktop: stored via desktop‑specific persistence.  
  - Android: `AndroidSettingsPersistence` uses `SharedPreferences` + JSON, still exposing the same `Settings` data class to shared code.  

- **Vaults**  
  - Desktop: choose any folder on disk.  
  - Android: default vault in app‑internal storage plus optional folders chosen via SAF; `AndroidVaultPicker` implements the shared `VaultPicker` interface.  

In practice, this means the **experience is parallel**, but the implementation respects what each platform is good at.
