package org.krypton.mcp

import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.krypton.chat.agent.AgentContext
import org.krypton.chat.agent.AgentResult
import org.krypton.chat.agent.CreateNoteAgent
import org.krypton.chat.agent.SearchNoteAgent
import org.krypton.chat.agent.SummarizeNoteAgent
import org.krypton.chat.agent.FlashcardAgent
import org.krypton.chat.agent.StudyAgent
import org.krypton.data.repository.SettingsRepository
import org.krypton.di.allModules
import org.krypton.util.AppLogger
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin

/**
 * MCP Server entry point for exposing Krypton agents as MCP tools.
 * 
 * The server exposes the following tools:
 * - create_note: Create markdown notes in a vault
 * - search_notes: Search notes in a vault
 * - summarize_notes: Summarize notes or topics
 * - generate_flashcards: Generate flashcards from a note
 * - create_study_goal: Create a new study goal with topics
 * - plan_study_goal: Plan sessions for a study goal
 * - generate_roadmap: Generate roadmap for a study goal
 * - prepare_session: Prepare a study session (generate summaries/flashcards)
 */
fun main(args: Array<String>) {
    runBlocking {
        AppLogger.i("MCP", "Starting Krypton MCP Server...")
        
        // Start Dependency Injection
        startKoin {
            modules(allModules)
        }
        
        val koin = getKoin()
        
        // Get agents from DI
        val createNoteAgent: CreateNoteAgent = koin.get()
        val searchNoteAgent: SearchNoteAgent = koin.get()
        val summarizeNoteAgent: SummarizeNoteAgent = koin.get()
        val flashcardAgent: FlashcardAgent = koin.get()
        val studyAgent: StudyAgent = koin.get()
        val settingsRepository: SettingsRepository = koin.get()
        
        // Get port from environment variable or use default
        val port = System.getenv("MCP_PORT")?.toIntOrNull() ?: 8080
        
        AppLogger.i("MCP", "Starting HTTP server on port $port")
        
        // Create and start Ktor server with MCP transport
        embeddedServer(Netty, port = port, host = "0.0.0.0") {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = false
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            
            // Install MCP server - this handles the MCP protocol over HTTP
            mcp {
                val server = Server(
                    serverInfo = Implementation(
                        name = "krypton-mcp-server",
                        version = "0.1.0",
                    ),
                    options = ServerOptions(
                        capabilities = ServerCapabilities(
                            tools = ServerCapabilities.Tools()
                        )
                    )
                ) {
                    "Krypton MCP Server - Exposes note creation, search, summarization, flashcard generation, and study goal management capabilities."
                }
                
                // Register tools
                registerCreateNoteTool(server, createNoteAgent, settingsRepository)
                registerSearchNotesTool(server, searchNoteAgent, settingsRepository)
                registerSummarizeNotesTool(server, summarizeNoteAgent, settingsRepository)
                registerGenerateFlashcardsTool(server, flashcardAgent, settingsRepository)
                registerCreateStudyGoalTool(server, studyAgent, settingsRepository)
                registerPlanStudyGoalTool(server, studyAgent, settingsRepository)
                registerGenerateRoadmapTool(server, studyAgent, settingsRepository)
                registerPrepareSessionTool(server, studyAgent, settingsRepository)
                
                server
            }
        }.start(wait = true)
    }
}

/**
 * Builds an AgentContext for general operations (no current note).
 */
private fun buildAgentContext(
    vaultPath: String,
    settingsRepository: SettingsRepository
): AgentContext {
    val settings = settingsRepository.settingsFlow.value
    return AgentContext(
        currentVaultPath = vaultPath,
        settings = settings,
        currentNotePath = null
    )
}

/**
 * Builds an AgentContext for operations on a specific note.
 */
private fun buildAgentContextForCurrentNote(
    vaultPath: String,
    notePath: String,
    settingsRepository: SettingsRepository
): AgentContext {
    val settings = settingsRepository.settingsFlow.value
    return AgentContext(
        currentVaultPath = vaultPath,
        settings = settings,
        currentNotePath = notePath
    )
}

/**
 * Registers the create_note tool.
 */
