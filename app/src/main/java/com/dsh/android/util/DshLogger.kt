package com.dsh.android.util

import android.content.Context
import android.os.Environment
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import javax.inject.Inject
import javax.inject.Singleton

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
) {
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun format(): String {
        val time = dateFormat.format(Date(timestamp))
        val base = "[$time] ${level.name}/$tag: $message"
        return if (throwable != null) {
            "$base\n  ${throwable::class.simpleName}: ${throwable.message}\n  ${
                throwable.stackTrace.take(5).joinToString("\n  ") { "at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" }
            }"
        } else base
    }
}

@Singleton
class DshLogger @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val RING_BUFFER_SIZE = 1000
        private const val TAG = "DshLogger"
    }

    // Ring buffer (always active, keeps last N entries)
    private val ringBuffer = ConcurrentLinkedDeque<LogEntry>()

    // File logging state
    private val _isFileLogging = MutableStateFlow(false)
    val isFileLogging: StateFlow<Boolean> = _isFileLogging.asStateFlow()

    private var logFileWriter: PrintWriter? = null
    private var logFile: File? = null

    // Log count for UI
    private val _logCount = MutableStateFlow(0)
    val logCount: StateFlow<Int> = _logCount.asStateFlow()

    // Recent logs for display (last 200)
    private val _recentLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val recentLogs: StateFlow<List<LogEntry>> = _recentLogs.asStateFlow()

    // ─── Ring Buffer API ───────────────────────────────────────

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String) = log(LogLevel.WARN, tag, message)
    fun e(tag: String, message: String, t: Throwable? = null) = log(LogLevel.ERROR, tag, message, t)

    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(level = level, tag = tag, message = message, throwable = throwable)

        // Always add to ring buffer
        ringBuffer.addLast(entry)
        while (ringBuffer.size > RING_BUFFER_SIZE) {
            ringBuffer.removeFirst()
        }
        _logCount.value = ringBuffer.size
        _recentLogs.value = ringBuffer.toList().takeLast(200)

        // Also write to Android logcat
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message, throwable)
            LogLevel.INFO -> Log.i(tag, message, throwable)
            LogLevel.WARN -> Log.w(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
        }

        // Also write to file if file logging is active
        synchronized(this) {
            logFileWriter?.let { writer ->
                try {
                    writer.println(entry.format())
                    writer.flush()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to write log to file", e)
                }
            }
        }
    }

    // ─── File Logging API ──────────────────────────────────────

    fun startFileLogging(): File? {
        return try {
            val dir = File(context.getExternalFilesDir(null), "logs")
            dir.mkdirs()

            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "dsh_log_${dateFormat.format(Date())}.txt"
            val file = File(dir, fileName)

            val writer = PrintWriter(FileWriter(file, true), true)

            // Write header
            writer.println("═══════════════════════════════════════════")
            writer.println("DSH Android Debug Log")
            writer.println("Started: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            writer.println("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (API ${android.os.Build.VERSION.SDK_INT})")
            writer.println("App version: ${getAppVersion()}")
            writer.println("═══════════════════════════════════════════")
            writer.println()

            // Flush ring buffer to file
            for (entry in ringBuffer) {
                writer.println(entry.format())
            }
            writer.println()
            writer.println("--- Live logging starts here ---")
            writer.println()

            logFileWriter = writer
            logFile = file
            _isFileLogging.value = true

            i(TAG, "File logging started: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start file logging", e)
            null
        }
    }

    fun stopFileLogging(): File? {
        synchronized(this) {
            logFileWriter?.let { writer ->
                writer.println()
                writer.println("--- Live logging stopped ---")
                writer.println("Stopped: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                writer.flush()
                writer.close()
            }
            logFileWriter = null
            _isFileLogging.value = false
            val file = logFile
            logFile = null
            i(TAG, "File logging stopped")
            return file
        }
    }

    fun getLogFile(): File? = logFile

    // ─── Export API ────────────────────────────────────────────

    fun exportRingBuffer(): File? {
        return try {
            val dir = File(context.getExternalFilesDir(null), "logs")
            dir.mkdirs()

            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "dsh_log_export_${dateFormat.format(Date())}.txt"
            val file = File(dir, fileName)

            val writer = PrintWriter(FileWriter(file))
            writer.println("═══════════════════════════════════════════")
            writer.println("DSH Android Log Export (Ring Buffer)")
            writer.println("Exported: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            writer.println("Entries: ${ringBuffer.size}")
            writer.println("═══════════════════════════════════════════")
            writer.println()

            for (entry in ringBuffer) {
                writer.println(entry.format())
            }

            writer.flush()
            writer.close()
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export ring buffer", e)
            null
        }
    }

    fun clearRingBuffer() {
        ringBuffer.clear()
        _logCount.value = 0
        _recentLogs.value = emptyList()
    }

    // ─── Helpers ───────────────────────────────────────────────

    private fun getAppVersion(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${pInfo.versionName} (${pInfo.longVersionCode})"
        } catch (e: Exception) {
            "unknown"
        }
    }

    /** Log an HTTP request/response pair */
    fun logRpc(namespace: String, method: String, wireKey: String, body: String? = null, response: String? = null, error: String? = null, durationMs: Long = 0) {
        val tag = "DshRpc"
        if (error != null) {
            e(tag, "[$namespace/$method] wireKey=$wireKey duration=${durationMs}ms ERROR: $error")
            if (body != null) d(tag, "[$namespace/$method] request: ${body.take(500)}")
            if (response != null) d(tag, "[$namespace/$method] response: ${response.take(500)}")
        } else {
            i(tag, "[$namespace/$method] wireKey=$wireKey duration=${durationMs}ms OK")
            if (body != null) d(tag, "[$namespace/$method] request: ${body.take(500)}")
            if (response != null) d(tag, "[$namespace/$method] response: ${response.take(500)}")
        }
    }

    /** Log a WebSocket event */
    fun logWebSocket(event: String, detail: String = "") {
        d("DshWebSocket", "$event${if (detail.isNotBlank()) " | $detail" else ""}")
    }
}
