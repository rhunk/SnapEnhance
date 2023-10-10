package me.rhunk.snapenhance.core.logger

import android.annotation.SuppressLint
import android.util.Log
import de.robv.android.xposed.XposedBridge
import me.rhunk.snapenhance.common.logger.AbstractLogger
import me.rhunk.snapenhance.common.logger.LogChannel
import me.rhunk.snapenhance.common.logger.LogLevel
import me.rhunk.snapenhance.core.bridge.BridgeClient
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook


@SuppressLint("PrivateApi")
class CoreLogger(
    private val bridgeClient: BridgeClient
): AbstractLogger(LogChannel.CORE) {
    companion object {
        private const val TAG = "SnapEnhanceCore"

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

    override fun debug(message: Any?, tag: String) = internalLog(tag, LogLevel.DEBUG, message)

    override fun error(message: Any?, tag: String) = internalLog(tag, LogLevel.ERROR, message)

    override fun error(message: Any?, throwable: Throwable, tag: String) {
        internalLog(tag, LogLevel.ERROR, message)
        internalLog(tag, LogLevel.ERROR, throwable.stackTraceToString())
    }

    override fun info(message: Any?, tag: String) = internalLog(tag, LogLevel.INFO, message)

    override fun verbose(message: Any?, tag: String) = internalLog(tag, LogLevel.VERBOSE, message)

    override fun warn(message: Any?, tag: String) = internalLog(tag, LogLevel.WARN, message)

    override fun assert(message: Any?, tag: String) = internalLog(tag, LogLevel.ASSERT, message)
}