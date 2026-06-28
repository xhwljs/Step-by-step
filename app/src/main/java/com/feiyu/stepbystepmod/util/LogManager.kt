package com.feiyu.stepbystepmod.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object LogManager {
    private val logs = CopyOnWriteArrayList<LogEntry>()
    private val listeners = mutableListOf<LogListener>()

    data class LogEntry(
        val timestamp: Long,
        val level: LogLevel,
        val tag: String,
        val message: String
    ) {
        fun toFormattedString(): String {
            val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            val time = dateFormat.format(Date(timestamp))
            val prefix = when (level) {
                LogLevel.INFO -> ""
                LogLevel.SUCCESS -> "✓ "
                LogLevel.WARNING -> "⚠ "
                LogLevel.ERROR -> "✗ "
                LogLevel.SEARCH -> "🔍 "
                LogLevel.MODIFY -> "📝 "
                LogLevel.RESTORE -> "↩ "
                LogLevel.DEBUG -> "DBG "
            }
            return "[$time] [${level.name}] $prefix$message"
        }
    }

    enum class LogLevel(val color: Long) {
        INFO(0xFFE2E8F0),
        SUCCESS(0xFF22C55E),
        WARNING(0xFFF59E0B),
        ERROR(0xFFEF4444),
        SEARCH(0xFF60A5FA),
        MODIFY(0xFFFB923C),
        RESTORE(0xFFA78BFA),
        DEBUG(0xFF94A3B8)
    }

    interface LogListener {
        fun onLogAdded(entry: LogEntry)
    }

    fun addListener(listener: LogListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: LogListener) {
        listeners.remove(listener)
    }

    private fun log(message: String, level: LogLevel, tag: String = "StepByStep") {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message)
        logs.add(entry)
        listeners.forEach { it.onLogAdded(entry) }
    }

    fun info(message: String, tag: String = "StepByStep") = log(message, LogLevel.INFO, tag)
    fun success(message: String, tag: String = "StepByStep") = log(message, LogLevel.SUCCESS, tag)
    fun warning(message: String, tag: String = "StepByStep") = log(message, LogLevel.WARNING, tag)
    fun error(message: String, tag: String = "StepByStep") = log(message, LogLevel.ERROR, tag)
    fun search(message: String, tag: String = "StepByStep") = log(message, LogLevel.SEARCH, tag)
    fun modify(message: String, tag: String = "StepByStep") = log(message, LogLevel.MODIFY, tag)
    fun restore(message: String, tag: String = "StepByStep") = log(message, LogLevel.RESTORE, tag)
    fun debug(message: String, tag: String = "StepByStep") = log(message, LogLevel.DEBUG, tag)

    fun getAllLogs(): List<LogEntry> = logs.toList()

    fun getFormattedLogs(): String {
        return logs.joinToString("\n") { it.toFormattedString() }
    }

    fun clear() {
        logs.clear()
    }

    fun getLogCount(): Int = logs.size
}