private fun registerCreateNoteTool(
    server: Server,
    agent: CreateNoteAgent,
    settingsRepository: SettingsRepository
) {
    val schemaJson = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("vault_path", buildJsonObject {
                    put("type", "string")
                    put("description", "Path to the vault directory where the note should be created.")
                })
                put("topic", buildJsonObject {
                    put("type", "string")
                    put("description", "Topic/title of the note to create.")
                })
            })
            put("required", buildJsonArray {
                add(JsonPrimitive("vault_path"))
                add(JsonPrimitive("topic"))
            })
        }
    
    server.addTool(
        name = "create_note",
        description = "Create a markdown note in the specified vault.",
        inputSchema = ToolSchema(schemaJson)
    ) { params ->
        try {
            // Extract arguments from CallToolRequest object
            val paramsMap = when {
                params is CallToolRequest -> {
                    // MCP SDK passes CallToolRequest with params.arguments
                    val arguments = params.params?.arguments
                    when {
                        arguments is JsonObject -> {
                            // Arguments are in JsonObject format, convert to Map
                            arguments.entries.associate { (key, value) ->
                                key to when (value) {
                                    is JsonPrimitive -> value.content
                                    else -> value.toString()
                                }
                            }
                        }
                        arguments is Map<*, *> -> {
                            arguments
                        }
                        else -> {
                            emptyMap<Any?, Any?>()
                        }
                    }
                }
                params is Map<*, *> && params.containsKey("arguments") -> {
                    // Fallback: if it's a Map with arguments key
                    (params["arguments"] as? Map<*, *>) ?: emptyMap<Any?, Any?>()
                }
                params is Map<*, *> -> {
                    // Fallback: params is the arguments directly
                    params
                }
                else -> {
                    AppLogger.w("MCP", "create_note unexpected params type: ${params?.javaClass?.name}")
                    emptyMap<Any?, Any?>()
                }
            }
            
            val vaultPath = paramsMap["vault_path"] as? String
                ?: return@addTool CallToolResult(
                    isError = true,
                    content = listOf(
                        textContent("Missing required parameter: vault_path")
                    )
                )
            
            val topic = paramsMap["topic"] as? String
                ?: return@addTool CallToolResult(
                    isError = true,
                    content = listOf(
                        textContent("Missing required parameter: topic")
                    )
                )
            
            val context = buildAgentContext(vaultPath, settingsRepository)
            val noteResult = runBlocking {
                try {
                    agent.execute("create a note on $topic", emptyList(), context) as AgentResult.NoteCreated
                } catch (e: Exception) {
                    AppLogger.e("MCP", "Error executing create_note", e)
                    return@runBlocking null
                }
            }
            
            if (noteResult == null) {
                return@addTool CallToolResult(
                    isError = true,
                    content = listOf(
                        textContent("Failed to create note. Make sure the vault path is valid and the agent can access it.")
                    )
                )
            }
            
            val responseText = """
                Note created successfully:
                - Path: ${noteResult.filePath}
                - Title: ${noteResult.title}
                - Preview: ${noteResult.preview}
            """.trimIndent()
            
            CallToolResult(
                isError = false,
                content = listOf(
                    textContent(responseText)
                )
            )
        } catch (e: Exception) {
            AppLogger.e("MCP", "Error in create_note tool", e)
            CallToolResult(
                isError = true,
                content = listOf(
                    textContent("Failed to create note: ${e.message ?: "Unknown error"}")
                )
            )
        }
    }
}

/**
 * Registers the search_notes tool.
 */
