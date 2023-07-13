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
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.Logger.xposedLog
import me.rhunk.snapenhance.bridge.AbstractBridgeClient
import me.rhunk.snapenhance.bridge.ForceStartActivity
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
import kotlin.reflect.full.createInstance
import kotlin.system.exitProcess


class ServiceBridgeClient: AbstractBridgeClient(), ServiceConnection {
    private val handlerThread = HandlerThread("BridgeClient")

    private lateinit var messenger: Messenger
    private lateinit var future: CompletableFuture<Boolean>

    override fun start(callback: (Boolean) -> Unit) {
        this.future = CompletableFuture()
        this.handlerThread.start()

        with(context.androidContext) {
            //ensure the remote process is running
            startActivity(
                Intent().apply {
                    setClassName(BuildConfig.APPLICATION_ID, ForceStartActivity::class.java.name)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )

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
        val message: BridgeMessage = BridgeMessageType.fromValue(msg.what).bridgeClass?.createInstance() ?: throw IllegalArgumentException("Unknown message type: ${msg.what}")

        return message.apply {
            read(msg.data)
        }
    }

    @Suppress("UNCHECKED_CAST", "UNUSED_PARAMETER")
    private fun <T : BridgeMessage> sendMessage(
        messageType: BridgeMessageType,
        bridgeMessage: BridgeMessage,
    ) = runBlocking {
        val timeoutHandler = Handler(handlerThread.looper).apply {
            postDelayed({
                Logger.debug("sendMessage for $messageType took more than 3 seconds to respond")
            }, 3000)
        }

        return@runBlocking suspendCancellableCoroutine<T> { continuation ->
            with(Message.obtain()) {
                what = messageType.value
                replyTo = Messenger(object : Handler(handlerThread.looper) {
                    override fun handleMessage(msg: Message) {
                        if (continuation.isCompleted) {
                            continuation.cancel(Throwable("Already completed"))
                            return
                        }
                        timeoutHandler.removeCallbacksAndMessages(null)
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
        sendMessage<FileAccessResult>(
            BridgeMessageType.FILE_ACCESS_REQUEST,
            FileAccessRequest(FileAccessRequest.FileAccessAction.EXISTS, fileType, null)
        ).run {
            if (state!!) {
                return readFile(fileType)
            }
            writeFile(fileType, defaultContent)
            return defaultContent
        }
    }

    override fun readFile(fileType: BridgeFileType): ByteArray {
        sendMessage<FileAccessResult>(
            BridgeMessageType.FILE_ACCESS_REQUEST,
            FileAccessRequest(FileAccessRequest.FileAccessAction.READ, fileType, null)
        ).run {
            return content!!
        }
    }

    override fun writeFile(
        fileType: BridgeFileType,
        content: ByteArray?
    ): Boolean {
        sendMessage<FileAccessResult>(
            BridgeMessageType.FILE_ACCESS_REQUEST,
            FileAccessRequest(FileAccessRequest.FileAccessAction.WRITE, fileType, content)
        ).run {
            return state!!
        }
    }

    override fun deleteFile(fileType: BridgeFileType): Boolean {
        sendMessage<FileAccessResult>(
            BridgeMessageType.FILE_ACCESS_REQUEST,
            FileAccessRequest(FileAccessRequest.FileAccessAction.DELETE, fileType, null)
        ).run {
            return state!!
        }
    }


    override fun isFileExists(fileType: BridgeFileType): Boolean {
        sendMessage<FileAccessResult>(
            BridgeMessageType.FILE_ACCESS_REQUEST,
            FileAccessRequest(FileAccessRequest.FileAccessAction.EXISTS, fileType, null)
        ).run {
            return state!!
        }
    }

    override fun downloadContent(url: String, path: String): Boolean {
        sendMessage<DownloadContentResult>(
            BridgeMessageType.DOWNLOAD_CONTENT_REQUEST,
            DownloadContentRequest(url, path)
        ).run {
            return state!!
        }
    }

    override fun getLoggedMessageIds(conversationId: String, limit: Int): List<Long> {
        sendMessage<MessageLoggerListResult>(
            BridgeMessageType.MESSAGE_LOGGER_REQUEST,
            MessageLoggerRequest(MessageLoggerRequest.Action.LIST_IDS, conversationId, limit.toLong())
        ).run {
            return messages!!
        }
    }

    override fun getMessageLoggerMessage(conversationId: String, id: Long): ByteArray? {
        sendMessage<MessageLoggerResult>(
            BridgeMessageType.MESSAGE_LOGGER_REQUEST,
            MessageLoggerRequest(MessageLoggerRequest.Action.GET, conversationId, id)
        ).run {
            return message
        }
    }

    override fun addMessageLoggerMessage(conversationId: String,id: Long, message: ByteArray) {
        sendMessage<MessageLoggerResult>(
            BridgeMessageType.MESSAGE_LOGGER_REQUEST,
            MessageLoggerRequest(MessageLoggerRequest.Action.ADD, conversationId, id, message)
        )
    }

    override fun deleteMessageLoggerMessage(conversationId: String,id: Long) {
        sendMessage<MessageLoggerResult>(
            BridgeMessageType.MESSAGE_LOGGER_REQUEST,
            MessageLoggerRequest(MessageLoggerRequest.Action.DELETE, conversationId, id)
        )
    }

    override fun clearMessageLogger() {
        sendMessage<MessageLoggerResult>(
            BridgeMessageType.MESSAGE_LOGGER_REQUEST,
            MessageLoggerRequest(MessageLoggerRequest.Action.CLEAR)
        )
    }

    override fun fetchTranslations(): LocaleResult {
        sendMessage<LocaleResult>(
            BridgeMessageType.LOCALE_REQUEST,
            LocaleRequest()
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
