package me.rhunk.snapenhance.common.logger

import android.util.Log

abstract class AbstractLogger(
    logChannel: LogChannel,
) {
    private val TAG = logChannel.shortName

    companion object {

        private const val TAG = "SnapEnhanceCommon"

        fun directDebug(message: Any?, tag: String = TAG) {
            Log.println(Log.DEBUG, tag, message.toString())
        }

        fun directError(message: Any?, throwable: Throwable, tag: String = TAG) {
            Log.println(Log.ERROR, tag, message.toString())
            Log.println(Log.ERROR, tag, throwable.toString())
        }

    }

    open fun debug(message: Any?, tag: String = TAG) {}

    open fun error(message: Any?, tag: String = TAG) {}

    open fun error(message: Any?, throwable: Throwable, tag: String = TAG) {}

    open fun info(message: Any?, tag: String = TAG) {}

    open fun verbose(message: Any?, tag: String = TAG) {}

    open fun warn(message: Any?, tag: String = TAG) {}

    open fun assert(message: Any?, tag: String = TAG) {}
}