private fun registerSearchNotesTool(
    server: Server,
    agent: SearchNoteAgent,
    settingsRepository: SettingsRepository
) {
    val searchSchemaJson = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("vault_path", buildJsonObject {
                    put("type", "string")
                    put("description", "Path to the vault directory to search in.")
                })
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "Search query text.")
                })
                put("limit", buildJsonObject {
                    put("type", "integer")
                    put("description", "Maximum number of matches to return.")
                    put("minimum", 1)
                })
            })
            put("required", buildJsonArray {
                add(JsonPrimitive("vault_path"))
                add(JsonPrimitive("query"))
            })
        }
    
    server.addTool(
        name = "search_notes",
        description = "Search notes in the specified vault for a query string using semantic and keyword search.",
        inputSchema = ToolSchema(searchSchemaJson)
    ) { params ->
        try {
            // Extract arguments from CallToolRequest object
            val paramsMap = when {
                params is CallToolRequest -> {
                    // MCP SDK passes CallToolRequest with params.arguments
                    val arguments = params.params?.arguments
                    AppLogger.i("MCP", "search_notes arguments type: ${arguments?.javaClass?.name}")
                    AppLogger.i("MCP", "search_notes arguments value: $arguments")
                    
                    when {
                        arguments is JsonObject -> {
                            // Arguments are in JsonObject format, convert to Map
                            arguments.entries.associate { (key, value) ->
                                key to when (value) {
                                    is JsonPrimitive -> value.content
                                    else -> value.toString()
                                }
                            }
                        }
                        arguments is Map<*, *> -> {
                            arguments
                        }
                        else -> {
                            AppLogger.w("MCP", "search_notes arguments is neither JsonObject nor Map: ${arguments?.javaClass?.name}")
                            emptyMap<Any?, Any?>()
                        }
                    }
                }
                params is Map<*, *> && params.containsKey("arguments") -> {
                    // Fallback: if it's a Map with arguments key
                    (params["arguments"] as? Map<*, *>) ?: emptyMap<Any?, Any?>()
                }
                params is Map<*, *> -> {
                    // Fallback: params is the arguments directly
                    params
                }
                else -> {
                    AppLogger.w("MCP", "search_notes unexpected params type: ${params?.javaClass?.name}")
                    emptyMap<Any?, Any?>()
                }
            }
            
            AppLogger.i("MCP", "search_notes paramsMap: $paramsMap")
            AppLogger.i("MCP", "search_notes paramsMap keys: ${paramsMap.keys}")
            
            val vaultPath = paramsMap["vault_path"] as? String
                ?: return@addTool CallToolResult(
                    isError = true,
                    content = listOf(
                        textContent("Missing required parameter: vault_path")
                    )
                )
            
            val query = paramsMap["query"] as? String
                ?: return@addTool CallToolResult(
                    isError = true,
                    content = listOf(
                        textContent("Missing required parameter: query")
                    )
                )
            
            val limit = (paramsMap["limit"] as? Number)?.toInt() ?: 20
            
            val context = buildAgentContext(vaultPath, settingsRepository)
            val notesFound = runBlocking {
                try {
                    agent.execute("search my notes for $query", emptyList(), context) as AgentResult.NotesFound
                } catch (e: Exception) {
                    AppLogger.e("MCP", "Error executing search_notes", e)
                    return@runBlocking null
                }
            }
            
            if (notesFound == null) {
                return@addTool CallToolResult(
                    isError = true,
                    content = listOf(
                        textContent("Failed to search notes. Make sure the vault path is valid and contains markdown files.")
                    )
                )
            }
            
            val limited = notesFound.results.take(limit)
            
            // Build JSON response
            val json = buildString {
                append("[\n")
                limited.forEachIndexed { index, match ->
                    append("  {\n")
                    append("    \"filePath\": \"${escapeJson(match.filePath)}\",\n")
                    append("    \"title\": \"${escapeJson(match.title)}\",\n")
                    append("    \"snippet\": \"${escapeJson(match.snippet)}\",\n")
                    append("    \"score\": ${match.score}\n")
                    append("  }")
                    if (index < limited.lastIndex) append(",")
                    append("\n")
                }
                append("]")
            }
            
            CallToolResult(
                isError = false,
                content = listOf(
                    textContent(json)
                )
            )
        } catch (e: Exception) {
            AppLogger.e("MCP", "Error in search_notes tool", e)
            CallToolResult(
                isError = true,
                content = listOf(
                    textContent("Failed to search notes: ${e.message ?: "Unknown error"}")
                )
            )
        }
    }
}

/**
 * Registers the summarize_notes tool.
 */
