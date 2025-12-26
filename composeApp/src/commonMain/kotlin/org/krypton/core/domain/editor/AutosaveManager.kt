package org.krypton.core.domain.editor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages automatic saving of documents with debouncing.
 * 
 * This domain-level manager handles the timing and logic for autosave,
 * separate from UI concerns. The actual file writing is delegated to
 * the caller through a callback.
 */
class AutosaveManager(
    private val coroutineScope: CoroutineScope
) {
    private var autosaveJob: Job? = null
    private var lastDocumentPath: String? = null
    private var lastDocumentText: String? = null
    
    /**
     * Schedules an autosave operation for the given document.
     * 
     * If a previous autosave is pending, it will be cancelled and a new one scheduled.
     * 
     * @param documentPath Path to the document (null for untitled documents)
     * @param documentText Current text content of the document
     * @param isDirty Whether the document has unsaved changes
     * @param autosaveIntervalSeconds Interval in seconds to wait before autosaving
     * @param onSave Callback to perform the actual save operation
     */
    fun scheduleAutosave(
        documentPath: String?,
        documentText: String,
        isDirty: Boolean,
        autosaveIntervalSeconds: Int,
        onSave: () -> Unit
    ) {
        // Cancel previous autosave if document changed
        if (documentPath != lastDocumentPath || documentText != lastDocumentText) {
            cancelAutosave()
            lastDocumentPath = documentPath
            lastDocumentText = documentText
        }
        
        // Only schedule if document is dirty and has a path
        if (!isDirty || documentPath == null) {
            return
        }
        
        // Cancel any existing autosave job
        autosaveJob?.cancel()
        
        // Schedule new autosave
        val delayMs = (autosaveIntervalSeconds * 1000L).coerceAtLeast(1000) // Minimum 1 second
        autosaveJob = coroutineScope.launch {
            delay(delayMs)
            onSave()
        }
    }
    
    /**
     * Cancels any pending autosave operation.
     */
    fun cancelAutosave() {
        autosaveJob?.cancel()
        autosaveJob = null
    }
    
    /**
     * Resets the autosave manager state.
     * Useful when switching documents or closing the editor.
     */
    fun reset() {
        cancelAutosave()
        lastDocumentPath = null
        lastDocumentText = null
    }
}

