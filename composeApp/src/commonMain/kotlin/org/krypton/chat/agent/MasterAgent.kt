package org.krypton.chat.agent

/**
 * Master agent that handles intent classification and routes to appropriate concrete agents.
 * 
 * This is the only agent that implements ChatAgent and is exposed to ChatService.
 * It uses an IntentClassifier to determine user intent, then delegates to the
 * appropriate concrete agent (CreateNoteAgent, SearchNoteAgent, SummarizeNoteAgent)
 * or returns null to fall back to normal RAG/chat flow.
 */
interface MasterAgent : ChatAgent