private fun registerSummarizeNotesTool(
    server: Server,
    agent: SummarizeNoteAgent,
    settingsRepository: SettingsRepository
) {
    val summarizeSchemaJson = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("vault_path", buildJsonObject {
                put("type", "string")
                put("description", "Path to the vault directory.")
            })
            put("mode", buildJsonObject {
                put("type", "string")
                put("description", "Either 'current_note' to summarize a specific note, or 'topic' to summarize notes about a topic.")
                put("enum", buildJsonArray {
                    add(JsonPrimitive("current_note"))
                    add(JsonPrimitive("topic"))
                })
            })
            put("note_path", buildJsonObject {
                put("type", "string")
                put("description", "Path to the note file to summarize (required when mode = 'current_note').")
            })
            put("topic", buildJsonObject {
                put("type", "string")
                put("description", "Topic to summarize when mode = 'topic'.")
            })
        })
        put("required", buildJsonArray {
            add(JsonPrimitive("vault_path"))
            add(JsonPrimitive("mode"))
        })
    }
    
    server.addTool(
        name = "summarize_notes",
        description = "Summarize either a specific note or notes on a topic from the vault.",
        inputSchema = ToolSchema(summarizeSchemaJson)
    ) { params ->
        try {
            // Extract arguments from CallToolRequest object
            val paramsMap = when {
                params is CallToolRequest -> {
                    // MCP SDK passes CallToolRequest with params.arguments
                    val arguments = params.params?.arguments
                    when {
                        arguments is JsonObject -> {
                            // Arguments are in JsonObject format, convert to Map
                            arguments.entries.associate { (key, value) ->
                                key to when (value) {
                                    is JsonPrimitive -> value.content
                                    else -> value.toString()
                                }
                            }
                        }
                        arguments is Map<*, *> -> {
                            arguments
                        }
                        else -> {
                            emptyMap<Any?, Any?>()
                        }
                    }
                }
                params is Map<*, *> && params.containsKey("arguments") -> {
                    // Fallback: if it's a Map with arguments key
                    (params["arguments"] as? Map<*, *>) ?: emptyMap<Any?, Any?>()
                }
                params is Map<*, *> -> {
                    // Fallback: params is the arguments directly
                    params
                }
                else -> {
                    AppLogger.w("MCP", "summarize_notes unexpected params type: ${params?.javaClass?.name}")
                    emptyMap<Any?, Any?>()
                }
            }
            
            val vaultPath = paramsMap["vault_path"] as? String
                ?: return@addTool CallToolResult(
                    isError = true,
                    content = listOf(
                        textContent("Missing required parameter: vault_path")
                    )
                )
            
            val mode = paramsMap["mode"] as? String
                ?: return@addTool CallToolResult(
                    isError = true,
                    content = listOf(
                        textContent("Missing required parameter: mode")
                    )
                )
            
            val context = when (mode) {
                "current_note" -> {
                    val notePath = paramsMap["note_path"] as? String
                        ?: return@addTool CallToolResult(
                            isError = true,
                            content = listOf(
                                textContent("Missing required parameter: note_path (required when mode = 'current_note')")
                            )
                        )
                    buildAgentContextForCurrentNote(vaultPath, notePath, settingsRepository)
                }
                "topic" -> {
                    val topic = paramsMap["topic"] as? String
                        ?: return@addTool CallToolResult(
                            isError = true,
                            content = listOf(
                                textContent("Missing required parameter: topic (required when mode = 'topic')")
                            )
                        )
                    buildAgentContext(vaultPath, settingsRepository)
                }
                else -> return@addTool CallToolResult(
                    isError = true,
                    content = listOf(
                        textContent("Invalid mode: $mode. Must be 'current_note' or 'topic'")
                    )
                )
            }
            
            val message = when (mode) {
                "current_note" -> "summarize this note"
                "topic" -> {
                    val topic = paramsMap["topic"] as? String ?: "unknown topic"
                    "summarize my notes on $topic"
                }
                else -> ""
            }
            
            val summary = runBlocking {
                try {
                    agent.execute(message, emptyList(), context) as AgentResult.NoteSummarized
                } catch (e: Exception) {
                    AppLogger.e("MCP", "Error executing summarize_notes", e)
                    return@runBlocking null
                }
            }
            
            if (summary == null) {
                return@addTool CallToolResult(
                    isError = true,
                    content = listOf(
                        textContent("Failed to summarize notes. Make sure the vault/note path is valid and contains content.")
                    )
                )
            }
            
            // Build JSON response with proper escaping
            val escapedSummary = escapeJson(summary.summary)
            val sourceFilesJson = summary.sourceFiles.joinToString(
                prefix = "[\"",
                postfix = "\"]",
                separator = "\",\""
            ) { escapeJson(it) }
            
            val json = """
                {
                  "title": "${escapeJson(summary.title)}",
                  "summary": "$escapedSummary",
                  "sourceFiles": $sourceFilesJson
                }
            """.trimIndent()
            
            CallToolResult(
                isError = false,
                content = listOf(
                    textContent(json)
                )
            )
        } catch (e: Exception) {
            AppLogger.e("MCP", "Error in summarize_notes tool", e)
            CallToolResult(
                isError = true,
                content = listOf(
                    textContent("Failed to summarize notes: ${e.message ?: "Unknown error"}")
                )
            )
        }
    }
}

