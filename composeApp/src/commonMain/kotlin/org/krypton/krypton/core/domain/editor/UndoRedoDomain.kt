package org.krypton.krypton.core.domain.editor

/**
 * Manages undo/redo history for a single document.
 * 
 * This is a platform-agnostic domain class that maintains
 * a stack of document states with configurable maximum size.
 * 
 * @param maxHistorySize Maximum number of states to keep in history (default: 100)
 */
class UndoRedoManager(private val maxHistorySize: Int = 100) {
    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()
    
    /**
     * Push a new state to the undo stack.
     * Clears the redo stack when a new edit is made.
     * 
     * @param text The document text state to save
     */
    fun pushState(text: String) {
        // Don't push if it's the same as the current state
        if (undoStack.isNotEmpty() && undoStack.last() == text) {
            return
        }
        
        undoStack.add(text)
        
        // Limit stack size
        if (undoStack.size > maxHistorySize) {
            undoStack.removeAt(0)
        }
        
        // Clear redo stack on new edit
        redoStack.clear()
    }
    
    /**
     * Undo the last change.
     * 
     * @param currentText The current document text
     * @return The previous state, or null if nothing to undo
     */
    fun undo(currentText: String): String? {
        if (undoStack.isEmpty()) {
            return null
        }
        
        // Save current state to redo stack
        redoStack.add(currentText)
        
        // Pop from undo stack
        val previousState = undoStack.removeAt(undoStack.size - 1)
        
        return previousState
    }
    
    /**
     * Redo the last undone change.
     * 
     * @param currentText The current document text
     * @return The next state, or null if nothing to redo
     */
    fun redo(currentText: String): String? {
        if (redoStack.isEmpty()) {
            return null
        }
        
        // Save current state to undo stack
        undoStack.add(currentText)
        
        // Pop from redo stack
        val nextState = redoStack.removeAt(redoStack.size - 1)
        
        return nextState
    }
    
    /**
     * Check if undo is available.
     */
    fun canUndo(): Boolean = undoStack.isNotEmpty()
    
    /**
     * Check if redo is available.
     */
    fun canRedo(): Boolean = redoStack.isNotEmpty()
    
    /**
     * Clear all history.
     */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
    
    /**
     * Initialize with initial state.
     * 
     * @param initialText The initial document text
     */
    fun initialize(initialText: String) {
        clear()
        undoStack.add(initialText)
    }
}

