package me.rhunk.snapenhance.bridge

import android.app.Service
import android.content.Intent
import android.os.IBinder
import me.rhunk.snapenhance.SharedContext
import me.rhunk.snapenhance.bridge.types.BridgeFileType
import me.rhunk.snapenhance.bridge.wrapper.MessageLoggerWrapper
import me.rhunk.snapenhance.bridge.wrapper.TranslationWrapper
import me.rhunk.snapenhance.download.DownloadProcessor

class BridgeService : Service() {
    private lateinit var messageLoggerWrapper: MessageLoggerWrapper
    override fun onBind(intent: Intent): IBinder {
        messageLoggerWrapper = MessageLoggerWrapper(getDatabasePath(BridgeFileType.MESSAGE_LOGGER_DATABASE.fileName)).also { it.init() }
        return BridgeBinder()
    }

    inner class BridgeBinder : BridgeInterface.Stub() {
        override fun createAndReadFile(fileType: Int, defaultContent: ByteArray?): ByteArray {
            val file = BridgeFileType.fromValue(fileType)?.resolve(this@BridgeService)
                ?: return defaultContent ?: ByteArray(0)

            if (!file.exists()) {
                if (defaultContent == null) {
                    return ByteArray(0)
                }

                file.writeBytes(defaultContent)
            }

            return file.readBytes()
        }

        override fun readFile(fileType: Int): ByteArray {
            val file = BridgeFileType.fromValue(fileType)?.resolve(this@BridgeService)
                ?: return ByteArray(0)

            if (!file.exists()) {
                return ByteArray(0)
            }

            return file.readBytes()
        }

        override fun writeFile(fileType: Int, content: ByteArray?): Boolean {
            val file = BridgeFileType.fromValue(fileType)?.resolve(this@BridgeService)
                ?: return false

            if (content == null) {
                return false
            }

            file.writeBytes(content)
            return true
        }

        override fun deleteFile(fileType: Int): Boolean {
            val file = BridgeFileType.fromValue(fileType)?.resolve(this@BridgeService)
                ?: return false

            if (!file.exists()) {
                return false
            }

            return file.delete()
        }

        override fun isFileExists(fileType: Int): Boolean {
            val file = BridgeFileType.fromValue(fileType)?.resolve(this@BridgeService)
                ?: return false

            return file.exists()
        }

        override fun getLoggedMessageIds(conversationId: String, limit: Int) = messageLoggerWrapper.getMessageIds(conversationId, limit).toLongArray()

        override fun getMessageLoggerMessage(conversationId: String, id: Long) = messageLoggerWrapper.getMessage(conversationId, id).second

        override fun addMessageLoggerMessage(conversationId: String, id: Long, message: ByteArray) {
            messageLoggerWrapper.addMessage(conversationId, id, message)
        }

        override fun deleteMessageLoggerMessage(conversationId: String, id: Long) = messageLoggerWrapper.deleteMessage(conversationId, id)

        override fun clearMessageLogger() = messageLoggerWrapper.clearMessages()

        override fun fetchTranslations() = TranslationWrapper.fetchLocales(context = this@BridgeService).associate {
            it.locale to it.content
        }

        override fun getAutoUpdaterTime(): Long {
            throw UnsupportedOperationException()
        }

        override fun setAutoUpdaterTime(time: Long) {
            throw UnsupportedOperationException()
        }

        override fun enqueueDownload(intent: Intent, callback: DownloadCallback) {
            SharedContext.ensureInitialized(this@BridgeService)
            DownloadProcessor(this@BridgeService, callback).onReceive(intent)
        }
    }
}