/**
 * Registers the generate_flashcards tool.
 */
private fun registerGenerateFlashcardsTool(
    server: Server,
    agent: FlashcardAgent,
    settingsRepository: SettingsRepository
) {
    val schemaJson = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("vault_path", buildJsonObject {
                put("type", "string")
                put("description", "Path to the vault directory.")
            })
            put("note_path", buildJsonObject {
                put("type", "string")
                put("description", "Path to the note file to generate flashcards from.")
            })
            put("max_cards", buildJsonObject {
                put("type", "integer")
                put("description", "Maximum number of flashcards to generate.")
                put("default", 20)
                put("minimum", 1)
            })
        })
        put("required", buildJsonArray {
            add(JsonPrimitive("vault_path"))
            add(JsonPrimitive("note_path"))
        })
    }
    
    server.addTool(
        name = "generate_flashcards",
        description = "Generate flashcards from a markdown note.",
        inputSchema = ToolSchema(schemaJson)
    ) { params ->
        try {
            val paramsMap = extractParamsMap(params)
            
            val vaultPath = paramsMap["vault_path"] as? String
                ?: return@addTool CallToolResult(
                    isError = true,
                    content = listOf(textContent("Missing required parameter: vault_path"))
                )
            
            val notePath = paramsMap["note_path"] as? String
                ?: return@addTool CallToolResult(
                    isError = true,
                    content = listOf(textContent("Missing required parameter: note_path"))
                )
            
            val maxCards = (paramsMap["max_cards"] as? Number)?.toInt() ?: 20
            
            val context = buildAgentContextForCurrentNote(vaultPath, notePath, settingsRepository)
            val result = runBlocking {
                try {
                    agent.execute("generate flashcards from $notePath", emptyList(), context) as AgentResult.FlashcardsGenerated
                } catch (e: Exception) {
                    AppLogger.e("MCP", "Error executing generate_flashcards", e)
                    return@runBlocking null
                }
            }
            
            if (result == null) {
                return@addTool CallToolResult(
                    isError = true,
                    content = listOf(textContent("Failed to generate flashcards. Make sure the note path is valid and contains content."))
                )
            }
            
            // Build JSON response
            val flashcardsJson = result.flashcards.joinToString(
                prefix = "[",
                postfix = "]",
                separator = ","
            ) { flashcard ->
                """
                {
                  "question": "${escapeJson(flashcard.question)}",
                  "answer": "${escapeJson(flashcard.answer)}",
                  "sourceFile": "${escapeJson(flashcard.sourceFile ?: notePath)}"
                }
                """.trimIndent()
            }
            
            val responseText = """
                Generated ${result.count} flashcards from note: $notePath
                $flashcardsJson
            """.trimIndent()
            
            CallToolResult(
                isError = false,
                content = listOf(textContent(responseText))
            )
        } catch (e: Exception) {
            AppLogger.e("MCP", "Error in generate_flashcards tool", e)
            CallToolResult(
                isError = true,
                content = listOf(textContent("Failed to generate flashcards: ${e.message ?: "Unknown error"}"))
            )
        }
    }
}

/**
 * Registers the create_study_goal tool.
 */
