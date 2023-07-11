package me.rhunk.snapenhance.bridge.common

import me.rhunk.snapenhance.bridge.common.impl.download.DownloadContentRequest
import me.rhunk.snapenhance.bridge.common.impl.download.DownloadContentResult
import me.rhunk.snapenhance.bridge.common.impl.file.FileAccessRequest
import me.rhunk.snapenhance.bridge.common.impl.file.FileAccessResult
import me.rhunk.snapenhance.bridge.common.impl.locale.LocaleRequest
import me.rhunk.snapenhance.bridge.common.impl.locale.LocaleResult
import me.rhunk.snapenhance.bridge.common.impl.messagelogger.MessageLoggerListResult
import me.rhunk.snapenhance.bridge.common.impl.messagelogger.MessageLoggerRequest
import me.rhunk.snapenhance.bridge.common.impl.messagelogger.MessageLoggerResult
import kotlin.reflect.KClass


enum class BridgeMessageType(
    val value: Int = 0,
    val bridgeClass: KClass<out BridgeMessage>? = null
) {
    UNKNOWN(-1),
    FILE_ACCESS_REQUEST(0, FileAccessRequest::class),
    FILE_ACCESS_RESULT(1, FileAccessResult::class),
    DOWNLOAD_CONTENT_REQUEST(2, DownloadContentRequest::class),
    DOWNLOAD_CONTENT_RESULT(3, DownloadContentResult::class),
    LOCALE_REQUEST(4, LocaleRequest::class),
    LOCALE_RESULT(5, LocaleResult::class),
    MESSAGE_LOGGER_REQUEST(6, MessageLoggerRequest::class),
    MESSAGE_LOGGER_RESULT(7, MessageLoggerResult::class),
    MESSAGE_LOGGER_LIST_RESULT(8, MessageLoggerListResult::class);

    companion object {
        fun fromValue(value: Int): BridgeMessageType {
            return values().firstOrNull { it.value == value } ?: UNKNOWN
        }

        fun fromClass(clazz: KClass<out BridgeMessage>): BridgeMessageType {
            return values().firstOrNull { it.bridgeClass == clazz } ?: UNKNOWN
        }
    }
}
