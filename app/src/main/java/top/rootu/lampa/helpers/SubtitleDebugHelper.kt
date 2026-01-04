package top.rootu.lampa.helpers

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * SubtitleDebugHelper - Collects and exports diagnostic information about subtitle loading
 * 
 * This helper class tracks subtitle loading attempts, errors, and API responses
 * to help diagnose issues with subtitle providers.
 */
object SubtitleDebugHelper {
    
    private const val TAG = "SubtitleDebugHelper"
    private const val MAX_LOG_ENTRIES = 200
    
    private val logEntries = mutableListOf<LogEntry>()
    
    data class LogEntry(
        val timestamp: Long,
        val level: LogLevel,
        val provider: String,
        val message: String,
        val stackTrace: String? = null
    )
    
    enum class LogLevel {
        DEBUG, INFO, WARNING, ERROR
    }
    
    /**
     * Log a debug message
     */
    fun logDebug(provider: String, message: String) {
        addLogEntry(LogLevel.DEBUG, provider, message)
        Log.d(TAG, "[$provider] $message")
    }
    
    /**
     * Log an info message
     */
    fun logInfo(provider: String, message: String) {
        addLogEntry(LogLevel.INFO, provider, message)
        Log.i(TAG, "[$provider] $message")
    }
    
    /**
     * Log a warning message
     */
    fun logWarning(provider: String, message: String) {
        addLogEntry(LogLevel.WARNING, provider, message)
        Log.w(TAG, "[$provider] $message")
    }
    
    /**
     * Log an error message
     */
    fun logError(provider: String, message: String, throwable: Throwable? = null) {
        val stackTrace = throwable?.stackTraceToString()
        addLogEntry(LogLevel.ERROR, provider, message, stackTrace)
        Log.e(TAG, "[$provider] $message", throwable)
    }
    
    /**
     * Add a log entry to the internal buffer
     */
    private fun addLogEntry(level: LogLevel, provider: String, message: String, stackTrace: String? = null) {
        synchronized(logEntries) {
            logEntries.add(LogEntry(System.currentTimeMillis(), level, provider, message, stackTrace))
            // Keep only the most recent entries
            if (logEntries.size > MAX_LOG_ENTRIES) {
                logEntries.removeAt(0)
            }
        }
    }
    
    /**
     * Get all log entries as a formatted string
     */
    fun getLogsAsString(): String {
        val sb = StringBuilder()
        sb.append("===============================================\n")
        sb.append("LAMPA SUBTITLE DEBUG LOG\n")
        sb.append("Generated: ${getCurrentTimestamp()}\n")
        sb.append("===============================================\n\n")
        
        synchronized(logEntries) {
            if (logEntries.isEmpty()) {
                sb.append("No subtitle loading attempts recorded.\n")
            } else {
                for (entry in logEntries) {
                    sb.append("[${formatTimestamp(entry.timestamp)}] ")
                    sb.append("[${entry.level}] ")
                    sb.append("[${entry.provider}] ")
                    sb.append(entry.message)
                    sb.append("\n")
                    
                    if (entry.stackTrace != null) {
                        sb.append("Stack trace:\n")
                        sb.append(entry.stackTrace)
                        sb.append("\n")
                    }
                    sb.append("\n")
                }
            }
        }
        
        sb.append("\n===============================================\n")
        sb.append("END OF LOG\n")
        sb.append("===============================================\n")
        
        return sb.toString()
    }
    
    /**
     * Export logs to a file in the Download directory
     * 
     * Note: Uses Environment.getExternalStorageDirectory() which is deprecated in API 29+
     * but required to access the specific path /storage/emulated/0/Download/ as requested.
     * Falls back to app-specific directory on failure.
     */
    fun exportLogsToFile(context: Context): String? {
        try {
            val logContent = getLogsAsString()
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
            val timestamp = LocalDateTime.now().format(formatter)
            val filename = "subtitle_debug_${timestamp}.log"
            
            // Save to Download directory as requested (/storage/emulated/0/Download/)
            // Note: This may not work on Android 11+ (API 30+) due to scoped storage
            val downloadDir = File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            
            val logFile = File(downloadDir, filename)
            
            try {
                FileOutputStream(logFile).use { output ->
                    output.write(logContent.toByteArray())
                }
                
                Log.i(TAG, "Subtitle debug log exported to: ${logFile.absolutePath}")
            } catch (e: Exception) {
                // If we can't write to Download folder (Android 11+ scoped storage), fall back to app directory
                Log.w(TAG, "Could not save to Download directory, trying app-specific directory", e)
                val appDownloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val fallbackFile = File(appDownloadDir, filename)
                
                FileOutputStream(fallbackFile).use { output ->
                    output.write(logContent.toByteArray())
                }
                
                Log.i(TAG, "Subtitle debug log exported to app directory: ${fallbackFile.absolutePath}")
                return fallbackFile.absolutePath
            }
            
            // Also save to cache directory as backup
            try {
                val cacheDir = context.cacheDir
                val cacheLogFile = File(cacheDir, filename)
                FileOutputStream(cacheLogFile).use { output ->
                    output.write(logContent.toByteArray())
                }
                Log.i(TAG, "Subtitle debug log also cached at: ${cacheLogFile.absolutePath}")
            } catch (e: Exception) {
                Log.w(TAG, "Could not save to cache directory", e)
            }
            
            // Try to also save to Backup directory if available
            try {
                if (Backup.writeFileSafely(filename, logContent)) {
                    Log.i(TAG, "Subtitle debug log also saved to: ${Backup.DIR}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not save to Backup directory", e)
            }
            
            return logFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting subtitle debug log", e)
            return null
        }
    }
    
    /**
     * Clear all log entries
     */
    fun clearLogs() {
        synchronized(logEntries) {
            logEntries.clear()
        }
        Log.i(TAG, "Subtitle debug logs cleared")
    }
    
    /**
     * Get current timestamp as formatted string
     */
    private fun getCurrentTimestamp(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        return LocalDateTime.now().format(formatter)
    }
    
    /**
     * Format a timestamp in milliseconds to a readable string
     */
    private fun formatTimestamp(millis: Long): String {
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
        return LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(millis),
            java.time.ZoneId.systemDefault()
        ).format(formatter)
    }
    
    /**
     * Trigger a diagnostic crash with subtitle logs
     * This can be used for debugging purposes to capture logs via crash handler
     */
    fun triggerDiagnosticCrash() {
        val diagnosticInfo = getLogsAsString()
        throw SubtitleDiagnosticException(diagnosticInfo)
    }
    
    /**
     * Custom exception for diagnostic crashes
     */
    class SubtitleDiagnosticException(diagnosticInfo: String) : 
        RuntimeException("Subtitle Diagnostic Crash Requested\n\n$diagnosticInfo")
}