private fun registerCreateStudyGoalTool(
    server: Server,
    agent: StudyAgent,
    settingsRepository: SettingsRepository
) {
    val schemaJson = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("vault_path", buildJsonObject {
                put("type", "string")
                put("description", "Path to the vault directory.")
            })
            put("title", buildJsonObject {
                put("type", "string")
                put("description", "Title of the study goal.")
            })
            put("description", buildJsonObject {
                put("type", "string")
                put("description", "Optional description of the goal.")
            })
            put("topics", buildJsonObject {
                put("type", "array")
                put("items", buildJsonObject {
                    put("type", "string")
                })
                put("description", "List of topics to study (each becomes a session).")
            })
            put("target_date", buildJsonObject {
                put("type", "string")
                put("description", "Optional target completion date (ISO-8601 format: YYYY-MM-DD).")
            })
        })
        put("required", buildJsonArray {
            add(JsonPrimitive("vault_path"))
            add(JsonPrimitive("title"))
            add(JsonPrimitive("topics"))
        })
    }
    
    server.addTool(
        name = "create_study_goal",
        description = "Create a new study goal with topics.",
        inputSchema = ToolSchema(schemaJson)
    ) { params ->
        try {
            val paramsMap = extractParamsMap(params)
            
            val vaultPath = paramsMap["vault_path"] as? String
                ?: return@addTool CallToolResult(
                    isError = true,
                    content = listOf(textContent("Missing required parameter: vault_path"))
                )
            
            val title = paramsMap["title"] as? String
                ?: return@addTool CallToolResult(
                    isError = true,
                    content = listOf(textContent("Missing required parameter: title"))
                )
            
            val topics = (paramsMap["topics"] as? List<*>)?.mapNotNull { it as? String }
                ?: return@addTool CallToolResult(
                    isError = true,
                    content = listOf(textContent("Missing required parameter: topics"))
                )
            
            val description = paramsMap["description"] as? String
            val targetDate = paramsMap["target_date"] as? String
            
            // Build message for agent (format: "create study goal for [title] about [topics] description: [desc] target date: [date]")
            val message = buildString {
                append("create study goal for $title")
                if (topics.isNotEmpty()) {
                    append(" about ${topics.joinToString(", ")}")
                }
                if (description != null && description.isNotBlank()) {
                    append(" description: $description")
                }
                if (targetDate != null && targetDate.isNotBlank()) {
                    append(" target date: $targetDate")
                }
            }
            
            val context = buildAgentContext(vaultPath, settingsRepository)
            val result = runBlocking {
                try {
                    agent.execute(message, emptyList(), context) as AgentResult.StudyGoalCreated
                } catch (e: Exception) {
                    AppLogger.e("MCP", "Error executing create_study_goal", e)
                    return@runBlocking null
                }
            }
            
            if (result == null) {
                return@addTool CallToolResult(
                    isError = true,
                    content = listOf(textContent("Failed to create study goal. Make sure the vault path is valid."))
                )
            }
            
            val topicsJson = result.topics.joinToString(
                prefix = "[\"",
                postfix = "\"]",
                separator = "\",\""
            ) { escapeJson(it) }
            
            val responseText = """
                Study goal created successfully:
                - Goal ID: ${result.goalId}
                - Title: ${escapeJson(result.title)}
                - Topics: $topicsJson
                - Matched Notes: ${result.matchedNotesCount}
            """.trimIndent()
            
            CallToolResult(
                isError = false,
                content = listOf(textContent(responseText))
            )
        } catch (e: Exception) {
            AppLogger.e("MCP", "Error in create_study_goal tool", e)
            CallToolResult(
                isError = true,
                content = listOf(textContent("Failed to create study goal: ${e.message ?: "Unknown error"}"))
            )
        }
    }
}

/**
 * Registers the plan_study_goal tool.
 */
