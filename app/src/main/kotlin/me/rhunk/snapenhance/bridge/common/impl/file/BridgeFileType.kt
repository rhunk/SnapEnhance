package me.rhunk.snapenhance.bridge.common.impl.file


enum class BridgeFileType(val value: Int, val fileName: String, val isDatabase: Boolean = false) {
    CONFIG(0, "config.json"),
    MAPPINGS(1, "mappings.json"),
    MESSAGE_LOGGER_DATABASE(2, "message_logger.db", true),
    STEALTH(3, "stealth.txt"),
    ANTI_AUTO_DOWNLOAD(4, "anti_auto_download.txt"),
    ANTI_AUTO_SAVE(5, "anti_auto_save.txt"),
    AUTO_UPDATER_TIMESTAMP(6, "auto_updater_timestamp.txt");

    companion object {
        fun fromValue(value: Int): BridgeFileType? {
            return values().firstOrNull { it.value == value }
        }
    }
}