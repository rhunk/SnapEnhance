package me.rhunk.snapenhance.action.impl

import android.app.AlertDialog
import android.os.Environment
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.action.AbstractAction
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.wrapper.impl.Message
import me.rhunk.snapenhance.data.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.database.objects.FriendFeedInfo
import me.rhunk.snapenhance.features.impl.Messaging
import me.rhunk.snapenhance.util.CallbackBuilder
import me.rhunk.snapenhance.util.export.ExportFormat
import me.rhunk.snapenhance.util.export.MessageExporter
import java.io.File

@OptIn(DelicateCoroutinesApi::class)
class ExportChatMessages : AbstractAction("action.export_chat_messages") {
    private val callbackClass by lazy {  context.mappings.getMappedClass("callbacks", "Callback") }

    private val fetchConversationWithMessagesCallbackClass by lazy {  context.mappings.getMappedClass("callbacks", "FetchConversationWithMessagesCallback") }

    private val enterConversationMethod by lazy {
        context.classCache.conversationManager.methods.first { it.name == "enterConversation" }
    }
    private val exitConversationMethod by lazy {
        context.classCache.conversationManager.methods.first { it.name == "exitConversation" }
    }
    private val fetchConversationWithMessagesPaginatedMethod by lazy {
        context.classCache.conversationManager.methods.first { it.name == "fetchConversationWithMessagesPaginated" }
    }

    private val conversationManagerInstance by lazy {
        context.feature(Messaging::class).conversationManager
    }

    private val dialogLogs = mutableListOf<String>()
    private var currentActionDialog: AlertDialog? = null

    private var exportType: ExportFormat? = null
    private var mediaToDownload: List<ContentType>? = null

    private fun logDialog(message: String) {
        context.runOnUiThread {
            if (dialogLogs.size > 20) dialogLogs.removeAt(0)
            dialogLogs.add(message)
            Logger.debug("dialog: $message")
            currentActionDialog!!.setMessage(dialogLogs.joinToString("\n"))
        }
    }

    private suspend fun askExportType() = suspendCancellableCoroutine { cont ->
        context.runOnUiThread {
            AlertDialog.Builder(context.mainActivity)
                .setTitle("Select the export format")
                .setItems(ExportFormat.values().map { it.name }.toTypedArray()) { _, which ->
                    cont.resumeWith(Result.success(ExportFormat.values()[which]))
                }
                .setOnCancelListener {
                    cont.resumeWith(Result.success(null))
                }
                .show()
        }
    }

    private suspend fun askMediaToDownload() = suspendCancellableCoroutine { cont ->
        context.runOnUiThread {
            val mediasToDownload = mutableListOf<ContentType>()
            val contentTypes = arrayOf(
                ContentType.SNAP,
                ContentType.EXTERNAL_MEDIA,
                ContentType.NOTE,
                ContentType.STICKER
            )
            AlertDialog.Builder(context.mainActivity)
                .setTitle("Select the media types to download")
                .setMultiChoiceItems(contentTypes.map { it.name }.toTypedArray(), BooleanArray(contentTypes.size) { false }) { _, which, isChecked ->
                    val media = contentTypes[which]
                    if (isChecked) {
                        mediasToDownload.add(media)
                    } else if (mediasToDownload.contains(media)) {
                        mediasToDownload.remove(media)
                    }
                }
                .setOnCancelListener {
                    cont.resumeWith(Result.success(null))
                }
                .setPositiveButton("OK") { _, _ ->
                    cont.resumeWith(Result.success(mediasToDownload))
                }
                .show()
        }
    }

