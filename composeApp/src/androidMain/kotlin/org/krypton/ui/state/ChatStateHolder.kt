package org.krypton.ui.state

/**
 * Android implementation: RAG does not require a vault.
 * 
 * On Android, RAG is query-only and uses ChromaDB Cloud,
 * so it can work without a local vault being open.
 */
internal actual fun ragRequiresVault(): Boolean = false

