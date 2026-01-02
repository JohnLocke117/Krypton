package org.krypton.ui.state

/**
 * Desktop (JVM) implementation: RAG requires a vault.
 * 
 * On Desktop, RAG indexes local notes from the vault,
 * so a vault must be open for RAG to work.
 */
internal actual fun ragRequiresVault(): Boolean = true

