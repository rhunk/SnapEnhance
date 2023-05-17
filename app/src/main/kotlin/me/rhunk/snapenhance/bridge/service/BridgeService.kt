package me.rhunk.snapenhance.bridge.service

import android.app.DownloadManager
import android.app.Service
import android.content.*
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.*
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.bridge.common.BridgeMessageType
import me.rhunk.snapenhance.bridge.common.impl.*
import java.io.File
import java.util.*

class BridgeService : Service() {
    companion object {
        const val CONFIG_FILE = "config.json"
        const val MAPPINGS_FILE = "mappings.json"
        const val STEALTH_FILE = "stealth.txt"
        const val MESSAGE_LOGGER_DATABASE = "message_logger"
        const val ANTI_AUTO_DOWNLOAD_FILE = "anti_auto_download.txt"
    }

    lateinit var messageLoggerDatabase: SQLiteDatabase

    override fun onBind(intent: Intent): IBinder {
        with(openOrCreateDatabase(MESSAGE_LOGGER_DATABASE, Context.MODE_PRIVATE, null)) {
            messageLoggerDatabase = this
            execSQL("CREATE TABLE IF NOT EXISTS messages (message_id INTEGER PRIMARY KEY, serialized_message BLOB)")
        }

        return Messenger(object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                runCatching {
                    this@BridgeService.handleMessage(msg)
                }.onFailure {
                    Logger.error("Failed to handle message", it)
                }
            }
        }).binder
    }

    private fun handleMessage(msg: Message) {
        val replyMessenger = msg.replyTo
        when (BridgeMessageType.fromValue(msg.what)) {
            BridgeMessageType.FILE_ACCESS_REQUEST -> {
                with(FileAccessRequest()) {
                    read(msg.data)
                    handleFileAccess(this) { message ->
                        replyMessenger.send(message)
                    }
                }
            }
            BridgeMessageType.DOWNLOAD_CONTENT_REQUEST -> {
                with(DownloadContentRequest()) {
                    read(msg.data)
                    handleDownloadContent(this) { message ->
                        replyMessenger.send(message)
                    }
                }
            }
            BridgeMessageType.LOCALE_REQUEST -> {
                with(LocaleRequest()) {
                    read(msg.data)
                    handleLocaleRequest(this) { message ->
                        replyMessenger.send(message)
                    }
                }
            }
            BridgeMessageType.MESSAGE_LOGGER_REQUEST -> {
                with(MessageLoggerRequest()) {
                    read(msg.data)
                    handleMessageLoggerRequest(this) { message ->
                        replyMessenger.send(message)
                    }
                }
            }

            else -> Logger.log("Unknown message type: " + msg.what)
        }
    }

    private fun handleMessageLoggerRequest(msg: MessageLoggerRequest, reply: (Message) -> Unit) {
        when (msg.action) {
            MessageLoggerRequest.Action.ADD  -> {
                messageLoggerDatabase.insert("messages", null, ContentValues().apply {
                    put("message_id", msg.messageId)
                    put("serialized_message", msg.message)
                })
            }
            MessageLoggerRequest.Action.CLEAR -> {
                messageLoggerDatabase.execSQL("DELETE FROM messages")
            }
            MessageLoggerRequest.Action.GET -> {
                val messageId = msg.messageId
                val cursor = messageLoggerDatabase.rawQuery("SELECT serialized_message FROM messages WHERE message_id = ?", arrayOf(messageId.toString()))
                val state = cursor.moveToFirst()
                val message: ByteArray? = if (state) {
                    cursor.getBlob(0)
                } else {
                    null
                }
                cursor.close()
                reply(MessageLoggerResult(state, message).toMessage(BridgeMessageType.MESSAGE_LOGGER_RESULT.value))
            }
            else -> {
                Logger.log(Exception("Unknown message logger action: ${msg.action}"))
            }
        }

        reply(MessageLoggerResult(true).toMessage(BridgeMessageType.MESSAGE_LOGGER_RESULT.value))
    }

    private fun handleLocaleRequest(msg: LocaleRequest, reply: (Message) -> Unit) {
        val deviceLocale = Locale.getDefault().toString()
        val compatibleLocale = resources.assets.list("lang")?.find { it.startsWith(deviceLocale) }?.substring(0, 5) ?: "en_US"

        resources.assets.open("lang/$compatibleLocale.json").use { inputStream ->
            val json = inputStream.bufferedReader().use { it.readText() }
            reply(LocaleResult(compatibleLocale, json.toByteArray(Charsets.UTF_8)).toMessage(BridgeMessageType.LOCALE_RESULT.value))
        }
    }

    private fun handleDownloadContent(msg: DownloadContentRequest, reply: (Message) -> Unit) {
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
                reply(DownloadContentResult(true).toMessage(BridgeMessageType.DOWNLOAD_CONTENT_RESULT.value))
            }
        }, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    private fun handleFileAccess(msg: FileAccessRequest, reply: (Message) -> Unit) {
        val file = when (msg.fileType) {
            FileAccessRequest.FileType.CONFIG -> CONFIG_FILE
            FileAccessRequest.FileType.MAPPINGS -> MAPPINGS_FILE
            FileAccessRequest.FileType.STEALTH -> STEALTH_FILE
            FileAccessRequest.FileType.ANTI_AUTO_DOWNLOAD -> ANTI_AUTO_DOWNLOAD_FILE
            else -> throw Exception("Unknown file type: " + msg.fileType)
        }.let { File(filesDir, it) }

        val result: FileAccessResult = when (msg.action) {
            FileAccessRequest.FileAccessAction.READ -> {
                if (!file.exists()) {
                    FileAccessResult(false, null)
                } else {
                    FileAccessResult(true, file.readBytes())
                }
            }
            FileAccessRequest.FileAccessAction.WRITE -> {
                if (!file.exists()) {
                    file.createNewFile()
                }
                file.writeBytes(msg.content!!)
                FileAccessResult(true, null)
            }
            FileAccessRequest.FileAccessAction.DELETE -> {
                if (!file.exists()) {
                    FileAccessResult(false, null)
                } else {
                    file.delete()
                    FileAccessResult(true, null)
                }
            }
            FileAccessRequest.FileAccessAction.EXISTS -> FileAccessResult(file.exists(), null)
            else -> throw Exception("Unknown action: " + msg.action)
        }

        reply(result.toMessage(BridgeMessageType.FILE_ACCESS_RESULT.value))
    }

}