    override fun run() {
        GlobalScope.launch(Dispatchers.Main) {
            exportType = askExportType() ?: return@launch
            mediaToDownload = if (exportType == ExportFormat.HTML) askMediaToDownload() else null

            val friendFeedEntries = context.database.getFriendFeed(20)
            val selectedConversations = mutableListOf<FriendFeedInfo>()

            AlertDialog.Builder(context.mainActivity)
                .setTitle("Select a conversation")
                .setMultiChoiceItems(
                    friendFeedEntries.map { it.feedDisplayName ?: it.friendDisplayName!!.split("|").firstOrNull() }.toTypedArray(),
                    BooleanArray(friendFeedEntries.size) { false }
                ) { _, which, isChecked ->
                    if (isChecked) {
                        selectedConversations.add(friendFeedEntries[which])
                    } else if (selectedConversations.contains(friendFeedEntries[which])) {
                        selectedConversations.remove(friendFeedEntries[which])
                    }
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .setNeutralButton("Export all") { _, _ ->
                    exportChatForConversations(friendFeedEntries)
                }
                .setPositiveButton("Export") { _, _ ->
                    exportChatForConversations(selectedConversations)
                }
                .show()
        }
    }

    private suspend fun conversationAction(isEntering: Boolean, conversationId: String, conversationType: String?) = suspendCancellableCoroutine { continuation ->
        val callback = CallbackBuilder(callbackClass)
            .override("onSuccess") { _ ->
                continuation.resumeWith(Result.success(Unit))
            }
            .override("onError") {
                continuation.resumeWith(Result.failure(Exception("Failed to ${if (isEntering) "enter" else "exit"} conversation")))
            }.build()

        if (isEntering) {
            enterConversationMethod.invoke(
                conversationManagerInstance,
                SnapUUID.fromString(conversationId).instanceNonNull(),
                enterConversationMethod.parameterTypes[1].enumConstants.first { it.toString() == conversationType },
                callback
            )
        } else {
            exitConversationMethod.invoke(
                conversationManagerInstance,
                SnapUUID.fromString(conversationId).instanceNonNull(),
                Long.MAX_VALUE,
                callback
            )
        }
    }

    private suspend fun fetchMessagesPaginated(conversationId: String, lastMessageId: Long) = suspendCancellableCoroutine { continuation ->
        val callback = CallbackBuilder(fetchConversationWithMessagesCallbackClass)
            .override("onFetchConversationWithMessagesComplete") { param ->
                val messagesList = param.arg<List<*>>(1).map { Message(it) }
                continuation.resumeWith(Result.success(messagesList))
            }
            .override("onServerRequest", shouldUnhook = false) {}
            .override("onError") {
                continuation.resumeWith(Result.failure(Exception("Failed to fetch messages")))
            }.build()

        fetchConversationWithMessagesPaginatedMethod.invoke(
            conversationManagerInstance,
            SnapUUID.fromString(conversationId).instanceNonNull(),
            lastMessageId,
            100,
            callback
        )
    }

    private suspend fun exportFullConversation(friendFeedInfo: FriendFeedInfo) {
        //first fetch the first message
        val conversationId = friendFeedInfo.key!!
        val conversationName = friendFeedInfo.feedDisplayName ?: friendFeedInfo.friendDisplayName!!.split("|").lastOrNull() ?: "unknown"

        conversationAction(true, conversationId, if (friendFeedInfo.feedDisplayName != null) "USERCREATEDGROUP" else "ONEONONE")

        logDialog("Exporting $conversationName ...")

        val foundMessages = fetchMessagesPaginated(conversationId, Long.MAX_VALUE).toMutableList()
        var lastMessageId = foundMessages.firstOrNull()?.messageDescriptor?.messageId ?: run {
            logDialog("No messages found")
            return
        }

        while (true) {
            Logger.debug("[$conversationName] fetching $lastMessageId")
            val messages = fetchMessagesPaginated(conversationId, lastMessageId)
            if (messages.isEmpty()) break
            foundMessages.addAll(messages)
            messages.firstOrNull()?.let {
                lastMessageId = it.messageDescriptor.messageId
            }
        }

        val outputFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "SnapEnhance/conversation_${conversationName}_${System.currentTimeMillis()}.${exportType!!.extension}"
        ).also { it.parentFile?.mkdirs() }

        logDialog("Writing output ...")
        MessageExporter(
            context = context,
            friendFeedInfo = friendFeedInfo,
            outputFile = outputFile,
            mediaToDownload = mediaToDownload,
            printLog = ::logDialog
        ).also {
            runCatching {
                it.readMessages(foundMessages)
            }.onFailure {
                logDialog("Failed to export conversation: ${it.message}")
                Logger.error(it)
            }
        }.exportTo(exportType!!)

        logDialog("\nExported to ${outputFile.absolutePath}\n")
        runCatching {
            conversationAction(false, conversationId, null)
        }
    }

    private fun exportChatForConversations(conversations: List<FriendFeedInfo>) {
        dialogLogs.clear()
        val jobs = mutableListOf<Job>()

        currentActionDialog = AlertDialog.Builder(context.mainActivity)
            .setTitle("Exporting chats")
            .setCancelable(false)
            .setMessage("")
            .setNegativeButton("Close") { dialog, _ ->
                jobs.forEach { it.cancel() }
                dialog.dismiss()
            }
            .create()

        logDialog("Processing ${conversations.size} conversations")

        currentActionDialog!!.show()

        GlobalScope.launch(Dispatchers.Main) {
            conversations.forEach { conversation ->
                launch {
                    runCatching {
                        exportFullConversation(conversation)
                    }.onFailure {
                        logDialog("Failed to export conversation ${conversation.key}")
                        logDialog(it.stackTraceToString())
                        Logger.xposedLog(it)
                    }
                }.also { jobs.add(it) }
            }
            jobs.joinAll()
            logDialog("Done! You now can close this dialog")
        }
    }
}