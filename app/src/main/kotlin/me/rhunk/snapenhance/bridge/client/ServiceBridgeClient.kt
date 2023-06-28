package me.rhunk.snapenhance.bridge.client


import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import de.robv.android.xposed.XposedHelpers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
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
import me.rhunk.snapenhance.bridge.common.impl.messagelogger.MessageLoggerListResult
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                bindService(
                    intent,
                    Context.BIND_AUTO_CREATE,
                    Executors.newSingleThreadExecutor(),
                    this@ServiceBridgeClient
                )
            } else {
                XposedHelpers.callMethod(
                    this,
                    "bindServiceAsUser",
                    intent,
                    this@ServiceBridgeClient,
                    Context.BIND_AUTO_CREATE,
                    Handler(handlerThread.looper),
                    android.os.Process.myUserHandle()
                )
            }
        }
        callback(future.get())
    }

    private fun handleResponseMessage(
        msg: Message
    ): BridgeMessage {
        val message: BridgeMessage = when (BridgeMessageType.fromValue(msg.what)) {
            BridgeMessageType.FILE_ACCESS_RESULT -> FileAccessResult()
            BridgeMessageType.DOWNLOAD_CONTENT_RESULT -> DownloadContentResult()
            BridgeMessageType.MESSAGE_LOGGER_RESULT -> MessageLoggerResult()
            BridgeMessageType.MESSAGE_LOGGER_LIST_RESULT -> MessageLoggerListResult()
            BridgeMessageType.LOCALE_RESULT -> LocaleResult()
            else -> throw IllegalStateException("Unknown message type: ${msg.what}")
        }

        with(message) {
            read(msg.data)
            return this
        }
    }

    @Suppress("UNCHECKED_CAST", "UNUSED_PARAMETER")
    private fun <T : BridgeMessage> sendMessage(
        messageType: BridgeMessageType,
        bridgeMessage: BridgeMessage,
        resultType: KClass<T>? = null
    ) = runBlocking {
        return@runBlocking suspendCancellableCoroutine<T> { continuation ->
            with(Message.obtain()) {
                what = messageType.value
                replyTo = Messenger(object : Handler(handlerThread.looper) {
                    override fun handleMessage(msg: Message) {
                        if (continuation.isCompleted) {
                            continuation.cancel(Throwable("Already completed"))
                            return
                        }
                        continuation.resumeWith(Result.success(handleResponseMessage(msg) as T))
                    }
                })
                data = Bundle()
                bridgeMessage.write(data)
                messenger.send(this)
            }
        }
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

    override fun getLoggedMessageIds(conversationId: String, limit: Int): List<Long> {
        sendMessage(
            BridgeMessageType.MESSAGE_LOGGER_REQUEST,
            MessageLoggerRequest(MessageLoggerRequest.Action.LIST_IDS, conversationId, limit.toLong()),
            MessageLoggerListResult::class
        ).run {
            return messages!!
        }
    }

    override fun getMessageLoggerMessage(conversationId: String, id: Long): ByteArray? {
        sendMessage(
            BridgeMessageType.MESSAGE_LOGGER_REQUEST,
            MessageLoggerRequest(MessageLoggerRequest.Action.GET, conversationId, id),
            MessageLoggerResult::class
        ).run {
            return message
        }
    }

    override fun addMessageLoggerMessage(conversationId: String,id: Long, message: ByteArray) {
        sendMessage(
            BridgeMessageType.MESSAGE_LOGGER_REQUEST,
            MessageLoggerRequest(MessageLoggerRequest.Action.ADD, conversationId, id, message),
            MessageLoggerResult::class
        )
    }

    override fun deleteMessageLoggerMessage(conversationId: String,id: Long) {
        sendMessage(
            BridgeMessageType.MESSAGE_LOGGER_REQUEST,
            MessageLoggerRequest(MessageLoggerRequest.Action.DELETE, conversationId, id),
            MessageLoggerResult::class
        )
    }

    override fun clearMessageLogger() {
        sendMessage(
            BridgeMessageType.MESSAGE_LOGGER_REQUEST,
            MessageLoggerRequest(MessageLoggerRequest.Action.CLEAR),
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

    override fun getAutoUpdaterTime(): Long {
        createAndReadFile(BridgeFileType.AUTO_UPDATER_TIMESTAMP, "0".toByteArray()).run {
            return if (isEmpty()) {
                0
            } else {
                String(this).toLong()
            }
        }
    }

    override fun setAutoUpdaterTime(time: Long) {
        writeFile(BridgeFileType.AUTO_UPDATER_TIMESTAMP, time.toString().toByteArray())
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
