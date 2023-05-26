package me.rhunk.snapenhance.bridge.client


import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import me.rhunk.snapenhance.BuildConfig
import me.rhunk.snapenhance.Logger.xposedLog
import me.rhunk.snapenhance.bridge.AbstractBridgeClient
import me.rhunk.snapenhance.bridge.common.BridgeMessage
import me.rhunk.snapenhance.bridge.common.BridgeMessageType
import me.rhunk.snapenhance.bridge.common.impl.download.DownloadContentRequest
import me.rhunk.snapenhance.bridge.common.impl.download.DownloadContentResult
import me.rhunk.snapenhance.bridge.common.impl.file.BridgeFileType
import me.rhunk.snapenhance.bridge.common.impl.file.FileAccessRequest
import me.rhunk.snapenhance.bridge.common.impl.file.FileAccessResult
import me.rhunk.snapenhance.bridge.common.impl.locale.LocaleRequest
import me.rhunk.snapenhance.bridge.common.impl.locale.LocaleResult
import me.rhunk.snapenhance.bridge.common.impl.messagelogger.MessageLoggerRequest
import me.rhunk.snapenhance.bridge.common.impl.messagelogger.MessageLoggerResult
import me.rhunk.snapenhance.bridge.service.BridgeService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.reflect.KClass
import kotlin.system.exitProcess


class ServiceBridgeClient: AbstractBridgeClient(), ServiceConnection {
    private val handlerThread = HandlerThread("BridgeClient")

    private lateinit var messenger: Messenger
    private lateinit var future: CompletableFuture<Boolean>

    override fun start(callback: (Boolean) -> Unit) {
        this.future = CompletableFuture()
        this.handlerThread.start()

        with(context.androidContext) {
            val intent = Intent()
                .setClassName(BuildConfig.APPLICATION_ID, BridgeService::class.java.name)
            bindService(
                intent,
                Context.BIND_AUTO_CREATE,
                Executors.newSingleThreadExecutor(),
                this@ServiceBridgeClient
            )
        }
        callback(future.get())
    }

    private fun handleResponseMessage(
        msg: Message,
        future: CompletableFuture<BridgeMessage>
    ) {
        val message: BridgeMessage = when (BridgeMessageType.fromValue(msg.what)) {
            BridgeMessageType.FILE_ACCESS_RESULT -> FileAccessResult()
            BridgeMessageType.DOWNLOAD_CONTENT_RESULT -> DownloadContentResult()
            BridgeMessageType.MESSAGE_LOGGER_RESULT -> MessageLoggerResult()
            BridgeMessageType.LOCALE_RESULT -> LocaleResult()
            else -> {
                future.completeExceptionally(IllegalStateException("Unknown message type: ${msg.what}"))
                return
            }
        }

        with(message) {
            read(msg.data)
            future.complete(this)
        }
    }

    @Suppress("UNCHECKED_CAST", "UNUSED_PARAMETER")
    private fun <T : BridgeMessage> sendMessage(
        messageType: BridgeMessageType,
        message: BridgeMessage,
        resultType: KClass<T>? = null
    ): T {
        val future = CompletableFuture<BridgeMessage>()

        val replyMessenger = Messenger(object : Handler(handlerThread.looper) {
            override fun handleMessage(msg: Message) {
                handleResponseMessage(msg, future)
            }
        })

        runCatching {
            with(Message.obtain()) {
                what = messageType.value
                replyTo = replyMessenger
                data = Bundle()
                message.write(data)
                messenger.send(this)
            }
        }

        return future.get() as T
    }

    override fun createAndReadFile(
        fileType: BridgeFileType,
        defaultContent: ByteArray
    ): ByteArray {
        sendMessage(
            BridgeMessageType.FILE_ACCESS_REQUEST,
            FileAccessRequest(FileAccessRequest.FileAccessAction.EXISTS, fileType, null),
            FileAccessResult::class
        ).run {
            if (state!!) {
                return readFile(fileType)
            }
            writeFile(fileType, defaultContent)
            return defaultContent
        }
    }

    override fun readFile(fileType: BridgeFileType): ByteArray {
        sendMessage(
            BridgeMessageType.FILE_ACCESS_REQUEST,
            FileAccessRequest(FileAccessRequest.FileAccessAction.READ, fileType, null),
            FileAccessResult::class
        ).run {
            return content!!
        }
    }

    override fun writeFile(
        fileType: BridgeFileType,
        content: ByteArray?
    ): Boolean {
        sendMessage(
            BridgeMessageType.FILE_ACCESS_REQUEST,
            FileAccessRequest(FileAccessRequest.FileAccessAction.WRITE, fileType, content),
            FileAccessResult::class
        ).run {
            return state!!
        }
    }

    override fun deleteFile(fileType: BridgeFileType): Boolean {
        sendMessage(
            BridgeMessageType.FILE_ACCESS_REQUEST,
            FileAccessRequest(FileAccessRequest.FileAccessAction.DELETE, fileType, null),
            FileAccessResult::class
        ).run {
            return state!!
        }
    }


    override fun isFileExists(fileType: BridgeFileType): Boolean {
        sendMessage(
            BridgeMessageType.FILE_ACCESS_REQUEST,
            FileAccessRequest(FileAccessRequest.FileAccessAction.EXISTS, fileType, null),
            FileAccessResult::class
        ).run {
            return state!!
        }
    }

    override fun downloadContent(url: String, path: String): Boolean {
        sendMessage(
            BridgeMessageType.DOWNLOAD_CONTENT_REQUEST,
            DownloadContentRequest(url, path),
            DownloadContentResult::class
        ).run {
            return state!!
        }
    }

    override fun getMessageLoggerMessage(id: Long): ByteArray? {
        sendMessage(
            BridgeMessageType.MESSAGE_LOGGER_REQUEST,
            MessageLoggerRequest(MessageLoggerRequest.Action.GET, id),
            MessageLoggerResult::class
        ).run {
            return message
        }
    }

    override fun addMessageLoggerMessage(id: Long, message: ByteArray) {
        sendMessage(
            BridgeMessageType.MESSAGE_LOGGER_REQUEST,
            MessageLoggerRequest(MessageLoggerRequest.Action.ADD, id, message),
            MessageLoggerResult::class
        )
    }

    override fun deleteMessageLoggerMessage(id: Long) {
        sendMessage(
            BridgeMessageType.MESSAGE_LOGGER_REQUEST,
            MessageLoggerRequest(MessageLoggerRequest.Action.DELETE, id),
            MessageLoggerResult::class
        )
    }

    override fun clearMessageLogger() {
        sendMessage(
            BridgeMessageType.MESSAGE_LOGGER_REQUEST,
            MessageLoggerRequest(MessageLoggerRequest.Action.CLEAR, 0),
            MessageLoggerResult::class
        )
    }

    override fun fetchTranslations(): LocaleResult {
        sendMessage(
            BridgeMessageType.LOCALE_REQUEST,
            LocaleRequest(),
            LocaleResult::class
        ).run {
            return this
        }
    }

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        messenger = Messenger(service)
        future.complete(true)
    }

    override fun onNullBinding(name: ComponentName) {
        xposedLog("failed to connect to bridge service")
        future.complete(false)
    }

    override fun onServiceDisconnected(name: ComponentName) {
        exitProcess(0)
    }
}