private fun registerPlanStudyGoalTool(
    server: Server,
    agent: StudyAgent,
    settingsRepository: SettingsRepository
) {
    val schemaJson = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("vault_path", buildJsonObject {
                put("type", "string")
                put("description", "Path to the vault directory.")
            })
            put("goal_id", buildJsonObject {
                put("type", "string")
                put("description", "ID of the study goal to plan.")
            })
        })
        put("required", buildJsonArray {
            add(JsonPrimitive("vault_path"))
            add(JsonPrimitive("goal_id"))
        })
    }
    
    server.addTool(
        name = "plan_study_goal",
        description = "Plan sessions for a study goal (creates sessions for each topic).",
        inputSchema = ToolSchema(schemaJson)
    ) { params ->
        try {
            val paramsMap = extractParamsMap(params)
            
            val vaultPath = paramsMap["vault_path"] as? String
                ?: return@addTool CallToolResult(
                    isError = true,
                    content = listOf(textContent("Missing required parameter: vault_path"))
                )
            
            val goalId = paramsMap["goal_id"] as? String
                ?: return@addTool CallToolResult(
                    isError = true,
                    content = listOf(textContent("Missing required parameter: goal_id"))
                )
            
            val context = buildAgentContext(vaultPath, settingsRepository)
            val result = runBlocking {
                try {
                    agent.execute("plan study goal $goalId", emptyList(), context) as AgentResult.StudyGoalPlanned
                } catch (e: Exception) {
                    AppLogger.e("MCP", "Error executing plan_study_goal", e)
                    return@runBlocking null
                }
            }
            
            if (result == null) {
                return@addTool CallToolResult(
                    isError = true,
                    content = listOf(textContent("Failed to plan study goal. Make sure the goal ID is valid."))
                )
            }
            
            val topicsJson = result.topics.joinToString(
                prefix = "[\"",
                postfix = "\"]",
                separator = "\",\""
            ) { escapeJson(it) }
            
            val responseText = """
                Study goal planned successfully:
                - Goal ID: ${result.goalId}
                - Sessions Created: ${result.sessionsCreated}
                - Topics: $topicsJson
            """.trimIndent()
            
            CallToolResult(
                isError = false,
                content = listOf(textContent(responseText))
            )
        } catch (e: Exception) {
            AppLogger.e("MCP", "Error in plan_study_goal tool", e)
            CallToolResult(
                isError = true,
                content = listOf(textContent("Failed to plan study goal: ${e.message ?: "Unknown error"}"))
            )
        }
    }
}

/**
 * Registers the generate_roadmap tool.
 */
private fun registerGenerateRoadmapTool(
    server: Server,
    agent: StudyAgent,
    settingsRepository: SettingsRepository
) {
    val schemaJson = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("vault_path", buildJsonObject {
                put("type", "string")
                put("description", "Path to the vault directory.")
            })
            put("goal_id", buildJsonObject {
                put("type", "string")
                put("description", "ID of the study goal.")
            })
        })
        put("required", buildJsonArray {
            add(JsonPrimitive("vault_path"))
            add(JsonPrimitive("goal_id"))
        })
    }
    
    server.addTool(
        name = "generate_roadmap",
        description = "Generate a roadmap for a study goal.",
        inputSchema = ToolSchema(schemaJson)
    ) { params ->
        try {
            val paramsMap = extractParamsMap(params)
            
            val vaultPath = paramsMap["vault_path"] as? String
                ?: return@addTool CallToolResult(
                    isError = true,
                    content = listOf(textContent("Missing required parameter: vault_path"))
                )
            
            val goalId = paramsMap["goal_id"] as? String
                ?: return@addTool CallToolResult(
                    isError = true,
                    content = listOf(textContent("Missing required parameter: goal_id"))
                )
            
            val context = buildAgentContext(vaultPath, settingsRepository)
            val result = runBlocking {
                try {
                    agent.execute("generate roadmap for goal $goalId", emptyList(), context) as AgentResult.RoadmapGenerated
                } catch (e: Exception) {
                    AppLogger.e("MCP", "Error executing generate_roadmap", e)
                    return@runBlocking null
                }
            }
            
            if (result == null) {
                return@addTool CallToolResult(
                    isError = true,
                    content = listOf(textContent("Failed to generate roadmap. Make sure the goal ID is valid."))
                )
            }
            
            val escapedRoadmap = escapeJson(result.roadmap)
            val responseText = """
                Roadmap generated for goal ${result.goalId}:
                $escapedRoadmap
            """.trimIndent()
            
            CallToolResult(
                isError = false,
                content = listOf(textContent(responseText))
            )
        } catch (e: Exception) {
            AppLogger.e("MCP", "Error in generate_roadmap tool", e)
            CallToolResult(
                isError = true,
                content = listOf(textContent("Failed to generate roadmap: ${e.message ?: "Unknown error"}"))
            )
        }
    }
}

