package me.rhunk.snapenhance.core.logger

abstract class AbstractLogger(
    logChannel: LogChannel,
) {
    private val TAG = logChannel.shortName


    open fun debug(message: Any?, tag: String = TAG) {}

    open fun error(message: Any?, tag: String = TAG) {}

    open fun error(message: Any?, throwable: Throwable, tag: String = TAG) {}

    open fun info(message: Any?, tag: String = TAG) {}

    open fun verbose(message: Any?, tag: String = TAG) {}

    open fun warn(message: Any?, tag: String = TAG) {}

    open fun assert(message: Any?, tag: String = TAG) {}
}