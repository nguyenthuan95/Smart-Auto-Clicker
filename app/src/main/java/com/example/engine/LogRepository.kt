package com.example.engine

import com.example.data.model.LogEntry
import com.example.data.model.LogLevel
import com.example.data.model.LogSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogRepository {
    private const val MAX_LOGS = 1000

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _sessions = MutableStateFlow<List<LogSession>>(emptyList())
    val sessions: StateFlow<List<LogSession>> = _sessions.asStateFlow()

    private val _currentSession = MutableStateFlow<LogSession?>(null)
    val currentSession: StateFlow<LogSession?> = _currentSession.asStateFlow()

    @Volatile
    var scriptStartTimeMs: Long = 0L

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun startNewSession(sessionName: String): LogSession {
        val now = System.currentTimeMillis()
        scriptStartTimeMs = now
        val session = LogSession(id = now, name = sessionName, startTimeMs = now)
        val list = _sessions.value.toMutableList()
        list.add(0, session)
        _sessions.value = list
        _currentSession.value = session
        info("Session", "=== BẮT ĐẦU PHIÊN CHẠY #$now: $sessionName ===")
        return session
    }

    fun endSession(summaryMessage: String? = null) {
        val session = _currentSession.value
        if (session != null && scriptStartTimeMs > 0) {
            val totalElapsed = System.currentTimeMillis() - scriptStartTimeMs
            val msg = summaryMessage ?: "Phiên kết thúc. Tổng thời gian chạy: ${totalElapsed}ms"
            info("Session", msg)
        }
        scriptStartTimeMs = 0L
    }

    fun log(level: LogLevel, tag: String = "Clicker", message: String) {
        val now = System.currentTimeMillis()
        val elapsed = if (scriptStartTimeMs > 0) now - scriptStartTimeMs else -1L
        val activeSession = _currentSession.value

        val formattedMessage = if (elapsed >= 0) "[+${elapsed}ms] $message" else message

        val entry = LogEntry(
            timestamp = now,
            level = level,
            tag = tag,
            message = formattedMessage,
            sessionId = activeSession?.id ?: 0L,
            elapsedMs = elapsed
        )

        val current = _logs.value.toMutableList()
        current.add(0, entry) // Thêm mới vào đầu danh sách
        if (current.size > MAX_LOGS) {
            current.removeAt(current.size - 1)
        }
        _logs.value = current
    }

    fun info(tag: String = "Clicker", message: String) = log(LogLevel.INFO, tag, message)
    fun action(tag: String = "Action", message: String) = log(LogLevel.ACTION, tag, message)
    fun ocr(tag: String = "OCR", message: String) = log(LogLevel.OCR, tag, message)
    fun warn(tag: String = "Warn", message: String) = log(LogLevel.WARN, tag, message)
    fun error(tag: String = "Error", message: String) = log(LogLevel.ERROR, tag, message)

    fun clear() {
        _logs.value = emptyList()
        _sessions.value = emptyList()
        _currentSession.value = null
        scriptStartTimeMs = 0L
    }

    fun exportToString(filterSessionId: Long? = null): String {
        val sb = StringBuilder()
        sb.append("=== SMART AUTO CLICKER LOGS ===\n")
        sb.append("Exported: ").append(Date().toString()).append("\n\n")
        val filtered = if (filterSessionId != null && filterSessionId != 0L) {
            _logs.value.filter { it.sessionId == filterSessionId }
        } else {
            _logs.value
        }
        val reversed = filtered.reversed()
        for (entry in reversed) {
            val time = dateFormat.format(Date(entry.timestamp))
            sb.append("[").append(time).append("] ")
                .append("[").append(entry.level.name).append("] ")
                .append("[").append(entry.tag).append("] ")
                .append(entry.message).append("\n")
        }
        return sb.toString()
    }
}
