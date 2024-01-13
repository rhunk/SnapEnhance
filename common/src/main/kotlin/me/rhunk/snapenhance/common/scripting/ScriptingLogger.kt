package me.rhunk.snapenhance.common.scripting

import me.rhunk.snapenhance.common.logger.AbstractLogger
import me.rhunk.snapenhance.common.logger.LogChannel

class ScriptingLogger(
    private val logger: AbstractLogger
) {
    companion object {
        private val TAG = LogChannel.SCRIPTING.channel
    }

    fun debug(message: Any?, tag: String = TAG) {
        logger.debug(message, tag)
    }

    fun error(message: Any?, tag: String = TAG) {
        logger.error(message, tag)
    }

    fun error(message: Any?, throwable: Throwable, tag: String = TAG) {
        logger.error(message, throwable, tag)
    }

    fun info(message: Any?, tag: String = TAG) {
        logger.info(message, tag)
    }

    fun verbose(message: Any?, tag: String = TAG) {
        logger.verbose(message, tag)
    }

    fun warn(message: Any?, tag: String = TAG) {
        logger.warn(message, tag)
    }

    fun assert(message: Any?, tag: String = TAG) {
        logger.assert(message, tag)
    }
}