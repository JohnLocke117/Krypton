# Krypton
Krypton is an AI-Powered Markdown Workspace for Learning and Research, built for Desktop and Android. Built with Kotlin Multiplatform, Krypton combines intelligent note-taking with a RAG-Powered Chat, Semantic Search, and Goal-Driven study tools to create a comprehensive knowledge management system and a "**Second Brain**".

You can treat Krypton as your final knowledge base and your second brain, as mentioned above. You can query your notes, make edits, learn a topic quickly, or learn a topic in detail. You can create timed Study Plans and generate flashcards as well for a quick crash course over a topic.


----
## Demonstration
Click below to watch the Video Demonstration for Krypton:

[![Watch the demo](https://img.youtube.com/vi/wDchsz8nmbo/hqdefault.jpg)](https://www.youtube.com/watch?v=wDchsz8nmbo)


---
## High-Level Overview
Here is a very basic high-level architecture of the app:

![high-level-overview](assets/images/high-level-overview.png)

The core logic is written in `src/commonMain` and the platform specific code is in `src/jvmMain` for Desktop and `src/androidMain` for Android.
Krypton makes use of some external services such as:
- Ollama/Gemini for Chat
- ChromaDB as the main VectorDB (both ChromaDB local and ChromaDB cloud are supported)
- Tavily for Web-enabled Searches and results

All services have been added to allow for graceful failure. However, either Ollama or GeminiAPI is necessary for normal chat. If ChromaDB or Tavily are unavailable, chat falls back to normal.

> [!IMPORTANT]
> The Desktop app can make use of either Ollama or GeminiAPI to generate its responses or embeddings. However, Android only support GeminiAPI for now.
> Similarly, Desktop can utilise local or cloud embeddings, while Android can only use ChromaDB Cloud as generating and storing embeddings locally was out of scope for this project in the given timeline. Maybe in the future yes would like to extend Android as well for a full local experience.


### Core Features
1. Markdown Notes & Vaults
	- Modern markdown editor with *live preview*, autosave, and full undo/redo.
	- Directory‑based vaults with full file and folder CRUD operations.
	- Per‑platform file access: native file system on Desktop and *SAF* on Android.

2. AI & Search
	- AI chat over your notes with multiple retrieval modes (NONE, RAG, WEB, HYBRID).
	- Retrieval‑augmented answers using semantic search over your local markdown vault.
	- Optional web search integration for up‑to‑date information and hybrid local+web answers.

3. Agentic Integration into the Chat
	- MasterAgent that classifies intent and routes to specialized tools.
	- Agents for **creating notes**, **searching notes**, **summarizing notes**, **generating flashcards**, and **managing study goals**.
	- Graceful fallback to normal chat when no specialized agent is needed.
	- All agents are also exposed as tools via MCP-SSE

4. Study Goals & Flashcards
	- Create study goals with topics and automatically matched notes.
	- Generate summaries and flashcards from notes to prepare quick or detailed study sessions.
	- Quiz mode with scoring and progress tracking across sessions and goals.

5. Cross‑Platform Experience using KMP (Kotlin Multiplatform)
	- Desktop app with multi‑panel layout and docked sidebars.
	- Android app with touch‑optimized UI and bottom navigation.
	- Shared core logic in Kotlin Multiplatform with thin platform layers for integrations.


---
## Getting Started
Here are two ways to setup Krypton. Complete Setup is recommended to test out all the features. Use the basic setup to test if the app builds.


|                                                   | Description                                               |
| ------------------------------------------------- | --------------------------------------------------------- |
| [Complete Setup](assets/readme/complete-setup.md) | Complete Krypton setup. Recommended.                      |
| [Basic Setup](assets/readme/basic-setup.md)       | Just the basic setup, use this to test if the app builds. |


----
## Detailed Overview of the App
I have also compiled a few other READMEs which you can view for a detailed low-level view of the app, how was it shaped, and the architectural decisions made along the way.

| README                                                         | Description                                                                                                                       |
| -------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| [Architecture Design](assets/readme/architecture-design.md)   | This README describes the entire architecture of the app and the key decisions made while building the app                        |
| [AI-Chat](assets/readme/ai-chat.md)                           | This README details the AI powered chat, how was it built and the RAG architecture                                                |
| [Agents](assets/readme/agents.md)                             | This README describes all the agents available in the app, how to use them, and how they are strung together by our `MasterAgent` |
| [Android Architecture](assets/readme/android-architecture.md) | This note describes more about the Android-side of the app, and how was it built.                                                 |


----
## License
Krypton is available with the [MIT License](LICENSE).
