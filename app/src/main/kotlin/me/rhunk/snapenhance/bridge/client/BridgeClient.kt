package me.rhunk.snapenhance.bridge.client


import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import me.rhunk.snapenhance.BuildConfig
import me.rhunk.snapenhance.Logger.log
import me.rhunk.snapenhance.Logger.xposedLog
import me.rhunk.snapenhance.ModContext
import me.rhunk.snapenhance.bridge.common.BridgeMessage
import me.rhunk.snapenhance.bridge.common.BridgeMessageType
import me.rhunk.snapenhance.bridge.common.impl.*
import me.rhunk.snapenhance.bridge.service.BridgeService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.reflect.KClass
import kotlin.system.exitProcess


class BridgeClient(
    private val context: ModContext
) : ServiceConnection {
    private val handlerThread = HandlerThread("BridgeClient")

    private lateinit var messenger: Messenger
    private lateinit var future: CompletableFuture<Boolean>

    fun start(callback: (Boolean) -> Unit = {}) {
        this.future = CompletableFuture()
        this.handlerThread.start()

        with(context.androidContext) {
            val intent = Intent()
                .setClassName(BuildConfig.APPLICATION_ID, BridgeService::class.java.name)
            bindService(
                intent,
                Context.BIND_AUTO_CREATE,
                Executors.newSingleThreadExecutor(),
                this@BridgeClient
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

    /**
     * Create a file if it doesn't exist, and read it
     *
     * @param fileType       the type of file to create and read
     * @param defaultContent the default content to write to the file if it doesn't exist
     * @return the content of the file
     */
    fun createAndReadFile(
        fileType: FileAccessRequest.FileType,
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

    /**
     * Read a file
     *
     * @param fileType the type of file to read
     * @return the content of the file
     */
    fun readFile(fileType: FileAccessRequest.FileType): ByteArray {
        sendMessage(
            BridgeMessageType.FILE_ACCESS_REQUEST,
            FileAccessRequest(FileAccessRequest.FileAccessAction.READ, fileType, null),
            FileAccessResult::class
        ).run {
            return content!!
        }
    }

    /**
     * Write a file
     *
     * @param fileType the type of file to write
     * @param content  the content to write to the file
     * @return true if the file was written successfully
     */
    fun writeFile(
        fileType: FileAccessRequest.FileType,
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

    /**
     * Delete a file
     *
     * @param fileType the type of file to delete
     * @return true if the file was deleted successfully
     */
    fun deleteFile(fileType: FileAccessRequest.FileType): Boolean {
        sendMessage(
            BridgeMessageType.FILE_ACCESS_REQUEST,
            FileAccessRequest(FileAccessRequest.FileAccessAction.DELETE, fileType, null),
            FileAccessResult::class
        ).run {
            return state!!
        }
    }

    /**
     * Check if a file exists
     *
     * @param fileType the type of file to check
     * @return true if the file exists
     */

    fun isFileExists(fileType: FileAccessRequest.FileType): Boolean {
        sendMessage(
            BridgeMessageType.FILE_ACCESS_REQUEST,
            FileAccessRequest(FileAccessRequest.FileAccessAction.EXISTS, fileType, null),
            FileAccessResult::class
        ).run {
            return state!!
        }
    }

    /**
     * Download content from a URL and save it to a file
     *
     * @param url  the URL to download content from
     * @param path the path to save the content to
     * @return true if the content was downloaded successfully
     */
    fun downloadContent(url: String, path: String): Boolean {
        sendMessage(
            BridgeMessageType.DOWNLOAD_CONTENT_REQUEST,
            DownloadContentRequest(url, path),
            DownloadContentResult::class
        ).run {
            return state!!
        }
    }

    fun getMessageLoggerMessage(id: Long): ByteArray? {
        sendMessage(
            BridgeMessageType.MESSAGE_LOGGER_REQUEST,
            MessageLoggerRequest(MessageLoggerRequest.Action.GET, id),
            MessageLoggerResult::class
        ).run {
            return message
        }
    }

    fun addMessageLoggerMessage(id: Long, message: ByteArray) {
        sendMessage(
            BridgeMessageType.MESSAGE_LOGGER_REQUEST,
            MessageLoggerRequest(MessageLoggerRequest.Action.ADD, id, message),
            MessageLoggerResult::class
        )
    }

    fun fetchTranslations(): LocaleResult {
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
        context.longToast("Bridge service disconnected")
        Thread.sleep(1000)
        exitProcess(0)
    }
}
