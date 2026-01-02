package org.krypton.chat.agent

/**
 * Represents the intent type of a user message.
 * Used by the MasterAgent to route messages to appropriate concrete agents.
 */
enum class IntentType {
    /**
     * User wants to create a new note or draft content for a new note.
     */
    CREATE_NOTE,
    
    /**
     * User wants to search, find, or list notes.
     */
    SEARCH_NOTES,
    
    /**
     * User wants a summary of the current note or a set of notes on a topic.
     */
    SUMMARIZE_NOTE,
    
    /**
     * Any other conversation, including questions and general chat.
     * Falls back to normal RAG/chat flow.
     */
    NORMAL_CHAT,
    
    /**
     * Intent could not be determined (classification failed or invalid response).
     * Falls back to normal RAG/chat flow.
     */
    UNKNOWN
}

