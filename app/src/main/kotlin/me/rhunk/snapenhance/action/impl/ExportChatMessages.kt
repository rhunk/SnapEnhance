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

    override fun run() {
        GlobalScope.launch(Dispatchers.Main){
            val exportType = askExportType() ?: return@launch

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
                    exportChatForConversations(exportType, friendFeedEntries)
                }
                .setPositiveButton("Export") { _, _ ->
                    exportChatForConversations(exportType, selectedConversations)
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
            500,
            callback
        )
    }

    private suspend fun exportFullConversation(exportFormat: ExportFormat, conversation: FriendFeedInfo) {
        //first fetch the first message
        val conversationId = conversation.key!!
        val conversationName = conversation.feedDisplayName ?: conversation.friendDisplayName!!.split("|").lastOrNull() ?: "unknown"

        conversationAction(true, conversationId, if (conversation.feedDisplayName != null) "USERCREATEDGROUP" else "ONEONONE")

        logDialog("exporting $conversationName ...")

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
            "SnapEnhance/conversation_${conversationName}_${System.currentTimeMillis()}.${exportFormat.extension}"
        ).also { it.parentFile?.mkdirs() }

        logDialog("Writing output ...")
        MessageExporter(context).also {
            it.readInfo(
                conversation,
                foundMessages,
                outputFile
            )
        }.exportTo(exportFormat)

        logDialog("\nExported to ${outputFile.absolutePath}\n")
        runCatching {
            conversationAction(false, conversationId, null)
        }
    }

    private fun exportChatForConversations(exportFormat: ExportFormat, conversations: List<FriendFeedInfo>) {
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
                        exportFullConversation(exportFormat, conversation)
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