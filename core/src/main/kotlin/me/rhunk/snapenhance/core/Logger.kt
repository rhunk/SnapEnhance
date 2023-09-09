package me.rhunk.snapenhance.core

import android.annotation.SuppressLint
import android.util.Log
import de.robv.android.xposed.XposedBridge
import me.rhunk.snapenhance.core.bridge.BridgeClient
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.hook

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

        fun fromPriority(priority: Int): LogLevel? {
            return values().find { it.priority == priority }
        }
    }
}

enum class LogChannels(val channel: String, val shortName: String) {
    CORE("SnapEnhanceCore", "core"),
    NATIVE("SnapEnhanceNative", "native"),
    MANAGER("SnapEnhanceManager", "manager"),
    XPOSED("LSPosed-Bridge", "xposed");

    companion object {
        fun fromChannel(channel: String): LogChannels? {
            return values().find { it.channel == channel }
        }
    }
}


@SuppressLint("PrivateApi")
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

    private var invokeOriginalPrintLog: (Int, String, String) -> Unit

    init {
        val printLnMethod = Log::class.java.getDeclaredMethod("println", Int::class.java, String::class.java, String::class.java)
        printLnMethod.hook(HookStage.BEFORE) { param ->
            val priority = param.arg(0) as Int
            val tag = param.arg(1) as String
            val message = param.arg(2) as String
            internalLog(tag, LogLevel.fromPriority(priority) ?: LogLevel.INFO, message)
        }

        invokeOriginalPrintLog = { priority, tag, message ->
            XposedBridge.invokeOriginalMethod(
                printLnMethod,
                null,
                arrayOf(priority, tag, message)
            )
        }
    }

    private fun internalLog(tag: String, logLevel: LogLevel, message: Any?) {
        runCatching {
            bridgeClient.broadcastLog(tag, logLevel.shortName, message.toString())
        }.onFailure {
            invokeOriginalPrintLog(logLevel.priority, tag, message.toString())
        }
    }

    fun debug(message: Any?, tag: String = TAG) = internalLog(tag, LogLevel.DEBUG, message)

    fun error(message: Any?, tag: String = TAG) = internalLog(tag, LogLevel.ERROR, message)

    fun error(message: Any?, throwable: Throwable, tag: String = TAG) {
        internalLog(tag, LogLevel.ERROR, message)
        internalLog(tag, LogLevel.ERROR, throwable.stackTraceToString())
    }

    fun info(message: Any?, tag: String = TAG) = internalLog(tag, LogLevel.INFO, message)

    fun verbose(message: Any?, tag: String = TAG) = internalLog(tag, LogLevel.VERBOSE, message)

    fun warn(message: Any?, tag: String = TAG) = internalLog(tag, LogLevel.WARN, message)

    fun assert(message: Any?, tag: String = TAG) = internalLog(tag, LogLevel.ASSERT, message)
}