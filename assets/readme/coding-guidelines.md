# Krypton Coding Guidelines

Use these rules for every **new feature**, **refactor**, and **bug fix**.

## 1. Architecture & layering

- Keep the existing layering intact:
  - UI (Compose) → Chat/Retrieval → RAG/Domain → Data → Platform (jvmMain).  
- In `commonMain`:
  - Only platform‑agnostic code (no `java.*`, no desktop/Android APIs).  
  - Define **interfaces and models**, not platform implementations.  
- In `jvmMain` (and future platforms):
  - Implement `commonMain` interfaces.  
  - Wire everything via DI (Koin).  
- Do not introduce new direct dependencies from UI into data/RAG/platform layers; always go through an interface.

## 2. Interfaces vs implementations

- For any new capability:
  - Define an interface in `commonMain` (e.g., `FooService`, `FooRepository`).  
  - Implement it under `jvmMain/.../impl/`.  
- UI and higher layers depend on interfaces, never directly on new concrete classes.  
- Prefer **small, single‑responsibility interfaces** over large “god” interfaces.

## 3. Models & data classes

- Use clear, canonical models:
  - Reuse existing types when possible (`RagChunk`, `Embedding`, `SearchResult`, `ChatMessage`, `ChatResponse`, `WebSnippet`, etc.).  
- New data classes must:
  - Be immutable (`val` fields).  
  - Use KMP‑friendly fields only (`String`, `Int`, `Long`, `Double`, `Boolean`, lists/maps of these, or other models).  
  - Be serializable (no function types, no platform types).  
- Avoid duplicate/competing models for the same concept.

## 4. Configuration & defaults

- All default values (URLs, models, timeouts, chunk sizes, etc.) live in `config/` and typed config models.  
- New hard‑coded constants should:
  - Go into the appropriate `*Defaults` object.  
  - Be wired into services through `Settings` or constructor parameters.  
- Never hard‑code secrets, URLs, or model names in feature code.

## 5. Error handling

- Define domain‑specific exceptions (e.g., `ChatException`, `RagException`) rather than throwing `Exception`.  
- Catch low‑level exceptions in data/platform layers and translate them into domain exceptions or result types.  
- Do not swallow exceptions silently; either:
  - Log via `AppLogger`, or  
  - Surface an error state to the caller.  
- Never use `System.err.println` or `println` for production logging.

## 6. Logging

- Always use the shared logging abstraction (`Logger` / `AppLogger`) from `util/`.  
- Choose log levels appropriately: `debug`, `info`, `warn`, `error`.  
- Avoid logging secrets, API keys, or raw user content unnecessarily.

## 7. Coroutines & concurrency

- Use structured concurrency:
  - No `GlobalScope.launch`.  
  - Prefer injected scopes or `viewModelScope`‑equivalent patterns.  
- Mark IO‑heavy or long‑running work as `suspend` and dispatch to appropriate dispatcher (`IO` from DI if needed).  
- Respect cancellation; do not catch and ignore `CancellationException`.

## 8. Dependency Injection (Koin)

- Add bindings for new interfaces in the appropriate module:
  - `DataModule` for repositories, HTTP clients, file systems.  
  - `RagModule` for RAG components.  
  - `ChatModule` for chat services.  
  - `WebModule` for web search.  
  - `UiModule` for state holders / view models.  
- Bind interfaces to implementations (`single<FooService> { FooServiceImpl(...) }`).  
- Use `single` for heavy or shared objects (HTTP clients, vector stores) and `factory` for lightweight or request‑scoped components.

## 9. UI and Compose

- Keep composables small and focused; split large screens into sub‑composables (`*Panel`, `*Controls`, `*List`, etc.).  
- UI state should live in state holders / view models, not deep inside composables.  
- UI should never:
  - Do file/HTTP operations directly.  
  - Instantiate service implementations directly.  
- Shared UI utilities that need platform‑specific behavior must use `expect/actual` (e.g., tooltips).

## 10. Naming & structure

- Use descriptive, consistent names:
  - `*Service` for business services.  
  - `*Repository` for data access.  
  - `*Facade` for higher‑level composed operations.  
  - `*StateHolder` or `*ViewModel` for UI state.  
- Keep files and classes focused; prefer several small files over a single giant one.  
- Remove dead code and unused imports as part of changes.

## 11. Testing & testability

- Keep logic in pure functions or small services where possible to make testing easy.  
- Use abstractions (`TimeProvider`, `IdGenerator`, interfaces) instead of hard‑coded calls to system APIs.  
- New logic that is non‑trivial (parsing, chunking, retrieval strategies, prompt building) should be testable without UI or network.

## 12. Secrets & external services

- Read secrets only via `SecretsLoader` or platform‑specific secure storage; never commit secrets or test keys.  
- New external services (APIs, tools) must:
  - Have a clean interface in `commonMain`.  
  - Be implemented in `jvmMain` with configurable base URLs and timeouts.  
- Handle timeouts and failures explicitly; provide user‑friendly error messages.

## 13. Agent/tool readiness

- When adding new capabilities that might become tools later:
  - Design the interface as `input → output` with simple data models.  
  - Avoid hidden global state; use DI for shared dependencies.  
  - Keep side effects clear and documented.  

## 14. Documentation & comments

- Add KDoc to all public interfaces and data classes describing:
  - What it does.  
  - Inputs, outputs, and error conditions.  
- Use comments to explain **why** something is done, not just **what**.
