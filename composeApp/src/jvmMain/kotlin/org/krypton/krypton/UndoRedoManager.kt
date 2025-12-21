package org.krypton.krypton

/**
 * Manages undo/redo history for a single document.
 * Maintains a stack of document states with configurable maximum size.
 */
class UndoRedoManager(private val maxHistorySize: Int = 100) {
    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()
    
    /**
     * Push a new state to the undo stack.
     * Clears the redo stack when a new edit is made.
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
     * Returns the previous state, or null if nothing to undo.
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
     * Returns the next state, or null if nothing to redo.
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
     */
    fun initialize(initialText: String) {
        clear()
        undoStack.add(initialText)
    }
}

