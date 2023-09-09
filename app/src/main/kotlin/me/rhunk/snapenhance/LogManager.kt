package me.rhunk.snapenhance

import android.content.SharedPreferences
import android.util.Log
import com.google.gson.GsonBuilder
import me.rhunk.snapenhance.core.LogLevel
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Duration.Companion.hours

class LogLine(
    val logLevel: LogLevel,
    val dateTime: String,
    val tag: String,
    val message: String
) {
    companion object {
        fun fromString(line: String) = runCatching {
            val parts = line.trimEnd().split("/")
            if (parts.size != 4) return@runCatching null
            LogLine(
                LogLevel.fromLetter(parts[0]) ?: return@runCatching null,
                parts[1],
                parts[2],
                parts[3]
            )
        }.getOrNull()
    }

    override fun toString(): String {
        return "${logLevel.letter}/$dateTime/$tag/$message"
    }
}


class LogReader(
    logFile: File
) {
    private val randomAccessFile = RandomAccessFile(logFile, "r")
    private var startLineIndexes = mutableListOf<Long>()
    var lineCount = queryLineCount()

    private fun readLogLine(): LogLine? {
        val lines = StringBuilder()
        val lastPointer = randomAccessFile.filePointer
        var lastChar: Int = -1
        var bufferLength = 0
        while (true) {
            val char = randomAccessFile.read()
            if (char == -1) {
                randomAccessFile.seek(lastPointer)
                return null
            }
            if ((char == '|'.code && lastChar == '\n'.code) || bufferLength > 4096) {
                break
            }
            lines.append(char.toChar())
            bufferLength++
            lastChar = char
        }

        return LogLine.fromString(lines.trimEnd().toString())
            ?: LogLine(LogLevel.ERROR, "1970-01-01 00:00:00", "LogReader", "Failed to parse log line: $lines")
    }

    fun incrementLineCount() {
        randomAccessFile.seek(randomAccessFile.length())
        startLineIndexes.add(randomAccessFile.filePointer)
        lineCount++
    }

    private fun queryLineCount(): Int {
        randomAccessFile.seek(0)
        var lines = 0
        var lastIndex: Long
        while (true) {
            lastIndex = randomAccessFile.filePointer
            readLogLine() ?: break
            startLineIndexes.add(lastIndex)
            lines++
        }
        return lines
    }

    private fun getLine(index: Int): String? {
        if (index <= 0 || index > lineCount) return null
        randomAccessFile.seek(startLineIndexes[index])
        return readLogLine()?.toString()
    }

    fun getLogLine(index: Int): LogLine? {
        return getLine(index)?.let { LogLine.fromString(it) }
    }
}


class LogManager(
    private val remoteSideContext: RemoteSideContext
) {
    companion object {
        private const val TAG = "SnapEnhanceManager"
        private val LOG_LIFETIME = 24.hours
    }

    var lineAddListener = { _: LogLine -> }

    private val logFolder = File(remoteSideContext.androidContext.cacheDir, "logs")
    private val preferences: SharedPreferences

    private var logFile: File

    init {
        if (!logFolder.exists()) {
            logFolder.mkdirs()
        }
        preferences = remoteSideContext.androidContext.getSharedPreferences("logger", 0)
        logFile = preferences.getString("log_file", null)?.let { File(it) }?.takeIf { it.exists() } ?: run {
            newLogFile()
            logFile
        }

        if (System.currentTimeMillis() - preferences.getLong("last_created", 0) > LOG_LIFETIME.inWholeMilliseconds) {
            newLogFile()
        }
    }

    private fun getCurrentDateTime(pathSafe: Boolean = false): String {
        return DateTimeFormatter.ofPattern(if (pathSafe) "yyyy-MM-dd_HH-mm-ss" else "yyyy-MM-dd HH:mm:ss").format(
            java.time.LocalDateTime.now()
        )
    }

    private fun newLogFile() {
        val currentTime = System.currentTimeMillis()
        logFile = File(logFolder, "snapenhance_${getCurrentDateTime(pathSafe = true)}.log").also {
            it.createNewFile()
        }
        preferences.edit().putString("log_file", logFile.absolutePath).putLong("last_created", currentTime).apply()
    }

    fun clearLogs() {
        logFolder.listFiles()?.forEach { it.delete() }
        newLogFile()
    }

    fun exportLogsToZip(outputStream: OutputStream) {
        val zipOutputStream = ZipOutputStream(outputStream)
        //add logFolder to zip
        logFolder.walk().forEach {
            if (it.isFile) {
                zipOutputStream.putNextEntry(ZipEntry(it.name))
                it.inputStream().copyTo(zipOutputStream)
                zipOutputStream.closeEntry()
            }
        }

        //add device info to zip
        zipOutputStream.putNextEntry(ZipEntry("device_info.json"))
        val gson = GsonBuilder().setPrettyPrinting().create()
        zipOutputStream.write(gson.toJson(remoteSideContext.installationSummary).toByteArray())
        zipOutputStream.closeEntry()

        zipOutputStream.close()
    }

    fun newReader(onAddLine: (LogLine) -> Unit) = LogReader(logFile).also {
        lineAddListener = { line -> it.incrementLineCount(); onAddLine(line) }
    }

    fun debug(message: Any?, tag: String = TAG) {
        internalLog(tag, LogLevel.DEBUG, message)
    }

    fun error(message: Any?, tag: String = TAG) {
        internalLog(tag, LogLevel.ERROR, message)
    }

    fun error(message: Any?, throwable: Throwable, tag: String = TAG) {
        internalLog(tag, LogLevel.ERROR, message)
        internalLog(tag, LogLevel.ERROR, throwable.stackTraceToString())
    }

    fun info(message: Any?, tag: String = TAG) {
        internalLog(tag, LogLevel.INFO, message)
    }

    fun verbose(message: Any?, tag: String = TAG) {
        internalLog(tag, LogLevel.VERBOSE, message)
    }

    fun warn(message: Any?, tag: String = TAG) {
        internalLog(tag, LogLevel.WARN, message)
    }

    fun assert(message: Any?, tag: String = TAG) {
        internalLog(tag, LogLevel.ASSERT, message)
    }

    fun internalLog(tag: String, logLevel: LogLevel, message: Any?) {
        runCatching {
            val line = LogLine(logLevel, getCurrentDateTime(), tag, message.toString())
            logFile.appendText("|$line\n", Charsets.UTF_8)
            lineAddListener(line)
            Log.println(logLevel.priority, tag, message.toString())
        }.onFailure {
            Log.println(Log.ERROR, tag, "Failed to log message: $message")
            Log.println(Log.ERROR, tag, it.toString())
        }
    }
}