# Android Support

Krypton includes full Android support with a mobile-optimized UI and platform-specific implementations. The Android version shares the same core business logic as the desktop version while providing a native mobile experience.

## Overview

The Android implementation provides:

- **Mobile-Optimized UI**: Navigation-based interface with dedicated screens for Notes, Editor, Chat, and Settings
- **Storage Access Framework (SAF)**: Secure file access using Android's SAF for vault selection
- **Platform-Specific Implementations**: Android-native file system, settings persistence, and vault picker
- **Full Feature Parity**: All core features available including RAG, agents, chat, and markdown editing
- **Shared Business Logic**: Common code in `commonMain` ensures consistency across platforms

## Architecture

### Platform Abstraction

Krypton uses platform abstractions to maintain clean separation between shared and platform-specific code:

```
commonMain/
├── platform/
│   ├── VaultPicker (interface)
│   └── SettingsConfigProvider (interface)
└── data/
    └── FileSystem (interface)

androidMain/
├── platform/
│   ├── AndroidVaultPicker (implementation)
│   └── AndroidSettingsConfigProvider (implementation)
└── data/files/impl/
    └── AndroidFileSystem (implementation)
```

### Dependency Injection

Android-specific dependencies are provided via `androidPlatformModule`:

```kotlin
val androidPlatformModule = module {
    single<VaultPicker> { AndroidVaultPicker(androidContext()) }
    single<SettingsConfigProvider> { AndroidSettingsConfigProvider(androidContext()) }
    single<FileSystem> { AndroidFileSystem(androidContext()) }
    single<SettingsPersistence> { 
        AndroidSettingsPersistence(androidContext(), get())
    }
}
```

The module is registered in `MainActivity.onCreate()`:

```kotlin
startKoin {
    androidContext(this@MainActivity)
    modules(allModules + androidPlatformModule)
}
```

## UI Architecture

### Navigation Structure

The Android UI uses a bottom navigation bar with four main screens:

1. **Notes List**: File browser and vault selection
2. **Editor**: Markdown editor with live preview
3. **Chat**: AI chat interface with RAG support
4. **Settings**: Application settings

### Screen Implementation

**Main Activity:**
- `MainActivity.kt`: Entry point, initializes Koin DI
- Uses `KryptonApp.android.kt` for platform-specific UI

**Screen Components:**
- `AndroidNotesListScreen`: File browser with SAF integration
- `AndroidEditorScreen`: Markdown editor optimized for mobile
- `AndroidChatScreen`: Chat interface with message history
- `AndroidSettingsScreen`: Settings management

### Mobile UI Features

- **Bottom Navigation**: Easy access to main features
- **Top App Bar**: Context-aware title and actions
- **Card-Based Layout**: Material Design 3 components
- **Responsive Design**: Adapts to different screen sizes
- **Touch-Optimized**: Large touch targets, swipe gestures

## Platform-Specific Implementations

### Platform Restrictions

**Important:** Android has specific platform restrictions compared to Desktop:

1. **LLM Provider**: Only Gemini API is supported (Ollama is not available on Android)
2. **Vector Backend**: Only ChromaDB Cloud is supported (local ChromaDB via Docker is not available)
3. **MCP Server**: MCP server is JVM-only and not available on Android
4. **Memory Limits**: Conversation memory is more conservative (15 messages max, 6,000 chars max) compared to Desktop (50 messages, 16,000 chars)

These restrictions are enforced at the settings level to prevent configuration errors.

### File System (`AndroidFileSystem`)

**Location:** `composeApp/src/androidMain/kotlin/org/krypton/data/files/impl/AndroidFileSystem.kt`

**Features:**
- Uses standard Java `File` API
- Handles Android storage permissions
- Supports app-internal and external storage
- Error handling with `FileError` sealed class
- SAF (Storage Access Framework) integration for external storage

