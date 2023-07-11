package me.rhunk.snapenhance.bridge.service

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.Service
import android.content.*
import android.net.Uri
import android.os.*
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.bridge.MessageLoggerWrapper
import me.rhunk.snapenhance.bridge.TranslationWrapper
import me.rhunk.snapenhance.bridge.common.BridgeMessage
import me.rhunk.snapenhance.bridge.common.BridgeMessageType
import me.rhunk.snapenhance.bridge.common.impl.*
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
import java.io.File
import java.util.*
import kotlin.reflect.full.createInstance
import kotlin.system.exitProcess

typealias ReplyCallback = (BridgeMessage) -> Unit

class BridgeService : Service() {
    private lateinit var messageLoggerWrapper: MessageLoggerWrapper
    private lateinit var bridgeThread: HandlerThread

    override fun onBind(intent: Intent): IBinder {
        bridgeThread = HandlerThread("BridgeService").also { it.start() }
        messageLoggerWrapper = MessageLoggerWrapper(getDatabasePath(BridgeFileType.MESSAGE_LOGGER_DATABASE.fileName)).also { it.init() }

        return Messenger(Handler.createAsync(bridgeThread.looper) { msg ->
            runCatching {
                this@BridgeService.handleMessage(msg)
            }.onFailure {
                Logger.error("Failed to handle message ${BridgeMessageType.fromValue(msg.what)}", it)
            }
            true
        }).binder
    }

    private fun handleMessage(msg: Message) {
        val replyCallback: ReplyCallback = {
            val repliedMessage = Message.obtain(Handler.createAsync(bridgeThread.looper), BridgeMessageType.fromClass(it::class).value).apply {
                it.write(this.data)
            }
            try {
                msg.replyTo.send(repliedMessage)
            } catch (ignored: DeadObjectException) {
                Logger.debug("got DeadObjectException, exiting")
                exitProcess(0)
            }
        }

        val bridgeMessage = BridgeMessageType.fromValue(msg.what).bridgeClass?.createInstance()
        if (bridgeMessage == null) {
            Logger.error("Unknown message type: ${BridgeMessageType.fromValue(msg.what)}")
            return
        }

        bridgeMessage.read(msg.data)

        when (bridgeMessage) {
            is FileAccessRequest -> handleFileAccess(bridgeMessage, replyCallback)
            is DownloadContentRequest -> handleDownloadContent(bridgeMessage, replyCallback)
            is LocaleRequest -> handleLocaleRequest(replyCallback)
            is MessageLoggerRequest ->  handleMessageLoggerRequest(bridgeMessage, replyCallback)
            else -> {
                Logger.error("Unknown message type: ${bridgeMessage::class}")
            }
        }
    }

    private fun handleMessageLoggerRequest(msg: MessageLoggerRequest, reply: ReplyCallback) {
        when (msg.action) {
            MessageLoggerRequest.Action.ADD -> {
                val isSuccess = messageLoggerWrapper.addMessage(msg.conversationId!!, msg.index!!, msg.message!!)
                reply(MessageLoggerResult(isSuccess))
                return
            }
            MessageLoggerRequest.Action.CLEAR -> {
                messageLoggerWrapper.clearMessages()
            }
            MessageLoggerRequest.Action.DELETE -> {
                messageLoggerWrapper.deleteMessage(msg.conversationId!!, msg.index!!)
            }
            MessageLoggerRequest.Action.GET -> {
                val (state, messageData) = messageLoggerWrapper.getMessage(msg.conversationId!!, msg.index!!)
                reply(MessageLoggerResult(state, messageData))
                return
            }
            MessageLoggerRequest.Action.LIST_IDS -> {
                val messageIds = messageLoggerWrapper.getMessageIds(msg.conversationId!!, msg.index!!.toInt())
                reply(MessageLoggerListResult(messageIds))
                return
            }
            else -> {
                Logger.log(Exception("Unknown message logger action: ${msg.action}"))
            }
        }

        reply(MessageLoggerResult(true))
    }

    private fun handleLocaleRequest(reply: ReplyCallback) {
        val locales = sortedSetOf<String>()
        val contentArray = sortedSetOf<String>()

        TranslationWrapper.fetchLocales(context = this).forEach { pair ->
            locales.add(pair.locale)
            contentArray.add(pair.content)
        }

        reply(LocaleResult(locales.toTypedArray(), contentArray.toTypedArray()))
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun handleDownloadContent(msg: DownloadContentRequest, reply: ReplyCallback) {
        if (!msg.url!!.startsWith("http://127.0.0.1:")) return

        val outputFile = File(msg.path!!)
        outputFile.parentFile?.let {
            if (!it.exists()) it.mkdirs()
        }
        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(msg.url))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationUri(Uri.fromFile(outputFile))
        val downloadId = downloadManager.enqueue(request)
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != downloadId) return
                unregisterReceiver(this)
                reply(DownloadContentResult(true))
            }
        }, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    private fun handleFileAccess(msg: FileAccessRequest, reply: ReplyCallback) {
        val fileFolder = if (msg.fileType!!.isDatabase) {
            File(dataDir, "databases")
        } else {
            File(filesDir.absolutePath)
        }
        val requestFile =  File(fileFolder, msg.fileType!!.fileName)

        val result: FileAccessResult = when (msg.action) {
            FileAccessRequest.FileAccessAction.READ -> {
                if (!requestFile.exists()) {
                    FileAccessResult(false, null)
                } else {
                    FileAccessResult(true, requestFile.readBytes())
                }
            }
            FileAccessRequest.FileAccessAction.WRITE -> {
                if (!requestFile.exists()) {
                    requestFile.createNewFile()
                }
                requestFile.writeBytes(msg.content!!)
                FileAccessResult(true, null)
            }
            FileAccessRequest.FileAccessAction.DELETE -> {
                if (!requestFile.exists()) {
                    FileAccessResult(false, null)
                } else {
                    requestFile.delete()
                    FileAccessResult(true, null)
                }
            }
            FileAccessRequest.FileAccessAction.EXISTS -> FileAccessResult(requestFile.exists(), null)
            else -> throw Exception("Unknown action: " + msg.action)
        }
        reply(result)
    }

}