/**
 * Registers the prepare_session tool.
 */
private fun registerPrepareSessionTool(
    server: Server,
    agent: StudyAgent,
    settingsRepository: SettingsRepository
) {
    val schemaJson = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("vault_path", buildJsonObject {
                put("type", "string")
                put("description", "Path to the vault directory.")
            })
            put("session_id", buildJsonObject {
                put("type", "string")
                put("description", "ID of the study session to prepare.")
            })
        })
        put("required", buildJsonArray {
            add(JsonPrimitive("vault_path"))
            add(JsonPrimitive("session_id"))
        })
    }
    
    server.addTool(
        name = "prepare_session",
        description = "Prepare a study session by generating summaries and flashcards.",
        inputSchema = ToolSchema(schemaJson)
    ) { params ->
        try {
            val paramsMap = extractParamsMap(params)
            
            val vaultPath = paramsMap["vault_path"] as? String
                ?: return@addTool CallToolResult(
                    isError = true,
                    content = listOf(textContent("Missing required parameter: vault_path"))
                )
            
            val sessionId = paramsMap["session_id"] as? String
                ?: return@addTool CallToolResult(
                    isError = true,
                    content = listOf(textContent("Missing required parameter: session_id"))
                )
            
            val context = buildAgentContext(vaultPath, settingsRepository)
            val result = runBlocking {
                try {
                    agent.execute("prepare session $sessionId", emptyList(), context) as AgentResult.SessionPrepared
                } catch (e: Exception) {
                    AppLogger.e("MCP", "Error executing prepare_session", e)
                    return@runBlocking null
                }
            }
            
            if (result == null) {
                return@addTool CallToolResult(
                    isError = true,
                    content = listOf(textContent("Failed to prepare session. Make sure the session ID is valid."))
                )
            }
            
            val responseText = """
                Session prepared successfully:
                - Session ID: ${result.sessionId}
                - Topic: ${escapeJson(result.topic)}
                - Summaries Generated: ${result.summariesCount}
                - Flashcards Generated: ${result.flashcardsCount}
            """.trimIndent()
            
            CallToolResult(
                isError = false,
                content = listOf(textContent(responseText))
            )
        } catch (e: Exception) {
            AppLogger.e("MCP", "Error in prepare_session tool", e)
            CallToolResult(
                isError = true,
                content = listOf(textContent("Failed to prepare session: ${e.message ?: "Unknown error"}"))
            )
        }
    }
}

/**
 * Helper function to extract parameters map from MCP tool request.
 */
private fun extractParamsMap(params: Any?): Map<String, Any?> {
    return when {
        params is CallToolRequest -> {
            val arguments = params.params?.arguments
            when {
                arguments is JsonObject -> {
                    arguments.entries.associate { (key, value) ->
                        key to when (value) {
                            is JsonPrimitive -> value.content
                            is JsonArray -> {
                                buildList {
                                    for (element in value) {
                                        add(when (element) {
                                            is JsonPrimitive -> element.content
                                            else -> element.toString()
                                        })
                                    }
                                }
                            }
                            else -> value.toString()
                        }
                    }
                }
                arguments is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    arguments as Map<String, Any?>
                }
                else -> {
                    emptyMap()
                }
            }
        }
        params is Map<*, *> && params.containsKey("arguments") -> {
            @Suppress("UNCHECKED_CAST")
            (params["arguments"] as? Map<String, Any?>) ?: emptyMap()
        }
        params is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            params as Map<String, Any?>
        }
        else -> {
            AppLogger.w("MCP", "Unexpected params type: ${params?.javaClass?.name}")
            emptyMap()
        }
    }
}

/**
 * Creates a TextContent from a string message.
 */
private fun textContent(message: String): TextContent {
    return TextContent(text = message)
}

/**
 * Escapes special characters in JSON strings.
 */
private fun escapeJson(text: String): String {
    return text
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
