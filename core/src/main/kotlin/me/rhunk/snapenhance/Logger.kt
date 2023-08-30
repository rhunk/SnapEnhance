package me.rhunk.snapenhance

import android.util.Log
import de.robv.android.xposed.XposedBridge
import me.rhunk.snapenhance.core.bridge.BridgeClient

enum class LogLevel(
    val letter: String,
    val shortName: String,
    val priority: Int = Log.INFO
) {
    VERBOSE("V", "verbose", Log.VERBOSE),
    DEBUG("D", "debug", Log.DEBUG),
    INFO("I", "info", Log.INFO),
    WARN("W", "warn", Log.WARN),
    ERROR("E", "error", Log.ERROR),
    ASSERT("A", "assert", Log.ASSERT);

    companion object {
        fun fromLetter(letter: String): LogLevel? {
            return values().find { it.letter == letter }
        }

        fun fromShortName(shortName: String): LogLevel? {
            return values().find { it.shortName == shortName }
        }
    }
}


class Logger(
    private val bridgeClient: BridgeClient
) {
    companion object {
        private const val TAG = "SnapEnhanceCore"

        fun directDebug(message: Any?, tag: String = TAG) {
            Log.println(Log.DEBUG, tag, message.toString())
        }

        fun directError(message: Any?, throwable: Throwable, tag: String = TAG) {
            Log.println(Log.ERROR, tag, message.toString())
            Log.println(Log.ERROR, tag, throwable.toString())
        }

        fun xposedLog(message: Any?, tag: String = TAG) {
            Log.println(Log.INFO, tag, message.toString())
            XposedBridge.log("$tag: $message")
        }

        fun xposedLog(message: Any?, throwable: Throwable, tag: String = TAG) {
            Log.println(Log.INFO, tag, message.toString())
            XposedBridge.log("$tag: $message")
            XposedBridge.log(throwable)
        }
    }

    private fun internalLog(tag: String, logLevel: LogLevel, message: Any?) {
        runCatching {
            bridgeClient.broadcastLog(tag, logLevel.shortName, message.toString())
        }.onFailure {
            Log.println(logLevel.priority, tag, message.toString())
        }
    }

    fun debug(message: Any?, tag: String = TAG) = internalLog(tag, LogLevel.DEBUG, message)

    fun error(message: Any?, tag: String = TAG) = internalLog(tag, LogLevel.ERROR, message)

    fun error(message: Any?, throwable: Throwable, tag: String = TAG) {
        internalLog(tag, LogLevel.ERROR, message)
        internalLog(tag, LogLevel.ERROR, throwable)
    }

    fun info(message: Any?, tag: String = TAG) = internalLog(tag, LogLevel.INFO, message)

    fun verbose(message: Any?, tag: String = TAG) = internalLog(tag, LogLevel.VERBOSE, message)

    fun warn(message: Any?, tag: String = TAG) = internalLog(tag, LogLevel.WARN, message)

    fun assert(message: Any?, tag: String = TAG) = internalLog(tag, LogLevel.ASSERT, message)
}