package me.rhunk.snapenhance.bridge.common


enum class BridgeMessageType(
    val value: Int = 0
) {
    UNKNOWN(-1),
    FILE_ACCESS_REQUEST(0),
    FILE_ACCESS_RESULT(1),
    DOWNLOAD_CONTENT_REQUEST(2),
    DOWNLOAD_CONTENT_RESULT(3),
    LOCALE_REQUEST(4),
    LOCALE_RESULT(5),
    MESSAGE_LOGGER_REQUEST(6),
    MESSAGE_LOGGER_RESULT(7),
    MESSAGE_LOGGER_LIST_RESULT(8);

    companion object {
        fun fromValue(value: Int): BridgeMessageType {
            return values().firstOrNull { it.value == value } ?: UNKNOWN
        }
    }
}