**Key Methods:**
- `listFiles()`: Lists files in directory
- `readFile()`: Reads file content
- `writeFile()`: Writes file content
- `createFile()` / `createDirectory()`: Creates files/directories
- `deleteFile()`: Deletes files (no system trash on Android)
- `moveToTrash()`: Falls back to delete (Android doesn't have system trash)

### Settings Persistence (`AndroidSettingsPersistence`)

**Location:** `composeApp/src/androidMain/kotlin/org/krypton/data/repository/impl/AndroidSettingsPersistence.kt`

**Implementation:**
- Uses Android `SharedPreferences` for storage
- JSON serialization for complex settings objects
- Reactive updates via `StateFlow`
- Automatic migration and validation

**Storage Location:**
- App's private data directory
- Accessible only by the app
- Persists across app restarts

### Vault Picker (`AndroidVaultPicker`)

**Location:** `composeApp/src/androidMain/kotlin/org/krypton/platform/AndroidVaultPicker.kt`

**Current Implementation:**
- Uses app-internal storage (`context.filesDir/vault`)
- Creates default vault directory if it doesn't exist

**Future Enhancements:**
- Storage Access Framework (SAF) integration for external directories
- User-selected vault locations
- Support for cloud storage providers

**SAF Integration (In Progress):**
The UI already includes SAF integration in `AndroidNotesListScreen`:
- Uses `ActivityResultContracts.OpenDocumentTree()` for folder selection
- Grants persistent URI permissions
- Extracts file paths from URIs when possible

### Settings Config Provider (`AndroidSettingsConfigProvider`)

**Location:** `composeApp/src/androidMain/kotlin/org/krypton/platform/AndroidSettingsConfigProvider.kt`

**Features:**
- Provides platform-specific default settings
- Handles Android-specific configuration paths
- Manages app version and migration logic
- Enforces platform restrictions (Gemini-only, ChromaDB Cloud-only)

### Study Persistence (`AndroidStudyPersistence`)

**Location:** `composeApp/src/androidMain/kotlin/org/krypton/data/study/impl/AndroidStudyPersistence.kt`

**Features:**
- Persists study goals, sessions, and related data
- Uses JSON serialization
- Stores data in `.krypton/study/` directory within vault
- Platform-specific file system integration

### Conversation Persistence (`AndroidConversationPersistence`)

**Location:** `composeApp/src/androidMain/kotlin/org/krypton/data/chat/impl/AndroidConversationPersistence.kt`

**Features:**
- Persists chat conversations per vault
- Stores conversations in `.krypton/chat/` directory
- Uses JSON serialization
- Platform-specific file system integration

## Features

### Core Features Available

All desktop features are available on Android:

✅ **Markdown Editor**
- Live preview mode
- Syntax highlighting
- Auto-save with configurable interval
- Undo/redo support
- Line numbers (configurable)
- Customizable font and tab size

✅ **AI Chat**
- RAG mode (with ChromaDB Cloud only)
- Web search mode (Tavily API)
- Hybrid mode (combines RAG + web)
- Plain chat mode (NONE)
- Agent system (CreateNoteAgent, SearchNoteAgent, SummarizeNoteAgent, FlashcardAgent, StudyAgent)
- Conversation history with bounded memory (15 messages max, 6,000 chars max)
- Streaming responses
- **Platform Restriction**: Android only supports Gemini API (Ollama not available)

✅ **File Management**
- Create, read, update, delete files
- Create and navigate folders
- File search (keyword-based)
- Recent folders tracking
- Vault selection via SAF

✅ **Vector Search**
- Automatic indexing of markdown files
- Semantic search via ChromaDB Cloud (local ChromaDB not supported)
- RAG retrieval with reranking
- Incremental updates (only changed files re-indexed)
- **Platform Restriction**: Android only supports ChromaDB Cloud (local ChromaDB via Docker not available)

✅ **Settings**
- Editor preferences (theme, font, tab size, line numbers)
- RAG configuration (ChromaDB URL, models, thresholds)
- UI customization (colors, sidebar widths)
- Chat settings (default retrieval mode)
- App settings (vault paths, recent folders)

✅ **Flashcards**
- AI-powered flashcard generation from notes
- Question-answer pairs
- Source file tracking
- FlashcardAgent for chat-based generation

✅ **Study System**
- Create study goals with topics
- Automatic note matching for goals
- Session planning
- Roadmap generation
- Session preparation (summaries and flashcards)
- StudyAgent for chat-based management

### Mobile-Specific Features

- **Bottom Navigation**: Quick access to main features
- **Touch Gestures**: Swipe navigation, long-press actions
- **Mobile Keyboard**: Optimized input handling
- **Screen Rotation**: Layout adapts to orientation

## Building and Running

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 24 (minimum) / 35 (target)
- JDK 17 or higher
- Gradle (included via wrapper)

### Build Configuration

**Minimum SDK:** 24 (Android 7.0 Nougat)
**Target SDK:** 35 (Android 15)
**Compile SDK:** 35

**Key Dependencies:**
- Jetpack Compose Multiplatform
- Koin for Android
- Ktor Client (CIO engine)
- Material 3 components

### Building

**Debug Build:**
```bash
./gradlew :composeApp:assembleDebug
```

**Release Build:**
```bash
./gradlew :composeApp:assembleRelease
```

**Install on Device:**
```bash
./gradlew :composeApp:installDebug
```

### Running in Android Studio

1. Open the project in Android Studio
2. Select the `android` run configuration
3. Choose a device or emulator
4. Click Run

### APK Location

After building, the APK can be found at:
```
composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

## Configuration

### Android Manifest

**Location:** `composeApp/src/androidMain/AndroidManifest.xml`

**Key Settings:**
- Package: `org.krypton.krypton`
- Main Activity: `org.krypton.MainActivity`
- Theme: Material Light (NoActionBar)

**Permissions:**
Currently, no special permissions are required. File access uses:
- App-internal storage (no permission needed)
- Storage Access Framework (user grants permission via system dialog)

### ProGuard/R8

For release builds, add ProGuard rules if needed:
- Ktor serialization
- Kotlin reflection (if used)
- Compose runtime

## Storage Access Framework (SAF)

### Current Implementation

The Android UI includes SAF integration for vault selection:

**Features:**
- Folder picker using `OpenDocumentTree`
- Persistent URI permissions
- Path extraction from URIs

**Limitations:**
- Some storage providers don't expose file paths
- Falls back to default vault if path extraction fails
- DocumentFile API may be needed for full SAF support

### Future Enhancements

1. **Full SAF Support**: Use DocumentFile API for all file operations
2. **Cloud Storage**: Support for Google Drive, Dropbox, etc.
3. **Multiple Vaults**: Support for multiple vault locations
4. **Vault Migration**: Move vaults between locations

## Testing

### Unit Tests

Android-specific implementations can be tested with:
- Mock Android Context
- Mock FileSystem
- Test doubles for platform abstractions

### UI Tests

Compose UI tests can be written using:
- Compose Test framework
- Espresso (if needed)
- Robolectric (for faster tests)

### Manual Testing Checklist

- [ ] Vault selection and opening
- [ ] File creation, editing, deletion
- [ ] Markdown editing and preview
- [ ] Chat with RAG enabled
- [ ] Agent functionality (create note, search, summarize)
- [ ] Settings persistence
- [ ] Screen rotation
- [ ] App backgrounding/foregrounding

## Known Limitations

### Current Limitations

1. **Vault Location**: Default vault uses app-internal storage; external vaults via SAF are supported but path extraction may fail for some providers
2. **File Paths**: Some SAF providers (cloud storage) don't expose file paths, requiring DocumentFile API for full support
3. **MCP Server**: MCP server is JVM-only (not available on Android)
4. **Desktop-Specific Features**: Some desktop UI features (multi-panel layout, docked sidebars) are replaced with mobile-optimized alternatives

### Platform Differences

**Desktop vs Android:**
- Desktop: File system access via standard paths
- Android: SAF for external storage, app-internal for default

**UI Differences:**
- Desktop: Multi-panel layout with sidebars
- Android: Single-screen navigation with bottom bar

**Feature Parity:**
- Core features: ✅ Full parity (editor, chat, RAG, file management, agents, flashcards, study system)
- UI features: ⚠️ Mobile-optimized (navigation-based instead of multi-panel)
- Advanced features: ⚠️ MCP server is desktop-only (all other features available)
- Platform restrictions: ⚠️ Gemini-only, ChromaDB Cloud-only (Ollama and local ChromaDB not available)

## Troubleshooting

### Common Issues

**Vault Not Opening:**
- Check app storage permissions
- Verify vault directory exists
- Check logs for file system errors

**File Access Errors:**
- Ensure SAF permissions are granted
- Check if path extraction is working
- Verify file exists and is readable

**Settings Not Persisting:**
- Check SharedPreferences access
- Verify JSON serialization
- Check logs for persistence errors

**RAG Not Working:**
- Verify ChromaDB is accessible from Android device/emulator
- Check network connectivity
- Verify Ollama is running and accessible
- Check RAG settings in app

### Debugging

**Enable Logging:**
- Kermit logger is configured by default
- Check Logcat for `Krypton` tag
- Use `AppLogger` for structured logging

**Check Dependencies:**
```bash
./gradlew :composeApp:dependencies
```

**Verify Build:**
```bash
./gradlew :composeApp:clean :composeApp:build
```

## Future Enhancements

### Planned Improvements

1. **Full SAF Support**: Complete DocumentFile API integration
2. **Cloud Storage**: Google Drive, Dropbox integration
3. **Offline Mode**: Better offline support for RAG
4. **Widgets**: Home screen widgets for quick access
5. **Notifications**: Background indexing notifications
6. **Share Integration**: Share to Krypton from other apps
7. **File Provider**: Share files from Krypton
8. **Material You**: Dynamic color theming

### Performance Optimizations

1. **Lazy Loading**: Load files on demand
2. **Caching**: Cache rendered markdown
3. **Background Indexing**: Index files in background
4. **Image Optimization**: Optimize images for mobile

## Contributing

When adding Android-specific features:

1. **Follow Platform Abstraction**: Use interfaces in `commonMain`
2. **Implement in androidMain**: Provide Android-specific implementation
3. **Update DI**: Add to `androidPlatformModule` if needed
4. **Test on Device**: Test on real device, not just emulator
5. **Document**: Update this file with new features

## Resources

- [Android Developer Documentation](https://developer.android.com)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Storage Access Framework](https://developer.android.com/guide/topics/providers/document-provider)
- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)

