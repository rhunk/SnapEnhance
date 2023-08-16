package me.rhunk.snapenhance.bridge

import android.app.Service
import android.content.Intent
import android.os.IBinder
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.SharedContextHolder
import me.rhunk.snapenhance.bridge.types.BridgeFileType
import me.rhunk.snapenhance.bridge.types.FileActionType
import me.rhunk.snapenhance.bridge.wrapper.LocaleWrapper
import me.rhunk.snapenhance.bridge.wrapper.MessageLoggerWrapper
import me.rhunk.snapenhance.download.DownloadProcessor

class BridgeService : Service() {
    private lateinit var messageLoggerWrapper: MessageLoggerWrapper
    private lateinit var remoteSideContext: RemoteSideContext

    override fun onBind(intent: Intent): IBinder {
        remoteSideContext = SharedContextHolder.remote(this).apply {
            checkForRequirements()
        }
        messageLoggerWrapper = MessageLoggerWrapper(getDatabasePath(BridgeFileType.MESSAGE_LOGGER_DATABASE.fileName)).also { it.init() }
        return BridgeBinder()
    }

    inner class BridgeBinder : BridgeInterface.Stub() {
        override fun fileOperation(action: Int, fileType: Int, content: ByteArray?): ByteArray {
            val resolvedFile by lazy { BridgeFileType.fromValue(fileType)?.resolve(this@BridgeService) }

            return when (FileActionType.values()[action]) {
                FileActionType.CREATE_AND_READ -> {
                    resolvedFile?.let {
                        if (!it.exists()) {
                            return content?.also { content -> it.writeBytes(content) } ?: ByteArray(0)
                        }

                        it.readBytes()
                    } ?: ByteArray(0)
                }
                FileActionType.READ -> {
                    resolvedFile?.takeIf { it.exists() }?.readBytes() ?: ByteArray(0)
                }
                FileActionType.WRITE -> {
                    content?.also { resolvedFile?.writeBytes(content) } ?: ByteArray(0)
                }
                FileActionType.DELETE -> {
                    resolvedFile?.takeIf { it.exists() }?.delete()
                    ByteArray(0)
                }
                FileActionType.EXISTS -> {
                    if (resolvedFile?.exists() == true)
                        ByteArray(1)
                    else ByteArray(0)
                }
            }
        }

        override fun getLoggedMessageIds(conversationId: String, limit: Int) = messageLoggerWrapper.getMessageIds(conversationId, limit).toLongArray()

        override fun getMessageLoggerMessage(conversationId: String, id: Long) = messageLoggerWrapper.getMessage(conversationId, id).second

        override fun addMessageLoggerMessage(conversationId: String, id: Long, message: ByteArray) {
            messageLoggerWrapper.addMessage(conversationId, id, message)
        }

        override fun deleteMessageLoggerMessage(conversationId: String, id: Long) = messageLoggerWrapper.deleteMessage(conversationId, id)

        override fun clearMessageLogger() = messageLoggerWrapper.clearMessages()

        override fun fetchLocales(userLocale: String) = LocaleWrapper.fetchLocales(context = this@BridgeService, userLocale).associate {
            it.locale to it.content
        }

        override fun enqueueDownload(intent: Intent, callback: DownloadCallback) {
            DownloadProcessor(
                remoteSideContext = SharedContextHolder.remote(this@BridgeService),
                callback = callback
            ).onReceive(intent)
        }
    }
}
