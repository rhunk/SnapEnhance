package me.rhunk.snapenhance.bridge.types

import android.content.Context
import java.io.File


enum class BridgeFileType(val value: Int, val fileName: String, val displayName: String, private val isDatabase: Boolean = false) {
    CONFIG(0, "config.json", "Config"),
    MAPPINGS(1, "mappings.json", "Mappings"),
    MESSAGE_LOGGER_DATABASE(2, "message_logger.db", "Message Logger",true),
    STEALTH(3, "stealth.txt", "Stealth Conversations"),
    ANTI_AUTO_DOWNLOAD(4, "anti_auto_download.txt", "Anti Auto Download"),
    ANTI_AUTO_SAVE(5, "anti_auto_save.txt", "Anti Auto Save"),
    AUTO_UPDATER_TIMESTAMP(6, "auto_updater_timestamp.txt", "Auto Updater Timestamp"),
    PINNED_CONVERSATIONS(7, "pinned_conversations.txt", "Pinned Conversations");

    fun resolve(context: Context): File = if (isDatabase) {
        context.getDatabasePath(fileName)
    } else {
        File(context.filesDir, fileName)
    }

    companion object {
        fun fromValue(value: Int): BridgeFileType? {
            return values().firstOrNull { it.value == value }
        }
    }
}