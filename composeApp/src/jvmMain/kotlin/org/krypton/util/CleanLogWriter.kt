package org.krypton.util

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import java.io.PrintStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Custom log writer that formats logs in a clean, Ollama-style format.
 * 
 * Format: [TAG] YYYY/MM/DD - HH:mm:ss | message
 * 
 * Example: [SecretsLoader] 2025/12/26 - 13:56:49 | Found secret 'TAVILLY_API_KEY'
 */
class CleanLogWriter(
    private val printStream: PrintStream = System.out
) : LogWriter() {
    
    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd - HH:mm:ss")
        .withZone(ZoneId.systemDefault())
    
    // Fixed width for tag (including brackets) to ensure timestamp alignment
    // Longest tag observed: [DefaultRetrievalService] = 24 chars, so 25 provides padding
    private val TAG_WIDTH = 25
    
    // ANSI color codes for terminal output
    private val ANSI_RESET = "\u001B[0m"
    private val ANSI_GREEN = "\u001B[32m"
    private val ANSI_YELLOW = "\u001B[33m"
    private val ANSI_RED = "\u001B[31m"
    private val ANSI_CYAN = "\u001B[36m"
    
    override fun log(
        severity: Severity,
        message: String,
        tag: String,
        throwable: Throwable?
    ) {
        val timestamp = dateTimeFormatter.format(Instant.now())
        val color = getColorForSeverity(severity)
        val reset = ANSI_RESET
        
        // Format tag with brackets and pad to fixed width for alignment
        val tagWithBrackets = "[$tag]"
        val paddedTag = tagWithBrackets.padEnd(TAG_WIDTH)
        
        // Format: [TAG] YYYY/MM/DD - HH:mm:ss | message
        val formattedLog = buildString {
            append("$color$paddedTag$reset")
            append(timestamp)
            append(" | ")
            append(message)
            
            if (throwable != null) {
                append("\n")
                append(throwable.stackTraceToString())
            }
        }
        
        printStream.println(formattedLog)
    }
    
    private fun getColorForSeverity(severity: Severity): String {
        return when (severity) {
            Severity.Info -> ANSI_GREEN
            Severity.Error -> ANSI_RED
            Severity.Debug -> ANSI_YELLOW
            Severity.Warn -> ANSI_CYAN
            else -> ANSI_RESET
        }
    }
}

